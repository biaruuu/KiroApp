package com.biaruuu.kiro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Watch time per day (with the genres watched), the daily streak, and the XP and level worked out
 * from them and from the rest of the library. Everything is derived from saved data, so it syncs
 * with the account and a level never depends on a counter that could drift.
 */
object WatchStats {
    private const val PREFS = "kiro_user_data"
    private const val KEY = "stats"

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(c: Context): JSONObject = try {
        JSONObject(prefs(c).getString(KEY, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    /** Adds watched time (media seconds) to today, split over the show's genres. */
    @Synchronized
    fun addWatchTime(c: Context, seconds: Int, genres: List<String>) {
        if (seconds <= 0) return
        val all = read(c)
        val day = LocalDate.now().toEpochDay().toString()
        val o = all.optJSONObject(day) ?: JSONObject()
        o.put("s", o.optInt("s") + seconds)
        if (genres.isNotEmpty()) {
            val g = o.optJSONObject("g") ?: JSONObject()
            val each = (seconds / genres.size).coerceAtLeast(1)
            genres.forEach { name -> g.put(name, g.optInt(name) + each) }
            o.put("g", g)
        }
        all.put(day, o)
        prefs(c).edit().putString(KEY, all.toString()).apply()
        Achievements.recordWatch(c)
        CloudSync.libraryChanged(c)
        _version.value++
    }

    /** Counts a newly finished episode on today's tally. */
    @Synchronized
    fun episodeFinished(c: Context) {
        val all = read(c)
        val day = LocalDate.now().toEpochDay().toString()
        val o = all.optJSONObject(day) ?: JSONObject()
        o.put("e", o.optInt("e") + 1)
        all.put(day, o)
        prefs(c).edit().putString(KEY, all.toString()).apply()
        _version.value++
    }

    @JvmStatic
    fun notifyChanged() {
        _version.value++
    }

    class Day(val day: Long, val seconds: Int, val episodes: Int)

    fun days(c: Context): Map<Long, Day> {
        val all = read(c)
        val out = HashMap<Long, Day>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val d = k.toLongOrNull() ?: continue
            val o = all.optJSONObject(k) ?: continue
            out[d] = Day(d, o.optInt("s"), o.optInt("e"))
        }
        return out
    }

    /** Seconds per genre over all time, biggest first. */
    fun genres(c: Context): List<Pair<String, Int>> {
        val all = read(c)
        val sum = HashMap<String, Int>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val g = all.optJSONObject(keys.next())?.optJSONObject("g") ?: continue
            val names = g.keys()
            while (names.hasNext()) {
                val n = names.next()
                sum[n] = (sum[n] ?: 0) + g.optInt(n)
            }
        }
        return sum.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    // ---- streak ----

    /** A day counts when at least a minute was watched or an episode was finished (older days came from the achievements log). */
    private fun activeDays(c: Context): Set<Long> {
        val out = HashSet<Long>()
        days(c).values.filter { it.seconds >= 60 || it.episodes > 0 }.forEach { out.add(it.day) }
        try {
            val a = JSONArray(prefs(c).getString("ach_days", "[]"))
            for (i in 0 until a.length()) out.add(a.optLong(i))
        } catch (_: Exception) {
        }
        return out
    }

    class Streak(val count: Int, val doneToday: Boolean, val best: Int, val activeDays: Set<Long>)

    fun streak(c: Context): Streak {
        val active = activeDays(c)
        val today = LocalDate.now().toEpochDay()
        val doneToday = today in active
        var d = if (doneToday) today else today - 1
        var n = 0
        while (d in active) {
            n++
            d--
        }
        // Longest run ever.
        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (x in active.sorted()) {
            run = if (x == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = x
        }
        best = maxOf(best, n, prefs(c).getInt("ach_best_streak", 0))
        return Streak(n, doneToday, best, active)
    }

    // ---- XP and level ----

    class Level(val level: Int, val xpIntoLevel: Long, val xpForNext: Long, val totalXp: Long)

    /** XP needed to get from [level] to the next one. */
    fun xpToNext(level: Int): Long = 100L + 40L * level

    /** Total XP at which [level] is reached (level 1 starts at 0). */
    fun xpAt(level: Int): Long = 100L * (level - 1) + 20L * level * (level - 1)

    private val STREAK_BONUS = listOf(7 to 150L, 14 to 300L, 30 to 600L, 60 to 1200L, 100 to 2500L, 200 to 5000L, 365 to 10000L)

    /**
     * XP: 1 per minute watched (at most 240 a day), 15 per finished episode, 100 per completed series,
     * 20 per active day, 5 per rating, 2 per My List entry, streak milestones and badge rewards.
     */
    fun totalXp(c: Context, streak: Streak = streak(c)): Long {
        var xp = 0L
        days(c).values.forEach { xp += minOf(it.seconds / 60, 240) }
        xp += WatchedStore.totalEpisodes(c) * 15L
        xp += WatchedStore.completed(c).size * 100L
        xp += streak.activeDays.size * 20L
        xp += MyListStore.allRated(c).size * 5L
        xp += MyListStore.all(c).size * 2L
        STREAK_BONUS.forEach { (d, b) -> if (streak.best >= d) xp += b }
        Achievements.ALL.forEach { a ->
            if (Achievements.isUnlocked(c, a.id)) xp += when (a.tier) { 0 -> 50L; 1 -> 100L; else -> 200L }
        }
        return xp
    }

    fun level(c: Context, streak: Streak = streak(c)): Level {
        val xp = totalXp(c, streak)
        var l = 1
        while (xp >= xpAt(l + 1)) l++
        return Level(l, xp - xpAt(l), xpToNext(l), xp)
    }

    fun levelNow(c: Context): Int = level(c).level

    // ---- merging (cloud and backup) ----

    /** Per day the larger tally wins, so two phones that watched on the same day don't lose time. */
    internal fun merge(localJson: String?, remoteJson: String): String = try {
        val out = JSONObject(remoteJson)
        val local = JSONObject(localJson ?: "{}")
        val keys = local.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val l = local.optJSONObject(k) ?: continue
            val r = out.optJSONObject(k)
            if (r == null) {
                out.put(k, l)
                continue
            }
            r.put("s", maxOf(l.optInt("s"), r.optInt("s")))
            r.put("e", maxOf(l.optInt("e"), r.optInt("e")))
            val lg = l.optJSONObject("g")
            if (lg != null) {
                val rg = r.optJSONObject("g") ?: JSONObject().also { r.put("g", it) }
                val names = lg.keys()
                while (names.hasNext()) {
                    val n = names.next()
                    rg.put(n, maxOf(lg.optInt(n), rg.optInt(n)))
                }
            }
        }
        out.toString()
    } catch (_: Exception) {
        remoteJson
    }
}
