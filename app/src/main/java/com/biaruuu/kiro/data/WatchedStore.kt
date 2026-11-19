package com.biaruuu.kiro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which episodes the user has finished, per anime. An anime counts as Completed once every
 * episode (1..total) is watched. Saved with the account like the rest of the library.
 */
object WatchedStore {

    private const val PREFS = "kiro_user_data"
    private const val KEY = "watched"

    private val _version = MutableStateFlow(0)

    /** Bumps on every change, from this phone or from the cloud. */
    val version: StateFlow<Int> = _version.asStateFlow()

    class Done(val id: Int, val title: String, val poster: String, val at: Long)

    @JvmStatic
    fun notifyChanged() {
        _version.value++
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(c: Context): JSONObject = try {
        JSONObject(prefs(c).getString(KEY, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun episodes(o: JSONObject): Set<Int> {
        val arr = o.optJSONArray("eps") ?: return emptySet()
        return (0 until arr.length()).map { arr.optInt(it) }.toSet()
    }

    private fun isDone(o: JSONObject): Boolean {
        val total = o.optInt("total")
        if (total <= 0) return false
        val eps = episodes(o)
        return (1..total).all { it in eps }
    }

    /** [total] is the number of episodes the anime has, or 0 when that isn't known yet. */
    fun markWatched(c: Context, id: Int, title: String, poster: String, episode: Int, total: Int) {
        if (!CloudSync.allowed(c)) return
        val all = read(c)
        val o = all.optJSONObject(id.toString()) ?: JSONObject()
        val eps = episodes(o).toMutableSet()
        val before = eps.size
        eps.add(episode)
        if (eps.size == before && o.optInt("total") == total) return
        o.put("t", title)
        o.put("p", poster)
        if (total > 0) o.put("total", total)
        o.put("at", System.currentTimeMillis())
        o.put("eps", JSONArray(eps.sorted()))
        all.put(id.toString(), o)
        prefs(c).edit().putString(KEY, all.toString()).apply()
        if (eps.size > before) WatchStats.episodeFinished(c)
        CloudSync.libraryChanged(c)
        notifyChanged()
    }

    fun watchedEpisodes(c: Context, id: Int): Set<Int> =
        read(c).optJSONObject(id.toString())?.let { episodes(it) } ?: emptySet()

    /** Every episode ever finished, over all anime. */
    fun totalEpisodes(c: Context): Int {
        val all = read(c)
        var n = 0
        val keys = all.keys()
        while (keys.hasNext()) n += all.optJSONObject(keys.next())?.let { episodes(it).size } ?: 0
        return n
    }

    fun isCompleted(c: Context, id: Int): Boolean =
        read(c).optJSONObject(id.toString())?.let { isDone(it) } ?: false

    /** Finished anime, most recently finished first. */
    fun completed(c: Context): List<Done> {
        val all = read(c)
        val out = ArrayList<Done>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = all.optJSONObject(k) ?: continue
            val id = k.toIntOrNull() ?: continue
            if (isDone(o)) out.add(Done(id, o.optString("t"), o.optString("p"), o.optLong("at")))
        }
        return out.sortedByDescending { it.at }
    }

    /** Forgets every watched episode of this anime. */
    fun clear(c: Context, id: Int) {
        val all = read(c)
        all.remove(id.toString())
        prefs(c).edit().putString(KEY, all.toString()).apply()
        CloudSync.libraryChanged(c)
        notifyChanged()
    }
}
