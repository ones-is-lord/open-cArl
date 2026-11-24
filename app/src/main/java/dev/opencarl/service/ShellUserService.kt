package dev.opencarl.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * Runs inside the shell uid when Shizuku starts it, so commands here have the
 * same power as `adb shell`. Bound through Shizuku.bindUserService.
 */
class ShellUserService : Service() {

    private val handler = Handler(Looper.getMainLooper()) { message ->
        if (message.what == MSG_EXEC) {
            val command = message.data?.getString(KEY_COMMAND).orEmpty()
            val replyTo = message.replyTo
            Thread {
                val result = execute(command)
                runCatching {
                    replyTo?.send(Message.obtain(null, MSG_RESULT).apply { data = result })
                }
            }.apply { isDaemon = true }.start()
        }
        true
    }

    override fun onBind(intent: Intent?): IBinder = Messenger(handler).binder

    private fun execute(command: String): Bundle {
        val bundle = Bundle()
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            bundle.putInt(KEY_EXIT, process.exitValue())
            bundle.putString(KEY_OUTPUT, output.take(MAX_OUTPUT))
            bundle.putString(KEY_UID, "shell")
            bundle
        }.getOrElse { error ->
            bundle.putInt(KEY_EXIT, -1)
            bundle.putString(KEY_OUTPUT, error.message ?: "exec failed")
            bundle
        }
    }

    companion object {
        const val MSG_EXEC = 1
        const val MSG_RESULT = 2
        const val KEY_COMMAND = "command"
        const val KEY_EXIT = "exit"
        const val KEY_OUTPUT = "output"
        const val KEY_UID = "uid"
        private const val MAX_OUTPUT = 64_000
    }
}
