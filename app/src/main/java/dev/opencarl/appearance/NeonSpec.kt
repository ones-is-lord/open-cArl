package dev.opencarl.appearance

/**
 * Pure geometry and colour maths for the neon display type. No Compose types
 * here so it can be unit tested on the JVM; the drawing lives in NeonType.kt.
 */
object NeonSpec {

    const val BLACK = 0xFF05030AL
    const val OFF_WHITE = 0xFFE8F7FFL

    data class Stop(val argb: Long, val fraction: Float)

    /** The nine stops from open_carl_neon_type.css, in order. */
    val rainbow: List<Stop> = listOf(
        Stop(0xFFFF18C8, 0.00f),
        Stop(0xFFFF3B55, 0.14f),
        Stop(0xFFFF8A16, 0.27f),
        Stop(0xFFFFF12E, 0.39f),
        Stop(0xFF57FF58, 0.52f),
        Stop(0xFF18F4FF, 0.65f),
        Stop(0xFF3978FF, 0.78f),
        Stop(0xFFA64DFF, 0.90f),
        Stop(0xFFFF18C8, 1.00f),
    )

    /** Background is 220% of the text box, exactly like background-size: 220%. */
    const val GRADIENT_SPAN = 2.2f

    const val PERIOD_MS = 12_000L

    /** 0..1 sawtooth so the gradient can drift without a visible jump. */
    fun phaseAt(elapsedMs: Long, periodMs: Long = PERIOD_MS): Float {
        if (periodMs <= 0L) return 0f
        val wrapped = ((elapsedMs % periodMs) + periodMs) % periodMs
        return wrapped.toFloat() / periodMs.toFloat()
    }

    /**
     * Largest font size that fits, given the width measured at [probeSizeSp].
     * Text width is close enough to linear in size for a single line.
     */
    fun fitFontSize(
        probeSizeSp: Float,
        measuredWidthPx: Float,
        availableWidthPx: Float,
        minSp: Float,
        maxSp: Float,
    ): Float {
        if (probeSizeSp <= 0f || measuredWidthPx <= 0f || availableWidthPx <= 0f) return minSp
        val scale = availableWidthPx / measuredWidthPx
        val fitted = probeSizeSp * scale
        return fitted.coerceIn(minSp, maxSp)
    }

    /** Uppercase without turning the user's text into something else. */
    fun display(text: String): String = text.trim().uppercase()

    fun banner(text: String, uppercase: Boolean): String =
        if (uppercase) text.trim().uppercase() else text.trim()
}
