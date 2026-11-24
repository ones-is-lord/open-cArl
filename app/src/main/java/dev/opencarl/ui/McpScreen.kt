package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import dev.opencarl.data.McpServerConfig
import dev.opencarl.data.SettingsRepository
import dev.opencarl.mcp.McpCatalog
import dev.opencarl.mcp.McpStatus
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun McpScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateSafe()
    val states by container.mcp.states.collectAsStateSafe()
    val scope = rememberCoroutineScope()
    var github by remember { mutableStateOf(container.settings.secret(SettingsRepository.SECRET_GITHUB)) }
    var tavily by remember { mutableStateOf(container.settings.secret(SettingsRepository.SECRET_TAVILY)) }
    var testResult by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("MCP servers", appearance, onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("integration secrets")
            OutlinedTextField(
                value = github,
                onValueChange = {
                    github = it
                    container.settings.setSecret(SettingsRepository.SECRET_GITHUB, it)
                },
                label = { Text("GitHub token", fontSize = 11.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tavily,
                onValueChange = {
                    tavily = it
                    container.settings.setSecret(SettingsRepository.SECRET_TAVILY, it)
                },
                label = { Text("Tavily API key", fontSize = 11.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch { container.refreshMcp(container.buildRegistry()) }
                }) { Text("connect all", fontSize = 12.sp) }
                TextButton(onClick = { container.mcp.shutdown() }) {
                    Text("disconnect", fontSize = 12.sp)
                }
            }

            SectionTitle("configured")
            settings.mcpServers.forEach { server ->
                val state = states.firstOrNull { it.id == server.id }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(server.name, fontSize = 13.sp)
                            Text(
                                server.transport.name.lowercase() + " · " + when (state?.status) {
                                    McpStatus.CONNECTED -> "connected · " + state.toolCount + " tools"
                                    McpStatus.CONNECTING -> "connecting…"
                                    McpStatus.ERROR -> "error: " + state.message.take(80)
                                    else -> "offline"
                                },
                                fontSize = 9.sp,
                                color = when (state?.status) {
                                    McpStatus.CONNECTED -> appearance.colorOf(ColorRole.ACCENT)
                                    McpStatus.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { enabled ->
                                container.settings.update { current ->
                                    current.copy(
                                        mcpServers = current.mcpServers.map {
                                            if (it.id == server.id) it.copy(enabled = enabled) else it
                                        },
                                    )
                                }
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch { testResult = container.mcp.test(server, secretsOf(container)) }
                        }) { Text("test", fontSize = 10.sp) }
                        TextButton(onClick = {
                            container.settings.update { current ->
                                current.copy(mcpServers = current.mcpServers.filterNot { it.id == server.id })
                            }
                        }) { Text("remove", fontSize = 10.sp) }
                    }
                }
            }

            SectionTitle("add from catalog")
            McpCatalog.presets
                .filter { preset -> settings.mcpServers.none { it.id == preset.id } }
                .forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontSize = 12.sp)
                            Text(
                                preset.description,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (preset.requires.isNotBlank()) {
                                Text(
                                    "needs: " + preset.requires,
                                    fontSize = 9.sp,
                                    color = appearance.colorOf(ColorRole.BANNER),
                                )
                            }
                        }
                        TextButton(onClick = {
                            val config = McpServerConfig(
                                id = preset.id,
                                name = preset.name,
                                transport = preset.transport,
                                url = preset.url,
                                command = preset.command,
                                args = preset.args,
                                env = preset.env,
                                headers = preset.headers,
                                presetId = preset.id,
                            )
                            container.settings.update { current ->
                                current.copy(mcpServers = current.mcpServers + config)
                            }
                        }) { Text("add", fontSize = 10.sp) }
                    }
                }
        }
    }

    testResult?.let { result ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = { Text("MCP test", fontSize = 14.sp) },
            text = { Text(result, fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("ok") } },
        )
    }
}

private fun secretsOf(container: AppContainer): Map<String, String> = mapOf(
    "GITHUB_TOKEN" to container.settings.secret(SettingsRepository.SECRET_GITHUB),
    "TAVILY_API_KEY" to container.settings.secret(SettingsRepository.SECRET_TAVILY),
    "HF_TOKEN" to container.settings.secret(SettingsRepository.SECRET_HF),
)
