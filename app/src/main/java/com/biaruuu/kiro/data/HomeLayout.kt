package com.biaruuu.kiro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/**
 * Order and visibility of the Home rows (Me > Home layout). Rows are identified by their title;
 * "continue" is Continue Watching and "because" the Recommended for you rows. The hero stays on top.
 */
object HomeLayout {
    const val CONTINUE = "continue"
    const val BECAUSE = "because"

    /** Bumped on every change so Home redraws at once. */
    val version = MutableStateFlow(0)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("kiro_home_layout", Context.MODE_PRIVATE)

    private fun list(c: Context, key: String): List<String> = try {
        val a = JSONArray(prefs(c).getString(key, "[]"))
        List(a.length()) { a.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(c: Context, key: String, v: Collection<String>) {
        prefs(c).edit().putString(key, JSONArray(v.toList()).toString()).apply()
        version.value++
    }

    fun order(c: Context) = list(c, "order")
    fun hidden(c: Context): Set<String> = list(c, "hidden").toSet()
    /** Rows seen on Home the last time it loaded: what the editor lists. */
    fun known(c: Context) = list(c, "known")

    fun remember(c: Context, keys: List<String>) {
        if (keys != known(c)) prefs(c).edit().putString("known", JSONArray(keys).toString()).apply()
    }

    /** [keys] in the saved order (new rows keep their place at the end), without the hidden ones. */
    fun arrange(c: Context, keys: List<String>, includeHidden: Boolean = false): List<String> {
        val saved = order(c)
        val ordered = saved.filter { it in keys } + keys.filter { it !in saved }
        val hidden = hidden(c)
        return if (includeHidden) ordered else ordered.filter { it !in hidden }
    }

    fun move(c: Context, keys: List<String>, key: String, by: Int) {
        val cur = arrange(c, keys, includeHidden = true).toMutableList()
        val i = cur.indexOf(key)
        val j = i + by
        if (i < 0 || j < 0 || j >= cur.size) return
        cur.removeAt(i); cur.add(j, key)
        save(c, "order", cur)
    }

    fun setHidden(c: Context, key: String, hide: Boolean) {
        val h = hidden(c).toMutableSet()
        if (hide) h.add(key) else h.remove(key)
        save(c, "hidden", h)
    }

    fun reset(c: Context) {
        prefs(c).edit().remove("order").remove("hidden").apply()
        version.value++
    }

    fun label(key: String) = when (key) {
        CONTINUE -> "Continue Watching"
        BECAUSE -> "Recommended for you"
        else -> key
    }
}
