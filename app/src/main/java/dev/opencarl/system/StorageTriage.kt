package dev.opencarl.system

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class StorageIssue(
    val path: String,
    val kind: Kind,
    val sizeBytes: Long,
    val detail: String,
) {
    enum class Kind { LARGE, ZERO_BYTE, DUPLICATE, BROKEN_LINK, EMPTY_DIR }
}

data class StorageReport(
    val scanned: Int,
    val totalBytes: Long,
    val freeBytes: Long,
    val issues: List<StorageIssue>,
    val durationMs: Long,
) {
    val reclaimableBytes: Long
        get() = issues.filter { it.kind == StorageIssue.Kind.DUPLICATE || it.kind == StorageIssue.Kind.ZERO_BYTE }
            .sumOf { it.sizeBytes }

    fun summary(): String = buildString {
        append("scanned $scanned files, ${human(totalBytes)} used, ${human(freeBytes)} free in ${durationMs}ms\n")
        val grouped = issues.groupBy { it.kind }
        grouped.forEach { (kind, list) ->
            append("- ").append(kind.name.lowercase().replace('_', ' ')).append(": ")
                .append(list.size).append(" (")
                .append(human(list.sumOf { it.sizeBytes })).append(")\n")
        }
        append("safe to reclaim: ").append(human(reclaimableBytes)).append('\n')
    }

    companion object {
        fun human(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format("%.0f MB", bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / (1L shl 10).toDouble())
            else -> "$bytes B"
        }
    }
}

/**
 * Read-only triage of shared storage: big files, empty files, duplicates,
 * broken symlinks and empty folders. Deletion is a separate, confirmed action.
 */
class StorageTriage(private val context: Context) {

    fun defaultRoots(): List<File> = listOfNotNull(
        Environment.getExternalStorageDirectory(),
        context.getExternalFilesDir(null),
        File(context.filesDir, "workspace"),
    ).distinct()

    suspend fun scan(
        roots: List<File> = defaultRoots(),
        largeThresholdBytes: Long = 100L * 1024 * 1024,
        maxFiles: Int = 20_000,
    ): StorageReport = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        var scanned = 0
        var total = 0L
        val issues = mutableListOf<StorageIssue>()
        val bySize = HashMap<Long, MutableList<File>>()
        val seenHashes = HashMap<String, File>()
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)

        fun visit(file: File, depth: Int) {
            if (scanned >= maxFiles || depth > 24) return
            val name = file.name
            if (name.startsWith(".") && file.isDirectory && depth > 0) return
            if (file.isDirectory) {
                val children = file.listFiles()
                if (children == null || children.isEmpty()) {
                    if (depth > 0) issues += StorageIssue(file.absolutePath, StorageIssue.Kind.EMPTY_DIR, 0, "empty folder")
                    return
                }
                children.forEach { visit(it, depth + 1) }
                return
            }
            scanned++
            if (!file.exists() && file.absolutePath.startsWith("/")) {
                issues += StorageIssue(file.absolutePath, StorageIssue.Kind.BROKEN_LINK, 0, "target is gone")
                return
            }
            val size = file.length()
            total += size
            when {
                size == 0L -> issues += StorageIssue(file.absolutePath, StorageIssue.Kind.ZERO_BYTE, 0, "0 bytes")
                size >= largeThresholdBytes -> issues += StorageIssue(
                    file.absolutePath,
                    StorageIssue.Kind.LARGE,
                    size,
                    StorageReport.human(size),
                )
            }
            if (size >= 64 * 1024) {
                bySize.getOrPut(size) { mutableListOf() }.add(file)
            }
        }

        roots.filter { it.exists() }.forEach { visit(it, 0) }

        bySize.values.filter { it.size > 1 }.forEach { group ->
            group.forEach { file ->
                val hash = hashOf(file) ?: return@forEach
                val original = seenHashes[hash]
                if (original == null) {
                    seenHashes[hash] = file
                } else {
                    issues += StorageIssue(
                        file.absolutePath,
                        StorageIssue.Kind.DUPLICATE,
                        file.length(),
                        "duplicate of ${original.absolutePath}",
                    )
                }
            }
        }

        StorageReport(
            scanned = scanned,
            totalBytes = total,
            freeBytes = stat.availableBytes,
            issues = issues.sortedByDescending { it.sizeBytes },
            durationMs = System.currentTimeMillis() - started,
        )
    }

    /** Deletes only zero-byte files and duplicate copies; never the original. */
    suspend fun clean(report: StorageReport, alsoEmptyDirs: Boolean = false): Int = withContext(Dispatchers.IO) {
        var removed = 0
        report.issues.forEach { issue ->
            val deletable = when (issue.kind) {
                StorageIssue.Kind.ZERO_BYTE, StorageIssue.Kind.DUPLICATE -> true
                StorageIssue.Kind.EMPTY_DIR -> alsoEmptyDirs
                else -> false
            }
            if (deletable) {
                val file = File(issue.path)
                if (runCatching { if (file.isDirectory) file.delete() else file.delete() }.getOrDefault(false)) removed++
            }
        }
        removed
    }

    private fun hashOf(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            val read = input.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
