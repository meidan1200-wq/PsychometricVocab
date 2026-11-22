package com.example.psychometricvocab.data.sync

import com.example.psychometricvocab.data.Word

/**
 * One-shot repair of the owner's cloud progress after the 22 Nov 2026 incident, as pure
 * functions (unit-tested). An old copy of the app data (updatedAt = 0 on ~1,800 progressed words)
 * signed in, the first-sign-in rule stamped those words with "now", they beat the cloud by
 * last-write-wins, and ~1,500 stale records were uploaded over the real progress. The stale
 * records all carry an updatedAt inside [WINDOW_START, WINDOW_END). The phone that still holds the
 * real progress replaces them with its own state, stamped now, so they beat every other copy.
 *
 * This is a destructive-looking write on the owner's data, so it runs only from a device that
 * really holds the progress ([canRepairFrom]); on any other device it is skipped, because
 * "replace the cloud with what this device has" from the bad copy, or from an empty install,
 * would redo or deepen the damage.
 */
object SyncRepair {
    /** 2026-11-22T00:00:00Z (inclusive). */
    const val WINDOW_START = 1_795_305_600_000L

    /** 2026-11-23T00:00:00Z (exclusive). */
    const val WINDOW_END = 1_795_392_000_000L

    /** A device whose progressed words are at least this share "stamped in the window" is the bad copy. */
    private const val BAD_COPY_SHARE = 0.9

    fun flagKey(uid: String) = "repair_20261122_done_$uid"

    fun inWindow(updatedAt: Long) = updatedAt in WINDOW_START until WINDOW_END

    /**
     * True if this phone can be trusted as the source of the repair: it has progressed words, and
     * they are NOT (almost) all stamped inside the window. The bad copy is exactly that: every one
     * of its words was stamped with the incident's "now". A fresh install has no progress at all,
     * and uploading its empty state would erase the stale values with zeros that then beat the
     * real phone.
     */
    fun canRepairFrom(local: List<Word>): Boolean {
        val progressed = local.filter { it.stableId != null && SyncMerge.hasProgress(it) }
        if (progressed.isEmpty()) return false
        val stamped = progressed.count { inWindow(it.updatedAt) }
        return stamped.toDouble() / progressed.size < BAD_COPY_SHARE
    }

    /**
     * [remoteAfterRepair]: what the normal merge should see in place of the cloud's list: the
     * window records are gone (so a stale value is never applied locally) and this phone's
     * replacements are in (so the merge sees them as equal and doesn't upload them a second time
     * with an older stamp). [uploads]: the replacements, this phone's state for every window
     * record (progress or default) stamped [now]. [inWindow]: how many window records there were.
     */
    data class Plan(val remoteAfterRepair: List<SyncRecord>, val uploads: List<SyncRecord>, val inWindow: Int)

    fun plan(local: List<Word>, remote: List<SyncRecord>, now: Long): Plan {
        val stale = remote.filter { inWindow(it.updatedAt) }
        val localById = HashMap<String, Word>(local.size * 2)
        for (w in local) w.stableId?.let { localById[it] = w }
        // A window record whose word this phone doesn't have can't be replaced; it is still kept
        // out of the merge. Defaults are uploaded too (toRecord doesn't care about progress), so
        // the stale October values are cleared in the cloud.
        val uploads = stale.mapNotNull { r -> localById[r.stableId]?.let(SyncMerge::toRecord)?.copy(updatedAt = now) }
        return Plan(remote.filter { !inWindow(it.updatedAt) } + uploads, uploads, stale.size)
    }
}
