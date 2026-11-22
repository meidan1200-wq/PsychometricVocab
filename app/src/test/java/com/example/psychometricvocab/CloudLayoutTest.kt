package com.example.psychometricvocab

import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.sync.CloudLayout
import com.example.psychometricvocab.data.sync.CloudLayout.Place
import com.example.psychometricvocab.data.sync.Layout
import com.example.psychometricvocab.data.sync.SourceMerge
import com.example.psychometricvocab.data.sync.SourceMerge.Origin
import com.example.psychometricvocab.data.sync.SyncCursor
import com.example.psychometricvocab.data.sync.SyncRecord
import com.example.psychometricvocab.data.sync.V2Migration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The v2 layout's pure rules: ids, members, bucket writes, days, cursors, dual-mode merging, the migration plan. */
class CloudLayoutTest {
    private fun record(id: String, updatedAt: Long = 1_000, correct: Int = 3, srs: Float = 1.5f) =
        SyncRecord(id, srs, 2.5f, 6, 1_700_000_000_000L, correct, 1, true, updatedAt)

    private fun word(id: Int, stableId: String?, track: String = "english", unit: Int = 1, updatedAt: Long = 0L, srs: Float = 0f, correct: Int = 0) =
        Word(id = id, track = track, word = "w$id", definition = "d$id", unit = unit, srsScore = srs, correctCount = correct, updatedAt = updatedAt, stableId = stableId)

    // ---- the layout switch ----

    @Test
    fun aMissingOrUnknownLayoutMeansV1() {
        assertEquals(Layout.V1, Layout.parse(null))
        assertEquals(Layout.V1, Layout.parse(""))
        assertEquals(Layout.V1, Layout.parse("v3"))
        assertEquals(Layout.V1, Layout.parse("v1"))
        assertEquals(Layout.DUAL, Layout.parse("dual"))
        assertEquals(Layout.V2, Layout.parse("v2"))
    }

    @Test
    fun eachLayoutReadsAndWritesWhatTheContractSays() {
        assertTrue(Layout.V1.readsV1 && Layout.V1.writesV1 && !Layout.V1.readsV2 && !Layout.V1.writesV2)
        assertTrue(Layout.DUAL.readsV1 && Layout.DUAL.writesV1 && Layout.DUAL.readsV2 && Layout.DUAL.writesV2)
        assertTrue(!Layout.V2.readsV1 && !Layout.V2.writesV1 && Layout.V2.readsV2 && Layout.V2.writesV2)
    }

    // ---- ids and members ----

    @Test
    fun bucketAndMemberIdsAreReadableAndPathSafe() {
        assertEquals("hebrew-u3", CloudLayout.bucketId(Place("hebrew", 3)))
        assertEquals("w3f9a1c2b4d5e", CloudLayout.memberKey("3f9a1c2b4d5e"))
        assertEquals("w3f9a1c2b4d5e_2", CloudLayout.memberKey("3f9a1c2b4d5e-2"))
        assertTrue(CloudLayout.memberKey("0123456789ab").first().isLetter())
        assertTrue(Regex("^w[0-9a-f_]{12,24}$").matches(CloudLayout.memberKey("0123456789ab-12")))
    }

    @Test
    fun aMemberRoundTripsEveryField() {
        val r = SyncRecord("ab12cd34ef56", 2.25f, 2.7f, 15, 1_795_000_123_456L, 8, 2, true, 1_795_000_000_000L)
        assertEquals(r, CloudLayout.recordOf(CloudLayout.memberFields(r)))
        assertEquals("ab12cd34ef56", CloudLayout.memberFields(r)["k"])
    }

    @Test
    fun aMemberMissingAFieldIsNeverGuessedAt() {
        val m = CloudLayout.memberFields(record("a")).toMutableMap()
        m.remove("interval")
        assertNull(CloudLayout.recordOf(m))
        assertNull(CloudLayout.recordOf(CloudLayout.memberFields(record("a")) - "k"))
    }

    @Test
    fun placementAndCapacityComeFromThePhonesOwnWordList() {
        val words = listOf(word(1, "a", "english", 1), word(2, "b", "english", 1), word(3, "c", "hebrew", 4), word(4, null))
        assertEquals(Place("hebrew", 4), CloudLayout.placement(words)["c"])
        assertNull(CloudLayout.placement(words)["zzz"])
        assertEquals(mapOf("english-u1" to 2, "hebrew-u4" to 1), CloudLayout.capacities(words))
    }

    // ---- bucket writes ----

    @Test
    fun recordsAreGroupedPerBucketAndOnlyTheChangedMembersAreWritten() {
        val words = listOf(word(1, "a", "english", 1), word(2, "b", "english", 1), word(3, "c", "hebrew", 4))
        val plan = CloudLayout.planWrites(listOf(record("a"), record("c")), CloudLayout.placement(words), CloudLayout.capacities(words))
        assertEquals(listOf("english-u1", "hebrew-u4"), plan.writes.map { it.id })
        val english = plan.writes.first { it.id == "english-u1" }
        assertEquals("english", english.track)
        assertEquals(1, english.unit)
        assertEquals("only the changed member, not the bucket's other word", setOf("wa"), english.members.keys)
        assertTrue(plan.refused.isEmpty() && plan.unplaced.isEmpty())
    }

    @Test
    fun aWordThePhoneCantPlaceGoesToTheCatchAllBucketAndIsReported() {
        val plan = CloudLayout.planWrites(listOf(record("ghost")), emptyMap(), emptyMap())
        assertEquals(listOf(CloudLayout.UNPLACED_BUCKET), plan.writes.map { it.id })
        assertEquals(listOf("ghost"), plan.unplaced)
        assertNull(plan.writes.single().track)
    }

    @Test
    fun aBucketThatCouldOutgrowTheSizeGuardIsRefusedAndNothingIsWrittenForIt() {
        val tooMany = CloudLayout.MAX_BUCKET_BYTES / CloudLayout.MEMBER_BYTES_UPPER + 1
        val plan = CloudLayout.planWrites(
            listOf(record("a"), record("c")),
            mapOf("a" to Place("english", 1), "c" to Place("hebrew", 4)),
            mapOf("english-u1" to tooMany, "hebrew-u4" to 400)
        )
        assertEquals(listOf("english-u1"), plan.refused)
        assertEquals(listOf("hebrew-u4"), plan.writes.map { it.id })
    }

    @Test
    fun theRealUnitsAreFarUnderTheGuard() {
        // the biggest real unit has 442 words
        assertTrue(CloudLayout.estimateBucketBytes(442) < CloudLayout.MAX_BUCKET_BYTES / 4)
    }

    // ---- days ----

    @Test
    fun daysGroupIntoMonthDocumentsAndBack() {
        val counts = mapOf("2026-11-01" to 12, "2026-11-22" to 40, "2026-12-03" to 7, "2026-12-04" to 0, "garbage" to 5)
        val grouped = CloudLayout.groupDays(counts)
        assertEquals(mapOf("2026-11" to mapOf("01" to 12, "22" to 40), "2026-12" to mapOf("03" to 7)), grouped)
        assertEquals(mapOf("2026-11-01" to 12, "2026-11-22" to 40, "2026-12-03" to 7), CloudLayout.flattenDays(grouped))
    }

    // ---- cursors ----

    @Test
    fun theV2CursorKeepsAFiveSecondMargin() {
        assertEquals(10_000_000L - 5_000_000L, SyncCursor.advance(null, listOf(10_000_000L), SyncCursor.V2_MARGIN_MICROS))
        // a pass that read nothing keeps the cursor; it never goes back
        assertEquals(7L, SyncCursor.advance(7L, emptyList(), SyncCursor.V2_MARGIN_MICROS))
        assertEquals(7L, SyncCursor.advance(7L, listOf(8_000_000L - 6_000_000L), SyncCursor.V2_MARGIN_MICROS))
    }

    @Test
    fun eachSourceDecidesFullOrIncrementalOnItsOwn() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        assertTrue(SyncCursor.isFull(null, now - day, now))
        assertFalse(SyncCursor.isFull(5L, now - day, now))
        assertTrue(SyncCursor.isFull(5L, now - day, now, forceFull = true))
        assertTrue("older than a week", SyncCursor.isFull(5L, now - 8 * day, now))
    }

    // ---- dual mode: one remote view out of two layouts ----

    @Test
    fun theNewerCopyWinsAndEqualOnesAreBoth() {
        val v1 = listOf(record("a", 2_000), record("b", 1_000), record("c", 5_000), record("only1", 1))
        val v2 = listOf(record("a", 1_000), record("b", 2_000), record("c", 5_000), record("only2", 1))
        val w = SourceMerge.unionWords(v1, v2).associateBy { it.record.stableId }
        assertEquals(Origin.V1, w.getValue("a").origin)
        assertEquals(Origin.V2, w.getValue("b").origin)
        assertEquals(Origin.BOTH, w.getValue("c").origin)
        assertEquals(Origin.V1, w.getValue("only1").origin)
        assertEquals(Origin.V2, w.getValue("only2").origin)
        assertEquals(5, w.size)
    }

    @Test
    fun theLargerDayCountWinsAcrossLayouts() {
        val u = SourceMerge.unionActivity(mapOf("d1" to 5, "d2" to 9, "d3" to 4), mapOf("d1" to 7, "d2" to 9, "d4" to 2))
        assertEquals(7 to Origin.V2, u["d1"])
        assertEquals(9 to Origin.BOTH, u["d2"])
        assertEquals(4 to Origin.V1, u["d3"])
        assertEquals(2 to Origin.V2, u["d4"])
    }

    @Test
    fun onlyWordsAppliedFromOneLayoutAreCopiedToTheOther() {
        val winners = SourceMerge.unionWords(
            listOf(record("fromOldClient", 9_000), record("echo", 8_000)),
            listOf(record("fromV2Only", 7_000), record("echo", 7_000))
        )
        // the pass applied "fromOldClient" (a v1-only write) and "fromV2Only"; "echo" was only read back
        val applied = listOf(
            word(1, "fromOldClient", updatedAt = 9_000, srs = 1.5f, correct = 3).copy(easeFactor = 2.5f, interval = 6, nextReviewDate = 1_700_000_000_000L, wrongCount = 1, isKnown = true),
            word(2, "fromV2Only", updatedAt = 7_000, srs = 1.5f, correct = 3).copy(easeFactor = 2.5f, interval = 6, nextReviewDate = 1_700_000_000_000L, wrongCount = 1, isKnown = true)
        )
        val fill = SourceMerge.crossFillWords(applied, winners)
        assertEquals(listOf("fromOldClient"), fill.toV2.map { it.stableId })
        assertEquals(listOf("fromV2Only"), fill.toV1.map { it.stableId })
    }

    @Test
    fun aWordTheLocalRowAlreadyHadIsNeverCopiedSoNothingEchoesBetweenLayouts() {
        val winners = SourceMerge.unionWords(listOf(record("mine", 5_000)), emptyList())
        val fill = SourceMerge.crossFillWords(emptyList(), winners)
        assertTrue(fill.toV1.isEmpty() && fill.toV2.isEmpty())
    }

    @Test
    fun raisedDaysAreCopiedToTheLayoutThatLacksThem() {
        val union = SourceMerge.unionActivity(mapOf("d1" to 9), mapOf("d2" to 4, "d3" to 6))
        val fill = SourceMerge.crossFillDays(mapOf("d1" to 9, "d2" to 4, "d3" to 99), union)
        assertEquals(mapOf("d1" to 9), fill.toV2)
        assertEquals(mapOf("d2" to 4), fill.toV1)
    }

    // ---- the migration plan and the equivalence check ----

    private val places = mapOf("a" to Place("english", 1), "b" to Place("english", 1), "c" to Place("hebrew", 2))
    private val caps = mapOf("english-u1" to 300, "hebrew-u2" to 200)

    @Test
    fun theDryRunPlanCountsBucketsSizesAndProblems() {
        val v1 = listOf(record("a"), record("b"), record("c"), record("ghost"))
        val plan = V2Migration.plan(v1, mapOf("2026-11-01" to 5, "2026-12-01" to 3, "2026-12-02" to 0), places, caps)
        assertEquals(4, plan.v1Words)
        assertEquals(2, plan.v1Days)
        assertEquals(listOf("english-u1", "hebrew-u2", CloudLayout.UNPLACED_BUCKET), plan.writes.map { it.id }.sortedBy { if (it == CloudLayout.UNPLACED_BUCKET) "z" else it })
        assertEquals(listOf("ghost"), plan.unplaced)
        assertEquals(2, plan.days.size)
        assertTrue(plan.safeToWrite)
    }

    @Test
    fun twoWordsThatWouldShareAMemberKeyBlockTheMigration() {
        val plan = V2Migration.plan(listOf(record("ab-2"), record("ab_2")), emptyMap(), emptyMap(), emptyMap())
        assertEquals(1, plan.keyCollisions.size)
        assertFalse(plan.safeToWrite)
    }

    @Test
    fun identicalV1AndV2HaveZeroDifferences() {
        val v1 = listOf(record("a"), record("b"))
        val diff = V2Migration.compare(v1, mapOf("2026-11-01" to 5), v1.reversed(), mapOf("2026-11-01" to 5))
        assertEquals(0, diff.count)
    }

    @Test
    fun everyKindOfDifferenceIsCounted() {
        val v1 = listOf(record("a"), record("b"), record("c", updatedAt = 1_000))
        val v2 = listOf(record("a"), record("c", updatedAt = 2_000), record("extra"))
        val diff = V2Migration.compare(v1, mapOf("d1" to 5, "d2" to 7), v2, mapOf("d1" to 6))
        assertEquals(listOf("b"), diff.missingInV2)
        assertEquals(listOf("extra"), diff.extraInV2)
        assertEquals("a changed timestamp is a difference", listOf("c"), diff.different)
        assertEquals(listOf("d1", "d2"), diff.daysDifferent)
        assertEquals(5, diff.count)
    }

    @Test
    fun theBackupFileHoldsEveryWordAndDayAndEscapesStrings() {
        val json = V2Migration.exportJson(listOf(record("a"), record("we\"ird\\id")), mapOf("2026-11-01" to 5), 123L)
        assertTrue(json.startsWith("{\"app\":\"vocab\""))
        assertTrue(json.contains("\"stableId\":\"a\""))
        assertTrue(json.contains("\"stableId\":\"we\\\"ird\\\\id\""))
        assertTrue(json.contains("\"2026-11-01\":5"))
        assertTrue(json.contains("\"takenAt\":123"))
        assertEquals(2, Regex("\"stableId\"").findAll(json).count())
    }
}
