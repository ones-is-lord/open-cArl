package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.ui.components.KeyBar
import dev.opencarl.ui.terminal.TerminalSurface

/**
 * The terminal screen: a native VT renderer over the live PTY, plus the
 * accessory key row. The surface stays composed for the whole session.
 */
@Composable
fun TerminalPane(
    container: AppContainer,
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { container.ensureSession() }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            TerminalSurface(
                emulator = container.emulator,
                appearance = appearance,
                onInput = { text -> container.writeTerminal(text) },
                onResize = { cols, rows -> container.resizeTerminal(cols, rows) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        KeyBar(
            appearance = appearance,
            onKey = { sequence -> container.writeTerminal(sequence) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(appearance.outlineWidthDp.coerceIn(0, 4).dp)
                .background(appearance.colorOf(ColorRole.OUTLINE)),
        )
    }
}
