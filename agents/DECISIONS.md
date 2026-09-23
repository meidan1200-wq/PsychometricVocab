# Decisions

Important decisions, newest on top. Format: `## YYYY-MM-DD — decision` then why, and who decided.

## 2026-09-24 — Cloud QA is a freelancer
QA may push code on its own, without the Manager's approval. Its only mission is to clean up code and fix bugs when needed, and it reports to the Manager at the end of each session. The Manager doesn't gate its work; IT still builds and tests every QA push, because the cloud can't compile Android code. Decided by the owner.

## 2026-09-23 — No token-burning poll loops; watch the branch with a shell script
Local agents watch the branch with a background shell loop (`git fetch` every 5 min, exits only when `origin/claude/charming-planck-v3dszd` moves). It costs no tokens while idle and wakes the agent only on a real commit. This replaces `/loop 5m`, which ran a full model turn every 5 minutes even when nothing had changed.
Why: the cloud agent can't send messages, so the repo is the only channel from it. Polling with the model costs tokens all day. Advice from the mpeam-exam-sim manager, adopted by the Manager.
Also adopted from that team: report once, when finished (no progress pings or acks); reports separate what was verified from what was assumed; whoever makes a change doesn't sign off on it; never weaken a test to make a change pass; every defect the owner finds becomes a line in the relevant role file.

## 2026-09-22 — Team restructure
A local Manager runs the team; the cloud agent becomes Cloud QA & Docs; IT keeps the local repo in sync and tests on the emulator. Decided by the owner.
