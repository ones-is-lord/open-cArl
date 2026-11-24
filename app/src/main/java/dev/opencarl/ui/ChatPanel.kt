package dev.opencarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencarl.app.ChatItem
import dev.opencarl.app.OpenCarlViewModel
import dev.opencarl.app.ToolStatus
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.ColorRole
import dev.opencarl.appearance.FontLibrary

@Composable
fun ChatPanel(
    vm: OpenCarlViewModel,
    appearance: AppearanceSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val approval by vm.approval.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, appearance.colorOf(ColorRole.OUTLINE), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "agent",
                fontFamily = FontLibrary.familyOf(appearance.headerFont),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = appearance.colorOf(ColorRole.ACCENT),
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                IconButton(onClick = { vm.stop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = appearance.colorOf(ColorRole.HIGHLIGHT))
                }
            }
            TextButton(onClick = { vm.newChat() }) { Text("new", fontSize = 11.sp) }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.id }) { item -> ChatRow(item, appearance) }
        }

        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ask cArl…", fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    vm.send(input)
                    input = ""
                }),
            )
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = !busy && input.isNotBlank(),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = appearance.colorOf(ColorRole.ACCENT))
            }
        }

        approval?.let { request ->
            AlertDialog(
                onDismissRequest = { vm.resolveApproval(false) },
                title = { Text("Allow ${request.call.name}?") },
                text = {
                    Column {
                        Text(request.description, fontSize = 12.sp)
                        Text(
                            request.call.arguments.take(700),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.resolveApproval(true) }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.resolveApproval(false) }) { Text("Deny") }
                },
            )
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, appearance: AppearanceSettings) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                item.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(appearance.colorOf(ColorRole.ACCENT).copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        is ChatItem.Assistant -> Text(
            item.text.ifBlank { "…" },
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = appearance.colorOf(ColorRole.TEXT),
        )
        is ChatItem.Tool -> {
            val statusColor = when (item.status) {
                ToolStatus.RUNNING -> appearance.colorOf(ColorRole.HIGHLIGHT)
                ToolStatus.OK -> appearance.colorOf(ColorRole.ACCENT)
                ToolStatus.DENIED -> appearance.colorOf(ColorRole.BANNER)
                ToolStatus.FAILED -> MaterialTheme.colorScheme.error
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(
                    "▸ ${item.name}  ${item.status.name.lowercase()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontFamily = FontFamily.Monospace,
                )
                if (item.arguments.isNotBlank() && item.arguments != "{}") {
                    Text(
                        item.arguments.take(300),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.output.isNotBlank()) {
                    Text(
                        item.output.takeLast(900),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        is ChatItem.Error -> Text(
            "⚠ ${item.message}",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
