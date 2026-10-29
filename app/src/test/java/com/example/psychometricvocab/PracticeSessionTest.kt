package com.example.psychometricvocab

import com.example.psychometricvocab.data.PracticeSession
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeSessionTest {
    @Test
    fun noHistoryGivesTheDefault() {
        assertEquals(15, PracticeSession.size(emptyList()))
    }

    @Test
    fun sizeIsSixtyPercentOfTheMedianDay() {
        assertEquals(36, PracticeSession.size(listOf(50, 60, 70)))                  // median 60 -> 36
        assertEquals(27, PracticeSession.size(listOf(40, 50)))                      // even count: median 45 -> 27
        assertEquals(21, PracticeSession.size(listOf(10, 35, 200, 30, 5, 36, 40)))  // median 35 -> 21
    }

    @Test
    fun theMedianIgnoresOneHugeOrTinyDay() {
        val withOutlier = PracticeSession.size(listOf(30, 32, 31, 29, 30, 33, 900))
        val without = PracticeSession.size(listOf(30, 32, 31, 29, 30, 33, 31))
        assertEquals(without, withOutlier)
    }

    @Test
    fun sizeIsClampedTo15And40() {
        assertEquals(15, PracticeSession.size(listOf(5, 10, 8)))   // 0.6 * 8 = 5 -> 15
        assertEquals(40, PracticeSession.size(listOf(500)))        // 300 -> 40
        assertEquals(15, PracticeSession.size(listOf(25)))         // 15 exactly
        assertEquals(40, PracticeSession.size(listOf(67)))         // 40.2 -> 40
    }

    @Test
    fun recentActiveCountsTakesTheLatestActiveDaysAndSkipsEmptyOnes() {
        val byDate = mapOf(
            "2026-10-01" to 10, "2026-10-02" to 0, "2026-10-03" to 20, "2026-10-04" to 30,
            "2026-10-05" to 0, "2026-10-06" to 40, "2026-10-07" to 50, "2026-10-08" to 60,
            "2026-10-09" to 70, "2026-10-10" to 80
        )
        // the 7 most recent days that have answers: 10-10 down to 10-03; 10-01 is the 8th, dropped
        assertEquals(listOf(80, 70, 60, 50, 40, 30, 20), PracticeSession.recentActiveCounts(byDate))
        assertEquals(listOf(80, 70), PracticeSession.recentActiveCounts(byDate, maxDays = 2))
        assertEquals(emptyList<Int>(), PracticeSession.recentActiveCounts(mapOf("2026-10-01" to 0)))
    }
}
