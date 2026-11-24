package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.opencarl.data.ApprovalPolicy
import dev.opencarl.data.ProviderConfig
import dev.opencarl.data.ProviderKind
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import java.util.UUID

@Composable
fun ProvidersScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateSafe()
    ScreenScaffold("Providers", appearance, onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("active provider")
            settings.providers.forEach { provider ->
                ProviderRow(
                    container = container,
                    provider = provider,
                    active = provider.id == settings.activeProviderId,
                    appearance = appearance,
                )
            }
            TextButton(onClick = {
                val id = "custom-" + UUID.randomUUID().toString().take(6)
                container.settings.update { current ->
                    current.copy(
                        providers = current.providers + ProviderConfig(
                            id = id,
                            name = "Custom",
                            baseUrl = "https://api.example.com/v1",
                            model = "model-name",
                        ),
                        activeProviderId = id,
                    )
                }
            }) { Text("+ add custom provider", fontSize = 12.sp) }

            SectionTitle("agent")
            Text("Approval policy", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ApprovalPolicy.entries.forEach { policy ->
                    FilterChip(
                        selected = settings.approvalPolicy == policy,
                        onClick = { container.settings.update { it.copy(approvalPolicy = policy) } },
                        label = { Text(policy.name.lowercase(), fontSize = 10.sp) },
                    )
                }
            }
            Text("Max steps per turn: ${settings.maxSteps}", fontSize = 12.sp)
            Slider(
                value = settings.maxSteps.toFloat(),
                onValueChange = { value -> container.settings.update { it.copy(maxSteps = value.toInt()) } },
                valueRange = 4f..60f,
                steps = 55,
            )
            Text(
                "AUTO lets the agent run shell commands without asking. SMART asks only for destructive ones.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderRow(
    container: AppContainer,
    provider: ProviderConfig,
    active: Boolean,
    appearance: AppearanceSettings,
) {
    var expanded by remember { mutableStateOf(false) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var model by remember(provider.id) { mutableStateOf(provider.model) }
    var apiKey by remember(provider.id) { mutableStateOf(container.settings.apiKey(provider.id)) }
    var maxTokens by remember(provider.id) { mutableStateOf(provider.maxTokens.toString()) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (active) appearance.colorOf(ColorRole.ACCENT) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = active,
                onClick = { container.settings.update { it.copy(activeProviderId = provider.id) } },
            )
            Column(Modifier.weight(1f)) {
                Text(provider.name, fontSize = 13.sp)
                Text(
                    "${provider.kind.name.lowercase()} · ${provider.model}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "close" else "edit", fontSize = 11.sp)
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key (BYOK, stored encrypted)", fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter(Char::isDigit) },
                    label = { Text("Max tokens", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        container.settings.update { current ->
                            current.copy(
                                providers = current.providers.map {
                                    if (it.id == provider.id) {
                                        it.copy(
                                            baseUrl = baseUrl.trim(),
                                            model = model.trim(),
                                            maxTokens = maxTokens.toIntOrNull() ?: it.maxTokens,
                                        )
                                    } else {
                                        it
                                    }
                                },
                            )
                        }
                        container.settings.setApiKey(provider.id, apiKey)
                        expanded = false
                    }) { Text("save", fontSize = 11.sp) }
                    TextButton(onClick = {
                        container.settings.update { current ->
                            val remaining = current.providers.filterNot { it.id == provider.id }
                            current.copy(
                                providers = remaining,
                                activeProviderId = if (current.activeProviderId == provider.id) {
                                    remaining.firstOrNull()?.id.orEmpty()
                                } else {
                                    current.activeProviderId
                                },
                            )
                        }
                    }) { Text("remove", fontSize = 11.sp) }
                }
            }
        }
    }
}

private fun ProviderKind.label(): String = name.lowercase()
