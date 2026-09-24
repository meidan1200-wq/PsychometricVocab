# Tasks

Owned by the Manager. Format: `- [ ] task — owner — status`.

- [x] Baseline audit of v1.1.3 — QA — done as a fix batch (90aa82d), see reports/2026-09-23-optimization-audit.md
- [ ] docs/ARCHITECTURE.md — QA — assigned 2026-09-23
- [x] IT connects to the Manager and reports tool versions — IT — done 2026-09-23
- [x] Baseline smoke test of v1.1.3 — IT — OK, see reports/2026-09-23-it-baseline-smoke.md
- [x] Verify QA fix batch 90aa82d — IT — OK, see reports/2026-09-24-it-qa-batch-verify.md
- [ ] UI fix batch: system Back, Home tab highlight, "כרטיסיות" label, Account in Hebrew, quiz "no idea" pre-select — Manager — done in 453d99c, IT verified OK
- [ ] OTA broken on Android 13+ (receiver NOT_EXPORTED, confirmed by IT) — Manager — fixed in 725b934, IT verified OK (full install, progress kept)
- [ ] OTA: first-time install permission loses the download — Manager — fixed, waiting for IT to verify
- [ ] Release v1.1.4 (all fixes so far) — Manager — needs owner approval
- [ ] Owner decision: the release APK is signed with the debug key
- [ ] Localize the update dialog, toasts and Play Protect guidance (currently English only) — unassigned
- [ ] Startup: load the account profile off the main thread (8 s first splash) — unassigned
- [ ] Owner decisions: what the "Learn" tab (tab 0) should show; dedupe quiz by headword; remember language across restarts
