package dev.opencarl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.PaletteColor

/** Full-screen settings page with the same static header language as the terminal. */
@Composable
fun ScreenScaffold(
    title: String,
    appearance: AppearanceSettings,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = appearance.colorOf(ColorRole.ACCENT),
                    )
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(appearance.outlineWidthDp.coerceIn(0, 4).dp)
                    .background(appearance.colorOf(ColorRole.OUTLINE)),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun ColorSwatch(
    paletteColor: PaletteColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(paletteColor.color)
            .border(if (selected) 2.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.9f else 0.25f), CircleShape),
    ) {
        if (selected) {
            Text(
                "✓",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 12.sp,
                color = if (paletteColor.color.luminance() > 0.6f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue
