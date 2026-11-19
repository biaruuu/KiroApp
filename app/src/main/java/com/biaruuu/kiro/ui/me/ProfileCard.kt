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
        // Everything scales with the card width (a 328dp card is 1.0), so it keeps its proportions on any phone.
        val k = (maxWidth.value / 328f).coerceIn(0.8f, 1.25f)
        val badgeSize = (maxWidth * 0.17f).coerceIn(48.dp, 68.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1F1A3D), Color(0xFF141318))))
                .border(1.5.dp, Brush.linearGradient(listOf(KiroPurple, Color(0xFFB7A9FF), KiroPurple)), shape),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = (14 * k).dp, top = (10 * k).dp, end = (8 * k).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DecoratedAvatar(profile.avatar, name.ifBlank { "K" }, (38 * k).dp, profile.decoration)
                Spacer(Modifier.width((12 * k).dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name.uppercase(),
                            color = Color.White,
                            fontSize = (19 * k).sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp,
                            fontFamily = NameStyles.family(context, profile.nameFont),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width((6 * k).dp))
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = (6 * k).dp, vertical = (2 * k).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.fluent_star_filled), null, tint = Gold, modifier = Modifier.size((12 * k).dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Lv ${level.level}", color = Color.White, fontSize = (11 * k).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    val frac by animateFloatAsState((level.xpIntoLevel.toFloat() / level.xpForNext).coerceIn(0f, 1f), label = "xp")
                    Text("${level.xpIntoLevel} / ${level.xpForNext} XP", color = Dim, fontSize = (11 * k).sp, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = (14 * k).dp, vertical = (8 * k).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(R.drawable.fluent_edit_regular, "Edit Profile", k, onEdit)
                PillButton(R.drawable.fluent_open_regular, "Share", k) {
                    val text = "$name on Kiro · Level ${level.level} · ${streak.count}-day streak · ${WatchedStore.totalEpisodes(context)} episodes watched"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = (14 * k).dp, vertical = (6 * k).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (streak.count > 0) "${streak.count}-day streak · ${tier.name}" else "No streak yet",
                    color = Dim, fontSize = (11 * k).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable(onClick = onStats),
                )
                Spacer(Modifier.width(8.dp))
                Text("$badges", color = Color.White, fontSize = (12 * k).sp, fontWeight = FontWeight.Bold)
                Text(" Badges", color = Dim, fontSize = (12 * k).sp)
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onBadges)
                        .padding(start = (10 * k).dp, end = (4 * k).dp, top = (4 * k).dp, bottom = (4 * k).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("View All", color = Color.White, fontSize = (11 * k).sp, fontWeight = FontWeight.Bold)
                    Icon(painterResource(R.drawable.fluent_chevron_right_regular), null, tint = Color.White, modifier = Modifier.size((15 * k).dp))
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: Int, label: String, k: Float, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = (10 * k).dp, vertical = (4.5f * k).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size((14 * k).dp))
        Spacer(Modifier.width((5 * k).dp))
        Text(label, color = Color.White, fontSize = (11.5f * k).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
