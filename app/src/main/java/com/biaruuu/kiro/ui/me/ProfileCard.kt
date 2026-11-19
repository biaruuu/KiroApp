package com.biaruuu.kiro.ui.me

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Achievements
import com.biaruuu.kiro.data.CloudSync
import com.biaruuu.kiro.data.NameStyles
import com.biaruuu.kiro.data.Profile
import com.biaruuu.kiro.data.WatchStats
import com.biaruuu.kiro.data.WatchedStore
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.components.StreakBadge
import com.biaruuu.kiro.ui.components.StreakTier
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Dim = Color(0xFF9A9AA2)
private val Gold = Color(0xFFFFC83D)

/** The profile card at the top of Me: avatar and name, level, streak flame, Edit Profile, Share and badges. */
@Composable
fun ProfileCard(
    profile: Profile,
    name: String,
    onEdit: () -> Unit,
    onBadges: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val wv by WatchedStore.version.collectAsState()
    val sv by WatchStats.version.collectAsState()
    val cv by CloudSync.version.collectAsState()
    val streak = remember(wv, sv, cv) { WatchStats.streak(context) }
    val level = remember(wv, sv, cv, streak) { WatchStats.level(context, streak) }
    val badges = remember(wv, sv, cv) { Achievements.ALL.count { Achievements.isUnlocked(context, it.id) } }
    val tier = StreakTier.of(streak.count)
    val shape = RoundedCornerShape(16.dp)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val badgeSize = (maxWidth * 0.19f).coerceIn(52.dp, 72.dp)
        val nameSize = if (maxWidth < 330.dp) 16.sp else 20.sp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1F1A3D), Color(0xFF141318))))
                .border(1.5.dp, Brush.linearGradient(listOf(KiroPurple, Color(0xFFB7A9FF), KiroPurple)), shape),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DecoratedAvatar(profile.avatar, name.ifBlank { "K" }, 44.dp, profile.decoration)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name.uppercase(),
                            color = Color.White,
                            fontSize = nameSize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            fontFamily = NameStyles.family(context, profile.nameFont),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.fluent_star_filled), null, tint = Gold, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Lv ${level.level}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    val frac by animateFloatAsState((level.xpIntoLevel.toFloat() / level.xpForNext).coerceIn(0f, 1f), label = "xp")
                    Text("${level.xpIntoLevel} / ${level.xpForNext} XP", color = Dim, fontSize = 11.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(frac).height(3.dp).background(Brush.horizontalGradient(listOf(KiroPurple, Color(0xFFB7A9FF)))))
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.clickable(onClick = onStats)) {
                    StreakBadge(streak.count, streak.doneToday, badgeSize)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(R.drawable.fluent_edit_regular, "Edit Profile", onEdit)
                PillButton(R.drawable.fluent_open_regular, "Share") {
                    val text = "$name on Kiro · Level ${level.level} · ${streak.count}-day streak · ${WatchedStore.totalEpisodes(context)} episodes watched"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (streak.count > 0) "${streak.count}-day streak · ${tier.name}" else "No streak yet",
                    color = Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable(onClick = onStats),
                )
                Spacer(Modifier.width(8.dp))
                Text("$badges", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(" Badges", color = Dim, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onBadges)
                        .padding(start = 11.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("View All", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(painterResource(R.drawable.fluent_chevron_right_regular), null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
