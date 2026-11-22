package com.example.psychometricvocab.data.sync

/**
 * The incremental-pull rules, as pure functions (unit-tested). A sync pass used to read EVERY
 * vocab document from the server at each start (~6,300 reads). Now every push stamps its documents
 * with a server-side change time (`serverUpdatedAt`), and a pass reads only the documents whose
 * `serverUpdatedAt` is greater than a cursor kept per account on this phone.
 *
 * The cursor is the newest `serverUpdatedAt` seen, in microseconds (Firestore's own precision).
 * It is a SERVER time on purpose: the device clock (`updatedAt`) can be wrong, and a write made
 * offline gets its server time when it finally reaches the server, so it always lands after the
 * cursor of anyone who already looked.
 *
 * Documents written before this version have no `serverUpdatedAt` and can never match a ">"
 * query. That is safe only because a phone with no cursor does a FULL pull first and only then
 * sets one; the cursor is stored under a versioned key so older builds' data can never look like
 * one.
 */
object SyncCursor {
    /**
     * A phone does a full pull again after this long without one. Safety net for a document that
     * an older build (no `serverUpdatedAt`) wrote after this phone's cursor was set, which an
     * incremental pull can't see.
     */
    const val FULL_RESYNC_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    sealed interface PullMode {
        /** Read every vocab document, as before. */
        object Full : PullMode

        /** Read only the documents changed on the server after [sinceMicros]. */
        data class Incremental(val sinceMicros: Long) : PullMode
    }

    /**
     * Full when there is no cursor (fresh install, new account, or data from before this
     * version), when a full pull is asked for (a sign-in: this phone may have changed while signed
     * out, and nothing recorded what), or when the last full pull is too old (or dated in the
     * future by a wrong clock); incremental from the cursor otherwise.
     */
    fun mode(cursorMicros: Long?, lastFullPullAt: Long?, now: Long, forceFull: Boolean = false): PullMode {
        if (forceFull || cursorMicros == null || lastFullPullAt == null) return PullMode.Full
        if (now < lastFullPullAt || now - lastFullPullAt > FULL_RESYNC_AFTER_MS) return PullMode.Full
        return PullMode.Incremental(cursorMicros)
    }

    /** [mode] for one source, as a yes/no: does it need a full pull? */
    fun isFull(cursorMicros: Long?, lastFullPullAt: Long?, now: Long, forceFull: Boolean = false): Boolean =
        mode(cursorMicros, lastFullPullAt, now, forceFull) is PullMode.Full

    /**
     * The cursor after a pass: the newest server time among the documents read, less [marginMicros].
     * A pass that read nothing, or nothing with a server time, keeps the current cursor (it never
     * goes back, and it is never invented).
     */
    fun advance(current: Long?, seenMicros: Collection<Long>, marginMicros: Long = 0L): Long? {
        val newest = (seenMicros.maxOrNull() ?: return current) - marginMicros
        return if (current == null || newest > current) newest else current
    }

    /**
     * The v2 layout's contract keeps the cursor 5 s behind the newest server time seen (a few
     * documents are read twice; a commit that lands just behind another is never skipped).
     */
    const val V2_MARGIN_MICROS = 5_000_000L

    fun toMicros(seconds: Long, nanos: Int): Long = seconds * 1_000_000L + nanos / 1_000

    fun secondsOf(micros: Long): Long = Math.floorDiv(micros, 1_000_000L)

    fun nanosOf(micros: Long): Int = (Math.floorMod(micros, 1_000_000L) * 1_000L).toInt()
}
