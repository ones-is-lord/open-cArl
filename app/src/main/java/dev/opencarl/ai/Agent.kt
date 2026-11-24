package dev.opencarl.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolRequested(val call: ToolCall, val description: String) : AgentEvent
    data class ToolDenied(val call: ToolCall) : AgentEvent
    data class ToolFinished(val call: ToolCall, val output: String) : AgentEvent
    data object Finished : AgentEvent
    data class Error(val message: String) : AgentEvent
}

class Agent(
    private val client: LlmClient,
    private val registry: ToolRegistry,
    private val maxSteps: Int,
    private val systemPrompt: () -> String,
    private val approver: suspend (ToolCall, String) -> Boolean,
    private val model: String,
    private val maxTokens: Int,
    private val temperature: Double,
) {

    fun run(history: MutableList<LlmMessage>, userText: String): Flow<AgentEvent> = flow {
        history.add(LlmMessage.User(userText))
        var step = 0
        while (step < maxSteps) {
            step++
            val request = LlmRequest(
                model = model,
                system = systemPrompt(),
                messages = history.toList(),
                tools = registry.specs,
                maxTokens = maxTokens,
                temperature = temperature,
            )
            val text = StringBuilder()
            val calls = sortedMapOf<Int, MutableCall>()
            var failure: String? = null
            client.stream(request).collect { event ->
                when (event) {
                    is LlmEvent.TextDelta -> {
                        text.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is LlmEvent.ToolCallStart -> calls.getOrPut(event.index) { MutableCall() }.apply {
                        if (event.id.isNotBlank()) id = event.id
                        if (event.name.isNotBlank()) name = event.name
                    }
                    is LlmEvent.ToolCallArgsDelta -> calls.getOrPut(event.index) { MutableCall() }.args.append(event.delta)
                    is LlmEvent.Failed -> failure = event.message
                    is LlmEvent.Completed -> Unit
                }
            }
            val error = failure
            if (error != null) {
                emit(AgentEvent.Error(error))
                return@flow
            }
            val toolCalls = calls.values
                .filter { it.name.isNotBlank() }
                .map { ToolCall(it.id.ifBlank { "call_${it.name}" }, it.name, it.args.toString().ifBlank { "{}" }) }
            history.add(LlmMessage.Assistant(text.toString(), toolCalls))
            if (toolCalls.isEmpty()) {
                emit(AgentEvent.Finished)
                return@flow
            }
            for (call in toolCalls) {
                val description = registry.descriptionOf(call.name)
                emit(AgentEvent.ToolRequested(call, description))
                if (!approver(call, description)) {
                    history.add(LlmMessage.ToolResult(call.id, call.name, "The user denied this action.", true))
                    emit(AgentEvent.ToolDenied(call))
                    continue
                }
                val output = registry.invoke(call.name, parseArgs(call.arguments))
                history.add(LlmMessage.ToolResult(call.id, call.name, output, output.startsWith("Error")))
                emit(AgentEvent.ToolFinished(call, output))
            }
        }
        emit(AgentEvent.Error("Stopped after $maxSteps steps without a final answer."))
    }

    private class MutableCall {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
    }
}
