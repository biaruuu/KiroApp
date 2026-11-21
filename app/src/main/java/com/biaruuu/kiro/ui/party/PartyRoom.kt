package com.biaruuu.kiro.ui.party

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.biaruuu.kiro.data.PartyChat
import com.biaruuu.kiro.data.PartyVoice
import com.biaruuu.kiro.data.WatchParty
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.i18n.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PrMuted = Color(0xFF9A9AA2)
private val PrCard = Color(0xFF18181B)
private val PrHair = Color(0xFF26262B)
private val PrChip = Color(0xFF222226)
private val PrGreen = Color(0xFF3DDC84)

/** Keeps chat and voice in step with the current party. Place once, high in the app. */
@Composable
fun PartyRuntime() {
    val party by WatchParty.session.collectAsState()
    val members by WatchParty.members.collectAsState()
    LaunchedEffect(party?.code) {
        PartyChat.listen(party?.code)
        if (party == null) PartyVoice.leave()
    }
    LaunchedEffect(members) { PartyVoice.reconcile(members) }
}

@Composable
private fun SoftButton(label: String, filled: Boolean = false, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color.Black else if (danger) Color(0xFFFF6B6B) else Color.White,
        fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(if (filled) Color.White else PrChip)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Join, mute and leave the voice call. Asks for the microphone the first time. */
@Composable
fun VoiceControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val joined by PartyVoice.joined.collectAsState()
    val muted by PartyVoice.muted.collectAsState()
    val error by PartyVoice.error.collectAsState()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) PartyVoice.join(context)
        else com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Voice chat needs the microphone permission.", Toast.LENGTH_LONG).show()
    }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!joined) {
                SoftButton("Join voice", filled = true) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        PartyVoice.join(context)
                    } else {
                        ask.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                Text("Talk with everyone in the room", color = PrMuted, fontSize = 12.sp)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (muted) Color(0xFFFF6B6B) else PrGreen))
                SoftButton(if (muted) "Unmute" else "Mute") { PartyVoice.setMuted(!muted) }
                SoftButton("Leave voice", danger = true) { PartyVoice.leave() }
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

/** The live message list with a box to write in. */
@Composable
fun PartyChatPane(modifier: Modifier = Modifier) {
    val msgs by PartyChat.messages.collectAsState()
    val me = WatchParty.uid()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex) }
    fun send() {
        if (text.isBlank()) return
        PartyChat.send(text)
        text = ""
    }
    Column(modifier.imePadding()) {
        if (msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No messages yet. Say hi!", color = PrMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(msgs, key = { it.id }) { m ->
                    Row {
                        DecoratedAvatar(m.avatar, m.name, 30.dp, m.decoration)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (m.uid == me) "You" else m.name, color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(timeFormat.format(Date(m.at)), color = PrMuted, fontSize = 11.sp)
                            }
                            Text(m.text, color = Color(0xFFD0D0D4), fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(PrCard).border(1.dp, PrHair, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) Text("Message", color = PrMuted, fontSize = 14.sp)
                BasicTextField(
                    value = text, onValueChange = { if (it.length <= 500) text = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            val ready = text.isNotBlank()
            Text(
                "Send", color = if (ready) Color.Black else Color(0xFF6A6A70), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(if (ready) Color.White else PrChip)
                    .clickable(enabled = ready) { send() }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** A small line at the bottom of the player when a friend writes, so chat is seen without opening it. */
@Composable
fun PartyToast(modifier: Modifier = Modifier) {
    val msgs by PartyChat.messages.collectAsState()
    val me = WatchParty.uid()
    var shown by remember { mutableStateOf<PartyChat.Msg?>(null) }
    var lastSeen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msgs.lastOrNull()?.id) {
        val last = msgs.lastOrNull() ?: return@LaunchedEffect
        if (lastSeen == null) { lastSeen = last.id; return@LaunchedEffect } // old history is not announced
        if (last.id == lastSeen) return@LaunchedEffect
        lastSeen = last.id
        if (last.uid == me) return@LaunchedEffect
        shown = last
        kotlinx.coroutines.delay(4500)
        if (shown?.id == last.id) shown = null
    }
    val m = shown ?: return
    Row(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(m.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(m.text, color = Color(0xFFE0E0E4), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** The room page: who is here, what the host is watching, voice and chat. */
@Composable
fun PartyRoom(onWatch: (animeId: Int, episode: Int, code: String) -> Unit) {
    val context = LocalContext.current
    val party by WatchParty.session.collectAsState()
    val members by WatchParty.members.collectAsState()
    val remote by WatchParty.remote.collectAsState()
    val p = party
    if (p == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("You're not in a room. Start or join one from Me > Watch party.", color = PrMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // Code and actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (p.host) "You are hosting" else "In a room", color = PrMuted, fontSize = 12.sp)
                Text(p.code, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
            }
            SoftButton("Share") {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "Join my Kiro watch party! Open Me > Watch party and enter the code ${p.code}")
                }
                context.startActivity(android.content.Intent.createChooser(send, null))
            }
            Spacer(Modifier.width(8.dp))
            SoftButton(if (p.host) "End" else "Leave", danger = true) { WatchParty.leave() }
        }
        // Members
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(members, key = { it.uid }) { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                    Box {
                        DecoratedAvatar(m.avatar, m.name, 48.dp, m.decoration)
                        if (m.voice) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape)
                                    .background(if (m.muted) Color(0xFFFF6B6B) else PrGreen).border(2.dp, Color(0xFF121212), CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(m.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    if (m.host) Text("Host", color = PrMuted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // What the host is watching
        val r = remote
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(PrCard)
                .border(1.dp, PrHair, RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            if (r != null && r.animeId > 0) {
                Text(if (p.host) "You are watching" else "${r.hostName} is watching", color = PrMuted, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text("${r.title.ifBlank { "An anime" }} · Episode ${r.episode}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                if (!p.host) {
                    Spacer(Modifier.height(10.dp))
                    SoftButton("Watch together", filled = true) { onWatch(r.animeId, r.episode, p.code) }
                }
            } else {
                Text(
                    if (p.host) "Browse Kiro and start any episode. Everyone in the room can join you from here, and the chat and voice stay with you."
                    else "Waiting for the host to start an episode. Feel free to browse Kiro meanwhile. The room stays open.",
                    color = PrMuted, fontSize = 13.sp, lineHeight = 19.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        VoiceControls(Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(PrHair))
        PartyChatPane(Modifier.weight(1f).fillMaxWidth())
    }
}

/** A small pill at the top while you are in a room, wherever you browse. Tap to open the room. */
@Composable
fun PartyPill(modifier: Modifier = Modifier, onOpen: () -> Unit, onWatch: (Int, Int, String) -> Unit) {
    val party by WatchParty.session.collectAsState()
    val members by WatchParty.members.collectAsState()
    val remote by WatchParty.remote.collectAsState()
    val joined by PartyVoice.joined.collectAsState()
    val muted by PartyVoice.muted.collectAsState()
    val p = party ?: return
    val r = remote
    Row(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xE618181B)).border(1.dp, PrHair, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (joined && !muted) PrGreen else if (joined) Color(0xFFFF6B6B) else Color(0xFF6A6A70)))
        Spacer(Modifier.width(8.dp))
        Text("${p.code} · ${members.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (!p.host && r != null && r.animeId > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "Join", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .clickable { onWatch(r.animeId, r.episode, p.code) }.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        } else {
            Spacer(Modifier.width(6.dp))
        }
    }
}
