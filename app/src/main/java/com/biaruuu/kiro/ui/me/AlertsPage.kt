package com.biaruuu.kiro.ui.me

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.AniListExtra
import com.biaruuu.kiro.data.AppSettings
import com.biaruuu.kiro.data.MyListStore
import com.biaruuu.kiro.data.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardBg = com.biaruuu.kiro.ui.theme.KiroSurface
private val Line = com.biaruuu.kiro.ui.theme.KiroOutline
private val Muted = com.biaruuu.kiro.ui.theme.KiroOnSurfaceMuted

private class AlertShow(val id: Int, val title: String)

private fun clock(min: Int): String =
    java.time.LocalTime.of(min / 60, min % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

/** A small white switch (the app's purple is not used on this page). */
@Composable
private fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) Color.White else Color(0xFF3A3A3E), tween(160), label = "track")
    val thumb by animateColorAsState(if (checked) Color(0xFF111111) else com.biaruuu.kiro.ui.theme.KiroOnSurfaceMuted, tween(160), label = "thumb")
    val x by animateDpAsState(if (checked) 18.dp else 2.dp, tween(160), label = "x")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(16.dp).clip(CircleShape).background(thumb))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        MiniSwitch(checked, onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun TimeChip(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(com.biaruuu.kiro.ui.theme.KiroSurface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = KiroText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

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
            AniListExtra.schedule(now, now + 7 * 86_400L)?.filter { it.anime.id in ids }
        } ?: emptyList()
    }
    fun pick(initial: Int, done: (Int) -> Unit) {
        android.app.TimePickerDialog(context, { _, h, m -> done(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 560.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).animateContentSize()) {
                    SwitchRow(
                        "New episode alerts",
                        "A notification when a show in your list or Continue Watching airs a new episode. Checked every 30 minutes.",
                        on,
                    ) { on = it; AppSettings.setEpisodeAlerts(context, it) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    SwitchRow(
                        "Quiet hours",
                        if (quiet) "No alerts from ${clock(qStart)} to ${clock(qEnd)}. Episodes that air meanwhile are announced afterwards."
                        else "Stay silent at night.",
                        quiet,
                    ) { quiet = it; AppSettings.setQuietHours(context, it) }
                    if (quiet) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TimeChip("From", clock(qStart), Modifier.weight(1f)) {
                                pick(qStart) { qStart = it; AppSettings.setQuietRange(context, qStart, qEnd) }
                            }
                            TimeChip("Until", clock(qEnd), Modifier.weight(1f)) {
                                pick(qEnd) { qEnd = it; AppSettings.setQuietRange(context, qStart, qEnd) }
                            }
                        }
                    }
                }
                SectionTitle("Alerts per show")
                if (shows.isEmpty()) {
                    Text("Add shows to Favourites or start watching to choose alerts for them.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (shows.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg)) {
                        shows.forEachIndexed { i, show ->
                            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Line))
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    AppSettings.setShowMuted(context, show.id, show.id !in muted)
                                    muted = AppSettings.mutedShows(context)
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    show.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = if (show.id in muted) Muted else Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(14.dp))
                                MiniSwitch(show.id !in muted) { enabled ->
                                    AppSettings.setShowMuted(context, show.id, !enabled)
                                    muted = AppSettings.mutedShows(context)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("Airing this week from your list")
                val list = upcoming
                if (list == null) {
                    Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                } else if (list.none { it.anime.id !in muted }) {
                    Text("Nothing from your list airs in the next 7 days.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            val zone = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault())
            items(upcoming.orEmpty().filter { it.anime.id !in muted }, key = { "${it.anime.id}:${it.episode}" }) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.anime.displayTitle, color = KiroText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text("Episode ${a.episode}", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Instant.ofEpochSecond(a.airingAt).atZone(zone).format(fmt), color = KiroText, fontSize = 12.sp)
                }
            }
        }
    }
}
