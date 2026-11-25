package dev.opencarl.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/** What the device can actually do, used to recommend a model instead of guessing. */
data class DeviceFacts(
    val totalRamMb: Int,
    val freeRamMb: Int,
    val cores: Int,
    val abi: String,
    val freeStorageMb: Int,
    val androidRelease: String,
    val sdk: Int,
    val device: String,
) {
    val tier: String
        get() = when {
            totalRamMb >= 12_000 -> "workstation"
            totalRamMb >= 8_000 -> "capable"
            totalRamMb >= 6_000 -> "modest"
            totalRamMb >= 4_000 -> "tight"
            else -> "minimal"
        }

    companion object {
        fun read(context: Context): DeviceFacts {
            val activity = context.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
            val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
            return DeviceFacts(
                totalRamMb = (info.totalMem / (1024 * 1024)).toInt(),
                freeRamMb = (info.availMem / (1024 * 1024)).toInt(),
                cores = Runtime.getRuntime().availableProcessors(),
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                freeStorageMb = stat?.let { (it.availableBytes / (1024 * 1024)).toInt() } ?: 0,
                androidRelease = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }
}

data class ModelChoice(
    val id: String,
    val name: String,
    val parameters: String,
    val quant: String,
    val downloadMb: Int,
    val minRamMb: Int,
    val note: String,
    val cloud: Boolean = false,
)

object ModelCatalog {

    val models = listOf(
        ModelChoice(
            id = "cloud",
            name = "Bring your own key",
            parameters = "cloud",
            quant = "n/a",
            downloadMb = 0,
            minRamMb = 0,
            note = "Fastest and best quality. Needs an API key and a connection; nothing runs on the device.",
            cloud = true,
        ),
        ModelChoice(
            id = "qwen2.5-0.5b-q4",
            name = "Qwen2.5 0.5B",
            parameters = "0.5B",
            quant = "Q4_K_M",
            downloadMb = 400,
            minRamMb = 1_500,
            note = "Runs anywhere, forgets quickly. Good for shell one-liners only.",
        ),
        ModelChoice(
            id = "llama3.2-1b-q4",
            name = "Llama 3.2 1B",
            parameters = "1B",
            quant = "Q4_K_M",
            downloadMb = 800,
            minRamMb = 2_000,
            note = "The smallest model that still holds a conversation.",
        ),
        ModelChoice(
            id = "qwen2.5-1.5b-q4",
            name = "Qwen2.5 1.5B",
            parameters = "1.5B",
            quant = "Q4_K_M",
            downloadMb = 1_100,
            minRamMb = 2_500,
            note = "Best tool-calling per megabyte. A sensible default for phones.",
        ),
        ModelChoice(
            id = "gemma2-2b-q4",
            name = "Gemma 2 2B",
            parameters = "2B",
            quant = "Q4_K_M",
            downloadMb = 1_600,
            minRamMb = 3_000,
            note = "Stronger prose than 1.5B, weaker at strict JSON.",
        ),
        ModelChoice(
            id = "llama3.2-3b-q4",
            name = "Llama 3.2 3B",
            parameters = "3B",
            quant = "Q4_K_M",
            downloadMb = 2_000,
            minRamMb = 4_000,
            note = "The sweet spot on 8 GB tablets.",
        ),
        ModelChoice(
            id = "qwen2.5-7b-q4",
            name = "Qwen2.5 7B",
            parameters = "7B",
            quant = "Q4_K_M",
            downloadMb = 4_700,
            minRamMb = 8_000,
            note = "Real coding ability. Expect seconds per reply, and heat.",
        ),
        ModelChoice(
            id = "qwen2.5-14b-q4",
            name = "Qwen2.5 14B",
            parameters = "14B",
            quant = "Q4_K_M",
            downloadMb = 9_000,
            minRamMb = 14_000,
            note = "Only on a workstation-class tablet, and only with the charger in.",
        ),
    )

    fun recommend(facts: DeviceFacts): ModelChoice = models
        .filterNot { it.cloud }
        .filter { it.minRamMb <= facts.totalRamMb * 0.55 && it.downloadMb * 2 <= facts.freeStorageMb }
        .maxByOrNull { it.minRamMb } ?: models.first { it.id == "llama3.2-1b-q4" }

    fun fits(choice: ModelChoice, facts: DeviceFacts): Boolean {
        if (choice.cloud) return true
        val ramOk = choice.minRamMb <= facts.totalRamMb
        val storageOk = choice.downloadMb * 2 <= facts.freeStorageMb
        return ramOk && storageOk
    }

    fun verdict(choice: ModelChoice, facts: DeviceFacts): String = when {
        choice.cloud -> "No device cost."
        !fits(choice, facts) -> "Too big for this device."
        choice.minRamMb <= facts.totalRamMb * 0.35 -> "Comfortable."
        else -> "Will run, but the system may kill it under load."
    }
}
