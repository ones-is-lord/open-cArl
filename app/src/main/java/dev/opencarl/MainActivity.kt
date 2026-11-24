package dev.opencarl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
        Surface(Modifier.fillMaxSize()) {
            OpenCarlApp(vm)
        }
    }
}
