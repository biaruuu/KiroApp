package com.biaruuu.kiro.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.components.AnimeRow
import com.biaruuu.kiro.ui.components.ErrorState

@Composable
fun HomeScreen(
    listState: LazyListState,
    onAnimeClick: (Anime) -> Unit,
    onPlay: (Anime) -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        HomeUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        is HomeUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = modifier,
        )

        is HomeUiState.Success -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (current.content.hero.isNotEmpty()) {
                item(key = "hero") {
                    HeroCarousel(
                        heroes = current.content.hero,
                        onPlay = onPlay,
                        onNotifications = onNotifications,
                    )
                }
            }
            items(current.content.sections, key = { it.title }) { section ->
                AnimeRow(
                    title = section.title,
                    items = section.items,
                    onAnimeClick = onAnimeClick,
                )
            }
        }
    }
}
