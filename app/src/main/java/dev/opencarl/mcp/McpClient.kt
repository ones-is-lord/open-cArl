package dev.opencarl.mcp

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import dev.opencarl.ai.arr
import dev.opencarl.ai.boolean
import dev.opencarl.ai.long
import dev.opencarl.ai.obj
import dev.opencarl.ai.parseJsonObject
import dev.opencarl.ai.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class McpClient(
    val serverId: String,
    val displayName: String,
    private val transport: McpTransport,
) : AutoCloseable {

    private val mutex = Mutex()
    private var nextId = 1L

    var tools: List<McpTool> = emptyList()
        private set
    var serverInfo: String = displayName
        private set
    var protocolVersion: String = ""
        private set

    suspend fun connect(): List<McpTool> {
        transport.start()
        val init = rpc(
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") { }
                putJsonObject("clientInfo") {
                    put("name", "open cArl")
                    put("version", "0.1.0")
                }
            },
        )
        serverInfo = init.obj("serverInfo")?.str("name") ?: displayName
        protocolVersion = init.str("protocolVersion").orEmpty()
        runCatching { transport.notify(notification("notifications/initialized")) }
        tools = fetchTools()
        return tools
    }

    private suspend fun fetchTools(): List<McpTool> {
        val collected = mutableListOf<McpTool>()
        var cursor: String? = null
        do {
            val params = buildJsonObject { if (cursor != null) put("cursor", cursor!!) }
            val result = rpc("tools/list", params)
            result.arr("tools")?.forEach { element ->
                val tool = element.jsonObject
                val name = tool.str("name") ?: return@forEach
                collected.add(
                    McpTool(
                        name = name,
                        description = tool.str("description").orEmpty(),
                        inputSchema = tool.obj("inputSchema") ?: JsonObject(emptyMap()),
                    ),
                )
            }
            cursor = result.str("nextCursor")
        } while (!cursor.isNullOrBlank())
        return collected
    }

    suspend fun callTool(name: String, arguments: JsonObject): String {
        val result = rpc(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
        )
        val text = result.arr("content")?.joinToString("\n") { element ->
            val item = element.jsonObject
            when (item.str("type")) {
                "text" -> item.str("text").orEmpty()
                "image" -> "[image ${item.str("mimeType").orEmpty()}]"
                "resource" -> item.obj("resource")?.str("text").orEmpty()
                else -> item.toString()
            }
        }.orEmpty().ifBlank { result.toString() }
        return if (result.boolean("isError") == true) "Error: $text" else text
    }

    private suspend fun rpc(method: String, params: JsonObject): JsonObject = mutex.withLock {
        val id = nextId++
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val response = transport.request(payload)
        response.obj("error")?.let { error ->
            throw McpException(error.str("message") ?: "JSON-RPC error in $method")
        }
        response.obj("result") ?: JsonObject(emptyMap())
    }

    private fun notification(method: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", method)
    }

    override fun close() = transport.close()
}
