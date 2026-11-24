package dev.opencarl.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.appearance.Palette32

@Composable
fun OpenCarlTheme(appearance: AppearanceSettings, content: @Composable () -> Unit) {
    val accent = appearance.colorOf(ColorRole.ACCENT)
    val text = appearance.colorOf(ColorRole.TEXT)
    val background = appearance.colorOf(ColorRole.TERMINAL_BACKGROUND)
    val link = appearance.colorOf(ColorRole.LINK)
    val outline = appearance.colorOf(ColorRole.OUTLINE)
    val surface = Palette32.byId("ink").color
    val surfaceHigh = Palette32.byId("charcoal").color

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Palette32.onColorFor(appearance.accent),
        primaryContainer = surfaceHigh,
        onPrimaryContainer = text,
        secondary = link,
        onSecondary = Palette32.onColorFor(appearance.link),
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = Palette32.byId("slate").color,
        outline = outline,
        outlineVariant = Palette32.byId("charcoal").color,
        error = Palette32.byId("coral").color,
        onError = Palette32.onColorFor("coral"),
    )

    val family = FontLibrary.familyOf(appearance.uiFont)
    val base = Typography()
    val typography = Typography(
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )

    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

fun monoStyle(family: FontFamily, size: Int = 13): TextStyle =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = (size + 5).sp)
