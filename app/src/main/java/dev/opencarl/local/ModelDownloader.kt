package dev.opencarl.local

import dev.opencarl.device.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads GGUF files into the app's model folder and builds the llama.cpp
 * command that serves them. The server is started inside the user's shell so
 * proot, packages and paths behave exactly like in a terminal session.
 */
class ModelDownloader(
    private val modelsDir: () -> File,
    private val http: OkHttpClient,
) {

    fun installed(): List<File> = modelsDir().listFiles { file ->
        file.isFile && file.name.endsWith(".gguf")
    }?.sortedBy { it.name }.orEmpty()

    suspend fun download(
        model: LocalModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val target = File(modelsDir().apply { mkdirs() }, model.file)
        if (target.exists() && target.length() > 0) return@withContext target
        val request = Request.Builder().url(model.downloadUrl).build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "download failed: HTTP ${response.code}" }
            val total = response.body?.contentLength() ?: -1L
            val partial = File(target.absolutePath + ".part")
            response.body?.byteStream()?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
            require(partial.length() > 0) { "empty download" }
            partial.renameTo(target)
        }
        target
    }

    fun serveCommand(
        model: File,
        port: Int = 8080,
        contextSize: Int = 4096,
        threads: Int = 4,
        gpuLayers: Int = 0,
    ): String = buildString {
        append("llama-server --model '").append(model.absolutePath).append('\'')
        append(" --host 127.0.0.1 --port ").append(port)
        append(" --ctx-size ").append(contextSize)
        append(" --threads ").append(threads)
        if (gpuLayers > 0) append(" --n-gpu-layers ").append(gpuLayers)
        append(" --jinja")
    }

    fun delete(model: File): Boolean = runCatching { model.delete() }.getOrDefault(false)
}
