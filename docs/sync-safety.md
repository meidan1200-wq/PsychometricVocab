# Sync safety: lessons and hard rules (read before touching sync or the owner's devices)

Every session that touches `data/sync/`, Firestore, or the owner's phone or emulator reads this first.
It records real incidents, so the rules survive context compaction.

## Incident 2026-11-22: stale copy overwrote the cloud
**What happened**
- The emulator held an old copy of the owner's data from Oct 16, restored from an export, with `updatedAt = 0` on 1,813 progressed words.
- The owner signed the emulator in to test a sync build.
- `SyncMerge.reconcile`'s first-sign-in rule stamped every legacy (`updatedAt = 0`) word with "now". Those copies then beat the cloud by last-write-wins, and ~1,500 stale records were uploaded over the real progress.
- The emulator also uploaded ~84 words of old IT test answers from Oct 24–29, because their real timestamps were newer than the phone's.

**Why it was not lost**
- The phone was not opened online afterwards. Its v1.1.9 does a full pull at start and would have taken the stale values.

**Repair (v1.1.10, `SyncRepair`)**
- The fix: a legacy stamp applies only when the cloud has no record of that word.
- A one-shot repair on the phone replaced every cloud record dated 2026-11-22 UTC with the phone's state (1,848 replaced).
- The 84 test-answer words were set back by hand from a USB backup of the phone (`run-as`). Result: 0 differences from the backup over 6,215 words.

**Damage left**
- The daily activity counts on ~18–21 days are slightly raised, because activity merges by max. They age out after 30 days.
- 9 words with no progress still carry test values in the cloud. The phone ignores them because its copy is newer.

**Second slip, same day**
- `cat src > databases/vocab_database` truncated the phone's DB to 0 bytes. The source path was wrong: Git Bash rewrote `/data/local/tmp` into a Windows path.
- The app was closed, and the file was restored within a minute.

## Hard rules
1. **Never sign a test device in to the owner's account** if it holds a copy of his data that is older than his phone's. Use a test account, or restore the device from a fresh copy of the phone first.
2. **A device holding a stale copy stays offline** (`svc wifi disable`, `svc data disable`). Today that is emulator-5554, until it's restored from a new phone backup.
3. **Back up before any sync test or device write**: the app's `databases` + `shared_prefs` via `run-as tar`. Check the integrity, the row count and the progressed count.
4. **Nothing invents a timestamp that can beat a cloud record.** Any "stamp with now" must be limited to words the cloud has no record for.
5. **Upgrade the phone around a sync bug without opening the old build online.** Use airplane mode plus `adb install -r` over USB, or a browser download. Never use the in-app updater, which opens the old build.
6. **Writing a file on the device:**
   - Push to `/data/local/tmp` with `MSYS_NO_PATHCONV=1` set.
   - Check the pushed file exists before any `> target` redirect; the redirect truncates the target first.
   - Compare the SHA-256 of the result with the source.
7. **After any repair, prove it on real data**: diff the phone's DB with the backup per `stableId`, and read the `SyncCoordinator` log line.
8. **Sign-out, resets and sync never delete cloud data.** Activity counts only go up (max-merge), so a wrong count can't be corrected by sync. Don't let test answers reach the owner's account.

## Backups (outside the repo, never committed)
- `C:\Users\Temp\Documents\owner-data-backup-2026-11-22\`: the phone before the repair. Two identical tars, plus an unpacked copy.
- `C:\Users\Temp\Documents\owner-data-backup-2026-10-16\`: the old export. **Stale. Never restore it to a signed-in device.**
