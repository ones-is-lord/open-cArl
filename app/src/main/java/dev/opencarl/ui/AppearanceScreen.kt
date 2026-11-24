package dev.opencarl.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.appearance.Palette32
import dev.opencarl.ui.components.ColorSwatch
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle

@Composable
fun AppearanceScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    ScreenScaffold("Appearance", appearance, onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("banner")
            OutlinedTextField(
                value = appearance.bannerText,
                onValueChange = { text -> container.settings.updateAppearance { it.copy(bannerText = text) } },
                label = { Text("Banner text", fontSize = 12.sp) },
                placeholder = { Text("open cArl", fontSize = 12.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("fonts")
            FontPicker("Interface", appearance.uiFont) { id ->
                container.settings.updateAppearance { it.copy(uiFont = id) }
            }
            FontPicker("Terminal", appearance.terminalFont) { id ->
                container.settings.updateAppearance { it.copy(terminalFont = id) }
            }
            FontPicker("Banner", appearance.bannerFont) { id ->
                container.settings.updateAppearance { it.copy(bannerFont = id) }
            }
            FontPicker("Header", appearance.headerFont) { id ->
                container.settings.updateAppearance { it.copy(headerFont = id) }
            }

            SectionTitle("colors")
            ColorRole.entries.forEach { role ->
                ColorRoleRow(
                    role = role,
                    current = appearance.colorIdOf(role),
                    onPick = { id -> container.settings.updateAppearance { it.withColor(role, id) } },
                )
            }

            SectionTitle("terminal")
            Text("Font size: ${appearance.terminalFontSize}sp", fontSize = 12.sp)
            Slider(
                value = appearance.terminalFontSize.toFloat(),
                onValueChange = { value ->
                    container.settings.updateAppearance { it.copy(terminalFontSize = value.toInt()) }
                },
                valueRange = 9f..26f,
                steps = 16,
            )
            Text("Outline: ${appearance.outlineWidthDp}dp", fontSize = 12.sp)
            Slider(
                value = appearance.outlineWidthDp.toFloat(),
                onValueChange = { value ->
                    container.settings.updateAppearance { it.copy(outlineWidthDp = value.toInt()) }
                },
                valueRange = 0f..4f,
                steps = 3,
            )
            Text(
                "The outline wraps the terminal and every settings page.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FontPicker(label: String, current: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val entry = FontLibrary.byId(current)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = true }) {
            Text(
                entry.label,
                fontSize = 12.sp,
                fontFamily = FontLibrary.familyOf(current),
                fontWeight = FontWeight.Medium,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FontLibrary.all.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontFamily = FontLibrary.familyOf(option.id),
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        onPick(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorRoleRow(role: ColorRole, current: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(Palette32.byId(current), selected = true, onClick = {})
            Text(
                role.label,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "  ${Palette32.byId(current).name}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Palette32.colors.forEach { color ->
                ColorSwatch(color, selected = color.id == current, onClick = { onPick(color.id) })
            }
        }
    }
}
