package com.biaruuu.kiro.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        val layoutVersion by com.biaruuu.kiro.data.HomeLayout.version.collectAsState()
        val defaultKeys = buildList {
            if (history.isNotEmpty()) add(com.biaruuu.kiro.data.HomeLayout.CONTINUE)
            current.content.sections.forEachIndexed { index, section ->
                add(section.title)
                if (index == 0 && because.isNotEmpty()) add(com.biaruuu.kiro.data.HomeLayout.BECAUSE)
            }
            if (current.content.sections.isEmpty() && because.isNotEmpty()) add(com.biaruuu.kiro.data.HomeLayout.BECAUSE)
        }
        LaunchedEffect(defaultKeys) { com.biaruuu.kiro.data.HomeLayout.remember(appContext, defaultKeys) }
        val rows = remember(defaultKeys, layoutVersion) { com.biaruuu.kiro.data.HomeLayout.arrange(appContext, defaultKeys) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(dims.rowSpacing),
        ) {
            if (current.content.hero.isNotEmpty()) {
                item(key = "hero", contentType = "hero") {
                    HeroCarousel(
                        heroes = current.content.hero,
                        onPlay = onPlay,
                        onNotifications = onNotifications,
                        hasUnread = hasUnread,
                    )
                }
            }
            // Rows in the order chosen in Me > Home layout (hidden ones left out).
            items(rows, key = { it }, contentType = { if (it == "continue" || it == "because") it else "row" }) { key ->
                when (key) {
                    com.biaruuu.kiro.data.HomeLayout.CONTINUE ->
                        ContinueWatchingRow(items = history, onContinue = onContinue)
                    com.biaruuu.kiro.data.HomeLayout.BECAUSE -> Column(verticalArrangement = Arrangement.spacedBy(dims.rowSpacing)) {
                        because.forEach { row ->
                            AnimeRow(title = row.title, items = row.items, onAnimeClick = onAnimeClick)
                        }
                    }
                    else -> current.content.sections.firstOrNull { it.title == key }?.let { section ->
                        if (section.isRanked) {
                            RankedRow(title = section.title, items = section.items, onAnimeClick = onAnimeClick)
                        } else {
                            AnimeRow(title = section.title, items = section.items, onAnimeClick = onAnimeClick)
                        }
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
