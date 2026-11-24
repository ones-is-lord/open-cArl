---
name: Model management
description: Download GGUF models from Hugging Face and serve them locally with llama.cpp.
---

# Model management

Models screen > Add model takes a Hugging Face URL. Both forms work:
```
https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/blob/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf
https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf
```
Gated repositories need an HF token (Settings > Integrations).

## Size guidance for phones
- 4-bit quantisation, Q4_K_M is the usual sweet spot.
- 1B-3B models: comfortable on 6-8 GB RAM.
- 7B Q4: needs ~5 GB free; expect slow tokens/second.
- 13B+: only on devices with 12 GB+ and patience.

## Serving
```
llama-server -m /root/models/model.gguf --host 127.0.0.1 --port 8080 -c 4096 -t 4
```
Then set the provider to `llama.cpp (on device)` and model `local`.

## Managing
The Models screen shows size, modified date and a delete action. Partial downloads resume
from the `.part` file, so a dropped connection does not restart from zero.
