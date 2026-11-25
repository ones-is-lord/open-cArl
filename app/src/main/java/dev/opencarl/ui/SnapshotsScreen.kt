package dev.opencarl.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.snapshot.Snapshot
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun SnapshotsScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf(container.snapshots.list()) }
    var log by remember { mutableStateOf("") }

    ScreenScaffold("Snapshots", appearance, onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("save the workspace")
            Text(
                "A snapshot copies the workspace files, the visible terminal and the conversation. "
                    + "Restoring replaces the workspace, so diff first.",
                fontSize = 9.sp,
            )
            Button(onClick = {
                scope.launch {
                    val snap = container.snapshots.create(
                        name = "manual",
                        note = "from the snapshots screen",
                        transcript = container.emulator.visibleText(),
                        chatJson = dev.opencarl.app.ChatMirror.json(),
                    )
                    snapshots = container.snapshots.list()
                    log = "saved ${snap.id} (${snap.files} files)"
                }
            }) { Text("take a snapshot", fontSize = 11.sp) }

            SectionTitle("history")
            snapshots.forEach { snap -> SnapshotRow(container, snap) { message -> log = message } }
            if (snapshots.isEmpty()) Text("nothing saved yet", fontSize = 10.sp)

            if (log.isNotBlank()) {
                Text(
                    log.take(4_000),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
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
private fun SnapshotRow(container: AppContainer, snap: Snapshot, onLog: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
    ) {
        Text("${snap.whenText}  ·  ${snap.files} files  ·  ${snap.bytes / 1024}KB", fontSize = 10.sp)
        Text(snap.name + if (snap.note.isNotBlank()) " — ${snap.note}" else "", fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                scope.launch {
                    val changes = container.snapshots.diff(snap)
                    onLog(
                        if (changes.isEmpty()) "no changes since ${snap.id}"
                        else changes.joinToString("\n"),
                    )
                }
            }) { Text("diff", fontSize = 9.sp) }
            TextButton(onClick = {
                scope.launch { onLog("restored ${container.snapshots.restore(snap)} files from ${snap.id}") }
            }) { Text("restore", fontSize = 9.sp) }
            TextButton(onClick = {
                scope.launch {
                    val transcript = container.snapshots.transcript(snap)
                    onLog("transcript from ${snap.id}:\n" + transcript.takeLast(1_500))
                }
            }) { Text("transcript", fontSize = 9.sp) }
        }
    }
}
