package com.example.psychometricvocab.data.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.psychometricvocab.data.ActivityLog
import com.example.psychometricvocab.data.StartupTrace
import com.example.psychometricvocab.data.VocabDatabase
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
            cloud.push(records)
            // The day counts ride along (recordAnswer already ran for these answers).
            val counts = activityLog.getAllCounts()
            val dayCounts = days.mapNotNull { day -> counts[day]?.takeIf { it > 0 }?.let { day to it } }.toMap()
            if (dayCounts.isNotEmpty()) cloud.pushActivity(dayCounts)
            // Handed to the SDK (which queues it offline): no longer ours to remember.
            state.clearPending(uid, ids, days)
        } catch (e: Exception) {
            Log.w(TAG, "push failed; kept as pending for the next push or pass", e)
        }
    }

    private suspend fun syncPass(forceFull: Boolean = false) = passLock.withLock {
        val user = cloud.user.value ?: return@withLock
        val uid = user.uid
        try {
            val now = System.currentTimeMillis()
            val cursor = state.cursor(uid)
            val mode = SyncCursor.mode(cursor, state.lastFullPullAt(uid), now, forceFull)
            val since = (mode as? SyncCursor.PullMode.Incremental)?.sinceMicros
            val full = since == null
            // What is pending as the pass starts is what it handles; anything that changes
            // during it stays pending for the next push.
            val pendingWords = state.pendingWords(uid)
            val pendingDays = state.pendingDays(uid)

            // Couldn't reach the server: do nothing at all (see CloudSync.pull).
            val remote = cloud.pull(since) ?: return@withLock

            val first = full && !prefs.getBoolean(firstKey(uid), false)
            val local = dao.getSyncableWords()
            val plan = if (full) SyncMerge.reconcile(local, remote.words, first, now)
            else SyncMerge.reconcileIncremental(local, remote.words, pendingWords, now)
            if (plan.localUpdates.isNotEmpty()) dao.updateWords(plan.localUpdates)
            if (plan.uploads.isNotEmpty()) cloud.push(plan.uploads)

            // Daily activity (it drives Practice's session size): merge by max per day, so counts
            // never go down on either side; only days with answers are uploaded.
            val counts = activityLog.getAllCounts()
            val activity = if (full) ActivityMerge.reconcile(counts, remote.activity)
            else ActivityMerge.reconcileIncremental(counts, remote.activity, pendingDays)
            activityLog.raiseTo(activity.raiseLocal)
            if (activity.uploads.isNotEmpty()) cloud.pushActivity(activity.uploads)

            // Bookkeeping last, in this order: "first" and the full-pull time, then the cursor. A
            // cursor means "everything up to here was applied and uploaded"; if the pass dies
            // before it is saved, the next one just reads the same documents again.
            state.clearPending(uid, pendingWords, pendingDays)
            if (first) prefs.edit().putBoolean(firstKey(uid), true).apply()
            if (full) state.setLastFullPullAt(uid, now)
            val next = SyncCursor.advance(cursor, listOfNotNull(remote.newestServerMicros))
            if (next != null && next != cursor) state.setCursor(uid, next)

            Log.i(
                TAG,
                "sync pass: ${if (full) "FULL" else "incremental"}${if (forceFull) " (sign-in)" else ""}, " +
                    "${remote.docsRead} doc(s) read, cursor ${cursor ?: "none"} -> ${next ?: "none"}, " +
                    "${plan.localUpdates.size} local row(s) updated from the cloud, ${plan.uploads.size} uploaded; " +
                    "activity: ${activity.raiseLocal.size} day(s) raised locally, ${activity.uploads.size} uploaded"
            )
        } catch (e: Exception) {
            Log.w(TAG, "sync pass failed; local data untouched beyond what was already applied", e)
        }
    }

    private fun firstKey(uid: String) = "first_sync_done_$uid"

    companion object {
        private const val TAG = "SyncCoordinator"
        private const val PREFS = "sync_prefs"
        private const val PUSH_DEBOUNCE_MS = 3_000L

        @Volatile private var INSTANCE: SyncCoordinator? = null

        fun get(context: Context): SyncCoordinator =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncCoordinator(context).also { INSTANCE = it }
            }
    }
}
