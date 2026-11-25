package dev.opencarl.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.Palette32
import dev.opencarl.terminal.Style
import dev.opencarl.terminal.VtParser

/**
 * Maps the terminal's 256 colors onto the app's palette: the first 16 come from
 * the user's chosen roles, the rest follow the standard xterm cube.
 */
class TerminalColors(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val selection: Color,
    private val ansi: IntArray,
) {
    fun fg(style: Style, selected: Boolean): Color {
        val base = resolve(style.fg, foreground)
        val color = if (style.inverse) resolve(style.bg, background) else base
        val dimmed = if (style.dim) color.copy(alpha = 0.65f) else color
        return if (selected) blend(dimmed, selection, 0.35f) else dimmed
    }

    fun bg(style: Style, selected: Boolean): Color {
        val base = if (style.inverse) resolve(style.fg, foreground) else resolve(style.bg, background)
        val withSelection = if (selected) blend(base, selection, 0.55f) else base
        return withSelection
    }

    fun isDefaultBg(style: Style): Boolean = style.bg == Style.DEFAULT && !style.inverse

    private fun resolve(value: Int, fallback: Color): Color = when {
        value == Style.DEFAULT -> fallback
        value and VtParser.TRUECOLOR != 0 -> Color(0xFF000000.toInt() or (value and 0xFFFFFF))
        value in 0..255 -> Color(ansi[value])
        else -> fallback
    }

    private fun blend(base: Color, other: Color, amount: Float): Color = Color(
        red = base.red + (other.red - base.red) * amount,
        green = base.green + (other.green - base.green) * amount,
        blue = base.blue + (other.blue - base.blue) * amount,
        alpha = 1f,
    )

    companion object {
        fun from(appearance: AppearanceSettings): TerminalColors {
            val background = appearance.colorOf(ColorRole.TERMINAL_BACKGROUND)
            val foreground = if (appearance.terminalOffWhite) {
                androidx.compose.ui.graphics.Color(0xFFE8F7FF)
            } else {
                appearance.colorOf(ColorRole.TEXT)
            }
            val cursor = appearance.colorOf(ColorRole.CURSOR)
            val accent = appearance.colorOf(ColorRole.ACCENT)
            val highlight = appearance.colorOf(ColorRole.HIGHLIGHT)
            val banner = appearance.colorOf(ColorRole.BANNER)
            val link = appearance.colorOf(ColorRole.LINK)

            val base = intArrayOf(
                argb(background), argb(banner), argb(accent), argb(highlight),
                argb(link), argb(Palette32.colorOf("violet")), argb(Palette32.colorOf("jade")),
                argb(foreground),
                argb(Palette32.colorOf("slate")), argb(Palette32.colorOf("rose")),
                argb(Palette32.colorOf("lime")), argb(Palette32.colorOf("gold")),
                argb(Palette32.colorOf("azure")), argb(Palette32.colorOf("orchid")),
                argb(Palette32.colorOf("teal")), argb(Palette32.colorOf("fog")),
            )
            val ansi = IntArray(256)
            base.copyInto(ansi, 0, 0, 16)
            for (i in 16..231) {
                val index = i - 16
                val r = (index / 36) % 6
                val g = (index / 6) % 6
                val b = index % 6
                ansi[i] = 0xFF000000.toInt() or (level(r) shl 16) or (level(g) shl 8) or level(b)
            }
            for (i in 232..255) {
                val shade = 8 + (i - 232) * 10
                ansi[i] = 0xFF000000.toInt() or (shade shl 16) or (shade shl 8) or shade
            }
            return TerminalColors(
                background = background,
                foreground = foreground,
                cursor = cursor,
                selection = accent.copy(alpha = 0.55f),
                ansi = ansi,
            )
        }

        private fun level(value: Int): Int = if (value == 0) 0 else 55 + value * 40

        private fun argb(color: Color): Int = color.toArgb()
    }
}
