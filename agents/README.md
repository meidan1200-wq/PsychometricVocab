# Agent Team — PsychometricVocab

The owner (meidan1200-wq) runs this project with a team of Claude agents. Every agent reads this file first, then its own role file.

| Role | Runs where | Does | File |
|---|---|---|---|
| **Manager** | Owner's PC (local) | Plans, asks the owner, assigns, merges, releases, reports | [MANAGER.md](MANAGER.md) |
| **Developer** | Owner's PC (local), own worktree `..\PsychometricVocab-dev` | New features and UX improvements, **owner-approved only** | [DEVELOPER.md](DEVELOPER.md) |
| **Cloud QA** | Cloud, session `session_01DYHRi2Kvpt2vZYWuRoX9oY` | **Bugs and optimization only** | [QA.md](QA.md) |
| **IT** | Owner's PC (local), own worktree `..\PsychometricVocab-it` | Builds and tests on the emulator: only what changed | [IT.md](IT.md) |

Local session IDs (titles change; IDs don't. Wake an idle session with `SendMessage` to its ID):
- Manager: title "Project manager setup"
- IT: `local_a744400b-2386-4db3-bdcb-73afd0457c9d`
- Developer: `local_36a43943-7269-4a1d-9b7c-da35668d3be3`

## Chain of command
1. **Owner**: final authority. **Anything that isn't a bug fix or an optimization needs the owner's OK first**: new features, UI or behavior changes, product decisions, releases.
2. **Manager**: assigns work, decides bug and optimization matters on its own, has the final word between agents, and asks the owner about everything else.
3. **Developer, QA, IT**: do their assigned work and report to the Manager. Any of them may push back once, with reasons; then the Manager decides.

## Branches
- `master`: released code. The app's updater reads `update.json` from here, so **nothing merges to master without the owner's approval.**
- `claude/charming-planck-v3dszd`: the working branch (tested, next release). **Only the Manager pushes or merges into it.**
- `feature/<name>`: one per Developer feature. QA pushes its fixes for that feature to the same branch. It's merged only after QA and IT are OK and the owner approves.
- `qa/<topic>`: QA's own bug and optimization fixes. The Manager merges them after IT is OK.
- **The Manager gives the final verdict:** it does the final merge and push, and creates every new version.
- The main folder `C:\Users\Temp\Documents\PsychometricVocab` stays on the working branch. Local agents who need another branch use their own worktree, so they never switch branches under each other.

## Feature cycle
1. The owner approves a feature → the Manager writes it in `TASKS.md` and sends it to the Developer.
2. The Developer builds it on `feature/<name>`, compiles, pushes, and writes a HANDOFF entry with **a test list for only that feature**.
3. **QA** reviews the feature branch for bugs and performance, and pushes fixes to the same branch. The Manager triggers QA (or asks the owner to, if QA's session is asleep).
4. **IT** builds the feature branch and runs **only that test list**, plus a quick launch check. No re-testing of things that already work.
5. Problems go back to the Developer (or QA) and repeat steps 2–4.
6. When everyone is done: the Manager sends the owner **one** final report and asks to merge, and later to release.

## Bug and optimization cycle
QA (freelance) pushes the fix to `qa/<topic>` → IT tests only the fix → the Manager merges it into the working branch. The owner hears about it in the next final report.

## How the agents talk
- **Local ↔ local:** `SendMessage` to the session ID (or the name from `ListAgents`).
- **To Cloud QA:** `SendMessage`, when it shows up in `ListAgents` (only while awake). Otherwise, the owner starts it.
- **From Cloud QA:** it pushes with an entry at the top of [HANDOFF.md](HANDOFF.md). It can't message.
- Report **once, when finished**: what was verified, and what wasn't. No progress pings or acknowledgements.
- Durable knowledge lives in `agents/` (roles, [TASKS.md](TASKS.md), [DECISIONS.md](DECISIONS.md), `reports/`) and `docs/`.
