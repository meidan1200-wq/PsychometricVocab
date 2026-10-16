package com.example.psychometricvocab.data.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Firebase implementation of [CloudSync]: Google sign-in through Credential Manager →
 * Firebase Auth, and Firestore for the records. The ONLY file in the app that imports Firebase.
 *
 * Records live at users/{uid}/progress/vocab~{stableId} (the shared shape from sync-spec.md):
 *   { uid, app: "vocab", itemKey: stableId, data: {srsScore, easeFactor, interval,
 *     nextReviewDate, correctCount, wrongCount, isKnown}, updatedAt, device: "phone" }
 * The Firestore rules allow only the owning uid to touch its own users/{uid} subtree; MAFAM's records ("mafam~...") sit
 * in the same collection and are never read or written here (queries filter on app == "vocab").
 *
 * Firestore's own offline persistence is on by default on Android, so a push while offline is
 * queued by the SDK and sent on reconnect.
 */
class FirebaseCloudSync private constructor(private val appContext: Context) : CloudSync {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _user = MutableStateFlow(auth.currentUser?.toSyncUser())
    override val user: StateFlow<SyncUser?> = _user
    override val isAvailable = true

    init {
        auth.addAuthStateListener { _user.value = it.currentUser?.toSyncUser() }
    }

    private fun com.google.firebase.auth.FirebaseUser.toSyncUser() = SyncUser(uid, email)

    private fun progressCollection(uid: String) =
        db.collection("users").document(uid).collection("progress")

    override suspend fun signIn(activity: Activity): SignInResult {
        // Generated into resources by the google-services plugin from google-services.json.
        val resId = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        if (resId == 0) return SignInResult.Unavailable
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(appContext.getString(resId))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return SignInResult.Failed("Unexpected credential type")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            val u = result.user ?: return SignInResult.Failed("No user returned")
            SignInResult.Success(u.toSyncUser())
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager sign-in failed", e)
            SignInResult.Failed(e.message)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-in failed", e)
            SignInResult.Failed(e.message)
        }
    }

    override suspend fun signOut() {
        // Local only: no Firestore call here, so remote data is never touched.
        auth.signOut()
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState failed (ignored)", e)
        }
    }

    override suspend fun push(records: List<SyncRecord>) {
        val uid = auth.currentUser?.uid ?: return
        if (records.isEmpty()) return
        val col = progressCollection(uid)
        // Firestore batches hold at most 500 writes.
        records.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { r -> batch.set(col.document(DOC_PREFIX + r.stableId), r.toDoc(uid)) }
            // Not awaited indefinitely: offline, commit() only completes on reconnect, but the
            // write is already in the local cache and queued by the SDK, so a timeout here is
            // not a failure and must not stall the caller.
            withTimeoutOrNull(PUSH_WAIT_MS) { batch.commit().await() }
        }
    }

    override suspend fun pull(): List<SyncRecord>? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            // Source.SERVER: an offline/failed pull throws (-> null below), rather than silently
            // handing back a stale cache that would look like "remote has nothing".
            val snap = progressCollection(uid).whereEqualTo("app", APP).get(Source.SERVER).await()
            snap.documents.mapNotNull { it.toRecord() }
        } catch (e: Exception) {
            Log.w(TAG, "pull failed or offline; changing nothing", e)
            null
        }
    }

    override fun onChange(callback: (List<SyncRecord>) -> Unit): () -> Unit {
        val uid = auth.currentUser?.uid ?: return {}
        val reg = progressCollection(uid).whereEqualTo("app", APP).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            callback(snap.documents.mapNotNull { it.toRecord() })
        }
        return { reg.remove() }
    }

    private fun SyncRecord.toDoc(uid: String): Map<String, Any> = mapOf(
        "uid" to uid,
        "app" to APP,
        "itemKey" to stableId,
        "data" to mapOf(
            "srsScore" to srsScore.toDouble(),
            "easeFactor" to easeFactor.toDouble(),
            "interval" to interval,
            "nextReviewDate" to nextReviewDate,
            "correctCount" to correctCount,
            "wrongCount" to wrongCount,
            "isKnown" to isKnown
        ),
        "updatedAt" to updatedAt,
        "device" to "phone"
    )

    private fun DocumentSnapshot.toRecord(): SyncRecord? {
        val itemKey = getString("itemKey") ?: return null
        val data = get("data") as? Map<*, *> ?: return null
        val updatedAt = (get("updatedAt") as? Number)?.toLong() ?: return null
        fun num(k: String) = data[k] as? Number
        return SyncRecord(
            stableId = itemKey,
            srsScore = num("srsScore")?.toFloat() ?: return null,
            easeFactor = num("easeFactor")?.toFloat() ?: return null,
            interval = num("interval")?.toInt() ?: return null,
            nextReviewDate = num("nextReviewDate")?.toLong() ?: return null,
            correctCount = num("correctCount")?.toInt() ?: return null,
            wrongCount = num("wrongCount")?.toInt() ?: return null,
            isKnown = data["isKnown"] as? Boolean ?: return null,
            updatedAt = updatedAt
        )
    }

    companion object {
        private const val TAG = "FirebaseCloudSync"
        private const val APP = "vocab"
        private const val DOC_PREFIX = "vocab~"
        private const val PUSH_WAIT_MS = 8_000L

        /** The Firebase-backed sync, or [UnavailableCloudSync] if this build has no Firebase config. */
        fun createOrUnavailable(context: Context): CloudSync {
            val app = context.applicationContext
            return try {
                if (FirebaseApp.getApps(app).isEmpty()) UnavailableCloudSync
                else FirebaseCloudSync(app)
            } catch (e: Exception) {
                Log.w(TAG, "Firebase not usable in this build", e)
                UnavailableCloudSync
            }
        }
    }
}
