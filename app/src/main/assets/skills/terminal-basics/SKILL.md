---
name: Terminal basics
description: Navigate the open cArl PTY, use the accessory keys, and run interactive TUIs.
---

# Terminal basics

open cArl runs a real PTY, so full-screen programs work: `vim`, `nano`, `htop`,
`less`, `tmux`, `opencode`.

## Keys
- The accessory bar above the keyboard sends Esc, Tab, arrows, `|`, `~`, `/` and Ctrl combos.
- Ctrl combos available: C-a C-c C-d C-e C-k C-l C-r C-u C-w C-z.
- Long-press the terminal to select text, then use the copy button in the header.
- Paste inserts clipboard text as typed input (newlines become Enter).

## Useful first commands
```
pwd; ls -la
echo $HOME
cat /proc/cpuinfo | head
getprop ro.product.model
```
The device shell is Android's `mksh` with `/system/bin` on PATH. Many GNU tools are
missing there. Install a Linux userland from Settings > Linux to get `bash`, `git`,
`python`, `node`, `apk`/`apt` and the rest.
