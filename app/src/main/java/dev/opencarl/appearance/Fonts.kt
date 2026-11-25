package dev.opencarl.appearance

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.opencarl.R

data class FontEntry(
    val id: String,
    val label: String,
    val resource: Int? = null,
    val family: FontFamily? = null,
    val cssName: String? = null,
    val monospace: Boolean = true,
)

/**
 * Fifteen bundled open-licensed faces plus the platform families. Each surface
 * (interface, terminal, banner, header) can use a different one.
 */
object FontLibrary {

    /** Oxanium at weight 800, the display face for the neon wordmark. */
    val oxanium: FontFamily = FontFamily(Font(R.font.oxanium_display))

    val bundled: List<FontEntry> = listOf(
        FontEntry("oxanium", "Oxanium (display)", R.font.oxanium_display, cssName = "Oxanium", monospace = false),
        FontEntry("oxanium_bold", "Oxanium Bold", R.font.oxanium_bold, cssName = "Oxanium", monospace = false),
        FontEntry("jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono, cssName = "JetBrains Mono"),
        FontEntry("fira_code", "Fira Code", R.font.fira_code, cssName = "Fira Code"),
        FontEntry("ibm_plex_mono", "IBM Plex Mono", R.font.ibm_plex_mono, cssName = "IBM Plex Mono"),
        FontEntry("source_code_pro", "Source Code Pro", R.font.source_code_pro, cssName = "Source Code Pro"),
        FontEntry("inconsolata", "Inconsolata", R.font.inconsolata, cssName = "Inconsolata"),
        FontEntry("space_mono", "Space Mono", R.font.space_mono, cssName = "Space Mono"),
        FontEntry("ubuntu_mono", "Ubuntu Mono", R.font.ubuntu_mono, cssName = "Ubuntu Mono"),
        FontEntry("cousine", "Cousine", R.font.cousine, cssName = "Cousine"),
        FontEntry("anonymous_pro", "Anonymous Pro", R.font.anonymous_pro, cssName = "Anonymous Pro"),
        FontEntry("share_tech_mono", "Share Tech Mono", R.font.share_tech_mono, cssName = "Share Tech Mono"),
        FontEntry("oxygen_mono", "Oxygen Mono", R.font.oxygen_mono, cssName = "Oxygen Mono"),
        FontEntry("b612_mono", "B612 Mono", R.font.b612_mono, cssName = "B612 Mono"),
        FontEntry("cutive_mono", "Cutive Mono", R.font.cutive_mono, cssName = "Cutive Mono"),
        FontEntry("vt323", "VT323", R.font.vt323, cssName = "VT323"),
        FontEntry("press_start_2p", "Press Start 2P", R.font.press_start_2p, cssName = "Press Start 2P"),
    )

    val system: List<FontEntry> = listOf(
        FontEntry("system_mono", "System Monospace", family = FontFamily.Monospace, cssName = "monospace"),
        FontEntry("system_sans", "System Sans", family = FontFamily.SansSerif, cssName = "sans-serif", monospace = false),
        FontEntry("system_serif", "System Serif", family = FontFamily.Serif, cssName = "serif", monospace = false),
        FontEntry("system_cursive", "System Cursive", family = FontFamily.Cursive, cssName = "cursive", monospace = false),
        FontEntry("system_default", "System Default", family = FontFamily.Default, cssName = "sans-serif", monospace = false),
    )

    val all: List<FontEntry> = bundled + system

    fun byId(id: String): FontEntry = all.firstOrNull { it.id == id } ?: bundled.first()

    fun familyOf(id: String): FontFamily {
        val entry = byId(id)
        entry.family?.let { return it }
        val res = entry.resource ?: R.font.jetbrains_mono
        return FontFamily(Font(res))
    }

    /** CSS font stack for xterm.js. */
    fun cssStack(id: String): String {
        val entry = byId(id)
        val name = entry.cssName ?: "monospace"
        return "'$name', 'JetBrains Mono', monospace"
    }
}
