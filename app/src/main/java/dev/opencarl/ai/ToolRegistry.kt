package dev.opencarl.ai

import kotlinx.serialization.json.JsonObject

class ToolRegistry {

    private val tools = LinkedHashMap<String, RegisteredTool>()

    val specs: List<ToolSpec> get() = tools.values.map { it.spec }

    fun register(spec: ToolSpec, handler: suspend (JsonObject) -> String) {
        tools[spec.name] = RegisteredTool(spec, handler)
    }

    fun register(tool: RegisteredTool) {
        tools[tool.spec.name] = tool
    }

    fun contains(name: String): Boolean = tools.containsKey(name)

    fun removePrefix(prefix: String) {
        tools.keys.filter { it.startsWith(prefix) }.forEach { tools.remove(it) }
    }

    fun descriptionOf(name: String): String = tools[name]?.spec?.description.orEmpty()

    suspend fun invoke(name: String, arguments: JsonObject): String {
        val tool = tools[name] ?: return "Error: unknown tool '$name'"
        return try {
            tool.handler(arguments)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            "Error: " + (t.message ?: t.javaClass.simpleName)
        }
    }
}

data class RegisteredTool(
    val spec: ToolSpec,
    val handler: suspend (JsonObject) -> String,
)
