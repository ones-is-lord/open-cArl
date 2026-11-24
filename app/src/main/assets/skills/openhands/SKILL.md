---
name: OpenHands
description: Run the OpenHands agent locally in the Linux userland and open its GUI inside open cArl.
---

# OpenHands

OpenHands runs as a Python web service. Inside the Linux userland:

```
apk add python3 py3-pip         # or: apt install -y python3 python3-pip python3-venv
python3 -m venv /opt/openhands
. /opt/openhands/bin/activate
pip install openhands-ai
openhands serve --host 127.0.0.1 --port 3000
```

Then open Settings > OpenHands and tap Launch. open cArl embeds the GUI in a WebView
pointed at `http://127.0.0.1:3000`.

## Notes
- The first start downloads a sandbox image; give it time and disk.
- Configure the LLM inside OpenHands' own settings; it does not read open cArl's keys.
- Keep the server bound to 127.0.0.1 so other devices on the network cannot reach it.
