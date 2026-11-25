package dev.opencarl.app

/**
 * Lets tools read the current conversation (for snapshots and dataset export)
 * without the container depending on the view model.
 */
object ChatMirror {
    @Volatile
    private var provider: () -> String = { "[]" }

    fun set(source: () -> String) {
        provider = source
    }

    fun json(): String = runCatching { provider() }.getOrDefault("[]")
}
