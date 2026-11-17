package com.biaruuu.kiro.ui.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private val Muted = Color(0xFF9A9A9F)
private val Card = Color(0xFF1A1A1D)
private val Green = Color(0xFF3DDC84)
private const val ONLINE_WINDOW_MS = 90_000L

private class PresenceUser(val uid: String, val name: String, val online: Boolean, val lastSeen: Long, val version: String, val watching: String?)
private class Letter(val id: String, val name: String, val message: String, val version: String, val createdAt: Long, val topic: String)

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
                Toast.makeText(context, "Couldn't save your developer card: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (rulesError != null) {
            Text(
                "Firestore refused access: ${rulesError}. Publish the latest firestore.rules in the Firebase Console, then reopen Kiro.",
                color = Color(0xFFFF6B6B), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2A1618)).padding(12.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Online", "Letters", "Announce", "Designs").forEachIndexed { i, label ->
                Column(
                    modifier = Modifier.weight(1f).clickable { tab = i }.padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, color = if (tab == i) Color.White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (tab == i) Color.White else Color.Transparent))
                }
            }
        }
        when (tab) {
            0 -> OnlineTab()
            1 -> LettersTab()
            2 -> AnnounceTab()
            else -> DesignsTab()
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
    val online = users.filter { it.online && now - it.lastSeen < ONLINE_WINDOW_MS }.sortedByDescending { it.watching != null }
    val recent = users.filter { it !in online }.sortedByDescending { it.lastSeen }.take(30)

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "${online.size} online · ${online.count { it.watching != null }} watching",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text("Signed-in users with Kiro open. Updates live.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
        }
        items(online, key = { "o:" + it.uid }) { u -> UserRow(u, true, now) }
        if (recent.isNotEmpty()) {
            item { Spacer(Modifier.height(10.dp)); Text("Recently active", color = Muted, fontSize = 13.sp) }
            items(recent, key = { "r:" + it.uid }) { u -> UserRow(u, false, now) }
        }
    }
}

@Composable
private fun UserRow(u: PresenceUser, online: Boolean, now: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) Green else Color(0xFF55555A)))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(u.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                if (online) (u.watching?.let { "Watching: $it" } ?: "Browsing") else "Last seen ${ago(u.lastSeen)}",
                color = if (online && u.watching != null) Color.White else Muted, fontSize = 12.sp,
            )
        }
        if (u.version.isNotEmpty()) Text("v${u.version}", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun LettersTab() {
    var letters by remember { mutableStateOf<List<Letter>>(emptyList()) }
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
    if (letters.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No letters yet.", color = Muted, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(letters, key = { it.id }) { l ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (l.topic.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            l.topic, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(listOf(ago(l.createdAt), if (l.version.isNotEmpty()) "v${l.version}" else "").filter { it.isNotEmpty() }.joinToString(" · "), color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(l.message, color = Color(0xFFD0D0D4), fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Delete", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { FirebaseFirestore.getInstance().collection("feedback").document(l.id).delete() },
                )
            }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(if (editing == null) "New announcement" else "Edit announcement", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Appears in every user's Notifications, right away for anyone with Kiro open.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            UnderlineField(
                value = title, onChange = { if (it.length <= 80) title = it }, hint = "Title",
                keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, onImeAction = {},
            )
            Spacer(Modifier.height(10.dp))
            UnderlineField(
                value = body, onChange = { if (it.length <= 1000) body = it }, hint = "Message",
                keyboardType = KeyboardType.Text, imeAction = ImeAction.Default, onImeAction = {}, singleLine = false,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                label = if (editing == null) "Send to all users" else "Save changes",
                enabled = title.isNotBlank() && body.isNotBlank(),
                busy = false,
                onClick = {
                    val col = FirebaseFirestore.getInstance().collection("announcements")
                    val id = editing
                    val fail = { e: Exception -> Toast.makeText(context, "Couldn't save: ${e.message}", Toast.LENGTH_LONG).show() }
                    if (id == null) {
                        col.add(hashMapOf("title" to title.trim(), "body" to body.trim(), "createdAt" to System.currentTimeMillis()))
                            .addOnSuccessListener { Toast.makeText(context, "Announcement sent.", Toast.LENGTH_SHORT).show() }
                            .addOnFailureListener(fail)
                    } else {
                        col.document(id).update(mapOf("title" to title.trim(), "body" to body.trim()))
                            .addOnSuccessListener { Toast.makeText(context, "Announcement updated.", Toast.LENGTH_SHORT).show() }
                            .addOnFailureListener(fail)
                    }
                    title = ""; body = ""; editing = null
                },
            )
            if (editing != null) {
                Spacer(Modifier.height(8.dp))
                Text("Cancel editing", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { title = ""; body = ""; editing = null }.padding(4.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Sent announcements", color = Muted, fontSize = 13.sp)
        }
        items(announcements, key = { it.id }) { a ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(14.dp)) {
                Text(a.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(a.body, color = Color(0xFFB4B4BA), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(ago(a.createdAt), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { title = a.title; body = a.body; editing = a.id })
                    Spacer(Modifier.width(18.dp))
                    Text("Delete", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable {
                        FirebaseFirestore.getInstance().collection("announcements").document(a.id).delete()
                    })
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
                bytes == null -> Toast.makeText(context, "Couldn't read that file.", Toast.LENGTH_SHORT).show()
                bytes.size > com.biaruuu.kiro.data.Designs.MAX_BYTES ->
                    Toast.makeText(context, "Too large (%.1f MB). The limit is 5 MB.".format(bytes.size / 1048576.0), Toast.LENGTH_LONG).show()
                else -> picked = bytes
            }
        }
    }
    val types = listOf(
        com.biaruuu.kiro.data.Designs.DECORATION to "Avatar Decoration",
    )

    LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Publish a design", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Up to 5 MB. Shows up for every user right away. Animated PNG, GIF and WebP all play.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { (id, label) ->
                    val on = type == id
                    Text(
                        label, color = if (on) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color.White else Color(0xFF2A2A2D)).clickable { type = id }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            UnderlineField(
                value = name, onChange = { if (it.length <= 30) name = it }, hint = "Name",
                keyboardType = KeyboardType.Text, imeAction = ImeAction.Done, onImeAction = {},
            )
            Spacer(Modifier.height(14.dp))
            val bytes = picked
            if (bytes != null) {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)).background(Card), contentAlignment = Alignment.Center) {
                    com.biaruuu.kiro.ui.components.AnimatedAsset(
                        com.biaruuu.kiro.data.Design("preview", type, name, bytes = bytes),
                        if (type == com.biaruuu.kiro.data.Designs.DECORATION) Modifier.size(120.dp) else Modifier.fillMaxSize(),
                        crop = type == com.biaruuu.kiro.data.Designs.LIBRARY_EFFECT,
                    )
                }
                Text("%.2f MB".format(bytes.size / 1048576.0), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                if (bytes == null) "Choose image" else "Choose a different image",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2D)).clickable { pick.launch("image/*") }.padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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
                            Toast.makeText(context, "Published.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Couldn't publish: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
            Text("Published designs", color = Muted, fontSize = 13.sp)
        }
        items(published, key = { it.id }) { d ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.biaruuu.kiro.ui.components.AnimatedAsset(d, Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(d.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(types.firstOrNull { it.first == d.type }?.second ?: d.type, color = Muted, fontSize = 12.sp)
                }
                Text("Delete", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable {
                    com.biaruuu.kiro.data.Designs.delete(d.id)
                })
            }
        }
    }
}
