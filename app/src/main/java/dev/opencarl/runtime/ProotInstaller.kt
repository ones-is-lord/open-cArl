package dev.opencarl.runtime

import android.content.Context
import android.net.Uri
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * Installs the two pieces proot needs: the proot executable itself and a
 * root filesystem. Both are user-supplied (licensing and size make bundling
 * impractical), then extracted into app-private storage.
 */
class ProotInstaller(private val context: Context, private val runtime: RuntimeController) {

    suspend fun installProot(uri: Uri): String = withContext(Dispatchers.IO) {
        val target = runtime.prootBinary
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("cannot open the selected file")
        target.setExecutable(true, false)
        if (!target.canExecute()) error("copied the file but it is not executable")
        "proot installed at " + target.absolutePath
    }

    suspend fun installRootfs(
        uri: Uri,
        onProgress: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val rootfs = runtime.rootfsDir
        if (rootfs.exists()) rootfs.deleteRecursively()
        rootfs.mkdirs()
        val name = uri.lastPathSegment.orEmpty().lowercase()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val input = BufferedInputStream(raw, 1 shl 16)
            val tar = when {
                name.endsWith(".tar.xz") || name.endsWith(".txz") ->
                    TarArchiveInputStream(XZCompressorInputStream(input))
                name.endsWith(".tar") -> TarArchiveInputStream(input)
                else -> TarArchiveInputStream(GzipCompressorInputStream(input))
            }
            tar.use { extract(it, rootfs, onProgress) }
        } ?: error("cannot open the selected archive")
        if (!File(rootfs, "bin/sh").exists()) {
            error("archive extracted but bin/sh is missing; is this a rootfs tarball?")
        }
        "rootfs installed in " + rootfs.absolutePath
    }

    private fun extract(
        tar: TarArchiveInputStream,
        dest: File,
        onProgress: (String) -> Unit,
    ) {
        val root = dest.canonicalFile
        var count = 0
        while (true) {
            val entry: TarArchiveEntry = tar.nextEntry ?: break
            val target = File(root, entry.name).canonicalFile
            if (!target.path.startsWith(root.path)) continue
            when {
                entry.isDirectory -> target.mkdirs()
                entry.isSymbolicLink -> {
                    target.parentFile?.mkdirs()
                    runCatching {
                        target.delete()
                        Os.symlink(entry.linkName, target.absolutePath)
                    }
                }
                entry.isLink -> {
                    target.parentFile?.mkdirs()
                    runCatching {
                        target.delete()
                        Os.link(File(root, entry.linkName).absolutePath, target.absolutePath)
                    }
                }
                else -> {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> tar.copyTo(output) }
                    if (entry.mode and 0b001_001_001 != 0) target.setExecutable(true, false)
                }
            }
            count++
            if (count % 250 == 0) onProgress("extracted $count entries")
        }
        onProgress("extracted $count entries")
    }

    fun importRootfsFromStream(input: InputStream, name: String): String {
        val rootfs = runtime.rootfsDir
        rootfs.mkdirs()
        val tar = if (name.endsWith(".tar.xz")) {
            TarArchiveInputStream(XZCompressorInputStream(input))
        } else {
            TarArchiveInputStream(GzipCompressorInputStream(input))
        }
        tar.use { extract(it, rootfs) {} }
        return rootfs.absolutePath
    }
}
