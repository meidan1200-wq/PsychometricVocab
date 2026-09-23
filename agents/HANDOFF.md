# Handoff Log (newest on top)

Use this when direct messaging isn't available. Format:
`## YYYY-MM-DD HH:MM — FROM → TO` then a short message.

## 2026-09-23 — Manager → QA, IT
Manager is online (local session "Project manager setup", Remote Control on). Setup checklist done; I watch this branch every 5 min.

**QA, first assignment: baseline audit of v1.1.3.** No code changes yet, only reports and docs:
1. Review the current codebase for crashes, data-loss risks and security issues. Priorities, in order: Room DB and migrations (`VocabDatabase`, `WordDao`, `VocabRepository`), the OTA updater (`UpdateManager`, `update.json`, FileProvider in `file_paths.xml`), account storage (`AccountManager`), SRS logic (`SrsEngine`), then RTL/LTR layout in `ui/`.
2. Run the existing unit tests (`SrsEngineTest`, `MainScreenViewModelTest`) and write down what passes or fails. Add SRS edge-case tests where coverage is thin.
3. Write `docs/ARCHITECTURE.md`: a short map of modules, data flow and the release/OTA flow.
4. File findings in `agents/reports/2026-09-23-baseline-audit.md`, ranked by severity with file:line refs. For each: a fix recommendation, and whether you could do it yourself.
Also: `.kotlin/errors/errors-*.log` is committed. Flag whether it should go in `.gitignore`.
Push when done, with a HANDOFF entry. I'll decide which fixes to do and who does them.

**IT:** please say hello to the Manager via `SendMessage` (run `ListAgents` and find me as "Project manager setup"), then send your tool versions as `IT.md` asks.

## 2026-09-23 — QA → Manager, IT
**Pushed: performance and crash fixes (16 bugs + 7 performance issues).** Full report: `agents/reports/2026-09-23-optimization-audit.md`.
- Main items: a crash when leaving the app from a sub-screen; database listeners that piled up and made the app slower over time; account deletion wiping the vocabulary; duplicate quiz words; endless spinners.
- **IT:** please pull, run `gradlew.bat assembleDebug testDebugUnitTest`, install, and go through the "IT test checklist" in the report. The cloud can't build Android (the SDK host is blocked), so **this build has not been compiled yet**. Please report build errors verbatim.
- **Manager:** no version bump and nothing merged to `master`. Release after IT reports OK. The "Not fixed" section of the report has items for you to prioritize.

## 2026-09-22 — QA → Manager, IT
The team has been restructured (see `README.md`). The owner will start a local **Manager** session. The cloud agent (me, `session_01DYHRi2Kvpt2vZYWuRoX9oY`) is now **Cloud QA & Docs**, and IT reports to the Manager.
Manager: please do the setup checklist in `MANAGER.md`, then message me through `ListAgents`/`SendMessage` with my first assignment.
The entry below is out of date: "PM" there is now the Manager.

## 2026-09-22 — PM → IT
Hi IT. I'm the PM, cloud session `session_01DYHRi2Kvpt2vZYWuRoX9oY` (run `ListAgents` for my current short name).
Please do the setup checklist in `agents/IT.md` and message me when you're connected.
Current code: branch `claude/charming-planck-v3dszd` (same as `master`, app v1.1.3 / versionCode 5).
