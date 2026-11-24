package dev.opencarl.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.data.RuntimeBackend
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun RuntimeScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateSafe()
    val runtimeSettings = settings.runtime
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    val prootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                status = runCatching { container.prootInstaller.installProot(it) }
                    .getOrElse { error -> "failed: " + error.message }
            }
        }
    }
    val rootfsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                status = "extracting…"
                status = runCatching {
                    container.prootInstaller.installRootfs(it) { progress -> status = progress }
                }.getOrElse { error -> "failed: " + error.message }
            }
        }
    }

    ScreenScaffold("Runtime", appearance, onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("shell backend")
            RuntimeBackend.entries.forEach { backend ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = runtimeSettings.backend == backend,
                        onClick = {
                            container.settings.update { it.copy(runtime = it.runtime.copy(backend = backend)) }
                            container.restartSession()
                        },
                    )
                    Column {
                        Text(
                            if (backend == RuntimeBackend.DEVICE) "Device shell" else "proot Linux userland",
                            fontSize = 13.sp,
                        )
                        Text(
                            if (backend == RuntimeBackend.DEVICE) {
                                "Android's own /system/bin/sh with toybox tools."
                            } else {
                                "Full Linux distribution inside app storage."
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("proot")
            OutlinedTextField(
                value = runtimeSettings.distro,
                onValueChange = { value ->
                    container.settings.update { it.copy(runtime = it.runtime.copy(distro = value)) }
                },
                label = { Text("Distribution label", fontSize = 11.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            StatusRow("proot binary", container.runtime.prootBinary.exists())
            StatusRow("root filesystem", container.runtime.rootfsDir.list()?.isNotEmpty() == true)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { prootPicker.launch(arrayOf("*/*")) }) {
                    Text("select proot binary", fontSize = 11.sp)
                }
                TextButton(onClick = { rootfsPicker.launch(arrayOf("*/*")) }) {
                    Text("select rootfs .tar.gz/.xz", fontSize = 11.sp)
                }
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                )
            }
            TextButton(onClick = {
                container.restartSession()
                status = "shell restarted: " + container.runtime.label(container.settings.settings.value.runtime)
            }) { Text("restart shell", fontSize = 11.sp) }

            SectionTitle("how to get the pieces")
            Text(
                "1. proot: build or download a static aarch64 proot binary (the Termux proot package ships one as libproot.so; rename it to proot).\n" +
                    "2. rootfs: any tarball works, for example Alpine minirootfs:\n" +
                    "   https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz\n" +
                    "3. Select both files above, switch the backend to proot, restart the shell.",
                fontSize = 10.sp,
                color = appearance.colorOf(ColorRole.HIGHLIGHT),
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "●" else "○", fontSize = 12.sp, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  $label", fontSize = 11.sp)
        Text(
            if (ok) "  ready" else "  missing",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
