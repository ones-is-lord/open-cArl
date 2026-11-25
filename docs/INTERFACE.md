# Interface

## Shell

```
┌──────────────────────────────────────────────┐
│ avatar · OPEN cARL wordmark · tagline · glyphs│  header, tap avatar = drawer
│ wifi bars · link · battery · storage · connect│  status strip, tap = network picker
├──────────────────────────────────────────────┤
│ ┌──────────────┐ ┌──────────────┐            │
│ │    AGENT     │ │   TERMINAL   │            │  two identical CLI boxes
│ │  chat pane   │ │   shell      │            │
│ └──────────────┘ └──────────────┘            │
├──────────────────────────────────────────────┤
│ Terminal   Chat   Split   Guide    More      │  Material 3 NavigationBar
└──────────────────────────────────────────────┘
```

## Panes

`PaneMode` is `TERMINAL`, `CHAT` or `SPLIT`, held in the app shell so both the
bottom bar and the header glyph can change it.

- `SPLIT` renders `SplitPanes`: two `CliBox` instances, each with the same neon
  frame, corner radius and monospace label. Side by side at 600 dp and wider,
  stacked below it (`BoxWithConstraints`).
- Both panes share **one PTY session and one chat history**. They are two windows
  onto the same agent, not independent sessions.

## Onboarding

Shown until `AppSettings.onboarded` is true. Three steps:

1. What the app is: a real shell plus an agent that uses it.
2. This device: model, Android version, cores, ABI, total and free RAM, free
   storage, and a tier (`minimal` through `workstation`).
3. Model choice: every entry in `ModelCatalog` with a fit verdict. The
   recommendation is the largest model whose `minRamMb` is within 55% of total
   RAM and whose download is at most half the free storage. The pick is stored in
   `chosenModelId`.

`ModelCatalog` is plain Kotlin, so the ranking is unit-testable without a device.

## Status strip

Polls every 5 seconds: transport (wifi/lte/eth/vpn), signal bars from
`NetworkCapabilities.signalStrength`, `NET_CAPABILITY_VALIDATED`, downstream
bandwidth, battery level and charging, free storage. Tapping it opens
`Settings.ACTION_WIFI_SETTINGS`, which is the closest thing Android offers to a
programmatic connect without location permission and user consent.

## Insets

`targetSdk` is 35, so the system draws the app edge to edge. The root applies
`windowInsetsPadding(WindowInsets.safeDrawing)` and the pane container applies
`imePadding()`. Without both, the header sits under the status bar and the input
line under the taskbar or keyboard.

## Failure surfaces

- `CrashLog` installs a default uncaught handler that writes the trace to
  `filesDir/logs/` and the next launch shows `CrashScreen` before anything else
  composes.
- `DiagnosticsScreen` (drawer) prints device, ABI, native PTY load state, runtime,
  rootfs, shell state, storage and the last crash, with a copy button.
