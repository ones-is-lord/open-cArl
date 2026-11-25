package dev.opencarl.appearance

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class ColorRole(val label: String) {
    ACCENT("Accent"),
    HIGHLIGHT("Highlight"),
    TEXT("Text"),
    BANNER("Banner"),
    LINK("Links"),
    OUTLINE("Terminal outline"),
    TERMINAL_BACKGROUND("Terminal background"),
    CURSOR("Terminal cursor"),
}

@Serializable
data class AppearanceSettings(
    val accent: String = "mint",
    val highlight: String = "sky",
    val text: String = "fog",
    val banner: String = "mint",
    val link: String = "ice",
    val outline: String = "jade",
    val terminalBackground: String = "void",
    val cursor: String = "mint",
    val outlineWidthDp: Int = 1,
    val animatedOutline: Boolean = true,
    val bannerText: String = "",
    val uiFont: String = "jetbrains_mono",
    val terminalFont: String = "jetbrains_mono",
    val bannerFont: String = "press_start_2p",
    val headerFont: String = "jetbrains_mono",
    val terminalFontSize: Int = 14,
    val neonAnimation: Boolean = true,
    val neonItalic: Boolean = true,
    val bannerVisible: Boolean = true,
    val bannerUppercase: Boolean = true,
    val bannerSizeSp: Int = 17,
    val terminalOffWhite: Boolean = true,
    val promptAccent: Boolean = true,
    val brandTitle: String = "OPEN CARL",
) {

    fun colorOf(role: ColorRole): Color = Palette32.colorOf(
        when (role) {
            ColorRole.ACCENT -> accent
            ColorRole.HIGHLIGHT -> highlight
            ColorRole.TEXT -> text
            ColorRole.BANNER -> banner
            ColorRole.LINK -> link
            ColorRole.OUTLINE -> outline
            ColorRole.TERMINAL_BACKGROUND -> terminalBackground
            ColorRole.CURSOR -> cursor
        },
    )

    fun colorIdOf(role: ColorRole): String = when (role) {
        ColorRole.ACCENT -> accent
        ColorRole.HIGHLIGHT -> highlight
        ColorRole.TEXT -> text
        ColorRole.BANNER -> banner
        ColorRole.LINK -> link
        ColorRole.OUTLINE -> outline
        ColorRole.TERMINAL_BACKGROUND -> terminalBackground
        ColorRole.CURSOR -> cursor
    }

    fun withColor(role: ColorRole, colorId: String): AppearanceSettings = when (role) {
        ColorRole.ACCENT -> copy(accent = colorId)
        ColorRole.HIGHLIGHT -> copy(highlight = colorId)
        ColorRole.TEXT -> copy(text = colorId)
        ColorRole.BANNER -> copy(banner = colorId)
        ColorRole.LINK -> copy(link = colorId)
        ColorRole.OUTLINE -> copy(outline = colorId)
        ColorRole.TERMINAL_BACKGROUND -> copy(terminalBackground = colorId)
        ColorRole.CURSOR -> copy(cursor = colorId)
    }

    val bannerLabel: String
        get() = bannerText.trim().ifEmpty { "AIN'T NONE OF THIS EVEN MATTERS" }

    val brandLabel: String get() = brandTitle.trim().ifEmpty { "OPEN CARL" }
}
