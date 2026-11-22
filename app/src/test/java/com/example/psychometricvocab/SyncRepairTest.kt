package com.example.psychometricvocab

import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.sync.SyncMerge
import com.example.psychometricvocab.data.sync.SyncRecord
import com.example.psychometricvocab.data.sync.SyncRepair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-shot repair of the owner's cloud progress after the 22 Nov 2026 incident: the stale
 * records stamped inside the window are replaced by the real phone's own state, never applied
 * locally, and the repair refuses to run from a device that doesn't hold the real progress.
 */
class SyncRepairTest {
    private val day = 24L * 60 * 60 * 1000
    private val incident = 1_795_367_238_849L          // 2026-11-22 17:07:18Z, the bad upload
    private val before = SyncRepair.WINDOW_START - 3 * day  // the owner's real practice
    private val now = SyncRepair.WINDOW_END + 10 * 60 * 60 * 1000 // 2026-11-23 10:00Z

    private fun word(id: Int, stableId: String, updatedAt: Long, srs: Float = 0f, correct: Int = 0, wrong: Int = 0, known: Boolean = false, interval: Int = 1) =
        Word(
            id = id, track = "english", word = "w$id", definition = "d$id", unit = 1,
            srsScore = srs, interval = interval, correctCount = correct, wrongCount = wrong,
            isKnown = known, updatedAt = updatedAt, stableId = stableId
        )

    private fun record(stableId: String, updatedAt: Long, srs: Float, correct: Int, known: Boolean = true, interval: Int = 6) =
        SyncRecord(stableId, srs, 2.5f, interval, 0L, correct, 0, known, updatedAt)

    /** The real phone: real progress, stamped well before the incident. */
    private val phone = listOf(
        word(1, "a", before, srs = 5f, correct = 20, known = true, interval = 40),
        word(2, "b", before, srs = 3f, correct = 9, wrong = 1, known = true, interval = 15),
        word(3, "c", 0L)                                   // never practised on the phone
    )

    /** The cloud after the incident: stale October values stamped inside the window. */
    private val cloud = listOf(
        record("a", incident, srs = 1f, correct = 2, known = false, interval = 1),
        record("b", incident + 60_000, srs = 1f, correct = 3, known = false, interval = 6),
        record("c", incident, srs = 1f, correct = 1, known = false, interval = 6),   // stale value for a word the phone never practised
        record("d", before, srs = 2f, correct = 4)                                  // outside the window
    )

    @Test
    fun theWindowIsExactlyTheDayOf22November2026Utc() {
        assertEquals(java.time.Instant.parse("2026-11-22T00:00:00Z").toEpochMilli(), SyncRepair.WINDOW_START)
        assertEquals(java.time.Instant.parse("2026-11-23T00:00:00Z").toEpochMilli(), SyncRepair.WINDOW_END)
        assertTrue(SyncRepair.inWindow(SyncRepair.WINDOW_START))
        assertFalse("the end is exclusive", SyncRepair.inWindow(SyncRepair.WINDOW_END))
        assertFalse(SyncRepair.inWindow(SyncRepair.WINDOW_START - 1))
        assertTrue(SyncRepair.inWindow(incident))
    }

    @Test
    fun windowRecordsAreReplacedByThisPhonesStateStampedNow() {
        val fix = SyncRepair.plan(phone, cloud, now)
        assertEquals(3, fix.inWindow)
        assertEquals(listOf("a", "b", "c"), fix.uploads.map { it.stableId })
        assertTrue(fix.uploads.all { it.updatedAt == now })
        val a = fix.uploads.first { it.stableId == "a" }
        assertEquals(5f, a.srsScore, 0f)
        assertEquals(20, a.correctCount)
        assertEquals(40, a.interval)
        assertTrue(a.isKnown)
    }

    @Test
    fun aWordThePhoneNeverPractisedIsUploadedAsTheDefaultState() {
        // so the stale October value for it is cleared in the cloud
        val c = SyncRepair.plan(phone, cloud, now).uploads.first { it.stableId == "c" }
        assertEquals(0f, c.srsScore, 0f)
        assertEquals(0, c.correctCount)
        assertEquals(0, c.wrongCount)
        assertEquals(1, c.interval)
        assertFalse(c.isKnown)
    }

    @Test
    fun windowRecordsAreNeverAppliedLocallyAndNothingIsUploadedTwice() {
        val fix = SyncRepair.plan(phone, cloud, now)
        assertTrue(fix.remoteAfterRepair.none { it.stableId in setOf("a", "b", "c") && it.updatedAt != now })
        val plan = SyncMerge.reconcile(phone, fix.remoteAfterRepair, isFirstSignIn = false, now = now)
        // the stale values (srs 1, correct 2/3/1) never reach the phone; it keeps its real progress
        assertTrue(plan.localUpdates.none { it.stableId in setOf("a", "b", "c") })
        // the replacements were already uploaded by the repair: the merge must not send them again with an older stamp
        assertTrue(plan.uploads.none { it.stableId in setOf("a", "b", "c") })
    }

    @Test
    fun recordsOutsideTheWindowKeepNormalLastWriteWins() {
        val fix = SyncRepair.plan(phone, cloud, now)
        assertTrue("outside the window is kept as it is", fix.remoteAfterRepair.any { it.stableId == "d" && it.updatedAt == before })
        // d is newer in the cloud than on the phone: applied
        val phoneWithD = phone + word(4, "d", before - day, srs = 1f, correct = 1)
        val newer = SyncMerge.reconcile(phoneWithD, SyncRepair.plan(phoneWithD, cloud, now).remoteAfterRepair, false, now)
        assertEquals(listOf("d"), newer.localUpdates.map { it.stableId })
        assertEquals(4, newer.localUpdates.single().correctCount)
        // d is older in the cloud than on the phone: the phone's is uploaded
        val phoneWithNewerD = phone + word(4, "d", before + day, srs = 6f, correct = 7)
        val older = SyncMerge.reconcile(phoneWithNewerD, SyncRepair.plan(phoneWithNewerD, cloud, now).remoteAfterRepair, false, now)
        assertTrue(older.localUpdates.isEmpty())
        assertEquals(listOf("d"), older.uploads.map { it.stableId })
    }

    @Test
    fun aWindowRecordForAWordThePhoneDoesNotHaveIsKeptOutOfTheMergeButNotUploaded() {
        val fix = SyncRepair.plan(phone, cloud + record("ghost", incident, srs = 1f, correct = 1), now)
        assertEquals(4, fix.inWindow)
        assertEquals(listOf("a", "b", "c"), fix.uploads.map { it.stableId })
        assertTrue(fix.remoteAfterRepair.none { it.stableId == "ghost" })
    }

    @Test
    fun theSecondRunIsANoOp() {
        val first = SyncRepair.plan(phone, cloud, now)
        // the cloud as it is after the first run: the replacements (stamped now, outside the window)
        val cloudAfter = first.remoteAfterRepair
        val second = SyncRepair.plan(phone, cloudAfter, now + day)
        assertEquals(0, second.inWindow)
        assertTrue(second.uploads.isEmpty())
        assertEquals(cloudAfter, second.remoteAfterRepair)
    }

    @Test
    fun theDoneFlagIsPerAccount() {
        assertEquals("repair_20261122_done_abc123", SyncRepair.flagKey("abc123"))
        assertFalse(SyncRepair.flagKey("u1") == SyncRepair.flagKey("u2"))
    }

    // ---- which device may run it ----

    @Test
    fun theRealPhoneMayRepair() {
        assertTrue(SyncRepair.canRepairFrom(phone))
        // a phone that also practised some words earlier on 22 Nov is still the real one
        val practisedTodayToo = phone + (10..12).map { word(it, "t$it", incident - 3_600_000, srs = 1f, correct = 1) }
        assertTrue(SyncRepair.canRepairFrom(practisedTodayToo))
    }

    @Test
    fun theBadCopyMayNotRepairFromItself() {
        // the emulator: every progressed word was stamped with the incident's "now" by the first-sign-in rule
        val badCopy = (1..1_813).map { word(it, "s$it", incident, srs = 1f, correct = 2) } + word(5000, "never", 0L)
        assertFalse(SyncRepair.canRepairFrom(badCopy))
    }

    @Test
    fun aFreshInstallMayNotRepair() {
        // uploading its empty state would erase the stale values with zeros that then beat the real phone
        assertFalse(SyncRepair.canRepairFrom(emptyList()))
        assertFalse(SyncRepair.canRepairFrom((1..50).map { word(it, "s$it", 0L) }))
    }

    @Test
    fun aDeviceWithOnlyAFewWordsStampedInTheWindowStillCounts() {
        val mixed = (1..100).map { word(it, "s$it", if (it <= 20) incident else before, srs = 1f, correct = 1) }
        assertTrue(SyncRepair.canRepairFrom(mixed))
        val almostAllBad = (1..100).map { word(it, "s$it", if (it <= 95) incident else before, srs = 1f, correct = 1) }
        assertFalse(SyncRepair.canRepairFrom(almostAllBad))
    }
}
