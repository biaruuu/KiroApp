package com.biaruuu.kiro.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.biaruuu.kiro.MainActivity
import com.biaruuu.kiro.R
import com.biaruuu.kiro.ui.i18n.I18n
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Two gentle reminders, sent from the existing 30-minute alert worker and governed by the same
 * switch and quiet hours: a streak about to break (evening) and the weekly recap (Sunday evening).
 * Each is sent at most once per day or week.
 */
object RetentionNotifier {
    private const val CHANNEL = "reminders_v1"

    fun run(c: Context) {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return
        val now = LocalDateTime.now()
        val prefs = c.getSharedPreferences("kiro_alerts", Context.MODE_PRIVATE)
        val today = LocalDate.now()

        // Streak about to break: 7 pm to 10 pm, only once a day.
        if (now.hour in 19..21 && prefs.getLong("streak_day", -1L) != today.toEpochDay()) {
            val s = WatchStats.streak(c)
            if (s.count >= 3 && !s.doneToday) {
                prefs.edit().putLong("streak_day", today.toEpochDay()).apply()
                post(c, 7001, "Keep your ${s.count}-day streak", "Watch an episode today to keep it going.")
            }
        }

        // Weekly recap: Sunday from 6 pm, once per week, only if something was watched.
        if (today.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
            if (prefs.getLong("recap_week", -1L) != monday) {
                val days = WatchStats.days(c)
                val week = (0 until 7).mapNotNull { days[monday + it] }
                val seconds = week.sumOf { it.seconds }
                val eps = week.sumOf { it.episodes }
                prefs.edit().putLong("recap_week", monday).apply()
                if (seconds >= 60 || eps > 0) {
                    val m = seconds / 60
                    val time = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
                    post(c, 7002, "Your weekly recap is ready", "You watched $time and $eps episodes this week.")
                }
            }
        }
    }

    private fun post(c: Context, id: Int, title: String, text: String) {
        I18n.ensure(c)
        val manager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Streak reminders and your weekly recap"
                },
            )
        }
        val open = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(UpdateWorker.EXTRA_OPEN, "stats")
        }
        val pending = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.fluent_alert_regular)
            .setContentTitle(I18n.tr(title))
            .setContentText(I18n.tr(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
