package dev.opencarl.ai

import dev.opencarl.memory.MemoryStore
import dev.opencarl.runtime.CommandRunner
import dev.opencarl.runtime.Workspace
import dev.opencarl.skills.SkillStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

class BuiltinTools(
    private val runner: CommandRunner,
    private val workspace: Workspace,
    private val skills: SkillStore,
    private val memory: MemoryStore,
    private val http: OkHttpClient,
) {

    fun install(registry: ToolRegistry) {
        registry.register(bashTool(), ::runBash)
        registry.register(readFileTool(), ::runReadFile)
        registry.register(writeFileTool(), ::runWriteFile)
        registry.register(listDirTool(), ::runListDir)
        registry.register(skillTool(), ::runSkill)
        registry.register(listSkillsTool(), ::runListSkills)
        registry.register(rememberTool(), ::runRemember)
        registry.register(recallTool(), ::runRecall)
        registry.register(fetchTool(), ::runFetch)
    }

    private fun schema(required: List<String>, properties: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { properties() }
            putJsonArray("required") { required.forEach { add(it) } }
        }

    private fun text(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun number(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun bashTool() = ToolSpec(
        name = "bash",
        description = "Run a shell command in the persistent Linux shell. The working directory, " +
            "environment and installed packages persist between calls. Returns exit code, cwd and output.",
        parameters = schema(listOf("command")) {
            put("command", text("Shell command to run. Multi-line scripts are allowed."))
            put("timeout_seconds", number("Timeout in seconds (5-900, default 120)."))
        },
    )

    private suspend fun runBash(args: JsonObject): String {
        val command = args.str("command")?.trim().orEmpty()
        if (command.isEmpty()) return "Error: command is required"
        val timeout = ((args.int("timeout_seconds") ?: 120).coerceIn(5, 900)) * 1000L
        val result = runner.run(command, timeout)
        val output = result.output.takeLast(24_000)
        return buildString {
            append("exit=").append(result.exitCode)
            append("  cwd=").append(result.cwd ?: runner.cwd)
            append('\n')
            append(output.ifBlank { "(no output)" })
        }
    }

    private fun readFileTool() = ToolSpec(
        name = "read_file",
        description = "Read a UTF-8 text file from the workspace. Paths are relative to the workspace root.",
        parameters = schema(listOf("path")) {
            put("path", text("File path, relative or absolute inside the workspace."))
        },
    )

    private suspend fun runReadFile(args: JsonObject): String {
        val path = args.str("path") ?: return "Error: path is required"
        return workspace.read(path)
    }

    private fun writeFileTool() = ToolSpec(
        name = "write_file",
        description = "Create or overwrite a UTF-8 text file in the workspace. Parent directories are created.",
        parameters = schema(listOf("path", "content")) {
            put("path", text("File path inside the workspace."))
            put("content", text("Full file content to write."))
        },
    )

    private suspend fun runWriteFile(args: JsonObject): String {
        val path = args.str("path") ?: return "Error: path is required"
        val content = args.str("content") ?: return "Error: content is required"
        return workspace.write(path, content)
    }

    private fun listDirTool() = ToolSpec(
        name = "list_dir",
        description = "List files and directories in the workspace.",
        parameters = schema(listOf("path")) {
            put("path", text("Directory path. Use '.' for the workspace root."))
        },
    )

    private suspend fun runListDir(args: JsonObject): String {
        val path = args.str("path") ?: "."
        return workspace.list(path)
    }

    private fun skillTool() = ToolSpec(
        name = "use_skill",
        description = "Load the full instructions of a skill by id or name.",
        parameters = schema(listOf("name")) {
            put("name", text("Skill id or name, e.g. 'github-workflow'."))
        },
    )

    private suspend fun runSkill(args: JsonObject): String {
        val name = args.str("name") ?: return "Error: name is required"
        val skill = skills.find(name) ?: return "Error: no skill named '$name'. Available: " +
            skills.skills.value.joinToString(", ") { it.id }
        return "# ${skill.name}\n\n${skill.body}"
    }

    private fun listSkillsTool() = ToolSpec(
        name = "list_skills",
        description = "List the installed skills with a one-line description each.",
        parameters = schema(emptyList()) { },
    )

    private suspend fun runListSkills(args: JsonObject): String {
        val all = skills.skills.value
        if (all.isEmpty()) return "No skills installed."
        return all.joinToString("\n") { "- ${it.id}: ${it.name} — ${it.description}" }
    }

    private fun rememberTool() = ToolSpec(
        name = "remember",
        description = "Append an entry to sequential long-term memory. Use for durable preferences, " +
            "decisions, project facts and corrections. Never store secrets.",
        parameters = schema(listOf("text")) {
            put("text", text("The memory to store, one or two sentences."))
            put("kind", text("One of: note, fact, preference, decision, task, correction."))
            put("tags", text("Comma-separated tags."))
            put("importance", number("1-5, where 5 is critical."))
        },
    )

    private suspend fun runRemember(args: JsonObject): String {
        val text = args.str("text")?.trim().orEmpty()
        if (text.isEmpty()) return "Error: text is required"
        val tags = args.str("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val entry = memory.add(
            text = text,
            kind = args.str("kind") ?: "note",
            tags = tags,
            importance = args.int("importance") ?: 2,
        )
        return "Remembered (${entry.kind}, importance ${entry.importance})."
    }

    private fun recallTool() = ToolSpec(
        name = "recall",
        description = "Search long-term memory. Empty query returns the most recent entries.",
        parameters = schema(emptyList()) {
            put("query", text("Keywords to search for."))
            put("limit", number("Maximum entries to return, default 10."))
        },
    )

    private suspend fun runRecall(args: JsonObject): String {
        val limit = (args.int("limit") ?: 10).coerceIn(1, 50)
        val results = memory.search(args.str("query").orEmpty(), limit)
        if (results.isEmpty()) return "No matching memories."
        return results.joinToString("\n") { entry ->
            "- [${entry.kind}] ${entry.text}" + if (entry.tags.isNotEmpty()) " (${entry.tags.joinToString(", ")})" else ""
        }
    }

    private fun fetchTool() = ToolSpec(
        name = "fetch_url",
        description = "Fetch an http(s) URL and return readable text (HTML is converted to plain text).",
        parameters = schema(listOf("url")) {
            put("url", text("Absolute http(s) URL."))
        },
    )

    private suspend fun runFetch(args: JsonObject): String {
        val url = args.str("url") ?: return "Error: url is required"
        return HttpFetcher.fetch(http, url)
    }
}
