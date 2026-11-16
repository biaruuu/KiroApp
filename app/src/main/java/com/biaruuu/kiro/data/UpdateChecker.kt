package com.biaruuu.kiro.data

import android.content.Context
import com.biaruuu.kiro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Looks up the newest published version and its release notes, and remembers what was announced. */
object UpdateChecker {

    class Release(val version: String, val notes: String, val apkUrl: String)

    private const val API = "https://api.github.com/repos/biaruuu/KiroApp/releases"
    private const val PREFS = "kiro_update"

    /** "latest" (open the update page) or "current" (show what changed in this version); consumed by the UI. */
    val request = MutableStateFlow<String?>(null)

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    suspend fun latest(): Release? = fetch("$API/latest")

    suspend fun forVersion(version: String): Release? = fetch("$API/tags/v$version")

    private suspend fun fetch(url: String): Release? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
            HttpClientProvider.get().newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val json = JSONObject(r.body?.string().orEmpty())
                val version = json.optString("tag_name").removePrefix("v")
                if (version.isEmpty()) return@withContext null
                val apk = json.optJSONArray("assets")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk") }
                        ?.optString("browser_download_url").orEmpty()
                }.orEmpty()
                Release(version, json.optString("body"), apk)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** "1.15.0" vs "1.14.2": true when [latest] is newer than [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once per version: the first time a user opens the app after updating to it. */
    fun consumeJustUpdated(c: Context): Boolean {
        val p = prefs(c)
        val last = p.getString("last_seen_version", null)
        p.edit().putString("last_seen_version", currentVersion).apply()
        return last != null && isNewer(currentVersion, last)
    }

    /** Returns true if this version has not been announced with a notification yet (and marks it). */
    fun markNotified(c: Context, version: String): Boolean {
        val p = prefs(c)
        if (p.getString("notified_version", null) == version) return false
        p.edit().putString("notified_version", version).apply()
        return true
    }
}
