package com.example.psychometricvocab.data.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.psychometricvocab.data.ActivityLog
import com.example.psychometricvocab.data.StartupTrace
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.data.WordDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Glue between the local Room words and a [CloudSync]. All of it runs quietly in the background
 * and the app works exactly as before if sync is unavailable, signed out, or offline.
 *
 *  - on app start and right after sign-in: one reconcile pass. A sign-in, a phone with no cursor
 *    yet, or a long gap since the last one reads the whole cloud ("full"); every other start
 *    reads only what changed on the server since this phone last looked ("incremental", see
 *    [SyncCursor]), and uploads only what changed here since it last pushed (see [SyncState]);
 *  - after each answer: that word is pushed, debounced so a quiz of rapid answers is one batch.
 *
 * Which cloud layout it reads and writes follows the account (see [Layout]): v1 is the flat
 * collection exactly as before and is what any account without a layout entry gets; dual reads
 * and writes both; v2 uses only the v2 space.
 *
 * Nothing here ever deletes remote data, and a pass that can't reach the server (pull() == null)
 * changes and uploads nothing.
 */
class SyncCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext

    // Creating the Firebase Auth and Firestore handles is slow on a cold first launch; MainActivity
    // builds this coordinator on a background thread at start-up so the first screen never waits.
    val cloud: CloudSync = StartupTrace.time("Firebase Auth + Firestore create") {
        FirebaseCloudSync.createOrUnavailable(appContext)
    }

    private val dao: WordDao get() = VocabDatabase.getInstance(appContext).wordDao()
    private val activityLog = ActivityLog(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = SyncState(prefs)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val passLock = Mutex()
    @Volatile private var pushJob: Job? = null

    /** The layout the last pass found; until the first pass, the one this phone last saw (V1 if none). */
    @Volatile private var layoutCache: Layout? = null

    private fun layoutOf(uid: String): Layout = layoutCache ?: state.lastLayout(uid) ?: Layout.V1

    /** Called once from MainActivity: catch up with the cloud if already signed in. */
    fun onAppStart() {
        if (cloud.user.value != null) scope.launch { syncPass() }
    }

    suspend fun signIn(activity: Activity): SignInResult {
        val result = cloud.signIn(activity)
        // Always a FULL pass after a sign-in: this phone may have changed while it was signed out,
        // and nothing recorded what, so every local word has to be compared with the cloud.
        if (result is SignInResult.Success) scope.launch { syncPass(forceFull = true) }
        return result
    }

    /** Local sign-out only (remote data is never touched); also used when the account is deleted. */
    suspend fun signOut() = cloud.signOut()

    /** The tool that moves an account from the flat layout to v2 (dry run first, writes only on an explicit confirmation). */
    fun migration(): VocabMigration = VocabMigration(cloud, { dao.getSyncableWords() }, appContext.filesDir, { state.deviceId() })

    /** Hook for VocabRepository.processAnswer: this word's progress just changed. */
    fun onWordChanged(stableId: String?) {
        val uid = cloud.user.value?.uid
        if (stableId == null || uid == null) return
        // Persisted before the debounce: if the process dies inside it, the next push or pass finds it.
        state.addPending(uid, stableId, activityLog.todayIso())
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            pushPending()
        }
    }

    private suspend fun pushPending() {
        val uid = cloud.user.value?.uid ?: return
        val ids = state.pendingWords(uid)
        val days = state.pendingDays(uid)
        if (ids.isEmpty() && days.isEmpty()) return
        val records = ids.mapNotNull { id ->
            dao.getByStableId(id)?.takeIf { SyncMerge.hasProgress(it) && it.updatedAt > 0L }
                ?.let(SyncMerge::toRecord)
        }
        try {
            val layout = layoutOf(uid)
            pushWords(layout, records)
            // The day counts ride along (recordAnswer already ran for these answers).
            val counts = activityLog.getAllCounts()
            val dayCounts = days.mapNotNull { day -> counts[day]?.takeIf { it > 0 }?.let { day to it } }.toMap()
            pushDays(layout, dayCounts)
            // Handed to the SDK (which queues it offline): no longer ours to remember.
            state.clearPending(uid, ids, days)
        } catch (e: Exception) {
            Log.w(TAG, "push failed; kept as pending for the next push or pass", e)
        }
    }

    /** Word records to the layout(s) the account is in. */
    private suspend fun pushWords(layout: Layout, records: List<SyncRecord>) {
        if (records.isEmpty()) return
        if (layout.writesV1) cloud.push(records)
        if (layout.writesV2) pushBuckets(records)
    }

    private suspend fun pushBuckets(records: List<SyncRecord>) {
        if (records.isEmpty()) return
        val words = dao.getSyncableWords()
        val plan = CloudLayout.planWrites(records, CloudLayout.placement(words), CloudLayout.capacities(words))
        if (plan.refused.isNotEmpty()) Log.e(TAG, "v2: bucket(s) over the size guard, nothing written for them, data stays local: ${plan.refused}")
        if (plan.invalid.isNotEmpty()) Log.e(TAG, "v2: ${plan.invalid.size} word(s) with an invalid member key, not written, data stays local: ${plan.invalid.take(5)}")
        if (plan.unplaced.isNotEmpty()) Log.w(TAG, "v2: ${plan.unplaced.size} word(s) not in this phone's list, written to ${CloudLayout.UNPLACED_BUCKET}")
        cloud.pushBuckets(plan.writes)
    }

    private suspend fun pushDays(layout: Layout, counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        if (layout.writesV1) cloud.pushActivity(counts)
        if (layout.writesV2) cloud.pushDays(CloudLayout.groupDays(counts))
    }

    /** The account's layout now; the last one seen if the cloud can't be asked; V1 if none. Remembered either way. */
    private suspend fun resolveLayout(uid: String): Layout {
        val fresh = cloud.readLayout()
        val layout = fresh ?: state.lastLayout(uid) ?: Layout.V1
        if (fresh != null) state.setLastLayout(uid, fresh)
        layoutCache = layout
        return layout
    }

    private suspend fun syncPass(forceFull: Boolean = false) = passLock.withLock {
        val user = cloud.user.value ?: return@withLock
        val uid = user.uid
        try {
            val now = System.currentTimeMillis()
            val layout = resolveLayout(uid)
            // What is pending as the pass starts is what it handles; anything that changes
            // during it stays pending for the next push.
            val pendingWords = state.pendingWords(uid)
            val pendingDays = state.pendingDays(uid)

            // ---- what to read from each layout the account is in ----
            val cursor1 = state.cursor(uid)
            val since1 = if (layout.readsV1) {
                (SyncCursor.mode(cursor1, state.lastFullPullAt(uid), now, forceFull) as? SyncCursor.PullMode.Incremental)?.sinceMicros
            } else null
            val full1 = layout.readsV1 && since1 == null
            val wordsCursor2 = state.v2Cursor(uid, V2_WORDS)
            val daysCursor2 = state.v2Cursor(uid, V2_DAYS)
            // Each v2 source (words, days) has its own cursor and its own full-pull clock.
            val full2w = layout.readsV2 && SyncCursor.isFull(wordsCursor2, state.lastFullPullV2At(uid, V2_WORDS), now, forceFull)
            val full2d = layout.readsV2 && SyncCursor.isFull(daysCursor2, state.lastFullPullV2At(uid, V2_DAYS), now, forceFull)
            // Reconciling against everything needs EVERY source that is read to have been read whole.
            val full = (!layout.readsV1 || full1) && (!layout.readsV2 || full2w)
            val fullDays = (!layout.readsV1 || full1) && (!layout.readsV2 || full2d)

            // Couldn't reach the server: do nothing at all (see CloudSync.pull).
            val remote1 = if (layout.readsV1) cloud.pull(since1) ?: return@withLock else null
            val remote2 = if (layout.readsV2) {
                cloud.pullV2(if (full2w) null else wordsCursor2, if (full2d) null else daysCursor2) ?: return@withLock
            } else null

            // ---- one remote view out of both layouts (the newer record per word, the larger count per day) ----
            val winners = SourceMerge.unionWords(remote1?.words.orEmpty(), remote2?.words.orEmpty())
            val activityUnion = SourceMerge.unionActivity(remote1?.activity.orEmpty(), remote2?.activity.orEmpty())
            val remoteActivity = activityUnion.mapValues { it.value.first }
            val docsRead = (remote1?.docsRead ?: 0) + (remote2?.docsRead ?: 0)

            val first = full && !prefs.getBoolean(firstKey(uid), false)
            val local = dao.getSyncableWords()
            // One-shot repair of the 22 Nov 2026 incident, BEFORE the merge (see SyncRepair). It
            // concerns the flat v1 records and needs the whole list to find every stale one, so it
            // only ever runs in a FULL pass of an account that is still on v1.
            val remoteWords = winners.map { it.record }
            val repaired = if (full && layout == Layout.V1) repairIfNeeded(uid, local, remoteWords, now) else remoteWords
            val plan = if (full) SyncMerge.reconcile(local, repaired, first, now)
            else SyncMerge.reconcileIncremental(local, repaired, pendingWords, now)
            if (plan.localUpdates.isNotEmpty()) dao.updateWords(plan.localUpdates)
            pushWords(layout, plan.uploads)

            // Daily activity (it drives Practice's session size): merge by max per day, so counts
            // never go down on either side; only days with answers are uploaded.
            val counts = activityLog.getAllCounts()
            val activity = if (fullDays) ActivityMerge.reconcile(counts, remoteActivity)
            else ActivityMerge.reconcileIncremental(counts, remoteActivity, pendingDays)
            activityLog.raiseTo(activity.raiseLocal)
            pushDays(layout, activity.uploads)

            // Dual: what only one layout held is copied to the other, so neither is left behind.
            if (layout == Layout.DUAL) {
                val words = SourceMerge.crossFillWords(plan.localUpdates, winners)
                if (words.toV1.isNotEmpty()) cloud.push(words.toV1)
                if (words.toV2.isNotEmpty()) pushBuckets(words.toV2)
                val days = SourceMerge.crossFillDays(activity.raiseLocal, activityUnion)
                if (days.toV1.isNotEmpty()) cloud.pushActivity(days.toV1)
                if (days.toV2.isNotEmpty()) cloud.pushDays(CloudLayout.groupDays(days.toV2))
            }

            // Bookkeeping last, in this order: "first" and the full-pull times, then the cursors. A
            // cursor means "everything up to here was applied and uploaded"; if the pass dies
            // before it is saved, the next one just reads the same documents again.
            state.clearPending(uid, pendingWords, pendingDays)
            if (first) prefs.edit().putBoolean(firstKey(uid), true).apply()
            var cursorNote = ""
            if (remote1 != null) {
                if (full1) state.setLastFullPullAt(uid, now)
                val next = SyncCursor.advance(cursor1, listOfNotNull(remote1.newestServerMicros))
                if (next != null && next != cursor1) state.setCursor(uid, next)
                cursorNote += " v1 cursor ${cursor1 ?: "none"} -> ${next ?: "none"};"
            }
            if (remote2 != null) {
                if (full2w) state.setLastFullPullV2At(uid, V2_WORDS, now)
                if (full2d) state.setLastFullPullV2At(uid, V2_DAYS, now)
                val nextW = SyncCursor.advance(wordsCursor2, listOfNotNull(remote2.newestWordsMicros), SyncCursor.V2_MARGIN_MICROS)
                val nextD = SyncCursor.advance(daysCursor2, listOfNotNull(remote2.newestDaysMicros), SyncCursor.V2_MARGIN_MICROS)
                if (nextW != null && nextW != wordsCursor2) state.setV2Cursor(uid, V2_WORDS, nextW)
                if (nextD != null && nextD != daysCursor2) state.setV2Cursor(uid, V2_DAYS, nextD)
                cursorNote += " v2 cursors ${wordsCursor2 ?: "none"}/${daysCursor2 ?: "none"} -> ${nextW ?: "none"}/${nextD ?: "none"};"
            }
            if (layout != Layout.V1) registerDevice(uid, now)

            Log.i(
                TAG,
                "sync pass [layout ${layout.wire}]: ${if (full) "FULL" else "incremental"}${if (fullDays != full) " (activity ${if (fullDays) "full" else "incremental"})" else ""}${if (forceFull) " (sign-in)" else ""}, " +
                    "$docsRead doc(s) read,$cursorNote " +
                    "${plan.localUpdates.size} local row(s) updated from the cloud, ${plan.uploads.size} uploaded; " +
                    "activity: ${activity.raiseLocal.size} day(s) raised locally, ${activity.uploads.size} uploaded"
            )
        } catch (e: Exception) {
            Log.w(TAG, "sync pass failed; local data untouched beyond what was already applied", e)
        }
    }

    /** Lists this device in users/{uid} devices (schema 2, lastSeen), at most twice a day; only once an account has left v1. */
    private suspend fun registerDevice(uid: String, now: Long) {
        val last = state.lastDeviceRegisteredAt(uid)
        if (last != null && now >= last && now - last < DEVICE_REGISTER_EVERY_MS) return
        try {
            cloud.registerDevice(state.deviceId(), android.os.Build.MODEL ?: "phone")
            state.setLastDeviceRegisteredAt(uid, now)
        } catch (e: Exception) {
            Log.w(TAG, "could not register this device (will retry on a later pass)", e)
        }
    }

    /**
     * Once per account: replace the cloud's stale 22 Nov 2026 records with this phone's own state
     * (see [SyncRepair]) and return the remote list the normal merge should use. Skipped on a
     * device that doesn't hold the real progress. The flag is saved only after the uploads were
     * handed to the SDK, so a failed push is simply tried again on the next pass.
     */
    private suspend fun repairIfNeeded(
        uid: String,
        local: List<Word>,
        remote: List<SyncRecord>,
        now: Long
    ): List<SyncRecord> {
        if (prefs.getBoolean(SyncRepair.flagKey(uid), false)) return remote
        if (!SyncRepair.canRepairFrom(local)) {
            Log.w(TAG, "repair: skipped, this device has no progress of its own or all of it carries the 22 Nov stamps, so it is not the one that holds the real progress; flag not set")
            return remote
        }
        val fix = SyncRepair.plan(local, remote, now)
        if (fix.uploads.isNotEmpty()) cloud.push(fix.uploads)
        prefs.edit().putBoolean(SyncRepair.flagKey(uid), true).apply()
        Log.i(TAG, "repair: ${fix.inWindow} remote in window, ${fix.uploads.size} uploaded, flag set")
        return fix.remoteAfterRepair
    }

    private fun firstKey(uid: String) = "first_sync_done_$uid"

    companion object {
        private const val TAG = "SyncCoordinator"
        private const val PREFS = "sync_prefs"
        private const val PUSH_DEBOUNCE_MS = 3_000L
        private const val DEVICE_REGISTER_EVERY_MS = 12L * 60 * 60 * 1000
        private const val V2_WORDS = "w"
        private const val V2_DAYS = "d"

        @Volatile private var INSTANCE: SyncCoordinator? = null

        fun get(context: Context): SyncCoordinator =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncCoordinator(context).also { INSTANCE = it }
            }
    }
}
