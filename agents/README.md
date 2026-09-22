# Agent Team — PsychometricVocab

The owner (meidan1200-wq) runs this project with a team of Claude agents. Every agent reads this file first, then its own role file.

| Role | Runs where | File |
|---|---|---|
| **Manager** | Owner's PC (local, Remote Control on) | [MANAGER.md](MANAGER.md) |
| **IT** | Owner's PC (local) | [IT.md](IT.md) |
| **Cloud QA & Docs** | Cloud (Claude Code on the web), session `session_01DYHRi2Kvpt2vZYWuRoX9oY` | [QA.md](QA.md) |

## Chain of command
1. **Owner**: final authority over everything.
2. **Manager**: makes project and product decisions when the owner isn't around, assigns work, and has the final word in any disagreement between agents.
3. **IT** and **Cloud QA**: do their assigned work and report to the Manager. Either may push back with reasons, once; after that the Manager decides.

## Source of truth
- **GitHub is the source of truth.** The PC's local repo stays in sync with it (IT's job).
- Working branch: `claude/charming-planck-v3dszd`. `master` = released code (the app's OTA updater reads `update.json` from `master`). Nothing merges to `master` without the owner's approval.
- Durable knowledge (bug reports, reviews, architecture notes, decisions) lives as files in `agents/` or `docs/`, so every agent can read it, including a new agent after a restart.

## How the agents talk
- **Local ↔ local** (Manager, IT): `SendMessage` using the names from `ListAgents`.
- **Local → Cloud QA:** `SendMessage` to QA's current short name from `ListAgents`. The name changes when the cloud container restarts; the session ID above never does.
- **Cloud QA → local:** QA pushes, adding an entry to [HANDOFF.md](HANDOFF.md). The local agents run a watch loop (`/loop 5m`) that fetches the branch and reads new entries. QA also messages them directly when they show up in its `ListAgents`.
- [HANDOFF.md](HANDOFF.md) is the fallback log for any message that has to survive a restart. Newest entry on top.

## Standard cycle
1. The Manager assigns work (to QA, or to whichever agent it chooses to write code).
2. The change is pushed to the working branch, with a HANDOFF entry saying what changed and what to check.
3. IT pulls, builds, installs on the emulator, smoke-tests, and reports **OK** or **FAIL** plus details to the Manager.
4. QA reviews the change and looks for bugs, then files findings in `agents/reports/`.
5. The Manager decides: fix, ship, or ask the owner.
