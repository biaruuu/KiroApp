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
 * The developer's live message: a dark card that fades in at the top of the screen on every phone at
 * the same moment, shows the verified name, and fades away by itself. Like and dislike are tapped on it
 * and the totals update for everyone while it is up.
 */
@Composable
fun LiveAnnouncementOverlay() {
    val ui = rememberUi()
    val live by GlobalSocket.current.collectAsState()
    val choice by GlobalSocket.myChoice.collectAsState()
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && last != null,
            enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { -it / 4 },
            exit = fadeOut(tween(700)),
        ) {
            val l = last ?: return@AnimatedVisibility
            val shape = RoundedCornerShape(ui.dp(22f))
            val name = admin?.username?.ifBlank { null } ?: l.username
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = ui.dp(14f), vertical = ui.dp(10f))
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF151517).copy(alpha = 0.96f))
                    .border(1.dp, Color(0xFF2C2C30), shape),
            ) {
                Column(Modifier.padding(start = ui.dp(16f), end = ui.dp(12f), top = ui.dp(14f))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DecoratedAvatar(admin?.avatar.orEmpty(), name, ui.dp(38f), admin?.decoration)
                        Spacer(Modifier.size(ui.dp(10f)))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name,
                                    color = Color.White,
                                    fontSize = ui.sp(15f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = com.biaruuu.kiro.data.NameStyles.family(context, admin?.nameFont.orEmpty()),
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.size(5.dp))
                                VerifiedBadge(ui.dp(16f))
                            }
                            Text("Kiro developer", color = Color(0xFF8E8E96), fontSize = ui.sp(11.5f), maxLines = 1)
                        }
                        Icon(
                            painter = painterResource(R.drawable.fluent_dismiss_regular),
                            contentDescription = "Close",
                            tint = Color(0xFF8E8E96),
                            modifier = Modifier
                                .size(ui.dp(30f))
                                .clip(CircleShape)
                                .clickable { visible = false }
                                .padding(ui.dp(6f)),
                        )
                    }
                    Spacer(Modifier.height(ui.dp(10f)))
                    Text(
                        l.text,
                        color = Color(0xFFE6E6EA),
                        fontSize = ui.sp(15f),
                        lineHeight = ui.sp(21f),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(ui.dp(12f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(10f))) {
                        Reaction(R.drawable.ic_thumb_up, l.likes, choice == "like", "Like") { GlobalSocket.react("like") }
                        Reaction(R.drawable.ic_thumb_down, l.dislikes, choice == "dislike", "Dislike") { GlobalSocket.react("dislike") }
                    }
                    Spacer(Modifier.height(ui.dp(12f)))
                }
                // Time left, drawn as a thin line that runs out.
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF222226))) {
                    Box(Modifier.fillMaxWidth(progress.value).height(2.dp).background(Color.White.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

@Composable
private fun Reaction(icon: Int, count: Int, picked: Boolean, label: String, onClick: () -> Unit) {
    val ui = rememberUi()
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(source)
            .height(ui.dp(36f))
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
