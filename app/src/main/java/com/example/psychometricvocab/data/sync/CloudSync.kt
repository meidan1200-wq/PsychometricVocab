package com.example.psychometricvocab.data.sync

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * What a pull returned: per-word progress plus the per-day activity counts. For a full pull that
 * is everything this app has in the cloud; for an incremental one only what changed on the server
 * after the cursor. [newestServerMicros] is the newest `serverUpdatedAt` among the documents read
 * (null if none had one), the input to the next cursor; [docsRead] is what Firestore billed.
 */
data class RemoteProgress(
    val words: List<SyncRecord>,
    val activity: Map<String, Int>,
    val newestServerMicros: Long? = null,
    val docsRead: Int = 0
)

/** The signed-in cloud account (display only). */
data class SyncUser(val uid: String, val email: String?)

sealed interface SignInResult {
    data class Success(val user: SyncUser) : SignInResult
    object Cancelled : SignInResult
    object Unavailable : SignInResult
    data class Failed(val failure: SignInFailure) : SignInResult
}

/**
 * The cloud backend, behind an interface so it can be swapped later (a self-hosted server
 * instead of Firebase would replace only the implementation). Nothing outside
 * data/sync/FirebaseCloudSync.kt imports Firebase.
 *
 * Rules every implementation must keep: signOut() and push() never delete remote data, and a
 * failed or offline pull() returns null so the caller changes nothing and uploads nothing.
 */
interface CloudSync {
    /** The signed-in user, or null. Null for good if the backend isn't configured in this build. */
    val user: StateFlow<SyncUser?>

    /** False when this build has no Firebase config (no google-services.json). */
    val isAvailable: Boolean

    suspend fun signIn(activity: Activity): SignInResult

    /** Local sign-out only. Never touches remote data. */
    suspend fun signOut()

    /** Queue these records for upload. Works offline (queued by the SDK); never deletes. */
    suspend fun push(records: List<SyncRecord>)

    /**
     * This app's remote records, from the server: all of them when [sinceMicros] is null, else only
     * those whose server-side change time (`serverUpdatedAt`) is after it (see [SyncCursor]).
     * Returns null if the server couldn't be reached (offline, not signed in, error) — NOT an
     * empty list: "couldn't look" must never be mistaken for "the cloud has nothing", or a sync
     * pass would upload over newer remote data.
     */
    suspend fun pull(sinceMicros: Long?): RemoteProgress?

    /** Queue these per-day activity counts (ISO date -> answers) for upload. Never deletes. */
    suspend fun pushActivity(counts: Map<String, Int>)

    /**
     * Live updates for this app's remote records, starting from [sinceMicros] like [pull] (null =
     * everything, which bills every document on attach). Returns a function that stops listening.
     * Nothing in the app calls this today.
     */
    fun onChange(sinceMicros: Long?, callback: (RemoteProgress) -> Unit): () -> Unit
}

/** Used when the build has no Firebase config: the app behaves exactly as before. */
object UnavailableCloudSync : CloudSync {
    private val none = kotlinx.coroutines.flow.MutableStateFlow<SyncUser?>(null)
    override val user: StateFlow<SyncUser?> = none
    override val isAvailable = false
    override suspend fun signIn(activity: Activity): SignInResult = SignInResult.Unavailable
    override suspend fun signOut() {}
    override suspend fun push(records: List<SyncRecord>) {}
    override suspend fun pull(sinceMicros: Long?): RemoteProgress? = null
    override suspend fun pushActivity(counts: Map<String, Int>) {}
    override fun onChange(sinceMicros: Long?, callback: (RemoteProgress) -> Unit): () -> Unit = {}
}
