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

    val DOWNLOAD_QUALITIES = listOf("Auto", "1080p", "720p", "480p")
    fun downloadQuality(c: Context): String = prefs(c).getString("download_quality", "Auto") ?: "Auto"
    fun setDownloadQuality(c: Context, q: String) = prefs(c).edit().putString("download_quality", q).apply()

    /** "email" or "google": shown as the "Last login" bubble on the welcome screen. */
    fun lastLogin(c: Context): String? = prefs(c).getString("last_login", null)
    fun setLastLogin(c: Context, method: String) = prefs(c).edit().putString("last_login", method).apply()
}
