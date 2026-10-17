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

    /** The reason the last sign-in failed (null = none to show). A cancel is never reported. */
    private val _failure = MutableStateFlow<SignInFailure?>(null)
    val failure: StateFlow<SignInFailure?> = _failure.asStateFlow()

    fun signIn(activity: Activity) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = coordinator.signIn(activity)
            _busy.value = false
            when (result) {
                is SignInResult.Success, SignInResult.Cancelled -> Unit
                SignInResult.Unavailable ->
                    _failure.value = SignInFailure(SignInProblem.OTHER, "no Firebase config")
                is SignInResult.Failed -> _failure.value = result.failure
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { coordinator.signOut() }
    }

    fun consumeFailure() {
        _failure.value = null
    }
}
