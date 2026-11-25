package dev.opencarl.system

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface

/** Cheap, real diagnostics: what is actually happening on this phone right now. */
class Diagnostics(
    private val context: Context,
    private val privilege: PrivilegeManager,
) {

    suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        buildString {
            append("device   ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · ").append(Build.VERSION.RELEASE).append(" (sdk ").append(Build.VERSION.SDK_INT)
                .append(")\n")
            append("uptime   ").append(uptime()).append('\n')
            append("load     ").append(readFile("/proc/loadavg")).append('\n')
            append("cpu      ").append(cpuUsage()).append('\n')
            append("memory   ").append(memory()).append('\n')
            append("storage  ").append(storage()).append('\n')
            append("battery  ").append(battery()).append('\n')
            append("thermal  ").append(thermal()).append('\n')
            append("network  ").append(network()).append('\n')
        }
    }

    suspend fun topProcesses(limit: Int = 12): String = withContext(Dispatchers.IO) {
        val outcome = privilege.run("ps -A -o PID,USER,%CPU,RSS,NAME --sort=-%cpu | head -$limit", PrivilegeLevel.APP)
        if (outcome.ok && outcome.output.isNotBlank()) {
            outcome.output.trim()
        } else {
            val manager = context.getSystemService(ActivityManager::class.java)
            manager.runningAppProcesses.orEmpty().take(limit)
                .joinToString("\n") { "${it.pid}  ${it.processName}" }
        }
    }

    suspend fun logcat(lines: Int = 300, filter: String = ""): String {
        val args = buildString {
            append("logcat -d -t ").append(lines.coerceIn(20, 5_000))
            if (filter.isNotBlank()) append(" | grep -i '").append(filter.replace("'", "")).append('\'')
        }
        val outcome = privilege.run(args, PrivilegeLevel.SHIZUKU, timeoutMs = 20_000)
        return if (outcome.output.isNotBlank()) outcome.output.take(20_000) else outcome.summary
    }

    suspend fun dumpsys(service: String): String {
        val outcome = privilege.run("dumpsys $service", PrivilegeLevel.SHIZUKU, timeoutMs = 30_000)
        return if (outcome.output.isNotBlank()) outcome.output.take(20_000) else outcome.summary
    }

    fun uptime(): String {
        val ms = SystemClock.elapsedRealtime()
        val days = ms / 86_400_000
        val hours = (ms % 86_400_000) / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return "${days}d ${hours}h ${minutes}m"
    }

    fun cpuUsage(): String {
        val first = readFile("/proc/stat").lineSequence().firstOrNull().orEmpty()
        Thread.sleep(220)
        val second = readFile("/proc/stat").lineSequence().firstOrNull().orEmpty()
        val a = first.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        val b = second.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (a.size < 4 || b.size < 4) return "unavailable"
        val idleA = a.getOrElse(3) { 0 } + a.getOrElse(4) { 0 }
        val idleB = b.getOrElse(3) { 0 } + b.getOrElse(4) { 0 }
        val totalA = a.sum()
        val totalB = b.sum()
        val totalDelta = (totalB - totalA).coerceAtLeast(1)
        val idleDelta = idleB - idleA
        val busy = ((totalDelta - idleDelta) * 100 / totalDelta).coerceIn(0, 100)
        return "$busy% of ${Runtime.getRuntime().availableProcessors()} cores"
    }

    fun memory(): String {
        val info = readFile("/proc/meminfo")
        fun value(key: String): Long =
            info.lineSequence().firstOrNull { it.startsWith(key) }
                ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0L
        val totalKb = value("MemTotal")
        val availableKb = value("MemAvailable")
        val usedMb = (totalKb - availableKb) / 1024
        val totalMb = totalKb / 1024
        val swapTotal = value("SwapTotal") / 1024
        val swapFree = value("SwapFree") / 1024
        return "$usedMb/${totalMb}MB used, swap ${swapTotal - swapFree}/${swapTotal}MB"
    }

    fun storage(): String {
        val stat = StatFs(context.filesDir.absolutePath)
        val freeGb = stat.availableBytes / 1e9
        val totalGb = stat.totalBytes / 1e9
        return String.format("%.1f/%.1fGB free on data", freeGb, totalGb)
    }

    fun battery(): String {
        val manager = context.getSystemService(BatteryManager::class.java)
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = manager.isCharging
        val temp = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return "$level%${if (charging) " charging" else ""} (counter $temp)"
    }

    fun thermal(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(PowerManager::class.java)
            val status = power.currentThermalStatus
            val label = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> "nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "light"
                PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "unknown"
            }
            val zones = thermalZones()
            return "$label" + if (zones.isNotBlank()) " · $zones" else ""
        }
        return thermalZones().ifBlank { "unavailable" }
    }

    fun network(): String {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .joinToString(", ") { it.name }
        }.getOrDefault("none")
        return interfaces
    }

    private fun thermalZones(): String = runCatching {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                val temp = File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toIntOrNull()
                val type = File(zone, "type").takeIf { it.canRead() }?.readText()?.trim()
                if (temp == null || type == null) null
                else "${type}=${String.format("%.1f", temp / 1000.0)}C"
            }
            ?.take(4)
            ?.joinToString(" ")
            .orEmpty()
    }.getOrDefault("")

    private fun readFile(path: String): String =
        runCatching { File(path).readText() }.getOrDefault("")
}
