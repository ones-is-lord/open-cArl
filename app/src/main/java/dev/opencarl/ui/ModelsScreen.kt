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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.data.SettingsRepository
import dev.opencarl.models.HfClient
import dev.opencarl.models.HfModel
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ModelsScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hf = remember { HfClient(container.http) { container.settings.secret(SettingsRepository.SECRET_HF) } }
    val modelsDir = remember { File(container.appContext.filesDir, "models").apply { mkdirs() } }
    var query by remember { mutableStateOf("qwen2.5-coder gguf") }
    var token by remember { mutableStateOf(container.settings.secret(SettingsRepository.SECRET_HF)) }
    var results by remember { mutableStateOf<List<HfModel>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var local by remember { mutableStateOf(modelsDir.walkTopDown().filter { it.isFile }.toList()) }

    ScreenScaffold("Models", appearance, onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("hugging face")
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    container.settings.setSecret(SettingsRepository.SECRET_HF, it)
                },
                label = { Text("HF token (optional, for gated repos)", fontSize = 11.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Search GGUF models", fontSize = 11.sp) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
                TextButton(enabled = query.isNotBlank(), onClick = {
                    scope.launch {
                        status = "searching…"
                        runCatching { hf.search(query) }
                            .onSuccess { results = it; status = it.size.toString() + " results" }
                            .onFailure { status = "search failed: " + it.message }
                    }
                }) { Text("search", fontSize = 11.sp) }
            }
            Text(status, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            results.forEach { model ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(model.id, fontSize = 12.sp)
                        Text(
                            "↓ ${model.downloads} · ♥ ${model.likes}" + (model.pipelineTag?.let { " · $it" } ?: ""),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            status = "resolving " + model.id
                            runCatching {
                                val files = hf.files(model.id)
                                val pick = files.firstOrNull { it.contains("Q4_K_M") } ?: files.firstOrNull()
                                    ?: error("no GGUF file in this repo")
                                status = "downloading " + pick
                                hf.download(model.id, pick, File(modelsDir, model.id.replace('/', '_'))) { done, total ->
                                    status = if (total > 0) {
                                        "downloading " + (done * 100 / total) + "%"
                                    } else {
                                        "downloading " + (done / 1_048_576) + " MiB"
                                    }
                                }
                            }.onSuccess {
                                status = "saved " + it.name
                                local = modelsDir.walkTopDown().filter { f -> f.isFile }.toList()
                            }.onFailure { status = "failed: " + it.message }
                        }
                    }) { Text("download", fontSize = 10.sp) }
                }
            }

            SectionTitle("on device")
            if (local.isEmpty()) {
                Text("No models yet. Downloaded GGUF files appear here.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            local.forEach { file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, fontSize = 12.sp)
                        Text(
                            (file.length() / 1_048_576).toString() + " MiB · " + file.parentFile?.name,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { file.delete(); local = modelsDir.walkTopDown().filter { f -> f.isFile }.toList() }) {
                        Text("delete", fontSize = 10.sp)
                    }
                }
            }
            Text(
                "Serve a model to the agent with llama.cpp: install llama-server in the Linux userland, run it on 127.0.0.1:8080, then pick the llama.cpp provider.",
                fontSize = 10.sp,
                color = appearance.colorOf(ColorRole.HIGHLIGHT),
            )
        }
    }
}
