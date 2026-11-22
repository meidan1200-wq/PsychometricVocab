package com.example.psychometricvocab.data.sync

import android.content.SharedPreferences

/**
 * What this phone remembers about its sync with the cloud, per account (uid), in the sync
 * preferences file:
 *  - the incremental-pull cursor and the time of the last full pull (see [SyncCursor]);
 *  - what changed locally and has not been handed to the cloud SDK yet ("pending"): word ids and
 *    activity days. Persisted, so a process killed inside the push debounce doesn't lose them: an
 *    incremental pass can no longer rediscover them by comparing against the whole cloud.
 *
 * Keys are versioned (`_v1_`) so a future change of meaning can't be misread.
 */
class SyncState(private val prefs: SharedPreferences) {
    fun cursor(uid: String): Long? = prefs.takeIf { it.contains(cursorKey(uid)) }?.getLong(cursorKey(uid), 0L)

    fun setCursor(uid: String, micros: Long) {
        prefs.edit().putLong(cursorKey(uid), micros).apply()
    }

    fun lastFullPullAt(uid: String): Long? =
        prefs.takeIf { it.contains(fullKey(uid)) }?.getLong(fullKey(uid), 0L)

    fun setLastFullPullAt(uid: String, millis: Long) {
        prefs.edit().putLong(fullKey(uid), millis).apply()
    }

    /** Remember that this word (and today's activity day) changed locally and still has to be pushed. */
    @Synchronized
    fun addPending(uid: String, stableId: String, day: String) {
        prefs.edit()
            .putStringSet(wordsKey(uid), set(wordsKey(uid)) + stableId)
            .putStringSet(daysKey(uid), set(daysKey(uid)) + day)
            .apply()
    }

    @Synchronized
    fun pendingWords(uid: String): Set<String> = set(wordsKey(uid))

    @Synchronized
    fun pendingDays(uid: String): Set<String> = set(daysKey(uid))

    /** Forget exactly these (what a pass or push has just handed over); anything added meanwhile stays. */
    @Synchronized
    fun clearPending(uid: String, words: Set<String>, days: Set<String>) {
        prefs.edit()
            .putStringSet(wordsKey(uid), set(wordsKey(uid)) - words)
            .putStringSet(daysKey(uid), set(daysKey(uid)) - days)
            .apply()
    }

    // getStringSet's result must not be modified, and may be a live view: always copy.
    private fun set(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    private fun cursorKey(uid: String) = "sync_cursor_v1_$uid"
    private fun fullKey(uid: String) = "sync_full_at_v1_$uid"
    private fun wordsKey(uid: String) = "sync_pending_words_v1_$uid"
    private fun daysKey(uid: String) = "sync_pending_days_v1_$uid"
}
