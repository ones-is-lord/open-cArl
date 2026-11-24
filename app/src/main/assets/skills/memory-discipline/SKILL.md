---
name: Memory discipline
description: What to store in sequential memory, and how to recall it before acting.
---

# Memory discipline

Memory is an ordered log. Each entry has a kind, tags and importance (1-5).

## When to remember
- Durable user preferences ("prefers Kotlin over Java", "always use pnpm").
- Decisions with a reason ("chose Room over raw SQLite because ...").
- Project facts that are expensive to rediscover: paths, hostnames, build commands.
- Corrections the user made to your behaviour.

Do not store secrets, one-off command output, or anything already in the repo.

## How to write an entry
```
remember(text="Build with ./gradlew :app:assembleDebug", kind="fact",
         tags=["android","build"], importance=4)
```

## How to recall
```
recall(query="build command", limit=10)
```
Recall before starting a task on a project the user has worked on before. The most recent
entries and everything with importance >= 4 are already injected into your context.

## Kinds
`note`, `fact`, `preference`, `decision`, `task`, `correction`.
