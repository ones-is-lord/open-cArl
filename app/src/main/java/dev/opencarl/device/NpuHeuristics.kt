package dev.opencarl.device

/**
 * Rough NPU throughput by SoC family, used only to nudge recommendations and to
 * explain to the user why a tiny model runs faster than the clocks suggest.
 * These are marketing TOPS figures, not measured on device.
 */
object NpuHeuristics {

    private val table: List<Pair<String, Float>> = listOf(
        "snapdragon 8 elite" to 45f,
        "sm8750" to 45f,
        "snapdragon 8 gen 3" to 45f,
        "sm8650" to 45f,
        "snapdragon 8 gen 2" to 36f,
        "sm8550" to 36f,
        "snapdragon 8+ gen 1" to 27f,
        "sm8475" to 27f,
        "snapdragon 8 gen 1" to 27f,
        "sm8450" to 27f,
        "snapdragon 7" to 12f,
        "dimensity 9300" to 48f,
        "dimensity 9200" to 30f,
        "dimensity 8300" to 18f,
        "dimensity 8" to 14f,
        "exynos 2400" to 34f,
        "exynos 2200" to 26f,
        "tensor g4" to 45f,
        "tensor g3" to 45f,
        "tensor g2" to 30f,
        "tensor" to 20f,
        "kirin 9000" to 20f,
        "kirin 9010" to 30f,
    )

    fun estimateTops(soc: String): Float? {
        val key = soc.lowercase()
        return table.firstOrNull { (needle, _) -> key.contains(needle) }?.second
    }
}
