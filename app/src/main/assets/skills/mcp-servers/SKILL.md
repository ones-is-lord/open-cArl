---
name: MCP servers
description: Add, configure and debug Model Context Protocol servers over HTTP, SSE or stdio.
---

# MCP servers

open cArl speaks MCP 2024-11-05. Tools from every connected server are merged into the
agent's tool list under `mcp__<server>__<tool>`.

## Transports
- **HTTP** — streamable HTTP endpoint, e.g. `https://mcp.deepwiki.com/mcp`.
- **SSE** — legacy event-stream endpoint that returns a POST URL.
- **stdio** — a local process speaking newline-delimited JSON-RPC. Requires the command to
  exist in the active Linux userland (`npx`, `uvx`, `python3`).

## Headers and secrets
Headers and env values support `${NAME}` placeholders resolved from the app's encrypted
store. Built-in names: `${GITHUB_TOKEN}`, `${HF_TOKEN}`, `${TAVILY_API_KEY}`.

## Debugging
1. Open Settings > MCP, tap the server, then Test. The handshake output shows the failing
   step (`initialize`, `tools/list`, or auth).
2. A 401 almost always means a missing or expired token header.
3. stdio servers that exit immediately usually have a missing binary: run the command in
   the terminal first and read the error.
