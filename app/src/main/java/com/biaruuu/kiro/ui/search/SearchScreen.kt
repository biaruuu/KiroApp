package com.biaruuu.kiro.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.components.ErrorState
import com.biaruuu.kiro.ui.components.MessageState
import com.biaruuu.kiro.ui.components.PosterCard
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.biaruuu.kiro.ui.theme.KiroSurface
import com.biaruuu.kiro.ui.theme.KiroSurfaceHigh
import com.biaruuu.kiro.ui.theme.LocalDimens

// These match AniList's genre names exactly.
private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery",
    "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    gridState: LazyGridState,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager: FocusManager = LocalFocusManager.current
    val dims = LocalDimens.current

    // On tablets the content is capped and centred instead of stretching edge to edge.
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = dims.searchMaxWidth)
            .fillMaxSize(),
    ) {
        TextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search anime") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.submit()
                    focusManager.clearFocus()
                },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = KiroSurfaceHigh,
                unfocusedContainerColor = KiroSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = KiroPurple,
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.gutter, vertical = 12.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(GENRES) { genre ->
                FilterChip(
                    selected = state.genre == genre,
                    onClick = { viewModel.onGenreToggle(genre) },
                    label = { Text(genre) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = KiroSurface,
                        labelColor = Color.White,
                        selectedContainerColor = KiroPurple,
                        selectedLabelColor = Color.White,
                    ),
                    border = null,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val showTrendingTitle = state.query.isBlank() && state.results.isNotEmpty()
        if (showTrendingTitle) {
            Text(
                text = if (state.genre == null) "Trending Now" else "Trending in ${state.genre}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = dims.gutter, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.results.isEmpty() && state.loading -> CircularProgressIndicator(
                    color = KiroPurple,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.results.isEmpty() && state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                )

                state.results.isEmpty() && state.loaded -> MessageState(
                    title = "No results",
                    subtitle = "Try a different title or remove the genre filter.",
                )

                else -> ResultsGrid(
                    gridState = gridState,
                    results = state.results,
                    loadingMore = state.loadingMore,
                    refreshing = state.loading,
                    onAnimeClick = {
                        focusManager.clearFocus()
                        onAnimeClick(it)
                    },
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
    }
}

@Composable
private fun ResultsGrid(
    gridState: LazyGridState,
    results: List<Anime>,
    loadingMore: Boolean,
    refreshing: Boolean,
    onAnimeClick: (Anime) -> Unit,
    onLoadMore: () -> Unit,
) {
    val dims = LocalDimens.current
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, results.size) {
        if (nearEnd) onLoadMore()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = dims.gridMinCell),
            contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { it.id }) { anime ->
                PosterCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = KiroPurple)
                    }
                }
            }
        }
        if (refreshing) {
            CircularProgressIndicator(
                color = KiroPurple,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }
    }
}
