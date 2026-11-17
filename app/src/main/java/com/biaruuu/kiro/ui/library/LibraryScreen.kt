package com.biaruuu.kiro.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import com.biaruuu.kiro.ui.components.PageTopBar
import kotlin.math.abs
import kotlin.math.roundToInt
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
import androidx.compose.foundation.lazy.items as listItems
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
import androidx.compose.runtime.collectAsState
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
private const val TAB_COMPLETED = 2
private const val TAB_DOWNLOAD = 3
private const val TAB_RATED = 4
private val Tabs = listOf("Recently Watched", "My List", "Completed", "Download", "Rated")

/** What a long-press → "Remove" will act on. */
private data class RemoveTarget(val tab: Int, val animeId: Int, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAnime: (Int) -> Unit,
    onContinue: (animeId: Int, episode: Int) -> Unit,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    onCustomize: () -> Unit = {},
) {
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { Tabs.size })
    val scope = rememberCoroutineScope()
    val tab = pager.currentPage
    var listSort by rememberSaveable { mutableIntStateOf(0) }
    var ratedSort by rememberSaveable { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    var sortSheet by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<RemoveTarget?>(null) }

    // Reloaded when a page opened from here is closed (refreshKey) and after every removal.
    val cloud by com.biaruuu.kiro.data.CloudSync.version.collectAsState()
    val history = remember(refreshKey, version, cloud) { WatchHistory.all(context) }
    val myList = remember(refreshKey, version, cloud) { MyListStore.all(context) }
    val rated = remember(refreshKey, version, cloud) { MyListStore.allRated(context) }

    val watchedVersion by com.biaruuu.kiro.data.WatchedStore.version.collectAsState()
    val completed = remember(refreshKey, version, cloud, watchedVersion) {
        com.biaruuu.kiro.data.WatchedStore.completed(context).map {
            MyListStore.Entry(it.id, it.title, it.poster, it.at, 0, 0)
        }
    }
    val profile by com.biaruuu.kiro.data.ProfileRepository.profile.collectAsState()
    val authState by com.biaruuu.kiro.data.auth.AuthRepository.state.collectAsState()
    val shownName = profile.username.ifBlank { (authState as? com.biaruuu.kiro.data.auth.AuthState.SignedIn)?.name ?: "Guest" }

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
        com.biaruuu.kiro.ui.components.LibraryHeader(
            profile = profile,
            name = shownName,
            modifier = Modifier.clickable(enabled = authState is com.biaruuu.kiro.data.auth.AuthState.SignedIn, onClick = onCustomize),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        LibraryTabs(pager = pager, onSelect = { scope.launch { pager.animateScrollToPage(it) } })
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        // Swiping or tapping a tab slides between the four pages.
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    TAB_WATCHED -> {
                        CountBar("${history.size} Series", null) {}
                        if (history.isEmpty()) {
                            EmptyState(R.drawable.fluent_inbox_regular, "Start watching to build your library!")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(160.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
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
                    }

                    TAB_LIST -> {
                        CountBar("${myList.size} Series", listSortLabels[listSort]) { sortSheet = true }
                        if (sortedList.isEmpty()) {
                            EmptyState(R.drawable.fluent_inbox_regular, "Start adding favorites to your list!")
                        } else {
                            PosterGrid(
                                entries = sortedList,
                                showRating = false,
                                onOpen = onOpenAnime,
                                onRemove = { removeTarget = RemoveTarget(TAB_LIST, it.id, it.title) },
                            )
                        }
                    }

                    TAB_COMPLETED -> {
                        CountBar("${completed.size} Series", null) {}
                        if (completed.isEmpty()) {
                            EmptyState(R.drawable.fluent_checkmark_regular, "Finish an anime and it will show up here!")
                        } else {
                            PosterGrid(
                                entries = completed,
                                showRating = false,
                                onOpen = onOpenAnime,
                                onRemove = { removeTarget = RemoveTarget(TAB_COMPLETED, it.id, it.title) },
                            )
                        }
                    }

                    TAB_DOWNLOAD -> {
                        val downloads by com.biaruuu.kiro.data.Downloads.items.collectAsState()
                        val series = downloads.map { it.animeId }.distinct().size
                        CountBar(if (downloads.isEmpty()) "0 Series" else "$series Series \u00b7 ${downloads.size} Episodes", null) {}
                        if (downloads.isEmpty()) {
                            EmptyState(R.drawable.fluent_arrow_download_regular, "Download episodes to watch offline")
                        } else {
                            DownloadList(downloads, onPlay = onContinue, onOpenAnime = onOpenAnime)
                        }
                    }

                    else -> {
                        CountBar("${rated.size} Series", ratedSortLabels[ratedSort]) { sortSheet = true }
                        if (sortedRated.isEmpty()) {
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
            TAB_COMPLETED -> "Remove from Completed?" to "\"${target.title}\" will be marked as not watched."
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
                        TAB_COMPLETED -> com.biaruuu.kiro.data.WatchedStore.clear(context, target.animeId)
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
private fun LibraryTabs(pager: PagerState, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val lefts = remember { mutableStateListOf<Float>().apply { repeat(Tabs.size) { add(0f) } } }
    val widths = remember { mutableStateListOf<Float>().apply { repeat(Tabs.size) { add(0f) } } }
    var viewport by remember { mutableIntStateOf(0) }

    // The white line slides between the two tabs while the page is being dragged.
    val cur = pager.currentPage
    val frac = pager.currentPageOffsetFraction
    val target = (if (frac > 0f) cur + 1 else if (frac < 0f) cur - 1 else cur).coerceIn(0, Tabs.lastIndex)
    val p = abs(frac).coerceIn(0f, 1f)
    val lineX = lerp(lefts[cur], lefts[target], p)
    val lineW = lerp(widths[cur], widths[target], p)

    // Keep the chosen tab in view.
    LaunchedEffect(cur, viewport, widths[cur]) {
        if (viewport > 0 && widths[cur] > 0f) {
            scroll.animateScrollTo((lefts[cur] + widths[cur] / 2f - viewport / 2f).roundToInt().coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Spacer(Modifier.width(16.dp))
            Tabs.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (cur == index) Color.White else Color(0xFF8C8C91),
                    label = "tab-color",
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            lefts[index] = it.positionInParent().x
                            widths[index] = it.size.width.toFloat()
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.width(if (index == Tabs.lastIndex) 16.dp else 24.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(lineX.roundToInt(), 0) }
                .width(with(density) { lineW.toDp() })
                .height(2.dp)
                .background(Color.White),
        )
    }
}

@Composable
private fun CountBar(count: String, sortLabel: String?, onSortClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(count, color = Muted, fontSize = 13.sp)
        if (sortLabel != null) {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSortClick,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sortLabel, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.fluent_chevron_down_regular),
                    contentDescription = "Sort",
                    tint = Muted,
                    modifier = Modifier.size(20.dp),
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
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .offset(y = (-4).dp)
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fluent_info_regular),
                    contentDescription = "Details",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            "Episode ${entry.episode}",
            color = Color.White,
            fontSize = 13.sp,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.id }) { e ->
            Column(modifier = Modifier.combinedClickable(onClick = { onOpen(e.id) }, onLongClick = { onRemove(e) })) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f)
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
                Spacer(Modifier.height(6.dp))
                Text(
                    e.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
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
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(String.format(java.util.Locale.US, "%.1f", e.rating / 2f), color = KiroPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(@DrawableRes icon: Int, message: String) {
    Column(
        // Bottom padding = the count bar's height, so the group is centred in the whole page.
        modifier = Modifier.fillMaxSize().padding(bottom = 52.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = EmptyTint,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(message, color = EmptyTint, fontSize = 14.sp, textAlign = TextAlign.Center)
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
        Column(modifier = Modifier.navigationBarsPadding().padding(top = 4.dp)) {
            labels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(53.dp)
                        .clickable {
                            onSelect(index)
                            close()
                        }
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(34.dp)) {
                        if (index == current) {
                            Icon(
                                painter = painterResource(R.drawable.fluent_checkmark_regular),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(label, color = Color.White, fontSize = 15.sp)
                }
            }
            Box(Modifier.padding(horizontal = 24.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Hairline))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(53.dp)
                    .clickable { close() }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(34.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_dismiss_regular),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text("Cancel", color = Color.White, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
    else String.format(java.util.Locale.US, "%d MB", (bytes / 1_000_000L).coerceAtLeast(if (bytes > 0) 1 else 0))

@Composable
private fun DownloadList(
    items: List<com.biaruuu.kiro.data.Downloads.Item>,
    onPlay: (animeId: Int, episode: Int) -> Unit,
    onOpenAnime: (Int) -> Unit,
) {
    val context = LocalContext.current
    val groups = remember(items) { items.groupBy { it.animeId }.values.toList() }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        listItems(groups, key = { it.first().animeId }) { episodes ->
            val first = episodes.first()
            val total = episodes.sumOf { it.bytes }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1D))) {
                // Series header
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAnime(first.animeId) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2D))) {
                        if (first.poster.isNotEmpty()) {
                            AsyncImage(model = first.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(first.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${episodes.size} episode${if (episodes.size == 1) "" else "s"}" + if (total > 0) " \u00b7 ${sizeLabel(total)}" else "",
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2D)))
                episodes.forEachIndexed { index, d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when {
                                    d.completed -> onPlay(d.animeId, d.episode)
                                    d.failed -> com.biaruuu.kiro.data.Downloads.retry(context, d)
                                    else -> {}
                                }
                            }
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (d.completed) Color(0xFF7B6CF6) else Color(0xFF2A2A2D)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${d.episode}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val name = d.episodeTitle.takeIf { it.isNotBlank() && !it.startsWith("Episode") } ?: "Episode ${d.episode}"
                            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            val pct = d.percent.toInt().coerceIn(0, 100)
                            val status = when {
                                d.completed -> "Ready to watch \u00b7 ${sizeLabel(d.bytes)}"
                                d.failed -> "Failed (${d.error.ifEmpty { "unknown" }}). Tap to retry"
                                d.waitingForNetwork -> "Waiting for Wi-Fi"
                                d.percent > 0f -> "Downloading $pct% \u00b7 ${sizeLabel(d.bytes)}"
                                d.bytes > 0 -> "Downloading \u00b7 ${sizeLabel(d.bytes)}"
                                else -> "Starting..."
                            }
                            Text(status, color = if (d.failed) Color(0xFFFF6B6B) else Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (d.active) {
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3A3D))) {
                                    if (d.percent > 0f) {
                                        Box(Modifier.fillMaxWidth((d.percent / 100f).coerceIn(0.02f, 1f)).height(4.dp).background(Color(0xFF7B6CF6)))
                                    } else {
                                        androidx.compose.material3.LinearProgressIndicator(
                                            color = Color(0xFF7B6CF6), trackColor = Color.Transparent,
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (d.active && d.percent > 0f) {
                            Spacer(Modifier.width(8.dp))
                            Text("${d.percent.toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { com.biaruuu.kiro.data.Downloads.remove(context, d.animeId, d.episode) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (d.completed) "\u00d7" else "\u25a0", color = Muted, fontSize = if (d.completed) 22.sp else 12.sp)
                        }
                    }
                    if (index < episodes.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(Color(0xFF2A2A2D)))
                }
            }
        }
    }
}
