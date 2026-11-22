package com.biaruuu.kiro.ui.party

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

private val PrMuted = PtMuted
private val PrCard = PtSurface
private val PrHair = PtLine
private val PrChip = PtRaised
private val PrGreen = PtGreen

/** Keeps chat and voice in step with the current party. Place once, high in the app. */
@Composable
fun PartyRuntime() {
    val party by WatchParty.session.collectAsState()
    val members by WatchParty.members.collectAsState()
    val notice by WatchParty.notice.collectAsState()
    val noticeContext = LocalContext.current
    LaunchedEffect(notice) {
        notice?.let {
            com.biaruuu.kiro.ui.i18n.I18nToast.make(noticeContext, it, Toast.LENGTH_SHORT).show()
            WatchParty.notice.value = null
        }
    }
    LaunchedEffect(party?.code) {
        PartyChat.listen(party?.code)
        if (party == null) PartyVoice.leave()
    }
    LaunchedEffect(members) { PartyVoice.reconcile(members) }
}

@Composable
private fun SoftButton(label: String, filled: Boolean = false, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PtButton(label, modifier, filled, danger, true, onClick)

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
        if (!joined) {
            Text("Voice chat", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Talk with everyone in the room. Earphones help avoid echo.", color = PrMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(14.dp))
            SoftButton("Join voice", filled = true, modifier = Modifier.fillMaxWidth()) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    PartyVoice.join(context)
                } else {
                    ask.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PtDot(if (muted) Color(0xFFFF6B6B) else PrGreen, 8)
                Spacer(Modifier.width(8.dp))
                Text(if (muted) "You're muted" else "You're in voice", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(if (muted) "Unmute" else "Mute", modifier = Modifier.weight(1f)) { PartyVoice.setMuted(!muted) }
                SoftButton("Leave", danger = true, modifier = Modifier.weight(1f)) { PartyVoice.leave() }
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

/** "Today 11:20 AM" or "Nov 21, 11:20 AM": the small centred time between runs of messages. */
private fun stampLabel(at: Long): String =
    if (android.text.format.DateUtils.isToday(at)) "Today " + timeFormat.format(Date(at)) else dayFormat.format(Date(at))

/** The live message list with a box to write in. Long-press one of your own messages to edit or delete it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PartyChatPane(modifier: Modifier = Modifier, onInvite: (() -> Unit)? = null) {
    val msgs by PartyChat.messages.collectAsState()
    val me = WatchParty.uid()
    var field by remember { mutableStateOf<android.widget.EditText?>(null) }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex) }
    var editing by remember { mutableStateOf<PartyChat.Msg?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val members by WatchParty.members.collectAsState()
    val lastOwnId = msgs.lastOrNull { it.uid == me }?.id
    // "Typing" fades by itself, so look again every second.
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            tick = android.os.SystemClock.elapsedRealtime()
        }
    }
    // "Seen by": the newest message counts as seen while this chat is on screen and the app is in front.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed = true
            else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifecycleOwner.lifecycle.addObserver(o)
        onDispose { lifecycleOwner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(msgs.lastOrNull()?.id, resumed) {
        if (resumed) msgs.lastOrNull()?.let { WatchParty.markSeen(it.at) }
    }
    fun send() {
        if (text.isBlank()) return
        val e = editing
        if (e != null) {
            PartyChat.edit(e, text)
            editing = null
        } else {
            PartyChat.send(text)
        }
        field?.setText("")
        text = ""
    }
    var emojiOpen by remember { mutableStateOf(false) }
    fun focusField() {
        emojiOpen = false
        field?.let {
            it.requestFocus()
            (it.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(it, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val hostUid = WatchParty.members.collectAsState().value.firstOrNull { it.host }?.uid
    Column(modifier.imePadding()) {
        if (msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val alone = WatchParty.members.collectAsState().value.size <= 1
                    Box(Modifier.size(56.dp).clip(CircleShape).background(PrChip), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            painterResource(com.biaruuu.kiro.R.drawable.fluent_chat_regular), contentDescription = null,
                            tint = PrMuted, modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(if (alone) "Invite your friends" else "No messages yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (alone) "Invite others so you can watch and chat together." else "Say hi to everyone.",
                        color = PrMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
                    )
                    if (alone && onInvite != null) {
                        Spacer(Modifier.height(16.dp))
                        SoftButton("Invite friends", filled = true, onClick = onInvite)
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(msgs, key = { _, m -> m.id }) { i, m ->
                    val mine = m.uid == me
                    val iAmHost = me != null && me == hostUid
                    val prev = msgs.getOrNull(i - 1)
                    val next = msgs.getOrNull(i + 1)
                    val newStamp = prev == null || m.at - prev.at > 15 * 60_000L
                    val first = prev == null || prev.uid != m.uid || m.at - prev.at > 120_000L
                    val last = next == null || next.uid != m.uid || next.at - m.at > 120_000L
                    val canMenu = mine || iAmHost
                    Column(Modifier.fillMaxWidth()) {
                        if (newStamp) {
                            Text(
                                stampLabel(m.at), color = PtFaint, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = if (i == 0) 2.dp else 12.dp, bottom = 8.dp),
                            )
                        } else if (first) {
                            Spacer(Modifier.height(8.dp))
                        }
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                if (!mine) {
                                    Box(Modifier.width(40.dp).padding(end = 6.dp), contentAlignment = Alignment.Center) {
                                        if (last) DecoratedAvatar(m.avatar, m.name, 30.dp, m.decoration)
                                    }
                                }
                                Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                                    if (first && !mine) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)) {
                                            Text(m.name, color = PrMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            if (m.uid == hostUid) {
                                                Spacer(Modifier.width(4.dp))
                                                androidx.compose.material3.Icon(
                                                    painterResource(com.biaruuu.kiro.R.drawable.fluent_crown_filled), contentDescription = "Host",
                                                    tint = Color.Unspecified, modifier = Modifier.size(12.dp),
                                                )
                                            }
                                        }
                                    }
                                    // iMessage-style: round bubbles; the last one of a run gets the small "tail" corner.
                                    val bubble = RoundedCornerShape(
                                        topStart = 18.dp, topEnd = 18.dp,
                                        bottomStart = if (!mine && last) 4.dp else 18.dp, bottomEnd = if (mine && last) 4.dp else 18.dp,
                                    )
                                    Column(
                                        Modifier.clip(bubble).background(if (mine) PtBlue else PtBubble)
                                            .then(if (canMenu) Modifier.combinedClickable(onClick = {}, onLongClick = { menuFor = m.id }) else Modifier)
                                            .padding(horizontal = 13.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.End,
                                    ) {
                                        EmojiText(m.text, Color.White, 15f)
                                        if (m.edited) Text("edited", color = Color(0x99FFFFFF), fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                if (mine) {
                                    Box(Modifier.width(40.dp).padding(start = 6.dp), contentAlignment = Alignment.Center) {
                                        if (last) DecoratedAvatar(m.avatar, m.name, 30.dp, m.decoration)
                                    }
                                }
                            }
                        DropdownMenu(
                            expanded = menuFor == m.id,
                            onDismissRequest = { menuFor = null },
                            modifier = Modifier.background(PrCard),
                        ) {
                            if (mine) DropdownMenuItem(
                                text = { Text("Edit message", color = Color.White, fontSize = 15.sp) },
                                trailingIcon = {
                                    androidx.compose.material3.Icon(
                                        painterResource(com.biaruuu.kiro.R.drawable.fluent_edit_regular), contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuFor = null
                                    editing = m
                                    field?.let { f ->
                                        f.setText(m.text)
                                        f.setSelection(f.text.length)
                                    }
                                    focusField()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete message", color = Color(0xFFFF5C5C), fontSize = 15.sp) },
                                trailingIcon = {
                                    androidx.compose.material3.Icon(
                                        painterResource(com.biaruuu.kiro.R.drawable.fluent_delete_regular), contentDescription = null,
                                        tint = Color(0xFFFF5C5C), modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuFor = null
                                    PartyChat.delete(m.id)
                                },
                            )
                        }
                        }
                        if (m.id == lastOwnId) {
                            val others = members.filter { it.uid != me }
                            val seenBy = others.filter { it.seenAt >= m.at }
                            if (seenBy.isNotEmpty()) {
                                Text(
                                    when {
                                        seenBy.size == others.size && seenBy.size > 1 -> "Seen by everyone"
                                        seenBy.size <= 2 -> "Seen by " + seenBy.joinToString(", ") { it.name }
                                        else -> "Seen by ${seenBy.size}"
                                    },
                                    color = PrMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 3.dp, end = 50.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        val typers = members.filter { it.uid != me && it.typingUntil > maxOf(tick, android.os.SystemClock.elapsedRealtime()) }
        Box(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            if (typers.isNotEmpty()) {
                Text(
                    when (typers.size) {
                        1 -> "${typers[0].name} is typing..."
                        2 -> "${typers[0].name} and ${typers[1].name} are typing..."
                        else -> "Several people are typing..."
                    },
                    color = PtFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        editing?.let { e ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(PrChip)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(2.dp).height(30.dp).background(Color.White))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Editing message", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(e.text, color = PrMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                androidx.compose.material3.Icon(
                    painterResource(com.biaruuu.kiro.R.drawable.fluent_dismiss_regular), contentDescription = "Cancel edit",
                    tint = PrMuted,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable {
                        editing = null
                        field?.setText("")
                        text = ""
                    }.padding(8.dp),
                )
            }
        }
        val ready = text.isNotBlank()
        Row(
            modifier = Modifier.fillMaxWidth().then(if (emojiOpen) Modifier else Modifier.navigationBarsPadding())
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(com.biaruuu.kiro.R.drawable.fluent_emoji_regular),
                contentDescription = "Emoji", tint = if (emojiOpen) Color.White else PrMuted,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable {
                    emojiOpen = !emojiOpen
                    if (emojiOpen) keyboard?.hide() else field?.requestFocus()
                }.padding(10.dp),
            )
            // iMessage-style: an outlined rounded field, with the blue send arrow appearing inside it.
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(22.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusField() }
                        .padding(start = 16.dp, end = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    EmojiInput(
                        hint = "Type a message", textColor = Color.White, hintColor = PrMuted, modifier = Modifier.fillMaxWidth(),
                        onReady = { field = it }, onChange = { text = it; WatchParty.setTyping(it.isNotBlank()) }, onSend = { send() },
                    )
                }
                androidx.compose.animation.AnimatedVisibility(visible = ready, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.fadeOut()) {
                    Box(
                        Modifier.padding(end = 6.dp).size(32.dp).clip(CircleShape).background(PtBlue).clickable { send() },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(com.biaruuu.kiro.R.drawable.fluent_arrow_up_filled),
                            contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        if (emojiOpen) {
            EmojiPickerPanel(height = 260.dp, modifier = Modifier.navigationBarsPadding(), onPick = { e ->
                val f = field ?: return@EmojiPickerPanel
                val at = f.selectionStart.coerceAtLeast(0)
                if (f.text.length + e.length <= 500) f.text.insert(at, e)
            })
        }
    }
}

/** New messages from friends over the video: a few small flat cards that fade away by themselves. */
@Composable
fun PartyToast(modifier: Modifier = Modifier) {
    val msgs by PartyChat.messages.collectAsState()
    val me = WatchParty.uid()
    val shown = remember { androidx.compose.runtime.mutableStateListOf<PartyChat.Msg>() }
    var lastSeen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msgs.lastOrNull()?.id) {
        val last = msgs.lastOrNull() ?: return@LaunchedEffect
        if (lastSeen == null) { lastSeen = last.id; return@LaunchedEffect } // old history is not announced
        if (last.id == lastSeen) return@LaunchedEffect
        lastSeen = last.id
        WatchParty.markSeen(last.at) // it is on screen, so it counts as seen
        if (last.uid == me) return@LaunchedEffect
        shown.add(last)
        while (shown.size > 3) shown.removeAt(0)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { m ->
            androidx.compose.runtime.key(m.id) { ToastCard(m) { shown.remove(m) } }
        }
    }
}

@Composable
private fun ToastCard(m: PartyChat.Msg, onDone: () -> Unit) {
    LaunchedEffect(m.id) {
        kotlinx.coroutines.delay(5000)
        onDone()
    }
    val visible = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    androidx.compose.animation.AnimatedVisibility(visibleState = visible, enter = androidx.compose.animation.fadeIn()) {
        // A light, slightly see-through bubble with dark text: small, so it never hides the picture, and readable on any scene.
        Row(
            modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE6F4F4F8))
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { DecoratedAvatar(m.avatar, m.name, 24.dp, m.decoration) }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(m.name, color = Color(0xFF6B6B73), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EmojiText(m.text, Color(0xFF111114), 13f, maxLines = 2)
            }
        }
    }
}

/** The room pill on the player's top bar: solid, so it stays clear over any picture. Tap for the room options. */
@Composable
fun PartyTopPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val party by WatchParty.session.collectAsState()
    val members by WatchParty.members.collectAsState()
    val p = party ?: return
    Row(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE6F4F4F8)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF1FB865)))
        Spacer(Modifier.width(7.dp))
        Text(p.code, color = Color(0xFF111114), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Icon(
            painterResource(com.biaruuu.kiro.R.drawable.fluent_people_regular), contentDescription = "Members",
            tint = Color(0xFF55555C), modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text("${members.size}", color = Color(0xFF55555C), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
                (if (r.title.isNotBlank()) r.title + " · " else "") + "Ep ${r.episode}",
                color = PrMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
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
