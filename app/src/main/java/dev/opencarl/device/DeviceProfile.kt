package dev.opencarl.device

/** Plain snapshot of what this phone can actually do. No Android imports: it is testable. */
data class DeviceProfile(
    val manufacturer: String = "unknown",
    val model: String = "unknown",
    val soc: String = "unknown",
    val abi: String = "arm64-v8a",
    val cores: Int = 8,
    val bigCoreMhz: Int = 2000,
    val totalRamMb: Int = 4096,
    val availRamMb: Int = 2048,
    val freeStorageMb: Int = 8192,
    val glesVersion: String = "3.2",
    val vulkan: Boolean = false,
    val npuTops: Float? = null,
    val androidSdk: Int = 34,
) {
    val bigCoreGhz: Float get() = bigCoreMhz / 1000f

    val isArm64: Boolean get() = abi.contains("arm64") || abi.contains("x86_64")

    /** Coarse bucket used for model sizing and UI copy. */
    val tier: DeviceTier
        get() = when {
            totalRamMb >= 11_000 && bigCoreMhz >= 2_600 && cores >= 8 -> DeviceTier.FLAGSHIP
            totalRamMb >= 7_000 && bigCoreMhz >= 2_200 -> DeviceTier.HIGH
            totalRamMb >= 5_000 && bigCoreMhz >= 1_800 -> DeviceTier.MID
            totalRamMb >= 3_000 -> DeviceTier.LOW
            else -> DeviceTier.MINIMAL
        }

    val summary: String
        get() = "$manufacturer $model · $soc · ${totalRamMb / 1024} GB RAM · ${cores} cores @ " +
            String.format("%.1f", bigCoreGhz) + " GHz · OpenGL ES $glesVersion" +
            (if (vulkan) " · Vulkan" else "")
}

enum class DeviceTier(val label: String, val blurb: String) {
    FLAGSHIP("S", "Comfortable with 7-8B models at Q4, fast prompt processing."),
    HIGH("A", "Best with 3-4B models; 7B works but tight and slow."),
    MID("B", "Stay at 1.5-3B models at Q4 for usable speed."),
    LOW("C", "1-1.5B models only, and expect a few tokens per second."),
    MINIMAL("D", "Use a cloud provider or a remote llama.cpp server; local inference will crawl."),
}
