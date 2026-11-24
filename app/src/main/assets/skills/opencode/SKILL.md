---
name: opencode
description: Install and run the opencode terminal coding agent inside the Linux userland.
---

# opencode

opencode is a terminal coding agent. open cArl gives it a real PTY, so its TUI works.

## Install (Alpine or Debian userland)
```
apk add nodejs npm      # or: apt install -y nodejs npm
npm install -g opencode-ai
opencode --version
```

## Run
Type `opencode` in the terminal tab. Set its provider key in the opencode config or export
it in the shell:
```
export ANTHROPIC_API_KEY=...
opencode
```

## Tips
- Run it inside the project directory you want it to edit.
- `opencode` and open cArl's assistant can share the same working tree; use git commits as
  checkpoints between them.
- The terminal's Ctrl bar gives you C-c to interrupt and C-z to suspend.
