---
name: Linux packages
description: Provision a real Linux userland and install packages with apk or apt.
---

# Linux packages

Two ways to get a Linux userland:

1. **Built-in proot distro** (Settings > Linux). Install Alpine (small) or Debian,
   then every command runs inside it with fake root. Alpine uses `apk`, Debian uses `apt`.
2. **Termux bridge** (optional). If Termux is installed, open cArl can hand commands to
   Termux so its packages are available.

## Alpine
```
apk update
apk add bash git python3 nodejs npm curl wget nano vim htop tmux openssh
```

## Debian
```
apt update
apt install -y bash git python3 nodejs npm curl wget nano vim htop tmux
```

## Node tooling (opencode, MCP servers)
```
npm install -g opencode-ai
npx -y @modelcontextprotocol/server-filesystem /root
```

## Python tooling (OpenHands, MCP servers)
```
python3 -m venv /opt/venv && . /opt/venv/bin/activate
pip install openhands-ai mcp-server-fetch
```

Keep the rootfs small: remove caches with `rm -rf /var/cache/apk/*` or `apt clean`.
