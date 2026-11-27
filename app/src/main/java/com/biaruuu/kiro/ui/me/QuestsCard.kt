package com.biaruuu.kiro.ui.me

import com.biaruuu.kiro.ui.theme.KiroText
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.DailyQuests
import com.biaruuu.kiro.data.WatchStats
import com.biaruuu.kiro.ui.i18n.I18nToast
import com.biaruuu.kiro.ui.i18n.Text
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val CardBg = Color(0xFF18181B)
private val Dim = Color(0xFF9A9AA2)
private val Track = Color(0xFF2A2A2E)

/** Three goals for today. Tap Claim on a finished one to collect its XP. */
@Composable
fun QuestsCard(days: Map<Long, WatchStats.Day>, version: Int) {
    val context = LocalContext.current
    val day = DailyQuests.today()
    val quests = DailyQuests.forDay(day)
    @Suppress("UNUSED_VARIABLE") val refresh = version
    val done = quests.count { DailyQuests.isClaimed(context, day, it.id) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily quests", color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$done/3", color = Dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("Resets every midnight", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        quests.forEachIndexed { i, q ->
            val claimed = DailyQuests.isClaimed(context, day, q.id)
            val have = q.progress(days[day])
            val ready = !claimed && have >= q.goal
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .then(if (claimed) Modifier.background(Color.White) else Modifier.border(1.5.dp, if (ready) Color.White else Color(0xFF3A3A3F), CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (claimed) Icon(painterResource(R.drawable.fluent_checkmark_regular), null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.title, color = if (claimed) Dim else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    val frac = (have.toFloat() / q.goal).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Track)) {
                        Box(Modifier.fillMaxWidth(frac).height(3.dp).background(Color.White))
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (ready) {
                    Text(
                        "Claim +${q.xp}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White)
                            .clickable {
                                val gained = DailyQuests.claim(context, q.id)
                                if (gained > 0) I18nToast.make(context, "+$gained XP", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                } else {
                    Text(if (claimed) "Done" else "${minOf(have, q.goal)}/${q.goal} ${q.unit}", color = Dim, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (i < quests.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222226)))
        }
        Spacer(Modifier.height(8.dp))
        Text("Clear all three for +${DailyQuests.ALL_DONE_BONUS} bonus XP", color = Dim, fontSize = 12.sp)
    }
}

/** This week against last week, with a share button. */
@Composable
fun RecapCard(days: Map<Long, WatchStats.Day>, version: Int) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = version
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun sec(d: LocalDate) = days[d.toEpochDay()]?.seconds ?: 0
    fun eps(d: LocalDate) = days[d.toEpochDay()]?.episodes ?: 0
    val week = (0 until 7).map { start.plusDays(it.toLong()) }
    val prev = week.map { it.minusDays(7) }
    val seconds = week.sumOf { sec(it) }
    val lastSeconds = prev.sumOf { sec(it) }
    val episodes = week.sumOf { eps(it) }
    val active = week.count { sec(it) >= 60 || eps(it) > 0 }
    val top = WatchStats.genresIn(context, start.toEpochDay(), start.plusDays(6).toEpochDay()).firstOrNull()?.first
    val bestDay = week.maxByOrNull { sec(it) }?.takeIf { sec(it) > 0 }
    val delta = when {
        lastSeconds <= 0 && seconds > 0 -> "First week on the board"
        lastSeconds <= 0 -> "No watching yet this week"
        else -> {
            val p = ((seconds - lastSeconds) * 100f / lastSeconds).toInt()
            (if (p >= 0) "+$p%" else "$p%") + " vs last week"
        }
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly recap", color = KiroText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(20.dp))
                    .clickable {
                        val text = "My week on Kiro: ${durText(seconds)} watched · $episodes episodes · $active/7 active days" +
                            (top?.let { " · mostly $it" } ?: "")
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.fluent_open_regular), null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", color = KiroText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(durText(seconds), color = KiroText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(delta, color = Dim, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecapStat("Episodes", "$episodes", Modifier.weight(1f))
            RecapStat("Active days", "$active/7", Modifier.weight(1f))
            RecapStat("Top genre", top ?: "-", Modifier.weight(1.3f))
        }
        if (bestDay != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Best day: ${bestDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} · ${durText(sec(bestDay))}",
                color = Dim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RecapStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF111114)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, color = KiroText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
