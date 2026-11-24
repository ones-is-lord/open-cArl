package dev.opencarl.ai

import dev.opencarl.data.ProviderConfig
import dev.opencarl.data.ProviderKind
import okhttp3.OkHttpClient

object LlmFactory {
    fun create(http: OkHttpClient, config: ProviderConfig, apiKey: String): LlmClient =
        when (config.kind) {
            ProviderKind.OPENAI_COMPATIBLE -> OpenAiClient(http, config, apiKey)
            ProviderKind.ANTHROPIC -> AnthropicClient(http, config, apiKey)
            ProviderKind.GEMINI -> GeminiClient(http, config, apiKey)
        }
}
