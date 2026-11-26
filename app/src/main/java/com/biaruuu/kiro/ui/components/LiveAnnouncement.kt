package com.biaruuu.kiro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Announcements
import com.biaruuu.kiro.data.GlobalSocket
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.me.VerifiedBadge
import com.biaruuu.kiro.ui.theme.pressScale
import com.biaruuu.kiro.ui.theme.rememberUi
import kotlinx.coroutines.delay

/**
 * The developer's live message, shown on every open phone at the same moment and fading away by itself.
 * On a normal screen it is a small dark card (name with the verified tick, the message, like and dislike).
 * While a video is playing it becomes a thin one-line bar at the very top so the picture stays visible.
 */
@Composable
fun LiveAnnouncementOverlay() {
    val ui = rememberUi()
    val live by GlobalSocket.current.collectAsState()
    val choice by GlobalSocket.myChoice.collectAsState()
    val inPlayer by GlobalSocket.inPlayer.collectAsState()
    val admin by Announcements.admin.collectAsState()
    val context = LocalContext.current

    // Keep the last message while it fades out.
    var last by remember { mutableStateOf<GlobalSocket.Live?>(null) }
    if (live != null) last = live
    var visible by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(live?.id) {
        val l = live
        if (l == null) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        progress.snapTo(l.remainingMs.toFloat() / l.durationMs.toFloat().coerceAtLeast(1f))
        progress.animateTo(0f, tween(l.remainingMs.toInt(), easing = LinearEasing))
        visible = false
    }

    Box(
        Modifier.fillMaxSize().statusBarsPadding().displayCutoutPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible && last != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 5 },
            exit = fadeOut(tween(600)),
        ) {
            val l = last ?: return@AnimatedVisibility
            val name = admin?.username?.ifBlank { null } ?: l.username
            val nameFont = com.biaruuu.kiro.data.NameStyles.family(context, admin?.nameFont.orEmpty())

            if (inPlayer) {
                // Thin bar: one line, small, see-through; it never covers the middle of the video.
                val shape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .padding(horizontal = ui.dp(12f), vertical = ui.dp(6f))
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                        .padding(start = ui.dp(6f), end = ui.dp(4f), top = ui.dp(4f), bottom = ui.dp(4f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DecoratedAvatar(admin?.avatar.orEmpty(), name, ui.dp(26f), null)
                    Spacer(Modifier.size(ui.dp(8f)))
                    Text(
                        name, color = Color.White, fontSize = ui.sp(12.5f), fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = nameFont,
                        modifier = Modifier.widthIn(max = ui.dp(110f)),
                    )
                    Spacer(Modifier.size(4.dp))
                    VerifiedBadge(ui.dp(13f))
                    Spacer(Modifier.size(ui.dp(8f)))
                    Text(
                        l.text, color = Color.White.copy(alpha = 0.92f), fontSize = ui.sp(12.5f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(ui.dp(6f)))
                    MiniReaction(R.drawable.ic_thumb_up, l.likes, choice == "like") { GlobalSocket.react("like") }
                    Spacer(Modifier.size(ui.dp(4f)))
                    MiniReaction(R.drawable.ic_thumb_down, l.dislikes, choice == "dislike") { GlobalSocket.react("dislike") }
                }
            } else {
                val shape = RoundedCornerShape(ui.dp(20f))
                Column(
                    modifier = Modifier
                        .padding(horizontal = ui.dp(14f), vertical = ui.dp(10f))
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0xFF111113).copy(alpha = 0.97f))
                        .border(1.dp, Color(0xFF2A2A2E), shape),
                ) {
                    Column(Modifier.padding(start = ui.dp(14f), end = ui.dp(8f), top = ui.dp(12f))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DecoratedAvatar(admin?.avatar.orEmpty(), name, ui.dp(34f), admin?.decoration)
                            Spacer(Modifier.size(ui.dp(10f)))
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name, color = Color.White, fontSize = ui.sp(14.5f), fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = nameFont,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.size(5.dp))
                                VerifiedBadge(ui.dp(15f))
                            }
                            Icon(
                                painter = painterResource(R.drawable.fluent_dismiss_regular),
                                contentDescription = "Close",
                                tint = Color(0xFF8E8E96),
                                modifier = Modifier
                                    .size(ui.dp(30f))
                                    .clip(CircleShape)
                                    .clickable { visible = false }
                                    .padding(ui.dp(7f)),
                            )
                        }
                        Spacer(Modifier.height(ui.dp(8f)))
                        Text(
                            l.text, color = Color(0xFFE6E6EA), fontSize = ui.sp(14.5f), lineHeight = ui.sp(20f),
                            maxLines = 5, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = ui.dp(6f)),
                        )
                        Spacer(Modifier.height(ui.dp(10f)))
                        Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(8f))) {
                            Reaction(R.drawable.ic_thumb_up, l.likes, choice == "like", "Like") { GlobalSocket.react("like") }
                            Reaction(R.drawable.ic_thumb_down, l.dislikes, choice == "dislike", "Dislike") { GlobalSocket.react("dislike") }
                        }
                        Spacer(Modifier.height(ui.dp(12f)))
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF1C1C1F))) {
                        Box(Modifier.fillMaxWidth(progress.value).height(2.dp).background(Color.White.copy(alpha = 0.65f)))
                    }
                }
            }
        }
    }
}

/** Icon-only like or dislike with its count, small enough for the thin player bar. */
@Composable
private fun MiniReaction(icon: Int, count: Int, picked: Boolean, onClick: () -> Unit) {
    val ui = rememberUi()
    Row(
        modifier = Modifier
            .height(ui.dp(26f))
            .clip(RoundedCornerShape(50))
            .background(if (picked) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = ui.dp(9f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon), contentDescription = null,
            tint = if (picked) Color.Black else Color.White, modifier = Modifier.size(ui.dp(13f)),
        )
        Spacer(Modifier.size(4.dp))
        Text(count.toString(), color = if (picked) Color.Black else Color.White, fontSize = ui.sp(12f), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Reaction(icon: Int, count: Int, picked: Boolean, label: String, onClick: () -> Unit) {
    val ui = rememberUi()
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(source)
            .height(ui.dp(34f))
            .clip(RoundedCornerShape(50))
            .background(if (picked) Color.White else Color(0xFF232326))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = ui.dp(14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (picked) Color.Black else Color.White,
            modifier = Modifier.size(ui.dp(17f)),
        )
        Spacer(Modifier.size(ui.dp(7f)))
        Text(
            count.toString(),
            color = if (picked) Color.Black else Color(0xFFE6E6EA),
            fontSize = ui.sp(14f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
