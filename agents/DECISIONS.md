# Decisions

Important decisions, newest on top. Format: `## YYYY-MM-DD — decision` then why, and who decided.

## 2026-09-24 — The Manager does the final merge and every version
QA no longer pushes to the working branch. It pushes to the feature branch it's reviewing, or to `qa/<topic>`. The Developer pushes to `feature/<name>`. The Manager gives the final verdict, merges into `claude/charming-planck-v3dszd`, and creates every version and release (releases need the owner's approval). Decided by the owner.

## 2026-10-29 — Word-selection model (owner's design, the Manager's math)
One continuous score per word: weight = E (exposure/recency from previous sessions, persisted) × D (difficulty from the smoothed success rate; recent misses ×1.5) × U (unit importance, only in all-words/Practice: unit 1 highest, a soft decline to ~0.5 at unit 10). Sampled by probability, with no if/else tie-breaks. Balance: 20–30% new words, ≤50% from the last 48h, and a cooldown for words answered right twice within 24h. Validated by a 30-day simulated-learner test with metric bands (overlap 30–60%, new 2–3/10, rising accuracy, unit-1 ≥1.5× unit-10 in master mode). Decided by the owner.

## 2026-10-25 — Token discipline (owner's request; learned from the MAFAM team)
The silence and "." rules go at the top of every role file, including "even at wrap-up". Reports run about 5 lines. IT keeps reusable test scripts and a STATE.md outside the repo, cites screenshots by path, and compacts at ~75%. IT may move to Haiku (owner's call) with exact scripted briefs; the Manager checks its conclusions.

## 2026-10-24 — v1.1.8: learning algorithm, Practice, "another round", skip fix (owner, directly)
- Word selection: recently practiced, not-yet-mastered words come back with high probability, decreasing as accuracy rises (weight from Laplace accuracy, 48h recency decay, due boost); at most 30% new words per session, introduced in unit order. No schema change (uses updatedAt/counts). The design is the Manager's, per the owner ("your choice").
- "לחזרה" becomes "תרגול": a personal practice over all units, sized by the user's habit (median daily answers over 7 active days × 0.6, clamped 15–40; default 15).
- The activity log syncs to Firebase (per day, max-merge).
- The results screen gets "עוד סיבוב" instead of "חזור לתפריט".
- Bug: with auto pass ON, the "הבא" button is hidden (the owner's suggestion); the v1.1.7 fix didn't hold (a 15-question quiz ended with 14 answered).

## 2026-10-16 — The export build ships as v1.1.6 through OTA
Owner request (via the MAFAM manager): deliver the export build as a normal update rather than a sideload. v1.1.6 (versionCode 8) = v1.1.5 + the read-only "Export my data (test)" button; no schema change, same key. S1/S2 become v1.1.7+, and their upgrade test starts from v1.1.6. This supersedes "the next real release is versionCode 8" below.

## 2026-10-16 — Real-data copy via a one-time export build
The owner told IT directly: build a test version from v1.1.5 with an "export my data" option. He installs it on his phone, exports, and IT runs the S1 migration test on that export. Guardrails (Manager): branch `it/export-build` from 27fd377, versionCode stays 7 (versionName 1.1.5-export), same debug key, read-only export (checkpoint WAL; DB + prefs zip via the share sheet), never published via update.json or master, tested on the emulator first. **The next real release (S1/S2) is versionCode 8.**

## 2026-10-16 — Phase 3 (vocab cloud sync) approved; new reporting channel
Approved by the owner directly in the Manager's session. Extra safety requirements: (1) before the migrating build touches anything, write a full local backup of the Room DB and prefs on the phone and keep it; (2) the upgrade test from v1.1.5 uses a real copy of the owner's data, checking srsScore, interval and nextReviewDate for every word before and after; (3) the first sign-in is upload-only, for words with progress; a remote record never overwrites local data with older or default values; (4) staged: the emulator with his data first, then publish. The app looks and behaves as today, plus one sign-in row in Profile and quiet background sync.
Communication: the MAFAM manager is the owner's single channel. Agents report there (the Manager) or to the Manager (Developer, IT) once, when finished or blocked; replies in their own chat are "."; no narration. When the owner writes directly in a session, answer normally. Decided by the owner.

## 2026-10-14 — Cross-app sync with MAFAM (plan only, nothing built)
Agreed in principle with the MAFAM project manager; the owner accepted it per that manager. Firebase (Spark) + Google Sign-In for one uid across both apps; Firestore for PROGRESS only; each app has a swappable sync interface with records {uid, app, itemKey, data, updatedAt}, last-write-wins per item; the combined progress view will be a MAFAM web screen.
**Our binding safety rules (owner: "keep the data on my phone safe"):** sign-in and sync never wipe or reset local progress, and the first sign-in uploads local progress as the starting state; replace `fallbackToDestructiveMigration` with real, tested Migrations BEFORE any schema bump; the item key is a stable per-word id, never the Room id.
Spec: `C:\Users\Temp\.gemini\antigravity\scratch\mpeam-exam-sim\docs\sync-spec.md` (Phase 3 = us). Our additions, sent to MAFAM: itemKey = a stable `id` added to the bundled asset (headwords alone can collide); adding `updatedAt` to Word is the schema change that needs the real Migration; the first upload covers only words with progress; sign-out never deletes remote data, a local reset never overwrites remote progress, and remote deletion is a separate, explicitly confirmed action; register the debug-keystore SHA-1/SHA-256 in Firebase; quiz prefs and the activity log stay local.
*Update:* the Firebase project `psychometric-sync` exists (Spark; Google sign-in; Firestore rules: users/{uid}/** only for that uid), and MAFAM is live on it. Owner rule (via the MAFAM manager, in sync-spec): **the vocab app stays exactly as it is, same screens and same behaviour; only its data becomes cloud-backed. It never shows MAFAM progress.** History-type items merge by union (counts never go down); our SRS state stays last-write-wins. If we ever sync the activity log, it merges by union too.
Waiting on: the owner's approval of Phase 3 here, and where the one required sign-in control goes. No code before both, and before the owner approves the feature here.

## 2026-09-24 — Home revision: one screen, no scrolling
After seeing d37dd05: units go back to ONE horizontal scrolling row, with compact, wide cards (icon beside the title) as in the mockup. "כל המילים" is a separate bigger card below the row, then the chart. Home should fit on one screen without vertical scrolling. Decided by the owner.

## 2026-09-24 — Home redesign (Feature C), owner spec from a Gemini mockup
- Take from the mockup: one "מרכז המידע שלי" stats card (known / total / to review); "מסלולי למידה" unit cards (icon, title, progress bar, "התחל"), 2 per row, ALL units; "כל המילים" as a bigger full-width card in the middle, as in the mockup.
- Do NOT take: duplicated review text ("כרטיסיות לחזרה" plus "0 לחזרה"); the mockup's colors (stay yellow and white); the mockup's HE|EN switch (the language pill stays exactly as it is, same place); ~~the daily-activity chart~~. *Update: the owner wants the daily-activity chart. It goes below the units and shows the last 7 days from a new per-day answer counter in SharedPreferences (no schema change), starting empty after the update.*
- Remove "פעולות מהירות". Keep the Home button icon and the other tab names.
- The "לומדות" tab becomes "פרופיל" (it opens Settings). Remove the avatar button from the Progress screen.
- Owner answers: flashcard swipe direction stays; "רק מילים שלא ידעתי" isn't touched for now; the Profile tab covers Settings access.
- A, B and C ship together as v1.1.5 after QA and IT. The owner wakes QA once everything works. Decided by the owner.

## 2026-09-24 — Quiz length is a 3-option pill: 5 / 10 / 15
The owner asked the Developer directly: the slider felt fiddly, so it's a pill like the Hebrew/English toggle. Default is still 10. Decided by the owner.

## 2026-09-24 — Owner-approved feature set A/B/C (see TASKS.md)
Quiz types stay 2 ("כל המילים" / "רק מילים שלא ידעתי"); "מילים קשות" meant the same thing. The quiz length is 5–15 (default 10), never 20 ("exhausting"). The review quiz is fixed at 10 for now. The Home redesign is optional and comes after the features, as a proposal first.

## 2026-09-24 — Keep signing with the debug key; release v1.1.4
The owner chose to keep the debug key for now (not a store app yet). Progress will later be saved on a real server with user accounts, which will make a future key change painless. Until then, **every release must be built on the owner's PC** (`~/.android/debug.keystore`), or existing installs will refuse the update. Release v1.1.4 was approved by the owner.

## 2026-09-24 — Team v3: Developer role, strict approval, targeted testing, one final report
- New **Developer** (local): builds owner-approved features and UX improvements on `feature/<name>` branches, in its own worktree.
- **QA:** bugs and optimization only. It reviews Developer branches and still pushes fixes freelance.
- **IT:** tests only what changed, plus a quick launch check. A full regression runs only before a release. It works in its own worktree.
- **Manager:** asks the owner before anything that isn't a bug fix or an optimization, and reports once, after all agents finish.
- Local agents never switch branches in the shared main folder; they use worktrees.
Decided by the owner.

## 2026-09-24 — Cloud QA is a freelancer
QA may push code on its own, without the Manager's approval. Its only mission is to clean up code and fix bugs when needed, and it reports to the Manager at the end of each session. The Manager doesn't gate its work; IT still builds and tests every QA push, because the cloud can't compile Android code. Decided by the owner.

## 2026-09-23 — No token-burning poll loops; watch the branch with a shell script
Local agents watch the branch with a background shell loop (`git fetch` every 5 min, exits only when `origin/claude/charming-planck-v3dszd` moves). It costs no tokens while idle and wakes the agent only on a real commit. This replaces `/loop 5m`, which ran a full model turn every 5 minutes even when nothing had changed. *Update 2026-09-24:* the desktop app kills background shells after about 10 minutes (exit 4), so the watcher doesn't survive. Agents fetch at the start of each turn and rely on pings instead.
Why: the cloud agent can't send messages, so the repo is the only channel from it. Polling with the model costs tokens all day. Advice from the mpeam-exam-sim manager, adopted by the Manager.
Also adopted from that team: report once, when finished (no progress pings or acks); reports separate what was verified from what was assumed; whoever makes a change doesn't sign off on it; never weaken a test to make a change pass; every defect the owner finds becomes a line in the relevant role file.

## 2026-09-22 — Team restructure
A local Manager runs the team; the cloud agent becomes Cloud QA & Docs; IT keeps the local repo in sync and tests on the emulator. Decided by the owner.
