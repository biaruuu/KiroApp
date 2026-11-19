package com.biaruuu.kiro.data

import android.content.Context
import org.json.JSONObject

/** Where a show stands for the user: Watching, Plan to watch, On hold or Dropped. */
object ShowStatus {
    const val WATCHING = "watching"
    const val PLAN = "plan"
    const val HOLD = "hold"
    const val DROPPED = "dropped"

    val ALL = listOf(WATCHING, PLAN, HOLD, DROPPED)

    fun label(s: String): String = when (s) {
        WATCHING -> "Watching"
        PLAN -> "Plan to watch"
        HOLD -> "On hold"
        DROPPED -> "Dropped"
        else -> "Status"
    }

    private const val FILE = "kiro_user_data"
    private const val KEY = "status"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(c: Context): JSONObject = try {
        JSONObject(prefs(c).getString(KEY, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    /** The saved status of a show, or null when it has none. */
    fun get(c: Context, id: Int): String? = read(c).optJSONObject(id.toString())?.optString("s")?.takeIf { it in ALL }

    /** Sets (or with null clears) the status. A show with a status also joins My List. */
    fun set(c: Context, anime: Anime, status: String?) {
        if (!CloudSync.allowed(c)) return
        val all = read(c)
        val key = anime.id.toString()
        if (status == null) {
            all.remove(key)
        } else {
            all.put(key, JSONObject().apply {
                put("s", status)
                put("at", System.currentTimeMillis())
                put("title", anime.displayTitle)
                put("poster", anime.posterUrl ?: "")
                put("score", anime.averageScore)
            })
            if (!MyListStore.isInList(c, anime.id)) MyListStore.toggle(c, anime)
        }
        prefs(c).edit().putString(KEY, all.toString()).apply()
        CloudSync.libraryChanged(c)
    }

    /** Shows with [status], most recently set first. */
    fun entries(c: Context, status: String): List<MyListStore.Entry> {
        val all = read(c)
        val out = ArrayList<Pair<Long, MyListStore.Entry>>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = all.optJSONObject(k) ?: continue
            if (o.optString("s") != status) continue
            val id = k.toIntOrNull() ?: continue
            val at = o.optLong("at")
            out.add(at to MyListStore.Entry(id, o.optString("title"), o.optString("poster"), at, o.optInt("score"), MyListStore.getRating(c, id)))
        }
        return out.sortedByDescending { it.first }.map { it.second }
    }

    fun clear(c: Context, id: Int) {
        val all = read(c)
        if (all.remove(id.toString()) != null) {
            prefs(c).edit().putString(KEY, all.toString()).apply()
            CloudSync.libraryChanged(c)
        }
    }
}
