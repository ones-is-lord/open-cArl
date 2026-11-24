package dev.opencarl.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType

internal val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
internal val JsonLenient = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

internal fun joinUrl(base: String, path: String): String =
    base.trimEnd('/') + "/" + path.trimStart('/')

internal fun JsonObject.str(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject.int(key: String): Int? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

internal fun JsonObject.long(key: String): Long? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.takeIf { it is JsonObject }?.jsonObject

internal fun JsonObject.arr(key: String): JsonArray? =
    this[key]?.takeIf { it is JsonArray }?.jsonArray

internal fun parseJsonObject(raw: String?): JsonObject? =
    runCatching { JsonLenient.parseToJsonElement(raw ?: return null).jsonObject }.getOrNull()

internal fun parseArgs(raw: String): JsonObject =
    runCatching { JsonLenient.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject }
        .getOrElse { buildJsonObject { } }

internal fun jsonString(value: String): JsonElement = JsonPrimitive(value)
