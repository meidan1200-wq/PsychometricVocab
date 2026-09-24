# Handoff Log (newest on top)

Use this when direct messaging isn't available. Format:
`## YYYY-MM-DD HH:MM — FROM → TO` then a short message.

## 2026-09-24 — Manager → QA (thanks, status)
Your home-redesign review (f8cdd68 per-unit SQL progress) and `qa/flashcard-double-swipe` (ead59a7) are merged into the release candidate `integration/v1.1.5` @ 9fbd3fc. HANDOFF was kept on both sides, and it builds and the tests pass locally. IT is running the full pre-release check next.
- "Words I missed" pool: **the owner decided not to touch it for now.** Please drop it from your open list.
- Auto-advance keeps running in the background: accepted as-is. You come back at most one question further on, never skipped or doubled (IT verified).

## 2026-09-24 — Manager → QA (review request 2)
Thanks for the Feature A review. It's IT-verified and waiting for the owner's merge approval.
Please review `feature/settings-auto-advance` (efce6bf, based on A): the new Settings screen, the `AppPreferences` auto-pass switch, and the auto-advance and blink logic in `QuizScreen` (a LaunchedEffect timer keyed on the question index). Bugs and performance only; push fixes to that same branch, with a HANDOFF entry there.

## 2026-09-24 — Manager → QA (review request)
Please review the Developer's branch `feature/quiz-shortcuts` (42c20c2): Home unit cards start quizzes, saved per-unit quiz type, quiz length 5–15 (new `data/QuizPreferences.kt`), and a 10-word review quiz. Look for bugs and performance issues only, and push any fixes to that same branch with a HANDOFF entry there. IT is testing it in parallel.

## 2026-09-24 — Manager → QA (bug to look at)
v1.1.4 is released (master = 314c35e). One bug for you, low severity: in memorize/test mode, a single left swipe on "Word 1 of 2", right after flipping the card, jumped straight to "Session Complete ✅0 ❌2", as if the swipe counted twice. Seen once on the emulator, not reproduced in 3 retries. Suspect a double-fired dismiss or drag end in `SwipeableFlashCard` / `FlashcardScreen` (the swipe-to-dismiss path, `FlashcardViewModel.onSwipe`). If you find the cause, push the fix to `qa/flashcard-double-swipe`.

## 2026-09-24 — Manager → QA
**Your role changed (owner's decision). Please re-read `agents/README.md` and `agents/QA.md` before your next push.**
- You now handle **bugs and optimization only**: no features, UI or wording changes.
- **Never push to `claude/charming-planck-v3dszd` or `master` again.** Push your own fixes to `qa/<topic>`. When reviewing a Developer branch `feature/<name>`, push fixes to that same branch. The Manager merges and creates versions.
- Every push: HANDOFF entry, "NOT COMPILED" if it touches Android code, and a test list covering only what you changed.
Status: your 90aa82d batch was built and verified by IT (10/10 tests, 9/10 checklist steps; the tenth screen can't be reached). The Manager fixed the rest: Back quitting the app, OTA broken on Android 13+ (your RECEIVER_EXPORTED call was right), and the first-time install permission. v1.1.4 is approved and waiting on a signing-key decision.

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
