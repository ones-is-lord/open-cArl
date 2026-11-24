package dev.opencarl.device

/**
 * Curated GGUF builds that are known to behave well under llama.cpp on Android.
 * Sizes are the Q4_K_M download sizes.
 */
data class LocalModel(
    val id: String,
    val name: String,
    val family: String,
    val paramsB: Float,
    val quant: String,
    val sizeGb: Float,
    val minRamGb: Float,
    val contextK: Int,
    val repo: String,
    val file: String,
    val bestFor: String,
) {
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/main/$file"
    val label: String get() = "$name $quant · ${String.format("%.1f", sizeGb)} GB"
}

object ModelCatalog {

    val models: List<LocalModel> = listOf(
        LocalModel(
            id = "qwen2.5-coder-1.5b",
            name = "Qwen2.5 Coder 1.5B",
            family = "Qwen",
            paramsB = 1.5f,
            quant = "Q4_K_M",
            sizeGb = 1.0f,
            minRamGb = 2.5f,
            contextK = 32,
            repo = "bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF",
            file = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",
            bestFor = "Code editing and shell work on mid-range phones.",
        ),
        LocalModel(
            id = "llama-3.2-1b",
            name = "Llama 3.2 1B",
            family = "Llama",
            paramsB = 1.0f,
            quant = "Q4_K_M",
            sizeGb = 0.8f,
            minRamGb = 2.0f,
            contextK = 8,
            repo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            file = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            bestFor = "Fast chat and quick commands on low-end devices.",
        ),
        LocalModel(
            id = "gemma-2-2b",
            name = "Gemma 2 2B",
            family = "Gemma",
            paramsB = 2.0f,
            quant = "Q4_K_M",
            sizeGb = 1.6f,
            minRamGb = 3.0f,
            contextK = 8,
            repo = "bartowski/gemma-2-2b-it-GGUF",
            file = "gemma-2-2b-it-Q4_K_M.gguf",
            bestFor = "Balanced writing and reasoning at small size.",
        ),
        LocalModel(
            id = "phi-3.5-mini",
            name = "Phi-3.5 Mini",
            family = "Phi",
            paramsB = 3.8f,
            quant = "Q4_K_M",
            sizeGb = 2.4f,
            minRamGb = 4.0f,
            contextK = 128,
            repo = "bartowski/Phi-3.5-mini-instruct-GGUF",
            file = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            bestFor = "Reasoning and long context on 6 GB+ devices.",
        ),
        LocalModel(
            id = "qwen2.5-3b",
            name = "Qwen2.5 3B Instruct",
            family = "Qwen",
            paramsB = 3.0f,
            quant = "Q4_K_M",
            sizeGb = 2.0f,
            minRamGb = 3.5f,
            contextK = 32,
            repo = "bartowski/Qwen2.5-3B-Instruct-GGUF",
            file = "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
            bestFor = "Solid general assistant on 6 GB devices.",
        ),
        LocalModel(
            id = "llama-3.1-8b",
            name = "Llama 3.1 8B Instruct",
            family = "Llama",
            paramsB = 8.0f,
            quant = "Q4_K_M",
            sizeGb = 4.9f,
            minRamGb = 7.5f,
            contextK = 32,
            repo = "bartowski/Meta-Llama-3.1-8B-Instruct-GGUF",
            file = "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            bestFor = "Flagship phones: best local quality if you can spare 5 GB.",
        ),
        LocalModel(
            id = "qwen2.5-coder-7b",
            name = "Qwen2.5 Coder 7B",
            family = "Qwen",
            paramsB = 7.0f,
            quant = "Q4_K_M",
            sizeGb = 4.4f,
            minRamGb = 7.0f,
            contextK = 32,
            repo = "bartowski/Qwen2.5-Coder-7B-Instruct-GGUF",
            file = "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf",
            bestFor = "Serious coding on 12 GB+ devices.",
        ),
        LocalModel(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B",
            family = "Llama",
            paramsB = 1.1f,
            quant = "Q4_K_M",
            sizeGb = 0.7f,
            minRamGb = 1.8f,
            contextK = 4,
            repo = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
            file = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            bestFor = "Last resort on 2-3 GB devices.",
        ),
    )

    fun byId(id: String): LocalModel? = models.firstOrNull { it.id == id }
}
