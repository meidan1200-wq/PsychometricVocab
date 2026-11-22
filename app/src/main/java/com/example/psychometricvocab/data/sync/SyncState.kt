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

    // ---- layout v2: its own cursors (one per source: words, days), full-pull time, and the layout last seen ----

    fun v2Cursor(uid: String, source: String): Long? = long(v2CursorKey(uid, source))

    fun setV2Cursor(uid: String, source: String, micros: Long) {
        prefs.edit().putLong(v2CursorKey(uid, source), micros).apply()
    }

    fun lastFullPullV2At(uid: String, source: String): Long? = long("sync_full_at_v2${source}_$uid")

    fun setLastFullPullV2At(uid: String, source: String, millis: Long) {
        prefs.edit().putLong("sync_full_at_v2${source}_$uid", millis).apply()
    }

    /** The layout the account was last seen in; used when the cloud can't be asked. */
    fun lastLayout(uid: String): Layout? = prefs.getString("sync_layout_v1_$uid", null)?.let { Layout.parse(it) }

    fun setLastLayout(uid: String, layout: Layout) {
        prefs.edit().putString("sync_layout_v1_$uid", layout.wire).apply()
    }

    fun lastDeviceRegisteredAt(uid: String): Long? = long("sync_device_at_v1_$uid")

    fun setLastDeviceRegisteredAt(uid: String, millis: Long) {
        prefs.edit().putLong("sync_device_at_v1_$uid", millis).apply()
    }

    /** A random id for this install, kept for good: the part after "vocab-" in the device registry. */
    @Synchronized
    fun deviceId(): String =
        prefs.getString("sync_device_id_v1", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").take(12).also {
            prefs.edit().putString("sync_device_id_v1", it).apply()
        }

    private fun long(key: String): Long? = prefs.takeIf { it.contains(key) }?.getLong(key, 0L)

    private fun v2CursorKey(uid: String, source: String) = "sync_cursor_v2${source}_$uid"

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
