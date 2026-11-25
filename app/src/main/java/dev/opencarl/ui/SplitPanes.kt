package dev.opencarl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.OpenCarlViewModel
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.ui.neon.FrameCyan
import dev.opencarl.ui.neon.NeonFrame
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal

enum class PaneMode { TERMINAL, CHAT, SPLIT }

/** One CLI box: same frame, same label treatment, whichever tool lives inside. */
@Composable
fun CliBox(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NeonFrame(modifier.padding(3.dp), corner = 14.dp, borderWidth = 1.4.dp, grunge = false) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 3.dp, bottom = 1.dp)) {
                Text(
                    text = label.uppercase(),
                    fontSize = 8.sp,
                    letterSpacing = 1.4.sp,
                    fontFamily = FontFamily.Monospace,
                    color = FrameCyan,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** Two of the same box: agent on top, shell underneath, or side by side when wide. */
@Composable
fun SplitPanes(
    vm: OpenCarlViewModel,
    appearance: AppearanceSettings,
    stacked: Boolean,
) {
    if (stacked) {
        Column(Modifier.fillMaxSize()) {
            CliBox("agent", Modifier.weight(1f)) {
                ChatPanel(vm = vm, appearance = appearance, onClose = {})
            }
            CliBox("terminal", Modifier.weight(1f)) {
                TerminalPane(container = vm.container, appearance = appearance, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            CliBox("agent", Modifier.weight(1f)) {
                ChatPanel(vm = vm, appearance = appearance, onClose = {})
            }
            CliBox("terminal", Modifier.weight(1f)) {
                TerminalPane(container = vm.container, appearance = appearance, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Standard Android bottom navigation, so the app behaves like an Android app. */
@Composable
fun PaneBar(
    mode: PaneMode,
    onMode: (PaneMode) -> Unit,
    onGuide: () -> Unit,
    onMore: () -> Unit,
) {
    val items = listOf(
        Triple("Terminal", Icons.Filled.Terminal, PaneMode.TERMINAL),
        Triple("Chat", Icons.Filled.SmartToy, PaneMode.CHAT),
        Triple("Split", Icons.Filled.Dashboard, PaneMode.SPLIT),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { (label, icon: ImageVector, target) ->
            NavigationBarItem(
                selected = mode == target,
                onClick = { onMode(target) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 9.sp) },
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = onGuide,
            icon = { Icon(Icons.Filled.MenuBook, contentDescription = "Guide") },
            label = { Text("Guide", fontSize = 9.sp) },
        )
        NavigationBarItem(
            selected = false,
            onClick = onMore,
            icon = { Icon(Icons.Filled.Menu, contentDescription = "More") },
            label = { Text("More", fontSize = 9.sp) },
        )
    }
}
