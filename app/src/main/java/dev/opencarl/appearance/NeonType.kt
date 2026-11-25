package dev.opencarl.appearance

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The neon display system: Oxanium ExtraBold, uppercase, tight tracking, a
 * rainbow gradient fill, a dark cutout stroke and layered bloom. Terminal text
 * never goes through here.
 */
object Neon {

    val Black = Color(0xFF05030A)
    val Pink = Color(0xFFFF18C8)
    val Red = Color(0xFFFF3B55)
    val Orange = Color(0xFFFF8A16)
    val Yellow = Color(0xFFFFF12E)
    val Green = Color(0xFF57FF58)
    val Cyan = Color(0xFF18F4FF)
    val Blue = Color(0xFF3978FF)
    val Violet = Color(0xFFA64DFF)
    val OffWhite = Color(0xFFE8F7FF)

    val family: androidx.compose.ui.text.font.FontFamily
        get() = FontLibrary.oxanium

    fun rainbowBrush(phase: Float, widthPx: Float): Brush {
        val span = widthPx * NeonSpec.GRADIENT_SPAN
        val shift = phase * span
        val stops = NeonSpec.rainbow.map { it.fraction to Color(it.argb) }.toTypedArray()
        return Brush.linearGradient(
            colorStops = stops,
            start = androidx.compose.ui.geometry.Offset(-shift, 0f),
            end = androidx.compose.ui.geometry.Offset(span - shift, 0f),
        )
    }

    /** True when the user or the system asked for less motion. */
    @Composable
    fun reducedMotion(appearance: AppearanceSettings): Boolean {
        val context = LocalContext.current
        val systemScale = remember(context) {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f)
        }
        return systemScale == 0f || !appearance.neonAnimation
    }
}

/**
 * The big OPEN CARL wordmark. Shrinks to fit its width instead of clipping, and
 * honours system font scale because every size is in sp.
 */
@Composable
fun NeonWordmark(
    text: String,
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
    maxSp: Float = 64f,
    minSp: Float = 20f,
    animate: Boolean = true,
) {
    val display = NeonSpec.display(text).ifEmpty { "OPEN CARL" }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val reduced = Neon.reducedMotion(appearance)
    val slant = if (appearance.neonItalic) FontStyle.Italic else FontStyle.Normal

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, reduced) {
        if (!animate || reduced) {
            phase = 0f
            return@LaunchedEffect
        }
        val started = System.currentTimeMillis()
        while (true) {
            phase = NeonSpec.phaseAt(System.currentTimeMillis() - started)
            kotlinx.coroutines.delay(50L)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availablePx = with(density) { maxWidth.toPx() }
        val probe = 40f
        val base = TextStyle(
            fontFamily = Neon.family,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = slant,
            fontSize = probe.sp,
            letterSpacing = (-0.045).em,
        )
        val measured = measurer.measure(AnnotatedString(display), base, softWrap = false)
        val sizeSp = NeonSpec.fitFontSize(probe, measured.size.width.toFloat(), availablePx, minSp, maxSp)
        val style = base.copy(fontSize = sizeSp.sp)
        val strokePx = with(density) { (sizeSp / 22f).coerceIn(1.2f, 3.4f).dp.toPx() }

        Box(Modifier.fillMaxWidth()) {
            val glowModifier = Modifier.graphicsLayer {
                scaleX = 1.03f
                scaleY = 1.05f
            }
            Text(
                text = display,
                style = style.copy(brush = SolidColor(Neon.Pink)),
                modifier = Modifier.alpha(0.55f).blur(10.dp).then(glowModifier),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = display,
                style = style.copy(brush = SolidColor(Neon.Cyan)),
                modifier = Modifier.alpha(0.45f).blur(18.dp).then(glowModifier),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = display,
                style = style.copy(brush = SolidColor(Color.White)),
                modifier = Modifier.alpha(0.30f).blur(3.dp),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = display,
                style = TextStyle(
                    fontFamily = Neon.family,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = slant,
                    fontSize = sizeSp.sp,
                    letterSpacing = (-0.045).em,
                    color = Neon.Black,
                    drawStyle = Stroke(width = strokePx),
                ),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = display,
                style = style.copy(brush = Neon.rainbowBrush(phase, availablePx)),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The persistent user banner: Oxanium Bold, cyan bloom, wrapping instead of
 * clipping. Text and visibility stay entirely under the user's control.
 */
@Composable
fun NeonBannerText(
    text: String,
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val shown = NeonSpec.banner(text, appearance.bannerUppercase).ifEmpty { "OPEN CARL" }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = TextStyle(
        fontFamily = Neon.family,
        fontWeight = FontWeight.Bold,
        fontStyle = if (appearance.neonItalic) FontStyle.Italic else FontStyle.Normal,
        fontSize = appearance.bannerSizeSp.sp,
        letterSpacing = 0.035.em,
    )
    BoxWithConstraints(modifier) {
        val availablePx = with(density) { maxWidth.toPx() }
        val measured = measurer.measure(AnnotatedString(shown), base, maxLines = 1, softWrap = false)
        val sizeSp = NeonSpec.fitFontSize(
            probeSizeSp = appearance.bannerSizeSp.toFloat(),
            measuredWidthPx = measured.size.width.toFloat(),
            availableWidthPx = availablePx,
            minSp = 10f,
            maxSp = appearance.bannerSizeSp.toFloat(),
        )
        val style = base.copy(fontSize = sizeSp.sp)
        Box {
            Text(
                text = shown,
                style = style.copy(brush = SolidColor(Neon.Cyan)),
                modifier = Modifier.alpha(0.55f).blur(8.dp),
                maxLines = maxLines,
            )
            Text(
                text = shown,
                style = style.copy(brush = SolidColor(Color.White)),
                modifier = Modifier.alpha(0.35f).blur(2.dp),
                maxLines = maxLines,
            )
            Text(
                text = shown,
                style = style.copy(brush = SolidColor(Neon.Cyan)),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small yellow accent label, used for section headings in the neon chrome. */
@Composable
fun NeonLabel(text: String, modifier: Modifier = Modifier, size: TextUnit = 11.sp) {
    Box(modifier) {
        Text(
            text = NeonSpec.display(text),
            style = TextStyle(
                fontFamily = Neon.family,
                fontWeight = FontWeight.ExtraBold,
                fontSize = size,
                letterSpacing = 0.09.em,
                brush = SolidColor(Neon.Orange),
            ),
            modifier = Modifier.alpha(0.5f).blur(6.dp),
        )
        Text(
            text = NeonSpec.display(text),
            style = TextStyle(
                fontFamily = Neon.family,
                fontWeight = FontWeight.ExtraBold,
                fontSize = size,
                letterSpacing = 0.09.em,
                brush = SolidColor(Neon.Yellow),
            ),
        )
    }
}
