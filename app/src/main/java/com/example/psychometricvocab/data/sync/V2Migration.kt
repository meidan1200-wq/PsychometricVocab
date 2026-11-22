package com.example.psychometricvocab.data.sync

import com.example.psychometricvocab.data.sync.CloudLayout.BucketWrite
import com.example.psychometricvocab.data.sync.CloudLayout.Place

/**
 * The migration from the flat v1 collection to the v2 layout, as pure functions (unit-tested):
 * the dry-run plan and the equivalence check. Nothing here touches the cloud; VocabMigration
 * does the reads and, only on an explicit confirmation, the writes.
 */
object V2Migration {
    data class BucketStat(val id: String, val words: Int, val estimatedBytes: Long, val capacity: Int)

    data class Plan(
        val v1Words: Int,
        val v1Days: Int,
        /** Every member of every bucket, ready to be written beside v1. */
        val writes: List<BucketWrite>,
        val days: Map<String, Map<String, Int>>,
        val stats: List<BucketStat>,
        /** v1 records whose word this phone's list can't place in a track and unit. */
        val unplaced: List<String>,
        /** Two stableIds that would share one member key (expected none). */
        val keyCollisions: List<String>,
        /** Buckets over the size guard (expected none). */
        val refused: List<String>,
        /** Words whose member key breaks the rule the cloud can not check (expected none). */
        val invalid: List<String> = emptyList()
    ) {
        /** True if the plan can be written as it stands. */
        val safeToWrite get() = keyCollisions.isEmpty() && refused.isEmpty() && invalid.isEmpty()
    }

    fun plan(
        v1Words: List<SyncRecord>,
        v1Activity: Map<String, Int>,
        placeOf: Map<String, Place>,
        capacity: Map<String, Int>
    ): Plan {
        val owners = HashMap<String, String>(v1Words.size * 2)
        val collisions = ArrayList<String>()
        for (r in v1Words) {
            val key = CloudLayout.memberKey(r.stableId)
            val other = owners.put(key, r.stableId)
            if (other != null && other != r.stableId) collisions.add("$key: ${r.stableId} and $other")
        }
        val bucketPlan = CloudLayout.planWrites(v1Words, placeOf, capacity)
        val stats = bucketPlan.writes.map {
            BucketStat(it.id, it.members.size, CloudLayout.estimateBucketBytes(it.members.size), capacity[it.id] ?: 0)
        }.sortedBy { it.id }
        return Plan(
            v1Words = v1Words.size,
            v1Days = v1Activity.count { it.value > 0 },
            writes = bucketPlan.writes.sortedBy { it.id },
            days = CloudLayout.groupDays(v1Activity),
            stats = stats,
            unplaced = bucketPlan.unplaced,
            keyCollisions = collisions,
            refused = bucketPlan.refused,
            invalid = bucketPlan.invalid
        )
    }

    /**
     * Everything the migration read from v1, as JSON, saved on the phone BEFORE anything is written
     * (the freeze-a-copy step). Hand-written so it has no Android dependency and is unit-tested.
     */
    fun exportJson(words: List<SyncRecord>, activity: Map<String, Int>, takenAtMillis: Long): String {
        fun q(x: String) = "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val sb = StringBuilder()
        sb.append("{\"app\":\"vocab\",\"layout\":\"v1\",\"takenAt\":").append(takenAtMillis).append(",\"words\":[")
        words.sortedBy { it.stableId }.forEachIndexed { i, r ->
            if (i > 0) sb.append(',')
            sb.append("{\"stableId\":").append(q(r.stableId))
                .append(",\"srsScore\":").append(r.srsScore)
                .append(",\"easeFactor\":").append(r.easeFactor)
                .append(",\"interval\":").append(r.interval)
                .append(",\"nextReviewDate\":").append(r.nextReviewDate)
                .append(",\"correctCount\":").append(r.correctCount)
                .append(",\"wrongCount\":").append(r.wrongCount)
                .append(",\"isKnown\":").append(r.isKnown)
                .append(",\"updatedAt\":").append(r.updatedAt)
                .append('}')
        }
        sb.append("],\"activity\":{")
        activity.entries.sortedBy { it.key }.forEachIndexed { i, (day, n) ->
            if (i > 0) sb.append(',')
            sb.append(q(day)).append(':').append(n)
        }
        sb.append("}}")
        return sb.toString()
    }

    /** What differs between v1 and the v2 read back from the cloud. Accepted only at [count] == 0. */
    data class Diff(
        val missingInV2: List<String>,
        val extraInV2: List<String>,
        val different: List<String>,
        val daysDifferent: List<String>
    ) {
        val count get() = missingInV2.size + extraInV2.size + different.size + daysDifferent.size
    }

    /**
     * Rebuilds the v1 view from v2 and compares it field by field: every word (all progress
     * fields and updatedAt) and every activity day. A word only in v1 is missing, a word only in v2
     * is extra, and both are differences.
     */
    fun compare(
        v1Words: List<SyncRecord>,
        v1Activity: Map<String, Int>,
        v2Words: List<SyncRecord>,
        v2Activity: Map<String, Int>
    ): Diff {
        val v2ById = v2Words.associateBy { it.stableId }
        val v1Ids = HashSet<String>(v1Words.size * 2)
        val missing = ArrayList<String>()
        val different = ArrayList<String>()
        for (r in v1Words) {
            v1Ids.add(r.stableId)
            val other = v2ById[r.stableId]
            when {
                other == null -> missing.add(r.stableId)
                other != r -> different.add(r.stableId)
            }
        }
        val extra = v2Words.map { it.stableId }.filter { it !in v1Ids }
        val a = v1Activity.filterValues { it > 0 }
        val b = v2Activity.filterValues { it > 0 }
        val daysDifferent = (a.keys + b.keys).filter { a[it] != b[it] }.sorted()
        return Diff(missing, extra, different, daysDifferent)
    }
}
