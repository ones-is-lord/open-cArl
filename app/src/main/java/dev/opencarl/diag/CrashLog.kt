package dev.opencarl.diag

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash capture. A device we cannot debug directly is only worth
 * shipping to if it can tell us why it died, so the handler writes the trace to
 * a file and the next launch shows it instead of a silent restart.
 */
object CrashLog {

    private fun dir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(dir(app), "crash-${stamp()}.txt").writeText(
                    "thread: ${thread.name}\nwhen: ${Date()}\n\n$trace",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun files(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            .orEmpty()

    fun latest(context: Context): String? = files(context).firstOrNull()?.readText()

    fun clear(context: Context) {
        files(context).forEach { it.delete() }
    }

    /** Non-fatal problems worth showing next to a crash. */
    fun note(context: Context, tag: String, detail: String) {
        runCatching {
            File(dir(context), "notes.log").appendText("${stamp()} [$tag] $detail\n")
        }
    }
}
