package dev.opencarl.terminal

/** Strips ANSI/VT control sequences so captured output is readable by an LLM. */
object Ansi {

    private val escape = Regex(
        "\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[@-_])"
    )
    private val control = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")

    fun strip(input: String): String =
        control.replace(escape.replace(input, ""), "").replace("\r\n", "\n").trimEnd()
}
