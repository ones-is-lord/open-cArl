package dev.opencarl.mcp

import dev.opencarl.ai.ToolRegistry
import dev.opencarl.ai.ToolSpec
import dev.opencarl.data.McpServerConfig
import dev.opencarl.data.McpTransportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient

class McpManager(
    private val http: OkHttpClient,
    private val workDirProvider: () -> String? = { null },
    private val wrapCommand: (List<String>) -> List<String> = { it },
) {

    private val _states = MutableStateFlow<List<McpServerState>>(emptyList())
    val states: StateFlow<List<McpServerState>> = _states.asStateFlow()

    private val clients = mutableMapOf<String, McpClient>()
    private val placeholder = Regex("\\{\\{([A-Z0-9_]+)\\}\\}")

    suspend fun connectAll(
        servers: List<McpServerConfig>,
        secrets: Map<String, String>,
    ): List<McpServerState> = coroutineScope {
        shutdown()
        val enabled = servers.filter { it.enabled }
        _states.value = enabled.map { McpServerState(it.id, it.name, McpStatus.CONNECTING) }
        enabled.map { config -> async { connectOne(config, secrets) } }.awaitAll()
    }

    private suspend fun connectOne(config: McpServerConfig, secrets: Map<String, String>): McpServerState {
        val name = config.name.ifBlank { config.id }
        val state = try {
            val client = McpClient(config.id, name, buildTransport(config, secrets))
            val tools = client.connect()
            synchronized(clients) { clients[config.id] = client }
            McpServerState(config.id, name, McpStatus.CONNECTED, tools.size, client.serverInfo)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            McpServerState(config.id, name, McpStatus.ERROR, 0, t.message ?: t.javaClass.simpleName)
        }
        _states.update { current -> current.map { if (it.id == state.id) state else it } }
        return state
    }

    fun registerTools(registry: ToolRegistry) {
        val snapshot = synchronized(clients) { clients.toMap() }
        snapshot.forEach { (serverId, client) ->
            val prefix = "mcp__" + sanitize(serverId) + "__"
            registry.removePrefix(prefix)
            client.tools.forEach { tool ->
                val spec = ToolSpec(
                    name = prefix + tool.name,
                    description = tool.description.ifBlank { "MCP tool from ${client.serverInfo}" },
                    parameters = tool.inputSchema,
                )
                registry.register(spec) { args -> client.callTool(tool.name, args) }
            }
        }
    }

    suspend fun test(config: McpServerConfig, secrets: Map<String, String>): String = try {
        McpClient(config.id, config.name, buildTransport(config, secrets)).use { client ->
            val tools = client.connect()
            buildString {
                append("Connected to ").append(client.serverInfo)
                append(" (").append(tools.size).append(" tools)\n")
                tools.take(40).forEach { append("  - ").append(it.name).append('\n') }
            }.trimEnd()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        "Failed: " + (t.message ?: t.javaClass.simpleName)
    }

    fun shutdown() {
        val snapshot = synchronized(clients) {
            val copy = clients.toMap()
            clients.clear()
            copy
        }
        snapshot.values.forEach { runCatching { it.close() } }
        _states.update { current -> current.map { it.copy(status = McpStatus.DISCONNECTED, toolCount = 0) } }
    }

    private fun buildTransport(config: McpServerConfig, secrets: Map<String, String>): McpTransport {
        val headers = config.headers.mapValues { (_, value) -> substitute(value, secrets) }
        return when (config.transport) {
            McpTransportKind.HTTP -> HttpMcpTransport(http, substitute(config.url, secrets), headers)
            McpTransportKind.SSE -> SseMcpTransport(http, substitute(config.url, secrets), headers)
            McpTransportKind.STDIO -> {
                val command = wrapCommand(listOf(config.command) + config.args)
                val env = config.env.mapValues { (_, value) -> substitute(value, secrets) } +
                    ("PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin")
                StdioMcpTransport(command, env, workDirProvider())
            }
        }
    }

    private fun substitute(value: String, secrets: Map<String, String>): String =
        placeholder.replace(value) { match -> secrets[match.groupValues[1]].orEmpty() }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_]"), "_")
}
