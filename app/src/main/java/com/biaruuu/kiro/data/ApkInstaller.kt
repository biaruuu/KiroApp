package com.biaruuu.kiro.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** Downloads the new APK inside the app and opens the system installer when it is done. */
object ApkInstaller {

    fun start(context: Context, url: String, version: String) {
        val app = context.applicationContext
        if (url.isEmpty()) {
            com.biaruuu.kiro.ui.i18n.I18nToast.make(app, "Update isn't available right now", Toast.LENGTH_SHORT).show()
            return
        }
        val name = "Kiro-$version.apk"
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        File(dir, name).delete()
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(url))
                .setTitle("Kiro $version")
                .setDescription("Downloading update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, name),
        )
        com.biaruuu.kiro.ui.i18n.I18nToast.make(app, "Downloading update...", Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                app.unregisterReceiver(this)
                val file = File(dir, name)
                if (!file.exists() || file.length() == 0L) {
                    com.biaruuu.kiro.ui.i18n.I18nToast.make(app, "Download failed. Please try again.", Toast.LENGTH_LONG).show()
                    return
                }
                val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
