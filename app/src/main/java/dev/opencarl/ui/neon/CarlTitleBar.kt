package dev.opencarl.ui.neon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.appearance.NeonSpec

val BrandYellow = Color(0xFFFFE21A)
val BrandOrange = Color(0xFFFF9A1F)
val TaglineCyan = Color(0xFF29F0FF)
val ControlMagenta = Color(0xFFFF2BD1)

/** Title bar from the reference: avatar, wordmark, rule, tagline, window controls. */
@Composable
fun CarlTitleBar(
    appearance: AppearanceSettings,
    subtitle: String,
    onAvatar: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarlAvatar(size = 40.dp, onClick = onAvatar)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(Modifier.weight(0.44f)) {
                BrandWordmark(
                    text = appearance.brandTitle,
                    italic = appearance.neonItalic,
                    availablePx = with(LocalDensity.current) { maxWidth.toPx() },
                    maxSp = 30f,
                    minSp = 12f,
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 7.dp)
                    .width(2.dp)
                    .height(24.dp)
                    .background(ControlMagenta),
            )
            BoxWithConstraints(Modifier.weight(0.56f)) {
                Tagline(
                    text = appearance.bannerLabel,
                    uppercase = appearance.bannerUppercase,
                    availablePx = with(LocalDensity.current) { maxWidth.toPx() },
                    maxSp = (appearance.bannerSizeSp - 3).coerceAtLeast(9).toFloat(),
                )
            }
        }
        WindowControls(onMinimize = onMinimize, onMaximize = onMaximize, onClose = onClose)
    }
}

@Composable
fun BrandWordmark(
    text: String,
    italic: Boolean,
    availablePx: Float,
    maxSp: Float,
    minSp: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val shown = NeonSpec.display(text).ifEmpty { "OPEN CARL" }
    val slant = if (italic) FontStyle.Italic else FontStyle.Normal
    val base = TextStyle(
        fontFamily = FontLibrary.oxanium,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = slant,
        fontSize = 24.sp,
        letterSpacing = (-0.03).em,
    )
    val measured = measurer.measure(AnnotatedString(shown), base, softWrap = false)
    val size = NeonSpec.fitFontSize(24f, measured.size.width.toFloat(), availablePx, minSp, maxSp)
    val stroke = with(LocalDensity.current) { (size / 11f).coerceIn(1.4f, 3.6f).dp.toPx() }
    val brush = Brush.linearGradient(
        colors = listOf(BrandYellow, BrandYellow, BrandOrange),
        start = Offset(0f, 0f),
        end = Offset(availablePx.coerceAtLeast(1f), 0f),
    )
    Box(modifier) {
        Text(
            text = shown,
            style = base.copy(fontSize = size.sp, brush = SolidColor(BrandOrange)),
            modifier = Modifier.alpha(0.55f).blur(10.dp),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = shown,
            style = TextStyle(
                fontFamily = FontLibrary.oxanium,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = slant,
                fontSize = size.sp,
                letterSpacing = (-0.03).em,
                color = Color(0xFF1A0A00),
                drawStyle = Stroke(width = stroke),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = shown,
            style = base.copy(fontSize = size.sp, brush = brush),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun Tagline(
    text: String,
    uppercase: Boolean,
    availablePx: Float,
    maxSp: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val shown = NeonSpec.banner(text, uppercase).ifEmpty { "AIN'T NONE OF THIS EVEN MATTERS" }
    val base = TextStyle(
        fontFamily = FontLibrary.oxanium,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = maxSp.sp,
        letterSpacing = 0.02.em,
    )
    val measured = measurer.measure(AnnotatedString(shown), base, maxLines = 1, softWrap = false)
    val size = NeonSpec.fitFontSize(maxSp, measured.size.width.toFloat(), availablePx, 8f, maxSp)
    val style = base.copy(fontSize = size.sp)
    Box(modifier) {
        Text(
            text = shown,
            style = style.copy(brush = SolidColor(TaglineCyan)),
            modifier = Modifier.alpha(0.5f).blur(7.dp),
            maxLines = 1,
        )
        Text(
            text = shown,
            style = style.copy(brush = SolidColor(TaglineCyan)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class Glyph(val x: Float, val y: Float, val z: Float)

/** Minus, square and X, drawn rather than iconified so they match the art. */
@Composable
fun WindowControls(onMinimize: () -> Unit, onMaximize: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(onClick = onMinimize) { glyph, glow ->
            if (glow) drawLine(ControlMagenta, Offset(glyph.x, glyph.y), Offset(glyph.z, glyph.y), strokeWidth = 7f, alpha = 0.35f)
            drawLine(ControlMagenta, Offset(glyph.x, glyph.y), Offset(glyph.z, glyph.y), strokeWidth = 2.6f)
        }
        GlyphButton(onClick = onMaximize) { glyph, glow ->
            val w = glyph.z - glyph.x
            drawRect(
                color = if (glow) ControlMagenta.copy(alpha = 0.3f) else ControlMagenta,
                topLeft = Offset(glyph.x, glyph.y - w / 2f),
                size = androidx.compose.ui.geometry.Size(w, w),
                style = Stroke(width = if (glow) 6f else 2.4f),
            )
        }
        GlyphButton(onClick = onClose) { glyph, glow ->
            val w = (glyph.z - glyph.x)
            val color = if (glow) ControlMagenta.copy(alpha = 0.3f) else ControlMagenta
            val width = if (glow) 6f else 2.6f
            drawLine(color, Offset(glyph.x, glyph.y - w / 2f), Offset(glyph.z, glyph.y + w / 2f), strokeWidth = width)
            drawLine(color, Offset(glyph.x, glyph.y + w / 2f), Offset(glyph.z, glyph.y - w / 2f), strokeWidth = width)
        }
    }
}

@Composable
private fun GlyphButton(
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Glyph, Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val box = Glyph(size.width * 0.2f, size.height / 2f, size.width * 0.8f)
            draw(box, true)
            draw(box, false)
        }
    }
}

/**
 * Prefers a bundled drawable named carl_avatar, otherwise draws a screenprint
 * placeholder so the header never looks unfinished.
 */
@Composable
fun CarlAvatar(size: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val custom = remember {
        val id = context.resources.getIdentifier("carl_avatar", "drawable", context.packageName)
        if (id != 0) id else null
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            drawCircle(color = BrandYellow)
            if (custom == null) {
                val r = this.size.minDimension / 2f
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                drawCircle(ControlMagenta, radius = r * 0.30f, center = Offset(cx - 1.6f, cy - r * 0.18f + 1.6f), alpha = 0.55f)
                drawCircle(Color(0xFF16002A), radius = r * 0.30f, center = Offset(cx, cy - r * 0.18f))
                drawCircle(ControlMagenta, radius = r * 0.60f, center = Offset(cx - 1.6f, cy + r * 0.92f + 1.6f), alpha = 0.55f)
                drawCircle(Color(0xFF16002A), radius = r * 0.60f, center = Offset(cx, cy + r * 0.92f))
            }
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(BrandYellow, ControlMagenta, TaglineCyan),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height),
                ),
                radius = this.size.minDimension / 2f - 1.6f,
                style = Stroke(width = 3f),
            )
        }
        if (custom != null) {
            Image(
                painter = painterResource(id = custom),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}
