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
fun PartyChatPane(modifier: Modifier = Modifier, onInvite: (() -> Unit)? = null) {
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
    var emojiOpen by remember { mutableStateOf(false) }
    Column(modifier.imePadding()) {
        if (msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val alone = WatchParty.members.collectAsState().value.size <= 1
                    Text(if (alone) "Invite your friends" else "No messages yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (alone) "Invite others so you can watch and chat together." else "Say hi to everyone.",
                        color = PrMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                    if (alone && onInvite != null) {
                        Spacer(Modifier.height(14.dp))
                        SoftButton("Invite friends", filled = true, onClick = onInvite)
                    }
                }
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
        if (emojiOpen) {
            val set = listOf("😂", "😍", "🔥", "👍", "😭", "🥹", "😮", "👏", "💀", "❤️", "🎉", "🙏")
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(set) { e ->
                    Text(e, fontSize = 24.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (text.length < 499) text += e }.padding(8.dp))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(com.biaruuu.kiro.R.drawable.fluent_emoji_regular),
                contentDescription = "Emoji", tint = if (emojiOpen) Color.White else PrMuted,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { emojiOpen = !emojiOpen }.padding(7.dp),
            )
            Spacer(Modifier.width(4.dp))
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
