package dev.opencarl.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import java.io.File

/** Reads what the device can do, without root or privileged APIs. */
class DeviceProfiler(private val context: Context) {

    fun profile(): DeviceProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val stat = StatFs(context.filesDir.absolutePath)
        val soc = socName()
        val gles = activityManager.deviceConfigurationInfo.reqGlEsVersion.let {
            "${(it shr 16) and 0xFFFF}.${(it shr 8) and 0xFF}"
        }
        val vulkan = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        return DeviceProfile(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            soc = soc,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cores = Runtime.getRuntime().availableProcessors(),
            bigCoreMhz = maxCpuKhz() / 1000,
            totalRamMb = (memory.totalMem / 1_048_576L).toInt(),
            availRamMb = (memory.availMem / 1_048_576L).toInt(),
            freeStorageMb = (stat.availableBytes / 1_048_576L).toInt(),
            glesVersion = gles,
            vulkan = vulkan,
            npuTops = NpuHeuristics.estimateTops(soc),
            androidSdk = Build.VERSION.SDK_INT,
        )
    }

    private fun socName(): String {
        val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() }.joinToString(" ")
        } else {
            Build.HARDWARE
        }
        return hardware.ifBlank { Build.BOARD }.trim()
    }

    private fun maxCpuKhz(): Int {
        val root = File("/sys/devices/system/cpu")
        val freqs = root.listFiles { file -> file.name.startsWith("cpu") }
            ?.mapNotNull { cpu ->
                File(cpu, "cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }
                    ?.readText()?.trim()?.toIntOrNull()
            }
            .orEmpty()
        val max = freqs.maxOrNull() ?: 0
        if (max > 0) return max
        return cpuInfoMaxMhz() * 1000
    }

    private fun cpuInfoMaxMhz(): Int = runCatching {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("cpu MHz") || it.startsWith("BogoMIPS") }
            ?.substringAfter(':')?.trim()?.toFloatOrNull()?.toInt() ?: 2000
    }.getOrDefault(2000)

    fun thermalStatus(): Int {
        val power = context.getSystemService(android.os.PowerManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else 0
    }

    fun batteryPercent(): Int {
        val manager = context.getSystemService(android.os.BatteryManager::class.java)
        return manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
