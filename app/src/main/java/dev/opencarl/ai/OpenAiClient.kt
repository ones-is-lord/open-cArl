package dev.opencarl.ai

import dev.opencarl.data.ProviderConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/** OpenAI /chat/completions streaming. Covers OpenRouter, Groq, DeepSeek, Ollama, llama.cpp, LM Studio. */
class OpenAiClient(
    private val http: OkHttpClient,
    private val config: ProviderConfig,
    private val apiKey: String,
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", openAiMessages(request.messages, request.system))
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
            }
        }

        val builder = Request.Builder()
            .url(joinUrl(config.baseUrl, "chat/completions"))
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        config.headers.forEach { (key, value) -> builder.header(key, value) }

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data == END_MARKER) {
                    close()
                    return
                }
                val parsed = parseJsonObject(data) ?: return
                parsed.str("error")?.let {
                    trySend(LlmEvent.Failed(it))
                    close()
                    return
                }
                parseChunk(parsed).forEach { event -> trySend(event) }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val detail = runCatching { response?.body?.string() }.getOrNull()
                val message = buildString {
                    append(t?.message ?: "stream failed")
                    if (response != null) append(" (HTTP ").append(response.code).append(')')
                    if (!detail.isNullOrBlank()) append(": ").append(detail.take(400))
                }
                trySend(LlmEvent.Failed(message))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(LlmEvent.Completed(null))
                close()
            }
        }

        val source = EventSources.createFactory(http).newEventSource(builder.build(), listener)
        awaitClose { source.cancel() }
    }

    private fun parseChunk(chunk: kotlinx.serialization.json.JsonObject): List<LlmEvent> {
        val events = mutableListOf<LlmEvent>()
        val choice = chunk.arr("choices")?.firstOrNull()?.let { element ->
            runCatching { element.jsonObject }.getOrNull()
        } ?: return events
        val delta = choice.obj("delta")
        delta?.str("content")?.takeIf { it.isNotEmpty() }?.let { events.add(LlmEvent.TextDelta(it)) }
        delta?.arr("tool_calls")?.forEach { element ->
            val call = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val index = call.int("index") ?: 0
            val id = call.str("id")
            val function = call.obj("function")
            val name = function?.str("name")
            if (!id.isNullOrBlank() || !name.isNullOrBlank()) {
                events.add(LlmEvent.ToolCallStart(index, id ?: "call_$index", name ?: ""))
            }
            val args = function?.str("arguments")
            if (!args.isNullOrEmpty()) events.add(LlmEvent.ToolCallArgsDelta(index, args))
        }
        choice.str("finish_reason")?.let { events.add(LlmEvent.Completed(it)) }
        return events
    }

    private fun openAiMessages(messages: List<LlmMessage>, system: String): kotlinx.serialization.json.JsonArray =
        buildJsonArray {
            if (system.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
            }
            messages.forEach { message ->
                when (message) {
                    is LlmMessage.User -> addJsonObject {
                        put("role", "user")
                        put("content", message.text)
                    }
                    is LlmMessage.Assistant -> addJsonObject {
                        put("role", "assistant")
                        put("content", message.text)
                        if (message.toolCalls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                message.toolCalls.forEach { call ->
                                    addJsonObject {
                                        put("id", call.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call.name)
                                            put("arguments", call.arguments.ifBlank { "{}" })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is LlmMessage.ToolResult -> addJsonObject {
                        put("role", "tool")
                        put("tool_call_id", message.callId)
                        put("content", message.content)
                    }
                }
            }
        }

    companion object {
        private const val END_MARKER = "[" + "DONE" + "]"
    }
}
