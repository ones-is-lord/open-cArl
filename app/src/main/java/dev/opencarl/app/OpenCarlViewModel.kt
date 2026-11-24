package dev.opencarl.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opencarl.ai.AgentEvent
import dev.opencarl.ai.LlmMessage
import dev.opencarl.ai.ToolCall
import dev.opencarl.ai.parseArgs
import dev.opencarl.ai.str
import dev.opencarl.data.ApprovalPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OpenCarlViewModel(application: Application) : AndroidViewModel(application) {

    val container: AppContainer = (application as dev.opencarl.openCarlApp).container

    private val _chat = MutableStateFlow<List<ChatItem>>(emptyList())
    val chat: StateFlow<List<ChatItem>> = _chat.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval.asStateFlow()

    private val history = mutableListOf<LlmMessage>()
    private var job: Job? = null
    private var nextId = 1L

    init {
        viewModelScope.launch { container.memory.load() }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _busy.value) return
        _chat.update { it + ChatItem.User(nextId++, prompt) }
        _busy.value = true
        job = viewModelScope.launch {
            val agent = container.buildAgent { call, description -> requestApproval(call, description) }
            val assistantId = nextId++
            _chat.update { it + ChatItem.Assistant(assistantId, "") }
            try {
                agent.run(history, prompt).collect { event -> handle(event, assistantId) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _chat.update { it + ChatItem.Error(nextId++, t.message ?: t.javaClass.simpleName) }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun handle(event: AgentEvent, assistantId: Long) {
        when (event) {
            is AgentEvent.TextDelta -> _chat.update { items ->
                items.map { item ->
                    if (item is ChatItem.Assistant && item.id == assistantId) {
                        item.copy(text = item.text + event.text)
                    } else {
                        item
                    }
                }
            }
            is AgentEvent.ToolRequested -> _chat.update { items ->
                items + ChatItem.Tool(nextId++, event.call.id, event.call.name, event.call.arguments)
            }
            is AgentEvent.ToolFinished -> updateTool(event.call.id) {
                it.copy(status = if (it.output.startsWith("Error")) ToolStatus.FAILED else ToolStatus.OK, output = event.output)
            }
            is AgentEvent.ToolDenied -> updateTool(event.call.id) {
                it.copy(status = ToolStatus.DENIED, output = "Denied by the user.")
            }
            is AgentEvent.Error -> _chat.update { it + ChatItem.Error(nextId++, event.message) }
            AgentEvent.Finished -> Unit
        }
    }

    private fun updateTool(callId: String, block: (ChatItem.Tool) -> ChatItem.Tool) {
        _chat.update { items ->
            items.map { item ->
                if (item is ChatItem.Tool && item.callId == callId) block(item) else item
            }
        }
    }

    private suspend fun requestApproval(call: ToolCall, description: String): Boolean {
        val policy = container.settings.settings.value.approvalPolicy
        if (policy == ApprovalPolicy.AUTO) return true
        if (policy == ApprovalPolicy.SMART && !isRisky(call)) return true
        val deferred = CompletableDeferred<Boolean>()
        _approval.value = ApprovalRequest(call, description, deferred)
        return try {
            deferred.await()
        } finally {
            _approval.value = null
        }
    }

    private fun isRisky(call: ToolCall): Boolean {
        if (call.name.startsWith("mcp__")) return false
        if (call.name != "bash") return false
        val command = parseArgs(call.arguments).str("command").orEmpty()
        return RISKY.containsMatchIn(command)
    }

    fun resolveApproval(allow: Boolean) {
        _approval.value?.decision?.complete(allow)
    }

    fun stop() {
        job?.cancel()
        container.interrupt()
        _busy.value = false
    }

    fun newChat() {
        job?.cancel()
        history.clear()
        _chat.value = emptyList()
        _busy.value = false
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        val RISKY = Regex(
            "(?i)(rm\\s+-[a-z]*r[a-z]*f|rm\\s+-[a-z]*f[a-z]*r|mkfs|dd\\s+if=|" +
                "git\\s+reset\\s+--hard|git\\s+push\\s+[^\\n]*--force|git\\s+clean\\s+-[a-z]*f|" +
                "pm\\s+uninstall|pkg\\s+(un)?install|apk\\s+(del|add)|shutdown|reboot|" +
                "chmod\\s+-R\\s+777\\s+/|:>\\s*/|>\\s*/dev/sd)",
        )
    }
}
