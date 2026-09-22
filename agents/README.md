# Agent Team — PsychometricVocab

The owner (meidan1200-wq) runs this project with two Claude agents. Every agent reads this file first, then its own role file.

| Role | Runs where | File |
|---|---|---|
| **PM / Lead Developer** | Cloud (Claude Code on the web) | [PM.md](PM.md) |
| **IT Manager** | Owner's Windows PC (`C:\Users\Temp\Documents\PsychometricVocab`) | [IT.md](IT.md) |

## Chain of command
1. **Owner**: final authority over everything.
2. **PM**: makes the project and product decisions when the owner isn't around, and has the final word in any disagreement between agents.
3. **IT**: carries out sync, build and emulator tasks, and reports back. IT may push back with reasons, but if a disagreement drags on, the PM decides.

## Source of truth
- **GitHub is the source of truth.** Code changes happen in the cloud and get pushed; the PC only pulls.
- Working branch: `claude/charming-planck-v3dszd`. `master` = released code (the app's OTA updater reads `update.json` from `master`).
- IT does **not** edit app source code. If a local fix is needed, IT reports it to the PM instead.

## How the agents talk
PM ID (permanent): **`session_01DYHRi2Kvpt2vZYWuRoX9oY`**. Short names like `psychometricvocab-xx` change whenever the cloud container restarts, so run `ListAgents` to get the current one.

- **IT → PM:** `SendMessage` to the PM's current name from `ListAgents`. If the PM doesn't show up there, write an entry in `HANDOFF.md` and push it.
- **PM → IT:** the PM pushes, adding an entry to `HANDOFF.md` with every push. IT runs a watch loop (`/loop 5m`, see IT.md) that fetches the branch and acts when there's a new commit. If IT is reachable through `ListAgents` (a CLI session with Remote Control on), the PM also messages it directly.
- **Humans:** the owner can talk to either agent at any time.

## Standard cycle
1. The PM develops, builds and runs unit tests in the cloud, then pushes.
2. The PM tells IT: *"Pushed `<commit>` on `<branch>`: <what changed / what to check>."*
3. IT pulls, builds, installs on the emulator, smoke-tests, and replies **OK** or **FAIL** plus details (build errors, logcat, screenshots).
4. On FAIL → the PM fixes and pushes again → repeat.
