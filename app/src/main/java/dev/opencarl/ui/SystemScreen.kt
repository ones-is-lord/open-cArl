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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.device.ModelRecommender
import dev.opencarl.device.Recommendation
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.scripts.ScriptLibrary
import dev.opencarl.scripts.ScriptRisk
import dev.opencarl.service.CarlAgentService
import dev.opencarl.system.RootStatus
import dev.opencarl.system.StorageReport
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { DEVICE("Device"), POWER("Power"), SCRIPTS("Scripts"), STORAGE("Storage"), ROOT("Root") }

@Composable
fun SystemScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.DEVICE) }
    var log by remember { mutableStateOf("") }

    val profile = remember { container.profiler.profile() }
    val recommendations = remember(profile) { ModelRecommender.recommend(profile, limit = 5) }
    val privilege by container.privilege.status.collectAsStateSafe()
    val background by CarlAgentService.running.collectAsStateSafe()
    var rootStatus by remember { mutableStateOf(RootStatus()) }
    var report by remember { mutableStateOf<StorageReport?>(null) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.privilege.register()
        container.privilege.connectShizuku()
        rootStatus = container.rootTools.status()
    }

    val modulePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val file = java.io.File(container.appContext.cacheDir, "module.zip")
                container.appContext.contentResolver.openInputStream(it)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                log = container.rootTools.installModule(file)
            }
        }
    }

    ScreenScaffold("System", appearance, onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Tab.entries.forEach { entry ->
                    FilterChip(selected = tab == entry, onClick = { tab = entry }, label = { Text(entry.label, fontSize = 10.sp) })
                }
            }

            when (tab) {
                Tab.DEVICE -> {
                    SectionTitle("this phone")
                    Text(profile.summary, fontSize = 11.sp)
                    Text("tier ${profile.tier.label} — ${profile.tier.blurb}", fontSize = 10.sp, color = appearance.colorOf(ColorRole.HIGHLIGHT))
                    Text("battery ${container.profiler.batteryPercent()}% · thermal ${container.profiler.thermalStatus()}", fontSize = 10.sp)
                    SectionTitle("models that fit")
                    recommendations.forEach { rec -> RecommendationRow(container, rec, appearance) { log = it } }
                    if (recommendations.isEmpty()) {
                        Text("Nothing local fits. Point a provider at a remote llama.cpp server instead.", fontSize = 10.sp)
                    }
                }

                Tab.POWER -> {
                    SectionTitle("privilege")
                    StatusLine("Shizuku installed", privilege.shizukuInstalled)
                    StatusLine("Shizuku running", privilege.shizukuRunning)
                    StatusLine("Shizuku granted", privilege.shizukuGranted)
                    StatusLine("root available", privilege.rootAvailable)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { container.privilege.refresh() }) { Text("refresh", fontSize = 10.sp) }
                        OutlinedButton(onClick = {
                            container.privilege.requestShizuku()
                            container.privilege.connectShizuku()
                        }) { Text("grant via Shizuku", fontSize = 10.sp) }
                    }
                    if (!privilege.shizukuInstalled) {
                        Text("Install Shizuku, start it over wireless debugging, then grant access here.", fontSize = 9.sp)
                    }
                    SectionTitle("background")
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(checked = background, onCheckedChange = { on ->
                            if (on) CarlAgentService.start(container.appContext) else CarlAgentService.stop(container.appContext)
                            container.settings.update { it.copy(backgroundServiceEnabled = on) }
                        })
                        Text("  keep running with the screen off", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(
                            checked = settings.autoUpdateOnLaunch,
                            onCheckedChange = { on -> container.settings.update { it.copy(autoUpdateOnLaunch = on) } },
                        )
                        Text("  update packages and skills on launch", fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            log = "checking…"
                            log = container.updates.runAll(container.updates.versionName()).summary()
                        }
                    }) { Text("update everything now", fontSize = 10.sp) }
                    SectionTitle("accessibility")
                    Text(
                        if (dev.opencarl.service.CarlAccessibilityService.isConnected) "service connected"
                        else "service off — enable it in Settings → Accessibility",
                        fontSize = 10.sp,
                    )
                }

                Tab.SCRIPTS -> {
                    SectionTitle("maintenance scripts")
                    ScriptLibrary.scripts.forEach { script ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                        ) {
                            Text("${script.name}  ·  ${script.risk.name.lowercase()}  ·  ${script.needs.label}", fontSize = 11.sp)
                            Text(script.summary, fontSize = 9.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    scope.launch { log = container.scriptRunner.run(script, dryRun = true).report() }
                                }) { Text("preview", fontSize = 9.sp) }
                                TextButton(onClick = {
                                    scope.launch { log = container.scriptRunner.run(script).report() }
                                }) { Text("run", fontSize = 9.sp) }
                            }
                            if (script.risk == ScriptRisk.DANGEROUS) {
                                Text("irreversible-ish: read the preview first", fontSize = 9.sp, color = appearance.colorOf(ColorRole.BANNER))
                            }
                        }
                    }
                }

                Tab.STORAGE -> {
                    SectionTitle("storage triage")
                    Button(onClick = {
                        scanning = true
                        scope.launch {
                            report = container.storage.scan()
                            scanning = false
                        }
                    }, enabled = !scanning) { Text(if (scanning) "scanning…" else "scan storage", fontSize = 11.sp) }
                    report?.let { result ->
                        Text(result.summary(), fontSize = 10.sp)
                        TextButton(onClick = {
                            scope.launch { log = "removed ${container.storage.clean(result)} files" }
                        }) { Text("delete safe duplicates and empty files", fontSize = 10.sp) }
                        result.issues.take(12).forEach { issue ->
                            Text("${issue.kind.name.lowercase()} · ${issue.path}", fontSize = 9.sp)
                        }
                    }
                }

                Tab.ROOT -> {
                    SectionTitle("root and recovery")
                    Text("manager: ${rootStatus.manager} · slot ${rootStatus.bootSlot.ifBlank { "n/a" }}", fontSize = 11.sp)
                    if (rootStatus.modules.isNotEmpty()) {
                        Text("modules: ${rootStatus.modules.joinToString(", ")}", fontSize = 9.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { modulePicker.launch(arrayOf("*/*")) }) { Text("install module zip", fontSize = 10.sp) }
                        OutlinedButton(onClick = { scope.launch { rootStatus = container.rootTools.status() } }) {
                            Text("refresh", fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = { log = container.rootTools.recoveryGuide(rootStatus) }) {
                        Text("show recovery plan", fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { log = container.rootTools.reboot("recovery") } }) {
                            Text("reboot to recovery", fontSize = 10.sp)
                        }
                        TextButton(onClick = { scope.launch { log = container.rootTools.reboot("bootloader") } }) {
                            Text("reboot to bootloader", fontSize = 10.sp)
                        }
                    }
                    if (!rootStatus.rootGranted) {
                        Text("No root detected. Shizuku still covers pm, settings and dumpsys.", fontSize = 9.sp)
                    }
                }
            }

            if (log.isNotBlank()) {
                Text(
                    log.take(6_000),
                    fontSize = 9.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row {
        Text(if (ok) "●" else "○", fontSize = 11.sp, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  $label", fontSize = 10.sp)
    }
}

@Composable
private fun RecommendationRow(
    container: AppContainer,
    rec: Recommendation,
    appearance: AppearanceSettings,
    onLog: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
    ) {
        Text("${rec.model.label} · ${rec.headline}", fontSize = 11.sp)
        Text(rec.reason, fontSize = 9.sp)
        Text(rec.model.bestFor, fontSize = 9.sp, color = appearance.colorOf(ColorRole.HIGHLIGHT))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = !busy && rec.fit != dev.opencarl.device.Fit.NO,
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            val file = container.models.download(rec.model) { done, total ->
                                if (total > 0) progress = done.toFloat() / total
                            }
                            onLog("downloaded ${file.name} (${StorageReport.human(file.length())})")
                        }.onFailure { onLog("download failed: ${it.message}") }
                        busy = false
                    }
                },
            ) { Text(if (busy) "downloading ${(progress * 100).toInt()}%" else "download", fontSize = 9.sp) }
            TextButton(onClick = {
                val installed = container.models.installed().firstOrNull { it.name == rec.model.file }
                onLog(
                    if (installed == null) "download the model first"
                    else "run this in the terminal:\n" + container.models.serveCommand(
                        installed,
                        port = container.settings.settings.value.localServerPort,
                        threads = (container.profiler.profile().cores / 2).coerceAtLeast(2),
                    ),
                )
            }) { Text("serve command", fontSize = 9.sp) }
        }
    }
}
