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

    private fun runBaseline(scenario: Scenario) = seeds.map { SelectionSimulation.run(scenario, it, SelectionSimulation.uniformBaseline()) }

    private fun assertBands(results: List<SelectionSimulation.Result>, baseline: List<SelectionSimulation.Result>) {
        for (r in results) {
            val v = SelectionSimulation.violations(r, targets = SelectionSimulation.GUARD)
            assertTrue("${r.scenario.name}, seed ${r.seed}: ${v.joinToString("; ")}", v.isEmpty())
        }
        val retention = SelectionSimulation.retentionViolation(results, baseline, SelectionSimulation.GUARD)
        assertTrue(retention ?: "", retention == null)
    }

    @Test
    fun allBandsHoldAndTheTableIsPrinted() {
        val singleScenario = SelectionSimulation.singleUnit()
        val coldScenario = SelectionSimulation.masterCold()
        val warmScenario = SelectionSimulation.masterWarm()
        val single = runAll(singleScenario)
        val masterCold = runAll(coldScenario)
        val masterWarm = runAll(warmScenario)
        val singleBase = runBaseline(singleScenario)
        val coldBase = runBaseline(coldScenario)
        val warmBase = runBaseline(warmScenario)
        val info = runAll(SelectionSimulation.masterMature()) + runAll(SelectionSimulation.heavyWeek())
        val table = SelectionSimulation.table(
            single + masterCold + masterWarm + info,
            mapOf(singleScenario.name to singleBase, coldScenario.name to coldBase, warmScenario.name to warmBase)
        )
        val missed = listOf(single to singleBase, masterCold to coldBase, masterWarm to warmBase).flatMap { (rs, base) ->
            rs.flatMap { SelectionSimulation.violations(it).map { v -> "${it.scenario.name}, seed ${it.seed}: $v" } } +
                listOfNotNull(SelectionSimulation.retentionViolation(rs, base))
        }
        val report = table + "\nTargets as specified (overlap >= 30%, retention +10 points and within 5 of uniform): " +
            (if (missed.isEmpty()) "all met\n" else "${missed.size} missed\n" + missed.joinToString("\n") { "  - $it" } + "\n")
        println(report)
        File("build").mkdirs()
        File("build/selection-simulation.txt").writeText(report)
        assertBands(single, singleBase)
        assertBands(masterCold, coldBase)
        assertBands(masterWarm, warmBase)
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
