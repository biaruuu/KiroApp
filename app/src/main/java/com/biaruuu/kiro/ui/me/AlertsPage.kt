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

private class AlertShow(val id: Int, val title: String)

private fun clock(min: Int): String =
    java.time.LocalTime.of(min / 60, min % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

/** Notification control: alerts on or off, quiet hours, which shows send alerts, and what airs this week. */
@Composable
fun AlertsPage() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AppSettings.episodeAlerts(context)) }
    var quiet by remember { mutableStateOf(AppSettings.quietHours(context)) }
    var qStart by remember { mutableStateOf(AppSettings.quietStart(context)) }
    var qEnd by remember { mutableStateOf(AppSettings.quietEnd(context)) }
    var muted by remember { mutableStateOf(AppSettings.mutedShows(context)) }
    val shows = remember {
        val seen = HashSet<Int>()
        val out = ArrayList<AlertShow>()
        MyListStore.all(context).forEach { if (seen.add(it.id)) out.add(AlertShow(it.id, it.title)) }
        WatchHistory.all(context).forEach { if (seen.add(it.animeId)) out.add(AlertShow(it.animeId, it.title)) }
        out
    }
    val upcoming by produceState<List<AniListExtra.Airing>?>(null) {
        val ids = shows.map { it.id }.toSet()
        val now = System.currentTimeMillis() / 1000
        value = withContext(Dispatchers.IO) {
            AniListExtra.schedule(now, now + 7 * 86_400L)?.filter { it.anime.id in ids && it.anime.id !in muted }
        } ?: emptyList()
    }
    fun pick(initial: Int, done: (Int) -> Unit) {
        android.app.TimePickerDialog(context, { _, h, m -> done(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = KiroPurple, checkedThumbColor = Color.White, checkedBorderColor = KiroPurple,
        uncheckedTrackColor = Color(0xFF3A3A3E), uncheckedThumbColor = Color(0xFFB4B4BA), uncheckedBorderColor = Color(0xFF3A3A3E),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("New episode alerts", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    modifier = Modifier.scale(0.92f), checked = on, colors = switchColors,
                    onCheckedChange = { on = it; AppSettings.setEpisodeAlerts(context, it) },
                )
            }
            Text(
                "Get a notification when an anime in your list or Continue Watching airs a new episode. Checked every 30 minutes.",
                color = Color(0xFF9A9A9F), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quiet hours", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    modifier = Modifier.scale(0.92f), checked = quiet, colors = switchColors,
                    onCheckedChange = { quiet = it; AppSettings.setQuietHours(context, it) },
                )
            }
            if (quiet) {
                com.biaruuu.kiro.ui.me.ChevronRow("From", clock(qStart)) {
                    pick(qStart) { qStart = it; AppSettings.setQuietRange(context, qStart, qEnd) }
                }
                com.biaruuu.kiro.ui.me.ChevronRow("Until", clock(qEnd)) {
                    pick(qEnd) { qEnd = it; AppSettings.setQuietRange(context, qStart, qEnd) }
                }
            }
            Text(
                if (quiet) "No alerts between ${clock(qStart)} and ${clock(qEnd)}. Episodes that air meanwhile are announced when quiet hours end."
                else "Silence alerts at night: choose a time range.",
                color = Color(0xFF9A9A9F), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text("Alerts per show", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
            if (shows.isEmpty()) {
                Text("Add shows to My List or start watching to choose alerts for them.", color = Color(0xFF9A9A9F), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        }
        items(shows, key = { "s${it.id}" }) { show ->
            Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(show.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Switch(
                    modifier = Modifier.scale(0.85f), checked = show.id !in muted, colors = switchColors,
                    onCheckedChange = { enabled ->
                        AppSettings.setShowMuted(context, show.id, !enabled)
                        muted = AppSettings.mutedShows(context)
                    },
                )
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text("Airing this week from your list", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            val list = upcoming
            if (list == null) {
                Text("Loading...", color = Color(0xFF9A9A9F), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else if (list.isEmpty()) {
                Text("Nothing from your list airs in the next 7 days.", color = Color(0xFF9A9A9F), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        }
        val list = upcoming.orEmpty()
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault())
        items(list, key = { "${it.anime.id}:${it.episode}" }) { a ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1B1F)).padding(12.dp),
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
