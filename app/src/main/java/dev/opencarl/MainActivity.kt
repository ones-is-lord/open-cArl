package dev.opencarl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.opencarl.app.OpenCarlViewModel
import dev.opencarl.ui.OpenCarlApp


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.opencarl.diag.CrashLog.install(this)
        enableEdgeToEdge()
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val vm: OpenCarlViewModel = viewModel()
        val settings by vm.container.settings.settings.collectAsStateWithLifecycle()
        var splash by remember { mutableStateOf(true) }
        val crash = remember { dev.opencarl.diag.CrashLog.latest(this) }
        var showCrash by remember { mutableStateOf(crash != null) }
        if (showCrash && crash != null) {
            dev.opencarl.ui.CrashScreen(
                report = crash,
                onContinue = { showCrash = false },
                onDismiss = {
                    dev.opencarl.diag.CrashLog.clear(this)
                    showCrash = false
                },
            )
            return
        }
        Surface(Modifier.fillMaxSize(), color = dev.opencarl.appearance.Neon.Black) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(dev.opencarl.appearance.Neon.Black)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                if (splash) {
                    dev.opencarl.ui.neon.CarlSplash(settings.appearance) { splash = false }
                } else {
                    OpenCarlApp(vm)
                    dev.opencarl.ui.neon.NeonFrameOverlay(Modifier.fillMaxSize())
                }
            }
        }
    }
}
