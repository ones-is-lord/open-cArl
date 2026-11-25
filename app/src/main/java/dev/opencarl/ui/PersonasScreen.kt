package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.persona.Persona
import dev.opencarl.persona.Personas
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle

@Composable
fun PersonasScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateSafe()
    var name by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    ScreenScaffold("Personas", appearance, onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("voice")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(
                    checked = settings.speakReplies,
                    onCheckedChange = { on -> container.settings.update { it.copy(speakReplies = on) } },
                )
                Text("  read replies aloud (text out only, no microphone)", fontSize = 11.sp)
            }
            TextButton(onClick = { container.speaker.stop() }) { Text("stop speaking", fontSize = 10.sp) }

            SectionTitle("who is talking")
            Personas.all(settings.customPersonas).forEach { persona ->
                val selected = persona.id == settings.personaId
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) appearance.colorOf(ColorRole.HIGHLIGHT).copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { container.settings.update { it.copy(personaId = persona.id) } }
                        .padding(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            (if (selected) "● " else "○ ") + persona.name,
                            fontSize = 12.sp,
                        )
                        if (!persona.builtin) {
                            TextButton(onClick = {
                                container.settings.update { current ->
                                    current.copy(
                                        customPersonas = current.customPersonas.filterNot { it.id == persona.id },
                                        personaId = if (current.personaId == persona.id) Personas.DEFAULT_ID else current.personaId,
                                    )
                                }
                            }) { Text("delete", fontSize = 9.sp) }
                        }
                    }
                    Text(persona.tagline, fontSize = 9.sp)
                }
            }

            SectionTitle("custom persona")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("name", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tagline,
                onValueChange = { tagline = it },
                label = { Text("one-line tagline", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("how it should talk and behave", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            TextButton(
                enabled = name.isNotBlank() && prompt.isNotBlank(),
                onClick = {
                    val persona = Persona(
                        id = "custom-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                        name = name.trim(),
                        tagline = tagline.trim(),
                        prompt = prompt.trim(),
                        builtin = false,
                    )
                    container.settings.update { current ->
                        current.copy(
                            customPersonas = current.customPersonas.filterNot { it.id == persona.id } + persona,
                            personaId = persona.id,
                        )
                    }
                    name = ""
                    tagline = ""
                    prompt = ""
                },
            ) { Text("add and switch to it", fontSize = 10.sp) }
            Text(
                "Personas change tone and method, never the rules: no crime, no harm to other people, no lying about results.",
                fontSize = 9.sp,
            )
        }
    }
}
