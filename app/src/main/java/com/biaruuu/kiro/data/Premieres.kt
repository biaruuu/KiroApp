package com.biaruuu.kiro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Starts in 3 days" for shows marked Plan to watch that have not aired yet. Dates come from AniList
 * and are kept for six hours; on the premiere day an optional reminder is sent.
 */
object Premieres {
    private const val FILE = "kiro_user_data"
    private const val KEY = "premieres"
    private const val STAMP = "premieres_at"
    private const val NOTIFIED = "premiere_notified"

    /** Bumps whenever the saved dates change, so screens can redraw. */
    val version = MutableStateFlow(0)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(c: Context): JSONObject = try {
        JSONObject(prefs(c).getString(KEY, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    /** Days until the premiere (0 = today), or null when unknown or already out. */
    fun daysUntil(c: Context, id: Int): Int? {
        val day = read(c).optLong(id.toString(), Long.MIN_VALUE)
        if (day == Long.MIN_VALUE) return null
        val d = (day - LocalDate.now().toEpochDay()).toInt()
        return if (d < 0) null else d
    }

    fun label(c: Context, id: Int): String? {
        val d = daysUntil(c, id) ?: return null
        return when {
            d == 0 -> "Premieres today"
            d == 1 -> "Starts tomorrow"
            d <= 60 -> "Starts in $d days"
            else -> "Starts " + LocalDate.ofEpochDay(read(c).optLong(id.toString())).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        }
    }

    /** Fetches fresh dates for the Plan to watch list. Blocking: call off the main thread. */
    fun refresh(c: Context, force: Boolean = false) {
        val p = prefs(c)
        val ids = ShowStatus.entries(c, ShowStatus.PLAN).map { it.id }
        if (ids.isEmpty()) {
            if (read(c).length() > 0) { p.edit().putString(KEY, "{}").apply(); version.value++ }
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(STAMP, 0L) < 6 * 3600_000L) return
        val found = AniListExtra.premieres(ids) ?: return
        val o = JSONObject()
        found.forEach { (id, day) -> o.put(id.toString(), day) }
        p.edit().putString(KEY, o.toString()).putLong(STAMP, now).apply()
        version.value++
    }

    /** Sends "premieres today" once per show, from 8 am, when premiere reminders are on. */
    fun notifyToday(c: Context) {
        if (!AppSettings.premiereAlerts(c)) return
        if (java.time.LocalTime.now().hour < 8) return
        val p = prefs(c)
        val today = LocalDate.now().toEpochDay()
        val done = p.getStringSet(NOTIFIED, emptySet()).orEmpty().toMutableSet()
        val muted = AppSettings.mutedShows(c)
        var changed = false
        for (e in ShowStatus.entries(c, ShowStatus.PLAN)) {
            if (e.id in muted || daysUntil(c, e.id) != 0) continue
            if (!done.add("${e.id}:$today")) continue
            changed = true
            RetentionNotifier.post(c, 7100 + (e.id and 0xFFF), e.title, "Premieres today", e.id)
        }
        if (changed) p.edit().putStringSet(NOTIFIED, done.toList().takeLast(100).toSet()).apply()
    }
}
