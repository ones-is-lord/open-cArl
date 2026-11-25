#!/usr/bin/env bash
# Builds, checksums and publishes an open cArl release.
# Needs a GitHub token with contents:write, either exported as GH_TOKEN or
# already configured for `gh auth`.
set -euo pipefail

VERSION=${1:-$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts | head -1)}
TAG=${2:-v$VERSION}
REPO=${REPO:-ones-is-lord/open-cArl}

echo "== building $VERSION"
./gradlew :app:assembleRelease :app:testDebugUnitTest --console=plain

APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "no APK at $APK"; exit 1; }

mkdir -p dist
cp "$APK" "dist/open-carl-$VERSION.apk"
( cd dist && sha256sum "open-carl-$VERSION.apk" > SHA256SUMS )

if [ -n "${GH_TOKEN:-}" ]; then export GH_TOKEN; fi
if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not authenticated; APK is in dist/ but nothing was published"
  exit 0
fi

echo "== publishing $TAG to $REPO"
gh release create "$TAG" "dist/open-carl-$VERSION.apk" \
  --repo "$REPO" \
  --title "open cArl $VERSION" \
  --notes-file dist/RELEASE_NOTES.md \
  --latest
