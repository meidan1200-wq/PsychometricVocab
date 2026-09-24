# 2026-09-24: IT verification of QA's fix batch (90aa82d)

**Result: OK.** `assembleDebug testDebugUnitTest` succeeded on JDK 25 / Gradle 9.1.0 with a real recompile. Unit tests pass 10/10 (SrsEngineTest 8, SubScreenCodecTest 2). Emulator: medium_phone, Android 16 / API 36.

| Step | Result |
|---|---|
| 1 Build + tests | PASS |
| 2 Background crash from sub-screens | PASS |
| 3 "Don't keep activities" restore | PASS. The quiz and flashcard list restart with new words after recreation (accepted) |
| 4 Language kept across config change | PASS for dark-mode toggle. Rotation untestable (portrait-locked). Resets to Hebrew after a full restart (known) |
| 5 "No hard words" message | Not reachable: the memorize option is disabled at 0 hard words, so no spinner either |
| Flip/swipe cards | PASS (sort → memorize loop → test → session summary) |
| 6 Rapid "Know it" | PASS |
| 7 Quiz repeats / identical options | PASS. "admit" appeared twice in one quiz: two DB entries with the same headword and different meanings |
| 8 Delete account keeps words | PASS. A fresh guest is greeted "Welcome back!" (minor) |
| 9 No slowdown after 20 tab switches | PASS (p99 57 ms after switching vs 150 ms fresh) |
| 10 Logcat errors | PASS |

## New findings
- **System Back on any sub-screen quit the app.** There was no BackHandler. Fixed by the Manager in 453d99c.
- Cold start splash is about 8 s on the first launch after install (emulator). Possibly EncryptedSharedPreferences; not measured.
- Deprecation warnings: `FlashCard.kt:54` Locale constructor; `FlashcardScreen.kt:505` `confirmValueChange`.

## Not checked
The OTA flow (API 36 is available for it), Hebrew-mode quizzes, account creation and photo picker after delete, a fresh install (duplicate "אֲלוּמָּה"), and real hardware.
