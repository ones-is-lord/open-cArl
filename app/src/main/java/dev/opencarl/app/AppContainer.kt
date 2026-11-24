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
