package dev.opencarl.runtime

import dev.opencarl.terminal.PtySession

/** The agent's view of the shell it can execute commands in. */
interface CommandRunner {
    val cwd: String
    suspend fun run(command: String, timeoutMs: Long = 180_000): PtySession.CommandResult
}
