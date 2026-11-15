package com.biaruuu.kiro.data

import android.content.Context
import org.json.JSONArray

/** The "Recent Searches" list: newest first, no repeats, at most [MAX] entries. */
object SearchHistory {
    private const val KEY = "recent_searches"
    private const val MAX = 10

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("kiro_user_data", Context.MODE_PRIVATE)

    fun all(c: Context): List<String> {
        val raw = prefs(c).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(c: Context, query: String): List<String> {
        val q = query.trim()
        if (q.length < 2) return all(c)
        val next = (listOf(q) + all(c).filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        save(c, next)
        return next
    }

    fun remove(c: Context, query: String): List<String> {
        val next = all(c).filterNot { it == query }
        save(c, next)
        return next
    }

    fun clear(c: Context): List<String> {
        save(c, emptyList())
        return emptyList()
    }

    private fun save(c: Context, list: List<String>) {
        prefs(c).edit().putString(KEY, JSONArray(list).toString()).apply()
    }
}
