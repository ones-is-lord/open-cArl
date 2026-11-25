# Architecture

```
app/src/main/java/dev/opencarl/
├── MainActivity.kt          single activity, splash then window
├── app/                     AppContainer (wiring), OpenCarlViewModel
├── runtime/                 PTY session, RuntimeBackend, shell profiles
├── terminal/                VT parser, screen buffer, colour model
├── ui/                      Compose screens
│   ├── neon/                CRT frame, title bar, splash, art slots
│   ├── terminal/            terminal surface + keyboard accessory row
│   └── components/          header, drawers, dialogs
├── ai/                      agent loop, tool registry, tool groups
├── appearance/              fonts, colour roles, neon maths, settings
├── memory/                  memory store, knowledge packs
├── schedule/                scheduled tasks, device triggers
├── privilege/               root/shizuku/device shells, audit log
├── skills/                  skill loader
├── system/                  apps, packages, storage, diagnostics, emulator
└── persona/                 built-in and user personas
```

## Layers

**Runtime.** A real PTY via JNI, two backends: an on-device shell (`sh`) and a
proot Debian rootfs. The agent's `bash` tool talks to the same session the
terminal shows, so the user sees exactly what the agent ran.

**Terminal.** A VT100/xterm parser feeding a screen buffer, rendered by a native
Compose canvas. Not a WebView, not a text field. It survives 4 MB bursts without
dropping bytes (see the stress tests).

**Agent.** `Agent.run` streams deltas, assembles tool calls, asks the approver,
executes, feeds results back, and stops on a step budget. Tools live in a
`ToolRegistry`; groups (`BuiltinTools`, `SystemTools`, `ExtendedTools`) install
themselves into it, and MCP servers add theirs at runtime.

**State.** Settings, memory, schedules, triggers and snapshots are JSON files
under `filesDir`. Nothing leaves the device unless a tool explicitly sends it.

## Threading

The PTY reader runs on its own thread and posts into a `StateFlow`. Compose
collects it. Agent runs are coroutines scoped to the ViewModel; cancelling the
view model interrupts the PTY.

## Deliberate limits

- Background work is alarm-based, not a foreground service: Android throttles
  anything else, so triggers poll every 15 minutes.
- Scheduling fires prompts only while the app is open; commands run headless.
- The terminal is a renderer, not an emulator: no sixel, no true-colour OSC 4
  palettes beyond the configured scheme.
