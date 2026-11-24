package dev.opencarl.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object HttpFetcher {

    private val scriptBlocks = Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>")
    private val comments = Regex("(?s)<!--.*?-->")
    private val blockTags = Regex("(?i)</?(p|div|br|li|tr|h[1-6]|section|article|header|footer)[^>]*>")
    private val tags = Regex("(?s)<[^>]+>")
    private val blankLines = Regex("\n{3,}")

    suspend fun fetch(http: OkHttpClient, url: String, maxChars: Int = 120_000): String =
        withContext(Dispatchers.IO) {
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "only http(s) URLs are allowed"
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "openCarl/0.1 (+https://github.com/ones-is-lord/open-cArl)")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} for $url")
                val body = response.body ?: error("empty response body")
                val raw = body.string()
                val contentType = response.header("Content-Type").orEmpty()
                val text = if (contentType.contains("html")) htmlToText(raw) else raw
                if (text.length > maxChars) text.take(maxChars) + "\n[truncated]" else text
            }
        }

    fun htmlToText(html: String): String {
        var text = scriptBlocks.replace(html, " ")
        text = comments.replace(text, " ")
        text = blockTags.replace(text, "\n")
        text = tags.replace(text, " ")
        text = text.replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'")
        text = text.lineSequence().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
        return blankLines.replace(text, "\n\n").trim()
    }
}
