package dev.opencarl.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

sealed interface LlmMessage {
    data class User(val text: String) : LlmMessage
    data class Assistant(
        val text: String,
        val toolCalls: List<ToolCall> = emptyList(),
    ) : LlmMessage
    data class ToolResult(
        val callId: String,
        val name: String,
        val content: String,
        val isError: Boolean = false,
    ) : LlmMessage
}

data class ToolCall(val id: String, val name: String, val arguments: String)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class LlmRequest(
    val model: String,
    val system: String,
    val messages: List<LlmMessage>,
    val tools: List<ToolSpec>,
    val maxTokens: Int,
    val temperature: Double,
)

sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent
    data class ToolCallStart(val index: Int, val id: String, val name: String) : LlmEvent
    data class ToolCallArgsDelta(val index: Int, val delta: String) : LlmEvent
    data class Completed(val finishReason: String?) : LlmEvent
    data class Failed(val message: String) : LlmEvent
}

interface LlmClient {
    fun stream(request: LlmRequest): Flow<LlmEvent>
}
