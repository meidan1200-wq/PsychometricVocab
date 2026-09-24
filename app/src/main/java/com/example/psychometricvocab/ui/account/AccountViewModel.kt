package com.example.psychometricvocab.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.AccountManager
import com.example.psychometricvocab.data.AccountProfile
import com.example.psychometricvocab.data.QuizPreferences
import com.example.psychometricvocab.data.VocabDatabase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val accountManager = AccountManager(app)
    private val database = VocabDatabase.getInstance(app)
    private val quizPrefs = QuizPreferences(app)

    val profile: StateFlow<AccountProfile> = accountManager.profile

    fun saveProfile(fullName: String, email: String, imageUri: String) {
        accountManager.saveProfile(
            AccountProfile(fullName = fullName, email = email, profileImageUri = imageUri)
        )
    }

    fun removeAccount() {
        accountManager.clearAccount()
        quizPrefs.clear()
        // Reset learning progress only. clearAllTables() also deleted the vocabulary itself, and
        // since seeding only happens when the database is first opened, the app showed 0 words
        // until it was restarted.
        viewModelScope.launch { database.wordDao().resetAllProgress() }
    }

    fun setGuestMode(isGuest: Boolean) {
        accountManager.setGuestMode(isGuest)
    }
}
