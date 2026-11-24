package dev.opencarl.ai

import dev.opencarl.data.ProviderConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
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

/** Gemini generateContent with streaming function calling. */
class GeminiClient(
    private val http: OkHttpClient,
    private val config: ProviderConfig,
    private val apiKey: String,
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmEvent> = callbackFlow {
        val payload = buildJsonObject {
            if (request.system.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", request.system) }
                    }
                }
            }
            put("contents", geminiContents(request.messages))
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            request.tools.forEach { tool ->
                                addJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.parameters)
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
            }
        }

        var url = joinUrl(config.baseUrl, "models/" + request.model + ":streamGenerateContent?alt=sse")
        if (apiKey.isNotBlank()) url += "&key=" + apiKey
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
        config.headers.forEach { (key, value) -> builder.header(key, value) }

        var toolIndex = 0
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                val chunk = parseJsonObject(data) ?: return
                chunk.obj("promptFeedback")?.str("blockReason")?.let { reason ->
                    trySend(LlmEvent.Failed("Gemini blocked the prompt: $reason"))
                    close()
                    return
                }
                chunk.obj("error")?.let { error ->
                    trySend(LlmEvent.Failed(error.str("message") ?: data))
                    close()
                    return
                }
                val candidate = chunk.arr("candidates")?.firstOrNull()?.jsonObject ?: return
                candidate.obj("content")?.arr("parts")?.forEach { element ->
                    val part = element.jsonObject
                    part.str("text")?.takeIf { it.isNotEmpty() }?.let { trySend(LlmEvent.TextDelta(it)) }
                    val functionCall = part.obj("functionCall") ?: return@forEach
                    val name = functionCall.str("name") ?: return@forEach
                    val args = functionCall["args"]?.toString() ?: "{}"
                    val index = toolIndex++
                    trySend(LlmEvent.ToolCallStart(index, "gemini_$index", name))
                    trySend(LlmEvent.ToolCallArgsDelta(index, args))
                }
                candidate.str("finishReason")?.let { reason ->
                    if (reason != "STOP") trySend(LlmEvent.Completed(reason))
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

    private fun geminiContents(messages: List<LlmMessage>): JsonArray {
        val blocks = mutableListOf<Pair<String, JsonArray>>()
        messages.forEach { message ->
            when (message) {
                is LlmMessage.User -> blocks.add(
                    "user" to buildJsonArray {
                        addJsonObject { put("text", message.text) }
                    },
                )
                is LlmMessage.Assistant -> {
                    val parts = buildJsonArray {
                        if (message.text.isNotBlank()) {
                            addJsonObject { put("text", message.text) }
                        }
                        message.toolCalls.forEach { call ->
                            addJsonObject {
                                putJsonObject("functionCall") {
                                    put("name", call.name)
                                    put("args", parseArgs(call.arguments))
                                }
                            }
                        }
                    }
                    if (parts.isNotEmpty()) blocks.add("model" to parts)
                }
                is LlmMessage.ToolResult -> blocks.add(
                    "user" to buildJsonArray {
                        addJsonObject {
                            putJsonObject("functionResponse") {
                                put("name", message.name)
                                putJsonObject("response") {
                                    put("content", message.content)
                                }
                            }
                        }
                    },
                )
            }
        }

        val merged = mutableListOf<Pair<String, JsonArray>>()
        blocks.forEach { (role, parts) ->
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                val combined = buildJsonArray {
                    last.second.forEach { add(it) }
                    parts.forEach { add(it) }
                }
                merged[merged.size - 1] = role to combined
            } else {
                merged.add(role to parts)
            }
        }

        return buildJsonArray {
            merged.forEach { (role, parts) ->
                addJsonObject {
                    put("role", role)
                    put("parts", parts)
                }
            }
        }
    }
}
