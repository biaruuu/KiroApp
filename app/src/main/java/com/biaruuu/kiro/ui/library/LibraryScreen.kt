package com.biaruuu.kiro.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.MyListStore
import com.biaruuu.kiro.data.WatchHistory
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.launch

private val Hairline = Color(0xFF2B2B2F)
private val Muted = Color(0xFF9A9A9F)
private val EmptyTint = Color(0xFF5E5E63)
private val Placeholder = Color(0xFF262628)
private val SheetBackground = Color(0xFF141414)

private const val TAB_WATCHED = 0
private const val TAB_LIST = 1
private const val TAB_DOWNLOAD = 2
private const val TAB_RATED = 3
private val Tabs = listOf("Recently Watched", "My List", "Download", "Rated")

/** What a long-press → "Remove" will act on. */
private data class RemoveTarget(val tab: Int, val animeId: Int, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAnime: (Int) -> Unit,
    onContinue: (animeId: Int, episode: Int) -> Unit,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(TAB_WATCHED) }
    var listSort by rememberSaveable { mutableIntStateOf(0) }
    var ratedSort by rememberSaveable { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    var sortSheet by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<RemoveTarget?>(null) }

    // Reloaded when a page opened from here is closed (refreshKey) and after every removal.
    val history = remember(refreshKey, version) { WatchHistory.all(context) }
    val myList = remember(refreshKey, version) { MyListStore.all(context) }
    val rated = remember(refreshKey, version) { MyListStore.allRated(context) }

    val sortedList = remember(myList, history, listSort) {
        when (listSort) {
            0 -> myList.sortedByDescending { it.addedAt }
            1 -> myList.sortedByDescending { e ->
                maxOf(e.addedAt, history.firstOrNull { it.animeId == e.id }?.updatedAt ?: 0L)
            }
            else -> myList.sortedByDescending { it.score }
        }
    }
    val sortedRated = remember(rated, ratedSort) {
        if (ratedSort == 0) rated
        else rated.sortedWith(compareByDescending<MyListStore.Entry> { it.rating }.thenByDescending { it.score })
    }

    val listSortLabels = listOf("Recently added", "Recently updated", "Highest average rating")
    val ratedSortLabels = listOf("Recently rated", "Highest rating")

    Column(modifier = modifier.statusBarsPadding()) {
        // Title bar
        Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text("Library", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        LibraryTabs(selected = tab, onSelect = { tab = it })
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        val count = when (tab) {
            TAB_WATCHED -> "${history.size} Series"
            TAB_LIST -> "${myList.size} Series"
            TAB_RATED -> "${rated.size} Series"
            else -> "0 Season"
        }
        val sortLabel = when (tab) {
            TAB_LIST -> listSortLabels[listSort]
            TAB_RATED -> ratedSortLabels[ratedSort]
            else -> null
        }
        CountBar(count = count, sortLabel = sortLabel, onSortClick = { sortSheet = true })

        when (tab) {
            TAB_WATCHED -> if (history.isEmpty()) {
                EmptyState(R.drawable.fluent_play_regular, "Nothing watched yet. Start an episode!")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(history, key = { it.animeId }) { e ->
                        WatchedCard(
                            entry = e,
                            onPlay = { onContinue(e.animeId, e.episode) },
                            onInfo = { onOpenAnime(e.animeId) },
                            onRemove = { removeTarget = RemoveTarget(TAB_WATCHED, e.animeId, e.title) },
                        )
                    }
                }
            }

            TAB_LIST -> if (sortedList.isEmpty()) {
                EmptyState(R.drawable.fluent_library_regular, "Start adding favorites to your list!")
            } else {
                PosterGrid(
                    entries = sortedList,
                    showRating = false,
                    onOpen = onOpenAnime,
                    onRemove = { removeTarget = RemoveTarget(TAB_LIST, it.id, it.title) },
                )
            }

            TAB_DOWNLOAD -> EmptyState(R.drawable.fluent_arrow_download_regular, "Download episodes to watch offline")

            else -> if (sortedRated.isEmpty()) {
                EmptyState(R.drawable.fluent_star_regular, "Rate anime and they will show up here!")
            } else {
                PosterGrid(
                    entries = sortedRated,
                    showRating = true,
                    onOpen = onOpenAnime,
                    onRemove = { removeTarget = RemoveTarget(TAB_RATED, it.id, it.title) },
                )
            }
        }
    }

    if (sortSheet && (tab == TAB_LIST || tab == TAB_RATED)) {
        val labels = if (tab == TAB_LIST) listSortLabels else ratedSortLabels
        val current = if (tab == TAB_LIST) listSort else ratedSort
        SortSheet(
            labels = labels,
            current = current,
            onSelect = { if (tab == TAB_LIST) listSort = it else ratedSort = it },
            onDismiss = { sortSheet = false },
        )
    }

    removeTarget?.let { target ->
        val (heading, body) = when (target.tab) {
            TAB_WATCHED -> "Remove from Recently Watched?" to "\"${target.title}\" will no longer appear under Continue Watching."
            TAB_LIST -> "Remove from My List?" to "\"${target.title}\" will be removed from your list."
            else -> "Remove your rating?" to "Your rating for \"${target.title}\" will be cleared."
        }
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Color(0xFF1E1E22),
            title = { Text(heading, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(body, color = Muted, fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = {
                    when (target.tab) {
                        TAB_WATCHED -> WatchHistory.remove(context, target.animeId)
                        TAB_LIST -> MyListStore.remove(context, target.animeId)
                        else -> {
                            MyListStore.setRating(context, target.animeId, 0)
                            MyListStore.clearRatedMeta(context, target.animeId)
                        }
                    }
                    version++
                    removeTarget = null
                }) { Text("Remove", color = KiroPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel", color = Color.White) }
            },
        )
    }
}

@Composable
private fun LibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    // Keep the chosen tab roughly centred, like the reference.
    LaunchedEffect(selected) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selected }
        if (info != null) {
            val center = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
            listState.animateScrollBy((info.offset + info.size / 2 - center).toFloat())
        } else {
            listState.animateScrollToItem(selected)
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        itemsIndexed(Tabs) { index, label ->
            val active = index == selected
            Column(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (active) Color.White else Color(0xFF8C8C91),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .height(3.dp)
                        .fillMaxWidth()
                        .background(if (active) Color.White else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun CountBar(count: String, sortLabel: String?, onSortClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(count, color = Muted, fontSize = 16.sp)
        if (sortLabel != null) {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSortClick,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sortLabel, color = Muted, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.fluent_chevron_down_regular),
                    contentDescription = "Sort",
                    tint = Muted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchedCard(
    entry: WatchHistory.Entry,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.combinedClickable(onClick = onPlay, onLongClick = onRemove)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Placeholder),
        ) {
            AsyncImage(
                model = entry.thumb ?: entry.image,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fluent_play_filled),
                    contentDescription = "Continue",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            // Progress: white track with the purple part watched, inset like the reference.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.92f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(entry.progress().coerceIn(0.04f, 1f))
                        .height(4.dp)
                        .background(KiroPurple),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                entry.title,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fluent_info_regular),
                    contentDescription = "Details",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            "Episode ${entry.episode}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(
    entries: List<MyListStore.Entry>,
    showRating: Boolean,
    onOpen: (Int) -> Unit,
    onRemove: (MyListStore.Entry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.id }) { e ->
            Column(modifier = Modifier.combinedClickable(onClick = { onOpen(e.id) }, onLongClick = { onRemove(e) })) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Placeholder),
                ) {
                    AsyncImage(
                        model = e.posterUrl.ifEmpty { null },
                        contentDescription = e.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    e.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showRating && e.rating > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_star_filled),
                            contentDescription = null,
                            tint = KiroPurple,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${e.rating}/5", color = Muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(@DrawableRes icon: Int, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = EmptyTint,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(message, color = EmptyTint, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    labels: List<String>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(top = 8.dp)) {
            labels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            onSelect(index)
                            close()
                        }
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(44.dp)) {
                        if (index == current) {
                            Icon(
                                painter = painterResource(R.drawable.fluent_checkmark_regular),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Text(label, color = Color.White, fontSize = 19.sp)
                }
            }
            Box(Modifier.padding(horizontal = 24.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Hairline))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { close() }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(44.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_dismiss_regular),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text("Cancel", color = Color.White, fontSize = 19.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
