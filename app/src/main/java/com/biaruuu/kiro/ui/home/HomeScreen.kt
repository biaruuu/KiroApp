package com.biaruuu.kiro.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.components.AnimeRow
import com.biaruuu.kiro.ui.components.ErrorState
import com.biaruuu.kiro.ui.components.RankedRow
import com.biaruuu.kiro.ui.components.HomeSkeleton
import com.biaruuu.kiro.ui.theme.LocalDimens

@Composable
fun HomeScreen(
    listState: LazyListState,
    onAnimeClick: (Anime) -> Unit,
    onPlay: (Anime) -> Unit,
    onContinue: (Int, Int) -> Unit,
    onNotifications: () -> Unit,
    onSchedule: () -> Unit = {},
    modifier: Modifier = Modifier,
    hasUnread: Boolean = false,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dims = LocalDimens.current
    val history = rememberWatchHistory()

    // "Because you watched ..." rows, from what was watched most recently.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val watchedVersion by com.biaruuu.kiro.data.WatchedStore.version.collectAsState()
    // The single latest thing watched or finished decides the recommendations.
    val seeds = remember(history, watchedVersion) {
        val recent = ArrayList<Triple<Long, Int, String>>()
        history.take(2).forEach { recent.add(Triple(it.updatedAt, it.animeId, it.title)) }
        com.biaruuu.kiro.data.WatchedStore.completed(appContext).take(1).forEach { recent.add(Triple(it.at, it.id, it.title)) }
        recent.sortedByDescending { it.first }.map { it.second to it.third }.distinctBy { it.first }
    }
    // Anime with a Filipino dub, from the developer's library; it updates live.
    val dubbed by com.biaruuu.kiro.data.TagalogDub.shows.collectAsState()
    LaunchedEffect(Unit) {
        if (com.biaruuu.kiro.data.auth.AuthRepository.isConfigured(appContext)) {
            runCatching { com.biaruuu.kiro.data.TagalogDub.warm() }
        }
    }
    var because by remember { mutableStateOf<List<com.biaruuu.kiro.data.BecauseYouWatched.Row>>(emptyList()) }
    LaunchedEffect(seeds) {
        because = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.biaruuu.kiro.data.BecauseYouWatched.load(appContext, seeds)
        }
    }

    when (val current = state) {
        HomeUiState.Loading -> HomeSkeleton(modifier = modifier)

        is HomeUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = modifier,
        )

        is HomeUiState.Success -> Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        ) {
            if (current.content.hero.isNotEmpty()) {
                item(key = "hero", contentType = "hero") {
                    HeroCarousel(
                        heroes = current.content.hero,
                        onPlay = onPlay,
                        onNotifications = onNotifications,
                        onSchedule = onSchedule,
                        hasUnread = hasUnread,
                    )
                }
            }
            if (history.isNotEmpty()) {
                item(key = "continue", contentType = "continue") {
                    ContinueWatchingRow(items = history, onContinue = onContinue)
                }
            }
            if (dubbed.isNotEmpty()) {
                item(key = "tagalog-dubbed", contentType = "row") {
                    AnimeRow(title = "Tagalog Dubbed", items = dubbed, onAnimeClick = onAnimeClick)
                }
            }
            current.content.sections.forEachIndexed { index, section ->
                item(key = section.title, contentType = if (section.isRanked) "ranked" else "row") {
                    if (section.isRanked) {
                        RankedRow(title = section.title, items = section.items, onAnimeClick = onAnimeClick)
                    } else {
                        AnimeRow(title = section.title, items = section.items, onAnimeClick = onAnimeClick)
                    }
                }
                // Right under Trending Now.
                if (index == 0) {
                    items(because, key = { "by:" + it.title }, contentType = { "row" }) { row ->
                        AnimeRow(title = row.title, items = row.items, onAnimeClick = onAnimeClick)
                    }
                }
            }
        }
        // Once the hero has scrolled away, a black strip keeps titles from running under the clock.
        val showStrip by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
        if (showStrip) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.Black)
                    .align(Alignment.TopCenter),
            )
        }
        }
    }
}
