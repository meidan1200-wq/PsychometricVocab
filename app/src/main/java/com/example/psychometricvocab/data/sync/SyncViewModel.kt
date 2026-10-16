package com.example.psychometricvocab.data.sync

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the single "Cloud backup" row in Settings. */
class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val coordinator = SyncCoordinator.get(app)

    val user: StateFlow<SyncUser?> = coordinator.cloud.user
    val isAvailable: Boolean = coordinator.cloud.isAvailable

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** One-shot message for the UI to show (and then clear): null, "cancelled" is silent. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signIn(activity: Activity) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = coordinator.signIn(activity)
            _busy.value = false
            when (result) {
                is SignInResult.Success, SignInResult.Cancelled -> Unit
                SignInResult.Unavailable -> _error.value = "unavailable"
                is SignInResult.Failed -> _error.value = result.message ?: "failed"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { coordinator.signOut() }
    }

    fun consumeError() {
        _error.value = null
    }
}
