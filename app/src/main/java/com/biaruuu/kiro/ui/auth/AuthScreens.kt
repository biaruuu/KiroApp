package com.biaruuu.kiro.ui.auth

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.AppSettings
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.data.auth.GoogleSignIn
import com.biaruuu.kiro.ui.components.PageTopBar
import com.biaruuu.kiro.ui.theme.AppFont
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Muted = Color(0xFF9A9A9F)
private val HintGray = Color(0xFF5E5E63)
private val ButtonGray = Color(0xFF222226)
private val BoxBorder = Color(0xFF2A2A2E)
private val ErrorRed = Color(0xFFFF6B6B)

/**
 * Shows [content] once the user is signed in with a verified email, or chose to look around as a
 * guest. When Firebase isn't set up in this build, sign-in is skipped entirely.
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
    val guest by AuthRepository.guest.collectAsState()
    when (val s = state) {
        AuthState.Loading -> Box(Modifier.fillMaxSize().background(KiroBackground))
        AuthState.SignedOut -> if (guest) content() else LoginFlow()
        is AuthState.NeedsVerification -> VerifyScreen(s.email)
        is AuthState.SignedIn -> ProfileGate(s.uid, s.name, content)
    }
}

@Composable
private fun LoginFlow() {
    // 0 welcome, 1 email, 2 guest confirmation, 3 terms, 4 privacy
    var screen by rememberSaveable { mutableIntStateOf(0) }
    when (screen) {
        0 -> WelcomeScreen(
            onEmail = { screen = 1 },
            onGuest = { screen = 2 },
            onTerms = { screen = 3 },
            onPrivacy = { screen = 4 },
        )
        1 -> EmailFlow(onBack = { screen = 0 })
        2 -> GuestConfirm(onBack = { screen = 0 })
        else -> {
            BackHandler { screen = 0 }
            CenteredPage {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    PageTopBar(if (screen == 3) "Terms of Use" else "Privacy Policy", onBack = { screen = 0 })
                    com.biaruuu.kiro.ui.legal.LegalContent(terms = screen == 3)
                }
            }
        }
    }
}

/** The "are you sure?" page shown before browsing without an account. */
@Composable
private fun GuestConfirm(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    CenteredPage {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageTopBar("Continue as Guest", onBack = onBack)
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp)) {
                Text(
                    "Are you sure you want to continue as a guest?",
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "You won't save your progress. As a guest, your My List, ratings, Continue Watching and watched episodes are not saved or synced.",
                    color = Color(0xFFB4B4BA),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "You can log in or create an account any time from the Me tab.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                PrimaryButton(label = "Yes, continue as guest", enabled = true, busy = false, onClick = { AuthRepository.continueAsGuest() })
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(26.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Go back", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Keeps page content a comfortable width on tablets and in landscape. */
@Composable
internal fun CenteredPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(KiroBackground), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = 520.dp).fillMaxSize()) { content() }
    }
}

// ---------------------------------------------------------------- Welcome

@Composable
private fun WelcomeScreen(onEmail: () -> Unit, onGuest: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastLogin = remember { AppSettings.lastLogin(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CenteredPage {
        LoginBackdrop()
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("KIRO", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = 10.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Discover your favorite anime and\ncustomize your library.",
                color = Color(0xFF9A9AA2),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            Box(Modifier.fillMaxWidth()) {
                WideButton(
                    label = "Continue with Email",
                    icon = R.drawable.fluent_mail_filled,
                    iconTint = Color.Black,
                    container = Color.White,
                    contentColor = Color.Black,
                    onClick = onEmail,
                )
                if (lastLogin == "email") LastLoginBubble(Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) {
                WideButton(
                    label = "Continue with Google",
                    icon = R.drawable.google_g_logo,
                    iconTint = null,
                    container = Color.Transparent,
                    outlined = true,
                    busy = busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            error = null
                            scope.launch {
                                val r = GoogleSignIn.signIn(context)
                                busy = false
                                if (r == null) AppSettings.setLastLogin(context, "google")
                                else if (r.isNotEmpty()) error = r
                            }
                        }
                    },
                )
                if (lastLogin == "google") LastLoginBubble(Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Continue as guest",
                color = Color(0xFF9A9AA2),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onGuest).padding(horizontal = 20.dp, vertical = 12.dp),
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = ErrorRed, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            val link = SpanStyle(textDecoration = TextDecoration.Underline)
            val legal = buildAnnotatedString {
                append("By creating an account, you agree to the ")
                pushStringAnnotation("page", "terms")
                withStyle(link) { append("Terms of Use") }
                pop()
                append(" and ")
                pushStringAnnotation("page", "privacy")
                withStyle(link) { append("Privacy Policy") }
                pop()
                append(".")
            }
            androidx.compose.foundation.text.ClickableText(
                text = legal,
                style = TextStyle(color = Color(0xFF5E5E63), fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 4.dp),
                onClick = { offset ->
                    legal.getStringAnnotations("page", offset, offset).firstOrNull()?.let {
                        if (it.item == "terms") onTerms() else onPrivacy()
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * One trending anime trailer playing muted across the top of the page (below a black status bar),
 * melting into the dark page before the buttons, like Laftel's login. A still of the show shows
 * first and stays if the trailer can't play. It does not switch shows.
 */
@Composable
private fun LoginBackdrop() {
    var anime by remember { mutableStateOf<com.biaruuu.kiro.data.Anime?>(null) }
    var videoId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val heroes = com.biaruuu.kiro.data.AniListRepository.loadHome().hero.take(5)
            anime = heroes.firstOrNull()
            // Use the first show that has a trailer, then stay on it.
            for (h in heroes) {
                val ids = com.biaruuu.kiro.data.AniListRepository.tmdbTrailers(h)
                if (ids.isNotEmpty()) {
                    anime = h
                    videoId = ids.first()
                    break
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(KiroBackground)) {
        val areaHeight = maxHeight * 0.58f
        Box(Modifier.fillMaxWidth().height(areaHeight).statusBarsPadding().clipToBounds()) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val coverWidth = maxOf(maxWidth, maxHeight * (16f / 9f)) * 1.05f
                val coverHeight = coverWidth * (9f / 16f)
                val still = anime?.heroUrl
                if (still != null) {
                    coil.compose.AsyncImage(
                        model = still,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val id = videoId
                if (id != null) {
                    androidx.compose.runtime.key(id) {
                        val alpha by androidx.compose.animation.core.animateFloatAsState(
                            if (playing) 1f else 0f,
                            androidx.compose.animation.core.tween(700),
                            label = "login-video",
                        )
                        com.biaruuu.kiro.ui.detail.TrailerPlayer(
                            videoId = id,
                            muted = true,
                            onPlaying = { playing = true },
                            onFailed = { videoId = null },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(coverWidth, coverHeight)
                                .alpha(alpha),
                        )
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.5f to Color.Black.copy(alpha = 0.35f),
                            1f to KiroBackground,
                        ),
                    ),
                )
            }
        }
        // The status bar stays solid black, as in the reference.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
    }
}

@Composable
private fun LastLoginBubble(modifier: Modifier = Modifier) {
    Column(modifier = modifier.offset(y = (-27).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Last login",
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Canvas(Modifier.size(width = 14.dp, height = 7.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(p, Color.White)
        }
    }
}

@Composable
private fun WideButton(
    label: String,
    icon: Int,
    iconTint: Color?,
    container: Color,
    onClick: () -> Unit,
    busy: Boolean = false,
    contentColor: Color = Color.White,
    outlined: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .then(if (outlined) Modifier.border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(26.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconTint != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp).size(20.dp),
            )
        } else {
            androidx.compose.foundation.Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp).size(18.dp),
            )
        }
        if (busy) {
            CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------- Email flow

@Composable
private fun EmailFlow(onBack: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var signUp by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    BackHandler {
        error = null
        info = null
        if (step == 1) step = 0 else onBack()
    }

    val emailOk = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordOk = if (signUp) password.length >= 6 else password.isNotEmpty()
    val canContinue = if (step == 0) emailOk else passwordOk && !busy

    fun submit() {
        if (step == 0) {
            if (emailOk) step = 1
            return
        }
        if (!canContinue) return
        busy = true
        error = null
        info = null
        val done: (String?) -> Unit = { err ->
            busy = false
            error = err
            if (err == null) AppSettings.setLastLogin(context, "email")
        }
        if (signUp) AuthRepository.signUp(email, password, done) else AuthRepository.signIn(email, password, done)
    }

    CenteredPage {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            PageTopBar(
                title = if (step == 0) "Continue with email" else if (signUp) "Create account" else "Log in",
                onBack = {
                    error = null
                    info = null
                    if (step == 1) step = 0 else onBack()
                },
            )
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
                if (step == 0) {
                    Text("Email", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    UnderlineField(
                        value = email,
                        onChange = { email = it },
                        hint = "Enter email",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onImeAction = { submit() },
                    )
                    Spacer(Modifier.height(16.dp))
                    CheckBox("Include a valid email format (e.g. @email.com)", emailOk)
                } else {
                    Text(email.trim(), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Password", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    UnderlineField(
                        value = password,
                        onChange = { password = it },
                        hint = "Enter password",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = { submit() },
                        transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            Text(
                                if (showPassword) "Hide" else "Show",
                                color = Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { showPassword = !showPassword }.padding(8.dp),
                            )
                        },
                    )
                    if (signUp) {
                        Spacer(Modifier.height(16.dp))
                        CheckBox("Use at least 6 characters", password.length >= 6)
                    }
                    error?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = ErrorRed, fontSize = 13.sp)
                    }
                    info?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = Color(0xFF7BD88F), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    if (!signUp) {
                        Text(
                            "Forgot password?",
                            color = Muted,
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                error = null
                                AuthRepository.resetPassword(email) { err ->
                                    if (err == null) info = "If an account exists, a reset link is on its way."
                                    else error = err
                                }
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(
                        if (signUp) "Already have an account? Log in" else "New to Kiro? Create account",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            signUp = !signUp
                            error = null
                            info = null
                        },
                    )
                }
            }
            PrimaryButton(
                label = if (step == 0) "Next" else if (signUp) "Sign up" else "Log in",
                enabled = canContinue,
                busy = busy,
                onClick = { submit() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
internal fun UnderlineField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = AppFont)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = style,
                cursorBrush = SolidColor(Color.White),
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
                modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 12.dp)) {
                        if (value.isEmpty()) Text(hint, color = HintGray, fontSize = 16.sp, fontWeight = FontWeight.Normal)
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (focused) Color.White else Color(0xFF3A3A3F)))
    }
}

@Composable
internal fun CheckBox(text: String, ok: Boolean, icon: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181B), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(
                painter = painterResource(R.drawable.fluent_checkmark_regular),
                contentDescription = null,
                tint = if (ok) Color.White else HintGray,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(text, color = Color(0xFF9A9AA2), fontSize = 13.sp)
    }
}

@Composable
internal fun PrimaryButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Color.White else ButtonGray)
            .clickable(
                enabled = enabled && !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = if (enabled) Color.Black else Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, color = if (enabled) Color.Black else HintGray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------- Verify email

@Composable
private fun VerifyScreen(email: String) {
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var good by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(30) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown--
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

    CenteredPage {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageTopBar("Verify your email", onBack = null)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    "We sent a verification link to",
                    color = Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(email, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Open it, then come back here.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                CheckBox("Please check your spam folder if you don't see the email.", false, icon = false)
                message?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = if (good) Color(0xFF7BD88F) else ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    if (cooldown > 0) "Resend email ($cooldown)" else "Resend email",
                    color = if (cooldown > 0) HintGray else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = cooldown == 0) {
                            cooldown = 30
                            AuthRepository.resendVerification { err ->
                                good = err == null
                                message = err ?: "Verification email sent again."
                            }
                        }
                        .padding(10.dp),
                )
                Text(
                    "Use a different account",
                    color = Muted,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { AuthRepository.signOut() }.padding(10.dp),
                )
            }
            PrimaryButton(
                label = "I've verified",
                enabled = true,
                busy = busy,
                onClick = {
                    busy = true
                    AuthRepository.checkVerified { ok ->
                        busy = false
                        if (!ok) {
                            good = false
                            message = "Not verified yet. Open the link in the email first."
                        }
                    }
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
