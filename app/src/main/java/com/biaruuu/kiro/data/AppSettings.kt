package com.biaruuu.kiro.data

import android.content.Context

/** Small on-device preferences (Me > Play / Download) plus the last sign-in method. */
object AppSettings {
    private const val FILE = "kiro_user_data"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoplayPreviews(c: Context): Boolean = prefs(c).getBoolean("autoplay_previews", true)
    fun setAutoplayPreviews(c: Context, on: Boolean) = prefs(c).edit().putBoolean("autoplay_previews", on).apply()

    fun downloadWifiOnly(c: Context): Boolean = prefs(c).getBoolean("download_wifi_only", true)
    fun setDownloadWifiOnly(c: Context, on: Boolean) = prefs(c).edit().putBoolean("download_wifi_only", on).apply()

    /** Skip intros, outros and recaps by itself (player menu). */
    fun autoSkip(c: Context): Boolean = prefs(c).getBoolean("auto_skip", false)
    fun setAutoSkip(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_skip", on).apply()

    val DOWNLOAD_QUALITIES = listOf("Auto", "1080p", "720p", "480p")
    fun downloadQuality(c: Context): String = prefs(c).getString("download_quality", "Auto") ?: "Auto"
    fun setDownloadQuality(c: Context, q: String) = prefs(c).edit().putString("download_quality", q).apply()

    /** "email" or "google": shown as the "Last login" bubble on the welcome screen. */
    fun lastLogin(c: Context): String? = prefs(c).getString("last_login", null)
    fun setLastLogin(c: Context, method: String) = prefs(c).edit().putString("last_login", method).apply()

    /** Most storage downloads may use, in GB; 0 = no limit. */
    val DOWNLOAD_LIMITS_GB = listOf(0, 2, 5, 10, 20, 50)
    fun downloadLimitGb(c: Context): Int = prefs(c).getInt("download_limit_gb", 0)
    fun setDownloadLimitGb(c: Context, gb: Int) = prefs(c).edit().putInt("download_limit_gb", gb).apply()
    fun downloadLimitBytes(c: Context): Long = downloadLimitGb(c) * 1024L * 1024L * 1024L

    /** Remembered player choices. */
    fun playSpeed(c: Context): Float = prefs(c).getFloat("play_speed", 1f)
    fun setPlaySpeed(c: Context, v: Float) = prefs(c).edit().putFloat("play_speed", v).apply()
    fun qualityPref(c: Context): Int = prefs(c).getInt("quality_pref", 0)
    fun setQualityPref(c: Context, v: Int) = prefs(c).edit().putInt("quality_pref", v).apply()
    /** "" = none chosen yet, "off" = no subtitles, otherwise a language code. */
    fun subtitlePref(c: Context): String = prefs(c).getString("sub_pref", "") ?: ""
    fun setSubtitlePref(c: Context, v: String) = prefs(c).edit().putString("sub_pref", v).apply()

    /** Notify when an anime in My List or Continue Watching airs a new episode. */
    fun episodeAlerts(c: Context): Boolean = prefs(c).getBoolean("episode_alerts", true)
    fun setEpisodeAlerts(c: Context, on: Boolean) = prefs(c).edit().putBoolean("episode_alerts", on).apply()
}
