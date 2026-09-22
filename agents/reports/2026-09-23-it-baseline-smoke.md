# 2026-09-23: IT baseline smoke test of v1.1.3 (commit a547d25)

**Result: OK.** Builds on JDK 25 / Gradle 9.1.0 (cached, not a clean build), installs on medium_phone (gpu host), and every main screen opens. No crashes or AndroidRuntime errors in logcat. Screenshots are on the PC only: `agents/reports/it-2026-09-23/` (untracked).

## Manager triage (checked against the code)
| # | Observation | Verdict | Next |
|---|---|---|---|
| 1 | Bottom-nav tab label is "שב" | **Bug.** Hard-coded in `ui/components/Components.kt:220`; the English equivalent is "Cards". It should read "כרטיסיות". | Fix in the next UI batch |
| 2 | "לומדות" tab highlighted while Home is active | Likely bug, not yet verified in code | Check `currentTab` mapping in the UI batch |
| 3 | Account screen is English-only in Hebrew mode; Latin text right-aligned | **Bug.** `ui/account/AccountScreen.kt:117,131` use hard-coded English labels (`AuthScreen` already localizes them) | Fix in the UI batch |
| 4 | Quiz "וואלה, אין לי מושג" looks pre-selected on each question | Unverified; could be styling of the always-last option | Check in the UI batch |
| 5 | 3 quiz answers didn't change the "known" count | **Not a bug.** Every answer is saved right away (`QuizViewModel.onAnswer` calls `repo.processAnswer`), but a word counts as known only when its `srsScore` reaches 1 (`SrsEngine.kt:34`) | None |
| 6 | Flashcard meaning is centered; the disabled radio is misaligned | Cosmetic | UI batch, low priority |
| – | IT saw "flashcards" as an expandable list | Probably the "Learn" list. There's a real swipe/flip card in `ui/components/FlashCard.kt`, which IT may not have reached | Retest in the next smoke run |

## Not checked
Clean build, English mode, a full 20-question quiz, the hard-words mode, single-unit selection, TTS, Account save/remove and photo picker, the OTA updater, rotation, dark mode, fresh install, and the Progress unit expanders.

## Env notes
The build warns "SDK XML version 4": command-line tools are older than Studio's. Harmless for now.
