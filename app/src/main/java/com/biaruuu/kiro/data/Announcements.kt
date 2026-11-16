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
import com.biaruuu.kiro.data.auth.AuthRepository
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow

data class Announcement(val id: String, val title: String, val body: String, val createdAt: Long)

/** The developer's public card shown on every notification. */
data class AdminCard(val username: String, val bio: String, val avatar: String, val email: String)

/**
 * Messages from the developer, listened to live: one published in the admin panel reaches every
 * open app within a moment, as a system notification and an entry on the Notifications page.
 */
object Announcements {
    val list = MutableStateFlow<List<Announcement>>(emptyList())
    val admin = MutableStateFlow<AdminCard?>(null)
    val unread = MutableStateFlow(false)

    /** Set when Firestore refuses the read (usually: the security rules have not been published). */
    val error = MutableStateFlow<String?>(null)

    private const val PREFS = "kiro_update"
    private const val CHANNEL = "announcements"
    private var started = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun start(context: Context) {
        if (started || !AuthRepository.isConfigured(context)) return
        started = true
        val ctx = context.applicationContext
        val p = prefs(ctx)
        // A fresh install starts "caught up", so old announcements never arrive as new ones.
        if (!p.contains("ann_seen")) p.edit().putLong("ann_seen", System.currentTimeMillis()).putLong("ann_notified", System.currentTimeMillis()).apply()

        val db = FirebaseFirestore.getInstance()
        db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    error.value = err.message
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                error.value = null
                val items = snap.documents.map {
                    Announcement(it.id, it.getString("title").orEmpty(), it.getString("body").orEmpty(), it.getLong("createdAt") ?: 0L)
                }
                list.value = items
                unread.value = (items.maxOfOrNull { it.createdAt } ?: 0L) > p.getLong("ann_seen", 0L)
                val notified = p.getLong("ann_notified", 0L)
                for (change in snap.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val a = items.firstOrNull { it.id == change.document.id } ?: continue
                    if (a.createdAt > notified) {
                        p.edit().putLong("ann_notified", maxOf(a.createdAt, p.getLong("ann_notified", 0L))).apply()
                        post(ctx, a)
                    }
                }
            }
        db.collection("meta").document("admin").addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            admin.value = AdminCard(
                snap.getString("username").orEmpty(),
                snap.getString("bio").orEmpty(),
                snap.getString("avatar").orEmpty(),
                snap.getString("email").orEmpty(),
            )
        }
    }

    fun markSeen(context: Context) {
        val newest = list.value.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()
        prefs(context).edit().putLong("ann_seen", maxOf(newest, prefs(context).getLong("ann_seen", 0L))).apply()
        unread.value = false
    }

    private fun post(ctx: Context, a: Announcement) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Announcements", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "News and messages from the Kiro developer"
                },
            )
        }
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(UpdateWorker.EXTRA_OPEN, "notifications")
        }
        val pending = PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.fluent_alert_regular)
            .setContentTitle(a.title.ifBlank { "Message from the Kiro developer" })
            .setContentText(a.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            manager.notify(5000 + (a.id.hashCode() and 0xFFFF), n)
        } catch (_: SecurityException) {
        }
    }
}
