package dev.opencarl.appearance

import androidx.compose.ui.graphics.Color

/** One of the 32 selectable colors. */
data class PaletteColor(val id: String, val name: String, val argb: Int) {
    val color: Color get() = Color(argb)
}

/**
 * The 32-color palette every themeable surface draws from: chrome, terminal,
 * banner, links, borders. Users pick a color per role in Appearance settings.
 */
object Palette32 {

    private fun c(id: String, name: String, rgb: Long): PaletteColor =
        PaletteColor(id, name, (0xFF000000L or rgb).toInt())

    val colors: List<PaletteColor> = listOf(
        c("void", "Void", 0x0B0F14),
        c("ink", "Ink", 0x111827),
        c("charcoal", "Charcoal", 0x1F2937),
        c("slate", "Slate", 0x94A3B8),
        c("mint", "Mint", 0x35E08A),
        c("emerald", "Emerald", 0x10B981),
        c("jade", "Jade", 0x2DD4BF),
        c("teal", "Teal", 0x14B8A6),
        c("cyan", "Cyan", 0x22D3EE),
        c("sky", "Sky", 0x38BDF8),
        c("azure", "Azure", 0x60A5FA),
        c("blue", "Blue", 0x3B82F6),
        c("indigo", "Indigo", 0x6366F1),
        c("violet", "Violet", 0x8B5CF6),
        c("purple", "Purple", 0xA855F7),
        c("fuchsia", "Fuchsia", 0xD946EF),
        c("magenta", "Magenta", 0xE879F9),
        c("pink", "Pink", 0xF472B6),
        c("rose", "Rose", 0xFB7185),
        c("red", "Red", 0xEF4444),
        c("coral", "Coral", 0xFF6B6B),
        c("orange", "Orange", 0xF97316),
        c("amber", "Amber", 0xF59E0B),
        c("gold", "Gold", 0xEAB308),
        c("yellow", "Yellow", 0xFDE047),
        c("lime", "Lime", 0xA3E635),
        c("green", "Green", 0x22C55E),
        c("fog", "Fog", 0xD7E3EE),
        c("ice", "Ice", 0x7DD3FC),
        c("periwinkle", "Periwinkle", 0xA5B4FC),
        c("lavender", "Lavender", 0xC4B5FD),
        c("orchid", "Orchid", 0xE9D5FF),
    )

    private val index = colors.associateBy { it.id }

    fun byId(id: String): PaletteColor = index[id] ?: colors.first()

    fun colorOf(id: String): Color = byId(id).color

    /** "#RRGGBB" for xterm.js and WebView CSS. */
    fun hex(id: String): String = String.format("#%06X", 0xFFFFFF and byId(id).argb)

    fun onColorFor(id: String): Color {
        val argb = byId(id).argb
        val luminance = (0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)) / 255.0
        return if (luminance > 0.6) Color(0xFF0B0F14) else Color(0xFFF4F8FB)
    }
}
