package com.biaruuu.kiro.ui.me

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.Backup
import com.biaruuu.kiro.data.CloudSync
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Card = Color(0xFF1E1E22)
private val Dim = Color(0xFF8E8E93)

/** Me > Backup and restore: save the library to a file, or load one (e.g. on a new phone or account). */
@Composable
fun BackupPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloudVersion by CloudSync.version.collectAsState()
    val now = remember(cloudVersion) { Backup.current(context) }
    var pending by remember { mutableStateOf<Backup.Data?>(null) }

    fun toast(msg: String) = com.biaruuu.kiro.ui.i18n.I18nToast.make(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.export(context).toByteArray()) } != null
                } catch (_: Exception) {
                    false
                }
            }
            toast(if (ok) "Backup saved." else "Couldn't save the backup.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                } catch (_: Exception) {
                    null
                }
            }
            val data = text?.let { Backup.parse(it) }
            when {
                data == null -> toast("That file isn't a Kiro backup.")
                data.isEmpty -> toast("That backup is empty.")
                else -> pending = data
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("On this phone", color = Dim, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(14.dp)) {
            Stat("My List", now.listCount)
            Stat("Rated", now.ratedCount)
            Stat("Completed", now.watchedCount)
            Stat("With a status", now.statusCount)
        }

        Spacer(Modifier.height(22.dp))
        Text("Backup", color = Dim, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        BigAction("Export backup", "Save your list, ratings, progress and statuses to a file.") {
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            exportLauncher.launch("kiro-backup-$stamp.json")
        }
        BigAction("Import backup", "Load a backup file. It is merged with what you already have.") {
            if (!CloudSync.allowed(context)) CloudSync.requireLogin(context) else importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Downloaded episodes aren't part of a backup (they're large); only your library is. To move to a new phone: export here, install Kiro there, log in, then import.",
            color = Dim, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }

    pending?.let { d ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Card,
            title = { Text("Restore backup?", color = Color.White) },
            text = {
                Text(
                    "This backup has ${d.listCount} in My List, ${d.ratedCount} rated, ${d.watchedCount} completed and ${d.statusCount} with a status. " +
                        "They are merged with your library; nothing you have is deleted.",
                    color = Color(0xFFD0D0D4),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    val changed = CloudSync.mergeBackup(context, d)
                    toast(if (changed) "Backup restored." else "Nothing new in that backup.")
                }) { Text("Restore", color = KiroPurple) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun Stat(label: String, n: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(n.toString(), color = Dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigAction(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Card)
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
