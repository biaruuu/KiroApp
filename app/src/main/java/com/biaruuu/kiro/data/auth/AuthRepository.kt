package com.biaruuu.kiro.data.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the user is in the sign-in flow. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class NeedsVerification(val email: String) : AuthState
    data class SignedIn(val email: String, val name: String, val google: Boolean) : AuthState
}

/**
 * Email + password accounts on Firebase Authentication. Every sign-up gets a verification email
 * and the app stays locked until the address is confirmed.
 *
 * Firebase only exists when the build had a google-services.json; [start] returns false
 * otherwise and the app simply skips sign-in.
 */
object AuthRepository {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var started = false

    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context.applicationContext).isNotEmpty()

    /** Starts listening to the signed-in user. Returns false when Firebase is not set up. */
    @Synchronized
    fun start(context: Context): Boolean {
        if (!isConfigured(context)) return false
        if (!started) {
            started = true
            FirebaseAuth.getInstance().addAuthStateListener { publish(it.currentUser) }
        }
        return true
    }

    private fun publish(user: FirebaseUser?) {
        _state.value = when {
            user == null -> AuthState.SignedOut
            !user.isEmailVerified -> AuthState.NeedsVerification(user.email.orEmpty())
            else -> {
                val email = user.email.orEmpty()
                val name = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
                AuthState.SignedIn(email, name, user.providerData.any { it.providerId == "google.com" })
            }
        }
    }

    /** [onDone] gets null on success, or a message that is safe to show. */
    fun signIn(email: String, password: String, onDone: (String?) -> Unit) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { t -> onDone(if (t.isSuccessful) null else message(t.exception)) }
    }

    fun signUp(email: String, password: String, onDone: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { t ->
            val user = t.result?.user
            if (!t.isSuccessful || user == null) {
                onDone(message(t.exception))
                return@addOnCompleteListener
            }
            user.sendEmailVerification()
            // The state listener already moved us to the "verify your email" screen.
            onDone(null)
        }
    }

    fun resendVerification(onDone: (String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onDone("You are signed out.")
            return
        }
        user.sendEmailVerification().addOnCompleteListener { t -> onDone(if (t.isSuccessful) null else message(t.exception)) }
    }

    /** Asks Firebase again whether the address is verified. [onDone] gets true when it is. */
    fun checkVerified(onDone: (Boolean) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onDone(false)
            return
        }
        user.reload().addOnCompleteListener {
            val fresh = FirebaseAuth.getInstance().currentUser
            publish(fresh)
            onDone(fresh?.isEmailVerified == true)
        }
    }

    fun resetPassword(email: String, onDone: (String?) -> Unit) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).addOnCompleteListener { t ->
            val e = t.exception
            // Don't reveal which addresses have an account.
            onDone(if (t.isSuccessful || e is FirebaseAuthInvalidUserException) null else message(e))
        }
    }

    /** Signs in with the ID token Google handed back (Google accounts are already verified). */
    fun signInWithGoogle(idToken: String, onDone: (String?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { t -> onDone(if (t.isSuccessful) null else message(t.exception)) }
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        _guest.value = false
    }

    /** Permanently deletes the account. Firebase wants a recent sign-in for this. */
    fun deleteAccount(onDone: (String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onDone("You are signed out.")
            return
        }
        user.delete().addOnCompleteListener { t ->
            if (t.isSuccessful) {
                _guest.value = false
                onDone(null)
            } else if (t.exception is FirebaseAuthRecentLoginRequiredException) {
                onDone("For security, log out, log in again, then delete your account.")
            } else {
                onDone(message(t.exception))
            }
        }
    }

    private val _guest = MutableStateFlow(false)

    /** True while the user chose to look around without an account. */
    val guest: StateFlow<Boolean> = _guest.asStateFlow()

    fun continueAsGuest() {
        _guest.value = true
    }

    fun requestLogin() {
        _guest.value = false
    }

    private fun message(e: Exception?): String = when (e) {
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException ->
            "Incorrect email or password."
        is FirebaseAuthUserCollisionException -> "An account with this email already exists."
        is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters."
        is FirebaseNetworkException -> "No internet connection."
        is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later."
        else -> "Something went wrong. Please try again."
    }
}
