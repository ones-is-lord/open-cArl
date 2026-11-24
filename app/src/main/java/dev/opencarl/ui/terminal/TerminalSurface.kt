package dev.opencarl.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import android.view.KeyEvent as AndroidKeyEvent
import dev.opencarl.appearance.AppearanceSettings
import dev.opencarl.appearance.FontLibrary
import dev.opencarl.terminal.Style
import dev.opencarl.terminal.TerminalEmulator
import dev.opencarl.terminal.TerminalFrame
import kotlin.math.max

/**
 * Native terminal renderer: no WebView, no JavaScript. Cells are drawn with the
 * Compose text stack, input goes straight to the PTY, and the IME is captured by
 * an invisible text field.
 */
@Composable
fun TerminalSurface(
    emulator: TerminalEmulator,
    appearance: AppearanceSettings,
    onInput: (String) -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val version by emulator.version.collectAsStateWithLifecycle()
    val title by emulator.title.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val fontFamily = remember(appearance.terminalFont) { FontLibrary.familyOf(appearance.terminalFont) }
    val fontSize = appearance.terminalFontSize.sp
    val colors = remember(appearance) { TerminalColors.from(appearance) }
    val baseStyle = remember(fontFamily, fontSize) {
        TextStyle(fontFamily = fontFamily, fontSize = fontSize, lineHeight = fontSize * 1.18f)
    }
    val measurer = rememberTextMeasurer()
    val cell = remember(measurer, baseStyle) {
        val result = measurer.measure(AnnotatedString("M"), baseStyle, softWrap = false, maxLines = 1)
        CellSize(result.size.width.toFloat().coerceAtLeast(1f), result.size.height.toFloat().coerceAtLeast(1f))
    }

    var scrollBack by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(Viewport(0, 0)) }
    val frame = remember(version, scrollBack, cell) { emulator.snapshot(scrollBack) }

    val focusRequester = remember { FocusRequester() }
    var imeBuffer by remember { mutableStateOf("") }

    val cols = (viewport.width / cell.width).toInt().coerceAtLeast(2)
    val rows = (viewport.height / cell.height).toInt().coerceAtLeast(2)

    LaunchedEffect(cols, rows, viewport) {
        if (viewport.width > 0 && viewport.height > 0) {
            if (emulator.cols != cols || emulator.rows != rows) {
                emulator.resize(cols, rows)
                onResize(cols, rows)
            }
        }
    }

    LaunchedEffect(version) {
        if (scrollBack > 0 && version != frame.version) {
            // keep the user's position while new output streams in
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size -> viewport = Viewport(size.width, size.height) }
            .background(colors.background)
            .onPreviewKeyEvent { event -> handleKey(event, emulator.applicationCursor, onInput) },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(cell) {
                    detectVerticalDragGestures { _, dragAmount ->
                        dragAccum += dragAmount
                        val steps = (dragAccum / cell.height).toInt()
                        if (steps != 0) {
                            scrollBack = (scrollBack + steps).coerceIn(0, frame.maxScrollBack)
                            dragAccum -= steps * cell.height
                        }
                    }
                }
                .pointerInput(frame, cell) {
                    detectTapGestures(
                        onTap = {
                            if (scrollBack > 0) scrollBack = 0
                            focusRequester.requestFocus()
                        },
                        onLongPress = { offset ->
                            val row = (offset.y / cell.height).toInt().coerceIn(0, frame.rows - 1)
                            val col = (offset.x / cell.width).toInt().coerceIn(0, frame.cols - 1)
                            copyWord(context, frame, row, col)
                        },
                    )
                },
        ) {
            drawFrame(frame, colors, measurer, baseStyle, cell)
        }

        BasicTextField(
            value = imeBuffer,
            onValueChange = { next ->
                if (next.isEmpty() && imeBuffer.isNotEmpty()) {
                    onInput("\u007f")
                } else if (next.length > imeBuffer.length) {
                    onInput(next.substring(imeBuffer.length).replace("\n", "\r"))
                }
                imeBuffer = ""
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .focusable(),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.None,
                capitalization = KeyboardCapitalization.None,
            ),
        )

        if (title.isNotBlank() && title != "sh" && title != "bash") {
            Text(
                title,
                color = colors.foreground.copy(alpha = 0.45f),
                fontSize = 9.sp,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        if (scrollBack > 0) {
            TextButton(
                onClick = { scrollBack = 0 },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Text("↓ ${scrollBack} lines", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            }
        }
    }
}

private data class CellSize(val width: Float, val height: Float)

private data class Viewport(val width: Int, val height: Int)

private fun handleKey(event: KeyEvent, appCursor: Boolean, onInput: (String) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val sequence = when (event.key) {
        Key.DirectionUp -> if (appCursor) "\u001bOA" else "\u001b[A"
        Key.DirectionDown -> if (appCursor) "\u001bOB" else "\u001b[B"
        Key.DirectionRight -> if (appCursor) "\u001bOC" else "\u001b[C"
        Key.DirectionLeft -> if (appCursor) "\u001bOD" else "\u001b[D"
        Key.MoveHome -> "\u001b[H"
        Key.MoveEnd -> "\u001b[F"
        Key.PageUp -> "\u001b[5~"
        Key.PageDown -> "\u001b[6~"
        Key.Insert -> "\u001b[2~"
        Key.Delete -> "\u001b[3~"
        Key.Enter, Key.NumPadEnter -> "\r"
        Key.Backspace -> "\u007f"
        Key.Tab -> "\t"
        Key.Escape -> "\u001b"
        else -> {
            val native = event.nativeKeyEvent
            val code = native.keyCode
            when {
                event.isCtrlPressed && code in AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z ->
                    ((code - AndroidKeyEvent.KEYCODE_A + 1).toChar()).toString()
                event.isAltPressed && native.unicodeChar != 0 ->
                    "\u001b" + native.unicodeChar.toChar()
                else -> return false
            }
        }
    }
    onInput(sequence)
    return true
}

private fun copyWord(context: Context, frame: TerminalFrame, row: Int, col: Int) {
    val line = frame.lines.getOrNull(row) ?: return
    if (col !in line.cells.indices) return
    fun isWord(index: Int): Boolean {
        val cell = line.cells[index]
        if (cell.continuation) return true
        val ch = cell.text.firstOrNull() ?: return false
        return ch.isLetterOrDigit() || ch in "_./-:@~="
    }
    var start = col
    while (start > 0 && isWord(start - 1)) start--
    var end = col
    while (end < line.cells.size - 1 && isWord(end + 1)) end++
    val text = buildString {
        for (index in start..end) {
            if (!line.cells[index].continuation) append(line.cells[index].text)
        }
    }
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
}

private fun spanFor(style: Style, colors: TerminalColors): SpanStyle = SpanStyle(
    color = colors.fg(style, false),
    background = colors.bg(style, false),
    fontWeight = if (style.bold) FontWeight.Bold else null,
    fontStyle = if (style.italic) FontStyle.Italic else null,
    textDecoration = when {
        style.underline && style.strike ->
            TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        style.underline -> TextDecoration.Underline
        style.strike -> TextDecoration.LineThrough
        else -> null
    },
)

private fun DrawScope.drawFrame(
    frame: TerminalFrame,
    colors: TerminalColors,
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    cell: CellSize,
) {
    val visibleRows = minOf(frame.rows, (size.height / cell.height).toInt() + 1)
    for (row in 0 until visibleRows) {
        val line = frame.lines.getOrNull(row) ?: continue
        val last = line.cells.indexOfLast { !it.isBlank() }
        if (last < 0) continue
        val builder = AnnotatedString.Builder()
        var col = 0
        while (col <= last && col < line.cells.size) {
            val current = line.cells[col]
            if (current.continuation) {
                col++
                continue
            }
            val start = builder.length
            builder.append(current.text.ifEmpty { " " })
            builder.addStyle(spanFor(current.style, colors), start, builder.length)
            col++
        }
        val layout = measurer.measure(
            text = builder.toAnnotatedString(),
            style = baseStyle.copy(color = colors.foreground),
            softWrap = false,
            maxLines = 1,
        )
        drawText(layout, topLeft = Offset(0f, row * cell.height))
    }

    if (frame.cursorVisible && frame.cursorRow in 0 until frame.rows) {
        val x = frame.cursorCol * cell.width
        val y = frame.cursorRow * cell.height
        drawRect(colors.cursor, topLeft = Offset(x, y), size = Size(cell.width, cell.height))
        val under = frame.lines.getOrNull(frame.cursorRow)
            ?.cells?.getOrNull(frame.cursorCol)?.text.orEmpty()
        if (under.isNotBlank()) {
            val layout = measurer.measure(
                text = AnnotatedString(under),
                style = baseStyle.copy(color = colors.background),
                softWrap = false,
                maxLines = 1,
            )
            drawText(layout, topLeft = Offset(x, y))
        }
    }
}
