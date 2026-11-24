package dev.opencarl.data

import dev.opencarl.appearance.AppearanceSettings
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderKind { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val model: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.3,
    val headers: Map<String, String> = emptyMap(),
    val needsKey: Boolean = true,
    val notes: String = "",
)

@Serializable
enum class ApprovalPolicy { ALWAYS_ASK, SMART, AUTO }

@Serializable
enum class RuntimeBackend { DEVICE, PROOT }

@Serializable
data class RuntimeSettings(
    val backend: RuntimeBackend = RuntimeBackend.DEVICE,
    val distro: String = "alpine",
    val prootArgs: String = "",
    val termuxBridgeEnabled: Boolean = false,
)

@Serializable
enum class McpTransportKind { HTTP, SSE, STDIO }

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val transport: McpTransportKind = McpTransportKind.HTTP,
    val url: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val presetId: String? = null,
)

@Serializable
data class AppSettings(
    val providers: List<ProviderConfig> = Defaults.providers,
    val activeProviderId: String = Defaults.providers.first().id,
    val approvalPolicy: ApprovalPolicy = ApprovalPolicy.SMART,
    val maxSteps: Int = 24,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val runtime: RuntimeSettings = RuntimeSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val memoryEnabled: Boolean = true,
    val openHandsUrl: String = "http://127.0.0.1:3000",
    val systemPromptExtra: String = "",
) {
    val activeProvider: ProviderConfig
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.first()
}

object Defaults {
    val providers: List<ProviderConfig> = listOf(
        ProviderConfig(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            model = "anthropic/claude-3.5-sonnet",
            notes = "One key, hundreds of models.",
        ),
        ProviderConfig(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
        ),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic",
            kind = ProviderKind.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-3-5-sonnet-latest",
        ),
        ProviderConfig(
            id = "gemini",
            name = "Google Gemini",
            kind = ProviderKind.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-2.0-flash",
        ),
        ProviderConfig(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
        ),
        ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
        ),
        ProviderConfig(
            id = "mistral",
            name = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            model = "codestral-latest",
        ),
        ProviderConfig(
            id = "llamacpp",
            name = "llama.cpp (on device)",
            baseUrl = "http://127.0.0.1:8080/v1",
            model = "local",
            needsKey = false,
            notes = "Start llama-server from the Models screen.",
        ),
        ProviderConfig(
            id = "ollama",
            name = "Ollama",
            baseUrl = "http://127.0.0.1:11434/v1",
            model = "qwen2.5-coder:7b",
            needsKey = false,
            notes = "Ollama running on this device or your LAN.",
        ),
        ProviderConfig(
            id = "lmstudio",
            name = "LM Studio",
            baseUrl = "http://127.0.0.1:1234/v1",
            model = "local-model",
            needsKey = false,
        ),
    )
}
