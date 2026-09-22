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
1. **Direct messages (preferred):** the PM and IT message each other by session name through `SendMessage`. For this to work, the local session has to be started with **Remote Control** enabled, so it can be reached from the cloud.
2. **Fallback, the handoff log:** [HANDOFF.md](HANDOFF.md) in this folder. Each side appends an entry; newest at the top. It's committed through git, so it works even when the direct link is down.

## Standard cycle
1. The PM develops, builds and runs unit tests in the cloud, then pushes.
2. The PM tells IT: *"Pushed `<commit>` on `<branch>`: <what changed / what to check>."*
3. IT pulls, builds, installs on the emulator, smoke-tests, and replies **OK** or **FAIL** plus details (build errors, logcat, screenshots).
4. On FAIL → the PM fixes and pushes again → repeat.
