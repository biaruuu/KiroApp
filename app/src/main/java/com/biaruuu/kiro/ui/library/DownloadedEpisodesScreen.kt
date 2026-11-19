package com.biaruuu.kiro.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import coil.compose.AsyncImage
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Downloads
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Panel = Color(0xFF1A1A1D)
private val Muted = Color(0xFF8E8E93)

private fun sizeText(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
    else String.format(java.util.Locale.US, "%d MB", (bytes / 1_000_000L).coerceAtLeast(if (bytes > 0) 1 else 0))

/** The downloaded (and downloading) episodes of one series, each on its own card. */
@Composable
fun DownloadedEpisodesScreen(
    animeId: Int,
    onBack: () -> Unit,
    onPlay: (animeId: Int, episode: Int) -> Unit,
    onOpenAnime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val all by Downloads.items.collectAsState()
    val episodes = remember(all, animeId) { all.filter { it.animeId == animeId }.sortedBy { it.episode } }
    var removeTarget by remember { mutableStateOf<Downloads.Item?>(null) }
    var removeAll by remember { mutableStateOf(false) }
    val title = episodes.firstOrNull()?.title.orEmpty()

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 700.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.fluent_chevron_left_regular), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (episodes.isNotEmpty()) {
                    Text(
                        "Delete all",
                        color = Muted, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { removeAll = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2D)))

            if (episodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloaded episodes", color = Muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth().clickable { onOpenAnime(animeId) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${episodes.size} episode${if (episodes.size == 1) "" else "s"} · ${sizeText(episodes.sumOf { it.bytes })}",
                                color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                            Text("Open anime", color = KiroPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(episodes, key = { it.id }) { d ->
                        EpisodeCard(
                            d,
                            onClick = {
                                when {
                                    d.completed -> onPlay(d.animeId, d.episode)
                                    d.failed -> Downloads.retry(context, d)
                                }
                            },
                            onRemove = { if (d.completed) removeTarget = d else Downloads.remove(context, d.animeId, d.episode) },
                        )
                    }
                }
            }
        }
    }

    removeTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF1E1E22),
            title = { Text("Delete episode ${d.episode}?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("It will be deleted from your phone.", color = Muted, fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = { Downloads.remove(context, d.animeId, d.episode); removeTarget = null }) {
                    Text("Delete", color = KiroPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel", color = Color.White) } },
        )
    }
    if (removeAll) {
        AlertDialog(
            onDismissRequest = { removeAll = false },
            containerColor = Color(0xFF1E1E22),
            title = { Text("Delete all episodes?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("Every downloaded episode of \"$title\" will be deleted from your phone.", color = Muted, fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = { Downloads.removeSeries(context, animeId); removeAll = false; onBack() }) {
                    Text("Delete", color = KiroPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { removeAll = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun EpisodeCard(d: Downloads.Item, onClick: () -> Unit, onRemove: () -> Unit) {
    val name = d.episodeTitle.takeIf { it.isNotBlank() } ?: "Episode ${d.episode}"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2D))) {
                AsyncImage(
                    model = d.thumb.ifEmpty { d.poster }.ifEmpty { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                if (d.completed) {
                    Box(Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.fluent_play_filled), contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Episode ${d.episode}", color = KiroPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (d.quality.isNotBlank() && d.completed) {
                    Spacer(Modifier.height(2.dp))
                    Text(d.quality, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.fluent_dismiss_regular), contentDescription = "Delete", tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        if (d.overview.isNotBlank()) {
            Text(
                d.overview,
                color = Color(0xFFB5B5BA), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
        // Status line
        val pct = d.percent.toInt().coerceIn(0, 100)
        val status = when {
            d.completed -> "Ready to watch · ${sizeText(d.bytes)}"
            d.failed -> "Failed (${d.error.ifEmpty { "unknown" }}). Tap to retry"
            d.paused -> "Paused · $pct%"
            d.queued -> "Queued · waiting for its turn"
            d.waitingForNetwork -> "Waiting for Wi-Fi"
            d.percent > 0f -> "Downloading $pct% · ${sizeText(d.bytes)}"
            d.bytes > 0 -> "Downloading · ${sizeText(d.bytes)}"
            else -> "Starting..."
        }
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(status, color = if (d.failed) Color(0xFFFF6B6B) else if (d.completed) KiroPurple else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (d.active) {
                Spacer(Modifier.height(6.dp))
                if (d.percent > 0f) {
                    LinearProgressIndicator(progress = { (d.percent / 100f).coerceIn(0.02f, 1f) }, color = KiroPurple, trackColor = Color(0xFF3A3A3D), modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
                } else {
                    LinearProgressIndicator(color = KiroPurple, trackColor = Color(0xFF3A3A3D), modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
