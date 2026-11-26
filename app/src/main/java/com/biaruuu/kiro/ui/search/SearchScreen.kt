package com.biaruuu.kiro.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.biaruuu.kiro.data.SearchHistory
import kotlin.math.roundToInt
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
import com.biaruuu.kiro.ui.i18n.Text
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
import com.biaruuu.kiro.ui.theme.KiroAccent
import com.biaruuu.kiro.ui.theme.LocalDimens
import kotlinx.coroutines.launch
import java.text.NumberFormat

// Colours sampled from the reference screens.
private val HeaderBackground = Color(0xFF121212)
private val FieldBackground = Color(0xFF18181B)
private val DividerColor = Color(0xFF333335)
private val MutedText = Color(0xFF8E8E93)
private val SheetBackground = Color(0xFF18181B)

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
    SortOrder.SCORE -> "Highest rated"
    SortOrder.OLDEST -> "Oldest first"
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
    val context = LocalContext.current
    var showSort by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(SearchHistory.all(context)) }

    // Back closes the search overlay first.
    BackHandler(enabled = focused) { focusManager.clearFocus() }

    fun pick(text: String) {
        viewModel.onQueryChange(text)
        viewModel.submit()
        recents = SearchHistory.add(context, text)
        focusManager.clearFocus()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Black strip behind the system status bar, as in the reference.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color.Black),
        )

        // Fixed header: search field (+ genre tabs when the field isn't being used).
        Column(modifier = Modifier.fillMaxWidth().background(HeaderBackground)) {
            SearchField(
                value = state.query,
                focused = focused,
                onFocusChange = { focused = it },
                onValueChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                onSearch = {
                    if (state.query.isNotBlank()) recents = SearchHistory.add(context, state.query)
                    viewModel.submit()
                    focusManager.clearFocus()
                },
                onCancel = {
                    viewModel.clearQuery()
                    focusManager.clearFocus()
                },
            )
            Divider()
            if (!focused) {
                GenreTabs(
                    selected = state.genre ?: ALL,
                    onSelect = { genre -> viewModel.onGenreToggle(genre) },
                    onSelectAll = { state.genre?.let(viewModel::onGenreToggle) },
                )
                Divider()
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (focused) {
                SearchOverlay(
                    query = state.query,
                    suggestions = remember(state.results) { state.results.take(8).map { it.displayTitle } },
                    recents = recents,
                    onPick = ::pick,
                    onRemove = { recents = SearchHistory.remove(context, it) },
                    onClearAll = { recents = SearchHistory.clear(context) },
                )
            } else {
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
                if (state.loading && state.results.isNotEmpty()) {
                    LinearProgressIndicator(
                        color = KiroAccent,
                        trackColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                    )
                }
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
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    val dims = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(KiroAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(FieldBackground)
                .onFocusChanged { onFocusChange(it.isFocused) },
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_search_regular),
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Search by title, tag",
                                color = MutedText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                    if (value.isNotEmpty() && focused) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_dismiss_circle_regular),
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
        if (focused) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel,
                    ),
            )
        }
    }
}

/** Recent searches while the field is empty, title suggestions (match in purple) while typing. */
@Composable
private fun SearchOverlay(
    query: String,
    suggestions: List<String>,
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val dims = LocalDimens.current
    val q = query.trim()
    LazyColumn(modifier = Modifier.fillMaxSize().background(com.biaruuu.kiro.ui.theme.KiroBackground)) {
        if (q.isEmpty()) {
            if (recents.isNotEmpty()) {
                item(key = "recent-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = dims.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Recent Searches", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Clear",
                            color = Color(0xFF9A9AA2),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClearAll,
                            ),
                        )
                    }
                }
                items(recents, key = { "recent-$it" }) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onPick(r) }
                            .padding(horizontal = dims.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            r,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.fluent_dismiss_regular),
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onRemove(r) },
                        )
                    }
                }
            }
        } else {
            item(key = "suggest-gap") { Spacer(Modifier.height(4.dp)) }
            items(suggestions, key = { "suggest-$it" }) { title ->
                val at = title.indexOf(q, ignoreCase = true)
                val text = buildAnnotatedString {
                    if (at < 0) {
                        append(title)
                    } else {
                        append(title.substring(0, at))
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append(title.substring(at, at + q.length)) }
                        append(title.substring(at + q.length))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onPick(title) }
                        .padding(horizontal = dims.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Genre text tabs. The white line slides to the picked one; "All" clears the genre filter. */
@Composable
private fun GenreTabs(
    selected: String,
    onSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    val dims = LocalDimens.current
    val density = LocalDensity.current
    val tabs = remember { listOf(ALL) + GENRES }
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    val scroll = rememberScrollState()
    val lefts = remember { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }
    val widths = remember { mutableStateListOf<Float>().apply { repeat(tabs.size) { add(0f) } } }
    var viewport by remember { mutableIntStateOf(0) }

    val lineX by animateFloatAsState(lefts[index], label = "genre-line-x")
    val lineW by animateFloatAsState(widths[index], label = "genre-line-w")

    LaunchedEffect(index, viewport, widths[index]) {
        if (viewport > 0 && widths[index] > 0f) {
            scroll.animateScrollTo((lefts[index] + widths[index] / 2f - viewport / 2f).roundToInt().coerceAtLeast(0))
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
            Spacer(Modifier.width(dims.gutter))
            tabs.forEachIndexed { i, tab ->
                val isSelected = i == index
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            lefts[i] = it.positionInParent().x
                            widths[i] = it.size.width.toFloat()
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (tab == ALL) onSelectAll() else if (!isSelected) onSelect(tab)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else MutedText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(if (i == tabs.lastIndex) dims.gutter else 24.dp))
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "count", contentType = "count") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$count Results",
                        color = MutedText,
                        fontSize = 13.sp,
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
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.fluent_chevron_down_regular),
                            contentDescription = "Sort",
                            tint = MutedText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            items(results, key = { it.id }, contentType = { "poster" }) { anime ->
                PosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    tagAtBottom = true,
                    compact = true,
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
                        CircularProgressIndicator(color = KiroAccent)
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
        Column(modifier = Modifier.navigationBarsPadding().padding(top = 4.dp)) {
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
            .height(53.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(34.dp)) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
        )
    }
}
