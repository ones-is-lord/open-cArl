package dev.opencarl.privilege

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import dev.opencarl.service.ShellUserService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

enum class PrivilegeLevel(val label: String) {
    APP("app"),
    SHIZUKU("shizuku"),
    ROOT("root"),
}

data class PrivilegeStatus(
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val rootAvailable: Boolean = false,
    val rootGranted: Boolean = false,
) {
    val best: PrivilegeLevel
        get() = when {
            rootGranted -> PrivilegeLevel.ROOT
            shizukuGranted && shizukuRunning -> PrivilegeLevel.SHIZUKU
            else -> PrivilegeLevel.APP
        }
}

data class CommandOutcome(
    val command: String,
    val level: PrivilegeLevel,
    val exitCode: Int,
    val output: String,
    val durationMs: Long,
) {
    val ok: Boolean get() = exitCode == 0
    val summary: String
        get() = "$command → exit $exitCode (${level.label}, ${durationMs}ms)"
}

/**
 * One place that decides how much power a command gets: plain app uid, the
 * shell uid through Shizuku, or root through su. Everything is audited.
 */
class PrivilegeManager(
    private val context: Context,
    private val audit: AuditLog,
) {

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status.asStateFlow()

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                _status.value = _status.value.copy(shizukuGranted = granted)
            }
        }

    fun register() {
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        refresh()
    }

    fun unregister() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
    }

    fun refresh() {
        val installed = isPackageInstalled("moe.shizuku.privileged.api")
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val root = rootAvailable()
        _status.value = PrivilegeStatus(
            shizukuInstalled = installed,
            shizukuRunning = running,
            shizukuGranted = granted,
            rootAvailable = root,
            rootGranted = root,
        )
    }

    fun requestShizuku() {
        runCatching {
            if (Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_REQUEST)
            }
        }.onFailure { Log.w(TAG, "shizuku request failed", it) }
    }

    suspend fun run(
        command: String,
        level: PrivilegeLevel = _status.value.best,
        timeoutMs: Long = 60_000,
        note: String = "",
    ): CommandOutcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val effective = if (level == PrivilegeLevel.SHIZUKU && !_status.value.shizukuGranted) {
            PrivilegeLevel.APP
        } else {
            level
        }
        if (effective == PrivilegeLevel.SHIZUKU) {
            val outcome = runViaShizuku(command, timeoutMs)
            audit.record(outcome.toEntry(note))
            return@withContext outcome
        }
        val process = runCatching { start(effective, command) }
            .getOrElse { error ->
                val outcome = CommandOutcome(
                    command = command,
                    level = effective,
                    exitCode = -1,
                    output = error.message ?: "failed to start",
                    durationMs = 0,
                )
                audit.record(outcome.toEntry(note))
                return@withContext outcome
            }
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { buffered ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val read = buffered.read(buffer)
                        if (read <= 0) break
                        output.append(buffer, 0, read)
                        if (output.length > MAX_OUTPUT) break
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { process.destroyForcibly() }
        }
        reader.join(1_000)
        val outcome = CommandOutcome(
            command = command,
            level = effective,
            exitCode = if (finished) process.exitValue() else -2,
            output = output.toString().take(MAX_OUTPUT),
            durationMs = System.currentTimeMillis() - started,
        )
        audit.record(outcome.toEntry(note))
        outcome
    }

    private fun start(level: PrivilegeLevel, command: String): Process = when (level) {
        PrivilegeLevel.APP -> ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()

        PrivilegeLevel.SHIZUKU -> error("shizuku commands go through runViaShizuku")

        PrivilegeLevel.ROOT -> ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
    }

    // ---------------------------------------------------------------- shizuku

    private val shizukuMessenger = AtomicReference<Messenger?>(null)
    private val pendingReplies = ConcurrentHashMap<Int, (Bundle) -> Unit>()
    private val requestIds = AtomicInteger(1)
    private val replyHandler = Handler(Looper.getMainLooper()) { message ->
        pendingReplies.remove(message.what)?.invoke(message.data ?: Bundle())
        true
    }

    private val shellConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shizukuMessenger.set(binder?.let { Messenger(it) })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuMessenger.set(null)
        }
    }

    /** Binds the shell user service; call after Shizuku grants permission. */
    fun connectShizuku() {
        if (!_status.value.shizukuGranted) return
        runCatching {
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val args = Shizuku.UserServiceArgs(ComponentName(context, ShellUserService::class.java))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(debuggable)
                .version(1)
            Shizuku.bindUserService(args, shellConnection)
        }.onFailure { Log.w(TAG, "could not bind the shizuku shell service", it) }
    }

    private suspend fun runViaShizuku(command: String, timeoutMs: Long): CommandOutcome {
        val started = System.currentTimeMillis()
        val messenger = shizukuMessenger.get()
            ?: return CommandOutcome(command, PrivilegeLevel.SHIZUKU, -1, "shizuku shell is not connected", 0)
        val id = requestIds.incrementAndGet()
        val reply = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Bundle> { continuation ->
                pendingReplies[id] = { bundle -> if (continuation.isActive) continuation.resume(bundle) }
                val message = Message.obtain(null, ShellUserService.MSG_EXEC).apply {
                    data = Bundle().apply { putString(ShellUserService.KEY_COMMAND, command) }
                    replyTo = Messenger(replyHandler)
                }
                runCatching { messenger.send(message) }.onFailure { error ->
                    pendingReplies.remove(id)
                    if (continuation.isActive) continuation.cancel(error)
                }
            }
        }
        pendingReplies.remove(id)
        return if (reply == null) {
            CommandOutcome(command, PrivilegeLevel.SHIZUKU, -2, "timed out after ${timeoutMs}ms", System.currentTimeMillis() - started)
        } else {
            CommandOutcome(
                command = command,
                level = PrivilegeLevel.SHIZUKU,
                exitCode = reply.getInt(ShellUserService.KEY_EXIT, -1),
                output = reply.getString(ShellUserService.KEY_OUTPUT).orEmpty(),
                durationMs = System.currentTimeMillis() - started,
            )
        }
    }

    private fun CommandOutcome.toEntry(note: String) = AuditEntry(
        level = level.label,
        command = command,
        exitCode = exitCode,
        durationMs = durationMs,
        output = output.take(4_000),
        note = note,
    )

    private fun rootAvailable(): Boolean {
        val paths = (System.getenv("PATH") ?: "").split(':')
        val inPath = paths.any { File(it, "su").canExecute() }
        if (inPath) return true
        return listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
            .any { File(it).exists() }
    }

    private fun isPackageInstalled(id: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(id, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(id, 0)
        }
        true
    }.getOrDefault(false)

    companion object {
        private const val TAG = "PrivilegeManager"
        private const val SHIZUKU_REQUEST = 4242
        private const val MAX_OUTPUT = 64_000
    }
}
