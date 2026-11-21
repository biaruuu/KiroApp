package com.biaruuu.kiro.ui.party

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.PartyVoice
import com.biaruuu.kiro.data.WatchParty
import com.biaruuu.kiro.ui.auth.UnderlineField
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.me.ToggleRow
import com.biaruuu.kiro.ui.player.PlayerScreen

private val RmBg = Color(0xFF121212)
private val RmCard = Color(0xFF18181B)
private val RmHair = Color(0xFF26262B)
private val RmChip = Color(0xFF222226)
private val RmMuted = Color(0xFF9A9AA2)
private val RmRed = Color(0xFFFF6B6B)
private val RmGreen = Color(0xFF3DDC84)

private enum class Sheet { Settings, Overview, Members, Nickname, Invite }

@Composable
private fun RmButton(label: String, filled: Boolean = false, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color.Black else if (danger) RmRed else Color.White,
        fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(if (filled) Color.White else RmChip)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/**
 * The watch party room, as its own page: the player on top (it keeps the normal Kiro player look and can
 * go fullscreen), and chat and voice underneath. On a tablet the player sits beside the chat instead.
 */
@Composable
fun PartyRoomScreen(onBack: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val party by WatchParty.session.collectAsState()
    val members by WatchParty.members.collectAsState()
    val remote by WatchParty.remote.collectAsState()
    val room by WatchParty.room.collectAsState()
    val p = party
    if (p == null) {
        Box(modifier.background(RmBg).statusBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You're not in a room.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Start or join one from Me > Watch party.", color = RmMuted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                RmButton("Back", filled = true, onClick = onBack)
            }
        }
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var manualFull by remember { mutableStateOf(false) }
    BackHandler(enabled = sheet != null) { sheet = null }
    BackHandler(enabled = manualFull && sheet == null) { manualFull = false }
    LaunchedEffect(room.voiceOff) { if (room.voiceOff) PartyVoice.leave() }

    BoxWithConstraints(modifier.background(RmBg)) {
        val phoneLandscape = maxHeight < 500.dp && maxWidth > maxHeight
        val wide = !phoneLandscape && maxWidth >= 720.dp
        val full = manualFull || phoneLandscape
        val headerH = 56.dp
        val topInset = with(LocalDensity.current) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
        val sideW = 380.dp
        val hasVideo = remote?.animeId?.let { it > 0 } == true

        // ---- Page chrome: header, then the space the player will cover, then chat and voice ----
        if (!full) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(headerH).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.fluent_chevron_left_regular), contentDescription = "Back", tint = Color.White,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack).padding(10.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                room.name.ifBlank { "Watch party" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (p.host) {
                                Spacer(Modifier.width(6.dp))
                                Icon(painterResource(R.drawable.fluent_crown_filled), contentDescription = "You are the host", tint = Color.Unspecified, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text("${p.code} · ${members.size} in room", color = RmMuted, fontSize = 12.sp)
                    }
                    RmButton("Invite", filled = true) { sheet = Sheet.Invite }
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        painterResource(R.drawable.fluent_more_vertical_regular), contentDescription = "Room settings", tint = Color.White,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { sheet = Sheet.Settings }.padding(10.dp),
                    )
                }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        RoomPanel(tab, { tab = it }, hasVideo, { manualFull = true }, { sheet = Sheet.Invite }, Modifier.width(sideW).fillMaxHeight())
                    }
                } else {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    RoomPanel(tab, { tab = it }, hasVideo, { manualFull = true }, { sheet = Sheet.Invite }, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        // ---- The player layer. It stays in the same place in the tree, so rotating or going fullscreen never restarts it. ----
        val playerMod = when {
            full -> Modifier.fillMaxSize()
            wide -> Modifier.padding(top = topInset + headerH).width(maxWidth - sideW).fillMaxHeight()
            else -> Modifier.padding(top = topInset + headerH).fillMaxWidth().aspectRatio(16f / 9f)
        }
        Box(playerMod.background(Color.Black)) {
            val r = remote
            if (r != null && r.animeId > 0) {
                key(r.animeId) {
                    PlayerScreen(
                        animeId = r.animeId,
                        startEpisode = r.episode,
                        onClose = { if (manualFull) manualFull = false else onBack() },
                        modifier = Modifier.fillMaxSize(),
                        partyCode = p.code,
                        fullscreen = full,
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nothing playing yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (p.host) "Pick an anime and start an episode. It plays here for everyone."
                        else "Waiting for the host to pick something. You can browse Kiro meanwhile; the room stays open.",
                        color = RmMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (p.host) RmButton("Browse anime", filled = true, onClick = onBrowse)
                    if (full) {
                        Spacer(Modifier.height(8.dp))
                        RmButton("Back", onClick = { if (manualFull) manualFull = false else onBack() })
                    }
                }
            }
        }

        // ---- Sheets ----
        sheet?.let { open ->
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheet = null },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(RmCard).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SheetContent(open, p, members, room, context, onNavigate = { sheet = it }, onClose = { sheet = null }, onLeave = { sheet = null; WatchParty.leave(); onBack() })
                }
            }
        }
    }
}

/** The tabs under the player: chat and voice, plus a fullscreen button. */
@Composable
private fun RoomPanel(
    tab: Int, onTab: (Int) -> Unit, hasVideo: Boolean, onFullscreen: () -> Unit, onInvite: () -> Unit, modifier: Modifier,
) {
    val room by WatchParty.room.collectAsState()
    Column(modifier.background(RmBg)) {
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            val tabs = listOf(R.drawable.fluent_chat_regular to "Chat", R.drawable.fluent_phone_regular to "Voice")
            tabs.forEachIndexed { i, (icon, label) ->
                val on = tab == i
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(if (on) RmCard else RmBg).clickable { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp).background(if (on) Color.White else Color.Transparent))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(icon), contentDescription = label, tint = if (on) Color.White else RmMuted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = if (on) Color.White else RmMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (hasVideo) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(RmBg).clickable(onClick = onFullscreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.fluent_fullscreen_regular), contentDescription = "Fullscreen", tint = RmMuted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fullscreen", color = RmMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RmHair))
        if (tab == 0) {
            PartyChatPane(Modifier.weight(1f).fillMaxWidth(), onInvite = onInvite)
        } else {
            VoicePane(room.voiceOff, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun VoicePane(voiceOff: Boolean, modifier: Modifier) {
    val members by WatchParty.members.collectAsState()
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (voiceOff) {
                Text("Voice chat is turned off by the host.", color = RmMuted, fontSize = 13.sp)
            } else {
                VoiceControls()
                Spacer(Modifier.height(6.dp))
                Text("Voice goes straight between phones. Use earphones to avoid echo.", color = RmMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        items(members, key = { it.uid }) { m ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RmCard).border(1.dp, RmHair, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DecoratedAvatar(m.avatar, m.name, 36.dp, m.decoration)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (m.host) {
                            Spacer(Modifier.width(6.dp))
                            Icon(painterResource(R.drawable.fluent_crown_filled), contentDescription = "Host", tint = Color.Unspecified, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text(
                    if (!m.voice) "Not in voice" else if (m.muted) "Muted" else "Talking",
                    color = if (!m.voice) RmMuted else if (m.muted) RmRed else RmGreen, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, back: (() -> Unit)?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            Icon(
                painterResource(R.drawable.fluent_chevron_left_regular), contentDescription = "Back", tint = Color.White,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = back).padding(6.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.fluent_dismiss_regular), contentDescription = "Close", tint = RmMuted,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClose).padding(8.dp),
        )
    }
}

@Composable
private fun SheetRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (danger) RmRed else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (!danger) Icon(painterResource(R.drawable.fluent_chevron_right_regular), contentDescription = null, tint = RmMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SheetContent(
    open: Sheet, p: WatchParty.Session, members: List<WatchParty.Member>, room: WatchParty.Room, context: Context,
    onNavigate: (Sheet) -> Unit, onClose: () -> Unit, onLeave: () -> Unit,
) {
    when (open) {
        Sheet.Settings -> {
            SheetTitle("Room settings", null, onClose)
            SheetRow("Overview") { onNavigate(Sheet.Overview) }
            SheetRow("Members") { onNavigate(Sheet.Members) }
            SheetRow("Nickname") { onNavigate(Sheet.Nickname) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(RmHair))
            SheetRow(if (p.host) "End room for everyone" else "Leave room", danger = true, onClick = onLeave)
        }
        Sheet.Overview -> {
            SheetTitle("Overview", { onNavigate(Sheet.Settings) }, onClose)
            var name by remember(room.name) { mutableStateOf(room.name) }
            if (p.host) {
                UnderlineField(
                    value = name, onChange = { if (it.length <= 40) name = it }, hint = "Room name",
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done, onImeAction = { WatchParty.setRoomName(name) },
                    autoFocus = false,
                )
                Spacer(Modifier.height(8.dp))
                RmButton("Save name", filled = true, modifier = Modifier.fillMaxWidth()) {
                    WatchParty.setRoomName(name)
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
            } else {
                Text(room.name.ifBlank { "Watch party" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Code ${p.code}", color = RmMuted, fontSize = 13.sp)
            Text("Host ${room.hostName.ifBlank { members.firstOrNull { it.host }?.name.orEmpty() }}", color = RmMuted, fontSize = 13.sp)
            Text("${members.size} in this room · ${members.count { it.voice }} in voice", color = RmMuted, fontSize = 13.sp)
            if (p.host) {
                Spacer(Modifier.height(8.dp))
                ToggleRow("Turn off voice chat", room.voiceOff) { WatchParty.setVoiceOff(it) }
                Text("Hides voice chat for everyone in the room.", color = RmMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(14.dp))
                var confirm by remember { mutableStateOf(false) }
                RmButton("Clear all chat messages", danger = true, modifier = Modifier.fillMaxWidth()) { confirm = true }
                if (confirm) {
                    AlertDialog(
                        onDismissRequest = { confirm = false }, containerColor = RmCard,
                        title = { Text("Clear the chat?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                        text = { Text("Every message in this room will be deleted for everyone.", color = Color(0xFFD0D0D4), fontSize = 14.sp) },
                        confirmButton = { TextButton(onClick = { confirm = false; WatchParty.clearChat() }) { Text("Clear", color = RmRed) } },
                        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = RmMuted) } },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Sheet.Members -> {
            SheetTitle("Members", { onNavigate(Sheet.Settings) }, onClose)
            members.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DecoratedAvatar(m.avatar, m.name, 40.dp, m.decoration)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (m.host) {
                                Spacer(Modifier.width(6.dp))
                                Icon(painterResource(R.drawable.fluent_crown_filled), contentDescription = "Host", tint = Color.Unspecified, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(if (m.host) "Host" else if (m.voice) "In voice" else "Watching", color = RmMuted, fontSize = 12.sp)
                    }
                    if (p.host && !m.host) {
                        Text(
                            "Remove", color = RmRed, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { WatchParty.ban(m.uid, m.name) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (p.host) {
                Spacer(Modifier.height(10.dp))
                Text("REMOVED", color = RmMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (room.banned.isEmpty()) {
                    Text("No one has been removed.", color = RmMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    room.banned.forEach { (uid, name) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(
                                "Allow", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { WatchParty.unban(uid) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Sheet.Nickname -> {
            SheetTitle("Nickname", { onNavigate(Sheet.Settings) }, onClose)
            var nick by remember { mutableStateOf("") }
            Text("Shown in this room's chat and member list only.", color = RmMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            UnderlineField(
                value = nick, onChange = { if (it.length <= 24) nick = it }, hint = "Nickname in this room",
                keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                onImeAction = { WatchParty.setNickname(nick); onNavigate(Sheet.Settings) },
            )
            Spacer(Modifier.height(12.dp))
            RmButton("Save", filled = true, modifier = Modifier.fillMaxWidth()) {
                WatchParty.setNickname(nick)
                onNavigate(Sheet.Settings)
            }
            Spacer(Modifier.height(8.dp))
        }
        Sheet.Invite -> {
            SheetTitle("Invite", null, onClose)
            Text("SHARE THIS CODE WITH FRIENDS", color = RmMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                p.code, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 8.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RmBg).border(1.dp, RmHair, RoundedCornerShape(14.dp)).padding(vertical = 18.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RmButton("Copy code", modifier = Modifier.weight(1f)) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Kiro room code", p.code))
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                }
                RmButton("Share", filled = true, modifier = Modifier.weight(1f)) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Join my Kiro watch party! Open Kiro > Me > Watch party and enter the code ${p.code}")
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Friends need Kiro and to be logged in. They enter the code under Me > Watch party.", color = RmMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}
