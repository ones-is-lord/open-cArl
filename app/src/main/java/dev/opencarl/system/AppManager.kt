package dev.opencarl.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val enabled: Boolean,
    val versionName: String,
    val sizeBytes: Long,
    val cacheBytes: Long,
    val dataBytes: Long,
)

/** Package inventory, APK installs, cache clearing and user-level debloating. */
class AppManager(
    private val context: Context,
    private val privilege: PrivilegeManager,
) {

    fun installed(includeSystem: Boolean = true): List<AppEntry> {
        val manager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getInstalledPackages(0)
        }
        val storage = context.getSystemService(StorageStatsManager::class.java)
        val volumeUuid: java.util.UUID = runCatching {
            context.getSystemService(StorageManager::class.java).getUuidForPath(context.filesDir)
        }.getOrDefault(java.util.UUID.nameUUIDFromBytes("primary".toByteArray()))
        return packages.mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && system) return@mapNotNull null
            var size = 0L
            var cache = 0L
            var data = 0L
            runCatching {
                val stats = storage.queryStatsForUid(volumeUuid, app.uid)
                size = stats.appBytes
                cache = stats.cacheBytes
                data = stats.dataBytes
            }
            AppEntry(
                packageName = info.packageName,
                label = manager.getApplicationLabel(app).toString(),
                system = system,
                enabled = app.enabled,
                versionName = info.versionName.orEmpty(),
                sizeBytes = size,
                cacheBytes = cache,
                dataBytes = data,
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** Installs an APK through a PackageInstaller session; the user still confirms. */
    suspend fun install(uri: Uri): String = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext "could not read the APK"
            stream.use { input ->
                val output = session.openWrite("apk", 0, -1)
                try {
                    input.copyTo(output)
                    session.fsync(output)
                } finally {
                    output.close()
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(pending.intentSender)
        }
        "install session $sessionId started; confirm the system prompt"
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean): String {
        val flag = if (enabled) "enable" else "disable-user --user 0"
        val outcome = privilege.run("pm $flag $packageName", PrivilegeLevel.SHIZUKU, note = "app-toggle")
        return if (outcome.ok) "$packageName ${if (enabled) "enabled" else "disabled"}" else outcome.output
    }

    suspend fun uninstall(packageName: String, keepData: Boolean = false): String {
        val command = if (keepData) "pm uninstall -k --user 0 $packageName" else "pm uninstall --user 0 $packageName"
        val outcome = privilege.run(command, PrivilegeLevel.SHIZUKU, note = "uninstall")
        return if (outcome.ok) "$packageName removed for this user" else outcome.output
    }

    suspend fun clearCache(packageName: String): String {
        val outcome = privilege.run("pm clear --cache-only $packageName", PrivilegeLevel.SHIZUKU, note = "clear-cache")
        return if (outcome.ok) "cache cleared for $packageName" else outcome.output
    }

    suspend fun stop(packageName: String): String {
        val outcome = privilege.run("am force-stop $packageName", PrivilegeLevel.SHIZUKU, note = "force-stop")
        return if (outcome.ok) "$packageName stopped" else outcome.output
    }

    fun apkPathOf(packageName: String): File? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        info.applicationInfo?.publicSourceDir?.let(::File)
    }.getOrNull()
}
