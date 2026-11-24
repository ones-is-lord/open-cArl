# open cArl

A standalone Android app that puts a real Linux terminal and an AI agent on the
same screen. The terminal is a PTY (not a fake command runner), the agent runs
locally in the app process, and it can drive that same shell.

- **Real terminal** — `xterm.js` in a WebView backed by a native `forkpty` shell.
  vim, htop, opencode and anything else that speaks VT100 work.
- **Agent in the terminal** — BYOK (bring your own key) for OpenAI-compatible
  endpoints, Anthropic and Gemini, plus on-device servers (llama.cpp, Ollama,
  LM Studio). The agent runs shell commands, reads and writes files, fetches
  URLs, uses skills, remembers things and calls MCP tools.
- **Skills** — markdown files with frontmatter. The index goes into the system
  prompt; the full text is loaded on demand through `use_skill`.
- **MCP** — HTTP, SSE and stdio transports, with a preset catalog (GitHub,
  Tavily, filesystem, fetch…).
- **Sequential memory** — an append-only log the agent writes to with `remember`
  and that is digested into the system prompt, newest entries first.
- **Linux userland** — run an Alpine (or any) rootfs under proot, or fall back to
  Android's own toybox shell.
- **Appearance** — 15 bundled open-licensed fonts plus 5 system families,
  assignable per surface; a 32-color palette with assignable roles (accent,
  highlight, text, banner, links, outline, terminal background, cursor); a thin
  outline whose width follows a slider; and a static header that stays put.

## Build

Requires JDK 17 and an Android SDK with platform 35 + NDK 27.

```bash
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # unit tests for memory, SSE parsing, palette
```

`local.properties` must point at your SDK (`sdk.dir=...`).

## First run

1. Open the drawer → **Providers**, pick a provider, paste your API key, set the
   model. Keys are stored in `EncryptedSharedPreferences`.
2. **Terminal** gives you a shell immediately (device backend).
3. Tap the chat bubble in the header to talk to the agent.

## Linux userland (optional)

The app does not ship proot or a rootfs. In **Runtime**:

1. Select a static aarch64 `proot` binary.
2. Select a rootfs tarball (`.tar.gz`, `.tar.xz` or `.tar`), for example
   `alpine-minirootfs-3.20.3-aarch64.tar.gz`.
3. Switch the backend to *proot Linux userland* and restart the shell.

Both are extracted into app-private storage; nothing needs root.

## Layout

```
app/src/main/java/dev/opencarl/
  ai/          LLM clients (OpenAI-compatible, Anthropic, Gemini), agent loop,
               tools, system prompt, JSON helpers
  app/         AppContainer (process-wide state), ViewModel, chat models
  appearance/  32-color palette, font library, appearance settings
  data/        settings repository, encrypted secrets, data models
  mcp/         JSON-RPC client, HTTP/SSE/stdio transports, manager, catalog
  memory/      sequential memory store
  models/      HuggingFace GGUF search and download
  runtime/     shell backends (device / proot), proot + rootfs installer
  skills/      skill store (bundled + imported markdown)
  terminal/    PTY session, xterm.js WebView, shell launch descriptor
  ui/          Compose screens
app/src/main/cpp/pty.c   native forkpty wrapper
app/src/main/assets/     xterm.js, fonts, skills
```

## Security notes

- The agent's shell commands run with the app's own UID inside the app sandbox.
- Approval policies: `always ask`, `smart` (ask only for destructive commands)
  and `auto`. The default is `smart`.
- MCP secrets are referenced as `{{GITHUB_TOKEN}}`-style placeholders and filled
  from encrypted storage at connect time.
- Cleartext HTTP is allowed so local model servers and LAN MCP servers work.

## License

Fonts keep their own licenses, see `app/src/main/assets/fonts/NOTICE.md`.
