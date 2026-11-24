package dev.opencarl.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.opencarl.memory.MemoryEntry
import dev.opencarl.skills.Skill
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SkillsScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val skills by container.skills.skills.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Skill?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { picked -> scope.launch { container.skills.importUri(picked) } }
    }

    ScreenScaffold("Skills", appearance, onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { importer.launch(arrayOf("text/*", "application/octet-stream")) }) {
                Text("+ import .md", fontSize = 12.sp)
            }
            TextButton(onClick = { scope.launch { container.skills.reload() } }) {
                Text("reload", fontSize = 12.sp)
            }
        }
        Text(
            "Skills are markdown files with frontmatter. The index goes into the system prompt; the agent loads a full skill with use_skill.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(skills, key = { it.id }) { skill ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { selected = skill }
                        .padding(8.dp),
                ) {
                    Text(skill.name, fontSize = 13.sp)
                    Text(
                        skill.description.ifBlank { skill.id },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    selected?.let { skill ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(skill.name, fontSize = 14.sp) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(skill.body, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("close") } },
            dismissButton = {
                if (!skill.builtin) {
                    TextButton(onClick = {
                        container.skills.delete(skill)
                        selected = null
                    }) { Text("delete") }
                }
            },
        )
    }
}

@Composable
fun MemoryScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val entries by container.memory.entries.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }

    ScreenScaffold("Memory", appearance, onBack) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("remember that…", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 3,
            )
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    scope.launch { container.memory.add(draft) }
                    draft = ""
                },
            ) { Text("add", fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${entries.size} entries, sequential", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { scope.launch { container.memory.clear() } }) {
                Text("clear all", fontSize = 10.sp)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.id }) { entry: MemoryEntry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("#${entry.seq} · ${entry.kind} · ${dateFormat.format(Date(entry.createdAt))}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.text, fontSize = 12.sp)
                    }
                    TextButton(onClick = { scope.launch { container.memory.delete(entry.id) } }) {
                        Text("✕", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
