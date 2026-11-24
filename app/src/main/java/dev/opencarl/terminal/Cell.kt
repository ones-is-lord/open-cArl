package dev.opencarl.terminal

/** SGR attributes for one cell. */
data class Style(
    val fg: Int = DEFAULT,
    val bg: Int = DEFAULT,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val strike: Boolean = false,
) {
    fun withFg(color: Int) = copy(fg = color)
    fun withBg(color: Int) = copy(bg = color)

    companion object {
        const val DEFAULT = -1
        val NONE = Style()
    }
}

/** One screen cell. [text] is empty for the trailing half of a wide character. */
class Cell {
    var text: String = " "
    var style: Style = Style.NONE
    var continuation: Boolean = false

    fun reset() {
        text = " "
        style = Style.NONE
        continuation = false
    }

    fun copyFrom(other: Cell) {
        text = other.text
        style = other.style
        continuation = other.continuation
    }

    fun isBlank(): Boolean = !continuation && (text.isEmpty() || text == " ") && style == Style.NONE
}

class Line(cols: Int) {
    val cells: Array<Cell> = Array(cols) { Cell() }

    fun copy(): Line {
        val copy = Line(cells.size)
        cells.forEachIndexed { index, cell -> copy.cells[index].copyFrom(cell) }
        return copy
    }

    fun resize(cols: Int) {
        if (cols == cells.size) return
        val next = Array(cols) { Cell() }
        val limit = minOf(cols, cells.size)
        for (i in 0 until limit) next[i].copyFrom(cells[i])
        // keep array identity stable for the buffer
        for (i in 0 until limit) cells[i].copyFrom(next[i])
        for (i in limit until cells.size) cells[i].reset()
    }

    fun isBlank(): Boolean = cells.all { it.isBlank() }

    fun text(): String = buildString {
        cells.forEach { cell ->
            if (cell.continuation) return@forEach
            append(cell.text)
        }
    }
}

/**
 * Column width of a code point in terminal cells. Covers the East Asian wide
 * and emoji ranges that matter in practice.
 */
fun cellWidth(codePoint: Int): Int = when {
    codePoint == 0 -> 0
    codePoint < 32 -> 0
    codePoint in 0x0300..0x036F -> 0
    codePoint in 0x1100..0x115F -> 2
    codePoint in 0x2E80..0x303E -> 2
    codePoint in 0x3041..0x33FF -> 2
    codePoint in 0x3400..0x4DBF -> 2
    codePoint in 0x4E00..0x9FFF -> 2
    codePoint in 0xA000..0xA4CF -> 2
    codePoint in 0xAC00..0xD7A3 -> 2
    codePoint in 0xF900..0xFAFF -> 2
    codePoint in 0xFE30..0xFE4F -> 2
    codePoint in 0xFF00..0xFF60 -> 2
    codePoint in 0xFFE0..0xFFE6 -> 2
    codePoint in 0x1F300..0x1F64F -> 2
    codePoint in 0x1F900..0x1F9FF -> 2
    codePoint in 0x20000..0x3FFFD -> 2
    else -> 1
}
