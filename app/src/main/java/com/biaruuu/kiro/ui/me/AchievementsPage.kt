package com.biaruuu.kiro.ui.me

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Achievements
import com.biaruuu.kiro.ui.theme.KiroAccent

private val Muted = com.biaruuu.kiro.ui.theme.KiroOnSurfaceMuted
private val Card = com.biaruuu.kiro.ui.theme.KiroSurface
private val Locked = Color(0xFF2E2E32)

private fun tierColor(tier: Int) = when (tier) {
    0 -> Color(0xFFCD8B4E)
    1 -> Color(0xFFC9CCD6)
    else -> Color(0xFFFFC83D)
}

private fun iconFor(m: Achievements.Metric) = when (m) {
    Achievements.Metric.EPISODES -> R.drawable.fluent_play_circle_regular
    Achievements.Metric.COMPLETED -> R.drawable.fluent_checkmark_regular
    Achievements.Metric.RATED -> R.drawable.fluent_star_filled
    Achievements.Metric.LIST -> R.drawable.fluent_library_regular
    Achievements.Metric.DOWNLOADS -> R.drawable.fluent_arrow_download_regular
    Achievements.Metric.STREAK, Achievements.Metric.NIGHT -> R.drawable.fluent_sparkle_regular
    Achievements.Metric.HOSTED, Achievements.Metric.JOINED -> R.drawable.fluent_people_regular
}

@Composable
fun AchievementsPage() {
    val context = LocalContext.current
    // Recalculated whenever the library changes.
    val watched by com.biaruuu.kiro.data.WatchedStore.version.collectAsState()
    val cloud by com.biaruuu.kiro.data.CloudSync.version.collectAsState()
    val downloads by com.biaruuu.kiro.data.Downloads.items.collectAsState()
    val rows = remember(watched, cloud, downloads) {
        Achievements.checkNew(context)
        Achievements.ALL.map { a ->
            val n = Achievements.count(context, a.metric)
            Triple(a, minOf(n, a.target), Achievements.isUnlocked(context, a.id))
        }
    }
    val done = rows.count { it.third }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.fluent_trophy_regular), null, tint = Color(0xFFFFC83D), modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$done of ${rows.size} unlocked", color = KiroText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Keep watching to earn them all", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Bar(done.toFloat() / rows.size, KiroAccent)
            }
        }
        items(rows, key = { it.first.id }) { (a, n, unlocked) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (unlocked) tierColor(a.tier).copy(alpha = 0.22f) else Locked),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(iconFor(a.metric)), null,
                        tint = if (unlocked) tierColor(a.tier) else Color(0xFF666670),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = if (unlocked) Color.White else Color(0xFFB4B4BA), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(a.desc, color = Muted, fontSize = 12.sp)
                    if (!unlocked) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Bar(n.toFloat() / a.target, tierColor(a.tier)) }
                            Spacer(Modifier.width(10.dp))
                            Text("$n/${a.target}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                if (unlocked) {
                    Icon(painterResource(R.drawable.fluent_checkmark_regular), null, tint = tierColor(a.tier), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Locked)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).background(color))
    }
}
