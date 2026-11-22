package com.example.psychometricvocab.data.sync

import com.example.psychometricvocab.data.Word

/**
 * Which cloud layout an account is in, read from users/{uid} apps.vocab.layout:
 *  - [V1]: the flat collection users/{uid}/progress, exactly as before. A missing entry, or a
 *    value this build doesn't know, means V1, so an account nobody migrated behaves as it always did;
 *  - [DUAL]: the app reads and writes BOTH layouts (a device that has not updated keeps working);
 *  - [V2]: the app reads and writes only its v2 space, users/{uid}/apps/vocab/...
 */
enum class Layout(val wire: String) {
    V1("v1"), DUAL("dual"), V2("v2");

    val readsV1 get() = this != V2
    val readsV2 get() = this != V1
    val writesV1 get() = this != V2
    val writesV2 get() = this != V1

    companion object {
        fun parse(value: String?): Layout = values().firstOrNull { it.wire == value } ?: V1
    }
}

/**
 * The v2 layout as pure functions (unit-tested; no Firebase types). The contract is MAFAM's
 * "Contract for both apps" in docs/progress-store-design.md; the field list was settled with
 * MAFAM's sync session.
 *
 *   users/{uid}/apps/vocab/words/{bucket}   one document per track and unit:
 *       { s: <server time>, t: <track>, u: <unit>, w: { "<memberKey>": { k: <stableId>, <progress>, updatedAt } } }
 *   users/{uid}/apps/vocab/days/{yyyy-mm}   one document per month: { s: <server time>, d: { "DD": count } }
 *
 * Writes only ever touch the members that changed (a merge of the nested map): never a whole bucket.
 * `s` is the cursor (server time); `updatedAt` stays the device clock, for last-write-wins only.
 */
object CloudLayout {
    /** A bucket document larger than this is refused and its data stays local. */
    const val MAX_BUCKET_BYTES = 700 * 1024

    /** firestore.rules caps a bucket at this many members; a bucket that could pass it is refused. */
    const val MAX_MEMBERS_PER_BUCKET = 1500

    /** The rules cannot check the keys of `w` (no loops), so the client does: a letter, then hex and underscores. */
    private val MEMBER_KEY = Regex("^w[0-9a-f_]{12,24}$")

    fun isValidMemberKey(key: String) = MEMBER_KEY.matches(key)

    /** The schema number a device reports for itself in users/{uid} devices. */
    const val SCHEMA = 2

    /** Words the local list can't place in a track and unit (none expected). */
    const val UNPLACED_BUCKET = "other-u0"

    /**
     * Where an unplaceable word lives: track "other", unit 0. It is a Place like any other so its bucket
     * is written with `t` = "other" and `u` = 0; firestore.rules refuses a bucket without them.
     */
    val UNPLACED = Place("other", 0)

    /** Upper bound of one member's stored size: key, nine small fields, with field names. */
    const val MEMBER_BYTES_UPPER = 320

    data class Place(val track: String, val unit: Int)

    fun bucketId(place: Place) = "${place.track}-u${place.unit}"

    /** A field-path-safe key for a word: a letter first, no hyphen. Unique because stableIds are. */
    fun memberKey(stableId: String) = "w" + stableId.replace('-', '_')

    fun dayDocId(isoDate: String) = isoDate.substring(0, 7)

    fun dayKey(isoDate: String) = isoDate.substring(8, 10)

    fun isoDate(docId: String, key: String) = "$docId-$key"

    private val ISO = Regex("""\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")

    fun isIsoDate(s: String) = ISO.matches(s)

    /** stableId -> where it lives, from the phone's own word list. */
    fun placement(words: List<Word>): Map<String, Place> {
        val out = HashMap<String, Place>(words.size * 2)
        for (w in words) w.stableId?.let { out[it] = Place(w.track, w.unit) }
        return out
    }

    /** How many words each bucket can ever hold (all the phone's words of that track and unit). */
    fun capacities(words: List<Word>): Map<String, Int> =
        words.filter { it.stableId != null }.groupingBy { bucketId(Place(it.track, it.unit)) }.eachCount()

    // ---- member <-> record ----

    fun memberFields(r: SyncRecord): Map<String, Any> = mapOf(
        "k" to r.stableId,
        "srsScore" to r.srsScore.toDouble(),
        "easeFactor" to r.easeFactor.toDouble(),
        "interval" to r.interval,
        "nextReviewDate" to r.nextReviewDate,
        "correctCount" to r.correctCount,
        "wrongCount" to r.wrongCount,
        "isKnown" to r.isKnown,
        "updatedAt" to r.updatedAt
    )

    /** The record a member holds, or null if it is missing a field (never guessed). */
    fun recordOf(member: Map<*, *>): SyncRecord? {
        fun num(k: String) = member[k] as? Number
        return SyncRecord(
            stableId = member["k"] as? String ?: return null,
            srsScore = num("srsScore")?.toFloat() ?: return null,
            easeFactor = num("easeFactor")?.toFloat() ?: return null,
            interval = num("interval")?.toInt() ?: return null,
            nextReviewDate = num("nextReviewDate")?.toLong() ?: return null,
            correctCount = num("correctCount")?.toInt() ?: return null,
            wrongCount = num("wrongCount")?.toInt() ?: return null,
            isKnown = member["isKnown"] as? Boolean ?: return null,
            updatedAt = num("updatedAt")?.toLong() ?: return null
        )
    }

    // ---- bucket writes ----

    /** One bucket's merge: only these members are written (plus s, t, u by the cloud layer). */
    data class BucketWrite(val id: String, val track: String?, val unit: Int?, val members: Map<String, Map<String, Any>>)

    /**
     * [refused]: bucket ids not written (over the size or member guard). [unplaced]: stableIds sent to
     * the catch-all bucket. [invalid]: stableIds whose member key breaks the key rule; they are not
     * written and stay local.
     */
    data class BucketPlan(
        val writes: List<BucketWrite>,
        val refused: List<String>,
        val unplaced: List<String>,
        val invalid: List<String> = emptyList()
    )

    /**
     * Groups [records] into per-bucket merges. A word [placeOf] can't place goes to
     * [UNPLACED_BUCKET] (reported in [BucketPlan.unplaced], never dropped). A bucket whose possible
     * size ([capacity] words, at the upper bound per member) is over [MAX_BUCKET_BYTES] is refused:
     * nothing is written for it and its id is in [BucketPlan.refused], the data stays local.
     */
    fun planWrites(
        records: List<SyncRecord>,
        placeOf: Map<String, Place>,
        capacity: Map<String, Int>
    ): BucketPlan {
        val byBucket = LinkedHashMap<String, MutableMap<String, Map<String, Any>>>()
        val places = HashMap<String, Place>()
        val unplaced = ArrayList<String>()
        val invalid = ArrayList<String>()
        for (r in records) {
            if (!isValidMemberKey(memberKey(r.stableId))) { invalid.add(r.stableId); continue }
            val known = placeOf[r.stableId]
            if (known == null) unplaced.add(r.stableId)
            val place = known ?: UNPLACED
            val id = bucketId(place)
            places[id] = place
            byBucket.getOrPut(id) { LinkedHashMap() }[memberKey(r.stableId)] = memberFields(r)
        }
        val refused = ArrayList<String>()
        val writes = ArrayList<BucketWrite>()
        for ((id, members) in byBucket) {
            val possible = maxOf(capacity[id] ?: 0, members.size)
            if (possible > MAX_MEMBERS_PER_BUCKET || possible.toLong() * MEMBER_BYTES_UPPER > MAX_BUCKET_BYTES) { refused.add(id); continue }
            val place = places.getValue(id)
            writes.add(BucketWrite(id, place.track, place.unit, members))
        }
        return BucketPlan(writes, refused, unplaced, invalid)
    }

    /** The conservative size of a bucket document holding [members] words. */
    fun estimateBucketBytes(members: Int): Long = members.toLong() * MEMBER_BYTES_UPPER

    // ---- the documents, exactly as they are written (pure, so the contract is pinned by tests) ----

    /** Stands for the server's own time inside a document built here; the cloud layer swaps in FieldValue.serverTimestamp(). */
    object ServerTime

    /** firestore.rules: at most this many days in a month document. */
    const val MAX_DAYS_PER_MONTH = 31

    /** [data] is merged only at [mergeFields]; null means a plain merge of everything in [data]. */
    data class DocWrite(val data: Map<String, Any>, val mergeFields: List<String>?)

    /**
     * One bucket write. `s` is in the data AND the mask on EVERY write (the rules refuse a write
     * without it, and another device's cursor query only sees a write that moved it); each changed
     * member is its own dotted path `w.<key>`, so it is replaced whole and nothing else is touched;
     * `t` and `u` are in the mask only when there is a value.
     */
    fun bucketWrite(w: BucketWrite): DocWrite {
        val data = HashMap<String, Any>()
        val mask = ArrayList<String>()
        data["s"] = ServerTime
        mask.add("s")
        w.track?.let { data["t"] = it; mask.add("t") }
        w.unit?.let { data["u"] = it; mask.add("u") }
        data["w"] = w.members
        w.members.keys.forEach { mask.add("w.$it") }
        return DocWrite(data, mask)
    }

    /** One month document: `s` and `d`, merged per day. */
    fun dayWrite(days: Map<String, Int>): DocWrite {
        require(days.size <= MAX_DAYS_PER_MONTH) { "a month document holds at most $MAX_DAYS_PER_MONTH days" }
        return DocWrite(mapOf("s" to ServerTime, "d" to days), null)
    }

    /**
     * This device in users/{uid} devices. A NESTED map (merged), never a dotted path: the key
     * `vocab-<id>` has a hyphen, which is not valid in a dotted path string.
     */
    fun deviceWrite(deviceId: String, name: String): Map<String, Any> = mapOf(
        "devices" to mapOf(
            "vocab-$deviceId" to mapOf("app" to "vocab", "name" to name, "schema" to SCHEMA, "lastSeen" to ServerTime)
        )
    )

    /** This app's entry in users/{uid} apps; the root document allows only `schema`, `apps` and `devices`. */
    fun layoutEntryWrite(layout: Layout, migration: Map<String, Any>): Map<String, Any> = mapOf(
        "apps" to mapOf(
            "vocab" to mapOf("layout" to layout.wire, "migratedAt" to ServerTime, "migration" to migration)
        )
    )

    // ---- days ----

    /** ISO date -> count, grouped as month document -> (DD -> count). Counts below 1 and bad dates are skipped. */
    fun groupDays(counts: Map<String, Int>): Map<String, Map<String, Int>> {
        val out = LinkedHashMap<String, MutableMap<String, Int>>()
        for ((iso, n) in counts) {
            if (n < 1 || !isIsoDate(iso)) continue
            out.getOrPut(dayDocId(iso)) { LinkedHashMap() }[dayKey(iso)] = n
        }
        return out
    }

    /** The inverse of [groupDays]. */
    fun flattenDays(days: Map<String, Map<String, Int>>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((docId, d) in days) for ((key, n) in d) {
            val iso = isoDate(docId, key)
            if (isIsoDate(iso)) out[iso] = n
        }
        return out
    }
}
