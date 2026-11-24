package dev.opencarl.app

import dev.opencarl.ai.ToolCall
import kotlinx.coroutines.CompletableDeferred

enum class ToolStatus { RUNNING, OK, DENIED, FAILED }

sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem

    data class Assistant(override val id: Long, val text: String) : ChatItem

    data class Tool(
        override val id: Long,
        val callId: String,
        val name: String,
        val arguments: String,
        val status: ToolStatus = ToolStatus.RUNNING,
        val output: String = "",
    ) : ChatItem

    data class Error(override val id: Long, val message: String) : ChatItem
}

data class ApprovalRequest(
    val call: ToolCall,
    val description: String,
    val decision: CompletableDeferred<Boolean>,
)
