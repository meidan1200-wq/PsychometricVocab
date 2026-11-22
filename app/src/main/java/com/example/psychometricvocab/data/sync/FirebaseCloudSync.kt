package com.example.psychometricvocab.data.sync

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
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
        val webClientId = appContext.getString(resId)
        return try {
            val idToken = try {
                // First choice: the accounts-on-device sheet. filterByAuthorizedAccounts = false so a
                // first-time user sees ALL their accounts, not only ones already used with the app.
                requestIdToken(
                    activity,
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .build()
                )
            } catch (e: NoCredentialException) {
                // Nothing usable offered (e.g. no account on the device yet). Fall back to the
                // explicit "Sign in with Google" flow, which can also offer to add an account.
                Log.i(TAG, "No credential from GetGoogleIdOption; trying GetSignInWithGoogleOption")
                requestIdToken(activity, GetSignInWithGoogleOption.Builder(webClientId).build())
            }
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            val u = result.user ?: return SignInResult.Failed(SignInFailure(SignInProblem.OTHER, "no user returned"))
            SignInResult.Success(u.toSyncUser())
        } catch (e: Exception) {
            val failure = classifySignInError(e)
            if (failure.problem == SignInProblem.CANCELLED) {
                SignInResult.Cancelled
            } else {
                Log.w(TAG, "Sign-in failed: $failure", e)
                SignInResult.Failed(failure)
            }
        }
    }

    private suspend fun requestIdToken(activity: Activity, option: CredentialOption): String {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("unexpected credential type: ${credential.type}")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
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

    override suspend fun pushActivity(counts: Map<String, Int>) {
        val uid = auth.currentUser?.uid ?: return
        val days = counts.filterValues { it > 0 }
        if (days.isEmpty()) return
        val col = progressCollection(uid)
        val now = System.currentTimeMillis()
        // Same collection and shape as the word records: users/{uid}/progress/vocab~activity-YYYY-MM-DD
        days.entries.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (day, count) ->
                val key = ACTIVITY_PREFIX + day
                batch.set(
                    col.document(DOC_PREFIX + key),
                    mapOf(
                        "uid" to uid,
                        "app" to APP,
                        "itemKey" to key,
                        "data" to mapOf("count" to count),
                        "updatedAt" to now,
                        SERVER_TIME to FieldValue.serverTimestamp(),
                        "device" to "phone"
                    )
                )
            }
            withTimeoutOrNull(PUSH_WAIT_MS) { batch.commit().await() }
        }
    }

    override suspend fun pull(sinceMicros: Long?, stampClock: Boolean): RemoteProgress? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val col = progressCollection(uid)
            // Source.SERVER: an offline/failed pull throws (-> null below), rather than silently
            // handing back a stale cache that would look like "remote has nothing".
            val snap = if (sinceMicros == null) {
                // Full pull. Stamp a clock document FIRST: its serverUpdatedAt comes back in this
                // very read, so the cursor can be set even when every real document predates this
                // version (none has a serverUpdatedAt), and any change after it is > the cursor.
                if (stampClock) stampClockDocument(col, uid)
                col.whereEqualTo("app", APP).get(Source.SERVER).await()
            } else {
                changedSince(col, sinceMicros).get(Source.SERVER).await()
            }
            snap.toRemoteProgress()
        } catch (e: Exception) {
            Log.w(TAG, "pull failed or offline; changing nothing", e)
            null
        }
    }

    override fun onChange(sinceMicros: Long?, callback: (RemoteProgress) -> Unit): () -> Unit {
        val uid = auth.currentUser?.uid ?: return {}
        val col = progressCollection(uid)
        val query = if (sinceMicros == null) col.whereEqualTo("app", APP) else changedSince(col, sinceMicros)
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            callback(snap.toRemoteProgress())
        }
        return { reg.remove() }
    }

    // ---- layout v2: users/{uid}/apps/vocab/{words,days}, and the root document users/{uid} ----

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun vocabSpace(uid: String) = userDoc(uid).collection("apps").document(APP)
    private fun wordsCollection(uid: String) = vocabSpace(uid).collection("words")
    private fun daysCollection(uid: String) = vocabSpace(uid).collection("days")

    private fun serverStamp(micros: Long) = Timestamp(SyncCursor.secondsOf(micros), SyncCursor.nanosOf(micros))

    override suspend fun readLayout(): Layout? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val snap = userDoc(uid).get(Source.SERVER).await()
            val vocab = (snap.get("apps") as? Map<*, *>)?.get(APP) as? Map<*, *>
            Layout.parse(vocab?.get("layout") as? String)
        } catch (e: Exception) {
            Log.w(TAG, "could not read the layout", e)
            null
        }
    }

    override suspend fun pullV2(sinceWordsMicros: Long?, sinceDaysMicros: Long?): RemoteV2? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val wordsQuery: Query = wordsCollection(uid).let { c ->
                if (sinceWordsMicros == null) c else c.whereGreaterThan("s", serverStamp(sinceWordsMicros))
            }
            val daysQuery: Query = daysCollection(uid).let { c ->
                if (sinceDaysMicros == null) c else c.whereGreaterThan("s", serverStamp(sinceDaysMicros))
            }
            val words = wordsQuery.get(Source.SERVER).await()
            val days = daysQuery.get(Source.SERVER).await()
            RemoteV2(
                words = words.documents.flatMap { doc ->
                    (doc.get("w") as? Map<*, *>)?.values.orEmpty().mapNotNull { (it as? Map<*, *>)?.let(CloudLayout::recordOf) }
                },
                activity = CloudLayout.flattenDays(
                    days.documents.associate { doc ->
                        doc.id to (doc.get("d") as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val n = (v as? Number)?.toInt() ?: return@mapNotNull null
                            key to n
                        }.toMap()
                    }
                ),
                newestWordsMicros = words.newestServerMicros(),
                newestDaysMicros = days.newestServerMicros(),
                docsRead = words.size() + days.size()
            )
        } catch (e: Exception) {
            Log.w(TAG, "v2 pull failed or offline; changing nothing", e)
            null
        }
    }

    private fun QuerySnapshot.newestServerMicros(): Long? =
        documents.mapNotNull { it.getTimestamp("s") }.maxOfOrNull { SyncCursor.toMicros(it.seconds, it.nanoseconds) }

    /** The builders in CloudLayout mark the server's time with [CloudLayout.ServerTime]; this is where it becomes real. */
    private fun resolve(v: Any?): Any? = when {
        v === CloudLayout.ServerTime -> FieldValue.serverTimestamp()
        v is Map<*, *> -> v.mapValues { resolve(it.value) }
        else -> v
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolvedData(data: Map<String, Any>): Map<String, Any> = resolve(data) as Map<String, Any>

    override suspend fun pushBuckets(writes: List<CloudLayout.BucketWrite>) {
        val uid = auth.currentUser?.uid ?: return
        if (writes.isEmpty()) return
        val col = wordsCollection(uid)
        // mergeFields with one dotted path per member: exactly these members are replaced, each
        // WHOLE (an old sub-field cannot be left behind), the rest of the bucket is untouched, and
        // `s` is in every write (see CloudLayout.bucketWrite).
        writes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { w ->
                val doc = CloudLayout.bucketWrite(w)
                batch.set(col.document(w.id), resolvedData(doc.data), SetOptions.mergeFields(doc.mergeFields!!))
            }
            withTimeoutOrNull(PUSH_WAIT_MS) { batch.commit().await() }
        }
    }

    override suspend fun pushDays(days: Map<String, Map<String, Int>>) {
        val uid = auth.currentUser?.uid ?: return
        if (days.isEmpty()) return
        val col = daysCollection(uid)
        days.entries.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (month, d) ->
                batch.set(col.document(month), resolvedData(CloudLayout.dayWrite(d).data), SetOptions.merge())
            }
            withTimeoutOrNull(PUSH_WAIT_MS) { batch.commit().await() }
        }
    }

    override suspend fun registerDevice(deviceId: String, name: String) {
        val uid = auth.currentUser?.uid ?: return
        withTimeoutOrNull(PUSH_WAIT_MS) {
            userDoc(uid).set(resolvedData(CloudLayout.deviceWrite(deviceId, name)), SetOptions.merge()).await()
        }
    }

    override suspend fun setLayoutEntry(layout: Layout, migration: Map<String, Any>) {
        val uid = auth.currentUser?.uid ?: return
        userDoc(uid).set(resolvedData(CloudLayout.layoutEntryWrite(layout, migration)), SetOptions.merge()).await()
    }

    /**
     * Documents whose server change time is after [sinceMicros]. A range on ONE field needs no
     * composite index (filtering on app == "vocab" as well would), so the app is checked on the
     * client; only vocab documents carry serverUpdatedAt today, so nothing else is read.
     */
    private fun changedSince(col: CollectionReference, sinceMicros: Long): Query =
        col.whereGreaterThan(
            SERVER_TIME,
            Timestamp(SyncCursor.secondsOf(sinceMicros), SyncCursor.nanosOf(sinceMicros))
        )

    private suspend fun stampClockDocument(col: CollectionReference, uid: String) {
        try {
            withTimeoutOrNull(PUSH_WAIT_MS) {
                col.document(DOC_PREFIX + CLOCK_KEY).set(
                    mapOf(
                        "uid" to uid,
                        "app" to APP,
                        "itemKey" to CLOCK_KEY,
                        "updatedAt" to System.currentTimeMillis(),
                        SERVER_TIME to FieldValue.serverTimestamp(),
                        "device" to "phone"
                    )
                ).await()
            }
        } catch (e: Exception) {
            // Not fatal: without it a cursor can still come from the documents themselves.
            Log.w(TAG, "could not stamp the sync clock document", e)
        }
    }

    private fun QuerySnapshot.toRemoteProgress(): RemoteProgress {
        val vocab = documents.filter { it.getString("app") == APP }
        return RemoteProgress(
            words = vocab.mapNotNull { it.toRecord() },
            activity = vocab.mapNotNull { it.toActivityDay() }.toMap(),
            // Every document read counts for the cursor (they were billed), whatever app wrote it.
            newestServerMicros = documents.mapNotNull { it.getTimestamp(SERVER_TIME) }
                .maxOfOrNull { SyncCursor.toMicros(it.seconds, it.nanoseconds) },
            docsRead = size()
        )
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
        // The server's own clock at commit time: what incremental pulls (SyncCursor) key on.
        // updatedAt stays the device clock, for last-write-wins.
        SERVER_TIME to FieldValue.serverTimestamp(),
        "device" to "phone"
    )

    /** (ISO date, count) for an "activity-YYYY-MM-DD" document, null for any other kind. */
    private fun DocumentSnapshot.toActivityDay(): Pair<String, Int>? {
        val itemKey = getString("itemKey") ?: return null
        if (!itemKey.startsWith(ACTIVITY_PREFIX)) return null
        val count = ((get("data") as? Map<*, *>)?.get("count") as? Number)?.toInt() ?: return null
        return itemKey.removePrefix(ACTIVITY_PREFIX) to count
    }

    private fun DocumentSnapshot.toRecord(): SyncRecord? {
        val itemKey = getString("itemKey") ?: return null
        if (itemKey.startsWith(ACTIVITY_PREFIX)) return null // an activity day, not a word
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
        private const val ACTIVITY_PREFIX = "activity-"
        private const val SERVER_TIME = "serverUpdatedAt"
        private const val CLOCK_KEY = "sync-clock"
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

/**
 * Maps whatever a sign-in threw to ONE specific [SignInFailure] (never a generic "failed"):
 * looks through the whole cause chain, because Credential Manager wraps the Play-services and
 * network errors it hits. Top-level and free of side effects so each case is unit-tested.
 */
internal fun classifySignInError(error: Throwable): SignInFailure {
    val chain = generateSequence(error) { it.cause }.take(8).toList()

    if (chain.any { it is GetCredentialCancellationException }) return SignInFailure(SignInProblem.CANCELLED)
    if (chain.any { it is NoCredentialException }) return SignInFailure(SignInProblem.NO_ACCOUNT)

    // Config: Play services' DEVELOPER_ERROR is status code 10 (wrong SHA-1 / client id / package).
    chain.firstNotNullOfOrNull { statusCodeOf(it) }?.let { code ->
        if (code == 10) return SignInFailure(SignInProblem.CONFIG, "10")
    }
    if (chain.any { it.message?.contains("DEVELOPER_ERROR", ignoreCase = true) == true }) {
        return SignInFailure(SignInProblem.CONFIG, "10")
    }

    val offline = chain.any {
        it is FirebaseNetworkException || it is java.io.IOException ||
            it.message?.contains("network", ignoreCase = true) == true ||
            it.message?.contains("unable to resolve host", ignoreCase = true) == true
    }
    if (offline) return SignInFailure(SignInProblem.NO_NETWORK)

    // Anything else: the type, plus the numeric code when there is one, so a screenshot diagnoses it.
    val code = chain.firstNotNullOfOrNull { statusCodeOf(it) }
    val type = error::class.java.simpleName.ifEmpty { error::class.java.name }
    return SignInFailure(SignInProblem.OTHER, if (code != null) "$type, code $code" else type)
}

/** Play services' ApiException exposes getStatusCode(); read it without depending on that class. */
private fun statusCodeOf(t: Throwable): Int? {
    if (!t::class.java.name.endsWith("ApiException")) return null
    return try {
        t::class.java.getMethod("getStatusCode").apply { isAccessible = true }.invoke(t) as? Int
    } catch (e: Exception) {
        null
    }
}
