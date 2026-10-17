package com.example.psychometricvocab

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.example.psychometricvocab.data.sync.SignInFailure
import com.example.psychometricvocab.data.sync.SignInMessages
import com.example.psychometricvocab.data.sync.SignInProblem
import com.example.psychometricvocab.data.sync.classifySignInError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/**
 * Sign-in must always say the REAL reason, never a generic "failed". One test per case, for both
 * the Throwable -> reason classification and the reason -> Hebrew/English text.
 */
class SignInErrorTest {

    // ---- classification: what the sign-in threw -> which reason ----

    @Test
    fun noCredentialMeansNoGoogleAccount() {
        val f = classifySignInError(NoCredentialException("No credentials available"))
        assertEquals(SignInProblem.NO_ACCOUNT, f.problem)
    }

    @Test
    fun noCredentialWrappedInAnotherExceptionIsStillNoAccount() {
        val f = classifySignInError(RuntimeException("wrapper", NoCredentialException("none")))
        assertEquals(SignInProblem.NO_ACCOUNT, f.problem)
    }

    @Test
    fun userCancellationIsCancelledNotAnError() {
        val f = classifySignInError(GetCredentialCancellationException("cancelled"))
        assertEquals(SignInProblem.CANCELLED, f.problem)
    }

    @Test
    fun networkFailureIsNoNetwork() {
        assertEquals(SignInProblem.NO_NETWORK, classifySignInError(UnknownHostException("x")).problem)
        assertEquals(
            SignInProblem.NO_NETWORK,
            classifySignInError(GetCredentialUnknownException("A network error has occurred")).problem
        )
    }

    @Test
    fun developerErrorCode10IsAConfigProblem() {
        // Play services' ApiException(DEVELOPER_ERROR) carries status code 10.
        val viaStatusCode = classifySignInError(RuntimeException("w", ApiException(10)))
        assertEquals(SignInProblem.CONFIG, viaStatusCode.problem)
        assertEquals("10", viaStatusCode.detail)
        // ...and Credential Manager often only surfaces it in the message text.
        val viaMessage = classifySignInError(GetCredentialUnknownException("16: DEVELOPER_ERROR"))
        assertEquals(SignInProblem.CONFIG, viaMessage.problem)
    }

    @Test
    fun anythingElseKeepsTheExceptionTypeAndCode() {
        val plain = classifySignInError(IllegalStateException("boom"))
        assertEquals(SignInProblem.OTHER, plain.problem)
        assertEquals("IllegalStateException", plain.detail)

        val withCode = classifySignInError(ApiException(7))
        assertEquals(SignInProblem.OTHER, withCode.problem)
        assertTrue(withCode.detail.orEmpty().contains("code 7"))
        assertTrue(withCode.detail.orEmpty().contains("ApiException"))
    }

    // ---- messages: reason -> the text the user sees ----

    @Test
    fun noAccountMessageAndAddAccountButton() {
        val f = SignInFailure(SignInProblem.NO_ACCOUNT)
        assertEquals("אין חשבון Google במכשיר", SignInMessages.text(f, isHebrew = true))
        assertEquals("No Google account on this device", SignInMessages.text(f, isHebrew = false))
        assertTrue(SignInMessages.offersAddAccount(f))
    }

    @Test
    fun cancelledShowsNothing() {
        val f = SignInFailure(SignInProblem.CANCELLED)
        assertNull(SignInMessages.text(f, isHebrew = true))
        assertNull(SignInMessages.text(f, isHebrew = false))
        assertFalse(SignInMessages.offersAddAccount(f))
    }

    @Test
    fun noNetworkMessage() {
        val f = SignInFailure(SignInProblem.NO_NETWORK)
        assertEquals("אין חיבור לאינטרנט", SignInMessages.text(f, isHebrew = true))
        assertEquals("No internet connection", SignInMessages.text(f, isHebrew = false))
        assertFalse(SignInMessages.offersAddAccount(f))
    }

    @Test
    fun configMessageShowsTheCode() {
        val f = SignInFailure(SignInProblem.CONFIG, "10")
        assertEquals("שגיאת הגדרה (קוד 10)", SignInMessages.text(f, isHebrew = true))
        assertEquals("Configuration error (code 10)", SignInMessages.text(f, isHebrew = false))
    }

    @Test
    fun otherMessageIncludesTheReasonAndIsNeverBareGeneric() {
        val f = SignInFailure(SignInProblem.OTHER, "IllegalStateException")
        val he = SignInMessages.text(f, isHebrew = true)
        val en = SignInMessages.text(f, isHebrew = false)
        assertNotNull(he)
        assertTrue(he!!.contains("IllegalStateException"))
        assertTrue(en!!.contains("IllegalStateException"))
    }

    /** Stand-in shaped like Play services' ApiException (name ends in ApiException, getStatusCode()). */
    @Suppress("unused")
    private class ApiException(private val code: Int) : RuntimeException("$code: status") {
        fun getStatusCode(): Int = code
    }
}
