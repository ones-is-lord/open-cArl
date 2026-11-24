package dev.opencarl.terminal

/**
 * Thin JNI surface over the native PTY spawner (see src/main/cpp/pty.c).
 */
internal object NativePty {

    private var loaded = false

    val available: Boolean
        get() = loaded

    init {
        loaded = try {
            System.loadLibrary("ptyexec")
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Returns [masterFd, pid], or [-1, -1] on failure. */
    @JvmStatic
    external fun create(
        executable: String,
        args: Array<String>,
        env: Array<String>,
        cwd: String?,
        rows: Int,
        cols: Int,
    ): IntArray

    @JvmStatic
    external fun read(fd: Int, buffer: ByteArray): Int

    @JvmStatic
    external fun write(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int

    @JvmStatic
    external fun setSize(fd: Int, rows: Int, cols: Int): Int

    @JvmStatic
    external fun waitFor(pid: Int): Int

    @JvmStatic
    external fun close(fd: Int)
}
