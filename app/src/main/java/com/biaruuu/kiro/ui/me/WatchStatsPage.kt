package com.biaruuu.kiro.ui.me

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.CloudSync
import com.biaruuu.kiro.data.Designs
import com.biaruuu.kiro.data.WatchStats
import com.biaruuu.kiro.data.WatchedStore
import com.biaruuu.kiro.ui.components.StreakBadge
import com.biaruuu.kiro.ui.components.StreakTier
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.KiroAccent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Card = Color(0xFF18181B)
private val Dim = Color(0xFF9A9AA2)

internal fun durText(seconds: Int): String {
    val m = seconds / 60
    return when {
        m < 1 -> if (seconds > 0) "<1m" else "0m"
        m < 60 -> "${m}m"
        else -> "${m / 60}h ${m % 60}m"
    }
}

/** Me > Watch stats: streak, level, time watched, a daily chart and the genres you watch most. */
@Composable
fun WatchStatsPage() {
    val context = LocalContext.current
    val wv by WatchedStore.version.collectAsState()
    val sv by WatchStats.version.collectAsState()
    val cv by CloudSync.version.collectAsState()
    val days = remember(wv, sv, cv) { WatchStats.days(context) }
    val streak = remember(wv, sv, cv) { WatchStats.streak(context) }
    val level = remember(wv, sv, cv, streak) { WatchStats.level(context, streak) }
    val genres = remember(wv, sv, cv) { WatchStats.genres(context) }
    val totalEpisodes = remember(wv, sv, cv) { WatchedStore.totalEpisodes(context) }

    val today = remember { LocalDate.now() }
    val weekStart = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val monthStart = remember { today.withDayOfMonth(1) }
    fun secOf(d: LocalDate) = days[d.toEpochDay()]?.seconds ?: 0
    fun epsOf(d: LocalDate) = days[d.toEpochDay()]?.episodes ?: 0
    val weekSeconds = (0 until 7).sumOf { secOf(weekStart.plusDays(it.toLong())) }
    val monthSeconds = (0 until today.lengthOfMonth()).sumOf { secOf(monthStart.plusDays(it.toLong())) }
    val allSeconds = days.values.sumOf { it.seconds }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        QuestsCard(days, sv)
        Spacer(Modifier.height(14.dp))
        StreakCard(streak, today)
        Spacer(Modifier.height(14.dp))
        LevelCard(level)
        Spacer(Modifier.height(14.dp))
        RecapCard(days, sv)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("This week", durText(weekSeconds), Modifier.weight(1f))
            Tile("This month", durText(monthSeconds), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Total time", durText(allSeconds), Modifier.weight(1f))
            Tile("Episodes watched", "$totalEpisodes", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        ChartCard(today, weekStart, monthStart, ::secOf, ::epsOf)

        Spacer(Modifier.height(14.dp))
        GenresCard(genres)

        Spacer(Modifier.height(14.dp))
        val events = remember(wv, sv, cv) {
            val out = ArrayList<Event>()
            com.biaruuu.kiro.data.WatchHistory.all(context).forEach { out.add(Event("watched", it.title, it.image.orEmpty(), it.updatedAt, "Episode ${it.episode}", it.animeId)) }
            WatchedStore.completed(context).forEach { out.add(Event("completed", it.title, it.poster, it.at, "", it.id)) }
            com.biaruuu.kiro.data.MyListStore.all(context).forEach { out.add(Event("added", it.title, it.posterUrl.orEmpty(), it.addedAt, "", it.id)) }
            com.biaruuu.kiro.data.MyListStore.allRated(context).forEach {
                out.add(Event("rated", it.title, it.posterUrl.orEmpty(), it.addedAt, if (it.rating % 2 == 0) "${it.rating / 2}/5" else "${it.rating / 2}.5/5", it.id))
            }
            out.sortedByDescending { it.at }
        }
        RecentActivityCard(events)
        Spacer(Modifier.height(14.dp))
        LibraryCard()
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
            SectionTitle("How you earn XP")
            Spacer(Modifier.height(8.dp))
            listOf(
                "Every minute watched: 1 XP (up to 240 a day)",
                "Every finished episode: +15 XP",
                "Every completed series: +100 XP",
                "Every day you watch: +20 XP",
                "Ratings +5 XP, Favourites +2 XP each",
                "Daily quests, streak milestones and badges give bonus XP",
            ).forEach { Text("• $it", color = Dim, fontSize = 13.sp, lineHeight = 20.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Higher levels unlock avatar decorations in Customize profile.", color = Dim, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StreakCard(streak: WatchStats.Streak, today: LocalDate) {
    val tier = StreakTier.of(streak.count)
    val next = StreakTier.next(streak.count)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val badge = (maxWidth * 0.34f).coerceIn(84.dp, 130.dp)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(16.dp),
        ) {
            SectionTitle("Activity streak")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakBadge(streak.count, streak.doneToday, badge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tier.name, color = tier.top, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
                    Text(
                        if (streak.count == 1) "1 day" else "${streak.count} days",
                        color = KiroText, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        androidx.compose.material3.Icon(
                            painterResource(com.biaruuu.kiro.R.drawable.fluent_trophy_regular), null, tint = tier.top, modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Best: ${streak.best} days", color = tier.top, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        when {
                            streak.count == 0 -> "Watch for a minute today to start a streak."
                            streak.doneToday -> "You're on fire. See you tomorrow!"
                            else -> "Watch today to keep your streak alive."
                        },
                        color = Dim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // The last 28 days, one block each; today is the pink one at the end.
            Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 27 downTo 0) {
                    val d = today.minusDays(i.toLong())
                    val active = d.toEpochDay() in streak.activeDays
                    val isToday = i == 0
                    Box(
                        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    active -> Brush.verticalGradient(listOf(tier.top, tier.bottom))
                                    isToday -> Brush.verticalGradient(listOf(Color(0xFFFF4DA6), Color(0xFFFF4DA6)))
                                    else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.09f)))
                                },
                            ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("28 days ago", color = Dim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("Today", color = Dim, fontSize = 10.sp)
            }
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                val left = next.minDays - streak.count
                Text(
                    "$left more ${if (left == 1) "day" else "days"} to ${next.name}",
                    color = KiroText, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                val prevMin = tier.minDays
                val frac by animateFloatAsState(((streak.count - prevMin).toFloat() / (next.minDays - prevMin)).coerceIn(0f, 1f), label = "tier")
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).background(Brush.horizontalGradient(listOf(tier.top, tier.bottom))))
                }
            }
        }
    }
}

/** Heavy uppercase section heading, with an optional "View all" link on the right. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            com.biaruuu.kiro.ui.i18n.I18n.tr(text),
            color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (action != null) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(action, color = Dim, fontSize = 12.sp)
                androidx.compose.material3.Icon(painterResource(com.biaruuu.kiro.R.drawable.fluent_chevron_right_regular), null, tint = Dim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private class Event(val kind: String, val title: String, val poster: String, val at: Long, val detail: String, val id: Int)

private fun agoText(ms: Long): String {
    val d = ((System.currentTimeMillis() - ms) / 1000).coerceAtLeast(0)
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60}m ago"
        d < 86400 -> "${d / 3600}h ago"
        else -> "${d / 86400}d ago"
    }
}

@Composable
private fun RecentActivityCard(events: List<Event>) {
    var filter by remember { mutableStateOf("all") }
    val shown = events.filter { filter == "all" || it.kind == filter }.take(6)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
        SectionTitle("Recent activity", action = "View all") { com.biaruuu.kiro.data.DeepLink.request.value = "t:library" }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "watched" to "Watched", "completed" to "Finished", "added" to "Added", "rated" to "Rated").forEach { (key, label) ->
                val on = filter == key
                Text(
                    label, color = if (on) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .border(1.dp, if (on) Color.White else Color(0xFF3A3A3F), RoundedCornerShape(16.dp))
                        .clickable { filter = key }.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (shown.isEmpty()) {
            Text("Nothing here yet. Watch, rate or add a show.", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        shown.forEach { e ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))
                    .clickable { com.biaruuu.kiro.data.DeepLink.request.value = "d:${e.id}" }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = e.poster, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2A2A2E)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = KiroText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (e.kind) {
                            "watched" -> "Watched"
                            "completed" -> "Finished"
                            "added" -> "Added to Favourites"
                            else -> "Rated"
                        } + if (e.detail.isNotEmpty()) " · ${e.detail}" else "",
                        color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(agoText(e.at), color = Dim, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private class LibData(
    val list: List<com.biaruuu.kiro.data.MyListStore.Entry>,
    val done: List<WatchedStore.Done>,
    val rated: Int,
    val statuses: List<Int>,
)

@Composable
private fun LibraryCard() {
    val context = LocalContext.current
    val wv by WatchedStore.version.collectAsState()
    val cv by CloudSync.version.collectAsState()
    val data = remember(wv, cv) {
        LibData(
            com.biaruuu.kiro.data.MyListStore.all(context),
            WatchedStore.completed(context),
            com.biaruuu.kiro.data.MyListStore.allRated(context).size,
            com.biaruuu.kiro.data.ShowStatus.ALL.map { com.biaruuu.kiro.data.ShowStatus.entries(context, it).size },
        )
    }
    val downloads by com.biaruuu.kiro.data.Downloads.items.collectAsState()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
        SectionTitle("Your library", action = "View all") { com.biaruuu.kiro.data.DeepLink.request.value = "t:library" }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Count("Favourites", data.list.size, Modifier.weight(1f))
            Count("Completed", data.done.size, Modifier.weight(1f))
            Count("Rated", data.rated, Modifier.weight(1f))
            Count("Downloads", downloads.count { it.completed }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Watching", "Plan", "On hold", "Dropped").forEachIndexed { i, label ->
                Count(label, data.statuses[i], Modifier.weight(1f))
            }
        }
        if (data.done.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Completed", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            PosterStrip(data.done.take(14).map { it.id to it.poster })
        }
        if (data.list.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Favourites", color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            PosterStrip(data.list.take(14).map { it.id to it.posterUrl })
        }
        if (data.done.isEmpty() && data.list.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Add shows to Favourites or finish a series and they show up here.", color = Dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Count(label: String, n: Int, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = Dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterStrip(items: List<Pair<Int, String?>>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth * 0.26f).coerceIn(76.dp, 104.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (id, poster) ->
                AsyncImage(
                    model = poster, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = w, height = w * 1.45f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2E))
                        .clickable { com.biaruuu.kiro.data.DeepLink.request.value = "d:$id" },
                )
            }
        }
    }
}

@Composable
private fun LevelCard(level: WatchStats.Level) {
    val context = LocalContext.current
    val nextDeco = remember(level.level) {
        Designs.all(Designs.DECORATION).map { Designs.requiredLevel(it) }.filter { it > level.level }.minOrNull()
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Level ${level.level}", color = KiroText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${level.totalXp} XP", color = Dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        val frac by animateFloatAsState((level.xpIntoLevel.toFloat() / level.xpForNext).coerceIn(0f, 1f), label = "lvl")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFD0D0D6)))))
        }
        Spacer(Modifier.height(8.dp))
        Text("${level.xpForNext - level.xpIntoLevel} XP to level ${level.level + 1}", color = Dim, fontSize = 13.sp)
        if (nextDeco != null) {
            Text("Next avatar decoration unlocks at level $nextDeco", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
        Text(label, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(value, color = KiroText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ChartCard(
    today: LocalDate,
    weekStart: LocalDate,
    monthStart: LocalDate,
    secOf: (LocalDate) -> Int,
    epsOf: (LocalDate) -> Int,
) {
    var month by remember { mutableStateOf(false) }
    val dates = remember(month) {
        if (month) (0 until today.lengthOfMonth()).map { monthStart.plusDays(it.toLong()) }
        else (0 until 7).map { weekStart.plusDays(it.toLong()) }
    }
    var picked by remember(month) { mutableIntStateOf(-1) }
    val values = dates.map { secOf(it) }
    val maxSec = maxOf(values.maxOrNull() ?: 0, 3600)
    val shownIdx = if (picked >= 0) picked else dates.indexOf(today).coerceAtLeast(0)
    val shown = dates[shownIdx]

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Daily watch time", Modifier.weight(1f))
            Seg("Week", !month) { month = false }
            Spacer(Modifier.width(6.dp))
            Seg("Month", month) { month = true }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${shown.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, ${shown.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${shown.dayOfMonth} · ${durText(secOf(shown))} · ${epsOf(shown)} ${if (epsOf(shown) == 1) "episode" else "episodes"}",
            color = Dim, fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        val chartH: Dp = 130.dp
        Row(Modifier.fillMaxWidth().height(chartH), horizontalArrangement = Arrangement.spacedBy(if (month) 2.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
            dates.forEachIndexed { i, d ->
                val target = if (d.isAfter(today)) 0f else values[i].toFloat() / maxSec
                val h by animateFloatAsState(target, label = "bar$i")
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                    ) { picked = i },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(maxOf(chartH * h, if (values[i] > 0) 4.dp else 2.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (values[i] == 0) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
                                else if (i == shownIdx) Brush.verticalGradient(listOf(Color.White, Color.White))
                                else Brush.verticalGradient(listOf(KiroAccent.copy(alpha = 0.7f), KiroAccent.copy(alpha = 0.45f))),
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (month) 2.dp else 8.dp)) {
            dates.forEachIndexed { i, d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val label = if (!month) d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    else if (i % 7 == 0) "${d.dayOfMonth}" else ""
                    Text(label, color = if (d == today) Color.White else Dim, fontSize = 10.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                }
            }
        }
        if (values.all { it == 0 }) {
            Spacer(Modifier.height(10.dp))
            Text("Watch something and your days fill in here.", color = Dim, fontSize = 12.sp)
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Tallest bar: ${durText(values.max())}", color = Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Seg(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (on) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) KiroAccent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

private val GENRE_COLORS = listOf(Color(0xFFFFFFFF), Color(0xFFD0D0D6), Color(0xFFA6A6AE), Color(0xFF85858D), Color(0xFF65656C), Color(0xFF4A4A50))

@Composable
private fun GenresCard(genres: List<Pair<String, Int>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
        SectionTitle("Most-watched genres")
        Spacer(Modifier.height(10.dp))
        if (genres.isEmpty()) {
            Text("Genres appear here as you watch.", color = Dim, fontSize = 13.sp)
            return@Column
        }
        val top = genres.take(6)
        val total = genres.sumOf { it.second }.coerceAtLeast(1)
        val max = top.first().second.coerceAtLeast(1)
        top.forEachIndexed { i, (name, sec) ->
            val frac by animateFloatAsState(sec.toFloat() / max, label = "g$i")
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = KiroText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(frac).height(10.dp).background(GENRE_COLORS[i % GENRE_COLORS.size]))
                }
                Text("${sec * 100 / total}%", color = Dim, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Counted from the time you watch, starting with this version.", color = Dim, fontSize = 11.sp)
    }
}
