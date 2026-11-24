package dev.opencarl.mcp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

/**
 * stdio transport: spawns the server process (inside the Linux userland) and
 * exchanges newline-delimited JSON-RPC over stdin/stdout.
 */
class StdioMcpTransport(
    private val command: List<String>,
    private val env: Map<String, String>,
    private val workDir: String?,
) : McpTransport {

    private val incoming = Channel<JsonObject>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    override suspend fun start() = withContext(Dispatchers.IO) {
        require(command.isNotEmpty()) { "stdio MCP server needs a command" }
        val builder = ProcessBuilder(command)
        if (!workDir.isNullOrBlank()) builder.directory(File(workDir))
        builder.environment().putAll(env)
        val p = builder.start()
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
        scope.launch { readLoop(p) }
        scope.launch {
            runCatching {
                p.errorStream.bufferedReader().forEachLine { line ->
                    if (line.isNotBlank()) Log.w("McpStdio", line.take(500))
                }
            }
        }
        Unit
    }

    private suspend fun readLoop(p: Process) {
        try {
            p.inputStream.bufferedReader().forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                parseJsonObject(line)?.let { incoming.trySend(it) }
            }
        } catch (t: Throwable) {
            Log.w("McpStdio", "reader stopped: ${t.message}")
        } finally {
            incoming.close()
        }
    }

    override suspend fun request(payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        write(payload)
        val id = payload.long("id") ?: -1L
        withTimeout(180_000) {
            while (true) {
                val message = incoming.receive()
                if (message.long("id") == id) return@withTimeout message
            }
            @Suppress("UNREACHABLE_CODE") JsonObject(emptyMap())
        }
    }

    override suspend fun notify(payload: JsonObject) = withContext(Dispatchers.IO) {
        write(payload)
        Unit
    }

    private fun write(payload: JsonObject) {
        val out = writer ?: throw McpException("stdio transport not started")
        synchronized(out) {
            out.write(payload.toString())
            out.newLine()
            out.flush()
        }
    }

    override fun close() {
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        incoming.close()
        scope.cancel()
    }
}
