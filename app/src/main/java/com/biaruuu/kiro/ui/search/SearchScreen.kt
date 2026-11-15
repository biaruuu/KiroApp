package com.biaruuu.kiro.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import com.biaruuu.kiro.ui.components.SearchSkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.SortOrder
import com.biaruuu.kiro.ui.components.ErrorState
import com.biaruuu.kiro.ui.components.MessageState
import com.biaruuu.kiro.ui.components.PosterCard
import com.biaruuu.kiro.ui.theme.KiroPurple
import com.biaruuu.kiro.ui.theme.LocalDimens
import kotlinx.coroutines.launch
import java.text.NumberFormat

// Colours sampled from the reference screens.
private val HeaderBackground = Color(0xFF1A1A1A)
private val FieldBackground = Color(0xFF3A3A3C)
private val DividerColor = Color(0xFF333335)
private val MutedText = Color(0xFF8E8E93)
private val SheetBackground = Color(0xFF161616)

// These match AniList's genre names exactly. "All" is added in front and means "no genre".
private val GENRES = listOf(
    "Action", "Romance", "Comedy", "Fantasy", "Adventure", "Drama", "Horror", "Mystery",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
)

private const val ALL = "All"

/** The poster grid always shows at least three cards across, more as the screen gets wider. */
private fun columnsFor(widthDp: Float, gutterDp: Float, gapDp: Float): Int {
    val target = 104f
    val usable = widthDp - gutterDp * 2
    return ((usable + gapDp) / (target + gapDp)).toInt().coerceAtLeast(3)
}

private fun SortOrder.label(): String = when (this) {
    SortOrder.POPULARITY -> "Popularity"
    SortOrder.RECENT -> "Recently updated"
}

@Composable
fun SearchScreen(
    gridState: LazyGridState,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager: FocusManager = LocalFocusManager.current
    var showSort by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Black strip behind the system status bar, as in the reference.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color.Black),
        )

        // Fixed header: search field + genre tabs.
        Column(modifier = Modifier.fillMaxWidth().background(HeaderBackground)) {
            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                onSearch = {
                    viewModel.submit()
                    focusManager.clearFocus()
                },
            )
            Divider()
            GenreTabs(
                selected = state.genre ?: ALL,
                onSelect = { genre -> viewModel.onGenreToggle(genre) },
                onSelectAll = { state.genre?.let(viewModel::onGenreToggle) },
            )
            Divider()
            if (state.loading) {
                LinearProgressIndicator(
                    color = KiroPurple,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.results.isEmpty() && state.loading -> SearchSkeleton(columns = 3)

                state.results.isEmpty() && state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry,
                )

                state.results.isEmpty() && state.loaded -> MessageState(
                    title = "No results",
                    subtitle = "Try a different title or pick another genre.",
                )

                else -> ResultsGrid(
                    gridState = gridState,
                    results = state.results,
                    total = state.total,
                    sort = state.sort,
                    loadingMore = state.loadingMore,
                    onSortClick = {
                        focusManager.clearFocus()
                        showSort = true
                    },
                    onAnimeClick = {
                        focusManager.clearFocus()
                        onAnimeClick(it)
                    },
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }

    if (showSort) {
        SortSheet(
            current = state.sort,
            onSelect = viewModel::onSortChange,
            onDismiss = { showSort = false },
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val dims = LocalDimens.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.gutter, vertical = 12.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(FieldBackground),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fluent_search_regular),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search by title, tag",
                            color = MutedText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_dismiss_regular),
                        contentDescription = "Clear",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClear,
                            ),
                    )
                }
            }
        },
    )
}

/** Text tabs with a white underline under the selected one. "All" clears the genre filter. */
@Composable
private fun GenreTabs(
    selected: String,
    onSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    val dims = LocalDimens.current
    val tabs = remember { listOf(ALL) + GENRES }

    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        items(tabs, key = { it }) { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (tab == ALL) onSelectAll() else if (!isSelected) onSelect(tab)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else MutedText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) Color.White else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    gridState: LazyGridState,
    results: List<Anime>,
    total: Int,
    sort: SortOrder,
    loadingMore: Boolean,
    onSortClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onLoadMore: () -> Unit,
) {
    val dims = LocalDimens.current
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 9
        }
    }
    LaunchedEffect(nearEnd, results.size) {
        if (nearEnd) onLoadMore()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = columnsFor(maxWidth.value, dims.gutter.value, dims.cardSpacing.value)
        val count = remember(total, results.size) {
            NumberFormat.getIntegerInstance().format(if (total > 0) total else results.size)
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = dims.gutter, end = dims.gutter, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(dims.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "count", contentType = "count") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$count Results",
                        color = MutedText,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSortClick,
                        ),
                    ) {
                        Text(
                            text = sort.label(),
                            color = MutedText,
                            fontSize = 16.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.fluent_chevron_down_regular),
                            contentDescription = "Sort",
                            tint = MutedText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            items(results, key = { it.id }, contentType = { "poster" }) { anime ->
                PosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    tagAtBottom = true,
                )
            }

            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
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
    }
}

/** Bottom sheet with the sort choices, styled like the reference (flat, with a Cancel row). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close(after: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            after()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            SortOrder.entries.forEach { option ->
                SheetRow(
                    label = option.label(),
                    icon = if (option == current) R.drawable.fluent_checkmark_regular else null,
                    onClick = { close { onSelect(option) } },
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor),
            )
            SheetRow(
                label = "Cancel",
                icon = R.drawable.fluent_dismiss_regular,
                onClick = { close() },
            )
        }
    }
}

@Composable
private fun SheetRow(
    label: String,
    icon: Int?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(56.dp)) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
        )
    }
}
