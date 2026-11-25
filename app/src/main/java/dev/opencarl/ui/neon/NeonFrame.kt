package dev.opencarl.ui.neon

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

val FramePink = Color(0xFFFF2BD1)
val FrameRose = Color(0xFFFF3EA5)
val FrameViolet = Color(0xFFB03BFF)
val FrameCyan = Color(0xFF22E8FF)

val FrameStops = listOf(FramePink, FrameRose, FrameViolet, FrameCyan)

private data class Grunge(val at: Float, val length: Float, val width: Float, val alpha: Float, val cool: Boolean)

private fun grungeSpecs(seed: Int = 1337, count: Int = 160): List<Grunge> {
    val random = Random(seed)
    return List(count) {
        Grunge(
            at = random.nextFloat(),
            length = 0.002f + random.nextFloat() * 0.013f,
            width = 0.5f + random.nextFloat() * 2.2f,
            alpha = 0.10f + random.nextFloat() * 0.45f,
            cool = random.nextBoolean(),
        )
    }
}

/**
 * The CRT window: a rounded gradient border with bloom and a distressed print
 * texture wrapping the interface. Content keeps its own layout and colours.
 */
@Composable
fun NeonFrame(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    borderWidth: Dp = 2.5.dp,
    grunge: Boolean = true,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        FrameLayers(corner = corner, borderWidth = borderWidth, grunge = grunge, glow = glow)
        content()
    }
}

/** Border only, for drawing over content that must not be re-laid out. */
@Composable
fun NeonFrameOverlay(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    borderWidth: Dp = 2.5.dp,
    grunge: Boolean = true,
    glow: Boolean = true,
) {
    Box(modifier) {
        FrameLayers(corner = corner, borderWidth = borderWidth, grunge = grunge, glow = glow)
    }
}

@Composable
private fun FrameLayers(corner: Dp, borderWidth: Dp, grunge: Boolean, glow: Boolean) {
    val density = LocalDensity.current
    val cornerPx = with(density) { corner.toPx() }
    val widthPx = with(density) { borderWidth.toPx() }
    val specs = remember { grungeSpecs() }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (glow && canBlur) {
        Canvas(Modifier.fillMaxSize().blur(20.dp).alpha(0.55f)) {
            frameStroke(cornerPx, widthPx * 3f)
        }
        Canvas(Modifier.fillMaxSize().blur(7.dp).alpha(0.80f)) {
            frameStroke(cornerPx, widthPx * 1.6f)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        frameStroke(cornerPx, widthPx)
        if (grunge) drawGrunge(specs, cornerPx)
    }
}

private fun DrawScope.frameBrush(): Brush = Brush.linearGradient(
    colors = FrameStops,
    start = Offset(0f, 0f),
    end = Offset(size.width, size.height * 0.35f),
)

private fun DrawScope.frameStroke(cornerPx: Float, widthPx: Float) {
    val inset = widthPx / 2f
    drawRoundRect(
        brush = frameBrush(),
        topLeft = Offset(inset, inset),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = widthPx),
    )
}

private fun DrawScope.drawGrunge(specs: List<Grunge>, cornerPx: Float) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 2f,
                top = 2f,
                right = size.width - 2f,
                bottom = size.height - 2f,
                radiusX = cornerPx,
                radiusY = cornerPx,
            ),
        )
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val total = measure.length
    if (total <= 0f) return
    specs.forEach { spec ->
        val distance = spec.at * total
        val position = measure.getPosition(distance)
        val tangent = measure.getTangent(distance)
        val half = spec.length * total * 0.5f
        drawLine(
            color = if (spec.cool) FrameCyan else FramePink,
            start = Offset(position.x - tangent.x * half, position.y - tangent.y * half),
            end = Offset(position.x + tangent.x * half, position.y + tangent.y * half),
            strokeWidth = spec.width,
            alpha = spec.alpha,
            cap = StrokeCap.Round,
        )
    }
}

/** Faint CRT scanlines drawn over the terminal, not over the chrome. */
@Composable
fun Scanlines(modifier: Modifier = Modifier, spacing: Dp = 3.dp, alpha: Float = 0.05f) {
    val density = LocalDensity.current
    val step = with(density) { spacing.toPx() }
    Canvas(modifier) {
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, alpha = alpha)
            y += step
        }
    }
}
