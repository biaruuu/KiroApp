package com.biaruuu.kiro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Badges earned by using Kiro. Progress is worked out from what is already on the phone (watched
 * episodes, completed series, ratings, My List, downloads) plus a daily watch streak and the watch
 * party counters, so nothing extra has to be synced.
 */
object Achievements {
    enum class Metric { EPISODES, COMPLETED, RATED, LIST, DOWNLOADS, STREAK, NIGHT, HOSTED, JOINED }

    /** [tier] 0 bronze, 1 silver, 2 gold. */
    class Def(val id: String, val title: String, val desc: String, val metric: Metric, val target: Int, val tier: Int)

    val ALL = listOf(
        Def("ep1", "First Steps", "Finish your first episode", Metric.EPISODES, 1, 0),
        Def("ep10", "Getting Hooked", "Finish 10 episodes", Metric.EPISODES, 10, 0),
        Def("ep50", "Binge Watcher", "Finish 50 episodes", Metric.EPISODES, 50, 1),
        Def("ep200", "Anime Addict", "Finish 200 episodes", Metric.EPISODES, 200, 2),
        Def("ep1000", "Living Legend", "Finish 1,000 episodes", Metric.EPISODES, 1000, 2),
        Def("done1", "Finisher", "Complete a whole series", Metric.COMPLETED, 1, 0),
        Def("done5", "Series Slayer", "Complete 5 series", Metric.COMPLETED, 5, 1),
        Def("done25", "Completionist", "Complete 25 series", Metric.COMPLETED, 25, 2),
        Def("rate5", "Critic", "Rate 5 anime", Metric.RATED, 5, 0),
        Def("rate25", "Taste Maker", "Rate 25 anime", Metric.RATED, 25, 1),
        Def("list10", "Curator", "Add 10 anime to My List", Metric.LIST, 10, 0),
        Def("list50", "Collector", "Add 50 anime to My List", Metric.LIST, 50, 1),
        Def("dl1", "Offline Mode", "Download an episode", Metric.DOWNLOADS, 1, 0),
        Def("dl10", "Prepared", "Keep 10 downloaded episodes", Metric.DOWNLOADS, 10, 1),
        Def("streak3", "On a Roll", "Watch 3 days in a row", Metric.STREAK, 3, 0),
        Def("streak7", "Weekly Ritual", "Watch 7 days in a row", Metric.STREAK, 7, 1),
        Def("streak30", "Unstoppable", "Watch 30 days in a row", Metric.STREAK, 30, 2),
        Def("night", "Night Owl", "Watch between midnight and 5 AM", Metric.NIGHT, 1, 1),
        Def("host1", "Party Starter", "Host a watch party", Metric.HOSTED, 1, 1),
        Def("join1", "Party Guest", "Join a friend's watch party", Metric.JOINED, 1, 1),
    )

    private const val PREFS = "kiro_user_data"
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var lastDay = -1L

    /** Call while the user watches; keeps the daily streak and the night owl flag. Cheap to call often. */
    fun recordWatch(c: Context) {
        val p = prefs(c)
        if (java.time.LocalTime.now().hour < 5 && !p.getBoolean("ach_night", false)) p.edit().putBoolean("ach_night", true).apply()
        val today = java.time.LocalDate.now().toEpochDay()
        if (today == lastDay) return
        lastDay = today
        val days = try {
            JSONArray(p.getString("ach_days", "[]")).let { a -> (0 until a.length()).map { a.optLong(it) }.toMutableSet() }
        } catch (_: Exception) {
            mutableSetOf()
        }
        if (!days.add(today)) return
        val kept = days.sorted().takeLast(400)
        var streak = 0
        var d = today
        while (d in kept) {
            streak++
            d--
        }
        val best = maxOf(streak, p.getInt("ach_best_streak", 0))
        p.edit().putString("ach_days", JSONArray(kept).toString()).putInt("ach_best_streak", best).apply()
    }

    /** Adds one to a counter such as "hosted" or "joined" (watch parties). */
    fun bump(c: Context, key: String) {
        val p = prefs(c)
        p.edit().putInt("ach_$key", p.getInt("ach_$key", 0) + 1).apply()
    }

    fun count(c: Context, m: Metric): Int = when (m) {
        Metric.EPISODES -> WatchedStore.totalEpisodes(c)
        Metric.COMPLETED -> WatchedStore.completed(c).size
        Metric.RATED -> MyListStore.allRated(c).size
        Metric.LIST -> MyListStore.all(c).size
        Metric.DOWNLOADS -> Downloads.items.value.count { it.completed }
        Metric.STREAK -> prefs(c).getInt("ach_best_streak", 0)
        Metric.NIGHT -> if (prefs(c).getBoolean("ach_night", false)) 1 else 0
        Metric.HOSTED -> prefs(c).getInt("ach_hosted", 0)
        Metric.JOINED -> prefs(c).getInt("ach_joined", 0)
    }

    private fun unlocked(c: Context): JSONObject = try {
        JSONObject(prefs(c).getString("ach_unlocked", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    fun isUnlocked(c: Context, id: String) = unlocked(c).has(id)

    /** Badges reached since the last call; they are remembered so each is announced once. */
    fun checkNew(c: Context): List<Def> {
        val have = unlocked(c)
        val fresh = ArrayList<Def>()
        val counts = HashMap<Metric, Int>()
        for (a in ALL) {
            if (have.has(a.id)) continue
            val n = counts.getOrPut(a.metric) { count(c, a.metric) }
            if (n >= a.target) {
                have.put(a.id, System.currentTimeMillis())
                fresh.add(a)
            }
        }
        if (fresh.isNotEmpty()) prefs(c).edit().putString("ach_unlocked", have.toString()).apply()
        return fresh
    }
}
