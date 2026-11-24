package dev.opencarl.terminal

/** Everything needed to spawn a shell inside a PTY. */
data class ShellLaunch(
    val executable: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val cwd: String,
    val label: String,
    val banner: String = "",
)
