package dev.opencarl.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown before the app composes when the previous run died, so a device we
 * cannot attach to still produces a readable report instead of a silent restart.
 * Deliberately plain: no theme, no appearance settings, nothing that could fail.
 */
@Composable
fun CrashScreen(report: String, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05030A))
            .padding(14.dp),
    ) {
        Text("open cArl stopped unexpectedly", color = Color(0xFFFF2BD1), fontSize = 16.sp)
        Text(
            "The previous run crashed. The trace below is what the developer needs.",
            color = Color(0xFFB9C7D0),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = report.take(12_000),
                color = Color(0xFFE8F7FF),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("open cArl crash", report))
            }) { Text("Copy") }
            OutlinedButton(onClick = onDismiss) { Text("Continue anyway") }
            OutlinedButton(onClick = onContinue) { Text("Dismiss") }
        }
    }
}
