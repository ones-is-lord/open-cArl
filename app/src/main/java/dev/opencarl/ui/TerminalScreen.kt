package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.appearance.Palette32
import dev.opencarl.terminal.TerminalWebView
import dev.opencarl.ui.components.KeyBar

/**
 * The terminal itself. The WebView stays composed for the whole session so
 * scrollback and running programs survive navigation; settings screens are
 * drawn on top of it instead of replacing it.
 */
@Composable
fun TerminalPane(
    container: AppContainer,
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val terminal = remember { TerminalWebView(context) }
    var readyTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { terminal.destroy() }
    }

    LaunchedEffect(Unit) {
        terminal.onInput = { text -> container.writeTerminal(text) }
        terminal.onBinary = { bytes -> container.writeTerminalBytes(bytes) }
        terminal.onResize = { cols, rows -> container.resizeTerminal(cols, rows) }
        terminal.onReady = { _, _ ->
            readyTick++
            container.ensureSession()
        }
        container.ensureSession()
    }

    LaunchedEffect(Unit) {
        container.terminalOutput.collect { text -> terminal.write(text) }
    }

    LaunchedEffect(
        appearance.terminalFont,
        appearance.terminalFontSize,
        appearance.terminalBackground,
        appearance.text,
        appearance.cursor,
        readyTick,
    ) {
        terminal.applyTheme(
            fontFamily = FontLibrary.cssStack(appearance.terminalFont),
            fontSize = appearance.terminalFontSize,
            background = Palette32.hex(appearance.terminalBackground),
            foreground = Palette32.hex(appearance.text),
            cursor = Palette32.hex(appearance.cursor),
            selection = Palette32.hex("charcoal"),
        )
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            AndroidView(factory = { terminal }, modifier = Modifier.fillMaxSize())
        }
        KeyBar(
            appearance = appearance,
            onKey = { sequence -> container.writeTerminal(sequence) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )
        Box(Modifier.fillMaxWidth().height(appearance.outlineWidthDp.coerceIn(0, 4).dp)
            .background(appearance.colorOf(dev.opencarl.appearance.ColorRole.OUTLINE)))
    }
}
