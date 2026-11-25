package dev.opencarl.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dev.opencarl.privilege.AuditLog
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DeviceTrigger(
    val id: Long,
    val kind: String,
    val value: String = "",
    val prompt: String = "",
    val command: String = "",
    val enabled: Boolean = true,
    val lastFiredAt: Long = 0L,
)

/** Persistent rules like "battery below 20 -> remind me to charge". */
class TriggerStore(private val fileProvider: () -> File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<DeviceTrigger> {
        val file = fileProvider()
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<DeviceTrigger>>(file.readText()) }
            .getOrDefault(emptyList())
    }

    fun save(items: List<DeviceTrigger>) {
        val file = fileProvider()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(items))
    }

    fun add(kind: String, value: String, prompt: String, command: String): DeviceTrigger {
        val items = load().toMutableList()
        val trigger = DeviceTrigger(
            id = (items.maxOfOrNull { it.id } ?: 0L) + 1,
            kind = kind,
            value = value,
            prompt = prompt,
            command = command,
        )
        items += trigger
        save(items)
        return trigger
    }

    fun remove(id: Long): Boolean {
        val items = load()
        val next = items.filterNot { it.id == id }
        if (next.size == items.size) return false
        save(next)
        return true
    }

    fun setEnabled(id: Long, enabled: Boolean): Boolean {
        val items = load()
        if (items.none { it.id == id }) return false
        save(items.map { if (it.id == id) it.copy(enabled = enabled) else it })
        return true
    }

    fun markFired(id: Long) {
        save(load().map { if (it.id == id) it.copy(lastFiredAt = System.currentTimeMillis()) else it })
    }
}

/** Reads the device state the trigger kinds are matched against. */
class DeviceState(private val context: Context) {

    data class Reading(
        val battery: Int,
        val charging: Boolean,
        val thermal: Int,
        val interactive: Boolean,
        val storageFreePercent: Int,
        val ssid: String,
    )

    fun read(): Reading {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            (batteryManager?.isCharging == true)
        val power = context.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else 0
        val stat = android.os.StatFs(context.filesDir.absolutePath)
        val freePercent = ((stat.availableBytes.toDouble() / stat.totalBytes.coerceAtLeast(1)) * 100).toInt()
        return Reading(
            battery = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            charging = charging,
            thermal = thermal,
            interactive = power?.isInteractive ?: true,
            storageFreePercent = freePercent,
            ssid = ssid(),
        )
    }

    private fun ssid(): String = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        wifi.connectionInfo?.ssid?.trim('"').orEmpty()
    }.getOrDefault("")
}

/** Evaluates the rules and reports which ones just became true. */
class TriggerEngine(private val state: DeviceState) {

    fun matches(trigger: DeviceTrigger, reading: DeviceState.Reading): Boolean = when (trigger.kind) {
        "battery_below" -> reading.battery in 0..(trigger.value.toIntOrNull() ?: 20)
        "battery_above" -> reading.battery >= (trigger.value.toIntOrNull() ?: 80)
        "charging" -> reading.charging
        "not_charging" -> !reading.charging
        "thermal_at_least" -> reading.thermal >= (trigger.value.toIntOrNull() ?: 3)
        "wifi_ssid" -> reading.ssid.equals(trigger.value.trim(), ignoreCase = true)
        "screen_off" -> !reading.interactive
        "screen_on" -> reading.interactive
        "storage_below" -> reading.storageFreePercent <= (trigger.value.toIntOrNull() ?: 10)
        else -> false
    }

    fun evaluate(triggers: List<DeviceTrigger>, reading: DeviceState.Reading = state.read()): List<DeviceTrigger> =
        triggers.filter { it.enabled && matches(it, reading) }
}

/**
 * Polls device state every fifteen minutes. Android will not let an app watch
 * battery or thermal state continuously in the background, so this is a poll,
 * not a subscription, and the tool says so.
 */
class TriggerScheduler(private val context: Context) {

    fun armNext() {
        val intent = Intent(context, DeviceTriggerReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val manager = context.getSystemService(AlarmManager::class.java)
        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + POLL_MS,
                pending,
            )
        }
    }

    fun cancel() {
        val intent = Intent(context, DeviceTriggerReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }

    companion object {
        const val POLL_MS = 15 * 60 * 1000L
        private const val REQUEST = 4711
    }
}

class DeviceTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = TriggerStore { File(context.filesDir, "schedule/triggers.json") }
        val triggers = store.load()
        TriggerScheduler(context).armNext()
        if (triggers.isEmpty()) return

        val engine = TriggerEngine(DeviceState(context))
        val fired = engine.evaluate(triggers)
        if (fired.isEmpty()) return

        val pending = goAsync()
        Thread {
            try {
                fired.forEach { trigger ->
                    if (trigger.command.isNotBlank()) {
                        val audit = AuditLog { File(context.filesDir, "audit/audit.jsonl") }
                        runBlocking {
                            PrivilegeManager(context, audit).run(
                                trigger.command,
                                PrivilegeLevel.APP,
                                note = "trigger:${trigger.kind}",
                            )
                        }
                    }
                    if (trigger.prompt.isNotBlank()) {
                        PendingPrompts.push(trigger.prompt)
                    }
                    store.markFired(trigger.id)
                }
                if (fired.any { it.prompt.isNotBlank() }) {
                    ScheduleNotifier.notify(
                        context,
                        "Trigger: " + fired.first().kind,
                        fired.first().prompt.ifBlank { "A device trigger fired." },
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
