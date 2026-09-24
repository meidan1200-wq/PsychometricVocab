# Decisions

Important decisions, newest on top. Format: `## YYYY-MM-DD — decision` then why, and who decided.

## 2026-09-24 — The Manager does the final merge and every version
QA no longer pushes to the working branch. It pushes to the feature branch it's reviewing, or to `qa/<topic>`. The Developer pushes to `feature/<name>`. The Manager gives the final verdict, merges into `claude/charming-planck-v3dszd`, and creates every version and release (releases need the owner's approval). Decided by the owner.

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
