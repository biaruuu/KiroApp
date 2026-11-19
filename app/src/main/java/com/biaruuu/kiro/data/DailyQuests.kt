package com.biaruuu.kiro.data

import android.content.Context
import java.time.LocalDate

/**
 * Three small goals a day, worked out from the watch tally. Finishing one lets you claim its XP.
 * Only today's quests can be claimed, and a claim is only accepted while the goal is really met.
 */
object DailyQuests {
    class Quest(val id: Int, val title: String, val goal: Int, val unit: String, val xp: Int) {
        fun progress(d: WatchStats.Day?): Int =
            if (d == null) 0 else if (unit == "ep") d.episodes else d.seconds / 60
    }

    const val ALL_DONE_BONUS = 30

    fun forDay(day: Long): List<Quest> {
        val mins = listOf(20, 30, 45)[(day % 3).toInt()]
        val third = if (day % 2 == 0L) Quest(2, "Finish 3 episodes", 3, "ep", 50)
        else Quest(2, "Watch 60 minutes", 60, "min", 50)
        return listOf(
            Quest(0, "Watch an episode", 1, "ep", 20),
            Quest(1, "Watch $mins minutes", mins, "min", 30),
            third,
        )
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    fun isClaimed(c: Context, day: Long, id: Int): Boolean = (WatchStats.claimedMask(c, day) shr id) and 1 == 1

    /** Claims quest [id] of today. Returns the XP gained, or 0 if it is not finished or already claimed. */
    fun claim(c: Context, id: Int): Int {
        val day = today()
        val q = forDay(day).firstOrNull { it.id == id } ?: return 0
        if (isClaimed(c, day, id)) return 0
        if (q.progress(WatchStats.days(c)[day]) < q.goal) return 0
        val mask = WatchStats.claimedMask(c, day) or (1 shl id)
        WatchStats.setClaimedMask(c, day, mask)
        return q.xp + if (mask == 7) ALL_DONE_BONUS else 0
    }

    /** XP from every quest ever claimed, including the bonus for clearing all three in a day. */
    fun bonusXp(c: Context): Long {
        var xp = 0L
        WatchStats.claimedMasks(c).forEach { (day, mask) ->
            forDay(day).forEach { if ((mask shr it.id) and 1 == 1) xp += it.xp }
            if (mask == 7) xp += ALL_DONE_BONUS
        }
        return xp
    }
}
