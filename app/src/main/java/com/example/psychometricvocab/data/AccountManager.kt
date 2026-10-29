package com.example.psychometricvocab.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountProfile(
    val fullName: String = "",
    val email: String = "",
    val profileImageUri: String = "",
    val isGuest: Boolean = false
)

class AccountManager(context: Context) {
    private val appContext = context.applicationContext

    // The encrypted store needs an Android Keystore key and Tink, which is slow on a cold first
    // launch (a fresh install runs un-optimised code): it used to be built in this constructor,
    // on the main thread, while the first screen was waiting. Now it is created on first use,
    // and [load] (below) makes that first use a background one.
    private val sharedPreferences by lazy {
        StartupTrace.time("EncryptedSharedPreferences create") {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "secret_account_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val _profile = MutableStateFlow(AccountProfile())
    val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

    // False until [load] has read the stored profile. Until then `profile` is just the empty
    // default, which must NOT be taken for "no account yet" (that would flash the registration
    // screen at an existing user).
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Reads the stored profile off the main thread, then flips [loaded]. */
    suspend fun load() {
        val stored = withContext(Dispatchers.IO) { loadProfile() }
        _profile.value = stored
        _loaded.value = true
    }

    private fun loadProfile(): AccountProfile {
        return AccountProfile(
            fullName = sharedPreferences.getString("full_name", "") ?: "",
            email = sharedPreferences.getString("email", "") ?: "",
            profileImageUri = sharedPreferences.getString("profile_image_uri", "") ?: "",
            isGuest = sharedPreferences.getBoolean("is_guest", false)
        )
    }

    fun saveProfile(profile: AccountProfile) {
        sharedPreferences.edit()
            .putString("full_name", profile.fullName)
            .putString("email", profile.email)
            .putString("profile_image_uri", profile.profileImageUri)
            .putBoolean("is_guest", profile.isGuest)
            .apply()
        _profile.value = profile
    }

    fun clearAccount() {
        sharedPreferences.edit().clear().apply()
        _profile.value = AccountProfile()
    }
    
    fun hasAccount(): Boolean {
        return sharedPreferences.getString("full_name", "")?.isNotEmpty() == true
    }

    fun setGuestMode(isGuest: Boolean) {
        saveProfile(_profile.value.copy(isGuest = isGuest))
    }
}
