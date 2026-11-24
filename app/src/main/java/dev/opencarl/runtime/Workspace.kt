package dev.opencarl.runtime

import java.io.File

/**
 * Sandboxes file tools to a root directory. With proot the root is the distro
 * rootfs, so "/root/x" and "x" both resolve inside it.
 */
class Workspace(private val rootProvider: () -> File) {

    fun root(): File = rootProvider().apply { mkdirs() }

    fun resolve(path: String): File {
        val root = root().canonicalFile
        val candidate = if (path.startsWith("/")) File(root, path.trimStart('/')) else File(root, path)
        val canonical = candidate.canonicalFile
        val rootPath = root.path
        require(canonical.path == rootPath || canonical.path.startsWith(rootPath + File.separator)) {
            "path escapes the workspace: $path"
        }
        return canonical
    }

    fun read(path: String, maxBytes: Int = 200_000): String {
        val file = resolve(path)
        require(file.isFile) { "not a file: $path" }
        require(file.length() <= maxBytes) { "file is larger than $maxBytes bytes: $path" }
        return file.readText()
    }

    fun write(path: String, content: String): String {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "Wrote ${content.length} chars to ${file.path}"
    }

    fun list(path: String): String {
        val dir = resolve(path)
        require(dir.isDirectory) { "not a directory: $path" }
        val entries = dir.listFiles().orEmpty().sortedBy { it.name }
        if (entries.isEmpty()) return "(empty directory)"
        return entries.joinToString("\n") { entry ->
            val kind = if (entry.isDirectory) "dir " else "file"
            val size = if (entry.isDirectory) "" else " ${entry.length()}b"
            "$kind  ${entry.name}$size"
        }
    }
}
