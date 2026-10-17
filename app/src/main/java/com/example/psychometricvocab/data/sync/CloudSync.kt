package com.example.psychometricvocab.data.sync

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

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
     * All of this app's remote records, from the server. Returns null if the server couldn't be
     * reached (offline, not signed in, error) — NOT an empty list: "couldn't look" must never be
     * mistaken for "the cloud has nothing", or a sync pass would upload over newer remote data.
     */
    suspend fun pull(): List<SyncRecord>?

    /** Live updates for this app's remote records. Returns a function that stops listening. */
    fun onChange(callback: (List<SyncRecord>) -> Unit): () -> Unit
}

/** Used when the build has no Firebase config: the app behaves exactly as before. */
object UnavailableCloudSync : CloudSync {
    private val none = kotlinx.coroutines.flow.MutableStateFlow<SyncUser?>(null)
    override val user: StateFlow<SyncUser?> = none
    override val isAvailable = false
    override suspend fun signIn(activity: Activity): SignInResult = SignInResult.Unavailable
    override suspend fun signOut() {}
    override suspend fun push(records: List<SyncRecord>) {}
    override suspend fun pull(): List<SyncRecord>? = null
    override fun onChange(callback: (List<SyncRecord>) -> Unit): () -> Unit = {}
}
