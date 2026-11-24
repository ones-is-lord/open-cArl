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

## Gotchas

- In Kotlin string literals `\s` is not a valid escape — use `\\s` or raw strings.
- `PtySession` sends SIGHUP via `android.system.Os.kill`; `android.os.Process`
  has no `SIGNAL_HUP`.
- The `terminal` tool (agent shell) call must include the `command` argument.
