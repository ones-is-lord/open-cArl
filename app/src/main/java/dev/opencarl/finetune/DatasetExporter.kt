package dev.opencarl.finetune

import dev.opencarl.app.ChatItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns finished conversations into training data. On-device training is not
 * practical, so this produces the two things that actually are: a clean ChatML
 * dataset and a recipe to train on a workstation or in the Linux userland.
 */
class DatasetExporter(private val workspaceRoot: () -> File) {

    fun export(history: List<ChatItem>, name: String = defaultName()): File {
        val root = File(workspaceRoot(), "training").apply { mkdirs() }
        val file = File(root, "$name.jsonl")
        file.printWriter().use { writer ->
            var pendingUser: String? = null
            history.forEach { item ->
                when (item) {
                    is ChatItem.User -> pendingUser = item.text
                    is ChatItem.Assistant -> {
                        val user = pendingUser ?: return@forEach
                        if (item.text.isBlank()) return@forEach
                        writer.println(turn(user, item.text))
                        pendingUser = null
                    }
                    else -> Unit
                }
            }
        }
        return file
    }

    fun recipe(dataset: File, baseModel: String = "Qwen2.5-Coder-1.5B-Instruct"): File {
        val root = dataset.parentFile ?: workspaceRoot()
        val file = File(root, dataset.nameWithoutExtension + "-recipe.md")
        file.writeText(
            """
            # Fine-tune recipe

            Dataset: `${dataset.name}` (${dataset.readLines().size} turns, ChatML)

            ## Option A: llama.cpp LoRA on a workstation
            ```bash
            git clone https://github.com/ggerganov/llama.cpp && cd llama.cpp
            cmake -B build && cmake --build build --config Release -j
            # convert the base model to gguf, then:
            ./build/bin/llama-finetune \
              --model-base ./models/$baseModel-Q4_K_M.gguf \
              --train-data ${dataset.absolutePath} \
              --lora-out ./lora-adapter.gguf \
              --threads 8 --ctx 4096 --epochs 3 --learning-rate 1e-4
            ```

            ## Option B: axolotl (full precision, better quality)
            ```yaml
            base_model: $baseModel
            datasets:
              - path: ${dataset.absolutePath}
                type: chat_template
            micro_batch_size: 1
            gradient_accumulation_steps: 8
            num_epochs: 3
            learning_rate: 0.0002
            lora_r: 16
            lora_alpha: 32
            ```
            ```bash
            pip install axolotl && axolotl train config.yml
            ```

            ## Then on the phone
            1. Merge the adapter (or use the LoRA directly with llama.cpp).
            2. Copy the resulting gguf into `~/.carl/models/`.
            3. Point a provider at the local server; the model picker lists it.

            ## Quality gate
            Keep 10% of turns as a holdout and compare answers before/after. If the
            fine-tune does not beat the base model on your holdout, do not ship it.
            """.trimIndent(),
        )
        return file
    }

    private fun turn(user: String, assistant: String): String =
        """{"messages":[{"role":"user","content":${json(user)}},{"role":"assistant","content":${json(assistant)}}]}"""

    private fun json(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
        return "\"" + escaped + "\""
    }

    private fun defaultName(): String =
        "session-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
}
