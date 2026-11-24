package dev.opencarl.ai

import dev.opencarl.device.DeviceProfile
import dev.opencarl.device.DeviceProfiler
import dev.opencarl.device.ModelRecommender
import dev.opencarl.finetune.DatasetExporter
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import dev.opencarl.research.ResearchEngine
import dev.opencarl.scripts.ScriptLibrary
import dev.opencarl.scripts.ScriptRunner
import dev.opencarl.service.CarlAccessibilityService
import dev.opencarl.system.AppManager
import dev.opencarl.system.RootTools
import dev.opencarl.system.StorageReport
import dev.opencarl.system.StorageTriage
import dev.opencarl.terminal.TerminalEmulator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The device-facing half of the toolset: screen control, privileged shell,
 * scripts, storage, packages, root and research.
 */
class SystemTools(
    private val profiler: DeviceProfiler,
    private val privilege: PrivilegeManager,
    private val storage: StorageTriage,
    private val apps: AppManager,
    private val root: RootTools,
    private val scripts: ScriptRunner,
    private val research: ResearchEngine,
    private val datasets: DatasetExporter,
    private val emulator: TerminalEmulator,
) {

    fun install(registry: ToolRegistry) {
        registry.register(deviceInfoTool(), ::runDeviceInfo)
        registry.register(screenTool(), ::runScreen)
        registry.register(privilegedTool(), ::runPrivileged)
        registry.register(scriptTool(), ::runScript)
        registry.register(storageTool(), ::runStorage)
        registry.register(appsTool(), ::runApps)
        registry.register(rootTool(), ::runRoot)
        registry.register(researchTool(), ::runResearch)
        registry.register(thinkTool(), ::runThink)
    }

    private fun schema(required: List<String>, properties: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { properties() }
            putJsonArray("required") { required.forEach { add(it) } }
        }

    private fun text(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun deviceInfoTool() = ToolSpec(
        name = "device_info",
        description = "Report this phone's CPU, GPU, RAM, NPU estimate and free storage, and list the local models that will actually run on it.",
        parameters = schema(emptyList()) {},
    )

    private suspend fun runDeviceInfo(args: JsonObject): String {
        val profile = profiler.profile()
        val recommendations = ModelRecommender.recommend(profile, limit = 5)
        return buildString {
            append(profile.summary).append('\n')
            append("tier ").append(profile.tier.label).append(": ").append(profile.tier.blurb).append('\n')
            append("battery ").append(profiler.batteryPercent()).append("%, thermal ")
                .append(profiler.thermalStatus()).append('\n')
            append("\nrecommended local models:\n")
            recommendations.forEach { rec ->
                append("- ").append(rec.model.label).append(" · ").append(rec.headline)
                    .append(" · ").append(rec.reason).append('\n')
            }
            if (recommendations.isEmpty()) append("none: use a cloud provider or a remote server\n")
        }
    }

    private fun screenTool() = ToolSpec(
        name = "screen",
        description = "Read or drive the phone's UI through the accessibility service: read the current screen, tap an element by its text, type into the focused field, swipe, or press back/home/recents.",
        parameters = schema(listOf("action")) {
            put("action", buildJsonObject {
                put("type", "string")
                put("description", "read | tap | type | swipe | back | home | recents | notifications")
                putJsonArray("enum") {
                    listOf("read", "tap", "type", "swipe", "back", "home", "recents", "notifications")
                        .forEach { add(it) }
                }
            })
            put("text", text("Text to tap, or the text to type."))
            put("x1", text("Swipe start x."))
            put("y1", text("Swipe start y."))
            put("x2", text("Swipe end x."))
            put("y2", text("Swipe end y."))
        },
    )

    private suspend fun runScreen(args: JsonObject): String {
        val service = CarlAccessibilityService.instance
            ?: return "The accessibility service is off. Enable it in Settings → Accessibility → open cArl device control."
        return when (args.str("action")?.lowercase()) {
            "read" -> service.describeScreen()
            "tap" -> {
                val label = args.str("text") ?: return "tap needs text"
                if (service.clickText(label)) "tapped \"$label\"" else "no element matched \"$label\""
            }
            "type" -> {
                val value = args.str("text") ?: return "type needs text"
                if (service.typeText(value)) "typed into the focused field" else "no editable field has focus"
            }
            "swipe" -> {
                val x1 = args.str("x1")?.toFloatOrNull() ?: 500f
                val y1 = args.str("y1")?.toFloatOrNull() ?: 1500f
                val x2 = args.str("x2")?.toFloatOrNull() ?: 500f
                val y2 = args.str("y2")?.toFloatOrNull() ?: 400f
                if (service.swipe(x1, y1, x2, y2)) "swiped" else "swipe rejected"
            }
            "back" -> if (service.pressBack()) "back" else "back rejected"
            "home" -> if (service.pressHome()) "home" else "home rejected"
            "recents" -> if (service.openRecents()) "recents" else "recents rejected"
            "notifications" -> if (service.openNotifications()) "notification shade" else "shade rejected"
            else -> "unknown action"
        }
    }

    private fun privilegedTool() = ToolSpec(
        name = "privileged_shell",
        description = "Run a shell command as the app, as the shell user through Shizuku, or as root. Use it for anything the plain terminal cannot do: pm, settings, dumpsys, system file repair. Every command is written to the audit log.",
        parameters = schema(listOf("command")) {
            put("command", text("The shell command to run."))
            put("level", text("app | shizuku | root. Defaults to the highest level available."))
        },
    )

    private suspend fun runPrivileged(args: JsonObject): String {
        val command = args.str("command") ?: return "command is required"
        val level = when (args.str("level")?.lowercase()) {
            "app" -> PrivilegeLevel.APP
            "shizuku" -> PrivilegeLevel.SHIZUKU
            "root" -> PrivilegeLevel.ROOT
            else -> privilege.status.value.best
        }
        val outcome = privilege.run(command, level, note = "agent")
        return buildString {
            append(outcome.summary).append('\n')
            if (outcome.output.isNotBlank()) append(outcome.output.take(8_000))
        }
    }

    private fun scriptTool() = ToolSpec(
        name = "run_script",
        description = "List or run the curated maintenance scripts: setup, debloat, cache trimming, animation speed, battery whitelist, media repair, storage triage, VM tuning.",
        parameters = schema(listOf("action")) {
            put("action", text("list | dry_run | run"))
            put("id", text("Script id, for example debloat or bootstrap."))
        },
    )

    private suspend fun runScript(args: JsonObject): String {
        if (args.str("action").isNullOrBlank() || args.str("action")?.lowercase() == "list") {
            return ScriptLibrary.scripts.joinToString("\n") { script ->
                "- ${script.id} (${script.risk.name.lowercase()}, needs ${script.needs.label}): ${script.summary}"
            }
        }
        val id = args.str("id") ?: return "id is required"
        val script = ScriptLibrary.byId(id) ?: return "unknown script: $id"
        val dryRun = args.str("action")?.lowercase() != "run"
        return scripts.run(script, dryRun = dryRun).report()
    }

    private fun storageTool() = ToolSpec(
        name = "storage",
        description = "Triage device storage: find large files, empty files, duplicates, broken links and empty folders, and optionally delete the safe ones.",
        parameters = schema(listOf("action")) {
            put("action", text("scan | clean"))
            put("path", text("Optional folder to scan, defaults to shared storage."))
        },
    )

    private suspend fun runStorage(args: JsonObject): String {
        val root = args.str("path")?.let { java.io.File(it) }
        val report = storage.scan(roots = root?.let { listOf(it) } ?: storage.defaultRoots())
        val summary = report.summary()
        return if (args.str("action")?.lowercase() == "clean") {
            summary + "removed ${storage.clean(report)} files"
        } else {
            summary + report.issues.take(25).joinToString("\n") { issue ->
                "- ${issue.kind.name.lowercase()} ${issue.path} ${issue.detail}"
            }
        }
    }

    private fun appsTool() = ToolSpec(
        name = "apps",
        description = "Inspect and manage installed packages: list, size, disable/enable, uninstall for the current user, clear cache, force stop, install an APK from a file path.",
        parameters = schema(listOf("action")) {
            put("action", text("list | disable | enable | uninstall | clear_cache | stop | install"))
            put("package", text("Package name, e.g. com.android.chrome."))
            put("path", text("APK path for install."))
        },
    )

    private suspend fun runApps(args: JsonObject): String {
        val packageName = args.str("package")
        return when (args.str("action")?.lowercase()) {
            "list", null -> apps.installed(includeSystem = false).take(40).joinToString("\n") { app ->
                "- ${app.label} (${app.packageName}) ${StorageReport.human(app.sizeBytes)}" +
                    if (app.enabled) "" else " [disabled]"
            }
            "disable" -> packageName?.let { apps.setEnabled(it, false) } ?: "package is required"
            "enable" -> packageName?.let { apps.setEnabled(it, true) } ?: "package is required"
            "uninstall" -> packageName?.let { apps.uninstall(it) } ?: "package is required"
            "clear_cache" -> packageName?.let { apps.clearCache(it) } ?: "package is required"
            "stop" -> packageName?.let { apps.stop(it) } ?: "package is required"
            "install" -> args.str("path")?.let { path ->
                apps.install(android.net.Uri.fromFile(java.io.File(path)))
            } ?: "path is required"
            else -> "unknown action"
        }
    }

    private fun rootTool() = ToolSpec(
        name = "root",
        description = "Root and recovery helpers: report the root manager and boot slot, list or install Magisk modules, reboot, and print a recovery plan.",
        parameters = schema(listOf("action")) {
            put("action", text("status | modules | install_module | reboot | recovery_guide"))
            put("path", text("Module zip path for install_module."))
            put("target", text("Reboot target: system, recovery, bootloader."))
        },
    )

    private suspend fun runRoot(args: JsonObject): String {
        val status = root.status()
        return when (args.str("action")?.lowercase()) {
            "status", null -> "${status.manager}, slot '${status.bootSlot.ifBlank { "n/a" }}', " +
                "${status.modules.size} modules"
            "modules" -> status.modules.joinToString("\n").ifBlank { "no modules" }
            "install_module" -> args.str("path")?.let { root.installModule(java.io.File(it)) }
                ?: "path is required"
            "reboot" -> root.reboot(args.str("target") ?: "system")
            "recovery_guide" -> root.recoveryGuide(status)
            else -> "unknown action"
        }
    }

    private fun researchTool() = ToolSpec(
        name = "deep_research",
        description = "Search the web and read the top pages, returning a citable corpus. Use it for anything current or factual that you cannot answer from memory.",
        parameters = schema(listOf("query")) {
            put("query", text("What to research."))
            put("pages", text("How many pages to read, default 4."))
        },
    )

    private suspend fun runResearch(args: JsonObject): String {
        val query = args.str("query") ?: return "query is required"
        val pages = args.str("pages")?.toIntOrNull() ?: 4
        return research.deepDive(query, pages)
    }

    private fun thinkTool() = ToolSpec(
        name = "think",
        description = "A scratchpad for planning. Nothing is executed; use it to lay out steps before acting.",
        parameters = schema(listOf("thought")) {
            put("thought", text("Your reasoning."))
        },
    )

    private fun runThink(args: JsonObject): String = "noted: " + (args.str("thought")?.take(600) ?: "")

    fun profile(): DeviceProfile = profiler.profile()

    fun exportDataset(history: List<dev.opencarl.app.ChatItem>): String =
        datasets.export(history).absolutePath
}
