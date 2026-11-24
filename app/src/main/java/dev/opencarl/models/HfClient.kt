package dev.opencarl.models

import dev.opencarl.ai.JsonLenient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class HfModel(
    val id: String,
    val downloads: Long = 0,
    val likes: Int = 0,
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    val tags: List<String> = emptyList(),
) {
    val quantized: Boolean get() = tags.any { it.contains("gguf", ignoreCase = true) }
}

@Serializable
private data class HfSibling(val rfilename: String = "")

@Serializable
private data class HfRepo(val id: String = "", val siblings: List<HfSibling> = emptyList())

class HfClient(
    private val http: OkHttpClient,
    private val token: () -> String,
) {

    suspend fun search(query: String, limit: Int = 25): List<HfModel> = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/api/models?search=" +
            java.net.URLEncoder.encode(query, "UTF-8") +
            "&filter=gguf&sort=downloads&direction=-1&limit=" + limit
        val request = buildRequest(url)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HuggingFace HTTP ${response.code}")
            JsonLenient.decodeFromString<List<HfModel>>(response.body!!.string())
        }
    }

    suspend fun files(repo: String): List<String> = withContext(Dispatchers.IO) {
        val request = buildRequest("https://huggingface.co/api/models/$repo")
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HuggingFace HTTP ${response.code}")
            val parsed = JsonLenient.decodeFromString<HfRepo>(response.body!!.string())
            parsed.siblings.map { it.rfilename }.filter { it.endsWith(".gguf") }
        }
    }

    /** Downloads [file] from [repo] into [destDir], reporting bytes written. */
    suspend fun download(
        repo: String,
        file: String,
        destDir: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(destDir, file.substringAfterLast('/'))
        target.parentFile?.mkdirs()
        val request = buildRequest("https://huggingface.co/$repo/resolve/main/$file")
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            val total = body.contentLength()
            var written = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
        }
        target
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder().url(url).header("User-Agent", "openCarl/0.1")
        val key = token()
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        return builder.build()
    }
}
