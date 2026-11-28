package com.biaruuu.kiro.ui.community

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.CommunityChat
import com.biaruuu.kiro.data.GlobalSocket
import com.biaruuu.kiro.data.NameStyles
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.me.VerifiedBadge
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroOnSurfaceMuted
import com.biaruuu.kiro.ui.theme.KiroOutline
import com.biaruuu.kiro.ui.theme.KiroSurface
import com.biaruuu.kiro.ui.theme.KiroSurfaceHigh
import com.biaruuu.kiro.ui.theme.KiroText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val Blurple = Color(0xFF5865F2)
private val MentionInk = Color(0xFFAAB3FF)
private val MentionBg = Color(0x335865F2)
private val MeBar = Color(0xFFF0B232)
private val MeTint = Color(0x1AF0B232)

private sealed interface Row_ {
    class Day(val label: String) : Row_
    class Item(val msg: CommunityChat.Msg, val joined: Boolean, val mine: Boolean) : Row_
}

private fun dayLabel(at: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(at))
private fun timeLabel(at: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
private fun sameDay(a: Long, b: Long): Boolean = dayLabel(a) == dayLabel(b)

/** Discord-style community room: live messages, photos, emoji, @mentions and a typing indicator. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by AuthRepository.state.collectAsState()
    val profile by com.biaruuu.kiro.data.ProfileRepository.profile.collectAsState()
    val signedIn = state as? AuthState.SignedIn
    val ready = signedIn != null && profile.username.isNotBlank()

    val messages by CommunityChat.messages.collectAsState()
    val typing by CommunityChat.typing.collectAsState()
    val online by GlobalSocket.online.collectAsState()
    val connected by GlobalSocket.connected.collectAsState()

    DisposableEffect(ready) {
        if (ready) { CommunityChat.identify(); CommunityChat.setOpen(true) }
        onDispose { CommunityChat.setOpen(false) }
    }

    var field by remember { mutableStateOf(TextFieldValue("")) }
    var photo by remember { mutableStateOf<String?>(null) }
    var showEmoji by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var actions by remember { mutableStateOf<CommunityChat.Msg?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val data = withContext(Dispatchers.IO) { CommunityChat.prepareImage(context, uri) }
            if (data == null) Toast.makeText(context, "Couldn't use that photo.", Toast.LENGTH_SHORT).show() else photo = data
        }
    }

    val rows = remember(messages) {
        val out = ArrayList<Row_>()
        var prev: CommunityChat.Msg? = null
        for (m in messages) {
            if (prev == null || !sameDay(prev.at, m.at)) out.add(Row_.Day(dayLabel(m.at)))
            val joined = prev != null && sameDay(prev.at, m.at) && prev.uid == m.uid && m.at - prev.at < 5 * 60_000 && out.lastOrNull() is Row_.Item
            out.add(Row_.Item(m, joined, false))
            prev = m
        }
        out
    }
    val listState = rememberLazyListState()
    val atBottom by remember { derivedStateOf { listState.layoutInfo.let { it.totalItemsCount == 0 || (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 2 } } }
    var firstScroll by remember { mutableStateOf(true) }
    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        if (firstScroll) { listState.scrollToItem(rows.lastIndex); firstScroll = false }
        else if (atBottom) listState.animateScrollToItem(rows.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().background(KiroBackground).statusBarsPadding().imePadding()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.fluent_chat_regular), null, tint = KiroText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Community", color = KiroText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (connected) "$online online" else "Connecting…",
                    color = if (connected) Color(0xFF3BA55D) else KiroOnSurfaceMuted, fontSize = 12.sp,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(KiroOutline))

        if (!ready) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Join the conversation", color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (signedIn == null) "Log in to chat with other Kiro users." else "Choose a username in Me → Account to start chatting.",
                        color = KiroOnSurfaceMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                    if (signedIn == null) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(Blurple).clickable { AuthRepository.requestLogin() }
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                        ) { Text("Log in", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
                    }
                }
            }
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                Text("No messages yet. Say hi!", color = KiroOnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(rows.size, key = { i -> (rows[i] as? Row_.Item)?.msg?.id ?: "day$i" }) { i ->
                    when (val r = rows[i]) {
                        is Row_.Day -> DaySeparator(r.label)
                        is Row_.Item -> MessageRow(
                            r.msg, r.joined,
                            onImage = { viewing = it },
                            onLong = { actions = r.msg },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            if (!atBottom && rows.isNotEmpty()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(KiroSurfaceHigh).border(0.5.dp, KiroOutline, RoundedCornerShape(12.dp))
                        .clickable { scope.launch { listState.animateScrollToItem(rows.lastIndex) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.fluent_arrow_download_regular), "Latest", tint = KiroText, modifier = Modifier.size(20.dp)) }
            }
        }

        // Typing indicator
        val typers by produceState(emptyList<String>(), typing) {
            while (true) {
                val now = System.currentTimeMillis()
                value = typing.filterValues { it > now }.keys.filter { !it.equals(CommunityChat.myName, true) }.sorted()
                delay(800)
            }
        }
        Box(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (typers.isNotEmpty()) {
                Text(
                    when (typers.size) {
                        1 -> "${typers[0]} is typing…"
                        2 -> "${typers[0]} and ${typers[1]} are typing…"
                        else -> "Several people are typing…"
                    },
                    color = KiroOnSurfaceMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // @mention suggestions
        val query = remember(field) {
            val before = field.text.substring(0, field.selection.start.coerceAtMost(field.text.length))
            Regex("(?:^|\\s)@([A-Za-z0-9_.]*)$").find(before)?.groupValues?.get(1)
        }
        if (query != null) {
            val names = remember(messages, query) {
                (messages.map { it.name }.distinct().filter { !it.equals(CommunityChat.myName, true) } +
                    if (CommunityChat.isDev) listOf("everyone") else emptyList())
                    .filter { it.startsWith(query, ignoreCase = true) }.take(8)
            }
            if (names.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    items(names.size) { i ->
                        val n = names[i]
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(KiroSurfaceHigh).clickable {
                                val cur = field.selection.start.coerceAtMost(field.text.length)
                                val start = cur - query.length - 1
                                val next = field.text.substring(0, start) + "@" + n + " " + field.text.substring(cur)
                                field = TextFieldValue(next, TextRange(start + n.length + 2))
                            }.padding(horizontal = 12.dp, vertical = 7.dp),
                        ) { Text("@$n", color = MentionInk, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }

        // Photo preview
        photo?.let { p ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box {
                    ChatImage(p, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)), crop = true)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).clip(CircleShape).background(Color(0xCC000000)).clickable { photo = null },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.fluent_dismiss_regular), "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }

        // Composer
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton(R.drawable.fluent_add_regular, "Add photo") { picker.launch("image/*") }
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(KiroSurface)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it.copy(text = it.text.take(1000))
                        if (it.text.isNotBlank()) CommunityChat.typingPing()
                    },
                    textStyle = TextStyle(color = KiroText, fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(KiroText),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 5,
                    modifier = Modifier.weight(1f).padding(vertical = 11.dp),
                    decorationBox = { inner ->
                        if (field.text.isEmpty()) Text("Message the community", color = KiroOnSurfaceMuted, fontSize = 15.sp)
                        inner()
                    },
                )
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable {
                        showEmoji = !showEmoji
                        if (showEmoji) keyboard?.hide() else keyboard?.show()
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.fluent_emoji_regular), "Emoji", tint = if (showEmoji) Blurple else KiroOnSurfaceMuted, modifier = Modifier.size(22.dp)) }
            }
            val canSend = (field.text.isNotBlank() || photo != null) && !sending
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (canSend) Blurple else KiroSurface).clickable(enabled = canSend) {
                    sending = true
                    val text = field.text
                    val image = photo.orEmpty()
                    CommunityChat.send(context, text, image) { error ->
                        sending = false
                        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                    field = TextFieldValue("")
                    photo = null
                    scope.launch { delay(120); if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex) }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.fluent_send_regular), "Send", tint = if (canSend) Color.White else KiroOnSurfaceMuted, modifier = Modifier.size(20.dp)) }
        }

        if (showEmoji) {
            EmojiPanel { e ->
                val a = field.selection.min
                val b = field.selection.max
                field = TextFieldValue(field.text.replaceRange(a, b, e), TextRange(a + e.length))
            }
        }
    }

    viewing?.let { data ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xE6000000)).clickable { viewing = null }, contentAlignment = Alignment.Center) {
                ChatImage(data, Modifier.fillMaxWidth().padding(8.dp), crop = false)
            }
        }
    }

    actions?.let { m ->
        AlertDialog(
            onDismissRequest = { actions = null },
            containerColor = KiroSurface,
            title = { Text(m.name, color = KiroText, fontSize = 16.sp) },
            text = { Text(m.text.ifBlank { "Photo" }, color = KiroOnSurfaceMuted, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                Row {
                    if (m.text.isNotBlank()) TextButton(onClick = { clipboard.setText(AnnotatedString(m.text)); actions = null }) { Text("Copy", color = KiroText) }
                    if (CommunityChat.canDelete(context, m)) TextButton(onClick = { CommunityChat.delete(context, m); actions = null }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                }
            },
            dismissButton = { TextButton(onClick = { actions = null }) { Text("Close", color = KiroOnSurfaceMuted) } },
        )
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(KiroSurface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, tint = KiroText, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DaySeparator(label: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(0.5.dp).background(KiroOutline))
        Text(label, color = KiroOnSurfaceMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(0.5.dp).background(KiroOutline))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(m: CommunityChat.Msg, joined: Boolean, onImage: (String) -> Unit, onLong: () -> Unit) {
    val context = LocalContext.current
    val tagged = CommunityChat.mentionsMe(m) && m.uid != CommunityChat.myUid()
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .background(if (tagged) MeTint else Color.Transparent)
            .combinedClickable(onClick = {}, onLongClick = onLong)
            .padding(start = if (tagged) 0.dp else 3.dp, end = 12.dp, top = if (joined) 1.dp else 8.dp, bottom = 1.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (tagged) MeBar else Color.Transparent))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(40.dp), contentAlignment = Alignment.TopCenter) {
            if (!joined) DecoratedAvatar(m.avatar, m.name, 36.dp, m.decoration)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (!joined) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.name, color = if (m.dev) Color(0xFFC9B8FF) else KiroText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = NameStyles.family(context, m.nameFont), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (m.dev) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Blurple).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("DEV", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                        }
                        Spacer(Modifier.width(5.dp))
                        VerifiedBadge(14.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(timeLabel(m.at), color = KiroOnSurfaceMuted, fontSize = 11.sp)
                }
            }
            if (m.text.isNotEmpty()) {
                Text(
                    styled(m.text), color = KiroText, fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.padding(top = if (joined) 0.dp else 2.dp),
                )
            }
            if (m.image.isNotEmpty()) {
                ChatImage(
                    m.image,
                    Modifier.padding(top = 6.dp).widthIn(max = 260.dp).heightIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
                        .clickable { onImage(m.image) },
                    crop = false,
                )
            }
        }
    }
}

private fun styled(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in Regex("@[A-Za-z0-9_.]{2,20}").findAll(text)) {
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = MentionInk, background = MentionBg, fontWeight = FontWeight.Medium)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** A photo from a data address, decoded off the main thread. */
@Composable
private fun ChatImage(data: String, modifier: Modifier, crop: Boolean) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, data) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = bmp
    if (b == null) {
        Box(modifier.background(KiroSurface).height(120.dp))
    } else {
        Image(
            bitmap = b, contentDescription = "Photo", modifier = if (crop) modifier else modifier.fillMaxWidth(),
            contentScale = if (crop) ContentScale.Crop else ContentScale.FillWidth,
        )
    }
}

private val EmojiGroups: List<Pair<String, List<String>>> = listOf(
    "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 👽 🤖".split(" "),
    "👍" to "👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 ✍️ 💪 🦾 👏 🙌 👐 🤲 🫶 🫡 🫠 🫣 🫢 🫰 🫵".split(" "),
    "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💋 💯 💢 💥 💫 💦 💨 🔥 ✨ ⭐ 🌟 ⚡ 🎉 🎊 🎈".split(" "),
    "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🦆 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🦑 🐠 🐬 🐳 🦈 🐘 🦒 🐈 🐕".split(" "),
    "🍕" to "🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍔 🍟 🍕 🌭 🍿 🍜 🍝 🍣 🍙 🍚 🍰 🧁 🍩 🍪 🍫 🍬 🍭 ☕ 🍵 🥤 🍺 🍻 🥂".split(" "),
    "🎮" to "🎮 🎧 🎬 🎤 🎵 🎶 📺 📱 💻 📷 🔔 🔒 🔑 💡 📌 📎 ✏️ 📚 🏆 ⚽ 🏀 🎯 🎲 🚀 ✈️ 🚗 🏠 🌈 ☀️ 🌙 ☁️ ❄️ 🌊 🌸 🌹".split(" "),
)

@Composable
private fun EmojiPanel(onPick: (String) -> Unit) {
    var group by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().height(260.dp).background(KiroSurface).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            EmojiGroups.forEachIndexed { i, g ->
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (i == group) KiroSurfaceHigh else Color.Transparent)
                        .clickable { group = i }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(g.first, fontSize = 20.sp) }
            }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            items(EmojiGroups[group].second) { e ->
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(e) }, contentAlignment = Alignment.Center) {
                    Text(e, fontSize = 26.sp)
                }
            }
        }
    }
}

/** The pop-up shown when someone tags you (or the developer tags everyone). Tap it to read the whole message. */
@Composable
fun CommunityMentionPopup() {
    val msg by CommunityChat.popup.collectAsState()
    val inPlayer by GlobalSocket.inPlayer.collectAsState()
    var detail by remember { mutableStateOf<CommunityChat.Msg?>(null) }
    val shown = msg?.takeIf { !inPlayer }

    LaunchedEffect(shown?.id) {
        val id = shown?.id ?: return@LaunchedEffect
        delay(7_000)
        if (CommunityChat.popup.value?.id == id) CommunityChat.dismissPopup()
    }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = shown != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
        ) {
            val m = shown ?: return@AnimatedVisibility
            Row(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 520.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(KiroSurface).border(0.5.dp, KiroOutline, RoundedCornerShape(16.dp))
                    .clickable { detail = m; CommunityChat.dismissPopup() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DecoratedAvatar(m.avatar, m.name, 34.dp, m.decoration)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (m.everyone) "${m.name} tagged everyone" else "${m.name} mentioned you",
                        color = KiroText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(m.text.ifBlank { "Photo" }, color = KiroOnSurfaceMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(R.drawable.fluent_dismiss_regular), "Dismiss", tint = KiroOnSurfaceMuted,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { CommunityChat.dismissPopup() }.padding(6.dp),
                )
            }
        }
    }

    detail?.let { m ->
        Dialog(onDismissRequest = { detail = null }) {
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(KiroSurface).border(0.5.dp, KiroOutline, RoundedCornerShape(20.dp)).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecoratedAvatar(m.avatar, m.name, 40.dp, m.decoration)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, color = KiroText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (m.dev) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Blurple).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                    Text("DEV", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(5.dp))
                                VerifiedBadge(14.dp)
                            }
                        }
                        Text(if (m.everyone) "tagged everyone" else "mentioned you", color = KiroOnSurfaceMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text(styled(m.text.ifBlank { "Sent a photo." }), color = KiroText, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().then(Modifier))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { detail = null }) { Text("Close", color = KiroOnSurfaceMuted) }
                    TextButton(onClick = { detail = null; CommunityChat.requestOpen() }) { Text("Open Community", color = MentionInk) }
                }
            }
        }
    }
}
