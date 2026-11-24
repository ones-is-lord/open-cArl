package dev.opencarl.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe facade over the VT buffer. The PTY reader thread feeds bytes as
 * text; the UI reads [version] and takes snapshots.
 */
class TerminalEmulator(
    cols: Int = 80,
    rows: Int = 24,
    maxScrollback: Int = 5000,
) {

    private val lock = Any()
    private val buffer = TerminalBuffer(cols, rows, maxScrollback)
    private val parser = VtParser(buffer) { _bell.tryEmit(Unit) }

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _bell = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val bell: SharedFlow<Unit> = _bell.asSharedFlow()

    val cols: Int get() = synchronized(lock) { buffer.cols }
    val rows: Int get() = synchronized(lock) { buffer.rows }

    fun feed(text: String) {
        if (text.isEmpty()) return
        val (version, title) = synchronized(lock) {
            parser.feed(text)
            buffer.version to buffer.title
        }
        if (_version.value != version) _version.value = version
        if (_title.value != title) _title.value = title
    }

    fun resize(newCols: Int, newRows: Int) {
        val version = synchronized(lock) {
            buffer.resize(newCols, newRows)
            buffer.version
        }
        if (_version.value != version) _version.value = version
    }

    fun snapshot(scrollBack: Int = 0): TerminalFrame = synchronized(lock) { buffer.snapshot(scrollBack) }

    fun visibleText(): String = synchronized(lock) { buffer.snapshot(0).text() }

    fun recentLines(count: Int = 200): String = synchronized(lock) {
        val total = buffer.totalLines
        buffer.lineTexts((total - count).coerceAtLeast(0), count).joinToString("\n")
    }

    fun clearScrollback() = synchronized(lock) {
        buffer.eraseInDisplay(3)
        _version.value = buffer.version
    }

    fun reset() = synchronized(lock) {
        buffer.reset()
        _version.value = buffer.version
    }

    val bracketedPaste: Boolean get() = synchronized(lock) { buffer.bracketedPaste }
    val applicationCursor: Boolean get() = synchronized(lock) { buffer.applicationCursor }
    val onAltScreen: Boolean get() = synchronized(lock) { buffer.onAltScreen }
}
