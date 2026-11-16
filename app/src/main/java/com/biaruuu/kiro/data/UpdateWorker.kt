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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.biaruuu.kiro.MainActivity
import com.biaruuu.kiro.R
import java.util.concurrent.TimeUnit

/** Checks for a new version in the background and tells the user with a notification. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val release = UpdateChecker.latest() ?: return Result.success()
        notifyIfNew(applicationContext, release)
        return Result.success()
    }

    companion object {
        const val EXTRA_OPEN = "kiro_open"
        private const val CHANNEL = "updates_v2"
        private const val NOTIFICATION_ID = 4101

        /**
         * Posts "a new version is available" once per version. Used by the background check and by
         * the check Kiro makes each time it opens. Returns true if a notification was shown.
         */
        @JvmStatic
        fun notifyIfNew(ctx: Context, release: UpdateChecker.Release): Boolean {
            if (!UpdateChecker.isNewer(release.version, UpdateChecker.currentVersion)) return false
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
            if (!UpdateChecker.markNotified(ctx, release.version)) return false

            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Tells you when a new version of Kiro is available"
                    },
                )
            }
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN, "update")
            }
            val pending = PendingIntent.getActivity(
                ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.fluent_arrow_download_regular)
                .setContentTitle("Kiro ${release.version} is available")
                .setContentText("Tap to see what's new and update.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            return try {
                manager.notify(NOTIFICATION_ID, notification)
                true
            } catch (_: SecurityException) {
                false
            }
        }

        /** A check every few hours, plus one right now. Safe to call on every launch. */
        @JvmStatic
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            wm.enqueueUniquePeriodicWork(
                "update-check",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
            )
            wm.enqueueUniqueWork(
                "update-check-now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(online).build(),
            )
        }
    }
}
