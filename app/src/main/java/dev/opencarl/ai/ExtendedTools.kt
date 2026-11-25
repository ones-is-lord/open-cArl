package dev.opencarl.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.opencarl.net.NetTools
import dev.opencarl.net.WifiScanner
import dev.opencarl.persona.Persona
import dev.opencarl.persona.Personas
import dev.opencarl.plan.PlanStore
import dev.opencarl.privilege.PrivilegeLevel
import dev.opencarl.privilege.PrivilegeManager
import dev.opencarl.schedule.TaskScheduler
import dev.opencarl.snapshot.SessionSnapshots
import dev.opencarl.study.StudyStore
import dev.opencarl.system.Diagnostics
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * The "middleman" half of the toolbox: files and code, interactive shell, the
 * userland package manager, network and wifi work, diagnostics, planning,
 * spaced-repetition study, scheduling, snapshots, clipboard, notifications,
 * personas and a persistent user profile.
 */
class ExtendedTools(
    private val context: Context,
    private val privilege: PrivilegeManager,
    private val net: NetTools,
    private val wifi: WifiScanner,
    private val diagnostics: Diagnostics,
    private val plans: PlanStore,
    private val study: StudyStore,
    private val scheduler: TaskScheduler,
    private val snapshots: SessionSnapshots,
    private val filesRoot: () -> File,
    private val sendToShell: (String) -> Unit,
    private val screenText: () -> String,
    private val transcript: () -> String,
    private val chatJson: () -> String,
    private val personaState: () -> Pair<String, List<Persona>>,
    private val setPersona: (String) -> Unit,
) {

    private val profileFile: File get() = File(context.filesDir, "profile/user.json")

    fun install(registry: ToolRegistry) {
        registry.register(filesTool(), ::runFiles)
        registry.register(scriptTool(), ::runScript)
        registry.register(codeTool(), ::runCode)
        registry.register(shellSendTool(), ::runShellSend)
        registry.register(packagesTool(), ::runPackages)
        registry.register(netTool(), ::runNet)
        registry.register(wifiTool(), ::runWifi)
        registry.register(diagnosticsTool(), ::runDiagnostics)
        registry.register(planTool(), ::runPlan)
        registry.register(studyTool(), ::runStudy)
        registry.register(scheduleTool(), ::runSchedule)
        registry.register(snapshotTool(), ::runSnapshot)
        registry.register(clipboardTool(), ::runClipboard)
        registry.register(notifyTool(), ::runNotify)
        registry.register(personaTool(), ::runPersona)
        registry.register(profileTool(), ::runProfile)
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

    // --------------------------------------------------------------- files

    private fun filesTool() = ToolSpec(
        name = "files",
        description = "Work inside the workspace: list, read, write, append, mkdir, delete, move, copy, stat or search file contents. Paths are relative to the workspace root.",
        parameters = schema(listOf("action")) {
            put("action", text("list | read | write | append | mkdir | delete | move | copy | stat | search"))
            put("path", text("Target path."))
            put("to", text("Destination for move and copy."))
            put("content", text("Content for write and append."))
            put("pattern", text("Regex for search."))
        },
    )

    private suspend fun runFiles(args: JsonObject): String {
        val root = filesRoot().apply { mkdirs() }.canonicalFile
        fun resolve(path: String?): File {
            val candidate = File(root, path?.trimStart('/') ?: ".")
            val canonical = candidate.canonicalFile
            require(canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)) {
                "path escapes the workspace: $path"
            }
            return canonical
        }
        return when (args.str("action")?.lowercase()) {
            "list", null -> {
                val dir = resolve(args.str("path"))
                dir.listFiles().orEmpty().sortedBy { it.name }.joinToString("\n") { file ->
                    val kind = if (file.isDirectory) "dir " else "file"
                    "$kind ${file.length().toString().padStart(10)}  ${file.name}"
                }.ifBlank { "empty directory" }
            }
            "read" -> {
                val file = resolve(args.str("path") ?: return "path is required")
                if (!file.isFile) "not a file" else file.readText().take(200_000)
            }
            "write" -> {
                val file = resolve(args.str("path") ?: return "path is required")
                file.parentFile?.mkdirs()
                file.writeText(args.str("content").orEmpty())
                "wrote ${file.length()} bytes to ${file.relativeTo(root).path}"
            }
            "append" -> {
                val file = resolve(args.str("path") ?: return "path is required")
                file.parentFile?.mkdirs()
                file.appendText(args.str("content").orEmpty())
                "appended, now ${file.length()} bytes"
            }
            "mkdir" -> {
                val dir = resolve(args.str("path") ?: return "path is required")
                dir.mkdirs()
                "created ${dir.relativeTo(root).path}"
            }
            "delete" -> {
                val target = resolve(args.str("path") ?: return "path is required")
                when {
                    target == root -> "refusing to delete the workspace root"
                    target.deleteRecursively() -> "deleted ${target.relativeTo(root).path}"
                    else -> "delete failed"
                }
            }
            "move", "copy" -> {
                val from = resolve(args.str("path") ?: return "path is required")
                val to = resolve(args.str("to") ?: return "to is required")
                to.parentFile?.mkdirs()
                val ok = if (args.str("action")?.lowercase() == "move") from.renameTo(to) else from.copyRecursively(to, true)
                if (ok) "${args.str("action")} ok" else "${args.str("action")} failed"
            }
            "stat" -> {
                val file = resolve(args.str("path") ?: return "path is required")
                "${file.relativeTo(root).path}\n  size ${file.length()} bytes\n  dir ${file.isDirectory}\n  modified ${java.util.Date(file.lastModified())}"
            }
            "search" -> {
                val pattern = Regex(args.str("pattern") ?: return "pattern is required", RegexOption.IGNORE_CASE)
                val hits = mutableListOf<String>()
                root.walkTopDown().filter { it.isFile && it.length() < 2_000_000 }.take(4_000).forEach { file ->
                    runCatching {
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                if (hits.size < 80 && pattern.containsMatchIn(line)) {
                                    hits += "${file.relativeTo(root).path}:${index + 1}: ${line.take(160)}"
                                }
                            }
                        }
                    }
                }
                hits.joinToString("\n").ifBlank { "no matches" }
            }
            else -> "unknown action"
        }
    }

    // -------------------------------------------------------- script / code

    private fun scriptTool() = ToolSpec(
        name = "script",
        description = "Write, list, run or delete your own shell scripts in the workspace bin folder. This is how the agent extends itself.",
        parameters = schema(listOf("action")) {
            put("action", text("write | run | list | delete"))
            put("name", text("Script name, for example backup.sh."))
            put("content", text("Script body for write."))
        },
    )

    private suspend fun runScript(args: JsonObject): String {
        val bin = File(filesRoot(), "bin").apply { mkdirs() }
        return when (args.str("action")?.lowercase()) {
            "write" -> {
                val name = args.str("name") ?: return "name is required"
                val file = File(bin, name)
                file.writeText(args.str("content").orEmpty())
                file.setExecutable(true)
                "wrote ${file.absolutePath}"
            }
            "run" -> {
                val name = args.str("name") ?: return "name is required"
                val file = File(bin, name)
                if (!file.isFile) return "no such script: $name"
                file.setExecutable(true)
                val outcome = privilege.run("sh '${file.absolutePath}'", PrivilegeLevel.APP, timeoutMs = 120_000, note = "script:$name")
                outcome.summary + "\n" + outcome.output.take(8_000)
            }
            "list" -> bin.listFiles().orEmpty().joinToString("\n") { "${it.name}  ${it.length()}b" }.ifBlank { "no scripts" }
            "delete" -> {
                val name = args.str("name") ?: return "name is required"
                if (File(bin, name).delete()) "deleted $name" else "delete failed"
            }
            else -> "unknown action"
        }
    }

    private fun codeTool() = ToolSpec(
        name = "code",
        description = "Run a snippet in python3, node or sh without leaving the app. The file is written to the workspace scratch folder, executed, and the output returned.",
        parameters = schema(listOf("language", "code")) {
            put("language", text("python3 | node | sh"))
            put("code", text("The program text."))
        },
    )

    private suspend fun runCode(args: JsonObject): String {
        val language = args.str("language")?.lowercase() ?: return "language is required"
        val code = args.str("code") ?: return "code is required"
        val extension = when (language) {
            "python3", "python" -> "py"
            "node", "js", "javascript" -> "js"
            "sh", "bash" -> "sh"
            else -> return "unsupported language: $language"
        }
        val interpreter = when (extension) {
            "py" -> "python3"
            "js" -> "node"
            else -> "sh"
        }
        val dir = File(filesRoot(), "scratch").apply { mkdirs() }
        val file = File(dir, "snippet-${System.currentTimeMillis()}.$extension")
        file.writeText(code)
        val probe = privilege.run("command -v $interpreter", PrivilegeLevel.APP)
        if (probe.output.isBlank()) {
            return "$interpreter is not installed in this shell. Install it with the packages tool first."
        }
        val outcome = privilege.run("$interpreter '${file.absolutePath}'", PrivilegeLevel.APP, timeoutMs = 120_000, note = "code:$language")
        return outcome.summary + "\n" + outcome.output.take(8_000)
    }

    private fun shellSendTool() = ToolSpec(
        name = "shell_send",
        description = "Type into the live terminal session, for interactive programs like ssh, vim, python REPL or top. Send the keystrokes and then read the screen back.",
        parameters = schema(listOf("text")) {
            put("text", text("What to type. A trailing newline is added."))
            put("screen", text("true to return the visible screen afterwards, default true."))
        },
    )

    private fun runShellSend(args: JsonObject): String {
        val text = args.str("text") ?: return "text is required"
        sendToShell(text.trimEnd('\n') + "\r")
        if (args.str("screen")?.lowercase() == "false") return "sent"
        return "sent. screen now:\n" + screenText().take(4_000)
    }

    // ------------------------------------------------------------ packages

    private fun packagesTool() = ToolSpec(
        name = "packages",
        description = "Find, install, remove and upgrade packages in the Linux userland (apk, apt or pkg, whichever exists). Use it when you need a tool that is not installed.",
        parameters = schema(listOf("action")) {
            put("action", text("search | install | remove | upgrade | info | manager"))
            put("name", text("Package name or search term."))
        },
    )

    private suspend fun runPackages(args: JsonObject): String {
        val managerProbe = privilege.run(
            "command -v apk || command -v apt-get || command -v pkg || true",
            PrivilegeLevel.APP,
        ).output.trim()
        val manager = when {
            managerProbe.contains("apk") -> "apk"
            managerProbe.contains("apt-get") -> "apt"
            managerProbe.contains("pkg") -> "pkg"
            else -> ""
        }
        if (args.str("action")?.lowercase() == "manager" || manager.isBlank()) {
            return if (manager.isBlank()) {
                "no package manager found in this shell. Start the Linux runtime first, or install one with the terminal."
            } else {
                "package manager: $manager"
            }
        }
        val name = args.str("name").orEmpty()
        val command = when (args.str("action")?.lowercase()) {
            "search" -> if (manager == "apk") "apk search $name" else "$manager search $name"
            "install" -> if (manager == "apk") "apk add --no-cache $name" else "$manager install -y $name"
            "remove" -> if (manager == "apk") "apk del $name" else "$manager remove -y $name"
            "upgrade" -> if (manager == "apk") "apk update && apk upgrade" else "$manager update && $manager upgrade -y"
            "info" -> if (manager == "apk") "apk info -a $name" else "$manager show $name"
            else -> return "unknown action"
        }
        val outcome = privilege.run(command, PrivilegeLevel.APP, timeoutMs = 300_000, note = "pkg:${args.str("action")}")
        return outcome.summary + "\n" + outcome.output.take(8_000)
    }

    // --------------------------------------------------------- net and wifi

    private fun netTool() = ToolSpec(
        name = "net",
        description = "Network work: ping a host, resolve DNS, probe an HTTP endpoint, TCP-scan ports, list interfaces, find the public address. Only scan hosts you own or may test.",
        parameters = schema(listOf("action")) {
            put("action", text("ping | dns | probe | scan | ifaces | public_ip"))
            put("target", text("Host, domain or URL."))
            put("ports", text("Port range like 1-1024 or 22,80,443. Defaults to common ports."))
        },
    )

    private suspend fun runNet(args: JsonObject): String {
        val target = args.str("target").orEmpty()
        return when (args.str("action")?.lowercase()) {
            "ping" -> net.ping(target.ifBlank { return "target is required" })
            "dns" -> net.dns(target.ifBlank { return "target is required" })
            "probe" -> net.probe(target.ifBlank { return "target is required" })
            "ifaces" -> net.localAddresses()
            "public_ip" -> net.publicAddress()
            "scan" -> {
                if (target.isBlank()) return "target is required"
                val spec = args.str("ports")
                val ports: IntRange = when {
                    spec.isNullOrBlank() -> NetTools.DEFAULT_PORTS.min()..NetTools.DEFAULT_PORTS.max()
                    spec.contains('-') -> {
                        val parts = spec.split("-")
                        (parts[0].trim().toIntOrNull() ?: 1)..(parts[1].trim().toIntOrNull() ?: 1024)
                    }
                    else -> {
                        val list = spec.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (list.isEmpty()) 1..1024 else list.min()..list.max()
                    }
                }
                val open = net.portScan(target, ports)
                if (open.isEmpty()) "no open ports in $ports on $target"
                else "open on $target:\n" + open.joinToString("\n") { it.line() }
            }
            else -> "unknown action"
        }
    }

    private fun wifiTool() = ToolSpec(
        name = "wifi",
        description = "Scan visible wifi networks, log them to a wardriving CSV and summarise by band. Needs the location permission; Android throttles repeat scans.",
        parameters = schema(listOf("action")) {
            put("action", text("scan | log | summary"))
        },
    )

    private suspend fun runWifi(args: JsonObject): String {
        if (!wifi.permissionGranted()) return "location permission is not granted, so wifi results are hidden"
        if (!wifi.enabled()) return "wifi is off"
        val points = wifi.scan()
        return when (args.str("action")?.lowercase()) {
            "log" -> "logged ${wifi.append(points)} networks to ${wifi.logFile().absolutePath}"
            else -> wifi.summary(points) + points.sortedByDescending { it.rssi }.take(15)
                .joinToString("\n") { "  ${it.ssid.ifBlank { "<hidden>" }} ${it.bssid} ${it.rssi}dBm ${it.band}" }
        }
    }

    private fun diagnosticsTool() = ToolSpec(
        name = "diagnostics",
        description = "What the phone is doing now: cpu, memory, storage, battery, thermal, uptime, top processes, logcat and dumpsys (last two through Shizuku).",
        parameters = schema(listOf("action")) {
            put("action", text("snapshot | top | logcat | dumpsys"))
            put("filter", text("logcat grep term, or the dumpsys service name."))
        },
    )

    private suspend fun runDiagnostics(args: JsonObject): String =
        when (args.str("action")?.lowercase()) {
            "top" -> diagnostics.topProcesses()
            "logcat" -> diagnostics.logcat(filter = args.str("filter").orEmpty())
            "dumpsys" -> diagnostics.dumpsys(args.str("filter")?.ifBlank { "battery" } ?: "battery")
            else -> diagnostics.snapshot()
        }

    // ------------------------------------------------------ plan and study

    private fun planTool() = ToolSpec(
        name = "plan",
        description = "A persistent task list you and the user share: add, list, mark doing or done, remove, clear finished. Use it for multi-step work and anything the user wants tracked.",
        parameters = schema(listOf("action")) {
            put("action", text("add | list | doing | done | todo | remove | clear_done"))
            put("text", text("Task text for add."))
            put("id", text("Task id."))
            put("tag", text("Optional tag, e.g. work or home."))
            put("due", text("yyyy-MM-dd or yyyy-MM-dd HH:mm."))
        },
    )

    private fun runPlan(args: JsonObject): String {
        return when (args.str("action")?.lowercase()) {
            "add" -> {
                val text = args.str("text") ?: return "text is required"
                val due = args.str("due")?.let { parseDate(it) }
                val item = plans.add(text, args.str("tag").orEmpty(), due)
                "added #${item.id}: ${item.text}"
            }
            "list", null -> plans.summary()
            "doing" -> planStatus(args, "doing")
            "done" -> planStatus(args, "done")
            "todo" -> planStatus(args, "todo")
            "remove" -> {
                val id = args.str("id")?.toLongOrNull() ?: return "id is required"
                if (plans.remove(id)) "removed #$id" else "no such task"
            }
            "clear_done" -> "cleared ${plans.clearDone()} finished tasks"
            else -> "unknown action"
        }
    }

    private fun planStatus(args: JsonObject, status: String): String {
        val id = args.str("id")?.toLongOrNull() ?: return "id is required"
        return if (plans.setStatus(id, status)) "#$id is $status" else "no such task"
    }

    private fun studyTool() = ToolSpec(
        name = "study",
        description = "Spaced repetition: add cards, see what is due, grade recall, read stats. Use it when teaching or quizzing so weak items come back.",
        parameters = schema(listOf("action")) {
            put("action", text("add | due | grade | stats"))
            put("front", text("Question side."))
            put("back", text("Answer side."))
            put("deck", text("Deck name, default 'default'."))
            put("id", text("Card id for grade."))
            put("quality", text("0 blackout to 5 instant."))
        },
    )

    private fun runStudy(args: JsonObject): String {
        return when (args.str("action")?.lowercase()) {
            "add" -> {
                val front = args.str("front") ?: return "front is required"
                val back = args.str("back") ?: return "back is required"
                "card #${study.add(front, back, args.str("deck").orEmpty()).id} added"
            }
            "due" -> study.due(10).joinToString("\n") { "#${it.id} ${it.front}" }.ifBlank { "nothing due" }
            "grade" -> {
                val id = args.str("id")?.toLongOrNull() ?: return "id is required"
                val quality = args.str("quality")?.toIntOrNull() ?: 3
                val card = study.grade(id, quality) ?: return "no such card"
                "#$id next in ${card.intervalDays} day(s)"
            }
            else -> study.stats()
        }
    }

    private fun scheduleTool() = ToolSpec(
        name = "schedule",
        description = "Schedule work for later: an agent prompt that runs when the app is open, or a shell command that runs headless. One-off or repeating.",
        parameters = schema(listOf("action")) {
            put("action", text("add | list | cancel"))
            put("label", text("Short name."))
            put("prompt", text("Agent prompt to run."))
            put("command", text("Shell command to run instead."))
            put("at", text("yyyy-MM-dd HH:mm, or +30m, +2h, +1d."))
            put("repeat", text("Repeat every N minutes, 0 for once."))
            put("id", text("Task id for cancel."))
        },
    )

    private fun runSchedule(args: JsonObject): String {
        return when (args.str("action")?.lowercase()) {
            "add" -> {
                val label = args.str("label") ?: "task"
                val at = parseDate(args.str("at").orEmpty()) ?: return "could not read the time"
                val task = scheduler.schedule(
                    label = label,
                    atMillis = at,
                    prompt = args.str("prompt").orEmpty(),
                    command = args.str("command").orEmpty(),
                    repeatMinutes = args.str("repeat")?.toIntOrNull() ?: 0,
                )
                "scheduled #${task.id} '$label' for ${java.util.Date(at)}"
            }
            "list" -> scheduler.load().joinToString("\n") { task ->
                "#${task.id} ${task.label} at ${java.util.Date(task.at)}" +
                    (if (task.repeatMinutes > 0) " every ${task.repeatMinutes}m" else "")
            }.ifBlank { "nothing scheduled" }
            "cancel" -> {
                val id = args.str("id")?.toLongOrNull() ?: return "id is required"
                if (scheduler.cancel(id)) "cancelled #$id" else "no such task"
            }
            else -> "unknown action"
        }
    }

    private fun snapshotTool() = ToolSpec(
        name = "snapshot",
        description = "Save or roll back the workspace: create a snapshot with the transcript and conversation, list them, diff one against current files, or restore it.",
        parameters = schema(listOf("action")) {
            put("action", text("create | list | diff | restore"))
            put("name", text("Snapshot name."))
            put("note", text("Why you took it."))
            put("id", text("Snapshot id."))
        },
    )

    private suspend fun runSnapshot(args: JsonObject): String {
        return when (args.str("action")?.lowercase()) {
            "create" -> {
                val snap = snapshots.create(
                    name = args.str("name").orEmpty(),
                    note = args.str("note").orEmpty(),
                    transcript = transcript(),
                    chatJson = chatJson(),
                )
                "snapshot ${snap.id}: ${snap.files} files, ${snap.bytes / 1024}KB"
            }
            "list" -> snapshots.list().joinToString("\n") { "${it.id}  ${it.whenText}  ${it.files} files  ${it.name}" }
                .ifBlank { "no snapshots" }
            "diff" -> {
                val id = args.str("id") ?: return "id is required"
                val snap = snapshots.list().firstOrNull { it.id == id } ?: return "no such snapshot"
                snapshots.diff(snap).joinToString("\n").ifBlank { "no changes since $id" }
            }
            "restore" -> {
                val id = args.str("id") ?: return "id is required"
                val snap = snapshots.list().firstOrNull { it.id == id } ?: return "no such snapshot"
                "restored ${snapshots.restore(snap)} files from $id"
            }
            else -> "unknown action"
        }
    }

    // ------------------------------------------------ clipboard and notify

    private fun clipboardTool() = ToolSpec(
        name = "clipboard",
        description = "Read or write the system clipboard, for moving text between the agent and other apps.",
        parameters = schema(listOf("action")) {
            put("action", text("read | write"))
            put("text", text("Text to place on the clipboard."))
        },
    )

    private fun runClipboard(args: JsonObject): String {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return "no clipboard service"
        return when (args.str("action")?.lowercase()) {
            "write" -> {
                manager.setPrimaryClip(ClipData.newPlainText("open cArl", args.str("text").orEmpty()))
                "copied"
            }
            else -> manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                .ifBlank { "clipboard is empty" }
        }
    }

    private fun notifyTool() = ToolSpec(
        name = "notify",
        description = "Post a notification to the user's shade, for finished long jobs and reminders.",
        parameters = schema(listOf("title")) {
            put("title", text("Notification title."))
            put("text", text("Body text."))
        },
    )

    private fun runNotify(args: JsonObject): String {
        val title = args.str("title") ?: return "title is required"
        val body = args.str("text").orEmpty()
        val notification = NotificationCompat.Builder(context, "schedule")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100_000).toInt(), notification)
        }.onFailure { return "notifications are blocked: ${it.message}" }
        return "notified"
    }

    // ------------------------------------------------------- persona, profile

    private fun personaTool() = ToolSpec(
        name = "persona",
        description = "Read or change your own tone. Use it when the user asks for a different register, and switch back when they are done.",
        parameters = schema(listOf("action")) {
            put("action", text("list | get | set"))
            put("id", text("Persona id to switch to."))
        },
    )

    private fun runPersona(args: JsonObject): String {
        val (current, custom) = personaState()
        return when (args.str("action")?.lowercase()) {
            "set" -> {
                val id = args.str("id") ?: return "id is required"
                val persona = Personas.byId(id, custom)
                setPersona(persona.id)
                "now speaking as ${persona.name}"
            }
            "get" -> "current persona: $current"
            else -> Personas.all(custom).joinToString("\n") { persona ->
                "${if (persona.id == current) "*" else " "} ${persona.id}: ${persona.tagline}"
            }
        }
    }

    private fun profileTool() = ToolSpec(
        name = "profile",
        description = "The long-term picture of this user: habits, needs, wants, plans, preferences. Write facts here when you learn them so you never ask twice.",
        parameters = schema(listOf("action")) {
            put("action", text("set | get | forget"))
            put("key", text("Category, e.g. habit, need, want, plan, preference, project."))
            put("value", text("The fact to store."))
        },
    )

    private fun runProfile(args: JsonObject): String {
        val map = loadProfile().toMutableMap()
        return when (args.str("action")?.lowercase()) {
            "set" -> {
                val key = args.str("key")?.lowercase() ?: return "key is required"
                val value = args.str("value") ?: return "value is required"
                val entries = map[key].orEmpty().toMutableList()
                if (entries.none { it.equals(value, true) }) entries += value
                map[key] = entries
                saveProfile(map)
                "$key: ${entries.size} entries"
            }
            "forget" -> {
                val key = args.str("key")?.lowercase() ?: return "key is required"
                map.remove(key)
                saveProfile(map)
                "forgot $key"
            }
            else -> if (map.isEmpty()) "nothing stored yet" else map.entries.joinToString("\n") { (key, values) ->
                "$key: " + values.joinToString("; ")
            }
        }
    }

    private fun loadProfile(): Map<String, List<String>> {
        val file = profileFile
        if (!file.exists()) return emptyMap()
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(file.readText())
            element.jsonObject.mapValues { (_, value) ->
                (value as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveProfile(map: Map<String, List<String>>) {
        profileFile.parentFile?.mkdirs()
        val json = buildJsonObject {
            map.forEach { (key, values) ->
                putJsonArray(key) { values.forEach { add(it) } }
            }
        }
        profileFile.writeText(json.toString())
    }

    /** Accepts epoch millis, +30m, +2h, +1d, HH:mm, yyyy-MM-dd or yyyy-MM-dd HH:mm. */
    private fun parseDate(input: String): Long? {
        val text = input.trim()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return it }
        Regex("^\\+(\\d+)([mhd])$").find(text)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val unit = when (match.groupValues[2]) {
                "m" -> 60_000L
                "h" -> 3_600_000L
                else -> 86_400_000L
            }
            return System.currentTimeMillis() + amount * unit
        }
        for (pattern in listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd")) {
            val parsed = runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                    .apply { isLenient = false }
                    .parse(text)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        Regex("^(\\d{1,2}):(\\d{2})$").find(text)?.let { match ->
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, match.groupValues[1].toInt())
                set(java.util.Calendar.MINUTE, match.groupValues[2].toInt())
                set(java.util.Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }
        return null
    }
}
