package dev.opencarl.terminal

/**
 * VT100/xterm escape sequence parser. Feeds characters into a [TerminalBuffer]:
 * C0 controls, CSI (with private markers and SGR incl. 256/truecolor),
 * OSC (titles) and the DEC private modes that matter for shells and TUIs.
 */
class VtParser(
    private val buffer: TerminalBuffer,
    private val onBell: () -> Unit = {},
) {

    private enum class State { GROUND, ESCAPE, CSI, OSC, STRING, CHARSET }

    private var state = State.GROUND
    private val params = IntArray(32)
    private var paramCount = 0
    private var currentParam = -1
    private var privateMarker = ' '
    private var intermediate = ' '
    private val osc = StringBuilder()
    private var highSurrogate: Char? = null

    fun feed(text: CharSequence) {
        for (index in text.indices) {
            val ch = text[index]
            when (state) {
                State.GROUND -> ground(ch)
                State.ESCAPE -> escape(ch)
                State.CSI -> csi(ch)
                State.OSC -> oscChar(ch)
                State.STRING -> if (ch == '\u0007') state = State.GROUND
                State.CHARSET -> state = State.GROUND
            }
        }
    }

    private fun ground(ch: Char) {
        when {
            ch == '\u001b' -> state = State.ESCAPE
            ch < ' ' -> control(ch)
            ch.isHighSurrogate() -> highSurrogate = ch
            ch.isLowSurrogate() -> {
                val high = highSurrogate
                highSurrogate = null
                val code = if (high != null) Character.toCodePoint(high, ch) else ch.code
                print(code)
            }
            else -> {
                highSurrogate = null
                print(ch.code)
            }
        }
    }

    private fun control(ch: Char) {
        when (ch) {
            '\n', '\u000b', '\u000c' -> buffer.lineFeed()
            '\r' -> buffer.carriageReturn()
            '\b' -> buffer.backspace()
            '\t' -> buffer.tab()
            '\u0007' -> onBell()
            else -> Unit
        }
    }

    private fun print(codePoint: Int) {
        if (codePoint == 0) return
        val text = String(Character.toChars(codePoint))
        buffer.putChar(text, cellWidth(codePoint))
    }

    private fun escape(ch: Char) {
        state = State.GROUND
        when (ch) {
            '[' -> beginCsi()
            ']' -> {
                osc.setLength(0)
                state = State.OSC
            }
            'P', 'X', '^', '_' -> state = State.STRING
            '(', ')', '*', '+', '-', '.', '/' -> state = State.CHARSET
            '7' -> buffer.saveCursor()
            '8' -> buffer.restoreCursor()
            'D' -> buffer.lineFeed()
            'E' -> {
                buffer.carriageReturn()
                buffer.lineFeed()
            }
            'M' -> buffer.reverseIndex()
            'c' -> buffer.reset()
            'H' -> Unit
            '=' , '>' -> Unit
            else -> Unit
        }
    }

    private fun beginCsi() {
        paramCount = 0
        currentParam = -1
        privateMarker = ' '
        intermediate = ' '
        state = State.CSI
    }

    private fun csi(ch: Char) {
        when {
            ch == '\u001b' -> state = State.ESCAPE
            ch in '0'..'9' -> {
                currentParam = if (currentParam < 0) ch - '0' else currentParam * 10 + (ch - '0')
                currentParam = currentParam.coerceAtMost(999_999)
            }
            ch == ';' || ch == ':' -> pushParam()
            ch in '<'..'?' && paramCount == 0 && currentParam < 0 -> privateMarker = ch
            ch in ' '..'/' -> intermediate = ch
            ch in '@'..'~' -> {
                pushParam()
                dispatch(ch)
                state = State.GROUND
            }
        }
    }

    private fun pushParam() {
        if (paramCount < params.size) {
            params[paramCount++] = if (currentParam < 0) 0 else currentParam
        }
        currentParam = -1
    }

    private fun param(index: Int, fallback: Int = 1): Int {
        val value = if (index < paramCount) params[index] else 0
        return if (value == 0) fallback else value
    }

    private fun dispatch(final: Char) {
        when (final) {
            'A' -> buffer.moveRelative(-param(0), 0)
            'B' -> buffer.moveRelative(param(0), 0)
            'C' -> buffer.moveRelative(0, param(0))
            'D' -> buffer.moveRelative(0, -param(0))
            'E' -> buffer.moveTo(buffer.cursorRow + param(0), 0)
            'F' -> buffer.moveTo(buffer.cursorRow - param(0), 0)
            'G', '`' -> buffer.setColumn(param(0) - 1)
            'H', 'f' -> buffer.moveTo(param(0) - 1, param(1) - 1)
            'd' -> buffer.setRow(param(0) - 1)
            'a' -> buffer.moveRelative(0, param(0))
            'e' -> buffer.moveRelative(param(0), 0)
            'J' -> buffer.eraseInDisplay(param(0, 0))
            'K' -> buffer.eraseInLine(param(0, 0))
            'X' -> buffer.eraseChars(param(0))
            'L' -> buffer.insertLines(param(0))
            'M' -> buffer.deleteLines(param(0))
            'P' -> buffer.deleteChars(param(0))
            '@' -> buffer.insertChars(param(0))
            'S' -> buffer.scrollUp(param(0))
            'T' -> buffer.scrollDown(param(0))
            'b' -> buffer.repeatLast(param(0))
            'm' -> sgr()
            'r' -> buffer.setRegion(param(0) - 1, param(1, buffer.rows) - 1)
            's' -> if (privateMarker == ' ') buffer.saveCursor()
            'u' -> if (privateMarker == ' ') buffer.restoreCursor()
            'h' -> setMode(enabled = true)
            'l' -> setMode(enabled = false)
            'Z' -> buffer.backTab(param(0))
            'c', 'n', 'q', 't', 'g' -> Unit
            else -> Unit
        }
        intermediate = ' '
    }

    private fun setMode(enabled: Boolean) {
        if (privateMarker != '?') return
        for (index in 0 until paramCount) {
            when (params[index]) {
                1 -> buffer.applicationCursor = enabled
                7 -> buffer.wrapEnabled = enabled
                25 -> buffer.cursorVisible = enabled
                47, 1047, 1049 -> if (enabled) buffer.enterAltScreen() else buffer.exitAltScreen()
                1048 -> if (enabled) buffer.saveCursor() else buffer.restoreCursor()
                2004 -> buffer.bracketedPaste = enabled
                else -> Unit
            }
        }
    }

    private fun sgr() {
        if (paramCount == 0) {
            buffer.style = Style.NONE
            return
        }
        var style = buffer.style
        var index = 0
        while (index < paramCount) {
            when (val code = params[index]) {
                0 -> style = Style.NONE
                1 -> style = style.copy(bold = true)
                2 -> style = style.copy(dim = true)
                3 -> style = style.copy(italic = true)
                4 -> style = style.copy(underline = true)
                7 -> style = style.copy(inverse = true)
                9 -> style = style.copy(strike = true)
                22 -> style = style.copy(bold = false, dim = false)
                23 -> style = style.copy(italic = false)
                24 -> style = style.copy(underline = false)
                27 -> style = style.copy(inverse = false)
                29 -> style = style.copy(strike = false)
                in 30..37 -> style = style.copy(fg = code - 30)
                39 -> style = style.copy(fg = Style.DEFAULT)
                in 40..47 -> style = style.copy(bg = code - 40)
                49 -> style = style.copy(bg = Style.DEFAULT)
                in 90..97 -> style = style.copy(fg = code - 90 + 8)
                in 100..107 -> style = style.copy(bg = code - 100 + 8)
                38, 48 -> {
                    val (color, consumed) = extendedColor(index)
                    style = if (code == 38) style.copy(fg = color) else style.copy(bg = color)
                    index += consumed
                }
                else -> Unit
            }
            index++
        }
        buffer.style = style
    }

    /** Returns the color plus how many extra parameters it consumed. */
    private fun extendedColor(index: Int): Pair<Int, Int> {
        if (index + 1 >= paramCount) return Style.DEFAULT to 1
        return when (params[index + 1]) {
            5 -> if (index + 2 < paramCount) params[index + 2].coerceIn(0, 255) to 2 else Style.DEFAULT to 2
            2 -> if (index + 4 < paramCount) {
                val r = params[index + 2].coerceIn(0, 255)
                val g = params[index + 3].coerceIn(0, 255)
                val b = params[index + 4].coerceIn(0, 255)
                (TRUECOLOR or (r shl 16) or (g shl 8) or b) to 4
            } else {
                Style.DEFAULT to 4
            }
            else -> Style.DEFAULT to 1
        }
    }

    private fun oscChar(ch: Char) {
        when (ch) {
            '\u0007' -> finishOsc()
            '\u001b' -> {
                finishOsc()
                state = State.ESCAPE
            }
            else -> if (osc.length < 2048) osc.append(ch)
        }
    }

    private fun finishOsc() {
        val payload = osc.toString()
        state = State.GROUND
        when {
            payload.startsWith("0;") || payload.startsWith("2;") ->
                buffer.setTitle(payload.substring(2).trim())
            payload.startsWith("1;") -> Unit
            else -> Unit
        }
        osc.setLength(0)
    }

    companion object {
        /** Marker bit for 24-bit colors packed into the fg/bg slot. */
        const val TRUECOLOR = 0x1000000
    }
}
