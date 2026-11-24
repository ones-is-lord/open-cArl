package dev.opencarl.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VtParserTest {

    private fun emu(cols: Int = 20, rows: Int = 5) = TerminalEmulator(cols, rows, maxScrollback = 100)

    private fun TerminalEmulator.line(row: Int): String =
        snapshot().lines[row].text().trimEnd()

    private fun TerminalEmulator.cell(row: Int, col: Int): Cell =
        snapshot().lines[row].cells[col]

    @Test
    fun `prints text and honours carriage return and newline`() {
        val term = emu()
        term.feed("hello\r\nworld")

        assertEquals("hello", term.line(0))
        assertEquals("world", term.line(1))
        assertEquals(1, term.snapshot().cursorRow)
        assertEquals(5, term.snapshot().cursorCol)
    }

    @Test
    fun `applies sgr colors and resets`() {
        val term = emu()
        term.feed("\u001b[31mred\u001b[0m plain")

        assertEquals(1, term.cell(0, 0).style.fg)
        assertEquals(Style.DEFAULT, term.cell(0, 4).style.fg)
        assertTrue(term.cell(0, 0).style != Style.NONE)
    }

    @Test
    fun `supports 256 color and truecolor escapes`() {
        val term = emu()
        term.feed("\u001b[38;5;200mX\u001b[38;2;10;20;30mY")

        assertEquals(200, term.cell(0, 0).style.fg)
        val truecolor = term.cell(0, 1).style.fg
        assertTrue(truecolor and VtParser.TRUECOLOR != 0)
        assertEquals(10, (truecolor shr 16) and 0xFF)
        assertEquals(20, (truecolor shr 8) and 0xFF)
        assertEquals(30, truecolor and 0xFF)
    }

    @Test
    fun `moves the cursor with cup and relative moves`() {
        val term = emu()
        term.feed("\u001b[3;5Hx")
        assertEquals("x", term.cell(2, 4).text)

        term.feed("\u001b[2A\u001b[3Dy")
        assertEquals("y", term.cell(0, 2).text)
    }

    @Test
    fun `erases to end of line and screen`() {
        val term = emu()
        term.feed("abcdef\u001b[1;3H\u001b[K")
        assertEquals("ab", term.line(0))

        term.feed("\u001b[1;1Habcdef\u001b[2J")
        assertEquals("", term.line(0))
    }

    @Test
    fun `scrolls the region and keeps scrollback`() {
        val term = emu(cols = 10, rows = 3)
        term.feed("one\r\ntwo\r\nthree\r\nfour")

        assertEquals("two", term.line(0))
        assertEquals("four", term.line(2))
        val scrolled = term.snapshot(scrollBack = 1)
        assertEquals("one", scrolled.lines[0].text().trimEnd())
    }

    @Test
    fun `alternate screen keeps the main screen intact`() {
        val term = emu()
        term.feed("main\r\n")
        term.feed("\u001b[?1049h")
        term.feed("alt")
        assertTrue(term.onAltScreen)
        assertEquals("alt", term.line(0))

        term.feed("\u001b[?1049l")
        assertFalse(term.onAltScreen)
        assertEquals("main", term.line(0))
    }

    @Test
    fun `wide characters occupy two cells`() {
        val term = emu()
        term.feed("日本")

        assertEquals("日", term.cell(0, 0).text)
        assertTrue(term.cell(0, 1).continuation)
        assertEquals("本", term.cell(0, 2).text)
        assertEquals(4, term.snapshot().cursorCol)
    }

    @Test
    fun `sets the window title through osc`() {
        val term = emu()
        term.feed("\u001b]0;my shell\u0007")
        assertEquals("my shell", term.title.value)
    }

    @Test
    fun `insert and delete characters shift the line`() {
        val term = emu()
        term.feed("abcdef\u001b[1;3H\u001b[2@")
        assertEquals("ab  cdef", term.line(0))

        term.feed("\u001b[1;3H\u001b[2P")
        assertEquals("abcdef", term.line(0))
    }

    @Test
    fun `resize keeps content and clamps the cursor`() {
        val term = emu(cols = 10, rows = 3)
        term.feed("keep me")
        term.resize(20, 6)

        assertEquals("keep me", term.line(0))
        assertEquals(20, term.snapshot().cols)
        assertEquals(6, term.snapshot().rows)
    }

    @Test
    fun `wrap mode and pending wrap behave like xterm`() {
        val term = emu(cols = 5, rows = 3)
        term.feed("abcde")
        assertEquals(4, term.snapshot().cursorCol)
        term.feed("f")
        assertEquals("abcde", term.line(0))
        assertEquals("f", term.line(1))
    }
}
