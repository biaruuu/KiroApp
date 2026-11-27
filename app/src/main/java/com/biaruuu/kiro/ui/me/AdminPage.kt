package com.biaruuu.kiro.ui.me

import com.biaruuu.kiro.ui.theme.KiroText
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.Admin
import com.biaruuu.kiro.data.Announcements
import com.biaruuu.kiro.data.ProfileRepository
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.ui.auth.PrimaryButton
import com.biaruuu.kiro.ui.auth.UnderlineField
import com.biaruuu.kiro.ui.theme.KiroAccent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private val Muted = Color(0xFF9A9A9F)
private val Card = Color(0xFF18181B)
private val AdmHair = Color(0xFF26262B)
private val AdmChip = Color(0xFF222226)
private val AdmSoft = Color(0xFFD0D0D4)
private val AdmRed = Color(0xFFFF6B6B)
private val Green = Color(0xFF3DDC84)
private const val ONLINE_WINDOW_MS = 90_000L

private class PresenceUser(val uid: String, val name: String, val online: Boolean, val lastSeen: Long, val version: String, val watching: String?)
private class Letter(val id: String, val name: String, val message: String, val version: String, val createdAt: Long, val topic: String)
private class Report(val id: String, val title: String, val episode: Int, val provider: String, val tried: String, val quality: String, val problem: String, val device: String, val version: String, val createdAt: Long)

// ---- shared pieces ----

/** Every tab sits in a centred column so it reads well on tablets and landscape too. */
@Composable
private fun AdmTabFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) { content() }
    }
}

@Composable
private fun AdmSectionTitle(title: String, hint: String? = null, count: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = KiroText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(count.toString(), color = Muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(AdmChip).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        if (hint != null) Text(hint, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun AdmLabel(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun AdmCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card)
            .border(1.dp, AdmHair, RoundedCornerShape(14.dp)).padding(14.dp),
    ) { content() }
}

@Composable
private fun AdmEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun AdmTag(text: String, strong: Boolean = false) {
    Text(
        text, color = if (strong) Color.Black else AdmSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (strong) Color.White else AdmChip).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A pill the admin can tap to pick one option. */
@Composable
private fun AdmPill(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color.White else AdmChip).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun AdmTextAction(label: String, color: Color = Color.White, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Delete with a confirmation, so one stray tap never removes something. */
@Composable
private fun AdmDeleteAction(label: String = "Delete", what: String, onConfirm: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    AdmTextAction(label, AdmRed) { ask = true }
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            containerColor = Card,
            title = { Text("$label?", color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(what, color = AdmSoft, fontSize = 14.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { ask = false; onConfirm() }) { Text(label, color = AdmRed) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ask = false }) { Text("Cancel", color = Muted) }
            },
        )
    }
}

@Composable
private fun AdmSecondaryButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AdmChip).clickable(onClick = onClick).padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun AdmStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, AdmHair, RoundedCornerShape(14.dp)).padding(vertical = 14.dp, horizontal = 14.dp),
    ) {
        Text(value, color = KiroText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

private val AdmListPad = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

/** The developer's tools: who is online and watching, letters from users, and announcements. */
@Composable
fun AdminPage() {
    val state by AuthRepository.state.collectAsState()
    if (!Admin.isAdmin(state)) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("This page is only for the Kiro developer.", color = Muted, fontSize = 14.sp)
        }
        return
    }
    // Keeps the public developer card (name, photo, bio) on every notification in step with this profile.
    val profile by ProfileRepository.profile.collectAsState()
    val context = LocalContext.current
    val rulesError by Announcements.error.collectAsState()
    LaunchedEffect(profile.username, profile.avatar, profile.bio) {
        if (profile.username.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("meta").document("admin").set(
                hashMapOf(
                    "username" to profile.username, "bio" to profile.bio, "avatar" to profile.avatar, "email" to Admin.EMAIL,
                    "decoration" to profile.decoration, "nameFont" to profile.nameFont,
                ),
            ).addOnFailureListener {
                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Couldn't save your developer card: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Small numbers on the Letters and Reports tabs so new things stand out.
    var letterCount by remember { mutableIntStateOf(0) }
    var reportCount by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val a = db.collection("feedback").addSnapshotListener { snap, _ -> if (snap != null) letterCount = snap.size() }
        val b = db.collection("reports").addSnapshotListener { snap, _ -> if (snap != null) reportCount = snap.size() }
        onDispose { a.remove(); b.remove() }
    }

    // A home with one card per tool; each opens its own page.
    var section by remember { mutableStateOf<Int?>(null) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = null }
    val tools = listOf(
        Triple("Online", "Who is using Kiro and what they are watching", 0),
        Triple("Letters", "Messages users sent from the app", letterCount),
        Triple("Reports", "Broken episode reports from the player", reportCount),
        Triple("Announce", "Send news to every user's Notifications", 0),
        Triple("Designs", "Publish avatar decorations", 0),
        Triple("Filipino dub", "Add Filipino dub episodes", 0),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        if (rulesError != null) {
            Text(
                "Firestore refused access: ${rulesError}. Publish the latest firestore.rules in the Firebase Console, then reopen Kiro.",
                color = AdmRed, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2A1618)).padding(12.dp),
            )
        }
        val current = section
        if (current == null) {
            AdmTabFrame {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cols = if (maxWidth >= 520.dp) 2 else 1
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = AdmListPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            AdmSectionTitle("Admin", "Pick a tool.")
                            Spacer(Modifier.height(4.dp))
                        }
                        items(tools.indices.toList().chunked(cols)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { i ->
                                    val (name, hint, n) = tools[i]
                                    AdmCard(Modifier.weight(1f).clickable { section = i }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(name, color = KiroText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            if (n > 0) AdmTag(if (n > 99) "99+" else n.toString(), strong = true)
                                            Spacer(Modifier.width(8.dp))
                                            Text("›", color = Muted, fontSize = 18.sp)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(hint, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                                    }
                                }
                                if (row.size < cols) Spacer(Modifier.weight((cols - row.size).toFloat()))
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹", color = KiroText, fontSize = 26.sp,
                    modifier = Modifier.clip(CircleShape).clickable { section = null }.padding(horizontal = 14.dp, vertical = 2.dp),
                )
                Text(tools[current].first, color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            AdmTabFrame {
                when (current) {
                    0 -> OnlineTab()
                    1 -> LettersTab()
                    2 -> ReportsTab()
                    3 -> AnnounceTab()
                    4 -> DesignsTab()
                    else -> TagalogTab()
                }
            }
        }
    }
}

@Composable
private fun OnlineTab() {
    var users by remember { mutableStateOf<List<PresenceUser>>(emptyList()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(Unit) {
        val reg = FirebaseFirestore.getInstance().collection("presence").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            users = snap.documents.map { d ->
                val w = d.get("watching") as? Map<*, *>
                PresenceUser(
                    d.id, d.getString("username").orEmpty().ifBlank { "User" }, d.getBoolean("online") == true,
                    d.getLong("lastSeen") ?: 0L, d.getString("version").orEmpty(),
                    w?.let { "${it["title"] ?: "Unknown"} · Episode ${it["episode"] ?: "?"}" },
                )
            }
        }
        onDispose { reg.remove() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            now = System.currentTimeMillis()
        }
    }
    val online = users.filter { it.online && now - it.lastSeen < ONLINE_WINDOW_MS }
    val watching = online.filter { it.watching != null }
    val browsing = online.filter { it.watching == null }
    val recent = users.filter { it !in online }.sortedByDescending { it.lastSeen }.take(30)

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = AdmListPad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdmStat(online.size.toString(), "Online", Modifier.weight(1f))
                AdmStat(watching.size.toString(), "Watching", Modifier.weight(1f))
                AdmStat(users.size.toString(), "Known users", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text("Signed-in users with Kiro open. Updates live.", color = Muted, fontSize = 12.sp)
        }
        if (watching.isNotEmpty()) {
            item { AdmLabel("Watching now") }
            items(watching, key = { "w:" + it.uid }) { u -> UserRow(u, true, now) }
        }
        if (browsing.isNotEmpty()) {
            item { AdmLabel("Browsing") }
            items(browsing, key = { "o:" + it.uid }) { u -> UserRow(u, true, now) }
        }
        if (online.isEmpty()) {
            item { Text("Nobody is online right now.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
        }
        if (recent.isNotEmpty()) {
            item { AdmLabel("Recently active") }
            items(recent, key = { "r:" + it.uid }) { u -> UserRow(u, false, now) }
        }
    }
}

@Composable
private fun UserRow(u: PresenceUser, online: Boolean, now: Long) {
    AdmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (online) Green else Color(0xFF55555A)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(u.name, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    if (online) (u.watching?.let { "Watching: $it" } ?: "Browsing") else "Last seen ${ago(u.lastSeen)}",
                    color = if (online && u.watching != null) AdmSoft else Muted, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            if (u.version.isNotEmpty()) AdmTag("v${u.version}")
        }
    }
}

@Composable
private fun LettersTab() {
    var letters by remember { mutableStateOf<List<Letter>>(emptyList()) }
    var topic by remember { mutableStateOf("All") }
    DisposableEffect(Unit) {
        val reg = FirebaseFirestore.getInstance().collection("feedback")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                letters = snap.documents.map { d ->
                    Letter(
                        d.id, d.getString("username").orEmpty().ifBlank { d.getString("uid").orEmpty().take(8) },
                        d.getString("message").orEmpty(), d.getString("version").orEmpty(), d.getLong("createdAt") ?: 0L, d.getString("topic").orEmpty(),
                    )
                }
            }
        onDispose { reg.remove() }
    }
    if (letters.isEmpty()) { AdmEmpty("No letters yet."); return }
    val topics = listOf("All") + letters.map { it.topic }.filter { it.isNotEmpty() }.distinct()
    val shown = if (topic == "All") letters else letters.filter { it.topic == topic }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = AdmListPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AdmSectionTitle("Letters", "Messages users sent from the app.", letters.size)
            if (topics.size > 2) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(topics) { t -> AdmPill(t, topic == t) { topic = t } }
                }
            }
        }
        items(shown, key = { it.id }) { l ->
            AdmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (l.topic.isNotEmpty()) { Spacer(Modifier.width(8.dp)); AdmTag(l.topic, strong = true) }
                    Spacer(Modifier.weight(1f))
                    Text(ago(l.createdAt), color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(l.message, color = AdmSoft, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (l.version.isNotEmpty()) Text("v${l.version}", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    AdmDeleteAction(what = "This letter will be removed for good.") {
                        FirebaseFirestore.getInstance().collection("feedback").document(l.id).delete()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsTab() {
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    DisposableEffect(Unit) {
        val reg = FirebaseFirestore.getInstance().collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                reports = snap.documents.map { d ->
                    Report(
                        d.id, d.getString("title").orEmpty(), (d.getLong("episode") ?: 0L).toInt(),
                        d.getString("provider").orEmpty(), d.getString("tried").orEmpty(), d.getString("quality").orEmpty(),
                        d.getString("problem").orEmpty(), d.getString("device").orEmpty(), d.getString("version").orEmpty(),
                        d.getLong("createdAt") ?: 0L,
                    )
                }
            }
        onDispose { reg.remove() }
    }
    if (reports.isEmpty()) { AdmEmpty("No broken episode reports."); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = AdmListPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AdmSectionTitle("Broken episodes", "Reports from the player. Mark one done once it is fixed.", reports.size) }
        items(reports, key = { it.id }) { r ->
            AdmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(ago(r.createdAt), color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdmTag("Episode ${r.episode}", strong = true)
                    if (r.provider.isNotEmpty()) AdmTag(r.provider)
                    if (r.quality.isNotEmpty()) AdmTag(r.quality)
                }
                if (r.problem.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(r.problem, color = AdmSoft, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (r.tried.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Tried: ${r.tried}", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf("v${r.version}", r.device).filter { it.length > 1 }.joinToString(" · "), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    AdmDeleteAction(label = "Mark done", what = "This report will be removed from the list.") {
                        FirebaseFirestore.getInstance().collection("reports").document(r.id).delete()
                    }
                }
            }
        }
    }
}

/** The live prompt: sent over the WebSocket, it fades in on every open phone at once, with your verified name. */
@Composable
private fun LiveAnnounceCard() {
    val context = LocalContext.current
    val profile by ProfileRepository.profile.collectAsState()
    val live by com.biaruuu.kiro.data.GlobalSocket.current.collectAsState()
    val online by com.biaruuu.kiro.data.GlobalSocket.online.collectAsState()
    val connected by com.biaruuu.kiro.data.GlobalSocket.connected.collectAsState()
    val prefs = remember { context.getSharedPreferences("kiro_global", android.content.Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("admin_token", "").orEmpty()) }
    var text by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(12) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    AdmCard {
        AdmSectionTitle(
            "Live prompt",
            if (connected) "Shows on every open phone right now ($online online), with your verified name and like / dislike buttons."
            else "Connecting to the server...",
        )
        Spacer(Modifier.height(10.dp))
        UnderlineField(
            value = token, onChange = { token = it; prefs.edit().putString("admin_token", it.trim()).apply() }, hint = "Admin token (ADMIN_TOKEN on the server)",
            keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, onImeAction = {}, autoFocus = false,
            transformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(10.dp))
        UnderlineField(
            value = text, onChange = { if (it.length <= 240) text = it }, hint = "Message",
            keyboardType = KeyboardType.Text, imeAction = ImeAction.Default, onImeAction = {}, autoFocus = false, singleLine = false,
        )
        Text("${text.length}/240", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Stays", color = Muted, fontSize = 12.sp)
            listOf(8, 12, 20, 30).forEach { sec ->
                Box(Modifier.clickable { seconds = sec }) {
                    AdmTag("${sec}s", strong = sec == seconds)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            label = "Send live prompt",
            enabled = connected && token.isNotBlank() && text.isNotBlank() && profile.username.isNotBlank(),
            busy = busy,
            onClick = {
                busy = true
                status = null
                com.biaruuu.kiro.data.GlobalSocket.announce(token.trim(), text.trim(), profile.username, seconds * 1000L) { err ->
                    busy = false
                    status = err ?: "Sent."
                    if (err == null) text = ""
                }
            },
        )
        status?.let { Text(it, color = if (it == "Sent.") Muted else AdmRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        live?.let { l ->
            Spacer(Modifier.height(8.dp))
            Text("Live now: ${l.likes} likes, ${l.dislikes} dislikes", color = AdmSoft, fontSize = 12.sp)
            AdmTextAction("Hide it now", Muted) { com.biaruuu.kiro.data.GlobalSocket.hide(token.trim()) }
        }
    }
}

@Composable
private fun AnnounceTab() {
    val context = LocalContext.current
    val announcements by Announcements.list.collectAsState()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = AdmListPad,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { LiveAnnounceCard() }
        item {
            AdmCard {
                AdmSectionTitle(if (editing == null) "New announcement" else "Edit announcement", "Appears in every user's Notifications, right away for anyone with Kiro open.")
                Spacer(Modifier.height(10.dp))
                UnderlineField(
                    value = title, onChange = { if (it.length <= 80) title = it }, hint = "Title",
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, onImeAction = {}, autoFocus = false,
                )
                Spacer(Modifier.height(10.dp))
                UnderlineField(
                    value = body, onChange = { if (it.length <= 1000) body = it }, hint = "Message",
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Default, onImeAction = {}, autoFocus = false, singleLine = false,
                )
                Text("${body.length}/1000", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    label = if (editing == null) "Send to all users" else "Save changes",
                    enabled = title.isNotBlank() && body.isNotBlank(),
                    busy = false,
                    onClick = {
                        val col = FirebaseFirestore.getInstance().collection("announcements")
                        val id = editing
                        val fail = { e: Exception -> com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Couldn't save: ${e.message}", Toast.LENGTH_LONG).show() }
                        if (id == null) {
                            col.add(hashMapOf("title" to title.trim(), "body" to body.trim(), "createdAt" to System.currentTimeMillis()))
                                .addOnSuccessListener { com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Announcement sent.", Toast.LENGTH_SHORT).show() }
                                .addOnFailureListener(fail)
                        } else {
                            col.document(id).update(mapOf("title" to title.trim(), "body" to body.trim()))
                                .addOnSuccessListener { com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Announcement updated.", Toast.LENGTH_SHORT).show() }
                                .addOnFailureListener(fail)
                        }
                        title = ""; body = ""; editing = null
                    },
                )
                if (editing != null) {
                    Spacer(Modifier.height(4.dp))
                    AdmTextAction("Cancel editing", Muted) { title = ""; body = ""; editing = null }
                }
            }
            Spacer(Modifier.height(8.dp))
            AdmSectionTitle("Sent", count = announcements.size)
        }
        if (announcements.isEmpty()) {
            item { Text("Nothing sent yet.", color = Muted, fontSize = 13.sp) }
        }
        items(announcements, key = { it.id }) { a ->
            AdmCard {
                Text(a.title, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(a.body, color = AdmSoft, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ago(a.createdAt), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    AdmTextAction("Edit") { title = a.title; body = a.body; editing = a.id }
                    AdmDeleteAction(what = "Users will no longer see \"${a.title}\".") {
                        FirebaseFirestore.getInstance().collection("announcements").document(a.id).delete()
                    }
                }
            }
        }
    }
}

/** Publish new avatar decorations and library looks (PNG, animated PNG, GIF or WebP, up to 5 MB). Users get them at once. */
@Composable
private fun DesignsTab() {
    val context = LocalContext.current
    val published by com.biaruuu.kiro.data.Designs.remote.collectAsState()
    var type by remember { mutableStateOf(com.biaruuu.kiro.data.Designs.DECORATION) }
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var publishing by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
            when {
                bytes == null -> com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Couldn't read that file.", Toast.LENGTH_SHORT).show()
                bytes.size > com.biaruuu.kiro.data.Designs.MAX_BYTES ->
                    com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Too large (%.1f MB). The limit is 5 MB.".format(bytes.size / 1048576.0), Toast.LENGTH_LONG).show()
                else -> picked = bytes
            }
        }
    }
    val types = listOf(
        com.biaruuu.kiro.data.Designs.DECORATION to "Avatar Decoration",
    )

    LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = AdmListPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AdmCard {
                AdmSectionTitle("Publish a design", "Up to 5 MB. Shows up for every user right away. Animated PNG, GIF and WebP all play.")
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { (id, label) -> AdmPill(label, type == id) { type = id } }
                }
                Spacer(Modifier.height(10.dp))
                UnderlineField(
                    value = name, onChange = { if (it.length <= 30) name = it }, hint = "Name",
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done, onImeAction = {}, autoFocus = false,
                )
                Spacer(Modifier.height(14.dp))
                val bytes = picked
                if (bytes != null) {
                    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF000000)), contentAlignment = Alignment.Center) {
                        com.biaruuu.kiro.ui.components.AnimatedAsset(
                            com.biaruuu.kiro.data.Design("preview", type, name, bytes = bytes),
                            if (type == com.biaruuu.kiro.data.Designs.DECORATION) Modifier.size(120.dp) else Modifier.fillMaxSize(),
                            crop = type == com.biaruuu.kiro.data.Designs.LIBRARY_EFFECT,
                        )
                    }
                    Text("%.2f MB".format(bytes.size / 1048576.0), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                }
                AdmSecondaryButton(if (bytes == null) "Choose image" else "Choose a different image") { pick.launch("image/*") }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    label = "Publish",
                    enabled = picked != null && name.isNotBlank() && !publishing,
                    busy = publishing,
                    onClick = {
                        val data = picked ?: return@PrimaryButton
                        publishing = true
                        com.biaruuu.kiro.data.Designs.publish(type, name.trim(), data) { error ->
                            publishing = false
                            if (error == null) {
                                picked = null; name = ""
                                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Published.", Toast.LENGTH_SHORT).show()
                            } else {
                                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Couldn't publish: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            AdmSectionTitle("Published", count = published.size)
        }
        if (published.isEmpty()) {
            item { Text("Nothing published yet.", color = Muted, fontSize = 13.sp) }
        }
        items(published, key = { it.id }) { d ->
            AdmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.biaruuu.kiro.ui.components.AnimatedAsset(d, Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.name, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(types.firstOrNull { it.first == d.type }?.second ?: d.type, color = Muted, fontSize = 12.sp)
                    }
                    AdmDeleteAction(what = "\"${d.name}\" will disappear for every user.") {
                        com.biaruuu.kiro.data.Designs.delete(d.id)
                    }
                }
            }
        }
    }
}

/** Add Filipino dub episodes: find the anime on AniList, give the episode number and a video address. */
@Composable
private fun TagalogTab() {
    val context = LocalContext.current
    val entries by com.biaruuu.kiro.data.TagalogDub.entries.collectAsState()
    DisposableEffect(Unit) {
        val stop = com.biaruuu.kiro.data.TagalogDub.listen()
        onDispose { stop() }
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.biaruuu.kiro.data.Anime>>(emptyList()) }
    var chosen by remember { mutableStateOf<com.biaruuu.kiro.data.Anime?>(null) }
    var episode by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var quality by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2 || chosen != null) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350L)
        com.biaruuu.kiro.data.AniListApi.search(q, null, com.biaruuu.kiro.data.SortOrder.POPULARITY, 1, object : com.biaruuu.kiro.data.AniListApi.Callback<com.biaruuu.kiro.data.SearchResult> {
            override fun onSuccess(result: com.biaruuu.kiro.data.SearchResult) { results = result.items.take(6) }
            override fun onError(error: Exception) {}
        })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = AdmListPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AdmCard {
                AdmSectionTitle("Add a Filipino dub episode", "Played as a \"Filipino Dub\" source for that anime and episode. Use a direct video link (.m3u8 or .mp4) that opens without signing in.")
                Spacer(Modifier.height(12.dp))
                AdmLabel("1 · Anime")
                val c = chosen
                if (c == null) {
                    UnderlineField(
                        value = query, onChange = { query = it }, hint = "Search AniList by title",
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Search, onImeAction = {}, autoFocus = false,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AdmChip).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        coil.compose.AsyncImage(
                            model = c.posterUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.displayTitle, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                            Text("AniList ${c.id}" + if (c.episodes > 0) " · ${c.episodes} episodes" else "", color = Muted, fontSize = 12.sp)
                        }
                        AdmTextAction("Change") { chosen = null; query = "" }
                    }
                }
                if (c != null) {
                    AdmLabel("2 · Episode and video")
                    UnderlineField(
                        value = episode, onChange = { if (it.length <= 4 && it.all(Char::isDigit)) episode = it }, hint = "Episode number",
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Next, onImeAction = {}, autoFocus = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    UnderlineField(
                        value = url, onChange = { url = it.trim() }, hint = "Video link (.m3u8 or .mp4)",
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, onImeAction = {}, autoFocus = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    UnderlineField(
                        value = referer, onChange = { referer = it.trim() }, hint = "Referer (only if the host needs one)",
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, onImeAction = {}, autoFocus = false,
                    )
                    AdmLabel("Quality")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(0 to "Auto", 480 to "480p", 720 to "720p", 1080 to "1080p").forEach { (h, label) ->
                            AdmPill(label, quality == h) { quality = h }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val ep = episode.toIntOrNull() ?: 0
                    PrimaryButton(
                        label = "Publish",
                        enabled = ep > 0 && url.startsWith("http") && !busy,
                        busy = busy,
                        onClick = {
                            busy = true
                            com.biaruuu.kiro.data.TagalogDub.publish(
                                com.biaruuu.kiro.data.TagalogDub.Entry("", c.id, c.displayTitle, c.posterUrl.orEmpty(), ep, url, quality, referer, 0L),
                            ) { error ->
                                busy = false
                                if (error == null) {
                                    episode = (ep + 1).toString(); url = ""
                                    com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Published episode $ep.", Toast.LENGTH_SHORT).show()
                                } else {
                                    com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Couldn't publish: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                }
            }
        }
        if (chosen == null) {
            items(results, key = { "r:" + it.id }) { a ->
                AdmCard(Modifier.clickable { chosen = a; results = emptyList() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        coil.compose.AsyncImage(
                            model = a.posterUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(width = 36.dp, height = 50.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.displayTitle, color = KiroText, fontSize = 14.sp, maxLines = 2)
                            Text(listOf(a.format, if (a.seasonYear > 0) a.seasonYear.toString() else "").filter { !it.isNullOrEmpty() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            AdmSectionTitle("Published", count = entries.size)
        }
        if (entries.isEmpty()) {
            item { Text("Nothing published yet.", color = Muted, fontSize = 13.sp) }
        }
        items(entries, key = { it.id }) { e ->
            AdmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.title.ifBlank { "AniList ${e.anilistId}" }, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("Episode ${e.episode} · ${if (e.quality > 0) "${e.quality}p" else "Auto"} · ${ago(e.createdAt)}", color = Muted, fontSize = 12.sp)
                    }
                    AdmDeleteAction(what = "Episode ${e.episode} of ${e.title.ifBlank { "this anime" }} will no longer appear as a Filipino Dub source.") {
                        com.biaruuu.kiro.data.TagalogDub.delete(e.id)
                    }
                }
            }
        }
    }
}
