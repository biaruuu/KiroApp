package com.biaruuu.kiro.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.biaruuu.kiro.ui.components.DetailSkeleton
import com.biaruuu.kiro.ui.components.EpisodeSkeletonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
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
import com.biaruuu.kiro.data.AppSettings
import com.biaruuu.kiro.data.MyListStore
import com.biaruuu.kiro.data.SeasonEntry
import com.biaruuu.kiro.ui.components.ErrorState
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.delay
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
    onPlay: (animeId: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: DetailViewModel = viewModel(key = "detail-$animeId", factory = DetailViewModel.Factory(animeId))
    val state by vm.state.collectAsState()
    val base = state.base

    Box(modifier = modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
      Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        when {
            base == null && state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            base == null -> DetailSkeleton()
            else -> DetailContent(
                anime = base,
                detail = state.detail,
                trailers = state.trailers,
                loadingMore = state.loading,
                onOpenAnime = onOpenAnime,
                onMore = onMore,
                onPlay = onPlay,
                episodesFromState = state.episodes,
                seasonsFromState = state.seasons,
            )
        }
        BackButton(onBack)
      }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
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
    trailers: List<String>,
    loadingMore: Boolean,
    onOpenAnime: (Int) -> Unit,
    onMore: (Int) -> Unit,
    onPlay: (Int, Int) -> Unit,
    episodesFromState: List<EpisodeInfo>,
    seasonsFromState: List<SeasonEntry>,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 600
    val heroHeight = minOf((config.screenWidthDp * 1.18f).dp, (config.screenHeightDp * 0.56f).dp)
        .let { if (wide) minOf(it, 480.dp) else it }
    // The video is 16:9. Make it just big enough to cover the hero (like CSS "cover"), centered
    // and clipped, so it fills the hero from top to bottom instead of sitting in a strip.
    val coverWidth = maxOf(config.screenWidthDp.dp, heroHeight * (16f / 9f)) * 1.08f
    val coverHeight = coverWidth * (9f / 16f)

    var inList by remember(anime.id) { mutableStateOf(MyListStore.isInList(context, anime.id)) }
    var rating by remember(anime.id) { mutableIntStateOf(MyListStore.getRating(context, anime.id)) }
    // Sound is on as soon as the trailer plays; the speaker button mutes it.
    var muted by rememberSaveable { mutableStateOf(false) }
    // Trailers come best-first; when one cannot be embedded or never starts, try the next.
    val trailerIds = trailers
    var trailerIndex by remember(trailerIds) { mutableIntStateOf(0) }
    var trailerPlaying by remember(trailerIds, trailerIndex) { mutableStateOf(false) }
    var showRating by remember { mutableStateOf(false) }
    var showSeasons by remember { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val trailerId = trailerIds.getOrNull(trailerIndex)

    val episodes = remember(detail, episodesFromState, newestFirst) {
        val list = episodesFromState.ifEmpty { detail?.episodes.orEmpty() }
        if (newestFirst) list.asReversed() else list
    }

    // Only episodes that have aired are listed; the next one gets a live countdown instead.
    val nextEp = detail?.nextEpisode ?: 0
    val nextAt = detail?.nextAiringAt ?: 0L
    val released = remember(episodes, nextEp, anime.status) {
        releasedEpisodes(episodes, nextEp, anime.status)
    }
    val nowSeconds by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis() / 1000
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ---- Hero: the trailer fills it, with rating, title and genres over the video ----
        item(key = "hero") {
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight).clipToBounds().background(Color.Black)) {
                AsyncImage(
                    model = anime.heroUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (trailerId != null && AppSettings.autoplayPreviews(context)) {
                    val alpha by animateFloatAsState(
                        if (trailerPlaying) 1f else 0.01f,
                        tween(500),
                        label = "trailer-fade",
                    )
                    androidx.compose.runtime.key(trailerId) {
                        LaunchedEffect(trailerId) {
                            // Give each video 12 seconds to start before moving on.
                            delay(12_000)
                            if (!trailerPlaying) trailerIndex++
                        }
                        TrailerPlayer(
                            videoId = trailerId,
                            muted = muted,
                            onPlaying = { trailerPlaying = true },
                            onFailed = { trailerIndex++ },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(coverWidth, coverHeight)
                                .alpha(alpha),
                        )
                    }
                }
                // Darkens only the lower part (behind rating and title) and melts into the page.
                Box(
                    modifier = Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.78f to Bg.copy(alpha = 0.72f),
                            1f to Bg,
                        ),
                    ),
                )
                // Swallows taps so the video behind never shows YouTube's own controls.
                Box(modifier = Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })

                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val rate = ratingLabel(anime.averageScore, detail?.ratingCount ?: 0)
                        if (rate != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.fluent_star_filled),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(rate, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (trailerId != null && trailerPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { muted = !muted },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (muted) R.drawable.fluent_speaker_mute_filled else R.drawable.fluent_speaker_filled,
                                    ),
                                    contentDescription = if (muted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        anime.displayTitle,
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val genres = genreLine(anime)
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(genres, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    }
                }
            }
        }

        // ---- Play, actions, synopsis ----
        item(key = "title") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(KiroPurple)
                        .clickable {
                            val first = released.firstOrNull()
                            if (first != null) onPlay(anime.id, first.number)
                            else Toast.makeText(context, "Not released yet", Toast.LENGTH_SHORT).show()
                        },
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
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
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
                Spacer(Modifier.height(14.dp))
                val text = synopsis(anime)
                if (text.isNotEmpty()) {
                    // Two lines with "More" sitting at the end of the second one.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text,
                            color = MutedText,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "More",
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Bg)
                                .clickable { onMore(anime.id) }
                                .padding(start = 14.dp),
                        )
                    }
                }
            }
        }

        // ---- Season dropdown ----
        val seasons = seasonsFromState
        if (seasons.size > 1) {
            item(key = "season") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(32.dp)
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
                        color = MutedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.fluent_arrow_sort_regular),
                        contentDescription = "Sort episodes",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (detail != null && released.isNotEmpty()) {
                    Text(
                        "${released.size} Episodes",
                        color = MutedText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.fluent_arrow_download_regular),
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (detail == null && loadingMore) {
            item(key = "loading") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    repeat(3) {
                        EpisodeSkeletonRow()
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }

        if (nextEp > 0) {
            item(key = "countdown") {
                val still = episodes.firstOrNull { it.number == nextEp }?.thumbnailUrl
                CountdownRow(
                    number = nextEp,
                    label = if (nextAt > 0) countdownLabel(nextAt - nowSeconds) else "Coming soon",
                    image = still ?: anime.heroUrl,
                )
            }
        }

        items(released, key = { it.number }, contentType = { "episode" }) { episode ->
            EpisodeRow(episode, fallbackImage = anime.heroUrl, onClick = { onPlay(anime.id, episode.number) })
        }

        item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
    }

    if (showRating) {
        RatingSheet(
            current = rating,
            onSelect = {
                rating = it
                MyListStore.setRating(context, anime, it)
            },
            onDismiss = { showRating = false },
        )
    }
    if (showSeasons && seasonsFromState.isNotEmpty()) {
        SeasonSheet(
            current = anime,
            entries = seasonsFromState,
            onSelect = { id -> if (id != anime.id) onOpenAnime(id) },
            onDismiss = { showSeasons = false },
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: Int, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun CountdownRow(number: Int, label: String, image: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ThumbBackground),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Episode $number", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
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
                .align(Alignment.Top)
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = episode.subtitle
            if (!sub.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(sub, color = MutedText, fontSize = 12.sp, maxLines = 1)
            }
            val plot = episode.overview
            if (!plot.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    plot,
                    color = MutedText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.fluent_arrow_download_regular),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
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
    entries: List<SeasonEntry>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
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
