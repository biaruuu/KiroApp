package com.biaruuu.kiro.ui.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.Downloads
import com.biaruuu.kiro.data.HomeLayout
import com.biaruuu.kiro.data.StorageInfo
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Muted = Color(0xFF9A9A9F)
private val CardBg = Color(0xFF1B1B1F)

private class Confirm(val title: String, val text: String, val action: () -> Unit)

/** Me > Storage: what takes space, and one-tap clean-ups. */
@Composable
fun StoragePage() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val downloads by Downloads.items.collectAsState()
    val sizes by produceState<Triple<Long, Long, Long>?>(null, refresh, downloads.size) {
        value = withContext(Dispatchers.IO) {
            Triple(StorageInfo.cacheBytes(context), StorageInfo.appDataBytes(context), 0L)
        }
    }
    val dlBytes = downloads.sumOf { it.bytes }
    val cache = sizes?.first ?: 0L
    val data = sizes?.second ?: 0L
    val total = (dlBytes + cache + data).coerceAtLeast(1L)
    val watched = remember(downloads) { StorageInfo.watchedDownloads(context) }
    val watchedBytes = watched.sumOf { it.bytes }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Kiro is using ${formatBytes(total)}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF2B2B2F))) {
            if (dlBytes > 0) Box(Modifier.weight(dlBytes.toFloat()).fillMaxSize().background(KiroPurple))
            if (cache > 0) Box(Modifier.weight(cache.toFloat()).fillMaxSize().background(Color(0xFFE5A93B)))
            if (data > 0) Box(Modifier.weight(data.toFloat()).fillMaxSize().background(Color(0xFF5BA8E8)))
        }
        Spacer(Modifier.height(14.dp))
        Legend(KiroPurple, "Downloads", formatBytes(dlBytes))
        Legend(Color(0xFFE5A93B), "Cache (images, saved pages)", formatBytes(cache))
        Legend(Color(0xFF5BA8E8), "App data", formatBytes(data))

        Spacer(Modifier.height(22.dp))
        Text("Clean up", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Action("Clear cache", formatBytes(cache), enabled = cache > 0) {
            confirm = Confirm("Clear cache?", "Images and saved pages are downloaded again when needed. Your downloads, list and progress are kept.") {
                val freed = StorageInfo.clearCache(context)
                Toast.makeText(context, "Freed ${formatBytes(freed)}", Toast.LENGTH_SHORT).show()
                refresh++
            }
        }
        Action("Delete watched episodes", if (watched.isEmpty()) "None" else "${watched.size} · ${formatBytes(watchedBytes)}", enabled = watched.isNotEmpty()) {
            confirm = Confirm("Delete watched downloads?", "Removes ${watched.size} downloaded episode${if (watched.size == 1) "" else "s"} you have already watched.") {
                val n = StorageInfo.removeWatchedDownloads(context)
                Toast.makeText(context, "Deleted $n episode${if (n == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                refresh++
            }
        }
        Action("Delete all downloads", formatBytes(dlBytes), enabled = downloads.isNotEmpty()) {
            confirm = Confirm("Delete all downloads?", "Every downloaded episode is removed from this phone.") {
                Downloads.removeAll(context)
                Toast.makeText(context, "All downloads deleted", Toast.LENGTH_SHORT).show()
                refresh++
            }
        }

        val shows = downloads.groupBy { it.animeId }
            .map { (id, list) -> Triple(id, list.first().title, list) }
            .sortedByDescending { t -> t.third.sumOf { it.bytes } }
        if (shows.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("Downloads by show", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            shows.forEach { (id, title, list) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.size} episode${if (list.size == 1) "" else "s"} · ${formatBytes(list.sumOf { it.bytes })}", color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        "Delete", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            confirm = Confirm("Delete $title?", "Removes ${list.size} downloaded episode${if (list.size == 1) "" else "s"}.") {
                                Downloads.removeSeries(context, id)
                                refresh++
                            }
                        }.padding(8.dp),
                    )
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Color(0xFF1E1E22),
            title = { Text(c.title, color = Color.White) },
            text = { Text(c.text, color = Color(0xFFD0D0D4)) },
            confirmButton = { TextButton(onClick = { confirm = null; c.action() }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Action(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(CardBg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (enabled) Color.White else Color(0xFF5E5E63), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Muted, fontSize = 13.sp)
    }
}

/** Me > Home layout: reorder or hide the Home rows. */
@Composable
fun HomeLayoutPage() {
    val context = LocalContext.current
    val version by HomeLayout.version.collectAsState()
    val known = remember(version) { HomeLayout.known(context) }
    val keys = remember(version) { HomeLayout.arrange(context, known, includeHidden = true) }
    val hidden = remember(version) { HomeLayout.hidden(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "Move rows up or down, or switch one off. The banner at the top always stays.",
            color = Muted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (keys.isEmpty()) {
            Text("Open the Home tab once so Kiro can list its rows here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
        keys.forEachIndexed { i, key ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(CardBg).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    HomeLayout.label(key),
                    color = if (key in hidden) Color(0xFF5E5E63) else Color.White,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                )
                Text(
                    "Up", color = if (i > 0) Color.White else Color(0xFF3A3A3E), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = i > 0) { HomeLayout.move(context, known, key, -1) }.padding(10.dp),
                )
                Text(
                    "Down", color = if (i < keys.lastIndex) Color.White else Color(0xFF3A3A3E), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = i < keys.lastIndex) { HomeLayout.move(context, known, key, 1) }.padding(10.dp),
                )
                Switch(
                    modifier = Modifier.scale(0.85f),
                    checked = key !in hidden,
                    onCheckedChange = { HomeLayout.setHidden(context, key, !it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = KiroPurple, checkedThumbColor = Color.White, checkedBorderColor = KiroPurple,
                        uncheckedTrackColor = Color(0xFF3A3A3E), uncheckedThumbColor = Color(0xFFB4B4BA), uncheckedBorderColor = Color(0xFF3A3A3E),
                    ),
                )
            }
        }
        if (keys.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Reset to default", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { HomeLayout.reset(context) }.padding(8.dp),
            )
        }
    }
}
