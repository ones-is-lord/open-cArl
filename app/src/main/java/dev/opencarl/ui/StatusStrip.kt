package dev.opencarl.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencarl.ui.neon.FrameCyan
import dev.opencarl.ui.neon.FramePink
import kotlinx.coroutines.delay

private data class Link(
    val kind: String,
    val bars: Int,
    val detail: String,
    val online: Boolean,
)

private fun readLink(context: Context): Link {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return Link("offline", 0, "", false)
    val network = manager.activeNetwork ?: return Link("offline", 0, "no network", false)
    val caps = manager.getNetworkCapabilities(network) ?: return Link("offline", 0, "no network", false)
    val kind = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "lte"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        else -> "other"
    }
    val strength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.signalStrength else 0
    val bars = when {
        strength <= -88 -> 1
        strength <= -77 -> 2
        strength <= -66 -> 3
        strength > -66 -> 4
        else -> 0
    }
    val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val kbps = caps.linkDownstreamBandwidthKbps
    return Link(kind, bars, if (kbps > 0) "${kbps / 1000} Mbps" else "", online)
}

/**
 * One thin strip: link state with bars, battery, free storage, and a jump to the
 * system network picker. Polls instead of subscribing because the platform does
 * not hand out continuous signal updates without a foreground service.
 */
@Composable
fun StatusStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(readLink(context)) }
    var battery by remember { mutableStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    var storageMb by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            link = readLink(context)
            val sticky = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            battery = if (level >= 0) level * 100 / scale else 0
            charging = (sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) == BatteryManager.BATTERY_STATUS_CHARGING
            storageMb = runCatching { (StatFs(context.filesDir.absolutePath).availableBytes / (1024 * 1024)).toInt() }.getOrDefault(0)
            delay(5_000)
        }
    }

    val accent = if (link.online) FrameCyan else FramePink
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF08060E))
            .clickable {
                runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("▮".repeat(link.bars) + "▯".repeat((4 - link.bars).coerceAtLeast(0)), fontSize = 9.sp, color = accent)
        Text(
            link.kind + if (link.online) "" else " · offline",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = accent,
        )
        if (link.detail.isNotEmpty()) {
            Text(link.detail, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.weight(1f)) {}
        Text("${battery}%${if (charging) " ⚡" else ""}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${storageMb / 1024} GB free", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("connect", fontSize = 9.sp, color = FrameCyan)
    }
}
