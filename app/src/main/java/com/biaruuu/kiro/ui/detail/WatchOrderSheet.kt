package com.biaruuu.kiro.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.data.OrderEntry
import com.biaruuu.kiro.data.WatchedStore
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.launch

private val SheetBg = Color(0xFF161616)
private val Dim = Color(0xFF8E8E93)

/** Row on the details page that opens the franchise watch order. */
@Composable
internal fun WatchOrderButton(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E22))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Franchise watch order", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("In release order · $count entries", color = Dim, fontSize = 12.sp)
        }
        Text("View", color = KiroPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchOrderSheet(
    currentId: Int,
    entries: List<OrderEntry>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val version by WatchedStore.version.collectAsState()
    val progress = remember(version, entries) {
        entries.associate { it.id to WatchedStore.watchedEpisodes(context, it.id).size }
    }
    val done = remember(version, entries) { entries.filter { WatchedStore.isCompleted(context, it.id) }.map { it.id }.toSet() }

    val mainCount = entries.count { it.main || it.id == currentId }
    var everything by remember { mutableStateOf(mainCount < 2) }
    val shown = if (everything) entries else entries.filter { it.main || it.id == currentId }
    val nextUp = shown.firstOrNull { it.id !in done && !it.upcoming }?.id

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(top = 16.dp)) {
            Text("Franchise watch order", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Sorted by release date, oldest first.", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp))
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderChip("Main story", !everything) { everything = false }
                OrderChip("Everything", everything) { everything = true }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val e = shown[i]
                    val here = e.id == currentId
                    val watched = progress[e.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (here) Color(0xFF232328) else Color.Transparent)
                            .clickable {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    onDismiss()
                                    if (!here) onSelect(e.id)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                        AsyncImage(
                            model = e.poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A2A2E)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(e.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = buildList {
                                add(e.formatLabel)
                                if (e.year > 0) add(e.year.toString())
                                if (e.episodes > 0) add(if (e.episodes == 1) "1 episode" else "${e.episodes} episodes")
                            }.joinToString(" · ")
                            Text(meta, color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (here) Tag("YOU ARE HERE", Color.White, Color(0xFF3A3A40))
                                if (e.id == nextUp && !here) Tag("NEXT UP", Color.White, KiroPurple)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        val status = when {
                            e.id in done -> "Watched"
                            e.upcoming -> "Upcoming"
                            watched > 0 && e.episodes > 0 -> "$watched/${e.episodes}"
                            watched > 0 -> "$watched watched"
                            else -> ""
                        }
                        if (status.isNotEmpty()) {
                            Text(status, color = if (e.id in done) KiroPurple else Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) KiroPurple else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) KiroPurple else Dim), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Tag(text: String, color: Color, bg: Color) {
    Text(
        text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(bg).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
