package dev.opencarl.memory

import dev.opencarl.ai.JsonLenient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

@Serializable
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val seq: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: String = "note",
    val text: String,
    val tags: List<String> = emptyList(),
    val importance: Int = 2,
)

/**
 * Append-only sequential memory: every entry keeps its position, so the agent
 * can reason about what it learned first and what changed later.
 */
class MemoryStore(private val fileProvider: () -> File) {

    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val entries: StateFlow<List<MemoryEntry>> = _entries.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val file = fileProvider()
        if (!file.exists()) return@withContext
        val loaded = runCatching {
            JsonLenient.decodeFromString<List<MemoryEntry>>(file.readText())
        }.getOrDefault(emptyList())
        _entries.value = loaded.sortedBy { it.seq }
    }

    suspend fun add(
        text: String,
        kind: String = "note",
        tags: List<String> = emptyList(),
        importance: Int = 2,
    ): MemoryEntry = mutex.withLock {
        val current = _entries.value
        val entry = MemoryEntry(
            seq = (current.maxOfOrNull { it.seq } ?: 0L) + 1L,
            kind = kind,
            text = text.trim(),
            tags = tags,
            importance = importance.coerceIn(1, 5),
        )
        val updated = current + entry
        _entries.value = updated
        persist(updated)
        entry
    }

    suspend fun update(id: String, text: String, importance: Int?) = mutex.withLock {
        val updated = _entries.value.map { entry ->
            if (entry.id != id) entry
            else entry.copy(text = text, importance = importance?.coerceIn(1, 5) ?: entry.importance)
        }
        _entries.value = updated
        persist(updated)
    }

    suspend fun delete(id: String) = mutex.withLock {
        val updated = _entries.value.filterNot { it.id == id }
        _entries.value = updated
        persist(updated)
    }

    /** Removes entries matching [predicate]; returns how many went. */
    suspend fun removeWhere(predicate: (MemoryEntry) -> Boolean): Int = mutex.withLock {
        val current = _entries.value
        val kept = current.filterNot(predicate)
        if (kept.size == current.size) return@withLock 0
        _entries.value = kept
        persist(kept)
        current.size - kept.size
    }

    suspend fun clear() = mutex.withLock {
        _entries.value = emptyList()
        persist(emptyList())
    }

    fun search(query: String, limit: Int = 10): List<MemoryEntry> {
        val all = _entries.value
        if (query.isBlank()) return all.takeLast(limit).reversed()
        val terms = tokenize(query)
        if (terms.isEmpty()) return all.takeLast(limit).reversed()
        return all.map { entry -> entry to score(entry, terms) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryEntry, Int>> { it.second }.thenByDescending { it.first.seq })
            .take(limit)
            .map { it.first }
    }

    /** Sequential digest for the system prompt: oldest first, newest kept if the budget runs out. */
    fun digest(maxChars: Int = 1800): String {
        val all = _entries.value
        if (all.isEmpty()) return ""
        val lines = all.map { entry ->
            val tags = if (entry.tags.isEmpty()) "" else " (" + entry.tags.joinToString(", ") + ")"
            "- [${entry.kind}] ${entry.text}$tags"
        }
        val selected = mutableListOf<String>()
        var budget = maxChars
        for (line in lines.asReversed()) {
            if (line.length + 1 > budget) break
            selected.add(0, line)
            budget -= line.length + 1
        }
        return selected.joinToString("\n")
    }

    private fun score(entry: MemoryEntry, terms: List<String>): Int {
        val haystack = (entry.text + " " + entry.tags.joinToString(" ")).lowercase()
        var score = 0
        terms.forEach { term ->
            if (haystack.contains(term)) score += 3
            if (entry.tags.any { it.equals(term, ignoreCase = true) }) score += 2
        }
        if (score == 0) return 0
        score += entry.importance
        val ageDays = ((System.currentTimeMillis() - entry.createdAt) / 86_400_000L).toInt()
        score += (10 - ageDays.coerceIn(0, 10)) / 4
        return score
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9_]+")).filter { it.length > 1 }.distinct()

    private fun persist(entries: List<MemoryEntry>) {
        runCatching {
            val file = fileProvider()
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JsonLenient.encodeToString(entries))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }
}
