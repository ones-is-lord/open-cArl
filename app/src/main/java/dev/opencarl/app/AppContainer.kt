package dev.opencarl.app

import android.content.Context
import dev.opencarl.ai.Agent
import dev.opencarl.ai.BuiltinTools
import dev.opencarl.ai.LlmFactory
import dev.opencarl.ai.SystemPrompt
import dev.opencarl.ai.ToolCall
import dev.opencarl.ai.ToolRegistry
import dev.opencarl.data.ApprovalPolicy
import dev.opencarl.data.SecureStore
import dev.opencarl.data.SettingsRepository
import dev.opencarl.mcp.McpManager
import dev.opencarl.memory.MemoryStore
import dev.opencarl.runtime.CommandRunner
import dev.opencarl.runtime.RuntimeController
import dev.opencarl.runtime.Workspace
import dev.opencarl.skills.SkillStore
import dev.opencarl.terminal.PtySession
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Process-wide singletons: settings, skills, memory, the PTY session and the
 * MCP connections. The UI reads state from here; the agent uses the same shell.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val settings = SettingsRepository(appContext, SecureStore(appContext))
    val skills = SkillStore(appContext, appContext.filesDir)
    val memory = MemoryStore { File(appContext.filesDir, "memory/entries.json") }
    val runtime = RuntimeController(appContext)
    val prootInstaller = dev.opencarl.runtime.ProotInstaller(appContext, runtime)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val mcp = McpManager(
        http = http,
        workDirProvider = { runtime.mcpWorkDir(settings.settings.value.runtime) },
        wrapCommand = { command -> runtime.wrapCommand(settings.settings.value.runtime)(command) },
    )

    val emulator = dev.opencarl.terminal.TerminalEmulator(80, 24)

    val audit = dev.opencarl.privilege.AuditLog { File(appContext.filesDir, "audit/audit.jsonl") }
    val privilege = dev.opencarl.privilege.PrivilegeManager(appContext, audit)
    val profiler = dev.opencarl.device.DeviceProfiler(appContext)
    val storage = dev.opencarl.system.StorageTriage(appContext)
    val apps = dev.opencarl.system.AppManager(appContext, privilege)
    val rootTools = dev.opencarl.system.RootTools(privilege)
    val scriptRunner = dev.opencarl.scripts.ScriptRunner(privilege)
    val research = dev.opencarl.research.ResearchEngine(http) { settings.secret("tavily") }
    val datasets = dev.opencarl.finetune.DatasetExporter { File(appContext.filesDir, "workspace") }
    val models = dev.opencarl.local.ModelDownloader({ File(appContext.filesDir, "models") }, http)
    val updates = dev.opencarl.update.UpdateManager(appContext, privilege, skills, http)
    val plans = dev.opencarl.plan.PlanStore { File(appContext.filesDir, "plan/tasks.json") }
    val study = dev.opencarl.study.StudyStore { File(appContext.filesDir, "study/cards.json") }
    val netTools = dev.opencarl.net.NetTools(http)
    val wifi = dev.opencarl.net.WifiScanner(appContext)
    val diagnostics = dev.opencarl.system.Diagnostics(appContext, privilege)
    val scheduler = dev.opencarl.schedule.TaskScheduler(appContext) { File(appContext.filesDir, "schedule/tasks.json") }
    val snapshots = dev.opencarl.snapshot.SessionSnapshots(
        rootDir = { File(appContext.filesDir, "snapshots") },
        workspaceRoot = { workspace.root() },
    )
    val speaker = dev.opencarl.voice.Speaker(appContext)

    init {
        plans.load()
        study.load()
    }

    private val _sessionExit = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val sessionExit: SharedFlow<Int> = _sessionExit.asSharedFlow()

    val workspace = Workspace { runtime.workspaceRoot(settings.settings.value.runtime) }

    private var pty: PtySession? = null
    private var ptyKey: String? = null

    val runner: CommandRunner = object : CommandRunner {
        override val cwd: String
            get() = pty?.cwd ?: runtime.homeDir.absolutePath
        override suspend fun run(command: String, timeoutMs: Long): PtySession.CommandResult {
            val session = ensureSession()
                ?: return PtySession.CommandResult(127, "Error: the shell is not running", null)
            return session.runCommand(command, timeoutMs)
        }
    }

    @Synchronized
    fun ensureSession(): PtySession? {
        val runtimeSettings = settings.settings.value.runtime
        val key = runtimeSettings.backend.name + "/" + runtimeSettings.distro + "/" + runtime.prootInstalled()
        val existing = pty
        if (existing != null && existing.isRunning && ptyKey == key) return existing
        existing?.close()
        val launch = runtime.shellLaunch(runtimeSettings)
        val session = PtySession(
            launch = launch,
            onOutput = { text -> emulator.feed(text) },
            onExit = { code -> _sessionExit.tryEmit(code) },
        )
        val started = session.start()
        pty = if (started) session else null
        ptyKey = key
        return pty
    }

    fun userProfile(): String {
        val file = File(appContext.filesDir, "profile/user.json")
        if (!file.exists()) return ""
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(file.readText())
            element.jsonObject.entries.joinToString("\n") { (key, value) ->
                val values = (value as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                "$key: " + values.joinToString("; ")
            }
        }.getOrDefault("")
    }

    fun writeTerminal(text: String) {
        pty?.write(text)
    }

    fun writeTerminalBytes(bytes: ByteArray) {
        pty?.write(bytes)
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        pty?.resize(rows, cols)
    }

    fun interrupt() {
        pty?.interrupt()
    }

    @Synchronized
    fun restartSession() {
        pty?.close()
        pty = null
        ptyKey = null
        ensureSession()
    }

    /** Rebuilds MCP connections and registers their tools into [registry]. */
    suspend fun refreshMcp(registry: ToolRegistry) {
        val servers = settings.settings.value.mcpServers
        val secrets = mapOf(
            "GITHUB_TOKEN" to settings.secret(SettingsRepository.SECRET_GITHUB),
            "TAVILY_API_KEY" to settings.secret(SettingsRepository.SECRET_TAVILY),
            "HF_TOKEN" to settings.secret(SettingsRepository.SECRET_HF),
        )
        mcp.connectAll(servers, secrets)
        mcp.registerTools(registry)
    }

    fun buildRegistry(): ToolRegistry {
        val registry = ToolRegistry()
        BuiltinTools(runner, workspace, skills, memory, http).install(registry)
        dev.opencarl.ai.SystemTools(
            profiler = profiler,
            privilege = privilege,
            storage = storage,
            apps = apps,
            root = rootTools,
            scripts = scriptRunner,
            research = research,
            datasets = datasets,
            emulator = emulator,
        ).install(registry)
        dev.opencarl.ai.ExtendedTools(
            context = appContext,
            privilege = privilege,
            net = netTools,
            wifi = wifi,
            diagnostics = diagnostics,
            plans = plans,
            study = study,
            scheduler = scheduler,
            snapshots = snapshots,
            filesRoot = { workspace.root() },
            sendToShell = { text -> writeTerminal(text) },
            screenText = { emulator.visibleText() },
            transcript = { emulator.visibleText() },
            chatJson = { dev.opencarl.app.ChatMirror.json() },
            personaState = {
                val current = settings.settings.value
                current.personaId to current.customPersonas
            },
            setPersona = { id -> settings.update { it.copy(personaId = id) } },
        ).install(registry)
        mcp.registerTools(registry)
        return registry
    }

    fun buildAgent(approver: suspend (ToolCall, String) -> Boolean): Agent {
        val snapshot = settings.settings.value
        val provider = snapshot.activeProvider
        val client = LlmFactory.create(http, provider, settings.apiKey(provider.id))
        val registry = buildRegistry()
        val policy = when (snapshot.approvalPolicy) {
            ApprovalPolicy.ALWAYS_ASK -> "ask before every shell command and file write"
            ApprovalPolicy.SMART -> "ask before destructive commands, otherwise proceed"
            ApprovalPolicy.AUTO -> "proceed without asking; the user has opted into autonomous mode"
        }
        return Agent(
            client = client,
            registry = registry,
            maxSteps = snapshot.maxSteps,
            systemPrompt = {
                SystemPrompt.build(
                    runtimeLabel = runtime.label(snapshot.runtime),
                    cwd = runner.cwd,
                    workspaceLabel = workspace.root().absolutePath,
                    skillsIndex = skills.indexForPrompt(),
                    memoryDigest = if (snapshot.memoryEnabled) memory.digest() else "",
                    approvalPolicy = policy,
                    extra = snapshot.systemPromptExtra,
                    persona = dev.opencarl.persona.Personas.byId(snapshot.personaId, snapshot.customPersonas).prompt,
                    userProfile = userProfile(),
                    planDigest = plans.summary().take(1_200),
                )
            },
            approver = approver,
            model = provider.model,
            maxTokens = provider.maxTokens,
            temperature = provider.temperature,
        )
    }

    suspend fun shutdown() {
        mcp.shutdown()
        pty?.close()
        pty = null
    }
}
