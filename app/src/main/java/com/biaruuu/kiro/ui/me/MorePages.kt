package com.biaruuu.kiro.ui.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.BuildConfig
import com.biaruuu.kiro.data.ApkInstaller
import com.biaruuu.kiro.data.CloudSync
import com.biaruuu.kiro.data.UpdateChecker
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.auth.PrimaryButton
import com.biaruuu.kiro.ui.auth.UnderlineField
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.google.firebase.firestore.FirebaseFirestore

private val Muted = Color(0xFF9A9A9F)
private val Body = Color(0xFFB4B4BA)

/**
 * The "What's new" page. [latest] shows the newest published version with an Update Now button;
 * otherwise it shows what changed in the version you are running (opened once after updating).
 */
@Composable
fun WhatsNewPage(latest: Boolean, onBack: () -> Unit, version: String? = null) {
    val context = LocalContext.current
    val current = UpdateChecker.currentVersion
    var release by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(latest, version) {
        release = if (version != null) UpdateChecker.forVersion(version) else if (latest) UpdateChecker.latest() else UpdateChecker.forVersion(current)
        loading = false
    }

    val rel = release
    val newer = rel != null && UpdateChecker.isNewer(rel.version, current)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(22.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KiroPurple, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else if (rel == null) {
                Text("Version $current", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("We couldn't load the details right now. Please check your connection.", color = Body, fontSize = 14.sp, lineHeight = 21.sp)
            } else {
                if (newer) {
                    Text(
                        "NEW VERSION",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(KiroPurple, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text("Kiro ${rel.version}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (!newer && latest) {
                    Spacer(Modifier.height(6.dp))
                    Text("You're up to date.", color = Muted, fontSize = 13.sp)
                }
                ReleaseNotes(rel.notes)
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)) {
            if (newer && rel != null) {
                PrimaryButton(
                    label = "Update Now",
                    enabled = true,
                    busy = false,
                    onClick = { ApkInstaller.start(context, rel.apkUrl, rel.version) },
                )
            } else {
                PrimaryButton(label = "Done", enabled = true, busy = false, onClick = onBack)
            }
        }
    }
}

/** Draws release notes written in simple markdown: "### Heading" and "- bullet" lines. */
@Composable
private fun ReleaseNotes(notes: String) {
    val clean = { t: String -> t.replace("**", "").replace("`", "").trim() }
    for (raw in notes.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() || line.startsWith("## ") || line.startsWith("# ") -> Unit
            line.startsWith("###") -> {
                Spacer(Modifier.height(20.dp))
                Text(clean(line.trimStart('#')), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                Spacer(Modifier.height(10.dp))
                Row {
                    Box(Modifier.padding(top = 8.dp).size(5.dp).background(KiroPurple, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Text(clean(line.drop(2)), color = Body, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                }
            }
            else -> {
                Spacer(Modifier.height(10.dp))
                Text(clean(line), color = Body, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** Me > Version: current and latest version; Update Now downloads and installs it. */
@Composable
fun VersionPage() {
    val context = LocalContext.current
    val current = UpdateChecker.currentVersion
    var release by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    LaunchedEffect(Unit) { release = UpdateChecker.latest() }
    val rel = release
    val canUpdate = rel != null && UpdateChecker.isNewer(rel.version, current)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement_Center,
    ) {
        Text("Kiro Android", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("Latest Version ${rel?.version ?: current}", color = Body, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text("Current Version $current", color = Body, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            label = "Update Now",
            enabled = canUpdate,
            busy = false,
            onClick = { if (rel != null) ApkInstaller.start(context, rel.apkUrl, rel.version) },
        )
    }
}

private val Arrangement_Center = androidx.compose.foundation.layout.Arrangement.Center

private val Faq = listOf(
    "How do I save an anime to My List?" to "Log in, open an anime and tap My List. Guests can browse, but nothing is saved without an account.",
    "How do I rate an anime?" to "Open the anime, tap Rate, pick a score (tap the left half of a star for a half star) and press Confirm.",
    "My library isn't showing on my other device." to "Make sure you are logged in with the same account and connected to the internet. Changes sync in real time.",
    "An episode won't play." to "Check your connection, then try again or pick another episode. Some episodes may not be available yet.",
    "How do I change my username, bio or photo?" to "Open Me, then Account Setting. Tap the photo, Username or Bio.",
    "How do I update Kiro?" to "Kiro tells you when a new version is out. You can also open Me, then Version, and tap Update Now.",
    "How do I delete my account?" to "Open Me, Account Setting, then Delete account. Your profile and library are deleted.",
)

@Composable
fun HelpPage() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        for ((q, a) in Faq) {
            Spacer(Modifier.height(22.dp))
            Text(q, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(a, color = Body, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text("Still stuck? Use Contact us to send us a message.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
    }
}

/** Send the developer a message. It lands in the admin panel's Letters tab with your username. */
@Composable
fun ContactPage(onDone: () -> Unit) {
    val context = LocalContext.current
    val state by AuthRepository.state.collectAsState()
    val profile by com.biaruuu.kiro.data.ProfileRepository.profile.collectAsState()
    val signedIn = state as? AuthState.SignedIn
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val topics = listOf("Bug", "Suggestion", "Question", "Other")
    var topic by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    if (sent) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(KiroPurple), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(com.biaruuu.kiro.R.drawable.fluent_checkmark_regular),
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Message sent", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Thanks! The developer reads every message.", color = Body, fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            PrimaryButton(label = "Done", enabled = true, busy = false, onClick = onDone)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (signedIn == null) {
                Text("Log in to send the developer a message.", color = Body, fontSize = 14.sp)
            } else {
                Text("What's it about?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val hints = listOf("Something isn't working", "An idea to make Kiro better", "Ask the developer anything", "Anything else")
                Column(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1D)),
                ) {
                    topics.forEachIndexed { i, label ->
                        val on = topic == i
                        Row(
                            Modifier.fillMaxWidth().clickable { topic = i }.padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(hints[i], color = Muted, fontSize = 12.sp)
                            }
                            Box(
                                Modifier.size(20.dp).clip(CircleShape)
                                    .background(if (on) KiroPurple else Color.Transparent)
                                    .border(1.5.dp, if (on) KiroPurple else Color(0xFF55555B), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                        if (i < topics.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Color(0xFF2A2A2D)))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Your message", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1D))
                        .padding(14.dp),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = message,
                        onValueChange = { if (it.length <= 1000) message = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 22.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(KiroPurple),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { inner ->
                            if (message.isEmpty()) Text("Tell us what's wrong or what you'd like to see", color = Muted, fontSize = 15.sp)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Sending as ${profile.username.ifBlank { signedIn.name }}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${message.length} / 1000", color = Muted, fontSize = 12.sp)
                }
            }
        }
        PrimaryButton(
            label = if (signedIn == null) "Log in" else "Send",
            enabled = signedIn == null || (message.isNotBlank() && !sending),
            busy = sending,
            onClick = {
                if (signedIn == null) {
                    AuthRepository.requestLogin()
                } else {
                    sending = true
                    FirebaseFirestore.getInstance().collection("feedback").add(
                        hashMapOf(
                            "uid" to signedIn.uid,
                            "username" to profile.username.ifBlank { signedIn.name },
                            "topic" to topics[topic],
                            "message" to message.trim(),
                            "version" to BuildConfig.VERSION_NAME,
                            "createdAt" to System.currentTimeMillis(),
                        ),
                    ).addOnCompleteListener { t ->
                        if (!sending) return@addOnCompleteListener
                        sending = false
                        if (t.isSuccessful) sent = true
                        else Toast.makeText(context, "Couldn't send. Check your connection and try again.", Toast.LENGTH_LONG).show()
                    }
                    // Offline, the message is kept and sent later; don't leave the person waiting.
                    scope.launch {
                        kotlinx.coroutines.delay(8_000L)
                        if (sending) { sending = false; sent = true }
                    }
                }
            },
        )
    }
}
