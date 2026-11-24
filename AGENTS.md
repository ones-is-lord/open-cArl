# Repository notes

## Build

```bash
export JAVA_HOME=/workspace/.toolchain/jdk17
export PATH=$JAVA_HOME/bin:/workspace/.toolchain/gradle-8.11.1/bin:$PATH
gradle :app:assembleDebug          # ~90s cold, 4s warm
gradle :app:testDebugUnitTest
```

`local.properties` needs `sdk.dir=/workspace/.toolchain/sdk`. The gradle binary in
`.toolchain` may need `chmod +x`.

## Conventions

- Package root `dev.opencarl`; Kotlin, Compose (Material 3), no XML layouts.
- State lives in `AppContainer` (Application-scoped) — the PTY, settings, skills,
  memory and MCP connections must outlive any screen.
- JSON helpers (`str`, `obj`, `arr`, `long`, `boolean`, `parseJsonObject`) live in
  `dev.opencarl.ai.JsonSupport` as `internal` extensions; other packages must
  import them explicitly.
- `TerminalWebView` is kept composed for the whole session; settings screens are
  drawn on top so scrollback survives navigation.
- Compose screens: `ScreenScaffold` + a scrolling `Column`; avoid nesting
  `LazyColumn` inside `Column` (use `verticalScroll`).

## Architecture notes

- Terminal is native: `terminal/VtParser` -> `TerminalBuffer` -> `TerminalEmulator`
  (thread-safe) -> `ui/terminal/TerminalSurface` (Compose Canvas). There is no
  WebView and no JS; do not reintroduce one.
- Privilege: `privilege/PrivilegeManager` routes to app uid, Shizuku
  (`bindUserService` + `service/ShellUserService` over Messenger) or `su`, and
  writes every command to `AuditLog` (JSONL in filesDir/audit).
- `ai/SystemTools` is the bridge between the agent and the device: screen,
  privileged_shell, run_script, storage, apps, root, deep_research, think.
- D.G.A.F. bypasses the approval gate only when `dgafEnabled && dgafAcceptedAt > 0`;
  both are set exclusively by the two-consent flow in `ui/DgafScreen`.

## Gotchas

- In Kotlin string literals `\s` is not a valid escape — use `\\s` or raw strings.
- `PtySession` sends SIGHUP via `android.system.Os.kill`; `android.os.Process`
  has no `SIGNAL_HUP`.
- The `terminal` tool (agent shell) call must include the `command` argument.
- `StorageStatsManager.queryStatsForUid` needs a `UUID` volume id from
  `StorageManager.getUuidForPath`; there is no single-argument overload.
- `Shizuku.newProcess` is private in 13.1.5 — use a bound user service.
- Heredoc-written Kotlin occasionally picks up a stray leading character; the
  compiler catches it, `grep '^[a-z]\{1,3\}[A-Z(]'` finds it faster.
