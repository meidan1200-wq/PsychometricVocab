package com.example.psychometricvocab.data.sync

/**
 * Merge rule for the daily activity log (answers per day) between the phone and the cloud.
 * Plain Kotlin, unit-tested. Activity is HISTORY, so it merges by max per day (the spec's union
 * rule): a day's count only ever goes up, on either side, and nothing is ever lowered or deleted.
 * Keys are ISO dates ("2026-10-24").
 */
object ActivityMerge {
    /** [raiseLocal]: days where the cloud has more than the phone; [uploads]: days where the phone has more. */
    data class Plan(val raiseLocal: Map<String, Int>, val uploads: Map<String, Int>)

    fun reconcile(local: Map<String, Int>, remote: Map<String, Int>): Plan {
        val raise = HashMap<String, Int>()
        val upload = HashMap<String, Int>()
        for (day in local.keys + remote.keys) {
            val l = (local[day] ?: 0).coerceAtLeast(0)
            val r = (remote[day] ?: 0).coerceAtLeast(0)
            when {
                r > l -> raise[day] = r
                l > r && l > 0 -> upload[day] = l // only days with activity are uploaded
            }
        }
        return Plan(raise, upload)
    }

    /**
     * The incremental pass: [remoteChanges] holds only the days that changed on the server since
     * the cursor, so only those days and the [pendingDays] (changed here, not yet handed to the
     * cloud SDK) are compared; a local day outside both is NOT re-uploaded just because the
     * partial remote map doesn't list it.
     */
    fun reconcileIncremental(
        local: Map<String, Int>,
        remoteChanges: Map<String, Int>,
        pendingDays: Set<String>
    ): Plan {
        val relevant = remoteChanges.keys + pendingDays
        return reconcile(local.filterKeys { it in relevant }, remoteChanges)
    }
}
