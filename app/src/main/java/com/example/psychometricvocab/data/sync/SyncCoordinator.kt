package com.example.psychometricvocab.data.sync

import android.app.Activity
import android.content.Context
import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Glue between the local Room words and a [CloudSync]. All of it runs quietly in the background
 * and the app works exactly as before if sync is unavailable, signed out, or offline.
 *
 *  - on app start and right after sign-in: one reconcile pass (pull, then apply newer remote /
 *    upload newer local), see [SyncMerge.reconcile];
 *  - after each answer: that word is pushed, debounced so a quiz of rapid answers is one batch.
 *
 * Nothing here ever deletes remote data, and a pass that can't reach the server (pull() == null)
 * changes and uploads nothing.
 */
class SyncCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    val cloud: CloudSync = FirebaseCloudSync.createOrUnavailable(appContext)

    private val dao: WordDao get() = VocabDatabase.getInstance(appContext).wordDao()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val passLock = Mutex()
    private val dirty = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var pushJob: Job? = null

    /** Called once from MainActivity: catch up with the cloud if already signed in. */
    fun onAppStart() {
        if (cloud.user.value != null) scope.launch { syncPass() }
    }

    suspend fun signIn(activity: Activity): SignInResult {
        val result = cloud.signIn(activity)
        if (result is SignInResult.Success) scope.launch { syncPass() }
        return result
    }

    /** Local sign-out only (remote data is never touched); also used when the account is deleted. */
    suspend fun signOut() = cloud.signOut()

    /** Hook for VocabRepository.processAnswer: this word's progress just changed. */
    fun onWordChanged(stableId: String?) {
        if (stableId == null || cloud.user.value == null) return
        dirty.add(stableId)
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(PUSH_DEBOUNCE_MS)
            pushDirty()
        }
    }

    private suspend fun pushDirty() {
        val ids = dirty.toList()
        if (ids.isEmpty()) return
        dirty.removeAll(ids.toSet())
        val records = ids.mapNotNull { id ->
            dao.getByStableId(id)?.takeIf { SyncMerge.hasProgress(it) && it.updatedAt > 0L }
                ?.let(SyncMerge::toRecord)
        }
        try {
            cloud.push(records)
        } catch (e: Exception) {
            Log.w(TAG, "push failed; will be reconciled on the next pass", e)
        }
    }

    private suspend fun syncPass() = passLock.withLock {
        val user = cloud.user.value ?: return@withLock
        try {
            // Couldn't reach the server: do nothing at all (see CloudSync.pull).
            val remote = cloud.pull() ?: return@withLock
            val first = !prefs.getBoolean(firstKey(user.uid), false)
            val plan = SyncMerge.reconcile(dao.getSyncableWords(), remote, first, System.currentTimeMillis())
            if (plan.localUpdates.isNotEmpty()) dao.updateWords(plan.localUpdates)
            if (plan.uploads.isNotEmpty()) cloud.push(plan.uploads)
            if (first) prefs.edit().putBoolean(firstKey(user.uid), true).apply()
            Log.i(TAG, "sync pass: first=$first, ${plan.localUpdates.size} local row(s) updated from the cloud, ${plan.uploads.size} uploaded")
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
