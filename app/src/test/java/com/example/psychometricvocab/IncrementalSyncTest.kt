package com.example.psychometricvocab

import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.sync.ActivityMerge
import com.example.psychometricvocab.data.sync.SyncCursor
import com.example.psychometricvocab.data.sync.SyncCursor.PullMode
import com.example.psychometricvocab.data.sync.SyncMerge
import com.example.psychometricvocab.data.sync.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The incremental-pull rules: when a pass reads everything and when only what changed, how the
 * cursor moves, and that a partial remote set never makes a pass re-upload or touch words it
 * didn't ask about. (The Firestore query itself is not unit-testable; see HANDOFF.)
 */
class IncrementalSyncTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    // ---- cursor: full or incremental ----

    @Test
    fun noCursorMeansAFullPull() {
        assertEquals(PullMode.Full, SyncCursor.mode(cursorMicros = null, lastFullPullAt = null, now = now))
        assertEquals(PullMode.Full, SyncCursor.mode(cursorMicros = null, lastFullPullAt = now - day, now = now))
    }

    @Test
    fun aCursorMeansAnIncrementalPullFromIt() {
        assertEquals(
            PullMode.Incremental(1_799_999_999_123_456L),
            SyncCursor.mode(1_799_999_999_123_456L, lastFullPullAt = now - 2 * day, now = now)
        )
    }

    @Test
    fun aSignInAlwaysPullsEverything() {
        assertEquals(PullMode.Full, SyncCursor.mode(5L, lastFullPullAt = now - day, now = now, forceFull = true))
    }

    @Test
    fun aCursorWithoutAFullPullOnRecordIsNotTrusted() {
        assertEquals(PullMode.Full, SyncCursor.mode(5L, lastFullPullAt = null, now = now))
    }

    @Test
    fun aLongGapSinceTheLastFullPullPullsEverythingAgain() {
        assertEquals(PullMode.Incremental(5L), SyncCursor.mode(5L, now - SyncCursor.FULL_RESYNC_AFTER_MS, now))
        assertEquals(PullMode.Full, SyncCursor.mode(5L, now - SyncCursor.FULL_RESYNC_AFTER_MS - 1, now))
    }

    @Test
    fun aFullPullDatedInTheFutureByAWrongClockIsNotTrusted() {
        assertEquals(PullMode.Full, SyncCursor.mode(5L, lastFullPullAt = now + day, now = now))
    }

    // ---- cursor: advancing ----

    @Test
    fun theCursorAdvancesToTheNewestServerTimeSeen() {
        assertEquals(900L, SyncCursor.advance(100L, listOf(300L, 900L, 500L)))
        assertEquals(900L, SyncCursor.advance(null, listOf(300L, 900L, 500L)))
    }

    @Test
    fun aPassThatReadNothingKeepsTheCursor() {
        assertEquals(100L, SyncCursor.advance(100L, emptyList()))
        assertNull(SyncCursor.advance(null, emptyList()))
    }

    @Test
    fun theCursorNeverGoesBack() {
        assertEquals(100L, SyncCursor.advance(100L, listOf(40L, 70L)))
    }

    @Test
    fun microsConvertBothWaysWithoutLosingPrecision() {
        val micros = SyncCursor.toMicros(seconds = 1_799_999_999L, nanos = 123_456_000)
        assertEquals(1_799_999_999_123_456L, micros)
        assertEquals(1_799_999_999L, SyncCursor.secondsOf(micros))
        assertEquals(123_456_000, SyncCursor.nanosOf(micros))
    }

    // ---- merge: a partial remote set ----

    private fun word(id: Int, stableId: String, updatedAt: Long = 0L, correct: Int = 0, srs: Float = 0f, known: Boolean = false) =
        Word(
            id = id, track = "english", word = "w$id", definition = "d$id", unit = 1,
            srsScore = srs, correctCount = correct, isKnown = known, updatedAt = updatedAt, stableId = stableId
        )

    private fun record(stableId: String, updatedAt: Long, correct: Int = 5) =
        SyncRecord(stableId, 2f, 2.5f, 6, 0L, correct, 0, true, updatedAt)

    @Test
    fun anIncrementalPassNeverReuploadsWordsItWasNotAskedAbout() {
        // 500 progressed words the cloud already has; the pass saw no remote changes and has nothing pending.
        val local = (1..500).map { word(it, "s$it", updatedAt = now - day, correct = 3, srs = 1f, known = true) }
        val plan = SyncMerge.reconcileIncremental(local, remoteChanges = emptyList(), pendingIds = emptySet(), now = now)
        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.localUpdates.isEmpty())
    }

    @Test
    fun aPendingWordIsUploadedEvenThoughTheRemoteSetDoesNotListIt() {
        val local = listOf(
            word(1, "a", updatedAt = now - 1000, correct = 4, srs = 1f, known = true),
            word(2, "b", updatedAt = now - day, correct = 3, srs = 1f, known = true)
        )
        val plan = SyncMerge.reconcileIncremental(local, emptyList(), pendingIds = setOf("a"), now = now)
        assertEquals(listOf("a"), plan.uploads.map { it.stableId })
    }

    @Test
    fun aChangedRemoteWordNewerThanLocalIsApplied() {
        val local = listOf(word(1, "a", updatedAt = now - day, correct = 1, srs = 1f))
        val plan = SyncMerge.reconcileIncremental(local, listOf(record("a", updatedAt = now, correct = 9)), emptySet(), now)
        assertEquals(1, plan.localUpdates.size)
        assertEquals(9, plan.localUpdates[0].correctCount)
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun aChangedRemoteWordOlderThanLocalIsNotAppliedAndLocalIsUploaded() {
        val local = listOf(word(1, "a", updatedAt = now, correct = 7, srs = 1f))
        val plan = SyncMerge.reconcileIncremental(local, listOf(record("a", updatedAt = now - day)), emptySet(), now)
        assertTrue(plan.localUpdates.isEmpty())
        assertEquals(listOf("a"), plan.uploads.map { it.stableId })
    }

    @Test
    fun aPendingWordTheCloudHasNewerIsNotOverwritten() {
        val local = listOf(word(1, "a", updatedAt = now - day, correct = 1, srs = 1f))
        val plan = SyncMerge.reconcileIncremental(local, listOf(record("a", updatedAt = now)), setOf("a"), now)
        assertTrue("last write wins per word", plan.uploads.isEmpty())
        assertEquals(1, plan.localUpdates.size)
    }

    @Test
    fun anIncrementalPassNeverInventsTimestampsForOldProgress() {
        // progress from before the updatedAt column (updatedAt = 0) is stamped only by the first FULL pass
        val local = listOf(word(1, "a", updatedAt = 0L, correct = 5, srs = 1f, known = true))
        val plan = SyncMerge.reconcileIncremental(local, emptyList(), setOf("a"), now)
        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.localUpdates.isEmpty())
    }

    // ---- merge: activity ----

    @Test
    fun anIncrementalPassOnlyComparesTheChangedAndPendingDays() {
        val local = mapOf("2026-11-01" to 30, "2026-11-02" to 40, "2026-11-03" to 12, "2026-11-04" to 8)
        val plan = ActivityMerge.reconcileIncremental(
            local,
            remoteChanges = mapOf("2026-11-02" to 55),
            pendingDays = setOf("2026-11-04")
        )
        assertEquals(mapOf("2026-11-02" to 55), plan.raiseLocal)
        assertEquals(mapOf("2026-11-04" to 8), plan.uploads)
    }

    @Test
    fun noChangesAndNothingPendingTouchesNoActivityDay() {
        val plan = ActivityMerge.reconcileIncremental(mapOf("2026-11-01" to 30), emptyMap(), emptySet())
        assertTrue(plan.raiseLocal.isEmpty())
        assertTrue(plan.uploads.isEmpty())
    }
}
