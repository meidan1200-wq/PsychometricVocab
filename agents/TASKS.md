# Tasks

Owned by the Manager. Format: `- [ ] task — owner — status`.

- [x] Baseline audit of v1.1.3 — QA — done as a fix batch (90aa82d), see reports/2026-09-23-optimization-audit.md
- [ ] docs/ARCHITECTURE.md — QA — assigned 2026-09-23
- [x] IT connects to the Manager and reports tool versions — IT — done 2026-09-23
- [x] Baseline smoke test of v1.1.3 — IT — OK, see reports/2026-09-23-it-baseline-smoke.md
- [x] Verify QA fix batch 90aa82d — IT — OK, see reports/2026-09-24-it-qa-batch-verify.md
- [ ] UI fix batch: system Back, Home tab highlight, "כרטיסיות" label, Account in Hebrew, quiz "no idea" pre-select — Manager — done in 453d99c, IT verified OK
- [ ] OTA broken on Android 13+ (receiver NOT_EXPORTED, confirmed by IT) — Manager — fixed in 725b934, IT verified OK (full install, progress kept)
- [x] OTA: first-time install permission loses the download — Manager — f269f8b, IT verified OK. Deny-path edge case fixed in b348b91, IT verified OK
- [x] Release v1.1.4 — Manager — released 2026-09-24: GitHub release v1.1.4, master = 314c35e. IT full regression OK, upgrade from 1.1.3 keeps progress
- [x] Owner decision: keep the debug key until there's a server with user accounts (see DECISIONS)
- [ ] Localize the update dialog, toasts and Play Protect guidance (currently English only) — unassigned
- [ ] Startup: load the account profile off the main thread (8 s first splash) — unassigned
- [ ] Owner decisions: what the "Learn" tab (tab 0) should show; dedupe quiz by headword; remember language across restarts
- [ ] Flashcard memorize: one swipe once counted twice (seen once on the emulator, not reproduced in 3 retries) — QA — open; see HANDOFF 2026-09-24

## Features approved by the owner 2026-09-24 (Developer), in this order
- [ ] **A. `feature/quiz-shortcuts`**: Home unit cards start a quiz directly; per-unit remembered quiz type (2 types, default "כל המילים", persisted); quiz length selector 5–15 (default 10, persisted) in quiz settings; the "לחזרה" card starts a 10-word review quiz — Developer
- [ ] **B. `feature/settings-auto-advance`**: the avatar opens a Settings screen (Account + preferences); "auto pass" toggle (right: short pause; wrong: mark it, blink the correct answer green, about 2–3 s, then next) — Developer
- [ ] **C. Home cleanup, proposal only**: remove the quick actions that duplicate the bottom bar; calmer, fewer colors. The Developer sends design options and the owner picks before any code — Developer
- Not needed now (owner): the Learn tab's content, remembering the language, slow first launch, Hebrew update dialog.
