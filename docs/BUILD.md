# Build and release

## Requirements

- JDK 17
- Android SDK with NDK and CMake 3.22.1 (the PTY and proot shims are native)
- Gradle 8.11 (wrapper included)

## Build

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # 39 unit tests
./gradlew :app:assembleRelease        # signed release APK
```

## Signing

Precedence:

1. `keystore.properties` at the repo root (gitignored)
2. `CARL_KEYSTORE`, `CARL_KEYSTORE_PASSWORD`, `CARL_KEY_ALIAS`,
   `CARL_KEY_PASSWORD` environment variables (CI)
3. AGP's debug config, which generates `~/.android/debug.keystore`

Without 1 or 2 the APK installs but is not Play-ready and cannot upgrade over a
build signed with a different key.

## Publishing

```bash
GH_TOKEN=<token with contents:write> ./tools/release.sh 0.2.0
```

Builds, tests, checksums into `dist/`, and creates a GitHub release with
`dist/RELEASE_NOTES.md`.

## CI

`.github/workflows/build.yml` runs the tests, builds both APKs on every push and
pull request, uploads them as artifacts, and attaches the release APK to any
`v*` tag. Add the four `CARL_*` secrets to sign properly in CI.

## Verified state

- `BUILD SUCCESSFUL`, 39/39 tests
- release APK 15.3 MB, APK Signature Scheme v2 block present
- Oxanium confirmed inside `resources.arsc`
- **not yet run on a physical device**
