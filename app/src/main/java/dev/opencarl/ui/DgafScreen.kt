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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.app.AppContainer
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.ui.components.ScreenScaffold
import dev.opencarl.ui.components.SectionTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CONSENT = "I agree and understand the risk of using this mode"

/**
 * D.G.A.F. is the "do whatever you want" mode. It is behind two separate
 * consent dialogs, each requiring the same explicit phrase, and it can be
 * revoked at any moment from this screen or the header chip.
 */
@Composable
fun DgafScreen(container: AppContainer, appearance: AppearanceSettings, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateSafe()
    var stage by remember { mutableStateOf(0) }
    var acknowledged by remember { mutableStateOf(false) }

    ScreenScaffold("D.G.A.F.", appearance, onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("status")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
            ) {
                Text(
                    if (settings.dgafEnabled) "ENABLED — the agent runs tools without asking" else "disabled",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (settings.dgafEnabled) appearance.colorOf(ColorRole.BANNER) else MaterialTheme.colorScheme.onSurface,
                )
                if (settings.dgafAcceptedAt > 0) {
                    val when_ = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(settings.dgafAcceptedAt))
                    Text("accepted $when_", fontSize = 9.sp)
                }
            }

            if (settings.dgafEnabled) {
                Button(onClick = {
                    container.settings.update { it.copy(dgafEnabled = false, dgafAcceptedAt = 0L) }
                }) { Text("revoke now", fontSize = 11.sp) }
                Text(
                    "Revoking is immediate: the next tool call goes back through the approval gate.",
                    fontSize = 9.sp,
                )
            } else {
                OutlinedButton(onClick = { stage = 1 }) { Text("enable D.G.A.F.", fontSize = 11.sp) }
                Text(
                    "Two confirmations are required. This is not a switch you flip casually.",
                    fontSize = 9.sp,
                )
            }

            SectionTitle("what it changes")
            Text(
                "• The approval dialog never appears.\n" +
                    "• privileged_shell, screen control, package removal, script execution and reboots all run on request.\n" +
                    "• Every action is still written to the audit log, but nothing stops it first.\n" +
                    "• The panic chip in the header revokes it in one tap.",
                fontSize = 10.sp,
            )

            SectionTitle("per-tool policy")
            Text(
                "Applies whenever D.G.A.F. is off. Tap to cycle inherit → allow → deny.",
                fontSize = 9.sp,
            )
            container.buildRegistry().specs.forEach { spec ->
                val current = settings.toolPolicies[spec.name] ?: "inherit"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(spec.name, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    TextButton(onClick = {
                        val next = when (current) {
                            "inherit" -> "allow"
                            "allow" -> "deny"
                            else -> "inherit"
                        }
                        container.settings.update { updated ->
                            val policies = updated.toolPolicies.toMutableMap()
                            if (next == "inherit") policies.remove(spec.name) else policies[spec.name] = next
                            updated.copy(toolPolicies = policies)
                        }
                    }) { Text(current, fontSize = 10.sp) }
                }
            }
        }
    }

    if (stage == 1) {
        AlertDialog(
            onDismissRequest = { stage = 0; acknowledged = false },
            title = { Text("Warning 1 of 2") },
            text = {
                Column {
                    Text(
                        "D.G.A.F. lets the model use every tool without asking first: shell commands, package removal, "
                            + "screen control, file deletion, reboots.",
                        fontSize = 11.sp,
                    )
                    Text(
                        "\nThis is not a great idea for most use cases. It exists for people who understand the risks "
                            + "and are watching what happens. Being lazy here could cost you: a wrong rm, a wiped folder, "
                            + "a disabled system package, or a device that will not boot.",
                        fontSize = 11.sp,
                    )
                    Text("\nYou can revoke it at any time from the header chip.", fontSize = 10.sp)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text("I have read the warning above.", fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = acknowledged, onClick = { stage = 2 }) { Text(CONSENT, fontSize = 9.sp) }
            },
            dismissButton = { TextButton(onClick = { stage = 0; acknowledged = false }) { Text("cancel") } },
        )
    }

    if (stage == 2) {
        AlertDialog(
            onDismissRequest = { stage = 0 },
            title = { Text("Warning 2 of 2 — final") },
            text = {
                Column {
                    Text(
                        "Last chance. After this, the agent will not ask again until you revoke it.",
                        fontSize = 11.sp,
                    )
                    Text(
                        "\nIf you are not sure, close this dialog and keep the approval gate. It costs one tap per "
                            + "risky action and it is the only thing standing between a bad command and your data.",
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    container.settings.update {
                        it.copy(dgafEnabled = true, dgafAcceptedAt = System.currentTimeMillis())
                    }
                    stage = 0
                    acknowledged = false
                }) { Text(CONSENT, fontSize = 9.sp) }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("keep the gate") } },
        )
    }
}
