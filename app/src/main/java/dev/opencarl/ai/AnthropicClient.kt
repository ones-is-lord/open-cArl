package dev.opencarl.ai

import dev.opencarl.data.ProviderConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

/** Anthropic Messages API with streaming tool use. */
class AnthropicClient(
    private val http: OkHttpClient,
    private val config: ProviderConfig,
    private val apiKey: String,
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("stream", true)
            if (request.system.isNotBlank()) put("system", request.system)
            put("messages", anthropicMessages(request.messages))
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        }
                    }
                }
            }
        }

        val builder = Request.Builder()
            .url(joinUrl(config.baseUrl, "messages"))
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .header("anthropic-version", "2023-06-01")
        if (apiKey.isNotBlank()) builder.header("x-api-key", apiKey)
        config.headers.forEach { (key, value) -> builder.header(key, value) }

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                val chunk = parseJsonObject(data) ?: return
                when (chunk.str("type")) {
                    "content_block_start" -> {
                        val block = chunk.obj("content_block")
                        if (block?.str("type") == "tool_use") {
                            trySend(
                                LlmEvent.ToolCallStart(
                                    index = chunk.int("index") ?: 0,
                                    id = block.str("id") ?: "tool_0",
                                    name = block.str("name") ?: "",
                                ),
                            )
                        }
                    }
                    "content_block_delta" -> {
                        val delta = chunk.obj("delta") ?: return
                        when (delta.str("type")) {
                            "text_delta" -> delta.str("text")?.let { trySend(LlmEvent.TextDelta(it)) }
                            "input_json_delta" -> delta.str("partial_json")?.let {
                                trySend(LlmEvent.ToolCallArgsDelta(chunk.int("index") ?: 0, it))
                            }
                        }
                    }
                    "message_delta" -> {
                        val reason = chunk.obj("delta")?.str("stop_reason")
                        trySend(LlmEvent.Completed(reason))
                    }
                    "message_stop" -> close()
                    "error" -> {
                        trySend(LlmEvent.Failed(chunk.obj("error")?.str("message") ?: data))
                        close()
                    }
                }
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

    private fun anthropicMessages(messages: List<LlmMessage>): JsonArray {
        val blocks = mutableListOf<Pair<String, JsonArray>>()
        messages.forEach { message ->
            when (message) {
                is LlmMessage.User -> blocks.add("user" to textBlocks(message.text))
                is LlmMessage.Assistant -> {
                    val content = buildJsonArray {
                        if (message.text.isNotBlank()) {
                            addJsonObject {
                                put("type", "text")
                                put("text", message.text)
                            }
                        }
                        message.toolCalls.forEach { call ->
                            addJsonObject {
                                put("type", "tool_use")
                                put("id", call.id)
                                put("name", call.name)
                                put("input", parseArgs(call.arguments))
                            }
                        }
                    }
                    if (content.isNotEmpty()) blocks.add("assistant" to content)
                }
                is LlmMessage.ToolResult -> blocks.add(
                    "user" to buildJsonArray {
                        addJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", message.callId)
                            put("content", message.content)
                            put("is_error", message.isError)
                        }
                    },
                )
            }
        }

        val merged = mutableListOf<Pair<String, JsonArray>>()
        blocks.forEach { (role, content) ->
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                val combined = buildJsonArray {
                    last.second.forEach { add(it) }
                    content.forEach { add(it) }
                }
                merged[merged.size - 1] = role to combined
            } else {
                merged.add(role to content)
            }
        }

        return buildJsonArray {
            merged.forEach { (role, content) ->
                addJsonObject {
                    put("role", role)
                    put("content", content)
                }
            }
        }
    }

    private fun textBlocks(text: String): JsonArray = buildJsonArray {
        addJsonObject {
            put("type", "text")
            put("text", text)
        }
    }
}
