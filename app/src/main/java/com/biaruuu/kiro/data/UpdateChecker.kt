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

    class Release(val version: String, val notes: String, val apkUrl: String, val published: String = "")

    // New builds are published to the public Kirodownload repo; the older KiroApp releases are still read so
    // nobody misses an update while the move happens.
    private val APIS = listOf(
        "https://api.github.com/repos/biaruuu/Kirodownload/releases",
        "https://api.github.com/repos/biaruuu/KiroApp/releases",
    )
    private const val PREFS = "kiro_update"

    /** "latest" (open the update page) or "current" (show what changed in this version); consumed by the UI. */
    val request = MutableStateFlow<String?>(null)

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    suspend fun latest(): Release? =
        APIS.mapNotNull { fetch("$it/latest") }.fold(null as Release?) { best, r -> if (best == null || isNewer(r.version, best.version)) r else best }

    /** Newest published versions first, for the Notifications page. */
    suspend fun recent(limit: Int = 12): List<Release> = withContext(Dispatchers.IO) {
        val all = ArrayList<Release>()
        for (api in APIS) {
            try {
                val req = Request.Builder().url("$api?per_page=$limit").header("Accept", "application/vnd.github+json").build()
                HttpClientProvider.get().newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val arr = org.json.JSONArray(r.body?.string().orEmpty())
                    (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it)) }
                        .filter { !it.version.contains('-') }
                        .forEach { all.add(it) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        all.distinctBy { it.version }
            .sortedWith { a, b -> if (a.version == b.version) 0 else if (isNewer(a.version, b.version)) -1 else 1 }
            .take(limit)
    }

    private fun parse(json: JSONObject): Release? {
        if (json.optBoolean("draft")) return null
        val version = json.optString("tag_name").removePrefix("v")
        if (version.isEmpty()) return null
        val apk = json.optJSONArray("assets")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url").orEmpty()
        }.orEmpty()
        return Release(version, json.optString("body"), apk, json.optString("published_at").take(10))
    }

    // The checks run every minute while the app is open. GitHub answers "304 Not Modified" to a
    // repeat request with the same ETag, and those answers don't count against its rate limit.
    private val etags = HashMap<String, String>()
    private val cached = HashMap<String, Release>()

    suspend fun forVersion(version: String): Release? {
        for (api in APIS) fetch("$api/tags/v$version")?.let { return it }
        return null
    }

    private suspend fun fetch(url: String): Release? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            synchronized(etags) { etags[url] }?.let { builder.header("If-None-Match", it) }
            HttpClientProvider.get().newCall(builder.build()).execute().use { r ->
                if (r.code == 304) return@withContext synchronized(etags) { cached[url] }
                if (!r.isSuccessful) return@withContext null
                val release = parse(JSONObject(r.body?.string().orEmpty())) ?: return@withContext null
                r.header("ETag")?.let { tag -> synchronized(etags) { etags[url] = tag; cached[url] = release } }
                release
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

    /** True while there is a published version newer than both the installed one and the last one the user looked at. */
    val unread = MutableStateFlow(false)
    private var latestVersion: String? = null

    /** The newest version this device last saw on the Notifications page (never older than the installed one). */
    fun seenVersion(c: Context): String {
        val seen = prefs(c).getString("notif_seen", null) ?: return currentVersion
        return if (isNewer(seen, currentVersion)) seen else currentVersion
    }

    fun onLatest(c: Context, release: Release) {
        latestVersion = release.version
        unread.value = isNewer(release.version, seenVersion(c))
    }

    fun markSeen(c: Context) {
        latestVersion?.let { prefs(c).edit().putString("notif_seen", it).apply() }
        unread.value = false
    }

    /** Returns true if this version has not been announced with a notification yet (and marks it). */
    fun markNotified(c: Context, version: String): Boolean {
        val p = prefs(c)
        if (p.getString("notified_version", null) == version) return false
        p.edit().putString("notified_version", version).apply()
        return true
    }
}
