package com.example.psychometricvocab.data.sync

/** Why a sign-in did not complete. Each case gets its own message; none is a generic "failed". */
enum class SignInProblem { NO_ACCOUNT, CANCELLED, NO_NETWORK, CONFIG, OTHER }

/**
 * [detail] is the diagnostic bit shown to the user: the numeric code for CONFIG (e.g. "10") and
 * the exception type/code for OTHER, so a screenshot is enough to diagnose. Unused otherwise.
 */
data class SignInFailure(val problem: SignInProblem, val detail: String? = null)

/**
 * The user-visible text for each failure, as a pure function (no Android, no Firebase) so every
 * case is unit-tested. Returns null for a deliberate cancel: that is not an error and shows nothing.
 */
object SignInMessages {
    fun text(failure: SignInFailure, isHebrew: Boolean): String? = when (failure.problem) {
        SignInProblem.CANCELLED -> null
        SignInProblem.NO_ACCOUNT ->
            if (isHebrew) "אין חשבון Google במכשיר" else "No Google account on this device"
        SignInProblem.NO_NETWORK ->
            if (isHebrew) "אין חיבור לאינטרנט" else "No internet connection"
        SignInProblem.CONFIG -> {
            val code = failure.detail ?: "10"
            if (isHebrew) "שגיאת הגדרה (קוד $code)" else "Configuration error (code $code)"
        }
        SignInProblem.OTHER -> {
            val reason = failure.detail ?: "unknown"
            if (isHebrew) "ההתחברות נכשלה ($reason)" else "Sign-in failed ($reason)"
        }
    }

    /** Only "no Google account" offers the add-account button. */
    fun offersAddAccount(failure: SignInFailure): Boolean = failure.problem == SignInProblem.NO_ACCOUNT
}
