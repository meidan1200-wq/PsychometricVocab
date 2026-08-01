package com.example.psychometricvocab.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_account_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

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
