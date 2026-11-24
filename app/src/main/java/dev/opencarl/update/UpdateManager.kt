package dev.opencarl.update

import android.content.Context
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import dev.opencarl.skills.SkillStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateReport(
    val appUpdate: String = "",
    val packages: String = "",
    val skills: Int = 0,
    val finishedAt: Long = System.currentTimeMillis(),
) {
    fun summary(): String = buildString {
        if (appUpdate.isNotBlank()) append("app: ").append(appUpdate).append('\n')
        if (packages.isNotBlank()) append("packages: ").append(packages).append('\n')
        append("skills: ").append(skills).append(" available\n")
    }
}

/**
 * Keeps the moving parts current: the app itself (via GitHub releases), the
 * Linux packages, and the bundled skills.
 */
class UpdateManager(
    private val context: Context,
    private val privilege: PrivilegeManager,
    private val skills: SkillStore,
    private val http: OkHttpClient,
    private val releaseRepo: String = "OpenHands/open-carl",
) {

    suspend fun checkAppUpdate(currentVersion: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$releaseRepo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "no release feed (HTTP ${response.code})"
                val body = response.body?.string().orEmpty()
                val tag = Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content.orEmpty()
                val assets = Json.parseToJsonElement(body).jsonObject["assets"]?.jsonArray?.size ?: 0
                if (tag.isBlank()) "no releases published yet"
                else if (tag.trimStart('v') == currentVersion) "up to date ($currentVersion)"
                else "update available: $tag ($assets assets)"
            }
        }.getOrElse { "update check failed: ${it.message}" }
    }

    suspend fun updatePackages(): String {
        val outcome = privilege.run(
            "apk update && apk upgrade --available",
            PrivilegeLevel.APP,
            timeoutMs = 300_000,
            note = "package-update",
        )
        return if (outcome.ok) {
            "packages updated in ${outcome.durationMs / 1000}s"
        } else {
            "package update failed: " + outcome.output.take(400)
        }
    }

    fun refreshSkills(): Int = runCatching {
        skills.reload()
        skills.skills.value.size
    }.getOrDefault(0)

    suspend fun runAll(currentVersion: String): UpdateReport {
        val app = checkAppUpdate(currentVersion)
        val packages = runCatching { updatePackages() }.getOrElse { it.message.orEmpty() }
        val skillCount = refreshSkills()
        return UpdateReport(appUpdate = app, packages = packages, skills = skillCount)
    }

    fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
