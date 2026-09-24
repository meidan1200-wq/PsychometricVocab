# Handoff Log (newest on top)

Use this when direct messaging isn't available. Format:
`## YYYY-MM-DD HH:MM — FROM → TO` then a short message.

## 2026-09-24 — Developer → Manager, QA, IT (Feature A pushed)
**Pushed to `feature/quiz-shortcuts`, branched from `claude/charming-planck-v3dszd` @ 206a390.** `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, all existing unit tests still pass. No DB schema change.

**What changed (user-visible):**
1. Home unit cards ("כל המילים" / "All Words" and each "יחידה N" / "Unit N" card) now start a **quiz** directly instead of opening flashcards. Flashcards are still reachable from the bottom-bar "Cards" tab and the "כרטיסיות לימוד" quick-action card, unchanged.
2. That shortcut quiz uses the type (all words / words I missed) last chosen **in Quiz Settings for that unit** (per track), defaulting to "all words" if nothing was saved yet. If the saved type is "words I missed" but there are no longer enough hard words for it, the quiz silently runs on all words instead and shows a short Toast explaining why.
3. Quiz Settings has a new **quiz length** slider, 5–15 words, default 10 (never above 15). It applies to normal quizzes and to the Home shortcuts. The "not enough hard words" gate in Quiz Settings now compares against this length instead of the old fixed 20.
4. The red "לחזרה" review card is unaffected in behavior but its word count moved from 20 to a **fixed 10** (independent of the length slider), per the owner's request in TASKS.md/DECISIONS.md.
5. All of the above (quiz length, per-unit/per-track saved type) is persisted in a new plain SharedPreferences file (`quiz_prefs`, via new `data/QuizPreferences.kt`) — not Room, no migration needed. Cleared in `AccountViewModel.removeAccount()` alongside the existing account/progress reset.
6. New UI text (quiz-length label/value, the fallback Toast, the updated "not enough hard words" subtitle) has both Hebrew and English strings and follows the existing RTL/LTR pattern.

**Files touched:** `data/QuizPreferences.kt` (new), `ui/quiz/QuizViewModel.kt`, `ui/quiz/QuizScreen.kt`, `ui/quiz/QuizSettingsScreen.kt`, `ui/home/HomeScreen.kt`, `Navigation.kt`, `NavigationKeys.kt`, `ui/account/AccountViewModel.kt`.

**Test list for IT (only this feature — no need to re-test flashcards, progress, or anything not listed):**
1. Home → tap "כל המילים" / "All Words" card → a quiz starts immediately (not flashcards), covering all units, using "all words" (no prior preference saved).
2. Home → tap a "יחידה N" card → a quiz starts immediately, scoped to that unit only.
3. Go to Quiz Settings (bottom-bar "Quiz" tab, or the "חידון מילים" quick action), pick Unit 1, set filter to "רק מילים שלא ידעתי" (needs ≥ current quiz-length hard words in Unit 1 — check the length slider's value), start the quiz. Then go back to Home and tap the "יחידה 1" card: it should start "words I missed" directly, no settings screen shown.
4. In Quiz Settings, move the quiz-length slider (e.g. to 6 or 15), start a quiz normally: question count should match the slider value, not 10 or 20.
5. **Restart the app** (kill and reopen) after steps 3–4, then repeat step 3's Home tap and check a normal quiz's length: both the saved unit-type preference and the length should still apply — this is the SharedPreferences persistence check.
6. Fallback: pick a unit/track combo with fewer hard words than the current quiz length, set its saved type to "words I missed" via Quiz Settings (only reachable while it still had enough words, or reduce the length slider first so it's briefly available, save it, then raise the length again) — then tap that unit's Home card. Expect a quiz on all words plus a short Toast, not a crash or an empty quiz.
7. Home's red "לחזרה" card still works and asks 10 words (count them or check `currentIndex`/total on the result screen), same as before this change size-wise (previously 20).
8. Delete the account (Account screen → remove account) then check Quiz Settings shows the default length (10) and no unit shows a saved type — i.e. prefs were cleared.
9. Hebrew and English tracks: quick check that the new quiz-length text and the fallback Toast text switch correctly with the language toggle, and RTL layout (slider, text alignment) looks correct in Hebrew.

**What I did not check:** real device testing (built/tested on emulator assumptions only, not run myself since I don't have emulator access — IT should run all of the above); no visual/screenshot check of the slider in dark mode if the app has one; did not test extremely low word counts (e.g. a unit with only 1–2 words) with the length slider at 15.

**Also per the Manager's request:** proposal C (calmer Home design) is not ready yet — I'll send it as a separate message once drafted, per DEVELOPER.md ("no code until owner picks").

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
