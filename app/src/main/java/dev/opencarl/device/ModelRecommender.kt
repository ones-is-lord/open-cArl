package dev.opencarl.device

import kotlin.math.pow

enum class Fit { GREAT, OK, TIGHT, NO }

data class Recommendation(
    val model: LocalModel,
    val fit: Fit,
    val score: Int,
    val estimatedTokensPerSecond: Float,
    val reason: String,
) {
    val headline: String get() = when (fit) {
        Fit.GREAT -> "recommended"
        Fit.OK -> "works"
        Fit.TIGHT -> "tight fit"
        Fit.NO -> "will not run"
    }
}

/**
 * Turns a [DeviceProfile] into a short list of models that will actually run
 * well on this phone, with an honest throughput estimate.
 */
object ModelRecommender {

    fun recommend(profile: DeviceProfile, limit: Int = 4): List<Recommendation> =
        ModelCatalog.models
            .map { evaluate(profile, it) }
            .filter { it.fit != Fit.NO }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenByDescending { it.estimatedTokensPerSecond })
            .take(limit)

    fun evaluate(profile: DeviceProfile, model: LocalModel): Recommendation {
        val requiredMb = maxOf(model.minRamGb * 1024f, model.sizeGb * 1024f * 1.2f)
        val downloadMb = model.sizeGb * 1024f * 1.1f
        val tokPerSecond = estimateTokensPerSecond(profile, model)

        val fit = when {
            !profile.isArm64 -> Fit.NO
            profile.totalRamMb < requiredMb -> Fit.NO
            profile.freeStorageMb < downloadMb -> Fit.NO
            profile.availRamMb < requiredMb -> Fit.TIGHT
            profile.availRamMb > requiredMb * 1.6f -> Fit.GREAT
            else -> Fit.OK
        }

        val tierMax = when (profile.tier) {
            DeviceTier.FLAGSHIP -> 9f
            DeviceTier.HIGH -> 5f
            DeviceTier.MID -> 3.5f
            DeviceTier.LOW -> 2f
            DeviceTier.MINIMAL -> 1.5f
        }
        val quality = (model.paramsB.coerceAtMost(tierMax) / tierMax).coerceIn(0f, 1f)
        val speed = (tokPerSecond.coerceAtMost(15f) / 15f).coerceIn(0f, 1f)
        var score = ((quality * 60f) + (speed * 40f)).toInt()
        if (fit == Fit.TIGHT) score -= 25
        if (profile.npuTops != null && profile.npuTops >= 20f && model.paramsB <= 2f) score += 3

        val reason = buildString {
            append("needs ${String.format("%.1f", requiredMb / 1024f)} GB RAM, ")
            append("${String.format("%.1f", model.sizeGb)} GB disk")
            append(" · ~").append(tokPerSecond.toInt()).append(" tok/s")
            when (fit) {
                Fit.NO -> {
                    if (!profile.isArm64) append(" · 64-bit CPU required")
                    else if (profile.totalRamMb < requiredMb) append(" · not enough RAM")
                    else append(" · free up storage first")
                }
                Fit.TIGHT -> append(" · close other apps first")
                else -> Unit
            }
        }
        return Recommendation(model, fit, score.coerceIn(0, 200), tokPerSecond, reason)
    }

    /**
     * Deliberately simple, calibrated against reported llama.cpp numbers:
     * throughput scales with big-core clock and inversely with parameter count.
     */
    fun estimateTokensPerSecond(profile: DeviceProfile, model: LocalModel): Float {
        if (model.paramsB <= 0f) return 0f
        var rate = (profile.bigCoreGhz * 6.0f) / model.paramsB.toDouble().pow(0.9).toFloat()
        if (profile.npuTops != null && profile.npuTops >= 10f && model.paramsB <= 2f) rate *= 1.15f
        if (!profile.isArm64) rate *= 0.7f
        if (profile.tier == DeviceTier.MINIMAL) rate *= 0.7f
        return rate.coerceIn(0.4f, 60f)
    }
}
