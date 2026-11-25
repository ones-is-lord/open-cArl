#!/usr/bin/env bash
# Device smoke test for open cArl. Run with a phone attached over adb.
# It installs the debug APK, exercises the terminal under load, and fails if
# AndroidRuntime logs a crash.
set -u

APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
PKG=dev.opencarl
SHOTS=${SHOTS:-/tmp/carl-smoke}
mkdir -p "$SHOTS"

fail() { echo "FAIL: $*" >&2; exit 1; }

command -v adb >/dev/null || fail "adb not on PATH"
adb get-state >/dev/null 2>&1 || fail "no device or emulator connected"
[ -f "$APK" ] || fail "APK not found: $APK"

echo "== installing $APK"
adb install -r "$APK" || fail "install failed"

echo "== clearing logcat and launching"
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -n "$PKG/.MainActivity" >/dev/null || fail "could not launch"
sleep 6

shot() { adb shell screencap -p "/sdcard/$1.png" >/dev/null 2>&1 && adb pull "/sdcard/$1.png" "$SHOTS/$1.png" >/dev/null 2>&1; }

echo "== idle screen"
shot 01-start

echo "== typing a command"
adb shell input text "echo%shello%sfrom%scArl"
adb shell input keyevent 66
sleep 3
shot 02-echo

echo "== load: 20000 lines through the parser"
adb shell input text "seq%1%s20000"
adb shell input keyevent 66
sleep 8
shot 03-load

echo "== load: colour and cursor churn"
adb shell input text "ls%s-lR%s/ 2>/dev/null | head -2000"
adb shell input keyevent 66
sleep 6
shot 04-listing

echo "== checking for crashes and ANRs"
CRASHES=$(adb logcat -d -b crash | grep -c "$PKG" || true)
FATAL=$(adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $PKG" | head -20 || true)
if [ "${CRASHES:-0}" -gt 0 ] || [ -n "$FATAL" ]; then
  echo "$FATAL"
  fail "$CRASHES crash buffer entries"
fi

echo "== recent app errors (warnings are fine)"
adb logcat -d -s "$PKG:W" AndroidRuntime:E | tail -30

echo "PASS: no crashes, screenshots in $SHOTS"
