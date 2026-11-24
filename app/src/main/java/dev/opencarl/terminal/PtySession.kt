package dev.opencarl.terminal

import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A shell running on a real PTY. Output is streamed to [onOutput] (UTF-8 decoded,
 * multi-byte safe) and commands can be executed with [runCommand] using hidden
 * markers, which is how the agent observes exit codes and the working directory.
 */
class PtySession(
    private val launch: ShellLaunch,
    private val onOutput: (String) -> Unit = {},
    private val onExit: (Int) -> Unit = {},
) {

    data class CommandResult(val exitCode: Int, val output: String, val cwd: String?)

    private var masterFd = -1
    private var pid = -1
    private val alive = AtomicBoolean(false)
    private val writeLock = Any()
    private val commandMutex = Mutex()
    private val captures = CopyOnWriteArrayList<Capture>()
    private val decoder = Utf8Stream()
    private var reader: Thread? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var cwd: String = launch.cwd
        private set

    val label: String get() = launch.label

    fun start(rows: Int = 24, cols: Int = 80): Boolean {
        if (isRunning) return true
        if (!NativePty.available) {
            Log.e(TAG, "native pty library unavailable")
            return false
        }
        val env = launch.env.map { (k, v) -> "$k=$v" }.toTypedArray()
        val result = try {
            NativePty.create(
                launch.executable,
                launch.args.toTypedArray(),
                env,
                launch.cwd,
                rows,
                cols,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "pty spawn failed", t)
            intArrayOf(-1, -1)
        }
        if (result.size < 2 || result[0] < 0) return false
        masterFd = result[0]
        pid = result[1]
        alive.set(true)
        isRunning = true
        reader = Thread({ pump() }, "pty-reader-${launch.label}").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun write(bytes: ByteArray) {
        val fd = masterFd
        if (fd < 0 || bytes.isEmpty()) return
        synchronized(writeLock) {
            try {
                NativePty.write(fd, bytes, 0, bytes.size)
            } catch (t: Throwable) {
                Log.w(TAG, "write failed", t)
            }
        }
    }

    fun resize(rows: Int, cols: Int) {
        val fd = masterFd
        if (fd < 0 || rows <= 0 || cols <= 0) return
        try {
            NativePty.setSize(fd, rows, cols)
        } catch (_: Throwable) {
        }
    }

    fun interrupt() = write("\u0003")

    fun close() {
        if (!alive.compareAndSet(true, false)) return
        isRunning = false
        val fd = masterFd
        masterFd = -1
        if (fd >= 0) {
            runCatching { NativePty.close(fd) }
        }
        if (pid > 0) {
            runCatching { Os.kill(pid, OsConstants.SIGHUP) }
        }
        reader?.interrupt()
    }

    /**
     * Runs [command] inside the shell and returns its output, exit status and
     * resulting working directory. Serialized: one command at a time.
     */
    suspend fun runCommand(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult =
        commandMutex.withLock {
            if (!isRunning) {
                return@withLock CommandResult(-1, "shell is not running", cwd)
            }
            val id = UUID.randomUUID().toString().replace("-", "").take(10)
            val begin = "__LB_B_${id}__".toByteArray()
            val endPrefix = "__LB_E_${id}__".toByteArray()
            val capture = Capture(begin, endPrefix)
            captures.add(capture)
            try {
                val script = buildString {
                    append("printf '\\n__LB_B_").append(id).append("__\\n'\n")
                    append(command)
                    append('\n')
                    append("__lb_rc=$?\n")
                    append("printf '__LB_E_")
                    append(id)
                    append("__%s__%s\\n' \"\$__lb_rc\" \"\$PWD\"\n")
                }
                write(script)
                val outcome = withTimeoutOrNull(timeoutMs) {
                    while (true) {
                        val done = capture.completed
                        if (done != null) return@withTimeoutOrNull done
                        if (!isRunning) return@withTimeoutOrNull null
                        delay(POLL_MS)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    null
                }
                if (outcome == null) {
                    interrupt()
                    delay(250)
                    val partial = capture.completed?.output ?: capture.partialOutput()
                    CommandResult(
                        exitCode = TIMEOUT_EXIT,
                        output = partial.trimEnd() + "\n[command timed out after ${timeoutMs / 1000}s]",
                        cwd = cwd,
                    )
                } else {
                    outcome.cwd?.let { if (it.isNotBlank()) cwd = it }
                    CommandResult(outcome.exitCode, Ansi.strip(outcome.output), cwd)
                }
            } finally {
                captures.remove(capture)
            }
        }

    private fun pump() {
        val buffer = ByteArray(8192)
        val fd = masterFd
        while (alive.get() && fd >= 0) {
            val n = try {
                NativePty.read(fd, buffer)
            } catch (t: Throwable) {
                -1
            }
            if (n <= 0) break
            val chunk = buffer.copyOf(n)
            captures.forEach { it.feed(chunk) }
            val text = decoder.decode(chunk)
            if (text.isNotEmpty()) {
                try {
                    onOutput(text)
                } catch (t: Throwable) {
                    Log.w(TAG, "output sink failed", t)
                }
            }
        }
        isRunning = false
        val exit = if (pid > 0) runCatching { NativePty.waitFor(pid) }.getOrDefault(-1) else -1
        alive.set(false)
        runCatching { onExit(exit) }
    }

    private class Capture(
        private val begin: ByteArray,
        private val endPrefix: ByteArray,
    ) {
        private val buffer = ByteArrayOutputStream()

        @Volatile
        var completed: CommandResult? = null
            private set

        fun partialOutput(): String = String(buffer.toByteArray(), Charsets.UTF_8)

        fun feed(chunk: ByteArray) {
            if (completed != null) return
            if (buffer.size() > MAX_CAPTURE) {
                completed = CommandResult(-3, "output limit exceeded", null)
                return
            }
            buffer.write(chunk)
            val data = buffer.toByteArray()
            val beginAt = indexOf(data, begin, 0)
            if (beginAt < 0) return
            val contentStart = beginAt + begin.size
            val endAt = indexOf(data, endPrefix, contentStart)
            if (endAt < 0) return
            val lineEnd = indexOf(data, NEWLINE, endAt)
            if (lineEnd < 0) return
            val tail = String(data, endAt + endPrefix.size, lineEnd - endAt - endPrefix.size, Charsets.UTF_8)
            val separator = tail.indexOf("__")
            val code = (if (separator >= 0) tail.substring(0, separator) else tail).trim().toIntOrNull() ?: -1
            val dir = if (separator >= 0) tail.substring(separator + 2).trim().ifEmpty { null } else null
            val output = String(data, contentStart, endAt - contentStart, Charsets.UTF_8)
                .removePrefix("\r\n").removePrefix("\n")
            completed = CommandResult(code, output, dir)
        }
    }

    companion object {
        private const val TAG = "PtySession"
        private const val POLL_MS = 40L
        private const val DEFAULT_TIMEOUT_MS = 180_000L
        private const val TIMEOUT_EXIT = -2
        private const val MAX_CAPTURE = 4 * 1024 * 1024
        private val NEWLINE = byteArrayOf('\n'.code.toByte())

        fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
            if (needle.isEmpty()) return from
            val last = haystack.size - needle.size
            if (last < from) return -1
            outer@ for (i in from..last) {
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}

/** Incremental UTF-8 decoder that keeps split multi-byte sequences intact. */
internal class Utf8Stream {
    private var pending: ByteArray = ByteArray(0)

    fun decode(chunk: ByteArray): String {
        val data = if (pending.isEmpty()) chunk else pending + chunk
        var cut = data.size
        var i = data.size - 1
        var walked = 0
        while (i >= 0 && walked < 3) {
            val b = data[i].toInt() and 0xFF
            if (b and 0xC0 == 0x80) {
                i--
                walked++
                continue
            }
            val needed = when {
                b and 0x80 == 0 -> 1
                b and 0xE0 == 0xC0 -> 2
                b and 0xF0 == 0xE0 -> 3
                b and 0xF8 == 0xF0 -> 4
                else -> 1
            }
            if (data.size - i < needed) cut = i
            break
        }
        if (i < 0) cut = data.size
        pending = if (cut < data.size) data.copyOfRange(cut, data.size) else ByteArray(0)
        return String(data, 0, cut, Charsets.UTF_8)
    }
}
