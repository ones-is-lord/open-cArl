package dev.opencarl.runtime

import android.content.Context
import dev.opencarl.data.RuntimeBackend
import dev.opencarl.data.RuntimeSettings
import dev.opencarl.terminal.ShellLaunch
import java.io.File

/**
 * Chooses how the shell is spawned: Android's own toybox shell, or a Linux
 * userland entered through proot.
 */
class RuntimeController(private val context: Context) {

    val files: File get() = context.filesDir

    val homeDir: File get() = File(files, "home").apply { mkdirs() }

    val rootfsDir: File get() = File(files, "rootfs")

    val prootBinary: File get() = File(files, "bin/proot")

    fun prootInstalled(): Boolean = prootBinary.canExecute() && File(rootfsDir, "bin/sh").exists()

    fun shellLaunch(settings: RuntimeSettings): ShellLaunch =
        if (settings.backend == RuntimeBackend.PROOT && prootInstalled()) prootLaunch(settings)
        else deviceLaunch()

    fun label(settings: RuntimeSettings): String = when {
        settings.backend == RuntimeBackend.PROOT && prootInstalled() ->
            "proot · ${settings.distro}"
        settings.backend == RuntimeBackend.PROOT ->
            "device shell (proot not installed)"
        else -> "device shell"
    }

    private fun deviceLaunch(): ShellLaunch {
        val path = listOf(
            "/system/bin",
            "/system/xbin",
            "/vendor/bin",
            "/product/bin",
            "/data/local/bin",
        ).joinToString(":")
        return ShellLaunch(
            executable = "/system/bin/sh",
            args = listOf("-l"),
            env = mapOf(
                "HOME" to homeDir.absolutePath,
                "TMPDIR" to homeDir.absolutePath,
                "PATH" to path,
                "TERM" to "xterm-256color",
                "LANG" to "C.UTF-8",
                "PAGER" to "cat",
                "SHELL" to "/system/bin/sh",
            ),
            cwd = homeDir.absolutePath,
            label = "device",
        )
    }

    private fun prootLaunch(settings: RuntimeSettings): ShellLaunch {
        val root = rootfsDir.absolutePath
        val args = mutableListOf(
            "--link2symlink",
            "-0",
            "-r", root,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/system",
            "-b", "/data/data/${context.packageName}",
            "-w", "/root",
        )
        if (settings.prootArgs.isNotBlank()) {
            args += settings.prootArgs.split(Regex("\\s+")).filter { it.isNotBlank() }
        }
        args += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PAGER=cat",
            "SHELL=/bin/sh",
            "/bin/sh", "-l",
        )
        return ShellLaunch(
            executable = prootBinary.absolutePath,
            args = args,
            env = mapOf("PROOT_TMP_DIR" to homeDir.absolutePath),
            cwd = root,
            label = "proot",
        )
    }

    /** Rootfs paths used by file tools and stdio MCP servers. */
    fun workspaceRoot(settings: RuntimeSettings): File =
        if (settings.backend == RuntimeBackend.PROOT && prootInstalled()) File(rootfsDir, "root")
        else homeDir

    fun mcpWorkDir(settings: RuntimeSettings): String =
        if (settings.backend == RuntimeBackend.PROOT && prootInstalled()) File(rootfsDir, "root").absolutePath
        else homeDir.absolutePath

    /** Wraps a stdio MCP command so it runs inside the userland when proot is active. */
    fun wrapCommand(settings: RuntimeSettings): (List<String>) -> List<String> = { command ->
        if (settings.backend == RuntimeBackend.PROOT && prootInstalled() && command.isNotEmpty()) {
            val root = rootfsDir.absolutePath
            listOf(
                prootBinary.absolutePath,
                "--link2symlink", "-0", "-r", root,
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-w", "/root",
                "/usr/bin/env", "-i",
                "HOME=/root",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            ) + command
        } else {
            command
        }
    }
}
