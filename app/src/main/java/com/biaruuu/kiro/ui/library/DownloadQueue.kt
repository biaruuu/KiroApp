package com.biaruuu.kiro.ui.library

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.data.Downloads
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.KiroAccent

private val Dim = Color(0xFF8E8E93)
private val Card = Color(0xFF18181B)

internal fun speedText(bps: Long): String = when {
    bps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB/s", bps / 1_000_000.0)
    bps >= 1_000 -> "${bps / 1_000} KB/s"
    else -> "$bps B/s"
}

internal fun etaText(seconds: Long): String = when {
    seconds < 0 -> ""
    seconds < 60 -> "${seconds}s left"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
}

private fun sizeShort(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.0f MB", b / (1024.0 * 1024.0))
    else -> "${b / 1024} KB"
}

/** Strip above the downloads grid: how many are running, total speed, and a way into the queue. */
@Composable
internal fun QueueBanner(onOpen: () -> Unit) {
    val queue by Downloads.queue.collectAsState()
    val active = queue.filter { it.active }
    val failed = queue.count { it.failed }
    if (active.isEmpty() && failed == 0) return
    val speed = active.sumOf { it.speed }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Card)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Download queue", color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val parts = buildList {
                if (active.isNotEmpty()) add("${active.size} in queue")
                if (speed > 0) add(speedText(speed))
                if (failed > 0) add("$failed failed")
            }
            Text(parts.joinToString(" · "), color = Dim, fontSize = 12.sp)
        }
        Text("Manage", color = KiroAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadQueueSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val queue by Downloads.queue.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = queue.filter { it.active }
    val allPaused = active.isNotEmpty() && active.all { it.paused }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.biaruuu.kiro.ui.theme.KiroSurface,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(top = 16.dp)) {
            Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download queue", color = KiroText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    val speed = active.sumOf { it.speed }
                    val left = active.filter { it.downloading }.maxOfOrNull { it.secondsLeft } ?: -1L
                    val sub = buildList {
                        add("${active.size} waiting or running")
                        if (speed > 0) add(speedText(speed))
                        if (left >= 0) add(etaText(left))
                    }.joinToString(" · ")
                    Text(sub, color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (active.isNotEmpty()) {
                    Text(
                        if (allPaused) "Resume all" else "Pause all",
                        color = KiroAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { if (allPaused) Downloads.resumeAll(context) else Downloads.pauseAll(context) }.padding(8.dp),
                    )
                }
            }
            if (queue.isEmpty()) {
                Text("Nothing is downloading.", color = Dim, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(modifier = Modifier.heightIn(max = 560.dp).padding(top = 8.dp)) {
                items(queue.size, key = { queue[it].id }) { i ->
                    QueueRow(queue[i], position = queue.take(i + 1).count { it.active }, activeCount = active.size)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(d: Downloads.Item, position: Int, activeCount: Int) {
    val context = LocalContext.current
    val pct = d.percent.toInt().coerceIn(0, 100)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = d.poster, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2E)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(d.title, color = KiroText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Episode ${d.episode}", color = Dim, fontSize = 12.sp)
                val status = when {
                    d.failed -> "Failed (${d.error.ifEmpty { "unknown" }})"
                    d.paused -> "Paused · $pct%"
                    d.queued -> "Queued · #$position"
                    d.waitingForNetwork -> "Waiting for Wi-Fi"
                    d.downloading -> buildList {
                        add("$pct%")
                        if (d.bytes > 0) add(sizeShort(d.bytes))
                        if (d.speed > 0) add(speedText(d.speed))
                        etaText(d.secondsLeft).takeIf { it.isNotEmpty() }?.let { add(it) }
                    }.joinToString(" · ")
                    else -> "Starting..."
                }
                Text(
                    status,
                    color = if (d.failed) Color(0xFFFF6B6B) else if (d.downloading) KiroAccent else Dim,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (d.active) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (d.percent / 100f).coerceIn(0.02f, 1f) },
                color = if (d.paused || d.queued) Dim else KiroAccent,
                trackColor = Color(0xFF3A3A3D),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.failed) {
                Action("Retry") { Downloads.retry(context, d) }
            } else {
                Action(if (d.paused) "Resume" else "Pause") { if (d.paused) Downloads.resume(context, d.id) else Downloads.pause(context, d.id) }
                if (position > 1) Action("Download first") { Downloads.prioritize(context, d.id) }
                if (position > 1) Action("Up") { Downloads.move(context, d.id, -1) }
                if (position < activeCount) Action("Down") { Downloads.move(context, d.id, 1) }
            }
            Spacer(Modifier.weight(1f))
            Action("Cancel", Color(0xFFFF6B6B)) { Downloads.remove(context, d.animeId, d.episode) }
        }
    }
}

@Composable
private fun Action(label: String, color: Color = Color.White, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
