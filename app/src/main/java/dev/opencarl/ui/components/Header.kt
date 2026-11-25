package dev.opencarl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.appearance.NeonBannerText

/**
 * Static header: present on every screen, banner text and all chrome colors
 * come from the appearance settings, and the thin outline follows the user's
 * width preference.
 */
@Composable
fun CarlHeader(
    appearance: AppearanceSettings,
    subtitle: String,
    onMenu: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = appearance.colorOf(ColorRole.OUTLINE)
    val width = appearance.outlineWidthDp.coerceIn(0, 4).dp
    val bannerFont = FontLibrary.familyOf(appearance.bannerFont)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = appearance.colorOf(ColorRole.ACCENT))
            }
            Column(Modifier.weight(1f)) {
                if (appearance.bannerVisible) {
                    NeonBannerText(
                        text = appearance.bannerLabel,
                        appearance = appearance,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                }
                Text(
                    text = subtitle,
                    fontFamily = FontLibrary.familyOf(appearance.headerFont),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onChat) {
                Icon(Icons.Filled.AddComment, contentDescription = "Agent", tint = appearance.colorOf(ColorRole.ACCENT))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Appearance", tint = appearance.colorOf(ColorRole.HIGHLIGHT))
            }
        }
        if (width > 0.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(width)
                    .background(outline),
            )
        }
    }
}
