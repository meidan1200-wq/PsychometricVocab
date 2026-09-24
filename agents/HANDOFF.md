# Handoff Log (newest on top)

Use this when direct messaging isn't available. Format:
`## YYYY-MM-DD HH:MM — FROM → TO` then a short message.

## 2026-09-24 — QA → Manager, IT (fix pushed to `qa/flashcard-double-swipe`)
**Cause found for "one left swipe → Session Complete ✅0 ❌2". NOT COMPILED** (Android code).
- `SwipeableFlashCard` reported the swipe from `animateFloatAsState`'s `finishedListener`, which fires after **every** completed animation, not once. The dismissed card stays touchable while it flies out (300 ms) and during the AnimatedContent exit, so any touch in that window restarts the animation, and when it ends the card reports again. The direction was re-read from `offsetX`, which is usually reset to 0 by then, so the extra answer is always "don't know", which matches ✅0 ❌2. And because the card callbacks called `vm.onSwipe()` (answers the *current* word), the stale callback answered the next card.
- Fix (`FlashCard.kt`): the direction is fixed when the card is released, the answer is reported at most once per card, and drags on a dismissed card are ignored. Also (`FlashcardViewModel.onCardSwiped`, `FlashcardScreen.kt`): a card's callback is ignored unless that card is still the current word.
- The "Know it / Don't know" buttons are unchanged (they already advance synchronously since 90aa82d).
**IT test list (only this change):** Flashcards → Memorize → "Ready to Test!" (and a plain unit session):
1. Swipe a card left once: exactly one ❌, next card shown ("Word 2 of N").
2. Swipe left, then immediately tap/drag the card again while it flies out, several times: still exactly one answer per card; no skipped cards.
3. Tap to flip, then swipe right: one ✅, and the next card comes in unflipped.
4. Last card: one swipe leads to Session Complete with counts that add up to the number of cards.
5. The buttons still work normally (one tap = one answer).
Not checked: I couldn't reproduce this on a device (none in the cloud); the cause comes from reading the code.

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
