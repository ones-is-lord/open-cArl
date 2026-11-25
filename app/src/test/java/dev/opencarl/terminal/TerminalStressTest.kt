package dev.opencarl.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Load tests for the terminal pipeline. These do not touch Compose or a real
 * PTY, so they run on the JVM; they exist to catch the parser and buffer
 * falling over when a build dumps a few megabytes at once.
 */
class TerminalStressTest {

    @Test
    fun `four megabytes of coloured output stay coherent`() {
        val emulator = TerminalEmulator(120, 40)
        val chunk = buildString {
            repeat(200) { index ->
                append("\u001b[38;5;").append(index % 256).append("m")
                append("line ").append(index).append(" of the build log, padded to look real ")
                append("\u001b[0m\r\n")
            }
        }
        val iterations = (4 * 1024 * 1024) / chunk.length + 1
        val started = System.nanoTime()
        repeat(iterations) { emulator.feed(chunk) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val text = emulator.visibleText()

        assertTrue("expected some content", text.isNotBlank())
        assertTrue("visible text should fit the screen", text.lines().size <= 40)
        assertTrue("parsed ${iterations * chunk.length} bytes in ${elapsedMs}ms, too slow", elapsedMs < 20_000)
    }

    @Test
    fun `alternate screen churn leaves the primary screen intact`() {
        val emulator = TerminalEmulator(80, 24)
        emulator.feed("primary content\r\n")
        repeat(500) {
            emulator.feed("\u001b[?1049h")
            emulator.feed("\u001b[2J\u001b[H full screen app frame $it")
            emulator.feed("\u001b[?1049l")
        }
        val text = emulator.visibleText()
        assertTrue("primary screen was lost: $text", text.contains("primary content"))
    }

    @Test
    fun `resize storm keeps the cursor inside the buffer`() {
        val emulator = TerminalEmulator(100, 30)
        repeat(200) { emulator.feed("row $it with a decent amount of text on it\r\n") }
        repeat(300) { step ->
            emulator.resize(40 + (step % 120), 12 + (step % 40))
            emulator.feed("after resize $step\r\n")
            val frame = emulator.snapshot()
            assertTrue("no rows after resize", frame.lines.isNotEmpty())
            assertTrue("too many rows", frame.lines.size <= 200)
        }
    }

    @Test
    fun `concurrent writers and readers do not corrupt the buffer`() = runBlocking {
        val emulator = TerminalEmulator(80, 24)
        val failures = AtomicInteger(0)
        val jobs = (0 until 4).map { writer ->
            async(Dispatchers.Default) {
                repeat(400) { index ->
                    runCatching {
                        emulator.feed("writer $writer line $index\r\n")
                    }.onFailure { failures.incrementAndGet() }
                }
            }
        } + listOf(
            async(Dispatchers.Default) {
                repeat(400) {
                    runCatching { emulator.snapshot() }.onFailure { failures.incrementAndGet() }
                }
            },
        )
        jobs.awaitAll()
        assertEquals("readers or writers threw", 0, failures.get())
        assertTrue(emulator.visibleText().isNotBlank())
    }

    @Test
    fun `wide characters emoji and broken sequences do not throw`() {
        val emulator = TerminalEmulator(40, 10)
        val nasty = buildString {
            append("日本語のテキスト\r\n")
            append("emoji 😀🚀 mixed with ascii\r\n")
            append("\u001b[38;2;255;0;0mtruecolor\u001b[0m\r\n")
            append("\u001b[999;999H far cursor\r\n")
            append("\u001b[1;1H\u001b[K\u001b[1M\u001b[1L partial edit\r\n")
            append("\u001b]0;title with spaces\u0007")
            append("\u001b[?25l hidden cursor\u001b[?25h\r\n")
            append("\u001b[38;5m truncated sgr\r\n")
        }
        repeat(200) { emulator.feed(nasty) }
        assertTrue(emulator.visibleText().isNotBlank())
    }

    @Test
    fun `scrollback keeps the last lines and drops the oldest`() {
        val emulator = TerminalEmulator(40, 10)
        repeat(5_000) { emulator.feed("line $it\r\n") }
        val frame = emulator.snapshot(scrollBack = 0)
        assertTrue(frame.lines.isNotEmpty())
        val text = emulator.visibleText()
        assertTrue("last line should be visible", text.contains("line 4999") || text.contains("line 4998"))
    }
}
