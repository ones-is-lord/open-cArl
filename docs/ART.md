# Art pipeline

Three images drive the look. All three are optional: the app falls back to
generated art, so a missing file never produces a blank screen.

| Source | Becomes | Used by |
|---|---|---|
| splash art (most detailed) | `drawable-nodpi/carl_splash.png` | launch screen, letterboxed on black, 1.2 s |
| icon (badge) | `mipmap-*dpi/ic_launcher_foreground.png` | adaptive launcher icon, art in the 66% safe zone |
| icon (badge centre) | `drawable-nodpi/carl_avatar.png` | circular portrait in the title bar |
| logo (brush wordmark) | `drawable-nodpi/carl_logo.png` | title bar and drawer, backdrop keyed to alpha |

```bash
tools/import-art.sh splash.png icon.png logo.png
```

The importer caps the splash at 1800 px on the long edge, trims the logo, keys
its near-black backdrop to transparency, crops the avatar from the badge centre,
and writes five icon densities. Then it rebuilds.

## Runtime lookup

`carl_splash`, `carl_logo` and `carl_avatar` are resolved by name with
`Resources.getIdentifier`, so dropping a file in is the whole integration.

## Fallbacks

- splash: the Oxanium wordmark and tagline on black
- logo: the same wordmark, drawn with an animated rainbow gradient
- avatar: a screenprint-style disc with a dark bust and a magenta misregistration
- icon: gradient ring, scanlines, Oxanium monogram, generated at build time

## Typography

Oxanium is bundled (variable, wght 200-800) and pinned to 800 for display and
700 for the banner through `res/font/oxanium_display.xml` and `oxanium_bold.xml`.
Terminal text stays monospace and never receives the neon treatment.

## Known approximation

The reference CSS uses `skewX(-4deg)`. Compose has no skew on `graphicsLayer`, so
the slant is the platform's synthetic oblique, switchable in Appearance.
