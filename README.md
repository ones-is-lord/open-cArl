# open cArl

A local-first AI assistant for Android that lives in a real terminal. No WebView,
no JavaScript terminal, no cloud requirement: a native VT emulator over a PTY,
an agent that can drive the shell, the phone's UI, its packages and its storage.

## What it actually does

**Terminal.** A VT100/xterm parser and screen buffer written in Kotlin
(`terminal/VtParser.kt`, `TerminalBuffer.kt`) rendered with Compose text. Handles
C0 controls, CSI (cursor, erase, insert/delete, scroll regions, SGR incl. 256 and
24-bit colour), OSC titles, alternate screen and bracketed paste. 12 unit tests
cover the sequences that break naive implementations.

**Agent.** BYOK for OpenAI-compatible, Anthropic and Gemini endpoints, plus a
local llama.cpp server. Tools: shell, files, fetch, skills, memory, and the
device tools below. Approval policies `always ask` / `smart` / `auto`, plus a
per-tool allow/deny matrix.

**Device control.** An accessibility service lets the agent read the screen, tap
by text, type, swipe and press back/home/recents. A foreground service with a
wake lock keeps long runs alive with the screen off.

**Privilege.** One runner, three levels: app uid, shell uid through Shizuku
(`bindUserService` + a Messenger-based shell service), or root via `su`. Every
command lands in an append-only audit log with exit code, duration and output.

**Maintenance.** Curated scripts (bootstrap, dev tools, animation speed, cache
trim, Doze whitelist, debloat, ART recompile, media repair, VM tuning) with
dry-run previews, risk labels and documented undo. Storage triage finds large
files, empty files, duplicates and broken links and can delete only the safe
ones. Package manager: inventory with sizes, disable/enable, uninstall for the
user, cache clear, force stop, APK install via PackageInstaller.

**Root and recovery.** Detects Magisk/KernelSU, lists modules, installs module
zips through `magisk --install-module`, reports the boot slot, and generates a
recovery plan (unlock, image per codename, `fastboot boot` first). It never
flashes anything itself — that is deliberate.

**Local models.** Device profiler reads CPU clocks, cores, RAM, OpenGL ES,
Vulkan and an NPU estimate, then ranks a curated GGUF catalogue by what will
actually run, with a throughput estimate. Models download in-app; the serve
command (`llama-server ... --jinja`) is generated for your installed file and
runs in your own shell, so proot and packages behave normally.

**Research and training data.** `deep_research` searches (Tavily with a key,
DuckDuckGo HTML without), reads the top pages and returns a citable corpus.
Finished conversations export to ChatML JSONL with a llama.cpp/axolotl recipe.

**D.G.A.F.** A no-oversight mode behind two separate warnings, each requiring
the phrase *"I agree and understand the risk of using this mode"*. The header
shows a red bar while it is on and revoking is one tap.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

JDK 17, Android SDK 35, NDK 27 (`local.properties` → `sdk.dir=...`).

## Setup order

1. **Providers** — paste a key, or point at `http://127.0.0.1:8080/v1` for a local server.
2. **Runtime** — device shell by default; select a proot binary and rootfs for a Linux userland.
3. **System → Power** — grant Shizuku, enable background mode, turn on launch updates.
4. **System → Device** — see what fits and download a model.
5. Accessibility is optional and only used when you ask the agent to touch the UI.

## Layout

```
app/src/main/java/dev/opencarl/
  ai/          clients, agent loop, builtin + system tools, prompt, JSON helpers
  appearance/  32-colour palette, font library
  app/         AppContainer, ViewModel, chat models
  data/        settings, encrypted secrets, models
  device/      profiler, model catalogue, recommender
  finetune/    dataset exporter and training recipe
  local/       GGUF downloader and serve command builder
  mcp/         JSON-RPC client, HTTP/SSE/stdio transports
  memory/      sequential memory store
  privilege/   Shizuku + root runner, audit log
  research/    search and page extraction
  runtime/     shell backends, proot installer
  scripts/     curated scripts and runner
  service/     accessibility, foreground service, Shizuku shell service
  skills/      skill store
  system/      storage triage, package manager, root tools
  terminal/    VT parser, buffer, emulator, PTY
  ui/          Compose screens incl. native terminal surface
app/src/main/cpp/pty.c   forkpty wrapper
```

## Personas and voice

Eight built-in voices (Carl is the default: dry, sarcastic, extremely direct) plus
custom ones you write yourself. A persona changes tone and method, never the
rules. Replies can be read aloud with Android TTS; markdown, code fences and URLs
are stripped first. There is **no microphone path anywhere in this app** — input is
text, always.

## Session snapshots

Every snapshot stores the workspace as a zip, the visible terminal and the
conversation. Diff compares CRC32 per file against the live workspace; restore
replaces the workspace. When `snapshotBeforeRisky` is on (default), a snapshot is
taken automatically before any risky tool call, so a bad `rm` is one tap from
undo.

## The command surface

34 tools, all real:

| Group | Tools |
|---|---|
| Shell and files | `bash`, `read_file`, `write_file`, `list_dir`, `files` (list/read/write/append/mkdir/delete/move/copy/stat/search), `script` (write/run/list/delete your own scripts), `code` (run python3/node/sh snippets), `shell_send` (type into the live PTY) |
| Packages | `packages` (search/install/remove/upgrade/info, auto-detects apk, apt or pkg) |
| Network | `net` (ping, dns, http probe, TCP port scan, interfaces, public IP), `wifi` (scan, wardriving CSV, band summary) |
| Device | `device_info`, `screen` (read/tap/type/swipe/back/home/recents), `diagnostics` (cpu, memory, storage, battery, thermal, top, logcat, dumpsys), `apps`, `storage`, `root`, `privileged_shell`, `run_script`, `clipboard`, `notify` |
| Knowledge | `deep_research`, `fetch_url`, `use_skill`, `list_skills`, `remember`, `recall`, `profile` (habits, needs, wants, plans), `plan` (persistent todos), `study` (SM-2 flashcards), `think` |
| Automation | `schedule` (prompt or headless command, one-off or repeating), `snapshot` (create/list/diff/restore), `persona` (switch your own tone) |
| MCP | `mcp__<server>__<tool>` for every connected server |

## Testing

```bash
./gradlew :app:testDebugUnitTest   # 34 tests
tools/device-smoke.sh              # installs, loads the terminal, fails on crash
```

`TerminalStressTest` pushes 4 MB of coloured output through the parser (about
9.6 MB/s on the JVM), churns the alternate screen 500 times, resizes 300 times,
and hammers the buffer from four writer threads plus a reader.

## Honest limits

- Still not run on a physical device: 34 unit tests pass and the parser survives
  4 MB bursts, but the PTY, the Compose renderer and the accessibility paths need
  a real phone. `tools/device-smoke.sh` is the script to prove it; it has not
  been run yet because this sandbox has no device.
- Scheduling fires prompts only when the app is open (a notification tells you),
  because a headless agent needs the process alive. Shell commands do run
  headless.
- Wardriving logs what Android lets a foreground app see; scans are throttled and
  need the location permission.
- Wifi, clipboard and notification tools are blocked until you grant the matching
  permissions, and they say so instead of failing silently.
- No on-device training. Datasets and recipes are generated; training happens on
  a workstation or in the userland.
- proot and rootfs are user-supplied (licensing and size).
- Shizuku and accessibility must be enabled by the user; without them the agent
  falls back to app-uid tools and tells you so.
- Debug build only: no signing config, no R8 rules, no instrumented tests.

## Layout additions

```
  ai/ExtendedTools.kt   files, code, packages, net, wifi, diagnostics, plan,
                        study, schedule, snapshot, clipboard, notify, persona,
                        profile, plus the date parser they share
  net/                  ping/dns/probe/port scan, wifi scan and CSV logging
  persona/              built-in voices and the custom persona model
  plan/ study/          persistent tasks, SM-2 flashcards
  schedule/             AlarmManager tasks and their receiver
  snapshot/             zip snapshots, diff and restore
  voice/Speaker.kt      text to speech, output only
tools/device-smoke.sh   adb smoke test for a real phone
```

## Licence

Bundled fonts keep their own licences: `app/src/main/assets/fonts/NOTICE.md`.
