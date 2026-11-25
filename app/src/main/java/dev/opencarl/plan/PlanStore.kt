package dev.opencarl.plan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class PlanItem(
    val id: Long,
    val text: String,
    val status: String = "todo",
    val tag: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
) {
    val dueText: String
        get() = dueAt?.let { SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date(it)) } ?: ""
}

/** Persistent task list that the agent and the user both work from. */
class PlanStore(private val fileProvider: () -> File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file: File get() = fileProvider()

    private val _items = MutableStateFlow<List<PlanItem>>(emptyList())
    val items: StateFlow<List<PlanItem>> = _items.asStateFlow()

    fun load() {
        val target = file
        if (!target.exists()) return
        _items.value = runCatching {
            json.decodeFromString<List<PlanItem>>(target.readText())
        }.getOrDefault(emptyList())
    }

    fun add(text: String, tag: String = "", dueAt: Long? = null): PlanItem {
        val item = PlanItem(
            id = (_items.value.maxOfOrNull { it.id } ?: 0L) + 1,
            text = text.trim(),
            tag = tag,
            dueAt = dueAt,
        )
        _items.value = _items.value + item
        persist()
        return item
    }

    fun setStatus(id: Long, status: String): Boolean {
        var changed = false
        _items.value = _items.value.map {
            if (it.id == id) {
                changed = true
                it.copy(status = status)
            } else {
                it
            }
        }
        if (changed) persist()
        return changed
    }

    fun remove(id: Long): Boolean {
        val next = _items.value.filterNot { it.id == id }
        val removed = next.size != _items.value.size
        _items.value = next
        if (removed) persist()
        return removed
    }

    fun clearDone(): Int {
        val next = _items.value.filterNot { it.status == "done" }
        val removed = _items.value.size - next.size
        _items.value = next
        persist()
        return removed
    }

    fun summary(): String {
        if (_items.value.isEmpty()) return "no tasks"
        return _items.value.joinToString("\n") { item ->
            val mark = when (item.status) {
                "done" -> "[x]"
                "doing" -> "[~]"
                else -> "[ ]"
            }
            "$mark #${item.id} ${item.text}" +
                (if (item.tag.isNotBlank()) " (${item.tag})" else "") +
                (if (item.dueAt != null) " due ${item.dueText}" else "")
        }
    }

    private fun persist() {
        val target = file
        target.parentFile?.mkdirs()
        runCatching { target.writeText(json.encodeToString(_items.value)) }
    }
}
