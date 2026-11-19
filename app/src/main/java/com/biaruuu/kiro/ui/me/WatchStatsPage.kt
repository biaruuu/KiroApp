package com.biaruuu.kiro.ui.me

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
import com.biaruuu.kiro.ui.theme.KiroPurple
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Card = Color(0xFF1A1A1D)
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
        StreakCard(streak, weekStart, today)
        Spacer(Modifier.height(14.dp))
        LevelCard(level)
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
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp)) {
            Text("How you earn XP", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Every minute watched: 1 XP (up to 240 a day)",
                "Every finished episode: +15 XP",
                "Every completed series: +100 XP",
                "Every day you watch: +20 XP",
                "Ratings +5 XP, My List +2 XP each",
                "Streak milestones and badges give bonus XP",
            ).forEach { Text("• $it", color = Dim, fontSize = 13.sp, lineHeight = 20.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Higher levels unlock avatar decorations in Customize profile.", color = Dim, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StreakCard(streak: WatchStats.Streak, weekStart: LocalDate, today: LocalDate) {
    val tier = StreakTier.of(streak.count)
    val next = StreakTier.next(streak.count)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val badge = (maxWidth * 0.40f).coerceIn(96.dp, 150.dp)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(tier.bottom.copy(alpha = 0.30f), Card)))
                .border(1.dp, tier.top.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakBadge(streak.count, streak.doneToday, badge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tier.name.uppercase(), color = tier.top, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(
                        if (streak.count == 1) "1 day streak" else "${streak.count} day streak",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black,
                    )
                    Text(
                        when {
                            streak.count == 0 -> "Watch for a minute today to start a streak."
                            streak.doneToday -> "You're on fire. See you tomorrow!"
                            else -> "Watch today to keep your streak alive."
                        },
                        color = Dim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                    Text("Best: ${streak.best} days", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                val left = next.minDays - streak.count
                Text(
                    "$left more ${if (left == 1) "day" else "days"} to ${next.name}",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                val prevMin = tier.minDays
                val frac by animateFloatAsState(((streak.count - prevMin).toFloat() / (next.minDays - prevMin)).coerceIn(0f, 1f), label = "tier")
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).background(Brush.horizontalGradient(listOf(tier.top, tier.bottom))))
                }
            }
            Spacer(Modifier.height(14.dp))
            // This week, Monday to Sunday.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val d = weekStart.plusDays(i.toLong())
                    val active = d.toEpochDay() in streak.activeDays
                    val isToday = d == today
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = Dim, fontSize = 11.sp)
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (active) Brush.verticalGradient(listOf(tier.top, tier.bottom)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
                                .then(if (isToday) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Level ${level.level}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("${level.totalXp} XP", color = Dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        val frac by animateFloatAsState((level.xpIntoLevel.toFloat() / level.xpForNext).coerceIn(0f, 1f), label = "lvl")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).background(Brush.horizontalGradient(listOf(KiroPurple, Color(0xFFB7A9FF)))))
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
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Card).padding(14.dp)) {
        Text(label, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
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

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily watch time", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                                else if (i == shownIdx) Brush.verticalGradient(listOf(Color(0xFFB7A9FF), KiroPurple))
                                else Brush.verticalGradient(listOf(KiroPurple.copy(alpha = 0.7f), KiroPurple.copy(alpha = 0.45f))),
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
        label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) KiroPurple else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

private val GENRE_COLORS = listOf(Color(0xFF7B6CF6), Color(0xFFFF6FB0), Color(0xFF4DD0E1), Color(0xFFFFB74D), Color(0xFF81C784), Color(0xFFE57373))

@Composable
private fun GenresCard(genres: List<Pair<String, Int>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp)) {
        Text("Most-watched genres", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                Text(name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
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
