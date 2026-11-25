package dev.opencarl.net

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class WifiPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val at: Long = System.currentTimeMillis(),
) {
    val band: String
        get() = when {
            frequency in 2400..2500 -> "2.4G"
            frequency in 4900..5900 -> "5G"
            frequency in 5925..7125 -> "6G"
            else -> "${frequency}MHz"
        }

    fun csv(): String = listOf(
        at.toString(),
        ssid.replace(",", "_"),
        bssid,
        rssi.toString(),
        frequency.toString(),
        band,
        capabilities.replace(",", ";"),
    ).joinToString(",")
}

/**
 * Wardriving without a wardriving app: sample visible networks and append them
 * to a CSV. Android throttles background scans, so this samples while the app is
 * in the foreground and says so instead of pretending otherwise.
 */
class WifiScanner(private val context: Context) {

    private val manager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun permissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun enabled(): Boolean = manager?.isWifiEnabled == true

    suspend fun scan(timeoutMs: Long = 12_000): List<WifiPoint> = withContext(Dispatchers.IO) {
        val wifi = manager ?: return@withContext emptyList()
        if (!permissionGranted()) return@withContext emptyList()
        runCatching { wifi.startScan() }
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        runCatching { context.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(wifi.scanResults.map { it.toPoint() })
                        }
                    }
                }
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(receiver, filter)
                }
            }
        } ?: wifi.scanResults.map { it.toPoint() }
    }

    fun logFile(): File {
        val dir = File(context.filesDir, "wardriving").apply { mkdirs() }
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return File(dir, "wifi-$day.csv")
    }

    fun append(points: List<WifiPoint>): Int {
        if (points.isEmpty()) return 0
        val file = logFile()
        val isNew = !file.exists()
        file.appendText(buildString {
            if (isNew) append("timestamp,ssid,bssid,rssi,freq,band,capabilities\n")
            points.forEach { append(it.csv()).append('\n') }
        })
        return points.size
    }

    fun summary(points: List<WifiPoint>): String {
        if (points.isEmpty()) return "no networks visible (permission, wifi off, or scan throttled)"
        val byBand = points.groupBy { it.band }
        return buildString {
            append(points.size).append(" networks, log ").append(logFile().absolutePath).append('\n')
            byBand.forEach { (band, list) ->
                append(band).append(": ").append(list.size)
                val strongest = list.maxByOrNull { it.rssi }
                if (strongest != null) {
                    append(" (strongest ").append(strongest.ssid.ifBlank { strongest.bssid })
                        .append(' ').append(strongest.rssi).append("dBm)")
                }
                append('\n')
            }
        }
    }

    private fun android.net.wifi.ScanResult.toPoint() = WifiPoint(
        ssid = SSID,
        bssid = BSSID,
        rssi = level,
        frequency = frequency,
        capabilities = capabilities.orEmpty(),
    )
}
