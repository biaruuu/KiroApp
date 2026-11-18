package com.biaruuu.kiro.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.biaruuu.kiro.MainActivity
import com.biaruuu.kiro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Tells the user when an episode of an anime in My List or Continue Watching has just aired.
 * Runs every 30 minutes in the background; the first run only starts the clock.
 */
class EpisodeAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        com.biaruuu.kiro.ui.i18n.I18n.ensure(applicationContext)
        val c = applicationContext
        if (!AppSettings.episodeAlerts(c)) return Result.success()
        // Quiet hours: wait. "last" stays put, so what aired meanwhile is announced afterwards.
        if (AppSettings.inQuietHours(c)) return Result.success()
        val prefs = c.getSharedPreferences("kiro_alerts", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis() / 1000
        val last = prefs.getLong("last", 0L)
        if (last == 0L) {
            prefs.edit().putLong("last", now).apply()
            return Result.success()
        }
        val muted = AppSettings.mutedShows(c)
        val ids = (MyListStore.all(c).map { it.id } + WatchHistory.all(c).map { it.animeId }).distinct().filter { it !in muted }
        if (ids.isEmpty()) {
            prefs.edit().putLong("last", now).apply()
            return Result.success()
        }
        // After a long gap only the last day is announced, so the phone is not flooded.
        val from = maxOf(last, now - 24 * 3600)
        val aired = withContext(Dispatchers.IO) { AniListExtra.aired(ids, from, now) } ?: return Result.success()
        prefs.edit().putLong("last", now).apply()
        if (aired.isEmpty() || !NotificationManagerCompat.from(c).areNotificationsEnabled()) return Result.success()

        val seen = prefs.getStringSet("seen", emptySet()).orEmpty().toMutableSet()
        val manager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when an anime in your list has a new episode"
                },
            )
        }
        for (a in aired.take(8)) {
            val key = "${a.anime.id}:${a.episode}"
            if (!seen.add(key)) continue
            val open = Intent(c, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(UpdateWorker.EXTRA_OPEN, "anime")
                putExtra("anime_id", a.anime.id)
            }
            val pending = PendingIntent.getActivity(c, a.anime.id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.fluent_alert_regular)
                .setContentTitle(com.biaruuu.kiro.ui.i18n.I18n.tr(a.anime.displayTitle))
                .setContentText(com.biaruuu.kiro.ui.i18n.I18n.tr("Episode ${a.episode} is out now"))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .build()
            try {
                manager.notify(6000 + (key.hashCode() and 0xFFFF), n)
            } catch (_: SecurityException) {
            }
        }
        prefs.edit().putStringSet("seen", seen.toList().takeLast(200).toSet()).apply()
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "episodes_v1"

        @JvmStatic
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "episode-alerts",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EpisodeAlertWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
