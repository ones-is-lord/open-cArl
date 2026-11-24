package dev.opencarl.terminal

/**
 * A VT screen: scrollback plus the visible window, a cursor, a scrolling region
 * and the alternate screen. Every mutation happens under [lock] in
 * TerminalEmulator; this class is not thread safe on its own.
 */
class TerminalBuffer(var cols: Int, var rows: Int, private val maxScrollback: Int = 5000) {

    private val lines = ArrayList<Line>()

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    var pendingWrap: Boolean = false
        private set
    var scrollTop: Int = 0
        private set
    var scrollBottom: Int = rows - 1
        private set
    var cursorVisible: Boolean = true
    var wrapEnabled: Boolean = true
    var style: Style = Style.NONE
    var title: String = ""
        private set
    var bracketedPaste: Boolean = false
    var applicationCursor: Boolean = false
    var version: Long = 0
        private set

    private var savedCursor: Pair<Int, Int>? = null
    private var altScreen = false
    private var mainLines: ArrayList<Line>? = null
    private var mainCursor: Pair<Int, Int> = 0 to 0
    private var lastPrinted: String = ""

    init {
        repeat(rows) { lines.add(Line(cols)) }
    }

    val screenTop: Int get() = lines.size - rows
    val totalLines: Int get() = lines.size

    fun setTitle(value: String) {
        title = value
        version++
    }

    fun touch() {
        version++
    }

    private fun screenLine(row: Int): Line = lines[(screenTop + row).coerceIn(0, lines.size - 1)]

    private fun trim() {
        while (lines.size > maxScrollback + rows) lines.removeAt(0)
    }

    fun reset() {
        lines.clear()
        repeat(rows) { lines.add(Line(cols)) }
        cursorRow = 0
        cursorCol = 0
        pendingWrap = false
        scrollTop = 0
        scrollBottom = rows - 1
        cursorVisible = true
        wrapEnabled = true
        style = Style.NONE
        savedCursor = null
        altScreen = false
        mainLines = null
        version++
    }

    // ---------------------------------------------------------------- writing

    fun putChar(text: String, width: Int) {
        if (pendingWrap && wrapEnabled) {
            cursorCol = 0
            lineFeed()
        }
        pendingWrap = false
        if (width == 2 && cursorCol >= cols - 1) {
            if (wrapEnabled) {
                cursorCol = 0
                lineFeed()
            } else {
                cursorCol = (cols - 2).coerceAtLeast(0)
            }
        }
        val line = screenLine(cursorRow)
        if (cursorCol in 0 until cols) {
            val cell = line.cells[cursorCol]
            cell.text = text
            cell.style = style
            cell.continuation = false
            if (width == 2 && cursorCol + 1 < cols) {
                val next = line.cells[cursorCol + 1]
                next.text = ""
                next.style = style
                next.continuation = true
            }
        }
        lastPrinted = text
        cursorCol += width
        if (cursorCol >= cols) {
            cursorCol = cols - 1
            pendingWrap = wrapEnabled
        }
        version++
    }

    fun repeatLast(count: Int) {
        if (lastPrinted.isEmpty()) return
        val width = cellWidth(lastPrinted.codePointAt(0))
        repeat(count.coerceIn(1, cols * rows)) { putChar(lastPrinted, width) }
    }

    fun lineFeed() {
        pendingWrap = false
        if (cursorRow == scrollBottom) {
            scrollUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
        version++
    }

    fun reverseIndex() {
        pendingWrap = false
        if (cursorRow == scrollTop) {
            scrollDown(1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
        version++
    }

    fun carriageReturn() {
        cursorCol = 0
        pendingWrap = false
        version++
    }

    fun backspace() {
        if (cursorCol > 0) cursorCol--
        pendingWrap = false
        version++
    }

    fun tab() {
        var next = cursorCol + 1
        while (next < cols - 1 && (next % 8) != 0) next++
        cursorCol = next.coerceAtMost(cols - 1)
        pendingWrap = false
        version++
    }

    fun backTab(count: Int) {
        var target = cursorCol
        repeat(count.coerceIn(1, cols)) {
            target = if (target == 0) 0 else ((target - 1) / 8) * 8
        }
        cursorCol = target
        version++
    }

    // ---------------------------------------------------------------- cursor

    fun moveTo(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
        pendingWrap = false
        version++
    }

    fun moveRelative(dRow: Int, dCol: Int) {
        moveTo(cursorRow + dRow, cursorCol + dCol)
    }

    fun setColumn(col: Int) {
        moveTo(cursorRow, col)
    }

    fun setRow(row: Int) {
        moveTo(row, cursorCol)
    }

    fun saveCursor() {
        savedCursor = cursorRow to cursorCol
    }

    fun restoreCursor() {
        savedCursor?.let { (row, col) -> moveTo(row, col) }
    }

    fun setRegion(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(0, rows - 1)
        if (t < b) {
            scrollTop = t
            scrollBottom = b
            moveTo(0, 0)
        }
    }

    // ---------------------------------------------------------------- scrolling

    fun scrollUp(count: Int) {
        val n = count.coerceIn(1, rows)
        repeat(n) {
            if (scrollTop == 0 && scrollBottom == rows - 1) {
                // Full-screen scroll: keep the top line as scrollback.
                lines.add(Line(cols))
                trim()
            } else {
                val index = (screenTop + scrollTop).coerceIn(0, lines.size - 1)
                lines.removeAt(index)
                lines.add(index, Line(cols))
            }
        }
        version++
    }

    fun scrollDown(count: Int) {
        val n = count.coerceIn(1, rows)
        repeat(n) {
            if (scrollTop == 0 && scrollBottom == rows - 1) {
                // Full-screen reverse scroll: the bottom line is discarded, the rest shifts down.
                val top = lines.size - rows
                val previous = (0 until rows).map { lines[top + it] }
                for (i in 0 until rows) {
                    lines[top + i] = if (i == 0) Line(cols) else previous[i - 1]
                }
            } else {
                val index = (screenTop + scrollBottom).coerceIn(0, lines.size - 1)
                lines.removeAt(index)
                lines.add(screenTop + scrollTop, Line(cols))
            }
        }
        version++
    }

    // ---------------------------------------------------------------- erasing

    fun eraseInLine(mode: Int) {
        val line = screenLine(cursorRow)
        when (mode) {
            0 -> for (c in cursorCol until cols) line.cells[c].reset()
            1 -> for (c in 0..cursorCol.coerceAtMost(cols - 1)) line.cells[c].reset()
            else -> line.cells.forEach { it.reset() }
        }
        version++
    }

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) screenLine(r).cells.forEach { it.reset() }
            }
            1 -> {
                eraseInLine(1)
                for (r in 0 until cursorRow) screenLine(r).cells.forEach { it.reset() }
            }
            2 -> lines.forEach { line -> line.cells.forEach { it.reset() } }
            3 -> {
                val keep = rows
                while (lines.size > keep) lines.removeAt(0)
                lines.forEach { line -> line.cells.forEach { it.reset() } }
            }
        }
        version++
    }

    fun eraseChars(count: Int) {
        val line = screenLine(cursorRow)
        for (c in cursorCol until (cursorCol + count).coerceAtMost(cols)) line.cells[c].reset()
        version++
    }

    fun insertChars(count: Int) {
        val line = screenLine(cursorRow)
        val n = count.coerceIn(1, cols)
        for (c in cols - 1 downTo cursorCol + n) line.cells[c].copyFrom(line.cells[c - n])
        for (c in cursorCol until (cursorCol + n).coerceAtMost(cols)) line.cells[c].reset()
        version++
    }

    fun deleteChars(count: Int) {
        val line = screenLine(cursorRow)
        val n = count.coerceIn(1, cols)
        for (c in cursorCol until cols - n) line.cells[c].copyFrom(line.cells[c + n])
        for (c in (cols - n).coerceAtLeast(cursorCol) until cols) line.cells[c].reset()
        version++
    }

    fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val n = count.coerceIn(1, scrollBottom - cursorRow + 1)
        repeat(n) {
            val index = (screenTop + scrollBottom).coerceIn(0, lines.size - 1)
            lines.removeAt(index)
            lines.add(screenTop + cursorRow, Line(cols))
        }
        version++
    }

    fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val n = count.coerceIn(1, scrollBottom - cursorRow + 1)
        repeat(n) {
            lines.removeAt((screenTop + cursorRow).coerceIn(0, lines.size - 1))
            lines.add(screenTop + scrollBottom, Line(cols))
        }
        version++
    }

    // ---------------------------------------------------------------- screens

    fun enterAltScreen() {
        if (altScreen) return
        mainLines = ArrayList(lines)
        mainCursor = cursorRow to cursorCol
        lines.clear()
        repeat(rows) { lines.add(Line(cols)) }
        altScreen = true
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        version++
    }

    fun exitAltScreen() {
        if (!altScreen) return
        mainLines?.let { saved ->
            lines.clear()
            lines.addAll(saved)
        }
        altScreen = false
        mainLines = null
        cursorRow = mainCursor.first.coerceIn(0, rows - 1)
        cursorCol = mainCursor.second.coerceIn(0, cols - 1)
        scrollTop = 0
        scrollBottom = rows - 1
        version++
    }

    val onAltScreen: Boolean get() = altScreen

    // ---------------------------------------------------------------- resize

    fun resize(newCols: Int, newRows: Int) {
        val c = newCols.coerceIn(2, 500)
        val r = newRows.coerceIn(2, 300)
        if (c == cols && r == rows) return
        lines.forEach { it.resize(c) }
        mainLines?.forEach { it.resize(c) }

        val oldCursorAbs = screenTop + cursorRow
        if (r > rows) {
            repeat(r - rows) { lines.add(Line(c)) }
        } else {
            var remove = rows - r
            while (remove > 0 && lines.size > r) {
                lines.removeAt(lines.size - 1)
                remove--
            }
        }
        cols = c
        rows = r
        scrollTop = 0
        scrollBottom = rows - 1
        val newTop = lines.size - rows
        cursorRow = (oldCursorAbs - newTop).coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        trim()
        version++
    }

    // ---------------------------------------------------------------- snapshot

    /** Copies the visible window for the renderer. [scrollBack] is 0 at the bottom. */
    fun snapshot(scrollBack: Int = 0): TerminalFrame {
        val maxBack = (lines.size - rows).coerceAtLeast(0)
        val back = scrollBack.coerceIn(0, maxBack)
        val start = (lines.size - rows - back).coerceAtLeast(0)
        val window = ArrayList<Line>(rows)
        for (i in 0 until rows) {
            val index = start + i
            window.add(if (index in lines.indices) lines[index].copy() else Line(cols))
        }
        return TerminalFrame(
            cols = cols,
            rows = rows,
            lines = window,
            cursorRow = if (back == 0) cursorRow else -1,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible && back == 0,
            scrollBack = back,
            maxScrollBack = maxBack,
            version = version,
        )
    }

    fun lineTexts(from: Int, count: Int): List<String> {
        val start = from.coerceIn(0, lines.size)
        val end = (start + count).coerceIn(start, lines.size)
        return (start until end).map { lines[it].text().trimEnd() }
    }
}

/** Immutable-enough view of the screen for one draw pass. */
class TerminalFrame(
    val cols: Int,
    val rows: Int,
    val lines: List<Line>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val scrollBack: Int,
    val maxScrollBack: Int,
    val version: Long,
) {
    fun text(): String = lines.joinToString("\n") { it.text().trimEnd() }
}
