package com.example.psychometricvocab.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.AccountManager
import com.example.psychometricvocab.data.AccountProfile
import com.example.psychometricvocab.data.ActivityLog
import com.example.psychometricvocab.data.AppPreferences
import com.example.psychometricvocab.data.QuizPreferences
import com.example.psychometricvocab.data.sync.SyncCoordinator
import com.example.psychometricvocab.data.VocabDatabase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val accountManager = AccountManager(app)
    // Only needed by removeAccount(); opening them in the constructor put the database check and
    // three SharedPreferences reads on the main thread at startup.
    private val database by lazy { VocabDatabase.getInstance(app) }
    private val quizPrefs by lazy { QuizPreferences(app) }
    private val appPrefs by lazy { AppPreferences(app) }
    private val activityLog by lazy { ActivityLog(app) }

    val profile: StateFlow<AccountProfile> = accountManager.profile

    /** False until the stored profile has been read in the background (see AccountManager.load). */
    val loaded: StateFlow<Boolean> = accountManager.loaded

    init {
        viewModelScope.launch { accountManager.load() }
    }

    fun saveProfile(fullName: String, email: String, imageUri: String) {
        accountManager.saveProfile(
            AccountProfile(fullName = fullName, email = email, profileImageUri = imageUri)
        )
    }

    fun removeAccount() {
        accountManager.clearAccount()
        quizPrefs.clear()
        appPrefs.clear()
        activityLog.clear()
        // Reset learning progress only. clearAllTables() also deleted the vocabulary itself, and
        // since seeding only happens when the database is first opened, the app showed 0 words
        // until it was restarted.
        viewModelScope.launch {
            // Disconnect the phone from the cloud account FIRST, locally only: no remote call
            // is made, so the cloud backup is never deleted or overwritten. (The reset below
            // writes updatedAt = 0, which can't beat real progress in any later merge, and with
            // no signed-in user no sync pass runs and nothing is pulled back onto the fresh state.)
            SyncCoordinator.get(getApplication()).signOut()
            database.wordDao().resetAllProgress()
        }
    }

    fun setGuestMode(isGuest: Boolean) {
        accountManager.setGuestMode(isGuest)
    }
}
