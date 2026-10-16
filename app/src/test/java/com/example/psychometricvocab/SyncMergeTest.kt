package com.example.psychometricvocab

import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.sync.SyncMerge
import com.example.psychometricvocab.data.sync.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cloud-sync rules (Phase 3). The owner's #1 concern: local progress is never lost, so
 * every case here is about "what must NOT change".
 */
class SyncMergeTest {
    private val now = 1_800_000_000_000L

    private fun word(
        id: Int, stableId: String?, srs: Float = 0f, correct: Int = 0, wrong: Int = 0,
        known: Boolean = false, interval: Int = 1, updatedAt: Long = 0L
    ) = Word(
        id = id, track = "english", word = "w$id", definition = "d$id", unit = 1,
        srsScore = srs, interval = interval, correctCount = correct, wrongCount = wrong,
        isKnown = known, updatedAt = updatedAt, stableId = stableId
    )

    private fun record(
        stableId: String, srs: Float, updatedAt: Long, correct: Int = 3, wrong: Int = 0,
        known: Boolean = true, interval: Int = 6
    ) = SyncRecord(stableId, srs, 2.5f, interval, 1_700_000_000_000L, correct, wrong, known, updatedAt)

    @Test
    fun firstUploadSendsOnlyWordsWithProgress() {
        val locals = listOf(
            word(1, "a", srs = 1f, correct = 2, updatedAt = 100),   // progress -> upload
            word(2, "b"),                                            // never practised -> not uploaded
            word(3, "c", wrong = 1, updatedAt = 200),               // progress -> upload
            word(4, null, srs = 1f, correct = 1, updatedAt = 300)   // no stableId -> never synced
        )
        val plan = SyncMerge.reconcile(locals, remote = emptyList(), isFirstSignIn = true, now = now)
        assertEquals(listOf("a", "c"), plan.uploads.map { it.stableId })
        assertTrue("first upload with an empty cloud must not change any local row", plan.localUpdates.isEmpty())
    }

    @Test
    fun firstUploadStampsProgressThatPredatesUpdatedAt() {
        // Progress from before the v5 updatedAt column: updatedAt = 0 but real progress.
        val legacy = word(1, "a", srs = 2f, correct = 3, known = true, updatedAt = 0)
        val plan = SyncMerge.reconcile(listOf(legacy), emptyList(), isFirstSignIn = true, now = now)
        assertEquals(now, plan.uploads.single().updatedAt)
        assertEquals("the phone is stamped too, so both sides agree", now, plan.localUpdates.single().updatedAt)
        // progress itself is untouched by the stamp
        assertEquals(2f, plan.localUpdates.single().srsScore, 0f)
    }

    @Test
    fun legacyProgressIsNotStampedOnLaterPasses() {
        val legacy = word(1, "a", srs = 2f, correct = 3, updatedAt = 0)
        val plan = SyncMerge.reconcile(listOf(legacy), emptyList(), isFirstSignIn = false, now = now)
        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.localUpdates.isEmpty())
    }

    @Test
    fun olderRemoteIsIgnoredAndLocalIsUploaded() {
        val local = word(1, "a", srs = 3f, correct = 5, known = true, updatedAt = 2_000)
        val remote = record("a", srs = 1f, updatedAt = 1_000, correct = 1, known = false)
        val plan = SyncMerge.reconcile(listOf(local), listOf(remote), isFirstSignIn = false, now = now)
        assertTrue("older remote must not touch the local word", plan.localUpdates.isEmpty())
        assertEquals(2_000L, plan.uploads.single().updatedAt)
        assertEquals(3f, plan.uploads.single().srsScore, 0f)
    }

    @Test
    fun newerRemoteIsApplied() {
        val local = word(1, "a", srs = 1f, correct = 1, updatedAt = 1_000)
        val remote = record("a", srs = 4f, updatedAt = 2_000, correct = 9, wrong = 2, known = true, interval = 14)
        val plan = SyncMerge.reconcile(listOf(local), listOf(remote), isFirstSignIn = false, now = now)
        val applied = plan.localUpdates.single()
        assertEquals(4f, applied.srsScore, 0f)
        assertEquals(9, applied.correctCount)
        assertEquals(2, applied.wrongCount)
        assertEquals(14, applied.interval)
        assertTrue(applied.isKnown)
        assertEquals(2_000L, applied.updatedAt)
        assertEquals("identity fields are untouched", local.id, applied.id)
        assertEquals(local.word, applied.word)
        assertTrue("applying remote must not echo it back", plan.uploads.isEmpty())
    }

    @Test
    fun equalTimestampChangesNothing() {
        val local = word(1, "a", srs = 1f, correct = 1, updatedAt = 1_000)
        val remote = record("a", srs = 9f, updatedAt = 1_000)
        val plan = SyncMerge.reconcile(listOf(local), listOf(remote), isFirstSignIn = false, now = now)
        assertTrue(plan.localUpdates.isEmpty())
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun missingRemoteRecordNeverChangesLocal() {
        // The cloud has other words but nothing for "a": local "a" stays exactly as it is.
        val local = word(1, "a", srs = 2f, correct = 4, updatedAt = 5_000)
        val unrelated = record("zzz", srs = 1f, updatedAt = 9_000)
        val plan = SyncMerge.reconcile(listOf(local), listOf(unrelated), isFirstSignIn = false, now = now)
        assertTrue(plan.localUpdates.isEmpty())
        assertEquals(listOf("a"), plan.uploads.map { it.stableId })
    }

    @Test
    fun remoteRecordWithNoLocalWordIsIgnored() {
        val plan = SyncMerge.reconcile(emptyList(), listOf(record("ghost", 1f, 1_000)), false, now)
        assertTrue(plan.localUpdates.isEmpty())
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun resetNeverOverwritesNewerRemote() {
        // After "delete account": defaults, updatedAt = 0. It has no progress to upload, so it can
        // never overwrite the cloud; and the cloud's newer record would win over it on a pull.
        val reset = word(1, "a", updatedAt = 0)
        val remote = record("a", srs = 3f, updatedAt = 4_000)
        val plan = SyncMerge.reconcile(listOf(reset), listOf(remote), isFirstSignIn = false, now = now)
        assertTrue("a reset must upload nothing", plan.uploads.isEmpty())
        assertEquals(4_000L, plan.localUpdates.single().updatedAt)
        assertFalse(SyncMerge.hasProgress(reset))
    }

    @Test
    fun resetWithNoRemoteUploadsNothing() {
        val reset = word(1, "a", updatedAt = 0)
        val plan = SyncMerge.reconcile(listOf(reset), emptyList(), isFirstSignIn = true, now = now)
        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.localUpdates.isEmpty())
    }
}
