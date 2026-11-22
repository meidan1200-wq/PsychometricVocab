package com.example.psychometricvocab.data.sync

import com.example.psychometricvocab.data.Word

/**
 * One word's synced progress, as stored remotely at users/{uid}/progress/vocab~{stableId}
 * (data = the progress fields, plus updatedAt). Plain Kotlin: no Firebase types, so all of the
 * merge/upload rules below are unit-testable on the JVM.
 */
data class SyncRecord(
    val stableId: String,
    val srsScore: Float,
    val easeFactor: Float,
    val interval: Int,
    val nextReviewDate: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val isKnown: Boolean,
    val updatedAt: Long
)

/**
 * The sync rules, as pure functions. The owner's #1 concern is that local progress is never
 * lost, so every rule here errs toward "change nothing":
 *  - only words WITH progress are ever uploaded (a default word has nothing worth storing);
 *  - a remote record replaces local state ONLY if it is strictly newer (last-write-wins per word);
 *  - a word with no remote record is never touched locally, and a remote record with no local
 *    word (or no stableId) is ignored;
 *  - a reset (defaults, updatedAt = 0) can therefore never beat real progress on either side:
 *    it has no progress to upload, and any remote record is newer than it.
 */
object SyncMerge {
    // Mirror the defaults in Word / WordDao.resetAllProgress.
    private const val DEFAULT_EASE = 2.5f
    private const val DEFAULT_INTERVAL = 1

    /** True if the word's SRS state differs from a never-practised word. */
    fun hasProgress(w: Word): Boolean =
        w.srsScore != 0f || w.easeFactor != DEFAULT_EASE || w.interval != DEFAULT_INTERVAL ||
            w.nextReviewDate != 0L || w.correctCount != 0 || w.wrongCount != 0 || w.isKnown

    fun toRecord(w: Word): SyncRecord? {
        val id = w.stableId ?: return null
        return SyncRecord(
            stableId = id,
            srsScore = w.srsScore,
            easeFactor = w.easeFactor,
            interval = w.interval,
            nextReviewDate = w.nextReviewDate,
            correctCount = w.correctCount,
            wrongCount = w.wrongCount,
            isKnown = w.isKnown,
            updatedAt = w.updatedAt
        )
    }

    /** Remote wins only when strictly newer; a tie keeps the local copy. */
    fun shouldApplyRemote(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
        remoteUpdatedAt > localUpdatedAt

    /** What one sync pass should do: rewrite these local rows, upload these records. */
    data class Plan(val localUpdates: List<Word>, val uploads: List<SyncRecord>)

    /**
     * One reconcile pass between the local words and the full set of remote records.
     *
     * Per local word with a stableId:
     *  - remote exists and is strictly newer → take the remote progress (local row updated);
     *  - otherwise, if the word has progress and a real timestamp, and the remote is missing or
     *    older → upload it. (A tie, or no progress, uploads nothing.)
     * Words with no remote record are never changed locally.
     *
     * [isFirstSignIn]: the account's first-ever sync from this phone. Progress that predates the
     * v5 `updatedAt` column (progress, but updatedAt = 0) is stamped with [now] first, so it is
     * uploadable and a fresh install restoring from the cloud can later see it as "newer than
     * default". This is the only place a local timestamp is invented.
     */
    fun reconcile(local: List<Word>, remote: List<SyncRecord>, isFirstSignIn: Boolean, now: Long): Plan {
        val remoteById = HashMap<String, SyncRecord>(remote.size * 2)
        for (r in remote) remoteById[r.stableId] = r
        val updates = ArrayList<Word>()
        val uploads = ArrayList<SyncRecord>()
        for (w in local) {
            val id = w.stableId ?: continue
            var cur = w
            if (isFirstSignIn && cur.updatedAt == 0L && hasProgress(cur)) cur = cur.copy(updatedAt = now)
            val r = remoteById[id]
            if (r != null && shouldApplyRemote(cur.updatedAt, r.updatedAt)) {
                cur = cur.copy(
                    srsScore = r.srsScore,
                    easeFactor = r.easeFactor,
                    interval = r.interval,
                    nextReviewDate = r.nextReviewDate,
                    correctCount = r.correctCount,
                    wrongCount = r.wrongCount,
                    isKnown = r.isKnown,
                    updatedAt = r.updatedAt
                )
            } else if (hasProgress(cur) && cur.updatedAt > 0L && (r == null || cur.updatedAt > r.updatedAt)) {
                toRecord(cur)?.let { uploads.add(it) }
            }
            if (cur != w) updates.add(cur)
        }
        return Plan(updates, uploads)
    }

    /**
     * One INCREMENTAL pass: [remoteChanges] are only the records that changed on the server since
     * the cursor, not the whole cloud, so "no remote record" no longer means "the cloud lacks
     * this word" and must not make every progressed word upload again. The pass therefore
     * considers only the local words that appear in [remoteChanges] (apply if strictly newer,
     * else upload if the phone is newer) and the ones in [pendingIds] (changed here and not yet
     * handed to the cloud SDK: upload unless the cloud copy is newer). Same per-word rules as
     * [reconcile]; every other word is left alone.
     */
    fun reconcileIncremental(
        local: List<Word>,
        remoteChanges: List<SyncRecord>,
        pendingIds: Set<String>,
        now: Long
    ): Plan {
        val relevant = HashSet<String>(pendingIds)
        for (r in remoteChanges) relevant.add(r.stableId)
        return reconcile(local.filter { it.stableId in relevant }, remoteChanges, isFirstSignIn = false, now = now)
    }
}
