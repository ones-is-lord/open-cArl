package dev.opencarl.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.opencarl.R
import dev.opencarl.privilege.AuditLog
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ScheduledTask(
    val id: Long,
    val label: String,
    val prompt: String = "",
    val command: String = "",
    val at: Long,
    val repeatMinutes: Int = 0,
    val enabled: Boolean = true,
)

/** Prompts that fired while the app was closed; the UI drains them on resume. */
object PendingPrompts {
    private val _prompt = MutableStateFlow<String?>(null)
    val prompt: StateFlow<String?> = _prompt.asStateFlow()

    fun push(text: String) {
        _prompt.value = text
    }

    fun take(): String? {
        val value = _prompt.value
        _prompt.value = null
        return value
    }
}

/**
 * Schedules agent prompts or shell commands. Commands run headless through the
 * privilege runner; prompts are queued and run as soon as the app is open, and a
 * notification tells you they are waiting.
 */
class TaskScheduler(
    private val context: Context,
    private val fileProvider: () -> File,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<ScheduledTask> {
        val file = fileProvider()
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<ScheduledTask>>(file.readText()) }
            .getOrDefault(emptyList())
            .sortedBy { it.at }
    }

    fun schedule(
        label: String,
        atMillis: Long,
        prompt: String = "",
        command: String = "",
        repeatMinutes: Int = 0,
    ): ScheduledTask {
        val tasks = load().toMutableList()
        val task = ScheduledTask(
            id = (tasks.maxOfOrNull { it.id } ?: 0L) + 1,
            label = label,
            prompt = prompt,
            command = command,
            at = atMillis,
            repeatMinutes = repeatMinutes,
        )
        tasks += task
        persist(tasks)
        arm(task)
        return task
    }

    fun cancel(id: Long): Boolean {
        val tasks = load()
        val task = tasks.firstOrNull { it.id == id } ?: return false
        alarmManager().cancel(pendingIntent(task))
        persist(tasks.filterNot { it.id == id })
        return true
    }

    fun rescheduleAll() {
        load().filter { it.enabled }.forEach { task ->
            if (task.at > System.currentTimeMillis()) arm(task)
        }
    }

    private fun persist(tasks: List<ScheduledTask>) {
        val file = fileProvider()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(tasks))
    }

    private fun arm(task: ScheduledTask) {
        val manager = alarmManager()
        val triggerAt = if (task.at > System.currentTimeMillis()) task.at else System.currentTimeMillis() + 5_000
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(task))
        }
    }

    private fun pendingIntent(task: ScheduledTask): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            putExtra(EXTRA_TASK, json.encodeToString(task))
        }
        return PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun alarmManager(): AlarmManager = context.getSystemService(AlarmManager::class.java)

    companion object {
        const val EXTRA_TASK = "task"
    }
}

object ScheduleNotifier {
    private const val CHANNEL = "schedule"

    fun notify(context: Context, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scheduled tasks", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent().setClassName(context.packageName, "dev.opencarl.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() % 100_000).toInt(), notification) }
    }
}

class ScheduledTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val fileProvider = { File(context.filesDir, "schedule/tasks.json") }
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TaskScheduler(context, fileProvider).rescheduleAll()
            TriggerScheduler(context).armNext()
            return
        }
        val raw = intent.getStringExtra(TaskScheduler.EXTRA_TASK) ?: return
        val json = Json { ignoreUnknownKeys = true }
        val task = runCatching {
            json.decodeFromString(ScheduledTask.serializer(), raw)
        }.getOrNull() ?: return

        val pending = goAsync()
        Thread {
            try {
                if (task.command.isNotBlank()) {
                    val audit = AuditLog { File(context.filesDir, "audit/audit.jsonl") }
                    val privilege = PrivilegeManager(context, audit)
                    runBlocking {
                        privilege.run(task.command, PrivilegeLevel.APP, note = "scheduled:${task.label}")
                    }
                }
                if (task.prompt.isNotBlank()) {
                    PendingPrompts.push(task.prompt)
                    notify(context, task)
                }
                if (task.repeatMinutes > 0) {
                    TaskScheduler(context, fileProvider).schedule(
                        label = task.label,
                        atMillis = System.currentTimeMillis() + task.repeatMinutes * 60_000L,
                        prompt = task.prompt,
                        command = task.command,
                        repeatMinutes = task.repeatMinutes,
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun notify(context: Context, task: ScheduledTask) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scheduled tasks", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent().setClassName(context.packageName, "dev.opencarl.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Scheduled task: ${task.label}")
            .setContentText("Open cArl to run it.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(task.id.toInt(), notification)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    }

    companion object {
        private const val CHANNEL = "schedule"
    }
}
