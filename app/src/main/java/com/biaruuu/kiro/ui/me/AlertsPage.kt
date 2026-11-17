package com.biaruuu.kiro.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.AniListExtra
import com.biaruuu.kiro.data.AppSettings
import com.biaruuu.kiro.data.MyListStore
import com.biaruuu.kiro.data.WatchHistory
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Turn new episode notifications on or off, and see what from your list airs this week. */
@Composable
fun AlertsPage() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AppSettings.episodeAlerts(context)) }
    val upcoming by produceState<List<AniListExtra.Airing>?>(null) {
        val ids = (MyListStore.all(context).map { it.id } + WatchHistory.all(context).map { it.animeId }).toSet()
        val now = System.currentTimeMillis() / 1000
        value = withContext(Dispatchers.IO) {
            AniListExtra.schedule(now, now + 7 * 86_400L)?.filter { it.anime.id in ids }
        } ?: emptyList()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("New episode alerts", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                modifier = Modifier.scale(0.92f),
                checked = on,
                onCheckedChange = { on = it; AppSettings.setEpisodeAlerts(context, it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = KiroPurple, checkedThumbColor = Color.White, checkedBorderColor = KiroPurple,
                    uncheckedTrackColor = Color(0xFF3A3A3E), uncheckedThumbColor = Color(0xFFB4B4BA), uncheckedBorderColor = Color(0xFF3A3A3E),
                ),
            )
        }
        Text(
            "Get a notification when an anime in your list or Continue Watching airs a new episode. Checked every 30 minutes.",
            color = Color(0xFF9A9A9F), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text("Airing this week from your list", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        val list = upcoming
        if (list == null) {
            Text("Loading...", color = Color(0xFF9A9A9F), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        } else if (list.isEmpty()) {
            Text("Nothing from your list airs in the next 7 days.", color = Color(0xFF9A9A9F), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        } else {
            val zone = remember { ZoneId.systemDefault() }
            val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault()) }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { "${it.anime.id}:${it.episode}" }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1B1F)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.anime.displayTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Episode ${a.episode}", color = Color(0xFF9A9A9F), fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(Instant.ofEpochSecond(a.airingAt).atZone(zone).format(fmt), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
