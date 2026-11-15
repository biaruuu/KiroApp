package com.biaruuu.kiro.ui.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.delay

private val Field = Color(0xFF1E1E22)
private val Muted = Color(0xFF9A9AA5)
private val ErrorRed = Color(0xFFFF6B6B)

/**
 * Shows [content] once the user is signed in with a verified email. When Firebase isn't set up
 * in this build, sign-in is skipped and [content] shows straight away.
 */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configured = remember { AuthRepository.start(context) }
    if (!configured) {
        content()
        return
    }
    val state by AuthRepository.state.collectAsState()
    when (val s = state) {
        AuthState.Loading -> Box(Modifier.fillMaxSize().background(KiroBackground))
        AuthState.SignedOut -> AuthScreen()
        is AuthState.NeedsVerification -> VerifyScreen(s.email)
        is AuthState.SignedIn -> content()
    }
}

private const val MODE_LOGIN = 0
private const val MODE_SIGNUP = 1
private const val MODE_FORGOT = 2

@Composable
private fun AuthFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(KiroBackground).statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("KIRO", color = KiroPurple, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}

@Composable
private fun AuthScreen() {
    var mode by rememberSaveable { mutableIntStateOf(MODE_LOGIN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun switchTo(m: Int) {
        mode = m
        error = null
        info = null
    }

    fun submit() {
        error = null
        info = null
        if (mode == MODE_SIGNUP && name.trim().length < 2) {
            error = "Enter a username."
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            error = "Enter a valid email address."
            return
        }
        if (mode != MODE_FORGOT && password.length < 6) {
            error = "Password must be at least 6 characters."
            return
        }
        busy = true
        val done: (String?) -> Unit = { err ->
            busy = false
            error = err
            // Success on sign-in / sign-up moves to the next screen by itself.
            if (err == null && mode == MODE_FORGOT) info = "If an account exists, a reset link is on its way."
        }
        when (mode) {
            MODE_LOGIN -> AuthRepository.signIn(email, password, done)
            MODE_SIGNUP -> AuthRepository.signUp(name, email, password, done)
            else -> AuthRepository.resetPassword(email, done)
        }
    }

    AuthFrame {
        Text(
            when (mode) {
                MODE_LOGIN -> "Welcome back"
                MODE_SIGNUP -> "Create your account"
                else -> "Reset your password"
            },
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (mode) {
                MODE_LOGIN -> "Sign in to keep watching."
                MODE_SIGNUP -> "We'll email you a link to verify your address."
                else -> "Enter your email and we'll send a reset link."
            },
            color = Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        if (mode == MODE_SIGNUP) {
            AuthField(name, { name = it }, "Username")
            Spacer(Modifier.height(12.dp))
        }
        AuthField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        if (mode != MODE_FORGOT) {
            Spacer(Modifier.height(12.dp))
            AuthField(
                password, { password = it }, "Password",
                keyboardType = KeyboardType.Password,
                transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show", color = Muted, fontSize = 13.sp)
                    }
                },
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = ErrorRed, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFF7BD88F), fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            label = when (mode) {
                MODE_LOGIN -> "Sign in"
                MODE_SIGNUP -> "Sign up"
                else -> "Send reset link"
            },
            busy = busy,
            onClick = { if (!busy) submit() },
        )

        Spacer(Modifier.height(12.dp))
        if (mode == MODE_LOGIN) {
            TextButton(onClick = { switchTo(MODE_FORGOT) }) { Text("Forgot password?", color = Muted) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (mode) {
                    MODE_LOGIN -> "New to Kiro?"
                    MODE_SIGNUP -> "Already have an account?"
                    else -> "Remembered it?"
                },
                color = Muted,
                fontSize = 15.sp,
            )
            TextButton(onClick = { switchTo(if (mode == MODE_LOGIN) MODE_SIGNUP else MODE_LOGIN) }) {
                Text(
                    if (mode == MODE_LOGIN) "Sign up" else "Sign in",
                    color = KiroPurple,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun VerifyScreen(email: String) {
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(30) }

    // Count down before "Resend" can be used again.
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown--
        }
    }

    fun check() {
        busy = true
        AuthRepository.checkVerified { ok ->
            busy = false
            if (!ok) message = "Not verified yet. Open the link in the email first."
        }
    }

    // Coming back from the mail app continues automatically.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) AuthRepository.checkVerified { }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    AuthFrame {
        Text("Verify your email", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "We sent a verification link to\n$email\nOpen it, then come back here.",
            color = Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = if (it.startsWith("Verification email")) Color(0xFF7BD88F) else ErrorRed, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("I've verified", busy) { if (!busy) check() }
        Spacer(Modifier.height(8.dp))
        TextButton(
            enabled = cooldown == 0,
            onClick = {
                cooldown = 30
                AuthRepository.resendVerification { err ->
                    message = err ?: "Verification email sent again."
                }
            },
        ) {
            Text(if (cooldown > 0) "Resend email ($cooldown)" else "Resend email", color = if (cooldown > 0) Muted else KiroPurple)
        }
        TextButton(onClick = { AuthRepository.signOut() }) { Text("Use a different account", color = Muted) }
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailing,
        shape = RoundedCornerShape(6.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Field,
            unfocusedContainerColor = Field,
            focusedIndicatorColor = KiroPurple,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = KiroPurple,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Muted,
            unfocusedLabelColor = Muted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(KiroPurple, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
