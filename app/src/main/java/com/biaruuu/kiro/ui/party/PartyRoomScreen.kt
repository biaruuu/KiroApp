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
import androidx.compose.foundation.shape.CircleShape
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

private val RmBg = PtBg
private val RmCard = PtSurface
private val RmHair = PtLine
private val RmChip = PtRaised
private val RmMuted = PtMuted
private val RmRed = PtRed
private val RmGreen = PtGreen

private enum class Sheet { Settings, Overview, Members, Nickname, Invite }

@Composable
private fun RmButton(label: String, filled: Boolean = false, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PtButton(label, modifier, filled, danger, true, onClick)

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
    val hostSettings by WatchParty.settings.collectAsState()
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
    // The pill and chat cards over the video jump to the chat (the host leaves fullscreen first).
    val chatAsk = PartyNav.chatRequest.intValue
    LaunchedEffect(chatAsk) {
        if (chatAsk > 0) {
            tab = 0
            if (p.host) manualFull = false
        }
    }

    BoxWithConstraints(modifier.background(RmBg)) {
        val phoneLandscape = maxHeight < 500.dp && maxWidth > maxHeight
        val wide = !phoneLandscape && maxWidth >= 720.dp
        // Only the host chooses fullscreen; guests follow it (a landscape phone is always full).
        val full = if (p.host) manualFull || phoneLandscape else hostSettings.fullscreen || phoneLandscape
        LaunchedEffect(full, p.host) {
            if (p.host) WatchParty.pushSettings(WatchParty.settings.value.copy(fullscreen = full))
        }
        val headerH = 60.dp
        val topInset = with(LocalDensity.current) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
        val sideW = (maxWidth * 0.32f).coerceIn(340.dp, 440.dp)
        val compactHeader = maxWidth < 400.dp
        val hasVideo = remote?.animeId?.let { it > 0 } == true

        // ---- Page chrome: header, then the space the player will cover, then chat and voice ----
        if (!full) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(headerH).padding(start = 4.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PtIconButton(R.drawable.fluent_chevron_left_regular, "Back", onClick = onBack)
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                room.name.ifBlank { "Watch party" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (p.host) {
                                Spacer(Modifier.width(6.dp))
                                Icon(painterResource(R.drawable.fluent_crown_filled), contentDescription = "You are the host", tint = Color.Unspecified, modifier = Modifier.size(17.dp))
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PtDot(PtGreen, 6)
                            Spacer(Modifier.width(6.dp))
                            Text("${members.size} watching", color = RmMuted, fontSize = 12.sp, maxLines = 1)
                            Text("  ·  ", color = PtFaint, fontSize = 12.sp)
                            Text(
                                p.code, color = RmMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Kiro room code", p.code))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                    if (!compactHeader) PtAvatarStack(members) { sheet = Sheet.Members }
                    Spacer(Modifier.width(4.dp))
                    PtButton("Invite", filled = true) { sheet = Sheet.Invite }
                    PtIconButton(R.drawable.fluent_more_vertical_regular, "Room settings") { sheet = Sheet.Settings }
                }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        RoomPanel(tab, { tab = it }, hasVideo && p.host, { manualFull = true }, { sheet = Sheet.Invite }, true, Modifier.width(sideW).fillMaxHeight())
                    }
                } else {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    RoomPanel(tab, { tab = it }, hasVideo && p.host, { manualFull = true }, { sheet = Sheet.Invite }, false, Modifier.weight(1f).fillMaxWidth())
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
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(RmCard).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(PtRaised))
                    SheetContent(open, p, members, room, context, onNavigate = { sheet = it }, onClose = { sheet = null }, onLeave = { sheet = null; WatchParty.leave(); onBack() })
                }
            }
        }
    }
}

/** Under the player: what is playing, then the Chat and Voice tabs. [side] draws a thin line on the left (tablet layout). */
@Composable
private fun RoomPanel(
    tab: Int, onTab: (Int) -> Unit, canFullscreen: Boolean, onFullscreen: () -> Unit, onInvite: () -> Unit, side: Boolean, modifier: Modifier,
) {
    val room by WatchParty.room.collectAsState()
    val remote by WatchParty.remote.collectAsState()
    val members by WatchParty.members.collectAsState()
    Row(modifier.background(RmBg)) {
        if (side) Box(Modifier.width(1.dp).fillMaxHeight().background(RmHair))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val r = remote
            if (r != null && r.animeId > 0) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.title.ifBlank { "Now playing" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Episode ${r.episode}" + room.hostName.ifBlank { r.hostName }.let { if (it.isBlank()) "" else "  ·  Hosted by $it" },
                            color = RmMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (canFullscreen) PtIconButton(R.drawable.fluent_fullscreen_regular, "Fullscreen", tint = RmMuted, onClick = onFullscreen)
                }
            }
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                val inVoice = members.count { it.voice }
                listOf("Chat" to 0, "Voice" to 1).forEach { (label, i) ->
                    val on = tab == i
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) }, contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = if (on) Color.White else RmMuted, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                            if (i == 1 && inVoice > 0) {
                                Spacer(Modifier.width(6.dp))
                                PtDot(RmGreen, 6)
                                Spacer(Modifier.width(3.dp))
                                Text("$inVoice", color = RmGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Box(
                            Modifier.align(Alignment.BottomCenter).size(width = 28.dp, height = 2.dp).clip(RoundedCornerShape(1.dp))
                                .background(if (on) Color.White else Color.Transparent),
                        )
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
}

@Composable
private fun VoicePane(voiceOff: Boolean, modifier: Modifier) {
    val members by WatchParty.members.collectAsState()
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Column(Modifier.fillMaxWidth().ptCard(18).padding(16.dp)) {
                if (voiceOff) {
                    Text("Voice chat is off", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("The host turned voice chat off for this room.", color = RmMuted, fontSize = 12.sp, lineHeight = 17.sp)
                } else {
                    VoiceControls()
                }
            }
            Spacer(Modifier.height(18.dp))
            PtSectionLabel("In this room · ${members.size}", Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        items(members, key = { it.uid }) { m ->
            val talking = m.voice && !m.muted
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).border(2.dp, if (talking) RmGreen else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { DecoratedAvatar(m.avatar, m.name, 34.dp, m.decoration) }
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (m.host) {
                        Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.fluent_crown_filled), contentDescription = "Host", tint = Color.Unspecified, modifier = Modifier.size(15.dp))
                    }
                }
                if (m.voice) {
                    PtDot(if (m.muted) RmRed else RmGreen, 6)
                    Spacer(Modifier.width(6.dp))
                    Text(if (m.muted) "Muted" else "Talking", color = if (m.muted) RmRed else RmGreen, fontSize = 12.sp)
                } else {
                    Text("Not in voice", color = PtFaint, fontSize = 12.sp)
                }
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
private fun SheetRow(label: String, hint: String = "", danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (danger) RmRed else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (hint.isNotBlank()) Text(hint, color = RmMuted, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        if (!danger) Icon(painterResource(R.drawable.fluent_chevron_right_regular), contentDescription = null, tint = RmMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = RmMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            SheetRow("Members", "${members.size}") { onNavigate(Sheet.Members) }
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
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RmBg).padding(horizontal = 14.dp, vertical = 6.dp)) {
                InfoRow("Code", p.code)
                InfoRow("Host", room.hostName.ifBlank { members.firstOrNull { it.host }?.name.orEmpty() })
                InfoRow("In room", "${members.size}")
                InfoRow("In voice", "${members.count { it.voice }}")
            }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.code.forEach { ch ->
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp)).background(RmBg).border(1.dp, RmHair, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch.toString(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
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
