package com.termdroid.agent

enum class LlmProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    CLAUDE("Anthropic Claude"),
    OPENAI("OpenAI / Codex"),
    CUSTOM("Ollama / Red Local / Custom"),
}

data class ProviderConfig(
    val provider: LlmProvider = LlmProvider.GEMINI,
    val token: String = "",
    val model: String = "",
    val baseUrl: String = "",
)

object TransportFactory {
    fun create(config: ProviderConfig): Transport = when (config.provider) {
        LlmProvider.CLAUDE -> ClaudeTransport(
            apiKey = config.token,
            model = config.model.ifBlank { ClaudeTransport.DEFAULT_MODEL },
            baseUrl = config.baseUrl,
        )
        LlmProvider.GEMINI -> GeminiTransport(
            token = config.token,
            model = config.model.ifBlank { "gemini-2.5-flash" },
            baseUrl = config.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" },
        )
        LlmProvider.OPENAI -> OpenAiTransport(
            token = config.token,
            baseUrl = config.baseUrl.ifBlank { "https://api.openai.com/v1" },
            model = config.model.ifBlank { "gpt-4o" },
        )
        LlmProvider.CUSTOM -> OpenAiTransport(
            token = config.token,
            baseUrl = config.baseUrl.ifBlank { "http://10.0.2.2:11434/v1" },
            model = config.model.ifBlank { "llama3.2" },
        )
    }
}
