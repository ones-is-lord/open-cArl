package dev.opencarl.snapshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class Snapshot(
    val id: String,
    val name: String,
    val createdAt: Long,
    val note: String = "",
    val files: Int = 0,
    val bytes: Long = 0,
    val chatFile: String = "chat.json",
    val transcriptFile: String = "transcript.txt",
) {
    val whenText: String
        get() = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(createdAt))
}

/**
 * Point-in-time copy of the workspace, the transcript and the conversation.
 * Restoring is destructive by design, so the UI always shows a diff first.
 */
class SessionSnapshots(
    private val rootDir: () -> File,
    private val workspaceRoot: () -> File,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<Snapshot> = rootDir().listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { readMeta(it) }
        ?.sortedByDescending { it.createdAt }
        .orEmpty()

    suspend fun create(
        name: String,
        note: String,
        transcript: String,
        chatJson: String,
    ): Snapshot = withContext(Dispatchers.IO) {
        val id = "snap-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(rootDir(), id).apply { mkdirs() }
        var count = 0
        var bytes = 0L
        val archive = File(dir, "workspace.zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            val root = workspaceRoot().canonicalFile
            if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(root).path.replace('\\', '/')
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                    bytes += file.length()
                }
            }
        }
        File(dir, "transcript.txt").writeText(transcript.takeLast(200_000))
        File(dir, "chat.json").writeText(chatJson)
        val meta = Snapshot(
            id = id,
            name = name.ifBlank { id },
            createdAt = System.currentTimeMillis(),
            note = note,
            files = count,
            bytes = bytes,
        )
        File(dir, "meta.json").writeText(json.encodeToString(Snapshot.serializer(), meta))
        meta
    }

    /** Added, modified and removed paths compared with the current workspace. */
    suspend fun diff(snapshot: Snapshot): List<String> = withContext(Dispatchers.IO) {
        val dir = File(rootDir(), snapshot.id)
        val archive = File(dir, "workspace.zip")
        if (!archive.isFile) return@withContext listOf("snapshot archive is missing")
        val root = workspaceRoot().canonicalFile
        val changes = mutableListOf<String>()
        val inArchive = HashSet<String>()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                inArchive += entry.name
                val current = File(root, entry.name)
                when {
                    !current.exists() -> changes += "removed  ${entry.name}"
                    crcOf(current) != entry.crc -> changes += "changed  ${entry.name}"
                }
            }
        }
        if (root.exists()) {
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(root).path.replace('\\', '/')
                if (relative !in inArchive) changes += "added    $relative"
            }
        }
        changes.sorted()
    }

    suspend fun restore(snapshot: Snapshot): Int = withContext(Dispatchers.IO) {
        val dir = File(rootDir(), snapshot.id)
        val archive = File(dir, "workspace.zip")
        if (!archive.isFile) return@withContext 0
        val root = workspaceRoot().apply { mkdirs() }
        root.listFiles()?.forEach { it.deleteRecursively() }
        var restored = 0
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val target = File(root, entry.name).canonicalFile
                if (!target.path.startsWith(root.canonicalPath)) return@forEach
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                restored++
            }
        }
        restored
    }

    fun transcript(snapshot: Snapshot): String =
        File(File(rootDir(), snapshot.id), snapshot.transcriptFile).takeIf { it.isFile }?.readText().orEmpty()

    fun chat(snapshot: Snapshot): String =
        File(File(rootDir(), snapshot.id), snapshot.chatFile).takeIf { it.isFile }?.readText().orEmpty()

    fun delete(snapshot: Snapshot): Boolean =
        File(rootDir(), snapshot.id).deleteRecursively()

    private fun readMeta(dir: File): Snapshot? {
        val meta = File(dir, "meta.json")
        if (!meta.isFile) return null
        return runCatching { json.decodeFromString(Snapshot.serializer(), meta.readText()) }.getOrNull()
    }

    private fun crcOf(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { stream: InputStream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }
}
