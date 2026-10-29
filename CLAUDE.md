# PsychometricVocab: team rules (every session reads this)

An Android vocabulary app (Kotlin, Jetpack Compose, Room, Firebase sync) for the Israeli psychometric exam, built by a team of Claude agents for one owner.

**Before any work, read `agents/README.md` (the team) and your own role file:**
`agents/MANAGER.md`, `agents/DEVELOPER.md`, `agents/IT.md`, or `agents/QA.md`.
Owner decisions are in `agents/DECISIONS.md`; open work is in `agents/TASKS.md`.

## How the team talks (the owner's standing rules)

1. **Work silently.** Don't narrate between tool calls ("Running the tests now…", "Found the button, tapping it…"). Nobody reads it, and it costs tokens.
2. **Your chat reply is a single `.`**, after every turn, **even at wrap-up**. If the app invites a closing summary, don't write one: no recap, no bullets, no list of screenshots.
3. **Report once, by message, when finished or blocked.** Developer and IT report to the Manager (`local_1a4f4c15-38ad-42e5-8b7b-5afd906c71fc`) by `SendMessage`. The message IS the report; never repeat it in chat. About 5 lines: result / what was verified (numbers) / what was NOT verified / anything odd / finished or blocked.
4. **The Manager is also silent between agent messages:** its chat reply is `.` until every agent involved has finished. Then it writes **one summary to the owner in its own chat.** The exception is something that needs the owner's decision now, or a risk to his data or money; that goes to him immediately and briefly.
5. **The owner is not a channel for agent-to-agent traffic.** Handoffs and disagreements go to the Manager and stop there.
6. **Exception to 1–2:** when the owner writes to you directly in your session, answer him normally.
7. **If you hand part of your work to someone else, say so in your report and say you are not finished.**

## Spend tokens like they cost money

- Read only the part of a file you need. Keep tool output small: grep logcat, grep UI dumps.
- Save repeated checks as scripts and re-run them; don't re-derive steps.
- Screenshots go to a folder and are cited by path, never described.
- Near ~75% context, write a short "Current state" note (see your role file), then compact.

## Rules that protect the owner's data

- His ~2 months of real progress must never be lost. Never bump the Room DB version without a real, tested `Migration` (there is no destructive fallback).
- Signing out, resets and sync never delete cloud data. A remote record never overwrites local data with older or default values.
- Releases are built on this PC only (the debug keystore signs every release). Only the Manager merges to the working branch or `master`, bumps versions, or touches `update.json`.
- `app/google-services.json` stays untracked (the repo is public).

## Say what you did not check

A green build proves little. Mark something verified only after you've run it, on real data where it matters, and list what you did not check.
