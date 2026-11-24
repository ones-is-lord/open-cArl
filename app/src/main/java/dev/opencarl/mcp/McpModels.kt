package dev.opencarl.mcp

import dev.opencarl.data.McpTransportKind
import kotlinx.serialization.json.JsonObject

class McpException(message: String) : Exception(message)

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

enum class McpStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class McpServerState(
    val id: String,
    val name: String,
    val status: McpStatus = McpStatus.DISCONNECTED,
    val toolCount: Int = 0,
    val message: String = "",
)

data class McpPreset(
    val id: String,
    val name: String,
    val description: String,
    val transport: McpTransportKind,
    val url: String = "",
    val command: String = "",
    val args: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val requires: String = "",
)

object McpCatalog {

    val presets: List<McpPreset> = listOf(
        McpPreset(
            id = "github",
            name = "GitHub",
            description = "Repos, issues, pull requests and code search via GitHub's official MCP server.",
            transport = McpTransportKind.HTTP,
            url = "https://api.githubcopilot.com/mcp/",
            headers = mapOf("Authorization" to "Bearer {{GITHUB_TOKEN}}"),
            requires = "GitHub token (Settings > Integrations)",
        ),
        McpPreset(
            id = "playwright",
            name = "Playwright",
            description = "Drive a real Chromium browser: navigate, click, type, snapshot, screenshot.",
            transport = McpTransportKind.STDIO,
            command = "npx",
            args = listOf("-y", "@playwright/mcp@latest"),
            requires = "Node.js in the Linux userland",
        ),
        McpPreset(
            id = "deepwiki",
            name = "DeepWiki",
            description = "Ask questions about any public GitHub repository's codebase.",
            transport = McpTransportKind.HTTP,
            url = "https://mcp.deepwiki.com/mcp",
        ),
        McpPreset(
            id = "context7",
            name = "Context7",
            description = "Up-to-date library documentation and code examples.",
            transport = McpTransportKind.HTTP,
            url = "https://mcp.context7.com/mcp",
        ),
        McpPreset(
            id = "sequential-thinking",
            name = "Sequential Thinking",
            description = "Structured step-by-step reasoning scratchpad.",
            transport = McpTransportKind.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-sequential-thinking"),
            requires = "Node.js in the Linux userland",
        ),
        McpPreset(
            id = "filesystem",
            name = "Filesystem",
            description = "Read and write files inside the Linux userland.",
            transport = McpTransportKind.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-filesystem", "/root"),
            requires = "Node.js in the Linux userland",
        ),
        McpPreset(
            id = "memory",
            name = "Memory (knowledge graph)",
            description = "A graph-based memory server for entities and relations.",
            transport = McpTransportKind.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-memory"),
            requires = "Node.js in the Linux userland",
        ),
        McpPreset(
            id = "fetch",
            name = "Fetch",
            description = "Fetch web pages and convert them to markdown.",
            transport = McpTransportKind.STDIO,
            command = "uvx",
            args = listOf("mcp-server-fetch"),
            requires = "Python/uv in the Linux userland",
        ),
        McpPreset(
            id = "tavily",
            name = "Tavily Search",
            description = "Web search and extraction tuned for agents.",
            transport = McpTransportKind.HTTP,
            url = "https://mcp.tavily.com/mcp/?tavilyApiKey={{TAVILY_API_KEY}}",
            requires = "Tavily API key (Settings > Integrations)",
        ),
        McpPreset(
            id = "notion",
            name = "Notion",
            description = "Search and edit Notion pages and databases.",
            transport = McpTransportKind.HTTP,
            url = "https://mcp.notion.com/mcp",
            requires = "Notion OAuth/token",
        ),
    )

    fun byId(id: String): McpPreset? = presets.firstOrNull { it.id == id }
}
