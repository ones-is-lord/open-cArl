package dev.opencarl.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lets the main agent hand a self-contained job to a sub-agent with its own
 * context and read back only the report. Depth is capped so a sub-agent cannot
 * spawn its way into a fork bomb.
 */
class Delegator(
    private val approver: suspend (ToolCall, String) -> Boolean,
    private val buildSubAgent: (suspend (ToolCall, String) -> Boolean, Int) -> Agent,
    private val maxDepth: Int = 1,
) {

    private val depth = AtomicInteger(0)

    fun install(registry: ToolRegistry) {
        registry.register(spec(), ::spawn)
    }

    private fun spec() = ToolSpec(
        name = "spawn",
        description = "Delegate a self-contained job to a sub-agent with a fresh context and get back a short report. Use it for long research, multi-file refactors or anything that would flood this conversation.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task") {
                    put("type", "string")
                    put("description", "The job, stated as if to a competent engineer who cannot ask questions.")
                }
                putJsonObject("steps") {
                    put("type", "string")
                    put("description", "Step budget for the sub-agent, default 12.")
                }
            }
            putJsonArray("required") { add("task") }
        },
    )

    private suspend fun spawn(args: JsonObject): String {
        val task = args.str("task")?.trim().orEmpty()
        if (task.isEmpty()) return "Error: task is required"
        val steps = args.int("steps")?.coerceIn(2, 30) ?: 12
        if (depth.get() >= maxDepth) return "Error: sub-agents cannot spawn sub-agents"
        depth.incrementAndGet()
        return try {
            val agent = buildSubAgent(approver, steps)
            val history = mutableListOf<LlmMessage>()
            val report = StringBuilder()
            agent.run(history, task).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> report.append(event.text)
                    is AgentEvent.Error -> report.append("\n[sub-agent error] ").append(event.message)
                    is AgentEvent.ToolDenied -> report.append("\n[sub-agent tool denied] ").append(event.call.name)
                    else -> Unit
                }
            }
            val text = report.toString().trim()
            if (text.isEmpty()) "sub-agent finished with no report" else "sub-agent report:\n" + text.take(8_000)
        } finally {
            depth.decrementAndGet()
        }
    }
}
