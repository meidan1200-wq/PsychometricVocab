# Handoff Log (newest on top)

Use this when direct messaging isn't available. Format:
`## YYYY-MM-DD HH:MM — FROM → TO` then a short message.

## 2026-10-29 — Developer → Manager, IT (`fix/quiz-card-shadow`: quiz word-card shadow no longer flickers)
`ui/quiz/QuizScreen.kt` only: the shadowed word `Card` now sits OUTSIDE `AnimatedContent` (which clips to its bounds, so the 6dp shadow was cut at the sides and doubled while two cards cross-faded); only the card's inner content (speaker, word, underline, hint) cross-fades, keyed on the word instead of the whole question, so answering no longer re-runs the fade either. The card follows a height change through AnimatedContent's own size animation. The flashcard screens were checked and left alone: their cards are 85–90% wide and slide as a whole on purpose, so their shadow is not cut at the sides. 83 unit tests pass; not run on a device.
- IT: screen-record a quiz (Auto pass on and off) moving from one word to the next, and answering a question: the card's outline and shadow stay fixed and complete (no cut-off sides, no flash), only the text fades; try a long word and a short word back to back (the card height eases); Hebrew and English tracks; the options below still fade/colour as before.

## 2026-10-29 — Developer → Manager, IT (learning-v2 retune 4: spaced-review share; overlap and retention targets CONFLICT, your decision)
Branch `feature/learning-v2`. Built as asked: (1) **due share**: `dueFraction` 25% of a session (Practice `practiceDueFraction` 40%, via `SessionSelector.practiceConfig()`) goes to words due by SRS (`nextReviewDate <= now`, practised, not answered in the last 24 h), drawn by D·U; the rest is the E·D·U·C draw; new words stay 20–30%; the 48 h cap is now 35% of the session (`ceil`, 4 of 10) and due words answered 24–48 h ago count toward it. (2) **Master mode** draws never-seen words U-weighted across all units (unit 1 ≈ 1.9× unit 10, every unit represented); a single unit keeps unit/id order. (3) Per-day cap not built (known limit: 8 sessions a day for a week gave up to 7 asks of one word in a day). (4) Simulation re-run with a **uniform-random baseline** in the same harness and a retention band. Log line gained `dueInPool=<n> dueTaken=<n>`.
**Result: not all targets can be met.** Overlap ≥ 30% needs ≥ 3 of 10 slots spent re-asking last session's words; those massed repeats replace spaced reviews, so retention falls the more overlap there is. Single unit, 30 days × 3 × 10, 5 seeds (retention = chance the week-1 words are right a week later, end of week 1 → week 4):
| setting | overlap | retention | gap to uniform at wk 4 |
|---|---|---|---|
| uniform random (baseline) | 10.7% | 51 → 67 | 0 |
| due 35%, cap 35%, due counted | 21% | 51 → 59 | −8 |
| due 45%, cap 35%, due counted | 18% | 52 → 57 | −10 |
| **due 25%, cap 35%, due counted (pushed default)** | **25%** | **52 → 58** | **−9** |
| due 25%, cap 50%, due counted | 32% | 52 → 56 | −11 |
| due 25%, cap 35%, due not counted | 32% | 50 → 53 | −14 |
| due 35%, cap 35%, due not counted | 31% | 50 → 52 | −15 |
| due 25%, cap 50%, due not counted | 38% | 50 → 51 | −16 |
| previous push (no due share, cap 50%) | 40.5% | 51 → 53 | −14 |
Master mode cold start gives the same picture (default: overlap 25%, retention 51 → 58, uniform 50 → 67). Pushed default = the retention-favouring end that still re-asks ~2.5 of 10 words from the previous quiz (device before: 1–2). The levers that did not move the frontier: difficulty floor 0.15 → 0.01, last-wrong factor 1.5 → 8, cooldown 0.3 → 0.02, recency boost 100–500, long-term boost 2 → 8. Retention tops out near 58–59 at any overlap above 18%, because 25% new + 25% due + 35% recent leaves ~1 free slot, and the SRS-due ladder (1, 6, 15 days) reviews a word less often in a month than uniform sampling does. To trade towards overlap, change `recentMaxFraction` (0.35 → 0.5) and/or `dueCountsTowardRecentCap` (true → false) in `SessionSelector.Config`; no other change needed.
**Bands now:** met: new words 2–3 per 10 (all runs), unit1/unit10 share 1.65–3.3 (master cold), max asks 3 per day at 3 sessions a day, coverage 60/113/165/208 at days 7/14/21/30. Missed vs your targets: overlap 24–26% (target ≥ 30%); retention +6 points (target +10) and 9 under uniform (target within 5). The committed test asserts guard values (overlap ≥ 22%, retention +4 and within 12 of uniform) so regressions are caught, and prints "Targets as specified: N missed" with the list in `app/build/selection-simulation.txt`.
**Files:** `data/SessionSelector.kt`, `ui/quiz/QuizViewModel.kt` (Practice config, log line); tests `SessionSelectorTest.kt` (due, cap and master-new-word tests added), `SelectionSimulation.kt`, `SelectionSimulationTest.kt`. 83 tests pass with `gradlew.bat assembleDebug testDebugUnitTest`.
- IT: (1) 3 quizzes in a row on one unit, "all words": about 2–3 of 10 repeat from the previous quiz; 2–3 never-seen; (2) a word missed yesterday (or answered right 6+ days ago) shows up in a quiz now (the log line shows `dueInPool` > 0 and `dueTaken` ≥ 2 of 10); (3) Practice: about 6 of 15 are due words when enough are due (`dueTaken` in logcat); (4) "All words" quiz across units: new words come from several units, mostly low ones; a single-unit quiz still introduces new words in order; (5) logcat tag `SessionSelector`, one line per quiz/practice start; `recent48hTaken` ≤ 4 of 10.
**Not checked:** anything on a device or emulator; real-data feel. The retention numbers come from a synthetic learner (stability model, a wrong answer cuts stability to 60%); a learner who benefits more from re-asking a missed word at once would narrow the gap.

## 2026-10-29 — Developer → Manager, IT (learning-v2 redesign: one combined score E·D·U·C, unknown-only pool fix, simulation)
Branch `feature/learning-v2`. `SessionSelector` is rewritten as the owner's 3-factor model: `weight = E·D·U·C`, sampled without replacement (Efraimidis–Spirakis). No carry-over quota, no if/else between kinds of word. **E** exposure = `1 + 300·e^(-h/6) + 2·e^(-h/96)` (h = hours since last answer; `updatedAt`, no schema change; words with no timestamp E=1). **D** = `0.15 + (1-a)`, `a=(c+1)/(c+w+2)`, ×1.5 if the last answer was wrong (`interval==1`, tied to `SrsEngine` by a test). **U** (master mode = "All words" quiz and Practice only) = `1/(1+0.1·(unit-1))` (unit 10 ≈ 0.53). **C** cooldown ×0.3 for a word right in its last two answers (`interval>=7`) with the last one < 24 h ago. Around the sampling: 20–30% of a session are never-answered words (introduced in unit/id order), and at most 50% come from words answered in the last 48 h (the cap gives way rather than return a short session). `isKnown` never excludes a word whose last answer was wrong. Removed: carry-over, mastery, due multiplier.
**Unknown-only fix:** the pool is `!isKnown || lastAnswerWrong` (`SessionSelector.unknownOnlyPool`), so a known word you just missed is still in "words I didn't know". Test: `theOnlyWordsIDidntKnowPoolKeepsAKnownWordJustAnsweredWrong`.
**Log line for the device** (logcat tag `SessionSelector`, one line per session built): `pool=<n> unknownOnly=<b> master=<b> practice=<b> recent48hInPool=<n> recent48hTaken=<n> newTaken=<n> taken=<n>`.
**Simulation** (`SelectionSimulationTest`, synthetic learner: recall `e^(-t/S)`, stability S ×(1.5+2·(1-recall)) on a right answer, ×0.6 on a wrong one, first look S=72 h·learnability; answers go through the real `SrsEngine`; 30 days × 3 sessions × 10 words, 5 seeds; table is also written to `app/build/selection-simulation.txt`). The six bands pass:
- Overlap with the previous session (30–60%): single unit 40.5% (39.7–41.5), master cold 39.8%, master with 10% of every unit practised 35.5%. Before (E = 1+4e^(-h/6)+e^(-h/72)) it was 11–17% in the same harness, matching your device numbers; the pull had to go from 4 to 300 because the practised pool is hundreds of words.
- New words per 10: 2–3 in every judged session, all runs.
- Unit 1 vs unit 10 share (master): ≥1.5× in every seed (cold: unit 10 never appears, see flag; with 10% practised: 27–85×).
- Max asks of one word in a day: 3 (violations of ">4×/day unless wrong each time": 0).
- Coverage (distinct words seen): single 59 / 112 / 164 / 208 at days 7/14/21/30; master cold 60 / 112 / 165 / 234.
- P(correct) on practised words rises: the week-1 words' chance of being right if asked a week later goes 50.7 → 54.8 → 53.5 → 53.1% (single), 49.9 → 54.4 → 53.8 → 53.2% (master cold); asserted on the seed mean, ≥ +2 points. For the 10%-practised master start the band is only "no collapse" (34.3 → 33.3%).
Constants tried (single, overlap / retention wk1→4): E short 4/6 h → 17% ; 24/6 → 27% ; 100/6 → 36% / 51→55 ; 200/6 → 39% ; 300/6 → 41% / 51→53 ; 500/6 → 42%. Long-term term 2·e^(-h/96) won over 8/96 and 12/72 (those cost overlap and retention). Chosen: short 300 / 6 h, long 2 / 96 h.
**Files:** `data/SessionSelector.kt`, `ui/quiz/QuizViewModel.kt`; tests `SessionSelectorTest.kt` (rewritten for the new model, 25 tests), `SelectionSimulation.kt` + `SelectionSimulationTest.kt` (new). 76 tests pass with `gradlew.bat assembleDebug testDebugUnitTest`.
- IT: (1) on the owner's data (Unit 1 mostly known words) do 3 quizzes in a row, "all words": count words repeated from the previous quiz, expect about 3–5 of 10, the missed ones first; never-seen words 2–3 of 10; (2) same with "only words I didn't know": a known word answered wrong in quiz 1 must be able to appear in quiz 2 (check the log line `pool=` rises by those words); (3) a word answered right twice in a row should mostly be absent from the next quiz; (4) Practice (Home red column) still opens, size 15 for a fresh user; (5) logcat `SessionSelector` shows one line per quiz/practice start with sensible numbers (recent48hTaken ≤ half of taken).
**Flags:** (a) **Caveat the simulation shows:** the repetition the owner wants costs retention. A uniform-random picker in the same learner reaches 51 → 67% (single unit) vs 51 → 53% here, because half of every session goes to words answered in the last 48 h, leaving ~2–3 slots per session for older words; the 20–30% new and 50% recent rules are the binding limits, not the constants. Your call if the owner should hear this. (b) 8 sessions a day for a week (info scenario): one word can be asked up to 6× in a day and 28 (word, day) pairs break ">4× unless wrong each time"; the band holds at 3 a day. Fixing it needs a per-day count of asks per word (not stored); not built. (c) Master mode introduces never-seen words in unit order, so on a cold start every new word comes from unit 1 until it is used up (≈ day 28 at 2.5 new per session); U only weights the practised words. Unchanged from before; say if new words should instead be spread by U across all units. (d) The 40%-practised master start (≈800 practised words, info only) has 29% overlap (28–31%): the pull dilutes as the practised pool grows. (e) Practice no longer favours due words (the due multiplier is gone). (f) The cooldown is approximated: "right twice" = `interval>=7`, and only the last answer's age is checked. Words with `updatedAt==0` (progress older than v5) get E=1 and never count as last-wrong. (g) The Practice column's number is still `getHardestWordsCount`; the hard-word SQL gate is unchanged.
**Not checked:** anything on a device or emulator; how the model feels on real data (the learner is synthetic, and the retention result depends on its assumed memory curve).

## 2026-10-25 — Developer → Manager, IT (learning-v2 retune 3: words just answered wrong come back, even known ones)
Your diagnosis was right and it explains the device numbers: quiz mode keeps `isKnown = true` on a wrong answer and Unit 1 is full of known words with long histories, so the owner's misses were excluded from carry-over. `SessionSelector.lastAnswerWrong(w)` = `updatedAt > 0 && interval == 1 && attempts > 0` (SrsEngine sets interval = 1 on every wrong answer and >= 6 on every correct one; no schema change, SrsEngine untouched). Carry-over = practised within 48 h AND (last answer wrong OR (not known AND m < 0.9)); weight = (3 if last answer wrong else 1) * max(1-m, 0.15) * exp(-hours/3). 28 selector tests pass, incl. one that ties `lastAnswerWrong` to `SrsEngine.processAnswer` (so changing SrsEngine breaks that test, not the feature) and the realistic case (Unit 1 of known 8/1-history words, the previous quiz's 5 wrong answers on known words: >= 4 of them are in the next quiz in all 200 seeds). IT: repeat the controlled right/wrong consecutive-quiz check on the owner's Unit 1: the words answered WRONG in quiz N should be in quiz N+1 (about 5 of 10 come from the previous quiz); words answered right drop out of carry-over once known/mastered. Caveats: a word's progress from before v1.1.5's `updatedAt` (updatedAt = 0) is never carry-over until it's answered again; a word needs one correct answer (interval grows) to leave carry-over, so a miss stays a candidate for 48 h unless re-answered right. Not run on a device.

## 2026-10-25 — Developer → Manager, IT (learning-v2 retune 2: carry-over decays with exp(-hours/3))
Heavy-user dilution fixed: the carry-over draw weight is now (1-m)*exp(-hours/3) (10 min ~0.95, 3 h ~0.37, 6 h ~0.14, 24 h ~0.0003); the 48 h eligibility and the 50% quota are unchanged. Note the specified /6 measured only ~3.9 words of overlap in the realistic case (60 words practised 20 h ago still unmastered, then a quiz 10 min ago), under the >= 4 target, so I simulated the draw for several scales (it reproduces IT's observation: /48 gives ~0.9) and took /3, the smallest that gives >= 4 on average and >= 3 in every run (/6 ~3.8, /4 ~4.7 with a min of 2, /3 ~4.9 with a min of 3). New tests: that exact heavy-user case over 200 seeds (>= 4 on average, >= 3 every seed, missed words preferred) and the weight curve; all 24 selector tests pass. IT: repeat the consecutive Unit-1 quizzes on the owner's data right after a long practice day: expect ~5 of each 10 to come from the previous quiz (still-unmastered words), fewer as they're answered right. Not run on a device.

## 2026-10-24 — Developer → Manager, IT (learning-v2 retune: carry-over quota, `ab97965`)
Item 1 was too weak on real data (3 quizzes overlapped 0-2 words): `SessionSelector` now reserves up to ceil(50%) of a session for words practised in the last 48 h that aren't mastered yet (accuracy < 0.9 and not known; drawn by weight (1-m)*recent), then at most 30% new words, then the rest by the existing weights (recency term raised to 1+6*recent*(1-m)); words leave carry-over at accuracy 0.9 or when known; Practice gets the same 50%. 22 selector tests pass incl. the IT scenario simulated (200-word pool with 80 old wrong-not-known words, quiz of 10: 5 wrong 5 right, next quiz 10 min later: >= 4 of those words reappear in all 200 seeds, the missed ones preferred). IT: repeat the 3-consecutive-quizzes check on Unit 1 and Unit 10: expect at least 5 of each 10-word quiz's words to come from the previous quiz while they are still unmastered, fewer as they're answered correctly (3 correct answers in quiz mode make a word known, so it drops out after about 3 right answers); new words still <= 3 of 10. Not run on a device.

## 2026-10-24 — Developer → Manager, IT (v1.1.8 `feature/learning-v2`: new selection, Practice, activity sync, Another round, the 14/15 bug)
Branch `feature/learning-v2`, from `origin/claude/charming-planck-v3dszd` @ 94d7155. Commits: `d603ee3` (1 selection) · `9a58386` (5 bug) · `61ed3e4` (2 Practice) · `96e3ae3` (4 Another round) · `55d4494` (3 activity sync). `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, all suites pass (SessionSelector 15, QuizAdvance 8, PracticeSession 5, ActivityMerge 6, SignInError 11, SyncMerge 10, SubScreenCodec 4, SrsEngine 4). **No schema change** (uses updatedAt/correct/wrong/srsScore/isKnown/nextReviewDate). **Nothing was run on a device**: the selection quality on real data, the UI and the cloud documents are unverified until IT.

**1. New selection** (`data/SessionSelector.kt`, pure, clock and Random injected). Exactly the spec: a=(c+1)/(c+w+2), m=a (min 0.85 if known), recent=exp(-hours/48) (0 if updatedAt=0), due=nextReviewDate<=now and practised, weight=(0.1+(1-m))*(1+3*recent*(1-m))*(2 if due); Efraimidis-Spirakis sampling; at most ceil(30%) brand-new words (new = no attempts, not known, srsScore 0), topped up with new ones only if there aren't enough practised words; new words come from the front of (unit, id) order (a window of ~3x what's needed, shuffled) so a unit is introduced progressively. Used for quizzes and Home shortcuts. Worked example from the tests: a word missed last hour (0 right, 1 wrong) weighs ~2.3, an old mastered one (10 right, known, 30 days ago) ~0.18: ~12x. The old `selectWordsForSession` and its 4 tests are gone; the same four properties are re-tested on the new function.
- IT: on the owner's real data, run 3 quizzes in a row on one unit: words missed in quiz 1 should mostly come back in quiz 2, less each time they're answered right; count never-seen words in a 10-question quiz: at most 3 (15 -> 5, 20 -> 6); new words should be the first ones of the lowest unit, not scattered; a unit with no practised words still works (all new); a fresh install works.

**2. Practice** (Home's red column). Label now "תרגול" / "Practice"; the number is unchanged. Tap = session on the current track across ALL units, same weighting with due multiplier 3, size = median(answers/day over the last 7 ACTIVE days from ActivityLog) x 0.6, clamped 15..40, 15 with no history (`PracticeSession`, pure; empty days are skipped, not counted as 0). The quiz title is "תרגול"/"Practice".
- IT: fresh user: 15 questions. To check adaptation (emulator): write `shared_prefs/activity_log.xml` with 7 recent days of 50 each -> 30 questions; 7 days of 100 -> 40; 7 days of 10 -> 15. Questions should span several units.
- **Flag:** the column's number is the existing `getHardestWordsCount` = words with wrongCount>0 and not known (i.e. "missed, not yet known"), NOT "due by nextReviewDate". I kept it as instructed ("its current meaning"); if you want it to show the due count, it's a one-line DAO change.

**3. Activity log in the cloud.** `users/{uid}/progress/vocab~activity-YYYY-MM-DD = {uid, app:"vocab", itemKey:"activity-YYYY-MM-DD", data:{count}, updatedAt, device:"phone"}`. Merge = max per day (`ActivityMerge`, pure, tested: counts never go down on either side, equal = nothing, zero days never uploaded, idempotent). Sync pass: pull cloud days (raise local), upload days where the phone is higher; the 3s debounced push after an answer also sends today's count. Old (>30 day) cloud days aren't re-imported. Word parsing ignores activity docs, and v1.1.7 clients ignore them too (no `srsScore`), so mixed versions are safe.
- IT (needs sign-in): answer a few words signed in: a `vocab~activity-<today>` doc appears with `data.count` equal to today's answers (and keeps rising). Raise its count in the console, restart the app: the Home chart's bar for that day grows. Lower it: the phone keeps its count and the doc is raised again on the next push. MAFAM's docs untouched.

**4. Results screen.** "חזור לתפריט / Back to Menu" -> centred "עוד סיבוב / Another round": restarts the SAME kind of session (same unit/type/length, the Home shortcut's saved type, or Practice) with a fresh selection; the top-bar back arrow and bottom bar still go home.
- IT: finish a unit quiz, a Home-shortcut quiz and a Practice: each "Another round" starts the same kind (same title) with different questions; no "Back to Menu".

**5. BUG 14 of 15 — root cause found.** Not the timer. The Next button lives in an `AnimatedVisibility` that stays clickable while it slides out after a tap, and it is recomposed with the NEW question's index: a tap in that window asked `onNext` to advance a question nobody had answered, skipping it (so my v1.1.7 index guard, which assumed a stale closure, didn't cover it). Fix: `QuizUiState.advanceFrom(expectedIndex)` now also requires that question to be answered, so answers always equal questions; `onAnswer` takes the question's index and is atomic/idempotent too (a double tap counts once; only the call that flips a question updates the SRS state). With Auto pass ON the Next button is hidden entirely (the timer is the only way forward); OFF: the button is the only way.
- IT: Auto pass ON, 15-question quiz, tap options fast and tap everywhere: no Next button is ever visible; the result screen must read correct + wrong = 15 (the owner's 12+2=14 must not recur); repeat on a 5- and 10-question quiz and on Practice. Auto pass OFF: Next present; double-tap / hammer Next on every question: never a skipped question, correct + wrong = quiz length at the end.

**Not checked:** everything on a device; how the new selection *feels* on real data (the numbers in the tests come from the formulas, and the statistical tests use seeded Randoms); the new-word window (3x) is my reading of "light randomness" and easy to tune; first sessions after upgrading from v1.1.5/6 treat most practised words as `updatedAt = 0` (recency 0, weight from accuracy and due only) until they're answered again, so the "come back fast" boost builds up from the first sessions on v1.1.8.

## 2026-10-17 — IT → Manager, Developer (export option removed, owner's direct instruction)
Pushed `c173290` on `claude/charming-planck-v3dszd` (on top of `c0c4cf4`, the v1.1.7 release). Owner asked directly, in chat, to remove the one-time "Export my data (test)" option now that v1.1.7 (cloud sync) has shipped — it existed only to get his real phone data for the Phase 3 S1/S2 testing, which is done.

**What changed:** removed the export `TextButton` from `ui/account/AccountScreen.kt` and deleted `data/DataExporter.kt` (nothing else referenced it). No version bump — that's the next release's call, not mine.

`gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, tests pass. Verified on the emulator: Account screen no longer shows the button, no crash, existing sign-in/data state unaffected.

**Not checked:** a full regression pass — this was a small, isolated removal (one button, one unused file) so I didn't re-run the whole S1/S2 test list. Flag if you want that done anyway before the next release.

## 2026-10-17 — Developer → Manager, IT (v1.1.7: sign-in no longer dead-ends; every failure says its real reason)
Pushed `673771e` on `feature/vocab-sync`. `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL; 11 new unit tests (`SignInErrorTest`) pass, all other suites still pass. **Not run on a device** (no emulator/account here).

**What changed:** (1) If the first request (`GetGoogleIdOption`, already `setFilterByAuthorizedAccounts(false)` so first-time users see all accounts) throws `NoCredentialException`, `FirebaseCloudSync.signIn` retries with `GetSignInWithGoogleOption` (the explicit "Sign in with Google" flow, which can offer to add an account). (2) The old generic "ההתחברות נכשלה. נסה שוב" is gone. `classifySignInError(Throwable)` walks the cause chain and returns one of: **no account** → "אין חשבון Google במכשיר" / "No Google account on this device", shown in a dialog with an **Add account** button (`Settings.ACTION_ADD_ACCOUNT`, Google accounts only; falls back to general Settings); **cancelled** → nothing shown (not an error); **no network** → "אין חיבור לאינטרנט" / "No internet connection"; **config** (Play services status code 10 / `DEVELOPER_ERROR`) → "שגיאת הגדרה (קוד 10)" / "Configuration error (code 10)"; **anything else** → "ההתחברות נכשלה (<exception type>[, code N])" / "Sign-in failed (<type>[, code N])" so a screenshot is enough to diagnose. (3) The text mapping is the pure `SignInMessages.text(failure, isHebrew)`; one unit test per case for both the classification and the texts.

**Test steps for IT:**
1. **Account present (owner's emulator, his account already added):** Profile → "Cloud backup" → "התחבר עם Google": the account sheet appears, pick the account, row shows the email + "התנתק". Normal sign-in, no error.
2. **Cancel:** open the picker and back out/dismiss: no toast, no dialog, row still signed-out.
3. **No account:** use a **second, clean AVD with no Google account** (never remove the owner's account from his emulator). Tap sign-in: you should first see the explicit Google sign-in sheet (the fallback); if the device still offers nothing, the dialog "אין חשבון Google במכשיר" with **הוסף חשבון** appears; the button opens the add-account screen. Also check the English text (switch the app language).
4. **No network:** airplane mode, tap sign-in: "אין חיבור לאינטרנט" (if the OS surfaces something else, the OTHER message with the exception type shows instead; send me the screenshot and I'll map it).
5. Config problem (code 10) can't be forced on a good setup; covered by the unit test only. If it ever appears for real, the message is "שגיאת הגדרה (קוד 10)" and means the SHA-1/client id doesn't match the signing key.

**Not checked:** everything on a device. In particular which exception the OS actually throws for the offline case and for a device with no account is inferred from the library's documented types and the owner's logcat (`NoCredentialException`), not observed; the classifier also matches "network" in the message and any `IOException` in the chain for the offline case.

## 2026-10-17 — Developer → Manager, IT (v1.1.7 work on `feature/vocab-sync`: merge, 2 bug fixes, S2 sync)
Branch head `6e808f3`. `gradlew.bat assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL; unit tests pass (SyncMergeTest 10, QuizAdvanceTest 4, SubScreenCodecTest 4, SrsEngineTest 8). Also built with `app/google-services.json` temporarily removed: it still builds (plugin applied only if the file exists; sync then reports "not available"). **Nothing here was run on a device or emulator** (none in this session), and sign-in needs a Google account on the device, so all of S2's runtime behavior is unverified until IT runs it. versionCode untouched (merge took 8 / 1.1.6 from the working branch).

Commits, in order: `6d25c5c` merge of origin/claude/charming-planck-v3dszd (v1.1.6, DataExporter; only build.gradle.kts conflicted, HANDOFF had none) · `2561153` status bar · `5e4497e` auto-pass · `6e808f3` S2.

**0. Merge:** done, export button included.

**(a) Status bar.** Cause: `enableEdgeToEdge()` with no args picks light/dark system-bar icons from the PHONE's night mode, and the app has only a light theme, so a phone in dark mode (the owner's Samsung) got white icons on the light yellow header. Fix: `enableEdgeToEdge(SystemBarStyle.light(transparent, transparent))` for status and nav bars, plus `android:windowLightStatusBar=true` in `themes.xml` for the first frame. One activity, one theme, no dark theme, no `values-night`, no dark-topped screen found, so every screen is covered.
- IT: put the phone/emulator in **dark mode**, open Home, Progress, Quiz, Flashcards, Profile/Settings, Account: clock/battery/signal icons must be dark and readable on every screen, also with dark mode OFF.

**(b) Auto-pass double advance.** Cause: the manual Next button is inside `AnimatedVisibility` and stays clickable while it slides out, with a stale `vm::onNext` reference, so a tap during the wait (or a second tap, or a tap as the timer fires) advanced twice. Fix: `QuizViewModel.onNext(expectedIndex)`; the pure transition is `QuizUiState.advanceFrom(expectedIndex)` and runs inside one atomic `update`, so a duplicate for an already-left question changes nothing. Both the button and the timer pass their own question's index. The timer effect was already keyed on the index (cancelled when it changes) and sits outside `AnimatedContent` (which only wraps the word card), so no old composition keeps it alive. Unit tests: two advances for the same index move exactly once; stale index returns the same state; last question completes once.
- IT: Auto pass ON; answer, then tap Next during the 1s/2.5s wait; also double-tap Next; also tap Next as the timer fires: always exactly one question forward, never a skip; last question goes to results once.

**S2 sync.** All in `data/sync/` behind `CloudSync` (signIn, signOut, push, pull, onChange); only `FirebaseCloudSync.kt` imports Firebase/Credential Manager.
- Sign-in: Credential Manager (Google ID token, `default_web_client_id` generated by the google-services plugin) → Firebase Auth. Records: `users/{uid}/progress/vocab~{stableId}` = `{uid, app:"vocab", itemKey, data:{srsScore, easeFactor, interval, nextReviewDate, correctCount, wrongCount, isKnown}, updatedAt, device:"phone"}`; queries filter `app == "vocab"` so MAFAM's `mafam~...` records are never read or written. Firestore's default offline persistence is on; pushes are batched (<=400) and not awaited past 8s (offline they stay queued by the SDK).
- **Rules (all in `SyncMerge.reconcile`, plain Kotlin, tested):** remote replaces local only if STRICTLY newer; a missing remote record never changes local; only words with progress and a stableId are uploaded; a reset (defaults, updatedAt 0) uploads nothing and any remote record beats it; equal timestamps change nothing.
- **Passes:** on app start (if signed in) and right after sign-in: pull, apply newer remote, upload newer local. After each answer: that word is pushed after a 3s debounce. `pull()` returns **null** (not empty) if the server can't be reached; a pass with null does nothing and uploads nothing, so an offline start can never upload old local state over newer remote.
- UI: one new row on the Profile/Settings screen, "גיבוי לענן / Cloud backup": "התחבר עם Google" when signed out; the account email + "התנתק" when signed in; "לא זמין בגרסה זו" if the build has no Firebase config. Nothing else changed.
- Sign-out and account delete never call Firestore. Account delete signs the phone out of the cloud account locally first, then resets progress (updatedAt 0).

Three judgment calls to confirm (all easy to change):
1. **Legacy progress is stamped at first sign-in.** Words answered before v5 have progress but `updatedAt = 0`. Uploading them at 0 would make them un-restorable (a fresh install's default is also 0, and "remote newer" is strict), so on the account's first sync from this phone those words get `updatedAt = now`, locally and in the cloud. Consequence: on that one first sync, the phone's old progress counts as "newest" against any pre-existing remote vocab record for the same word (there are none today; sync is new).
2. **First sync = pull first, then upload only what beats the cloud**, instead of a blind upload of everything. Still "upload only words with progress, never pull defaults over local data"; the extra check just stops it overwriting newer remote records (Firestore writes are blind).
3. **Account delete signs out of the cloud (locally).** Otherwise the next app start would pull the cloud copy straight back onto the freshly reset phone (merge rules would allow it: remote is newer than a reset). Delete therefore means "wipe this phone, disconnect, keep the cloud backup"; signing in again restores from the cloud.

**Test list for IT (needs a Google account on the emulator/phone; Play-services image):**
1. Profile tab: the "Cloud backup" row shows "Sign in with Google"; tap, pick the account: the row shows the email + "Sign out". Cancel the picker: nothing changes, no error. Airplane mode then tap sign-in: a short failure toast, app fine.
2. **First sign-in with real progress** (best on the owner's exported data): in the Firebase console (Firestore, `users/{uid}/progress`) the count of `vocab~...` documents equals the number of words with progress on the phone (compare with a DB query: words where any of srsScore/correct/wrong/isKnown/interval/nextReview is non-default); each doc has the expected fields, `app="vocab"`, `device="phone"`; no document for never-practised words. Local progress is unchanged (Progress screen / DB diff).
3. Answer a few quiz questions and flashcards while signed in: after ~3-5s those words' documents update (`updatedAt` increases, counts change).
4. **Two-way merge:** edit one `vocab~` doc in the console to a newer `updatedAt` and different counts, restart the app: that word on the phone takes the cloud values. Edit another doc to an OLDER `updatedAt`: the phone keeps its own and the doc gets overwritten by the phone's newer state on the next pass. A word with no doc: unchanged.
5. **Offline then reconnect:** airplane mode, answer several questions, close the app, turn the network on, reopen (sign-in persists): the answered words reach the cloud after the app-start pass. Starting the app offline must not change any local row.
6. **Sign out** keeps all local progress and all cloud docs (verify in the console). Signing in again with the same account does not duplicate or reset anything.
7. **Account delete** (Account screen): local progress resets, the cloud docs are untouched, the row shows signed-out. Sign in again: progress comes back from the cloud (call 3 above).
8. A build WITHOUT `google-services.json`: the row reads "Not available in this build" and the app behaves exactly as v1.1.6.
9. Fresh install on a second device/emulator, same Google account: sign-in restores progress from the cloud; defaults are never pushed up.
10. Regression: quizzes, flashcards, Progress counts, Home, Export button all unchanged.

**Not checked:** everything on a device (sign-in, Firestore rules, offline queueing, the merge on real data, Credential Manager with the debug key's SHA registration); whether `firebaseBom 34.0.0` / `credentials 1.5.0` / `googleid 1.1.1` are the versions you want pinned (they resolved and built); the Samsung status bar itself (verified by reasoning and the emulator-independent API, not on One UI); `onChange` (the live listener) is implemented but deliberately not used by the coordinator.

## 2026-10-16 — Developer → Manager, IT (Phase 3 S1: 2 fixes from your review)
Pushed to `feature/vocab-sync`, commit ea61a00 (on top of 99e6280). BUILD SUCCESSFUL, tests pass, androidTest compiles.

1. **MIGRATION_3_4 byte corruption, confirmed and fixed.** The `Write` tool call that originally rewrote `VocabDatabase.kt` silently re-normalized "אֲלוּמָּה" (dagesh/qamatz combining-mark order swapped) — exactly as you found. Fixed by `git checkout 74f2d89 -- VocabDatabase.kt` (restoring byte-identical original) and re-applying every S1 change via targeted `Edit` calls whose old/new strings never include the MIGRATION_3_4 block's lines — confirmed with `git diff 74f2d89 HEAD` showing zero diff on those exact lines. Grepped the rest of the S1 diff for other embedded Hebrew literals compared against pre-existing data: the only other candidate is `VocabDataLoader.trackFor`'s `"עברית"` check, which has no combining marks (plain consonants only) so it was never at risk; the asset JSON's Hebrew fields were already confirmed byte-identical in your review. I'll avoid `Write` for files with pre-existing non-ASCII literals going forward — `Edit` only touches the bytes in its own old/new strings, so it's safe by construction.
2. **Backup now retries internal storage.** `BackupManager` tries external storage first (`getExternalFilesDir`); if that's null or the copy fails, it retries into `filesDir/backups` before giving up. Only if both fail does it log loudly and proceed with the migration anyway (unchanged reasoning from the first push: `MIGRATION_4_5` is additive-only, so it doesn't itself put rows at risk even without the backup). The already-exists skip check now covers both locations, so a backup still only runs once per real install, confirmed by inspection of the logic (not a device test).

**Test list addition for IT:** when re-running the upgrade test from S1's list, also check that the backup folder's `databases/vocab_database` contains the "אֲלוּמָּה" row with its definition correctly merged (not duplicated) after the 3→4 step — that's the concrete symptom the byte corruption would have caused for anyone still on v3. Everything else from the original S1 test list stands unchanged.

S2 still on hold, as the owner said.

## 2026-10-16 — Developer → Manager, IT (Phase 3 S1 pushed: backup + Migration + stable ids, no network code)
**Pushed to `feature/vocab-sync`, branched from `claude/charming-planck-v3dszd` @ 74f2d89.** `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, all tests pass. The new instrumented `MigrationTest` compiles clean (`gradlew.bat compileDebugAndroidTestKotlin`) but **has not been run** — it needs a connected device/emulator, which this session doesn't have. **S2 (Firebase/sign-in) is deliberately not built yet** — holding on that pending a look at the Firebase project/google-services.json setup, per direct owner instruction in my session.

**What changed:**
1. **BackupManager** (new): before Room opens a DB older than v5, raw-copies `databases/vocab_database` (+ `-wal`/`-shm`) and all of `shared_prefs/` to `getExternalFilesDir("backups")/pre-v5-<timestamp>/` (USB-reachable). Skips if a `pre-v5-*` backup already exists. Runs via a direct SQLite `PRAGMA user_version` read, before `Room.databaseBuilder(...).build()` is even called — by the time Room is involved it's too late to capture pre-migration bytes.
2. **Real Migration, `fallbackToDestructiveMigration` removed.** `MIGRATION_4_5` adds `updatedAt` (epoch ms, last-write-wins clock) and `stableId` (+ a non-unique index) to `Word`/`words` — purely additive (`ADD COLUMN`, `CREATE INDEX`), never rewrites or drops a row. `exportSchema` turned on; `app/schemas/.../4.json` was generated once by temporarily reverting to the real, released v4 `Word` shape (no new fields) so there's an accurate historical "before" schema for the migration test; `5.json` is the real current one.
3. **Stable sync ids.** `tools/generate_stable_ids.py` (one-time, already run, idempotent — re-running is a safe no-op) added an `id` to every entry in the bundled asset's `flat` list: a 12-hex-char hash of `track|unit|word|definition`, with a numeric suffix on a hash collision. Checked against the real asset: 6215 entries, 0 collisions (confirmed "admit" — the duplicate-headword example from the spec — gets two distinct ids from its two different definitions). Fresh installs seed `stableId` directly from the asset; an upgrading install gets it backfilled by matching `(track, unit, word, definition)` (`VocabDataLoader.backfillStableIds`, runs automatically after migration); unmatched rows are counted and logged, **never deleted**.
4. `VocabRepository.processAnswer` sets `updatedAt = now`. `resetAllProgress` (account delete) sets it back to `0`, so a reset can never out-rank real progress in a future merge.
5. **MigrationTest** (`app/src/androidTest/.../data/MigrationTest.kt`): builds a real v4 DB with two rows of non-default progress (not just defaults), runs `MIGRATION_4_5`, asserts every field plus the row count survive untouched and the new columns land at their safe defaults (`updatedAt=0`, `stableId=NULL`).

**One judgment call to flag, explicitly, for the Manager/owner — the spec's "if the copy fails, don't migrate; log it and keep running on the old data":** I implemented the loud-logging part, but **not** a true non-migrating fallback. Room's `@Database(version=...)` is fixed at compile time, so "keep running on the old [v4] data" without migrating would need a second, parallel `@Database(version=4)` class with its own cut-down entity/DAO (no `updatedAt`/`stableId`) wired through every screen that touches `WordDao` today — a sizeable, untested subsystem for a rare I/O failure (disk full, permissions), and I judged shipping that unverified (no device to test it on) riskier than the alternative. Today: if the backup copy fails, it logs loudly (`Log.e`) and **still proceeds** with the real migration — `MIGRATION_4_5` is additive-only (`ADD COLUMN`/`CREATE INDEX`), one of the safest migration shapes in SQLite, so it doesn't itself put rows at risk even without the backup's safety net. Flagging this because it's the one place I deviated from the literal spec wording, and the owner's #1 concern here deserves an explicit yes/no rather than a silent judgment call.

**Test list for IT (S1 only — no sync/sign-in UI exists yet, nothing else changed):**
1. **Upgrade test, real data (the owner's explicit requirement):** install v1.1.5 (or whatever's on the owner's phone today), use it a bit / confirm it has real progress, then install this build over it. Before opening the app (or right after first launch), check `Android/data/com.example.psychometricvocab/files/backups/pre-v5-<timestamp>/` exists via USB/file manager, containing a `databases/vocab_database` file (plus `-wal`/`-shm` if present) and a `shared_prefs/` folder with the expected prefs files.
2. **Field-by-field equality:** before upgrading, note a handful of words' state (via Progress screen, or `adb shell` sqlite3 query on the old DB: `srsScore`, `easeFactor`, `interval`, `nextReviewDate`, `correctCount`, `wrongCount`, `isKnown`). After upgrading, confirm those same words show identical values (Progress screen / quiz behavior unaffected) — nothing migrated should change their learning state. New columns (`updatedAt`, `stableId`) aren't visible in the UI yet, but `adb shell` can confirm `stableId` got filled in (not NULL) for matched rows.
3. **Row count unchanged** after the upgrade (total word count on Home should read the same as before).
4. **Fresh install** (uninstall first, or a clean emulator): confirms the app seeds normally and isn't broken by the asset's reformatting (file size dropped from ~3.1MB to ~1.76MB — same content, more compact JSON, verified by diffing the parsed structure, not the raw bytes).
5. **Run the instrumented MigrationTest** (`gradlew.bat connectedDebugAndroidTest` or Android Studio's test runner) on the emulator or a device — I could not run this myself.
6. **A second upgrade attempt right after the first** (e.g. install v1.1.5 again over this build, or just relaunch) should NOT create a second `pre-v5-*` backup folder — confirms the skip-if-exists check.
7. Normal app use (quizzes, flashcards, account delete) still behaves exactly as before — this commit didn't touch any UI.

**What I did not check:** anything requiring a device — the actual backup-copy behavior, the migration itself running on a real v1.1.5 DB, the MigrationTest execution, and whether my judgment call on backup-failure (point above) holds up under an actual simulated failure (e.g., a full disk). All of this is compile-verified only.

## 2026-09-24 — Manager → QA (thanks, status)
Your home-redesign review (f8cdd68 per-unit SQL progress) and `qa/flashcard-double-swipe` (ead59a7) are merged into the release candidate `integration/v1.1.5` @ 9fbd3fc. HANDOFF was kept on both sides, and it builds and the tests pass locally. IT is running the full pre-release check next.
- "Words I missed" pool: **the owner decided not to touch it for now.** Please drop it from your open list.
- Auto-advance keeps running in the background: accepted as-is. You come back at most one question further on, never skipped or doubled (IT verified).

## 2026-09-24 — Manager → QA (review request 2)
Thanks for the Feature A review. It's IT-verified and waiting for the owner's merge approval.
Please review `feature/settings-auto-advance` (efce6bf, based on A): the new Settings screen, the `AppPreferences` auto-pass switch, and the auto-advance and blink logic in `QuizScreen` (a LaunchedEffect timer keyed on the question index). Bugs and performance only; push fixes to that same branch, with a HANDOFF entry there.
## 2026-09-24 — QA → Manager, IT (review of feature/home-redesign @ c4e04f0: features A + B + C)
**Verdict: no bugs found, one optimization pushed. NOT COMPILED** (Android code).
Pushed on this branch:
- **Home loaded every word of the track (~4,000 rows) just to count known/total per unit** for the unit cards' progress bars, and re-ran it on every answer while Home was subscribed. Now a single SQL aggregate: `WordDao.getUnitProgress()` (`SELECT unit, SUM(isKnown), COUNT(*) … GROUP BY unit`) returns a small `UnitProgress(unit, known, total)` list. `UnitProgress` is a plain result class, **not an entity, so no DB schema or version change.**
Reviewed, no problem found:
- **B, auto pass:** the timer is keyed on (enabled, answered, index), so leaving the quiz, answering, or pressing Next cancels it; the index check after the delay prevents double advancing; the blink only runs for the correct option after a wrong answer.
- **B, Settings:** the Profile tab is a tab root with no back arrow; system Back from Profile goes to Home (existing BackHandler); Account is pushed from Settings and returns to it.
- **A:** the 5/10/15 pill saves immediately, and older stored lengths snap to the nearest allowed value; my `effectiveUnknownOnly` fix is still in place.
- **C, activity log:** answers are counted in the single `processAnswer` funnel; pruning is cheap (≤30 keys); it's cleared with the account; the chart is read on each Home entry; LocalDate is fine at minSdk 26; day labels are mirrored by RTL.
Notes for the Manager (not changed, low):
1. **`qa/flashcard-double-swipe` is not in this branch.** Until it's merged, the double-swipe bug also double-counts that day in the new activity chart. The code merges cleanly (checked); only `agents/HANDOFF.md` conflicts, because both branches added entries at the top. Keep both.
2. With auto pass on, the timer keeps running if the app is sent to the background mid-wait, so the quiz may have advanced one question when the user returns. It's harmless; mention it only if the owner notices.
3. The "words I missed" pool vs gate mismatch from my Feature A review is still open (owner's call).
**IT test list (only this change):** Home unit cards show the same progress % as before (compare two or three units with the Progress tab: known/total per unit must match). Answer a few quiz questions in a unit, go back to Home, and that unit's bar has moved. Check both languages.

## 2026-09-24 — Developer → Manager, IT (Feature C: 3 fixes from IT's measurements)
Pushed on top of 32bb2e4, `HomeScreen.kt` only. BUILD SUCCESSFUL, tests pass.
1. **Double gap found and fixed:** header Box's `.padding(bottom = 14.dp)` + the 16dp Spacer + SectionTitle's own 8dp top padding were stacking (38dp ≈ 100px, matches IT's measurement exactly). Removed the Box's bottom padding and cut the Spacer to 8dp, so the gap is now 8dp + SectionTitle's 8dp = 16dp ≈ 42px, matching every other section gap.
2. **No peek → visible peek:** unit card width fraction `0.46f` (exactly 2 cards, no peek) → `0.41f`, so a slice of the third card shows at the reading-forward edge.
3. **Chart grown using the freed space:** bar-row height 130dp → 150dp. Math against IT's numbers: freeing 58px above + spending most of it on the chart still leaves the Hebrew margin (chart bottom → bottom bar) comfortably over the ~24dp/63px floor (96 + 58 − 53 ≈ 101px ≈ 38dp).

**Test list (just these 3):** stats card → "מסלולי למידה" gap now visually matches the other section gaps (not noticeably bigger). Unit row shows ~2 full cards plus a visible sliver of the third in both languages. Chart is a bit taller than before; still comfortable space above the bottom bar in Hebrew (the tighter of the two languages) — if IT can, a quick pixel re-measure of that margin would confirm my math without me needing another guess-and-check round.

## 2026-09-24 — Developer → Manager, IT (Feature C: sizing refinement, owner sent this directly to me)
The owner messaged me directly (with the mockup screenshot again) asking to fine-tune sizes/gaps closer to the mockup's proportions, explicitly no color or structural changes, and reiterated **no vertical scrolling**. Pushed on top of 8ad1f6d, `HomeScreen.kt` only. BUILD SUCCESSFUL, tests pass.

- **Unit card width is now responsive** (`screenWidthDp * 0.46f`) instead of a fixed 200dp guess — matches "about half the screen width" on whatever the actual device width is, rather than a number I picked assuming one screen size. This was the main thing worth fixing properly rather than guessing another fixed dp.
- Trimmed a few paddings back down slightly for a safety margin against scrolling: section gaps 18dp→16dp, unit-card internal padding 18dp→16dp (icon 40→38dp, title 17→16sp, progress bar 8→7dp), chart internal padding 20dp→16dp and bar-row height 150dp→130dp, final bottom spacer 12dp→8dp. Net effect: still noticeably bigger/more spaced than the original tight pass, just not quite as large as my previous push, to leave headroom.
- No color changes, no structural changes (still one scrolling unit row, כל המילים below it, chart below that) — sizing/spacing only, per the owner's explicit ask.

**Test list (same as before, re-check just the sizing):** Home fits one screen without scrolling in both languages; unit cards are close to half-screen width with ~2 visible + a peek of the third; gaps between stats/units/כל המילים/chart look even; chart still reads as the tallest section, not a thin sliver.

## 2026-09-24 — Developer → Manager, IT (Feature C: scale Home up to use the spare room)
Pushed on top of 469c503, `HomeScreen.kt` only. BUILD SUCCESSFUL, tests pass. Section gaps 6-10dp → 18dp (stats↔units, units↔כל המילים, כל המילים↔chart), row spacing between unit cards 10dp→16dp; unit cards scaled up (172dp→200dp wide, bigger icon circle/title, thicker progress bar, more internal padding, same icon-beside-title shape); daily-activity chart bar area 48dp→150dp tall with matching padding, so it now fills most of the remaining space down toward the bottom bar instead of leaving a big empty gap. Note: I could not use `Modifier.weight()` to make the chart dynamically fill exactly to the bottom bar — this Column has `verticalScroll` for the small-screen safety net, and Compose doesn't allow `weight` inside an unbounded-height scrollable Column (it throws at runtime) — so this is a generous fixed height instead, sized by inspection against the screenshots you sent, not a live measurement.

## 2026-09-24 — Developer → Manager, IT (Feature C: fix "כל המילים" icon placement)
Pushed on top of 64827d7 (same branch, `HomeScreen.kt` only). BUILD SUCCESSFUL, tests pass. `AllWordsCard`'s Row was `SpaceBetween` (icon pinned to the far opposite edge); now the icon and the title+count text are grouped together with `Arrangement.spacedBy(12.dp)`, icon first in code order so it sits right next to the text at the start side (right in Hebrew), matching mockup 29.png. Still solid `Yellow`, not the mockup's orange. IT check: on the "כל המילים" card, the book icon should be immediately beside "כל המילים"/"N מילים", both grouped near the start edge — not on opposite sides of the card.

## 2026-09-24 — Developer → Manager, IT (Feature C revision: one-screen Home, units back to a scrolling row)
Pushed on top of the chart addendum. `gradlew.bat assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, tests pass. No DB/schema change.

**What changed:** per the owner's follow-up (fit Home on one screen, no vertical scroll — everything else stays, "great work").
1. Units are back to **one horizontal scrolling `LazyRow`** (like pre-redesign Home), not the vertical 2-per-row grid. All units in it, natural RTL mirroring in Hebrew (unit 1 at the right, scrolls left) — same automatic mechanism as the stat columns and the activity chart, no manual reversing.
2. Unit cards redesigned: wider and shorter (172dp wide, height now just fits its content instead of a fixed taller box), icon circle **beside** the title on one line, then the progress bar, then "התחל" — about 2 cards visible with a peek of the third.
3. "כל המילים" is now its own full-width yellow card **below** the unit row (not spliced between rows like the previous vertical-grid version).
4. Order top to bottom: header (untouched) → My stats → Learning paths row → כל המילים → Daily activity chart. Tightened spacing/padding throughout (header top padding, card paddings, section-title padding, chart bar-row height 70dp→48dp) to fit a 1080x2400 screen without scrolling; the outer `Column` still has `verticalScroll` as a safety net for smaller screens, per the owner's OK.

**Files touched:** `ui/home/HomeScreen.kt` only.

**Test list for IT (short, just this revision):**
1. Both languages, on the target emulator size: Home fits on one screen, no scroll needed to see the chart at the bottom.
2. "מסלולי למידה" is one horizontal row that scrolls sideways, showing all units (5 HE / 7 EN); about 2 cards visible with a peek of the next one. In Hebrew it scrolls right-to-left (unit 1 at the right); in English left-to-right (unit 1 at the left).
3. "כל המילים" is a separate full-width card below the row, not between units.
4. Tapping a unit card or "כל המילים" still starts a quiz (Feature A's shortcut behavior, unchanged).
5. If it still doesn't fit on your actual screen size, tell me how much more to trim (which section) rather than me guessing again blind — I have no emulator access, this pass was sized by inspection only.

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
