package dev.opencarl.skills

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val body: String,
    val builtin: Boolean,
    val updatedAt: Long,
)

/**
 * Markdown skills with YAML-ish frontmatter, in the same shape as the skills
 * used by OpenHands/Claude. The index goes into the system prompt; the agent
 * pulls the full text on demand through the use_skill tool.
 */
class SkillStore(private val context: Context, private val root: File) {

    private val dir = File(root, "skills")
    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    init {
        dir.mkdirs()
        installBundled()
        reload()
    }

    fun installBundled() {
        runCatching {
            val bundled = context.assets.list("skills").orEmpty()
            for (name in bundled) {
                val target = File(dir, name)
                if (target.exists()) continue
                val body = context.assets.open("skills/$name/SKILL.md").bufferedReader().use { it.readText() }
                target.mkdirs()
                File(target, "SKILL.md").writeText(body)
            }
        }
    }

    fun reload() {
        val loaded = mutableListOf<Skill>()
        dir.listFiles().orEmpty().forEach { entry ->
            val file = if (entry.isDirectory) File(entry, "SKILL.md") else entry.takeIf { it.name.endsWith(".md") }
            if (file == null || !file.exists()) return@forEach
            val parsed = parse(file.readText(), entry.nameWithoutExtension, builtin = false, updatedAt = file.lastModified())
            loaded.add(parsed)
        }
        _skills.value = loaded.sortedBy { it.name.lowercase() }
    }

    fun find(key: String): Skill? {
        val needle = key.trim().lowercase()
        return _skills.value.firstOrNull { it.id.lowercase() == needle || it.name.lowercase() == needle }
            ?: _skills.value.firstOrNull { it.name.lowercase().contains(needle) || it.id.lowercase().contains(needle) }
    }

    fun indexForPrompt(): String =
        _skills.value.joinToString("\n") { "- ${it.id}: ${it.name} — ${it.description}" }

    fun importText(content: String, fileName: String): Skill {
        val id = fileName.removeSuffix(".md").ifBlank { "skill-" + UUID.randomUUID().toString().take(6) }
        val target = File(dir, id)
        target.mkdirs()
        File(target, "SKILL.md").writeText(content)
        reload()
        return parse(content, id, builtin = false, updatedAt = System.currentTimeMillis())
    }

    suspend fun importUri(uri: Uri): Skill? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext null
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.md"
            importText(text, name)
        }.getOrNull()
    }

    fun delete(skill: Skill) {
        val target = File(dir, skill.id)
        if (target.isDirectory) target.deleteRecursively() else File(dir, "${skill.id}.md").delete()
        reload()
    }

    private fun parse(raw: String, fallbackId: String, builtin: Boolean, updatedAt: Long): Skill {
        var name = fallbackId
        var description = ""
        var body = raw.trim()
        if (raw.startsWith("---")) {
            val end = raw.indexOf("\n---", startIndex = 3)
            if (end > 0) {
                val header = raw.substring(3, end).trim()
                body = raw.substring(end + 4).trim()
                header.lines().forEach { line ->
                    val key = line.substringBefore(':', "").trim().lowercase()
                    val value = line.substringAfter(':', "").trim().trim('"', '\'')
                    when (key) {
                        "name" -> if (value.isNotEmpty()) name = value
                        "description" -> description = value
                    }
                }
            }
        }
        if (description.isEmpty()) {
            description = body.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim().orEmpty()
        }
        return Skill(
            id = fallbackId,
            name = name,
            description = description.take(240),
            body = body,
            builtin = builtin,
            updatedAt = updatedAt,
        )
    }
}
