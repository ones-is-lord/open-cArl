package dev.opencarl.privilege

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class AuditEntry(
    val at: Long = System.currentTimeMillis(),
    val level: String,
    val command: String,
    val exitCode: Int,
    val durationMs: Long,
    val output: String,
    val note: String = "",
) {
    val time: String
        get() = SimpleDateFormat("MMM d HH:mm:ss", Locale.US).format(Date(at))
}

/**
 * Every privileged action lands here: what ran, at which level, what came back.
 * Append-only JSONL so the file stays readable and exportable.
 */
class AuditLog(private val fileProvider: () -> File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file: File get() = fileProvider()

    private val _entries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    fun load() {
        val target = file
        if (!target.exists()) return
        val loaded = target.readLines()
            .mapNotNull { line ->
                runCatching { json.decodeFromString<AuditEntry>(line) }.getOrNull()
            }
            .takeLast(MAX_KEEP)
        _entries.value = loaded
    }

    fun record(entry: AuditEntry) {
        _entries.value = (_entries.value + entry).takeLast(MAX_KEEP)
        val target = file
        target.parentFile?.mkdirs()
        runCatching { target.appendText(json.encodeToString(AuditEntry.serializer(), entry) + "\n") }
    }

    fun clear() {
        _entries.value = emptyList()
        runCatching { file.delete() }
    }

    fun exportTo(target: File): File {
        target.parentFile?.mkdirs()
        target.writeText(_entries.value.joinToString("\n") { json.encodeToString(AuditEntry.serializer(), it) })
        return target
    }

    companion object {
        const val MAX_KEEP = 500
    }
}
