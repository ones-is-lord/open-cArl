package dev.opencarl.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import dev.opencarl.ai.arr
import dev.opencarl.ai.boolean
import dev.opencarl.ai.long
import dev.opencarl.ai.obj
import dev.opencarl.ai.parseJsonObject
import dev.opencarl.ai.str
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

/** Parses one SSE event. Returns null at end of stream. */
internal fun BufferedSource.readSseEvent(): Pair<String?, String>? {
    var event: String? = null
    val data = StringBuilder()
    while (true) {
        val line = readUtf8Line()
        if (line == null) {
            return if (data.isEmpty()) null else event to data.toString()
        }
        if (line.isEmpty()) {
            if (data.isNotEmpty() || event != null) return event to data.toString()
            continue
        }
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
        }
    }
}

/**
 * Legacy HTTP+SSE transport: a GET stream delivers messages, a separate POST
 * endpoint accepts them. Still used by a number of hosted MCP servers.
 */
class SseMcpTransport(
    private val http: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>,
) : McpTransport {

    private val incoming = Channel<JsonObject>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var postUrl: String? = null
    private var call: Call? = null
    private var reader: Job? = null

    override suspend fun start() = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("Accept", "text/event-stream")
        headers.forEach { (key, value) -> builder.header(key, value) }
        val streamCall = http.newCall(builder.build())
        call = streamCall
        val response = streamCall.execute()
        if (!response.isSuccessful) throw McpException("SSE connect failed: HTTP ${response.code}")
        val source = response.body?.source() ?: throw McpException("empty SSE body")
        val endpoint = withTimeout(20_000) {
            while (true) {
                val event = source.readSseEvent() ?: throw McpException("stream closed before endpoint event")
                if (event.first == "endpoint") return@withTimeout event.second.trim()
            }
            @Suppress("UNREACHABLE_CODE") ""
        }
        postUrl = resolveUrl(url, endpoint)
        reader = scope.launch { readLoop(source) }
    }

    private suspend fun readLoop(source: BufferedSource) {
        try {
            while (true) {
                val event = source.readSseEvent() ?: break
                val type = event.first
                if (type != null && type != "message") continue
                val parsed = parseJsonObject(event.second) ?: continue
                incoming.send(parsed)
            }
        } catch (_: Throwable) {
            // stream closed
        } finally {
            incoming.close()
        }
    }

    override suspend fun request(payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val target = postUrl ?: throw McpException("SSE transport not started")
        post(target, payload)
        val id = payload.long("id") ?: -1L
        withTimeout(120_000) {
            while (true) {
                val message = incoming.receive()
                if (message.long("id") == id) return@withTimeout message
            }
            @Suppress("UNREACHABLE_CODE") JsonObject(emptyMap())
        }
    }

    override suspend fun notify(payload: JsonObject) = withContext(Dispatchers.IO) {
        val target = postUrl ?: throw McpException("SSE transport not started")
        post(target, payload)
        Unit
    }

    private fun post(target: String, payload: JsonObject) {
        val builder = Request.Builder()
            .url(target)
            .post(payload.toString().toRequestBody(MCP_JSON))
        headers.forEach { (key, value) -> builder.header(key, value) }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw McpException("HTTP ${response.code} on message post")
            runCatching { response.body?.string() }
        }
    }

    override fun close() {
        runCatching { call?.cancel() }
        reader?.cancel()
        incoming.close()
    }
}
