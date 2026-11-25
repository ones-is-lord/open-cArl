package dev.opencarl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val vm: OpenCarlViewModel = viewModel()
        val settings by vm.container.settings.settings.collectAsStateWithLifecycle()
        var splash by remember { mutableStateOf(true) }
        Surface(Modifier.fillMaxSize(), color = dev.opencarl.appearance.Neon.Black) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .background(dev.opencarl.appearance.Neon.Black),
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
