package com.biaruuu.kiro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Export and import of the user's library (My List, ratings, progress, statuses) as one JSON file. */
object Backup {
    private const val FORMAT = 1

    class Data(
        val myList: String,
        val ratings: String,
        val ratedMeta: String,
        val history: String,
        val watched: String,
        val status: String,
        val stats: String = "{}",
    ) {
        val listCount get() = count(myList)
        val ratedCount get() = count(ratings)
        val watchedCount get() = count(watched)
        val statusCount get() = count(status)
        val isEmpty get() = listCount + ratedCount + watchedCount + statusCount + count(history) == 0

        private fun count(json: String): Int = try {
            if (json.trimStart().startsWith("[")) JSONArray(json).length() else JSONObject(json).length()
        } catch (_: Exception) {
            0
        }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("kiro_user_data", Context.MODE_PRIVATE)

    /** What is on this phone right now. */
    fun current(c: Context): Data {
        val p = prefs(c)
        return Data(
            p.getString("my_list", "{}") ?: "{}", p.getString("ratings", "{}") ?: "{}",
            p.getString("rated_meta", "{}") ?: "{}", p.getString("watch_history", "[]") ?: "[]",
            p.getString("watched", "{}") ?: "{}", p.getString("status", "{}") ?: "{}",
            p.getString("stats", "{}") ?: "{}",
        )
    }

    fun export(c: Context): String {
        val d = current(c)
        return JSONObject()
            .put("app", "kiro")
            .put("format", FORMAT)
            .put("created", System.currentTimeMillis())
            .put("myList", JSONObject(d.myList))
            .put("ratings", JSONObject(d.ratings))
            .put("ratedMeta", JSONObject(d.ratedMeta))
            .put("history", JSONArray(d.history))
            .put("watched", JSONObject(d.watched))
            .put("status", JSONObject(d.status))
            .put("stats", JSONObject(d.stats))
            .toString(2)
    }

    /** Reads a backup file's text; null when it is not a Kiro backup. */
    fun parse(text: String): Data? = try {
        val o = JSONObject(text)
        if (o.optString("app") != "kiro" || o.optInt("format", 0) !in 1..FORMAT) {
            null
        } else {
            Data(
                (o.optJSONObject("myList") ?: JSONObject()).toString(),
                (o.optJSONObject("ratings") ?: JSONObject()).toString(),
                (o.optJSONObject("ratedMeta") ?: JSONObject()).toString(),
                (o.optJSONArray("history") ?: JSONArray()).toString(),
                (o.optJSONObject("watched") ?: JSONObject()).toString(),
                (o.optJSONObject("status") ?: JSONObject()).toString(),
                (o.optJSONObject("stats") ?: JSONObject()).toString(),
            )
        }
    } catch (_: Exception) {
        null
    }
}
