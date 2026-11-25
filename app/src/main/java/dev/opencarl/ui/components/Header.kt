package dev.opencarl.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.ui.neon.CarlTitleBar
import dev.opencarl.ui.neon.FrameStops

/**
 * The window chrome from the reference art: avatar, wordmark, rule, tagline and
 * the three controls, with a gradient hairline under the bar.
 */
@Composable
fun CarlHeader(
    appearance: AppearanceSettings,
    subtitle: String,
    onMenu: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = onChat,
) {
    Column(modifier.fillMaxWidth()) {
        CarlTitleBar(
            appearance = appearance,
            subtitle = subtitle,
            onAvatar = onMenu,
            onMinimize = onChat,
            onMaximize = onSettings,
            onClose = onClose,
        )
        Canvas(Modifier.fillMaxWidth().height(1.5.dp)) {
            drawRect(brush = Brush.horizontalGradient(FrameStops))
        }
        Text(
            text = subtitle,
            fontSize = 8.sp,
            color = androidx.compose.ui.graphics.Color(0xFF8FA6B2),
            maxLines = 1,
            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}
