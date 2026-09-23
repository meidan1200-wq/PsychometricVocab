# QA report: performance and lifecycle audit (2026-09-23)

**Author:** Cloud QA (`session_01DYHRi2Kvpt2vZYWuRoX9oY`)
**Branch:** `claude/charming-planck-v3dszd`
**Status:** fixed in code; **not yet built or run on a device** (see "Verification" below).

## Summary
The app gets slower the longer it's used, and it can crash when you leave it from a sub-screen. The root causes are database listeners that pile up and never stop, heavy work on the main thread, and navigation state that can't be saved. All of the items under "Fixed" are done in this push.

## Fixed

### Crashes and data bugs
| # | Problem | Where | Fix |
|---|---|---|---|
| 1 | **Crash when the app goes to the background while a sub-screen (flashcards, quiz, quiz settings, account) is open.** The sub-screen key was stored in `rememberSaveable`, which can only hold values that fit in a Bundle; the key classes don't, so saving the instance state throws. | `Navigation.kt` | Added a String `Saver` (`SubScreenCodec` in `NavigationKeys.kt`) and a unit test for it. |
| 2 | The selected language reset to Hebrew after the activity was recreated (theme or font change, or process death). | `Navigation.kt`, `AppState.kt` | `AppState` is now saved with a `Saver`. |
| 3 | **Deleting the account deleted the whole vocabulary** (`clearAllTables()`), so the app showed 0 words until it was restarted. | `AccountViewModel.kt` | New `WordDao.resetAllProgress()` resets progress and keeps the words. |
| 4 | Quizzes could show **the same word twice**: the selection algorithm topped up the list with words it had already picked. | `SrsEngine.selectWordsForSession` | Rewritten to pick distinct words; a test covers it (the old code fails it). |
| 5 | A quiz answer could have **two options with identical text**, since some words share a definition. | `QuizViewModel.buildQuestion` | Distractors must have a different definition. |
| 6 | Starting a new quiz could get filled with questions from the **previous** quiz's unit, because the old loader was still running. | `QuizViewModel` | The previous load job is cancelled; loading is now a single read. |
| 7 | A quick double tap on "Know it" or "Don't know" **skipped a card**, and the same word could be recorded twice. | `FlashcardViewModel.onSwipe` | State advances immediately; the database write happens afterwards. |
| 8 | Sort mode could record the same swipe twice (the swipe callback can fire repeatedly). | `FlashcardViewModel.onSwipeWord` | Ignores words that were already sorted. |
| 9 | **Endless spinner** when a session has no words: memorize mode with no hard words, or a unit that's already fully sorted. | `FlashcardScreen` | Added an empty-state message and a Back button. |
| 10 | Memorize mode for a single unit often found 0 words: it took the global top 50 hard words, then filtered by unit. | `FlashcardViewModel`, `WordDao` | New query that filters by unit in SQL. |
| 11 | The update dialog could be shown on an Activity that had already closed, throwing BadTokenException (a crash). | `UpdateManager` | Checks `isFinishing`/`isDestroyed` first. |
| 12 | The update check ran again on every activity recreation and left a thread running each time. | `MainActivity`, `UpdateManager` | Runs only on a fresh start; the executor is shut down. |
| 13 | The update download receiver was tied to the Activity, so if the user left the app mid-download it leaked and the install never started. | `UpdateManager` | Uses the application context. |
| 14 | A first launch interrupted mid-seed could leave a **half-filled word database permanently**: inserts were chunked, and the "count == 0" check never re-seeds. | `VocabDataLoader` | Single transactional insert. |
| 15 | Picking an avatar from some providers could crash (`takePersistableUriPermission` throws SecurityException). | `AccountScreen` | Wrapped in try/catch. |
| 16 | The Progress screen's expanded unit collapsed on every data refresh (its state wasn't remembered). | `ProgressScreen` | Now uses `remember`, and list items have keys. |

### Performance (why it felt slower over time)
| # | Problem | Fix |
|---|---|---|
| P1 | **`HomeViewModel.loadData` started a new database listener every time Home was shown, and none ever stopped.** After a few minutes of switching tabs, each answer made dozens of listeners re-run their queries. | `stateIn(WhileSubscribed)` + `flatMapLatest`: one listener per language, and it runs only while Home is visible. |
| P2 | Same for **Progress**, which is worse: each listener loads **every word** of the track (about 4,000 rows) and groups them **on the main thread**, after every answer, forever. | Same pattern, plus `flowOn(Dispatchers.Default)`. |
| P3 | **Quiz** loading left a listener on the whole word table after every quiz, so each answer re-queried about 4,000 rows once per quiz you had ever started. | One-shot `first()`, and the previous job is cancelled. |
| P4 | The quiz shuffled the entire word list for every question (20 × 4,000). | Picks 3 random distractors directly; runs off the main thread. |
| P5 | **A new TextToSpeech engine was created for every flashcard** (binding the TTS service on every swipe). | One engine per flashcard screen. |
| P6 | `Html.fromHtml` ran on every recomposition for every word shown, but only 25 of 6,215 entries contain markup. | Plain strings are returned unchanged. |
| P7 | Flashcard settings loaded every untouched word just to count them. | Uses the existing `COUNT(*)` query. |

### Tests
- The old `SrsEngineTest` **did not compile** (it passed `Int` where `Float` is required) and asserted a fixed order from a shuffled algorithm. It's rewritten with 8 meaningful tests.
- Added `SubScreenCodecTest` (2 tests).
- Removed `MainScreenViewModelTest` and `androidTest/.../MainScreenTest`. They were leftover template code referencing classes that don't exist, which broke `gradlew test` and `connectedAndroidTest`.
- `gradlew` is now marked executable in git (needed on Linux/CI; no effect on Windows).

## Verification
- **Done in the cloud:** `SrsEngine`, `Word` and `SubScreenCodec` were compiled and all 10 unit tests were run on a JVM harness: **10/10 pass**. The old `SrsEngine` fails the 2 repeated-word tests.
- **Not done:** a full Android build. The cloud can't reach `dl.google.com` (the Android SDK and Google Maven host), so Compose, Room and AndroidX code wasn't compiled here. The changes were reviewed line by line, but **IT must build before anything is released.**

### IT test checklist
1. `gradlew.bat assembleDebug testDebugUnitTest` must pass.
2. **Background crash:** open Flashcards (any unit), press Home, reopen the app. Repeat from Quiz, Quiz settings and Account. There should be no crash, and you should come back to the same screen.
3. Turn on Developer options → "Don't keep activities", then repeat step 2. The app should restore the same sub-screen and language.
4. Switch the language to English, rotate or change the theme: the language should stay English.
5. Flashcards → Memorize, on a unit with no mistakes yet: you should see the "No hard words to practice yet" message, not an endless spinner.
6. Test mode: tap "Know it" rapidly several times. The counter should advance exactly once per tap, with no skipped cards.
7. Run two or three quizzes. No word should repeat within a quiz, and no answer options should have identical text.
8. Account → delete account → Home should still show all words (with progress reset to 0).
9. Speed feel: switch tabs about 20 times, then answer cards. It should stay as fast as at launch.
10. Logcat (`adb logcat -d *:E`) should show no new errors.

## Not fixed: needs a decision or device testing
- **Startup jank:** `EncryptedSharedPreferences` (security-crypto `1.1.0-alpha06`, which is deprecated) is created on the main thread at launch; Keystore/Tink setup can take hundreds of ms. Suggested fix: load the profile off the main thread, or migrate to DataStore. It touches the registration flow, so it's left for the Manager to schedule.
- **OTA on Android 14+:** the download-complete receiver is registered `RECEIVER_NOT_EXPORTED`. The broadcast comes from the system DownloadManager, and on some devices a not-exported receiver doesn't get it, so the download finishes but the installer never opens. **IT: test the update flow on an Android 14+ emulator.** If it's broken, the fix is `RECEIVER_EXPORTED` (safe here: it only installs our own downloaded file).
- **`fallbackToDestructiveMigration()`**: any future DB `version` bump without a `Migration` **silently wipes all user progress**. Rule for every developer: never bump the DB version without writing a migration.
- **Fresh installs contain a duplicate "אֲלוּמָּה"**: `MIGRATION_3_4` only fixes upgrades. Fix it in the JSON asset.
- The 3 MB word asset stores the data twice (`vocabulary` and `flat`); only `flat` is used. Removing `vocabulary` would halve the asset size.
- The language isn't remembered across full app restarts (only across recreation). That's a product decision.
- A flashcard session restarts if the Activity is recreated. That's acceptable for now.
- A quiz with nothing to ask (0 hard words) now shows a 0% result screen. A friendlier message would be better.
