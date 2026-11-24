package dev.opencarl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole

private data class AccessoryKey(val label: String, val sequence: String)

private val keys = listOf(
    AccessoryKey("ESC", "\u001b"),
    AccessoryKey("TAB", "\t"),
    AccessoryKey("^C", "\u0003"),
    AccessoryKey("^D", "\u0004"),
    AccessoryKey("^Z", "\u001a"),
    AccessoryKey("↑", "\u001b[A"),
    AccessoryKey("↓", "\u001b[B"),
    AccessoryKey("→", "\u001b[C"),
    AccessoryKey("←", "\u001b[D"),
    AccessoryKey("|", "|"),
    AccessoryKey("/", "/"),
    AccessoryKey("-", "-"),
    AccessoryKey("~", "~"),
    AccessoryKey("$", "$"),
    AccessoryKey("HOME", "\u001b[H"),
    AccessoryKey("END", "\u001b[F"),
    AccessoryKey("PGUP", "\u001b[5~"),
    AccessoryKey("PGDN", "\u001b[6~"),
)

/** Swipeable row of terminal keys for soft keyboards. */
@Composable
fun KeyBar(
    appearance: AppearanceSettings,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    val accent = appearance.colorOf(ColorRole.ACCENT)
    val outline = appearance.colorOf(ColorRole.OUTLINE)

    fun send(sequence: String) {
        var value = sequence
        if (value.length == 1) {
            if (ctrl) value = ((value[0].uppercaseChar().code - 64).toChar()).toString()
            if (alt) value = "\u001b$value"
        }
        onKey(value)
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        item {
            TextButton(
                onClick = { ctrl = !ctrl },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (ctrl) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (ctrl) accent else outline, RoundedCornerShape(6.dp)),
            ) {
                Text("CTRL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            TextButton(
                onClick = { alt = !alt },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (alt) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (alt) accent else outline, RoundedCornerShape(6.dp)),
            ) {
                Text("ALT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(keys) { key ->
            TextButton(onClick = { send(key.sequence) }) {
                Text(key.label, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}
