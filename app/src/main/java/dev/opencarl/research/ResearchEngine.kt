package dev.opencarl.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

data class Source(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * Web research without a browser: search, then read the pages and hand the
 * agent a compact corpus it can cite. Tavily when a key is present, DuckDuckGo
 * HTML otherwise so it works with no account at all.
 */
class ResearchEngine(
    private val http: OkHttpClient,
    private val tavilyKey: () -> String?,
) {

    suspend fun search(query: String, max: Int = 6): List<Source> = withContext(Dispatchers.IO) {
        val key = tavilyKey()
        if (!key.isNullOrBlank()) tavily(query, key, max).takeIf { it.isNotEmpty() } ?: duckDuckGo(query, max)
        else duckDuckGo(query, max)
    }

    suspend fun deepDive(query: String, pages: Int = 4, charsPerPage: Int = 4_000): String =
        withContext(Dispatchers.IO) {
            val sources = search(query, pages + 2)
            if (sources.isEmpty()) return@withContext "No results for: $query"
            val builder = StringBuilder()
            builder.append("query: ").append(query).append("\n\n")
            sources.take(pages).forEachIndexed { index, source ->
                val text = fetchText(source.url).take(charsPerPage)
                builder.append("[").append(index + 1).append("] ").append(source.title).append('\n')
                builder.append(source.url).append('\n')
                builder.append(text.ifBlank { source.snippet }).append("\n\n")
            }
            builder.append("cite sources as [n] using the numbers above.\n")
            builder.toString()
        }

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) openCarl/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                htmlToText(body)
            }
        }.getOrDefault("")
    }

    private fun tavily(query: String, key: String, max: Int): List<Source> {
        val payload = Json.parseToJsonElement(
            """{"query":${quote(query)},"max_results":$max,"search_depth":"advanced"}""",
        )
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $key")
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.map { element ->
                    val obj = element.jsonObject
                    Source(
                        title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                        url = obj["url"]?.jsonPrimitive?.content.orEmpty(),
                        snippet = obj["content"]?.jsonPrimitive?.content.orEmpty().take(600),
                    )
                }.orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    private fun duckDuckGo(query: String, max: Int): List<Source> {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) openCarl/1.0")
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val pattern = Regex(
                    """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>.*?<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""",
                    RegexOption.DOT_MATCHES_ALL,
                )
                pattern.findAll(html).take(max).map { match ->
                    Source(
                        title = stripTags(match.groupValues[2]).trim(),
                        url = decodeDuckLink(match.groupValues[1]),
                        snippet = stripTags(match.groupValues[3]).trim().take(600),
                    )
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeDuckLink(link: String): String {
        val marker = "uddg="
        if (!link.contains(marker)) return link
        val encoded = link.substringAfter(marker).substringBefore('&')
        return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(link)
    }

    private fun quote(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    private fun stripTags(html: String): String = html
        .replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")

    private fun htmlToText(html: String): String {
        val withoutScripts = html
            .replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<nav.*?</nav>", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<footer.*?</footer>", RegexOption.DOT_MATCHES_ALL), " ")
        return stripTags(withoutScripts).trim()
    }
}
