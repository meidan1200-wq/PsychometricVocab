# Handoff Log (newest on top)

Use this when direct messaging isn't available. Format:
`## YYYY-MM-DD HH:MM — FROM → TO` then a short message.

## 2026-09-24 — Developer → Manager, IT (Feature C addendum: daily-activity chart added)
Pushed on top of the Home redesign commit. `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, tests pass. No DB schema change (per the spec: `Word` still has no timestamps).

**What changed:** added "הפעילות היומית" / "Daily activity" back in, below the units section, per the owner's follow-up (this replaces my earlier "no chart added" from the first Feature C push).
- New `data/ActivityLog.kt`: plain SharedPreferences, date string → answer count, pruned to the last ~30 days on every write. `recordAnswer()` is called from `VocabRepository.processAnswer` — the single funnel both `QuizViewModel` and `FlashcardViewModel` already go through for every quiz answer and flashcard know/don't-know swipe. `VocabRepository` takes it as an optional constructor param (`activityLog: ActivityLog? = null`) so every other `VocabRepository(dao)` construction site (Home, Progress, Quiz/Flashcard settings) is untouched — only the two ViewModels that actually process answers pass one in.
- Home shows the last 7 days as a small yellow bar chart in a white card, today at the reading-direction end (this falls out for free from the app's existing `LayoutDirection.Rtl` mirroring for Hebrew — the same mechanism the stat columns already use — so I didn't need to manually reverse the list). Days are labeled lightly: א׳–ש׳ in Hebrew, Mon–Sun in English.
- Empty state (fresh install/update, nothing recorded yet): every bar renders as a thin flat sliver (not invisible) plus a short hint, "תרגל היום כדי לראות את הפעילות שלך" / "Practice today to see your activity", shown above the chart.
- Kept cheap per the spec: `HomeViewModel` reads `ActivityLog.getLast7Days()` once in `loadData()`, which already runs fresh every time Home re-enters composition (returning from a quiz or flashcards session), not on a continuous Flow/observer — no DB involved, just one SharedPreferences read.
- Cleared in `AccountViewModel.removeAccount()` alongside the other prefs stores.

**Files touched:** `data/ActivityLog.kt` (new), `data/VocabRepository.kt`, `ui/quiz/QuizViewModel.kt`, `ui/flashcard/FlashcardViewModel.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/account/AccountViewModel.kt`.

**Test list additions for IT (on top of the Feature C list already in this file):**
1. Fresh install (or after clearing app data): Home shows the chart with 7 flat/empty bars and the "practice today" hint, not a crash or a blank card.
2. Answer a few quiz questions, go back to Home: today's bar grows (taller / visibly more than the flat sliver), hint disappears once any day has activity.
3. Do a few flashcard swipes (know/don't-know, either counts), go back to Home: today's bar increases further — confirms flashcards feed the same counter as quiz answers.
4. **Restart the app**, check Home: today's (and any earlier day's) activity is still there — SharedPreferences persistence check.
5. Delete the account, reopen Home: chart is back to the empty state (all flat, hint showing) — confirms `ActivityLog.clear()` ran.
6. Both languages: day labels read א׳ ב׳ ג׳ ד׳ ה׳ ו׳ ש׳ under the bars in Hebrew (right-to-left, today on the left) and Mon–Sun-style abbreviations in English (left-to-right, today on the right) — bars should visually flow in the correct reading direction for each language.

## 2026-09-24 — Developer → Manager, IT (Feature C pushed: Home redesign)
**Pushed to `feature/home-redesign`, branched from `origin/integration/v1.1.5` @ e96443a** (A + B merged). `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, all tests pass (SubScreenCodecTest updated for `SettingsKey`'s removal). No DB schema change.

**What changed (user-visible), per the owner's mockup + `agents/DECISIONS.md` (2026-09-24, Home redesign):**
1. **Header** (greeting, subtitle, Hebrew/English pill): unchanged — same position, same style. The mockup's "HE | EN" switch was not taken.
2. **"מרכז המידע שלי" / My stats**: the 3 separate stat cards became one white card with 3 columns (ידועות/green check, סה"כ מילים/book, לחזרה/refresh), each with one icon, one number, one label — no second "0 לחזרה"-style line, which the owner explicitly rejected in the mockup. ידועות still taps to Progress (known filter), לחזרה still starts the fixed 10-word review quiz — both preserved from before.
3. **"מסלולי למידה" / Learning paths**: unit cards redesigned (icon in a yellow-tinted circle, "יחידה N" — no invented names, a known/total progress bar, "התחל"/"Start"), 2 per row, for **all** units. "כל המילים" is a bigger full-width solid-yellow card, placed after the first row of 2 units, with the rest of the units in rows of 2 below it. Tapping any card (including "כל המילים") starts Feature A's quiz shortcut, same as before this redesign.
4. **Removed "פעולות מהירות"** entirely (the 3 quick-action rows), and did not add a daily-activity chart.
5. **Colors**: unit cards are white with yellow accents (icon circle, progress bar); "כל המילים" is solid yellow. No salmon or purple anywhere.
6. **Bottom bar**: tab index 0 renamed "לומדות"/"Learn" → "פרופיל"/"Profile" with a person icon; it now shows the Feature B Settings screen as that tab's own root content (not a popup/pushed screen, so no back arrow there). Tapping "חשבון"/Account inside it still pushes the Account screen as a sub-screen; back from Account returns to the Profile tab. The centre Home icon and every other tab's name/icon are untouched.
7. **Progress screen**: the avatar button is gone from its top bar (was already the only place it did anything). Home's `onAvatarClick` — already dead code before this feature, per Feature B's HANDOFF note — is now removed for real, since Settings is reached via the Profile tab.
8. `VocabTopBar`'s nav-icon slot now shows a blank spacer instead of an avatar box when neither `onBack` nor `onAvatarClick` is given, instead of always rendering a non-functional avatar circle — this is what makes item 7 possible without deleting the avatar code path outright (kept generic in case a future screen wants it).

**Files touched:** `ui/home/HomeScreen.kt` (rewritten), `ui/home/HomeViewModel.kt` (added per-unit `unitStats`), `ui/progress/ProgressScreen.kt`, `ui/settings/SettingsScreen.kt` (onBack now optional), `ui/components/Components.kt` (`VocabTopBar` nav-slot fallback, `VocabBottomNav` tab 0 relabeled), `Navigation.kt`, `NavigationKeys.kt` (`SettingsKey` removed — Settings is a tab root now, not a pushed sub-screen), `SubScreenCodecTest.kt`.

**Test list for IT (only Feature C):**
1. Home, both languages: header (greeting + HE/EN pill) unchanged in place/style; one "מרכז המידע שלי"/"My stats" card with 3 columns, one label each (no duplicated review text).
2. Tap "ידועות"/Known → goes to Progress. Tap "לחזרה"/"To review" → starts the review quiz (10 words, unchanged from before).
3. **Hebrew track (5 units):** "מסלולי למידה" shows unit 1+2 in a row, then a full-width yellow "כל המילים" card, then units 3+4 in a row, then unit 5 alone (half-width card, not stretched full width). **English track (7 units):** same pattern — 1+2, "All Words", 3+4, 5+6, 7 alone.
4. Each unit card: icon circle, "יחידה N"/"Unit N" only (no other text), a progress bar that visibly reflects that unit's known/total (compare against the Progress screen's per-unit breakdown for the same numbers), "התחל"/"Start" at the bottom.
5. Tap a unit card → starts a quiz on that unit directly (Feature A's shortcut behavior — saved type, fallback toast, etc. all still apply, this feature didn't touch that logic). Tap "כל המילים" → starts a quiz on all units.
6. "פעולות מהירות"/"Quick Actions" is gone from Home entirely; no chart was added in its place.
7. Colors: unit cards are white with yellow icon circle + yellow progress bar; "כל המילים" is solid yellow. Nothing salmon or purple anywhere on Home.
8. Bottom bar: first tab now reads "פרופיל"/"Profile" with a person icon, not "לומדות"/"Learn" with a book icon. Tapping it shows the Settings screen (Account row + Auto pass switch) directly, no back arrow at the top. Tap "חשבון"/Account inside it → Account screen opens with a back arrow; pressing it returns to the Profile tab (still showing Settings, tab still highlighted as index 0). The centre Home icon and the Cards/Quiz/Progress tabs are all unchanged.
9. Progress screen's top bar: no avatar circle anymore (just the title, blank space where it used to be).
10. Restart the app after picking a unit's quiz type in Quiz Settings (Feature A), then tap that unit's card from the new Home layout — the saved-preference shortcut behavior from Feature A should still work exactly as before (this feature only changed layout/entry points, not that logic).

**What I did not check:** real device/emulator visual verification (I don't have emulator access — same caveat as every prior feature); very small screens where 2-per-row unit cards might feel cramped; RTL edge cases in the "כל המילים" card's icon/text mirroring beyond what the layout system handles automatically (same mechanism as the pre-existing stat cards, not new code).

## 2026-09-24 — Developer → Manager, IT (Feature B pushed: Settings + Auto pass)
**Pushed to `feature/settings-auto-advance`, branched from `feature/quiz-shortcuts` @ 5938776** (so it carries Feature A's quiz code; rebase/merge onto `claude/charming-planck-v3dszd` is the Manager's call once A lands). `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, all tests pass (incl. a new `SettingsKey` codec round-trip). No DB schema change.

**What changed (user-visible):**
1. New **Settings screen** (`ui/settings/SettingsScreen.kt`). The avatar circle on the Progress top bar (and Home's `onAvatarClick`, wired the same way) now opens "הגדרות / Settings" instead of jumping straight to Account. It has an **Account** row (tap → the existing Account screen, unchanged) and a **Preferences** section for things that will grow over time.
   - **Note for IT:** I found Home doesn't currently *render* a visible avatar at all — `onAvatarClick`/`profile` were already unused/dead in `HomeScreen.kt` before this feature. I wired Home's callback to Settings for consistency (so it's correct if an avatar is ever added there), but there's nothing to tap on Home today — **please test the Settings entry point via the Progress screen's avatar**, not Home. Flagging this as a separate pre-existing gap, not something I fixed as part of B.
2. **"מעבר אוטומטי" / Auto pass** switch in Preferences, default OFF, persisted in a new `data/AppPreferences.kt` (plain SharedPreferences, `app_prefs`, separate from `QuizPreferences` since it's general app settings, not quiz-specific). Cleared in `AccountViewModel.removeAccount()` alongside the other prefs.
3. When ON, in a quiz after answering:
   - Correct → normal green feedback, auto-advances after **~1s**.
   - Wrong → wrong option stays red, the **correct option blinks green** (a looping light/dark green pulse) for **~2.5s**, then auto-advances.
   - Last question still goes to the results screen as normal (same `onNext()` path).
   - The manual "Next" button still works and skips the wait.
   - When OFF, the quiz is byte-for-byte unchanged from before this feature.
4. **Safety for leaving/backgrounding during the wait:** the auto-advance timer runs in a `LaunchedEffect` keyed on the question index — leaving the quiz screen (back, or navigating away) cancels it automatically via Compose's composition lifecycle, and answering/advancing to a new question always starts a fresh timer for that question rather than reusing a stale one. As a second guard, right before calling `onNext()` after the delay, it re-checks the live `QuizViewModel` state's `currentIndex` against the index captured when the timer started — if they differ (the manual button already advanced), it does nothing, so a race between the manual button and the timer can't advance twice. Going to the background doesn't dispose the composition, so the delay keeps counting and fires once when the app is foregrounded again — not a crash or a skip, just a normal single advance (possibly instant if the delay had already elapsed).

**Files touched:** `data/AppPreferences.kt` (new), `ui/settings/SettingsScreen.kt` (new), `ui/quiz/QuizScreen.kt`, `NavigationKeys.kt`, `Navigation.kt`, `ui/account/AccountViewModel.kt`, `SubScreenCodecTest.kt`.

**Test list for IT (only Feature B):**
1. From the Progress screen, tap the avatar circle: Settings opens (not Account directly). Tap "חשבון"/"Account" inside Settings: the existing Account screen opens.
2. In Settings, toggle Auto pass ON, back out, reopen Settings: it's still ON. **Restart the app** (kill and reopen), open Settings again: still ON — persistence check.
3. Start a quiz with Auto pass ON. Answer correctly: green feedback, moves to the next question on its own after about a second.
4. Same quiz, answer wrong: your wrong pick shows red, the correct option blinks green a few times, and it moves on after about 2.5 seconds (noticeably longer than the correct-answer case).
5. Auto pass ON, on the **last question**: answering (correct or wrong) auto-advances straight to the results screen, not a blank state or a crash.
6. Auto pass ON: answer a question, then immediately tap the manual "Next" button before the auto-advance fires — should advance once (to the next question, not skip one), no double-advance.
7. Auto pass ON: answer a question, then press the in-app back arrow (or leave the quiz another way) before the wait finishes — no crash, no question skipped if you start a new quiz right after.
8. Auto pass ON: answer a question, then send the app to background (Home button) during the wait, and bring it back before/after the wait would have elapsed — no crash, exactly one advance, not two.
9. Auto pass OFF (the default, and re-confirm after toggling it back off): quiz behaves exactly as before this feature — no blinking, no auto-advance, manual Next required.
10. Delete the account, then reopen Settings: Auto pass should show OFF again (prefs cleared).

**What I did not check:** real device testing (emulator-only, and I don't have emulator access myself — same caveat as Feature A); TalkBack/accessibility on the blinking option; extremely short quizzes (1 question) with Auto pass on the last-question path specifically.
## 2026-09-24 — Developer → Manager, IT (Feature A: snap old slider values to the pill, English labels)
Pushed on top of b47588b. `QuizPreferences.getQuizLength()`/`setQuizLength()` now snap to the nearest of a new `ALLOWED_LENGTHS = [5, 10, 15]` list (used by the pill too), so a length saved while the slider still existed (e.g. IT's emulator has 8 or 11 saved) shows the nearest chip selected instead of nothing. English chip labels changed to "5 words"/"10 words"/"15 words" to match Hebrew's "N מילים" — picked this over bare numbers since it fits fine at 3-across. BUILD SUCCESSFUL, tests pass. IT: on your emulator specifically, please check Quiz Settings now shows a chip selected (not none) on first open, matching whatever was closest to your old saved value.

## 2026-09-24 — Developer → Manager, IT (Feature A: owner asked for a pill instead of the slider)
Pushed to `feature/quiz-shortcuts`, on top of 5938776. `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, existing tests pass. Owner feedback (direct, in my session, with screenshots): the slider felt too fiddly — wants a 3-option pill (5 / 10 / 15) in the same spot, same visual style as the Hebrew/English language toggle.

**Change:** `QuizSettingsScreen.kt`'s quiz-length `Slider` replaced with a 3-way segmented pill (`QuizLengthChip`, a private twin of `Components.kt`'s `ToggleChip` look — same rounded pill, `SurfaceGray` track, animated yellow-fill selection). Only 5, 10, 15 are selectable now (`QuizPreferences.MIN_LENGTH` / `DEFAULT_LENGTH` / `MAX_LENGTH`); tapping a chip saves immediately via `quizPrefs.setQuizLength(...)`, same as the slider's release-save behavior before it. No change to `QuizPreferences`, `QuizViewModel`, or anything downstream — this is a UI-only swap, the stored value is still a plain `Int` in the same 5–15 range.

**Test list for IT (just this):**
1. Quiz Settings shows "אורך המבחן" with a 3-segment pill (5 / 10 / 15) instead of a slider, styled like the language toggle above it.
2. Tap each option: the selected one fills yellow, others go transparent/gray text, default on first open is 10 (or whatever was last saved).
3. Pick a value, back out without pressing Start, reopen Settings: the same value is still selected (saved on tap, not just on Start).
4. Start a quiz with each of 5 / 10 / 15 selected: the quiz has exactly that many questions.
5. Quick regression: everything else on this screen (unit picker, word filter, saved-type display) still works as before — this change didn't touch that code.

## 2026-09-24 — QA → Manager, IT (review of feature/quiz-shortcuts @ 42c20c2 + b7012ff)
**Verdict: sound, one small bug fixed. NOT COMPILED** (Android code; the cloud can't build). `SubScreenCodec` + `SrsEngine` tests pass on the JVM harness (11/11).
Fixed on this branch:
- **Quiz-length slider could show and save the step below the one picked.** `onValueChange = { it.toInt() }` truncates stepped float values (8.9999994 → 8). Now `roundToInt()` (`QuizSettingsScreen.kt`).
- **The Developer's b7012ff "show the saved per-unit type" didn't stick.** The saved "missed" choice loads instantly, but `hardestWordsCount` starts at 0 until the DB query returns, and the old `if (!hasEnoughHardWords && unknownOnly) unknownOnly = false` (a state write during composition) reset it to "all words" in that gap. Now derived: `effectiveUnknownOnly = unknownOnly && hasEnoughHardWords`, used for the radio buttons and on Start. (This also resolves my pre-existing item 2 below.)
- `SubScreenCodecTest`: covers the new `useSavedPreference` field, and restoring a quiz key saved by v1.1.4 (4 fields).
Checked, no problem (including the Developer's b7012ff: prefs load per unit, length saved on release): saving/restoring the new nav field, preference keys per track/unit ("all" for null), prefs cleared with the account, fallback toast consumed once, review quiz fixed at 10, cancellation of old quiz loads still intact.
Not fixed (for the Manager; behavior/product, not mine to change):
1. **"Words I missed" gate vs pool mismatch** (pre-existing, now more visible): the gate counts *hard* words (`wrongCount > 0 AND isKnown = 0`), but the quiz pool is *all unknown* words (`!isKnown`), which includes words never seen. So a "words I missed" quiz can be mostly brand-new words. If the intent is "missed" words only, the pool should use the same filter as the count. Needs the owner's call.
2. (fixed above)
3. The quiz restarts from question 1 if the Activity is recreated mid-quiz (theme change, "Don't keep activities"). Pre-existing, low.
**IT test list (only this change):** the Developer's steps 1–2 for the saved type (that's where the race showed). Then in Quiz Settings, drag the length slider through every value 5→15 and back; the label must match each notch, and after Start the quiz must have exactly that many questions. Then leave the app from a Home-shortcut quiz and come back: it restores to the same quiz.

## 2026-09-24 — Developer → Manager, IT (Feature A: 2 fixes from IT's report)
Pushed to `feature/quiz-shortcuts`, on top of 42c20c2. `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, existing tests pass.

**Fix 1 — Quiz Settings didn't show the saved per-unit type.** `unknownOnly` now loads from `quizPrefs.getUnknownOnly(track, selectedUnit)` (default "all words") whenever the track or selected unit changes, instead of always starting `false`. Confirmed: this only sets local UI state — the saved preference itself is written in exactly one place, the Start button's `onClick`, same as before. `QuizViewModel`'s fallback path (Home shortcuts) only calls `quizPrefs.getUnknownOnly(...)` (a read), never `setUnknownOnly`, so a fallback from "missed" → "all words" never overwrites what the owner saved in Settings.

**Fix 2 — Quiz length was only saved on Start.** `Slider` now also saves via `onValueChangeFinished` (on thumb release), not just when Start is pressed. Start still calls `setQuizLength` too (harmless/idempotent) as a safety net.

**Test list for IT (just these two):**
1. In Quiz Settings, select Unit 1, set filter to "רק מילים שלא ידעתי" (needs ≥ quiz length hard words), press Start. Go back to Quiz Settings and re-select Unit 1: the filter should already show "רק מילים שלא ידעתי" selected, not "כל המילים".
2. From step 1, select a *different* unit that has fewer hard words than the quiz length: filter should show "כל המילים" selected (with the existing "not enough hard words" subtitle on the disabled option) — and it should NOT have silently overwritten Unit 1's saved "missed" preference (re-check Unit 1 shows "missed" still selected).
3. Move the quiz-length slider to a new value, then press the in-app back arrow (don't press Start). Reopen Quiz Settings: the slider should show the new value, and a quiz started from a Home shortcut should use that length.
4. Re-run last time's regression list once more for the two touched files (`QuizSettingsScreen.kt` slider/radio behavior) — no need to redo the full 9-step list, just a quick sanity pass that starting a quiz from Quiz Settings still works normally.

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
