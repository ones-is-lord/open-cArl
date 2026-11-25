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

37 tools, all real:

| Group | Tools |
|---|---|
| Shell and files | `bash`, `read_file`, `write_file`, `list_dir`, `files` (list/read/write/append/mkdir/delete/move/copy/stat/search), `script` (write/run/list/delete your own scripts), `code` (run python3/node/sh snippets), `shell_send` (type into the live PTY) |
| Packages | `packages` (search/install/remove/upgrade/info, auto-detects apk, apt or pkg) |
| Network | `net` (ping, dns, http probe, TCP port scan, interfaces, public IP), `wifi` (scan, wardriving CSV, band summary) |
| Device | `device_info`, `screen` (read/tap/type/swipe/back/home/recents), `diagnostics` (cpu, memory, storage, battery, thermal, top, logcat, dumpsys), `apps`, `storage`, `root`, `privileged_shell`, `run_script`, `clipboard`, `notify` |
| Knowledge | `deep_research`, `fetch_url`, `use_skill`, `list_skills`, `remember`, `recall`, `profile` (habits, needs, wants, plans), `plan` (persistent todos), `study` (SM-2 flashcards), `think` |
| Delegation | `spawn` (sub-agent with a fresh context, depth-capped at one level) |
| Automation | `triggers` (battery, charging, thermal, wifi, screen, storage), `schedule`, `snapshot`, `persona` |
| Knowledge | `kb` (import a folder of notes or code into memory with provenance, search it, remove a pack) |
| MCP | `mcp__<server>__<tool>` for every connected server |

## Building a release

```bash
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Signing reads `keystore.properties` (gitignored) or the `CARL_KEYSTORE*`
environment variables. With neither it falls back to the Android debug key, so
the APK installs but is not Play-ready and cannot be upgraded over a build signed
with a different key. `.github/workflows/build.yml` runs the tests, builds both
APKs on every push, and attaches the release APK to any `v*` tag.

## Testing

```bash
./gradlew :app:testDebugUnitTest   # 34 tests
tools/device-smoke.sh              # installs, loads the terminal, fails on crash
```

`TerminalStressTest` pushes 4 MB of coloured output through the parser (about
9.6 MB/s on the JVM), churns the alternate screen 500 times, resizes 300 times,
and hammers the buffer from four writer threads plus a reader.

## Install

```bash
curl -LO https://github.com/ones-is-lord/open-cArl/releases/latest/download/open-carl-0.2.0.apk
adb install -r open-carl-0.2.0.apk
```

Android 8.0 (API 26) or newer. Signed with the debug key, so it installs but is
not Play-ready — see [docs/BUILD.md](docs/BUILD.md).

## The window

Split CLI panes, onboarding, a cheat sheet and a live status strip. See
[docs/INTERFACE.md](docs/INTERFACE.md) for the layout and the reasoning.


The main screen is one CRT window, built from the reference art:

- a rounded gradient border (magenta → rose → violet → cyan) with bloom and a
  deterministic distressed-print texture, drawn as an overlay so the terminal
  keeps its own layout;
- a title bar with the Carl avatar, the OPEN CARL wordmark in yellow-to-orange
  neon, a magenta rule and the cyan tagline;
- the three window glyphs: minus collapses the agent panel, square opens
  appearance, X closes the panel or opens the drawer;
- a hairline gradient under the bar and a dim status line with runtime and
  provider.

The tagline is the persistent user banner: change the words, the case, the size
or hide it entirely in Appearance. The wordmark text is editable too.

### Swapping in your own art

Two drawables are picked up automatically when they exist, no code change:

| File | Used for |
|---|---|
| `app/src/main/res/drawable-nodpi/carl_splash.png` | full-screen launch art, letterboxed on black, 1.2 s |
| `app/src/main/res/drawable-nodpi/carl_logo.png` | brush wordmark in the title bar and drawer |
| `app/src/main/res/drawable-nodpi/carl_avatar.png` | circular portrait in the title bar |
| `app/src/main/res/mipmap-*dpi/ic_launcher_foreground.png` | adaptive launcher icon, art inside the 66% safe zone |

One command does all four, including density buckets and the circular avatar
crop:

```bash
tools/import-art.sh splash.png icon.png logo.png
```

Until they are present the app falls back to the wordmark on black and to a
screenprint-style placeholder avatar, so nothing looks broken.

## Neon typography

The display type comes from `open_carl_neon_type.css`, rebuilt natively in
Compose (`appearance/NeonSpec.kt` for the maths, `appearance/NeonType.kt` for the
drawing). Oxanium is bundled, not fetched: `res/font/oxanium_var.ttf` is the
variable font (wght 200-800, OFL, licence in `assets/fonts/OFL.txt`) pinned to
800 for the wordmark and 700 for the banner by the `oxanium_display` and
`oxanium_bold` font families. Nothing here needs a network at first launch.

| CSS | Compose |
|---|---|
| `font-family: Oxanium; font-weight: 800` | `FontLibrary.oxanium` (variable axis pinned to 800) |
| `text-transform: uppercase` | `NeonSpec.display()` |
| `letter-spacing: -0.055em` | `letterSpacing = (-0.045).em` on the wordmark |
| `font-style: italic` / `skewX(-4deg)` | `FontStyle.Italic`; Compose's layer API has no skew, so the platform oblique is used and can be switched off |
| `background: linear-gradient(100deg, 9 stops)` + `background-size: 220%` | `Brush.linearGradient` over a 2.2x span, shifted by an animated phase |
| `-webkit-text-stroke: 1.5px` + `paint-order: stroke fill` | a `Stroke`-styled text layer drawn under the fill |
| three `drop-shadow` bloom layers | pink, cyan and white text layers with `Modifier.blur`, scaled 1.03x for a rim on pre-API-31 devices |
| `animation: oc-rainbow-drift 12s` | phase driven at 20 Hz from `NeonSpec.phaseAt` |
| `prefers-reduced-motion` | disabled when `ANIMATOR_DURATION_SCALE == 0` or the in-app toggle is off |
| `.oc-banner-text` (cyan, 700, uppercase) | `NeonBannerText` in the header, user text, size, case and visibility preserved |
| `.oc-terminal` (off-white mono) | terminal foreground `#E8F7FF`, no gradients, monospace untouched |
| prompt accent | `PS1` sets magenta path and cyan `$` for both device and proot shells |

Sizing: every size is in `sp`, so Android font scaling applies. The wordmark and
banner measure their text once and shrink to fit their width instead of clipping
or scrolling horizontally; the banner wraps to two lines and ellipsises beyond
that.

## Honest limits

- The neon typography has not been eyeballed on a device either: it compiles, the
  font is packaged, and the colour maths is unit tested, but glow strength and
  gradient speed want a real screen. The Appearance screen toggles exist partly
  so you can dial it down without a rebuild.
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
