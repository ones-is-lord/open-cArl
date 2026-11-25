package dev.opencarl.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.diag.CrashLog
import dev.opencarl.ui.components.ScreenScaffold

/**
 * Everything needed to explain a failure from a screenshot: what the device is,
 * whether the native shell loaded, whether the rootfs exists, and the last
 * crash trace verbatim.
 */
@Composable
fun DiagnosticsScreen(
    container: AppContainer,
    appearance: AppearanceSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val facts = remember {
        buildList {
            add("app" to (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"))
            add("package" to context.packageName)
            add("android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            add("target sdk" to runCatching {
                context.packageManager.getApplicationInfo(context.packageName, 0).targetSdkVersion.toString()
            }.getOrNull().orEmpty())
            add("device" to "${Build.MANUFACTURER} ${Build.MODEL}")
            add("abi" to Build.SUPPORTED_ABIS.joinToString(", "))
            add("native pty" to runCatching {
                dev.opencarl.terminal.NativePty
                "loadable"
            }.getOrElse { "FAILED: ${it.message}" })
            add("runtime" to container.runtime.label(container.settings.settings.value.runtime))
            add("proot rootfs" to if (container.runtime.prootInstalled()) "installed" else "not installed")
            add("shell" to if (container.ensureSession()?.isRunning == true) "running" else "not running")
            add("files dir" to (if (context.filesDir.canWrite()) "writable" else "NOT writable"))
            add("free storage" to runCatching {
                val stat = android.os.StatFs(context.filesDir.absolutePath)
                "${stat.availableBytes / (1024 * 1024)} MB"
            }.getOrElse { "?" })
        }
    }
    val crash = remember { CrashLog.latest(context) }

    ScreenScaffold(title = "Diagnostics", appearance = appearance, onBack = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            facts.forEach { (key, value) ->
                Text(
                    text = "$key: $value",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (crash != null) {
                Text(
                    text = "last crash",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = crash.take(6_000),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    val report = buildString {
                        facts.forEach { (k, v) -> appendLine("$k: $v") }
                        if (crash != null) appendLine().append(crash)
                    }
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("open cArl diagnostics", report))
                },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Copy report") }
        }
    }
}
