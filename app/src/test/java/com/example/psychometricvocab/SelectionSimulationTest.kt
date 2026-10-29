package com.example.psychometricvocab

import com.example.psychometricvocab.SelectionSimulation.Scenario
import com.example.psychometricvocab.data.SessionSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Long-run behaviour of SessionSelector against a synthetic learner (see SelectionSimulation):
 * 30 days x 3 sessions x 10 words, 5 seeds each, in a single 208-word unit and in master mode
 * (10 units x 200). The six bands the owner asked for are asserted per seed; the metric table is
 * printed and also written to build/selection-simulation.txt for HANDOFF.
 */
class SelectionSimulationTest {
    private val seeds = 1..5

    private fun runAll(scenario: Scenario) = seeds.map { SelectionSimulation.run(scenario, it) }

    private fun assertBands(results: List<SelectionSimulation.Result>) {
        for (r in results) {
            val v = SelectionSimulation.violations(r)
            assertTrue("${r.scenario.name}, seed ${r.seed}: ${v.joinToString("; ")}", v.isEmpty())
        }
        val rise = SelectionSimulation.riseViolation(results)
        assertTrue(rise ?: "", rise == null)
    }

    @Test
    fun allBandsHoldAndTheTableIsPrinted() {
        val single = runAll(SelectionSimulation.singleUnit())
        val masterCold = runAll(SelectionSimulation.masterCold())
        val masterWarm = runAll(SelectionSimulation.masterWarm())
        val info = runAll(SelectionSimulation.masterMature()) + runAll(SelectionSimulation.heavyWeek())
        val table = SelectionSimulation.table(single + masterCold + masterWarm + info)
        println(table)
        File("build").mkdirs()
        File("build/selection-simulation.txt").writeText(table)
        assertBands(single)
        assertBands(masterCold)
        assertBands(masterWarm)
    }

    @Test
    fun fiveSeedsGiveDifferentRunsButTheSameSeedRepeats() {
        val a = SelectionSimulation.run(SelectionSimulation.singleUnit(), 1)
        val b = SelectionSimulation.run(SelectionSimulation.singleUnit(), 1)
        val c = SelectionSimulation.run(SelectionSimulation.singleUnit(), 2)
        assertEquals(a.overlap, b.overlap, 0.0)
        assertEquals(a.coverageByDay, b.coverageByDay)
        assertTrue(a.coverageByDay != c.coverageByDay || a.overlap != c.overlap)
    }

    @Test
    fun overlapComesFromTheRecencyPullAndNotFromSomethingIncidental() {
        // A guard on the harness itself: with the recency pull switched off the overlap band fails.
        val noPull = SessionSelector.Config(shortBoost = 0.0, longBoost = 0.0)
        val r = SelectionSimulation.run(SelectionSimulation.singleUnit(), 1, noPull)
        assertTrue("without exposure pull overlap should be low: ${r.overlap}", r.overlap < 0.30)
    }
}
