# open cArl — TODO

Status: `x` done, ` ` not started, `~` partial. Ordered by what blocks a real
daily-driver release, not by what is fun.

## Blocks first real use

- [ ] **Run it on the phone.** Nothing in this repo has executed on hardware.
      The parser, the PTY, the renderer and the accessibility paths are all
      unproven. `tools/device-smoke.sh` is the script; every result needs to be
      recorded before anything else is called done.
- [ ] **Import the three art files** (splash, icon, logo).
      `tools/import-art.sh splash.png icon.png logo.png`. Slots are wired and
      fall back gracefully, so this is cosmetic but it is the whole point of the
      art round.
- [x] **Push the repo and cut the release.** Done: `main` is on GitHub and
      v0.2.0 carries the APK —
      https://github.com/ones-is-lord/open-cArl/releases/tag/v0.2.0
- [ ] **Decide the signing key.** The APK currently falls back to the Android
      debug key. Fine for sideloading, wrong for anything else, and it cannot be
      upgraded over later without uninstalling.
- [ ] **Verify proot bootstrap end to end** on a clean device: download, extract,
      first `apt` run, and that `libproot.so` extraction under W^X actually
      works on Android 14 and 15.

## Should exist before 1.0

- [ ] Foreground service for long agent runs, so a run survives the app being
      backgrounded. Right now cancelling the view model interrupts the PTY.
- [ ] Real notification actions: approve, deny and open-from-notification
      instead of only deep-linking to the app.
- [ ] Incremental knowledge-pack re-import (hash per file) instead of re-adding
      every chunk on each import.
- [ ] Trigger de-duplication: a rule that stays true re-fires every poll. Needs a
      cooldown or edge detection per rule.
- [ ] Export and import of settings, memory and knowledge packs, so a phone swap
      does not lose the setup.
- [ ] Accessibility audit: TalkBack labels on the terminal surface, the window
      glyphs and the drawer; check that the neon chrome does not break contrast
      in high-contrast mode.
- [ ] Screen-reader-friendly rendering of ANSI colours (a "plain output" mode).

## Nice to have

- [ ] True `skewX` slant for the wordmark. Compose has no skew on
      `graphicsLayer`; would need a text-to-path pass or a shader.
- [ ] Monochrome silhouette for themed launcher icons. Deliberately omitted:
      tinting the full-colour badge produces a grey blob.
- [ ] Sixel and OSC 4 palette support in the terminal renderer.
- [ ] Terminal split view (two sessions side by side) on tablets.
- [ ] Local model runner integration beyond the current BYOK path.
- [ ] Play Store packaging: bundle format, data-safety form, privacy policy.
- [ ] Reproducible builds and a signed release hash published with each tag.
- [ ] Real device matrix in CI (emulator at minimum) for the smoke script.

## Known rough edges

- [ ] `spawn` is depth-capped at one level; a genuinely deep task needs manual
      chaining.
- [ ] Triggers poll every 15 minutes, so "battery below 20%" can fire late. That
      is an Android platform limit, not a bug, but the UI should say so where the
      rule is created.
- [ ] Banner and tagline auto-shrink to fit; on a narrow phone with a long
      tagline the type gets small instead of wrapping to a second line.
- [ ] The grunge texture is procedural, not the source bitmap.
- [ ] No microphone input anywhere, by request. TTS reply playback only.
