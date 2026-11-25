# Tools

37 tools, all real. Every one is a Kotlin function bound to a JSON schema; there
is no tool that is only described but not implemented.

Approval: with `ALWAYS_ASK` every shell command and write is confirmed, with
`SMART` (default) destructive ones are, with `AUTO` nothing is. D.G.A.F. mode is
the explicit, revocable version of `AUTO`.

| Tool | What it does |
|---|---|
| `bash` | Run a shell command in the persistent Linux shell. The working directory, environment and installed packages persist between calls. Returns exit code, cwd and output. |
| `read_file` | Read a UTF-8 text file from the workspace. Paths are relative to the workspace root. |
| `write_file` | Create or overwrite a UTF-8 text file in the workspace. Parent directories are created. |
| `list_dir` | List files and directories in the workspace. |
| `use_skill` | Load the full instructions of a skill by id or name. |
| `list_skills` | List the installed skills with a one-line description each. |
| `remember` | Append an entry to sequential long-term memory. Use for durable preferences, decisions, project facts and corrections. Never store secrets. |
| `recall` | Search long-term memory. Empty query returns the most recent entries. |
| `fetch_url` | Fetch an http(s) URL and return readable text (HTML is converted to plain text). |
| `spawn` | Delegate a self-contained job to a sub-agent with a fresh context and get back a short report. Use it for long research, multi-file refactors or anything that would flood this conversation. |
| `files` | Work inside the workspace: list, read, write, append, mkdir, delete, move, copy, stat or search file contents. Paths are relative to the workspace root. |
| `script` | Write, list, run or delete your own shell scripts in the workspace bin folder. This is how the agent extends itself. |
| `code` | Run a snippet in python3, node or sh without leaving the app. The file is written to the workspace scratch folder, executed, and the output returned. |
| `shell_send` | Type into the live terminal session, for interactive programs like ssh, vim, python REPL or top. Send the keystrokes and then read the screen back. |
| `packages` | Find, install, remove and upgrade packages in the Linux userland (apk, apt or pkg, whichever exists). Use it when you need a tool that is not installed. |
| `net` | Network work: ping a host, resolve DNS, probe an HTTP endpoint, TCP-scan ports, list interfaces, find the public address. Only scan hosts you own or may test. |
| `wifi` | Scan visible wifi networks, log them to a wardriving CSV and summarise by band. Needs the location permission; Android throttles repeat scans. |
| `diagnostics` | What the phone is doing now: cpu, memory, storage, battery, thermal, uptime, top processes, logcat and dumpsys (last two through Shizuku). |
| `plan` | A persistent task list you and the user share: add, list, mark doing or done, remove, clear finished. Use it for multi-step work and anything the user wants tracked. |
| `study` | Spaced repetition: add cards, see what is due, grade recall, read stats. Use it when teaching or quizzing so weak items come back. |
| `schedule` | Schedule work for later: an agent prompt that runs when the app is open, or a shell command that runs headless. One-off or repeating. |
| `snapshot` | Save or roll back the workspace: create a snapshot with the transcript and conversation, list them, diff one against current files, or restore it. |
| `clipboard` | Read or write the system clipboard, for moving text between the agent and other apps. |
| `notify` | Post a notification to the user's shade, for finished long jobs and reminders. |
| `persona` | Read or change your own tone. Use it when the user asks for a different register, and switch back when they are done. |
| `profile` | The long-term picture of this user: habits, needs, wants, plans, preferences. Write facts here when you learn them so you never ask twice. |
| `kb` | Knowledge packs: import a folder or file of notes, docs or code into memory so recall can cite it. Then search it instead of guessing. |
| `triggers` | Rules that fire on device state: battery below/above, charging, thermal level, wifi ssid, screen on/off, storage low. Each fires an agent prompt or a headless command. Polled every 15 minutes because Android throttles background sensors. |
| `device_info` | Report this phone's CPU, GPU, RAM, NPU estimate and free storage, and list the local models that will actually run on it. |
| `screen` | Read or drive the phone's UI through the accessibility service: read the current screen, tap an element by its text, type into the focused field, swipe, or press back/home/recents. |
| `privileged_shell` | Run a shell command as the app, as the shell user through Shizuku, or as root. Use it for anything the plain terminal cannot do: pm, settings, dumpsys, system file repair. Every command is written to the audit log. |
| `run_script` | List or run the curated maintenance scripts: setup, debloat, cache trimming, animation speed, battery whitelist, media repair, storage triage, VM tuning. |
| `storage` | Triage device storage: find large files, empty files, duplicates, broken links and empty folders, and optionally delete the safe ones. |
| `apps` | Inspect and manage installed packages: list, size, disable/enable, uninstall for the current user, clear cache, force stop, install an APK from a file path. |
| `root` | Root and recovery helpers: report the root manager and boot slot, list or install Magisk modules, reboot, and print a recovery plan. |
| `deep_research` | Search the web and read the top pages, returning a citable corpus. Use it for anything current or factual that you cannot answer from memory. |
| `think` | A scratchpad for planning. Nothing is executed; use it to lay out steps before acting. |

## Sub-agents

`spawn` builds a second agent with a fresh history, its own step budget and the
same tool set minus `spawn` itself. Depth is capped at one level, so delegation
cannot recurse into a fork bomb. The parent receives only the final report.

## MCP

Every configured MCP server contributes its tools under
`mcp__<server>__<tool>`, with secrets substituted at connect time and never
written to disk.
