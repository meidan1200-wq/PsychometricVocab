package com.example.psychometricvocab.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.psychometricvocab.data.AccountManager
import com.example.psychometricvocab.data.AccountProfile
import com.example.psychometricvocab.data.VocabDatabase
import kotlinx.coroutines.flow.StateFlow

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val accountManager = AccountManager(app)
    private val database = VocabDatabase.getInstance(app)

    val profile: StateFlow<AccountProfile> = accountManager.profile

    fun saveProfile(fullName: String, email: String, imageUri: String) {
        accountManager.saveProfile(
            AccountProfile(fullName = fullName, email = email, profileImageUri = imageUri)
        )
    }

    fun removeAccount() {
        accountManager.clearAccount()
        // Optional: clear database progress when removing account to simulate "all data deleted"
        // For now we just clear the account profile, but we can also clear the DB.
        Thread { database.clearAllTables() }.start()
    }

    fun setGuestMode(isGuest: Boolean) {
        accountManager.setGuestMode(isGuest)
    }
}
