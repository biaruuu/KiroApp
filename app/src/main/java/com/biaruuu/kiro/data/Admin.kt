package com.biaruuu.kiro.data

import com.biaruuu.kiro.data.auth.AuthState

/** The developer account. The same address is checked again by the Firestore rules, so hiding the panel is not the only lock. */
object Admin {
    const val EMAIL = "yurischevalier@gmail.com"

    fun isAdmin(state: AuthState): Boolean = state is AuthState.SignedIn && state.email.equals(EMAIL, ignoreCase = true)
}
