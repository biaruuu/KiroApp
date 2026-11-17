package com.biaruuu.kiro.ui.schedule

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.data.AniListExtra
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.MyListStore
import com.biaruuu.kiro.data.WatchHistory
import com.biaruuu.kiro.ui.components.KiroSpinner
import com.biaruuu.kiro.ui.components.UnderlineTabs
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val Hairline = Color(0xFF2B2B2F)
private val Muted = Color(0xFF9A9A9F)
private val Placeholder = Color(0xFF262628)

private sealed interface Loaded {
    object Loading : Loaded
    object Failed : Loaded
    class Ok(val all: List<AniListExtra.Airing>) : Loaded
}

/** The weekly airing schedule: one page per day, in the phone's own time zone. */
@Composable
fun ScheduleScreen(onAnimeClick: (Anime) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val days = remember(today) { (0 until 7).map { today.plusDays(it.toLong()) } }
    val labels = remember(days) {
        days.mapIndexed { i, d -> if (i == 0) "Today" else d.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())) }
    }
    val pager = rememberPagerState(pageCount = { days.size })
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }

    val state by produceState<Loaded>(Loaded.Loading, attempt) {
        value = Loaded.Loading
        val start = today.atStartOfDay(zone).toEpochSecond()
        val end = today.plusDays(7).atStartOfDay(zone).toEpochSecond()
        val list = withContext(Dispatchers.IO) { AniListExtra.schedule(start, end) }
        value = if (list == null) Loaded.Failed else Loaded.Ok(list)
    }
    val mine = remember(attempt, state) {
        (MyListStore.all(context).map { it.id } + WatchHistory.all(context).map { it.animeId }).toSet()
    }
    // Ticks once a minute so "in 2h 10m" and "Aired" stay right.
    val now by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis() / 1000
        }
    }

    Column(modifier = modifier.statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text("Schedule", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        UnderlineTabs(labels, pager) { scope.launch { pager.animateScrollToPage(it) } }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        when (val s = state) {
            Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KiroSpinner() }
            Loaded.Failed -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Couldn't load the schedule", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Check your connection and try again.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Retry", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(KiroPurple).clickable { attempt++ }
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                )
            }
            is Loaded.Ok -> HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val day = days[page]
                val from = day.atStartOfDay(zone).toEpochSecond()
                val to = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
                val dayItems = remember(s, page, onlyMine, mine) {
                    s.all.filter { it.airingAt in from until to && (!onlyMine || it.anime.id in mine) }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${dayItems.size} episodes", color = Muted, fontSize = 13.sp)
                        Text(
                            "My list only",
                            color = if (onlyMine) Color.White else Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (onlyMine) KiroPurple else Color(0xFF232326))
                                .clickable { onlyMine = !onlyMine }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    if (dayItems.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (onlyMine) "Nothing from your list airs on this day" else "No episodes scheduled",
                                color = Color(0xFF5E5E63), fontSize = 14.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(dayItems, key = { "${it.anime.id}:${it.episode}" }) { a ->
                                AiringRow(a, a.anime.id in mine, now, zone) { onAnimeClick(a.anime) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiringRow(a: AniListExtra.Airing, inList: Boolean, now: Long, zone: ZoneId, onClick: () -> Unit) {
    val time = remember(a.airingAt) {
        Instant.ofEpochSecond(a.airingAt).atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    val left = a.airingAt - now
    val status = when {
        left <= 0 -> "Aired"
        left < 3600 -> "in ${left / 60 + 1}m"
        left < 86_400 -> "in ${left / 3600}h ${left % 3600 / 60}m"
        else -> "in ${left / 86_400}d ${left % 86_400 / 3600}h"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(60.dp).height(86.dp).clip(RoundedCornerShape(4.dp)).background(Placeholder)) {
            AsyncImage(
                model = a.anime.posterUrl,
                contentDescription = a.anime.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.anime.displayTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text("Episode ${a.episode} · $time", color = Muted, fontSize = 12.sp)
            if (inList) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "In your list", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(KiroPurple).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            status, color = if (left <= 0) Muted else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF232326)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
