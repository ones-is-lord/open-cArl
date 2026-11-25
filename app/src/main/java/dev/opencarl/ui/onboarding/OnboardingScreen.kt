package dev.opencarl.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.Neon
import dev.opencarl.appearance.NeonWordmark
import dev.opencarl.ui.neon.Tagline

/**
 * First run: what this device is, what it can carry, and which model to start
 * with. The recommendation comes from measured RAM and storage, not a guess.
 */
@Composable
fun OnboardingScreen(
    appearance: AppearanceSettings,
    onDone: (modelId: String) -> Unit,
) {
    val context = LocalContext.current
    val facts = remember { DeviceFacts.read(context) }
    val recommended = remember(facts) { ModelCatalog.recommend(facts) }
    var step by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(recommended.id) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Neon.Black)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        NeonWordmark(text = "OPEN CARL", appearance = appearance, maxSp = 40f, minSp = 20f)
        Tagline(text = "AIN'T NONE OF THIS EVEN MATTERS", uppercase = true, availablePx = 1200f, maxSp = 12f)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                0 -> {
                    Text("A real Linux shell, and an agent that uses it.", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "open cArl runs a shell on this device. You can type in it, and you can ask the agent to type in it for you. " +
                            "Nothing leaves the device unless you pick a cloud model or a tool sends it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Next: look at what this device can carry.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                1 -> {
                    Text("This device", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    listOf(
                        "model" to facts.device,
                        "android" to "${facts.androidRelease} (API ${facts.sdk})",
                        "cpu" to "${facts.cores} cores, ${facts.abi}",
                        "ram" to "${facts.totalRamMb} MB total, ${facts.freeRamMb} MB free",
                        "storage" to "${facts.freeStorageMb} MB free",
                        "class" to facts.tier,
                    ).forEach { (key, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(key, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Text(
                        "A local model needs roughly twice its download size in RAM once the runtime and context are loaded.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text("Pick a brain", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Recommended for ${facts.device}: ${recommended.name}. You can change this later in Models.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ModelCatalog.models.forEach { choice ->
                        val selected = choice.id == picked
                        val fits = ModelCatalog.fits(choice, facts)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                .clickable { picked = choice.id }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    choice.name + if (choice.id == recommended.id) "  ★" else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (choice.cloud) "cloud" else "${choice.downloadMb} MB",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(choice.note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                ModelCatalog.verdict(choice, facts),
                                fontSize = 10.sp,
                                color = if (fits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) { Text("Back") }
            }
            Box(Modifier.weight(1f))
            Text("${step + 1} / 3", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f))
            Button(onClick = {
                if (step < 2) step++ else onDone(picked)
            }) { Text(if (step < 2) "Next" else "Start") }
        }
    }
}
