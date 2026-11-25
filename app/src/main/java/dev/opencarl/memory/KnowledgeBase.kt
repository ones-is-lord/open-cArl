package dev.opencarl.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Knowledge packs: point at a folder of notes, docs or code and it is chunked
 * into the memory store with provenance tags, so recall can cite the file.
 */
class KnowledgeBase(
    private val memory: MemoryStore,
    private val roots: () -> List<File>,
) {

    private val extensions = setOf("md", "markdown", "txt", "rst", "org", "kt", "kts", "java", "py", "js", "ts", "sh", "json", "yaml", "yml", "toml", "csv")

    suspend fun import(path: String, pack: String, maxFiles: Int = 400): String = withContext(Dispatchers.IO) {
        val target = resolve(path) ?: return@withContext "no such path: $path"
        val name = pack.ifBlank { target.name }
        val files = if (target.isFile) listOf(target) else target.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions && it.length() < 2_000_000 }
            .take(maxFiles)
            .toList()
        if (files.isEmpty()) return@withContext "nothing readable in $path"

        var chunks = 0
        var chars = 0
        files.forEach { file ->
            val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
            val relative = file.absolutePath.removePrefix(target.absolutePath).trimStart('/').ifEmpty { file.name }
            chunk(text).forEachIndexed { index, part ->
                memory.add(
                    text = "[$name/$relative#$index] $part",
                    kind = "doc",
                    tags = listOf("kb", name, relative),
                    importance = 3,
                )
                chunks++
                chars += part.length
            }
        }
        "imported $name: ${files.size} files, $chunks chunks, ${chars / 1024}KB indexed"
    }

    fun packs(): List<String> = memory.entries.value
        .flatMap { it.tags }
        .filter { it != "kb" }
        .groupingBy { it }
        .eachCount()
        .filterKeys { key -> memory.entries.value.any { "kb" in it.tags && key in it.tags } }
        .entries
        .sortedByDescending { it.value }
        .take(40)
        .map { "${it.key} (${it.value})" }

    fun search(query: String, limit: Int = 8): List<MemoryEntry> =
        memory.search(query, limit * 3).filter { "kb" in it.tags }.take(limit)

    suspend fun remove(pack: String): Int = memory.removeWhere { "kb" in it.tags && pack in it.tags }

    fun stats(): String {
        val docs = memory.entries.value.filter { "kb" in it.tags }
        if (docs.isEmpty()) return "no knowledge packs imported"
        val packs = docs.flatMap { it.tags }.filter { it != "kb" }.toSet().size
        val chars = docs.sumOf { it.text.length }
        return "${docs.size} chunks, $packs tags, ${chars / 1024}KB indexed"
    }

    private fun resolve(path: String): File? {
        val candidates = mutableListOf<File>()
        roots().forEach { candidates += File(it, path) }
        candidates += File(path)
        return candidates.firstOrNull { it.exists() }
    }

    /** Paragraph-aware chunking with a small overlap so context survives the cut. */
    private fun chunk(text: String, size: Int = 900, overlap: Int = 120): List<String> {
        val clean = text.replace("\r\n", "\n").trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= size) return listOf(clean)
        val out = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = (start + size).coerceAtMost(clean.length)
            if (end < clean.length) {
                val paragraph = clean.lastIndexOf("\n\n", end)
                val line = clean.lastIndexOf('\n', end)
                end = when {
                    paragraph > start + size / 2 -> paragraph
                    line > start + size / 2 -> line
                    else -> end
                }
            }
            out += clean.substring(start, end).trim()
            if (end >= clean.length) break
            start = (end - overlap).coerceAtLeast(start + 1)
        }
        return out.filter { it.isNotBlank() }
    }
}
