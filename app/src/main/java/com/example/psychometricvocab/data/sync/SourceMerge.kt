package com.example.psychometricvocab.data.sync

import com.example.psychometricvocab.data.Word

/**
 * Pure rules for the DUAL layout, where the app reads both the flat v1 collection and the v2
 * space and writes both. The two sources are first merged into one remote view (the newer record
 * wins per word, the larger count per day), the normal reconcile then runs against that view, and
 * what only one side holds is copied across so neither layout is left behind.
 */
object SourceMerge {
    enum class Origin { V1, V2, BOTH }

    data class Winner(val record: SyncRecord, val origin: Origin)

    /**
     * One record per word: the strictly newer of the two copies; on equal timestamps the v2 copy
     * (the layout the app is moving to) with origin BOTH, since neither side is behind.
     */
    fun unionWords(v1: List<SyncRecord>, v2: List<SyncRecord>): List<Winner> {
        val v1ById = HashMap<String, SyncRecord>(v1.size * 2)
        for (r in v1) v1ById[r.stableId] = r
        val out = ArrayList<Winner>(v1.size + v2.size)
        val seen = HashSet<String>(v2.size * 2)
        for (r2 in v2) {
            seen.add(r2.stableId)
            val r1 = v1ById[r2.stableId]
            out.add(
                when {
                    r1 == null -> Winner(r2, Origin.V2)
                    r1.updatedAt > r2.updatedAt -> Winner(r1, Origin.V1)
                    r1.updatedAt < r2.updatedAt -> Winner(r2, Origin.V2)
                    else -> Winner(r2, Origin.BOTH)
                }
            )
        }
        for (r1 in v1) if (r1.stableId !in seen) out.add(Winner(r1, Origin.V1))
        return out
    }

    /** One count per day: the larger, and which side(s) held it. */
    fun unionActivity(v1: Map<String, Int>, v2: Map<String, Int>): Map<String, Pair<Int, Origin>> {
        val out = HashMap<String, Pair<Int, Origin>>()
        for (day in v1.keys + v2.keys) {
            val a = v1[day]
            val b = v2[day]
            out[day] = when {
                a == null -> b!! to Origin.V2
                b == null -> a to Origin.V1
                a > b -> a to Origin.V1
                a < b -> b to Origin.V2
                else -> b to Origin.BOTH
            }
        }
        return out
    }

    data class WordCrossFill(val toV1: List<SyncRecord>, val toV2: List<SyncRecord>)

    /**
     * After a pass: the rows it took from the cloud ([applied]) whose winning copy came from only
     * one layout are written to the other, so an old client's v1-only write reaches v2 and the
     * reverse. Only words APPLIED in this pass are copied: a word the pass merely read back (its own
     * earlier write) is not, which is what keeps two layouts from echoing a write between them forever.
     */
    fun crossFillWords(applied: List<Word>, winners: List<Winner>): WordCrossFill {
        val byId = winners.associateBy { it.record.stableId }
        val toV1 = ArrayList<SyncRecord>()
        val toV2 = ArrayList<SyncRecord>()
        for (w in applied) {
            val win = byId[w.stableId ?: continue] ?: continue
            if (w.updatedAt != win.record.updatedAt || !SyncMerge.sameProgress(w, win.record)) continue
            when (win.origin) {
                Origin.V1 -> toV2.add(win.record)
                Origin.V2 -> toV1.add(win.record)
                Origin.BOTH -> Unit
            }
        }
        return WordCrossFill(toV1, toV2)
    }

    data class DayCrossFill(val toV1: Map<String, Int>, val toV2: Map<String, Int>)

    /** The same for activity days the pass raised locally. */
    fun crossFillDays(raisedLocally: Map<String, Int>, union: Map<String, Pair<Int, Origin>>): DayCrossFill {
        val toV1 = HashMap<String, Int>()
        val toV2 = HashMap<String, Int>()
        for ((day, n) in raisedLocally) {
            val (count, origin) = union[day] ?: continue
            if (count != n) continue
            when (origin) {
                Origin.V1 -> toV2[day] = n
                Origin.V2 -> toV1[day] = n
                Origin.BOTH -> Unit
            }
        }
        return DayCrossFill(toV1, toV2)
    }
}
