package dev.opencarl.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import dev.opencarl.ai.arr
import dev.opencarl.ai.boolean
import dev.opencarl.ai.long
import dev.opencarl.ai.obj
import dev.opencarl.ai.parseJsonObject
import dev.opencarl.ai.str
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

val MCP_JSON = "application/json".toMediaType()

/** Resolves an SSE endpoint that may be relative to the stream URL. */
internal fun resolveUrl(base: String, endpoint: String): String {
    if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) return endpoint
    val origin = runCatching { java.net.URI(base) }.getOrNull() ?: return endpoint
    val prefix = origin.scheme + "://" + origin.authority
    return if (endpoint.startsWith("/")) prefix + endpoint
    else prefix + origin.path.substringBeforeLast('/', "") + "/" + endpoint
}

/** One JSON-RPC connection to an MCP server. */
interface McpTransport : AutoCloseable {
    suspend fun start()
    suspend fun request(payload: JsonObject): JsonObject
    suspend fun notify(payload: JsonObject)
}

/**
 * Streamable HTTP transport: one POST per message, responses arrive either as a
 * JSON body or as an SSE stream carrying the matching response id.
 */
class HttpMcpTransport(
    private val http: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>,
) : McpTransport {

    private var sessionId: String? = null

    override suspend fun start() = Unit

    override suspend fun request(payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val response = execute(payload)
        response.use {
            if (!it.isSuccessful) {
                val detail = runCatching { it.body?.string() }.getOrNull()?.take(500).orEmpty()
                throw McpException("HTTP ${it.code}: $detail")
            }
            val body = it.body ?: throw McpException("empty response body")
            val contentType = it.header("Content-Type").orEmpty()
            if (contentType.contains("text/event-stream")) {
                readResponseFromSse(body.source(), payload.long("id") ?: -1L)
            } else {
                val text = body.string()
                parseJsonObject(text) ?: throw McpException("invalid JSON: ${text.take(300)}")
            }
        }
    }

    override suspend fun notify(payload: JsonObject) = withContext(Dispatchers.IO) {
        execute(payload).use { response ->
            if (!response.isSuccessful) {
                throw McpException("HTTP ${response.code} on notification")
            }
            runCatching { response.body?.string() }
        }
        Unit
    }

    private fun execute(payload: JsonObject) = http.newCall(buildRequest(payload)).execute()

    private fun buildRequest(payload: JsonObject): Request {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(MCP_JSON))
            .header("Accept", "application/json, text/event-stream")
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }

    private fun readResponseFromSse(source: BufferedSource, requestId: Long): JsonObject {
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: throw McpException("stream closed before a response arrived")
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    val parsed = parseJsonObject(data.toString())
                    data.setLength(0)
                    if (parsed != null && parsed.long("id") == requestId) return parsed
                }
                continue
            }
            if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
        }
    }

    override fun close() = Unit
}
