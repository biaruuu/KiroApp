package com.biaruuu.kiro.data

import android.content.Context
import org.json.JSONArray

/** Anime you gave the gold award to (kept on this phone). */
object AwardStore {
    private const val PREFS = "kiro_user_data"
    private const val KEY = "awards"

    private fun read(c: Context): MutableSet<Int> = try {
        val arr = JSONArray(c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { arr.optInt(it) }.toMutableSet()
    } catch (_: Exception) {
        mutableSetOf()
    }

    fun has(c: Context, id: Int) = id in read(c)

    /** Flips the award; returns true when it is now given. */
    fun toggle(c: Context, id: Int): Boolean {
        val set = read(c)
        val now = if (id in set) { set.remove(id); false } else { set.add(id); true }
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, JSONArray(set.toList()).toString()).apply()
        return now
    }
}
