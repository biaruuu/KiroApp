package com.biaruuu.kiro.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.AnimeDetail
import com.biaruuu.kiro.data.EpisodeInfo
import com.biaruuu.kiro.data.MyListStore
import com.biaruuu.kiro.ui.components.ErrorState
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.launch

private val Bg = Color(0xFF121212)
private val DividerColor = Color(0xFF333335)
private val MutedText = Color(0xFF8E8E93)
private val SheetBackground = Color(0xFF161616)
private val ThumbBackground = Color(0xFF2A2A2C)

@Composable
fun DetailScreen(
    animeId: Int,
    onBack: () -> Unit,
    onOpenAnime: (Int) -> Unit,
    onMore: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: DetailViewModel = viewModel(key = "detail-$animeId", factory = DetailViewModel.Factory(animeId))
    val state by vm.state.collectAsState()
    val base = state.base

    Box(modifier = modifier.fillMaxSize().background(Bg)) {
        when {
            base == null && state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            base == null -> CircularProgressIndicator(
                color = KiroPurple,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> DetailContent(
                anime = base,
                detail = state.detail,
                loadingMore = state.loading,
                onOpenAnime = onOpenAnime,
                onMore = onMore,
            )
        }
        BackButton(onBack)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_left_regular),
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    anime: Anime,
    detail: AnimeDetail?,
    loadingMore: Boolean,
    onOpenAnime: (Int) -> Unit,
    onMore: (Int) -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 600
    val heroHeight = minOf((config.screenWidthDp * 0.78f).dp, (config.screenHeightDp * 0.5f).dp)
        .let { if (wide) minOf(it, 420.dp) else it }

    var inList by remember(anime.id) { mutableStateOf(MyListStore.isInList(context, anime.id)) }
    var rating by remember(anime.id) { mutableIntStateOf(MyListStore.getRating(context, anime.id)) }
    var muted by rememberSaveable { mutableStateOf(true) }
    var trailerPlaying by remember(detail?.trailerYoutubeId) { mutableStateOf(false) }
    var trailerFailed by remember(detail?.trailerYoutubeId) { mutableStateOf(false) }
    var showRating by remember { mutableStateOf(false) }
    var showSeasons by remember { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val trailerId = detail?.trailerYoutubeId?.takeIf { it.isNotBlank() && !trailerFailed }
    val comingSoon = { Toast.makeText(context, "Streaming is coming soon", Toast.LENGTH_SHORT).show() }

    val episodes = remember(detail, newestFirst) {
        val list = detail?.episodes.orEmpty()
        if (newestFirst) list.asReversed() else list
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ---- Hero: trailer, or the still image until it plays / when it cannot play ----
        item(key = "hero") {
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight).background(Color.Black)) {
                AsyncImage(
                    model = anime.heroUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (trailerId != null) {
                    val alpha by animateFloatAsState(
                        if (trailerPlaying) 1f else 0f,
                        tween(500),
                        label = "trailer-fade",
                    )
                    TrailerPlayer(
                        videoId = trailerId,
                        muted = muted,
                        onPlaying = { trailerPlaying = true },
                        onFailed = { trailerFailed = true },
                        modifier = Modifier.fillMaxSize().alpha(alpha),
                    )
                }
                // Mute toggle, bottom-right, only while a trailer is on screen.
                if (trailerId != null && trailerPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { muted = !muted },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (muted) R.drawable.fluent_speaker_mute_filled else R.drawable.fluent_speaker_filled,
                            ),
                            contentDescription = if (muted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // ---- Title block ----
        item(key = "title") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
                val rate = ratingLabel(anime.averageScore, detail?.ratingCount ?: 0)
                if (rate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_star_filled),
                            contentDescription = null,
                            tint = KiroPurple,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(rate, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    anime.displayTitle,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                val genres = genreLine(anime)
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(genres, color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(KiroPurple)
                        .clickable(onClick = comingSoon),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_play_filled),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Play", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionButton(
                        label = "My List",
                        icon = if (inList) R.drawable.fluent_checkmark_regular else R.drawable.fluent_add_regular,
                        tint = if (inList) KiroPurple else Color.White,
                    ) {
                        inList = MyListStore.toggle(context, anime)
                        Toast.makeText(
                            context,
                            if (inList) "Added to My List" else "Removed from My List",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    ActionButton(
                        label = "Rating",
                        icon = if (rating > 0) R.drawable.fluent_star_filled else R.drawable.fluent_star_regular,
                        tint = if (rating > 0) KiroPurple else Color.White,
                    ) { showRating = true }
                    ActionButton(label = "Share", icon = R.drawable.fluent_open_regular, tint = Color.White) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(anime))
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
                Spacer(Modifier.height(16.dp))
                val text = synopsis(anime)
                if (text.isNotEmpty()) {
                    Text(
                        text,
                        color = Color(0xFFD0D0D4),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "More",
                    color = MutedText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onMore(anime.id) }.padding(vertical = 6.dp),
                )
            }
        }

        // ---- Season dropdown ----
        val seasons = seasonEntries(anime, detail)
        if (seasons.size > 1) {
            item(key = "season") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, MutedText), RoundedCornerShape(4.dp))
                        .clickable { showSeasons = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            anime.displayTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.fluent_chevron_down_regular),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ---- Episodes header + coming soon banner ----
        item(key = "episodes-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.clickable { newestFirst = !newestFirst },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (newestFirst) "Newest" else "Oldest",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.fluent_arrow_sort_regular),
                        contentDescription = "Sort episodes",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (detail != null && episodes.isNotEmpty()) {
                    Text(
                        "${episodes.size} Episodes",
                        color = MutedText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        item(key = "banner") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(KiroPurple)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Streaming is coming soon", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (detail == null && loadingMore) {
            item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KiroPurple, modifier = Modifier.size(28.dp))
                }
            }
        }

        items(episodes, key = { it.number }, contentType = { "episode" }) { episode ->
            EpisodeRow(episode, fallbackImage = anime.heroUrl, onClick = comingSoon)
        }

        item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
    }

    if (showRating) {
        RatingSheet(
            current = rating,
            onSelect = {
                rating = it
                MyListStore.setRating(context, anime.id, it)
            },
            onDismiss = { showRating = false },
        )
    }
    if (showSeasons && detail != null) {
        SeasonSheet(
            current = anime,
            detail = detail,
            onSelect = { id -> if (id != anime.id) onOpenAnime(id) },
            onDismiss = { showSeasons = false },
        )
    }
}

private data class SeasonEntry(val id: Int, val title: String)

private fun seasonEntries(anime: Anime, detail: AnimeDetail?): List<SeasonEntry> {
    val related = detail?.related.orEmpty()
    val before = related.filter { it.isPrequel }.map { SeasonEntry(it.id, it.title) }
    val after = related.filter { it.relationType == "SEQUEL" }.map { SeasonEntry(it.id, it.title) }
    return before + SeasonEntry(anime.id, anime.displayTitle) + after
}

@Composable
private fun ActionButton(label: String, icon: Int, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(min = 72.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeInfo, fallbackImage: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThumbBackground),
        ) {
            val thumb = episode.thumbnailUrl ?: fallbackImage
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                episode.heading,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = episode.subtitle
            if (!sub.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(sub, color = MutedText, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.fluent_arrow_download_regular),
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(22.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingSheet(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Rate this anime", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (star in 1..5) {
                    Icon(
                        painter = painterResource(
                            if (star <= current) R.drawable.fluent_star_filled else R.drawable.fluent_star_regular,
                        ),
                        contentDescription = "$star stars",
                        tint = if (star <= current) KiroPurple else MutedText,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                // Tapping the current rating clears it.
                                onSelect(if (star == current) 0 else star)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSheet(
    current: Anime,
    detail: AnimeDetail,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val entries = remember(current.id, detail) { seasonEntries(current, detail) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        LazyColumn(modifier = Modifier.navigationBarsPadding().heightIn(max = 480.dp).padding(vertical = 8.dp)) {
            items(entries, key = { it.id }) { entry ->
                val selected = entry.id == current.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onSelect(entry.id)
                            }
                        }
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(40.dp)) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.fluent_checkmark_regular),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Text(
                        entry.title,
                        color = if (selected) Color.White else Color(0xFFD0D0D4),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
