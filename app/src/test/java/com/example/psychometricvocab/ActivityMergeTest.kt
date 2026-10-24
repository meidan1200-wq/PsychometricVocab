package com.example.psychometricvocab

import com.example.psychometricvocab.data.sync.ActivityMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Activity is history: it merges by max per day, so a day's count only ever goes up. */
class ActivityMergeTest {
    @Test
    fun theHigherSideWinsPerDay() {
        val plan = ActivityMerge.reconcile(
            local = mapOf("2026-10-20" to 30, "2026-10-21" to 10),
            remote = mapOf("2026-10-20" to 12, "2026-10-21" to 25)
        )
        assertEquals(mapOf("2026-10-20" to 30), plan.uploads)     // phone has more -> upload
        assertEquals(mapOf("2026-10-21" to 25), plan.raiseLocal)  // cloud has more -> raise the phone
    }

    @Test
    fun equalCountsChangeNothing() {
        val plan = ActivityMerge.reconcile(mapOf("2026-10-20" to 7), mapOf("2026-10-20" to 7))
        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.raiseLocal.isEmpty())
    }

    @Test
    fun aDayOnlyOnThePhoneIsUploadedAndOnlyInTheCloudIsPulled() {
        val plan = ActivityMerge.reconcile(mapOf("2026-10-20" to 5), mapOf("2026-10-19" to 9))
        assertEquals(mapOf("2026-10-20" to 5), plan.uploads)
        assertEquals(mapOf("2026-10-19" to 9), plan.raiseLocal)
    }

    @Test
    fun daysWithoutActivityAreNeverUploaded() {
        val plan = ActivityMerge.reconcile(mapOf("2026-10-20" to 0), emptyMap())
        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.raiseLocal.isEmpty())
    }

    @Test
    fun nothingIsEverLowered() {
        // A smaller or missing remote value never reduces local; a missing local never reduces remote.
        val plan = ActivityMerge.reconcile(
            local = mapOf("2026-10-20" to 40),
            remote = mapOf("2026-10-20" to 3, "2026-10-18" to 8)
        )
        assertTrue("local 40 must not be lowered to 3", plan.raiseLocal.keys == setOf("2026-10-18"))
        assertEquals(40, plan.uploads["2026-10-20"])
        assertTrue("the cloud's 8 for 10-18 is never overwritten by an upload", "2026-10-18" !in plan.uploads)
    }

    @Test
    fun mergingTwiceIsStable() {
        val local = mapOf("2026-10-20" to 30)
        val remote = mapOf("2026-10-20" to 12, "2026-10-21" to 25)
        val first = ActivityMerge.reconcile(local, remote)
        val newLocal = local + first.raiseLocal
        val newRemote = remote + first.uploads
        val second = ActivityMerge.reconcile(newLocal, newRemote)
        assertTrue(second.raiseLocal.isEmpty())
        assertTrue(second.uploads.isEmpty())
    }
}
