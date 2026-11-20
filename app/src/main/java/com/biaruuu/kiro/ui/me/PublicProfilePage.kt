@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.biaruuu.kiro.ui.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.data.Achievements
import com.biaruuu.kiro.data.DeepLink
import com.biaruuu.kiro.data.NameStyles
import com.biaruuu.kiro.data.PublicProfile
import com.biaruuu.kiro.data.WatchStats
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.components.StreakBadge
import com.biaruuu.kiro.ui.i18n.I18nToast
import com.biaruuu.kiro.ui.i18n.Text

private val Card = Color(0xFF18181B)
private val Dim = Color(0xFF9A9AA2)

@Composable
private fun Pill(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(24.dp))
            .then(if (filled) Modifier.background(Color.White) else Modifier.border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(24.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 16.dp),
    )
}

/** Me > Share profile: switch the public page on or off and send its link. */
@Composable
fun ShareProfilePage() {
    val context = LocalContext.current
    val link = remember { PublicProfile.link() }
    var on by remember { mutableStateOf(PublicProfile.enabled(context)) }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            if (link == null) {
                Text("Log in to share your profile.", color = Dim, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
                return@Column
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        busy = true
                        val next = !on
                        PublicProfile.setEnabled(context, next) { ok ->
                            busy = false
                            if (ok) on = next else I18nToast.make(context, "Couldn't update. Check your connection.", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Public profile", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (on) "Anyone with your link can see it." else "Off. Nobody can open your profile.",
                            color = Dim, fontSize = 12.sp, lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    MiniSwitch(on) {}
                }
            }
            if (on) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
                    Text("Your link", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(link, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Copy", false, Modifier.weight(1f)) {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kiro profile", link))
                            I18nToast.make(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                        Pill("Share", true, Modifier.weight(1f)) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "My Kiro profile: $link")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Shows your name, picture, bio, level, streak, badges and your six highest rated shows. Your library and watch history stay private. It updates each time you open Kiro. The link opens in the Kiro app.",
                color = Color(0xFF6A6A70), fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
    }
}

/** Someone's public profile, opened from their link. */
@Composable
fun PublicProfileView(uid: String) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PublicProfile.View?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(uid) {
        PublicProfile.load(uid) { state = it; loading = false }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
        val v = state
        if (v == null) {
            Text(
                if (loading) "Loading..." else "This profile isn't available. It may be switched off.",
                color = Dim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 48.dp),
            )
            return@Box
        }
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            DecoratedAvatar(v.avatar, v.name, 88.dp, v.decoration.ifBlank { null })
            Spacer(Modifier.height(14.dp))
            Text(
                v.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = NameStyles.family(context, v.nameFont),
            )
            if (v.bio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(v.bio, color = Dim, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
                    Text("Level", color = Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${v.level}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Streak", color = Dim, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${v.streak} d", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    if (v.streak > 0) StreakBadge(v.streak, true, 44.dp)
                }
            }
            val badges = v.badges.mapNotNull { id -> Achievements.ALL.firstOrNull { it.id == id } }.sortedByDescending { it.tier }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("${badges.size} badges", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    badges.take(12).forEach { b ->
                        Text(
                            b.title, color = Color.White, fontSize = 12.sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Card).padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            if (v.favorites.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("Favourites", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                v.favorites.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { f ->
                            Column(Modifier.weight(1f).clickable { DeepLink.request.value = "d:${f.id}" }) {
                                AsyncImage(
                                    model = f.poster.ifEmpty { null }, contentDescription = f.title, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.705f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222226)),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(f.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
