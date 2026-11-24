---
name: Android device
description: Inspect and automate the phone from the shell with getprop, am, pm, dumpsys and termux-api.
---

# Android device

The device shell runs as the app's uid, so no root. Useful commands:

```
getprop ro.product.model          # device model
getprop ro.build.version.release  # Android version
pm list packages | grep -i termux
am start -a android.intent.action.VIEW -d https://example.com
dumpsys battery
dumpsys meminfo | head -20
cmd wifi status
```

## Limits
- No access to other apps' private data.
- `/sdcard` needs storage permission; the app's own home is always writable.
- `su` is only available on rooted devices.

## Termux bridge
If Termux is installed and its `allow-external-apps` setting is on, open cArl can run
commands through `com.termux.RUN_COMMAND`, which unlocks Termux packages and `termux-api`
(hardware sensors, clipboard, notifications, SMS, camera).
