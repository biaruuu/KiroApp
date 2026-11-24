package com.biaruuu.kiro.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import com.biaruuu.kiro.ui.theme.KiroAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF121212)
private val DividerColor = Color(0xFF333335)
private val MutedText = Color(0xFF8E8E93)
private val SheetBackground = Color(0xFF18181B)
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
    var showStatus by remember { mutableStateOf(false) }
    val cloudVersion by com.biaruuu.kiro.data.CloudSync.version.collectAsState()
    var status by remember(anime.id, cloudVersion) { mutableStateOf(com.biaruuu.kiro.data.ShowStatus.get(context, anime.id)) }
    var newestFirst by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val watchedVersion by com.biaruuu.kiro.data.WatchedStore.version.collectAsState()
    val watchedEps = remember(watchedVersion, anime.id) { com.biaruuu.kiro.data.WatchedStore.watchedEpisodes(context, anime.id) }
    val completedByMe = remember(watchedVersion, anime.id) { com.biaruuu.kiro.data.WatchedStore.isCompleted(context, anime.id) }
    var rewatch by remember { mutableStateOf<Int?>(null) }
    rewatch?.let { n ->
        com.biaruuu.kiro.ui.components.RewatchDialog(
            onNo = { rewatch = null },
            onYes = {
                rewatch = null
                onPlay(anime.id, n)
            },
        )
    }

    // Every trailer gets two chances (a slow start or a one-off error shouldn't hide it for good).
    val trailerId = if (trailerIds.isNotEmpty() && trailerIndex < trailerIds.size * 2) trailerIds[trailerIndex % trailerIds.size] else null

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
    val downloads by com.biaruuu.kiro.data.Downloads.items.collectAsState()
    val downloadScope = rememberCoroutineScope()
    fun startDownload(episode: EpisodeInfo) {
        com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Preparing download...", Toast.LENGTH_SHORT).show()
        downloadScope.launch {
            val request = com.biaruuu.kiro.data.stream.StreamRequest(
                anime.id, episode.number, anime.displayTitle, anime.titleEnglish, anime.titleRomaji,
                anime.seasonYear, detail?.malId ?: 0, anime.episodes,
            )
            val found = try {
                com.biaruuu.kiro.data.stream.StreamRepository.resolve(context, request)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val cap = com.biaruuu.kiro.data.AppSettings.downloadQuality(context).removeSuffix("p").toIntOrNull() ?: 0
            val source = (if (cap > 0) found.firstOrNull { it.height in 1..cap } else null) ?: found.firstOrNull()
            if (source == null) {
                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "This episode isn't available to download right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            com.biaruuu.kiro.data.Downloads.start(
                context, anime.id, anime.displayTitle, anime.posterUrl.orEmpty(), episode.number,
                episode.heading, episode.thumbnailUrl.orEmpty(), episode.overview.orEmpty(), source, cap,
            ) { msg -> com.biaruuu.kiro.data.Downloads.toast(context, msg) }
            com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Download started", Toast.LENGTH_SHORT).show()
        }
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
                com.biaruuu.kiro.ui.components.HdArtwork(anime, Modifier.fillMaxSize())
                if (trailerId != null && AppSettings.autoplayPreviews(context)) {
                    val alpha by animateFloatAsState(
                        if (trailerPlaying) 1f else 0.01f,
                        tween(500),
                        label = "trailer-fade",
                    )
                    androidx.compose.runtime.key(trailerId) {
                        LaunchedEffect(trailerId) {
                            // Give each video 25 seconds to start before moving on.
                            delay(25_000)
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
                                    .clip(RoundedCornerShape(8.dp))
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
                                Text(rate, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                        fontWeight = FontWeight.SemiBold,
                    )
                    val info = metaLine(anime, detail).replace(" | ", "  ·  ")
                    if (info.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(info, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    val genres = genreLine(anime)
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(genres, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Normal)
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
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KiroAccent)
                        .clickable {
                            val first = released.firstOrNull()
                            if (first != null) {
                                if (first.number in watchedEps) rewatch = first.number else onPlay(anime.id, first.number)
                            }
                            else com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Not released yet", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_play_filled),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Play", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (completedByMe) Arrangement.spacedBy(12.dp) else Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        label = "My List",
                        icon = if (inList) R.drawable.fluent_checkmark_regular else R.drawable.fluent_add_regular,
                        tint = if (inList) KiroAccent else Color(0xFF9A9AA2),
                    ) {
                        if (!com.biaruuu.kiro.data.CloudSync.allowed(context)) {
                            com.biaruuu.kiro.data.CloudSync.requireLogin(context)
                            return@ActionButton
                        }
                        inList = MyListStore.toggle(context, anime)
                        com.biaruuu.kiro.ui.i18n.I18nToast.make(
                            context,
                            if (inList) "Added to My List" else "Removed from My List",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    ActionButton(
                        label = status?.let { com.biaruuu.kiro.data.ShowStatus.label(it) } ?: "Status",
                        icon = R.drawable.fluent_inbox_regular,
                        tint = if (status != null) KiroAccent else Color(0xFF9A9AA2),
                    ) {
                        if (!com.biaruuu.kiro.data.CloudSync.allowed(context)) {
                            com.biaruuu.kiro.data.CloudSync.requireLogin(context)
                            return@ActionButton
                        }
                        showStatus = true
                    }
                    ActionButton(
                        label = if (rating > 0) (if (rating % 2 == 0) "${rating / 2}" else "${rating / 2}.5") else "Rate",
                        icon = if (rating > 0) R.drawable.fluent_star_filled else R.drawable.fluent_star_regular,
                        tint = Color.White,
                    ) { showRating = true }
                    ActionButton(label = "Share", icon = R.drawable.fluent_open_regular, tint = Color.White) {
                        // A picture card (poster, score, your rating) plus the text, for social apps.
                        com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Making your share card...", Toast.LENGTH_SHORT).show()
                        downloadScope.launch {
                            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                ShareCard.create(context, anime, rating)
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, shareText(anime))
                                if (file != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = android.content.ClipData.newRawUri("", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                } else {
                                    type = "text/plain"
                                }
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                    // Not a button: finishing an anime earns this shimmering medal automatically.
                    if (completedByMe) {
                        Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                            com.biaruuu.kiro.ui.components.AnimatedAsset(awardDesign, Modifier.height(38.dp).width(30.dp))
                        }
                        // The green "COMPLETED" rubber stamp, right of the medal.
                        Image(
                            painter = painterResource(R.drawable.completed_green),
                            contentDescription = "Completed",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(30.dp).weight(1f, fill = false),
                        )
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
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "More",
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
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
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1B1F))
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
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1B1F)).clickable { newestFirst = !newestFirst }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
            EpisodeRow(
                episode,
                fallbackImage = anime.heroUrl,
                download = downloads.firstOrNull { it.animeId == anime.id && it.episode == episode.number },
                onDownload = { startDownload(episode) },
                onRemoveDownload = { com.biaruuu.kiro.data.Downloads.remove(context, anime.id, episode.number) },
                watched = episode.number in watchedEps,
                onClick = {
                    if (episode.number in watchedEps) rewatch = episode.number else onPlay(anime.id, episode.number)
                },
            )
        }

        item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
    }

    if (showRating) {
        RatingSheet(
            current = rating,
            onSelect = {
                if (!com.biaruuu.kiro.data.CloudSync.allowed(context)) {
                    showRating = false
                    com.biaruuu.kiro.data.CloudSync.requireLogin(context)
                    return@RatingSheet
                }
                rating = it
                MyListStore.setRating(context, anime, it)
            },
            onDismiss = { showRating = false },
        )
    }
    if (showStatus) {
        StatusSheet(
            current = status,
            onPick = { picked ->
                com.biaruuu.kiro.data.ShowStatus.set(context, anime, picked)
                status = picked
                inList = MyListStore.isInList(context, anime.id)
            },
            onDismiss = { showStatus = false },
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
        modifier = Modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFB4B4BA), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private val awardDesign = com.biaruuu.kiro.data.Design("award_medal", "badge", "Award", asset = "badges/award_medal.png")

@Composable
private fun CountdownRow(number: Int, label: String, image: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
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
            Text("Episode $number", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeInfo,
    fallbackImage: String?,
    download: com.biaruuu.kiro.data.Downloads.Item?,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    watched: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Top)
                .width(128.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
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
            if (watched) com.biaruuu.kiro.ui.components.WatchedBadge(Modifier.align(Alignment.TopStart))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                episode.heading,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
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
        Spacer(Modifier.width(4.dp))
        DownloadButton(download, onDownload, onRemoveDownload)
    }
}

/** Download icon for an episode: tap to save, shows progress while saving, a tick once saved. */
@Composable
private fun DownloadButton(item: com.biaruuu.kiro.data.Downloads.Item?, onStart: () -> Unit, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val done = item?.completed == true
    val running = item != null && item.active
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable {
                when {
                    done -> confirm = true
                    running -> onRemove()
                    else -> onStart()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            done -> Icon(painterResource(R.drawable.fluent_checkmark_regular), contentDescription = "Downloaded", tint = com.biaruuu.kiro.ui.theme.KiroAccent, modifier = Modifier.size(24.dp))
            running -> {
                val p = (item!!.percent / 100f).coerceIn(0f, 1f)
                if (p > 0f) {
                    androidx.compose.material3.CircularProgressIndicator(progress = { p }, color = Color.White, trackColor = Color(0xFF3A3A3D), strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                } else {
                    com.biaruuu.kiro.ui.components.KiroSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                }
                Text(if (p > 0f) "${(p * 100).toInt()}" else "", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
            else -> Icon(painterResource(R.drawable.fluent_arrow_download_regular), contentDescription = "Download", tint = if (item?.failed == true) Color(0xFFFF6B6B) else Color.White, modifier = Modifier.size(24.dp))
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Color(0xFF18181B),
            title = { Text("Remove download?", color = Color.White) },
            text = { Text("This episode will be deleted from your phone.", color = MutedText) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false; onRemove() }) { Text("Remove", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Cancel", color = Color.White) }
            },
        )
    }
}

private val RatingLabels = listOf(
    "Disappointing :(", "Hmm.. Not impressed", "Below expectations", "Nothing special", "Had potential",
    "Enjoyable", "Entertaining and engaging", "Highly recommended :)", "Absolutely fantastic!", "A MASTERPIECE",
)

/** Laftel-style rating: tap the left half of a star for .5, the right half for a whole star. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingSheet(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var picked by remember { mutableIntStateOf(current) } // half stars, 0..10
    val starSize = 34.dp
    val pitch = 40.dp
    val pitchPx = with(androidx.compose.ui.platform.LocalDensity.current) { pitch.toPx() }
    val starPx = with(androidx.compose.ui.platform.LocalDensity.current) { starSize.toPx() }

    fun halvesAt(x: Float): Int {
        val index = (x / pitchPx).toInt().coerceIn(0, 4)
        val within = (x - index * pitchPx).coerceIn(0f, starPx)
        return index * 2 + if (within < starPx / 2f) 1 else 2
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
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(48.dp)
                        .clickable { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_dismiss_regular),
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (picked % 2 == 0) "${picked / 2}" else "${picked / 2}.5",
                color = if (picked > 0) Color.White else Color(0xFF8E8E93),
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (picked > 0) RatingLabels[picked - 1] else "Tap to rate",
                color = if (picked > 0) Color(0xFFE6E6E8) else Color(0xFF9A9A9F),
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier
                    .width(pitch * 4 + starSize)
                    .height(starSize)
                    .pointerInput(Unit) {
                        detectTapGestures { picked = halvesAt(it.x) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            picked = halvesAt(change.position.x.coerceAtLeast(0f)).coerceAtLeast(1)
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(pitch - starSize),
            ) {
                for (star in 0 until 5) {
                    val fill = ((picked - star * 2).coerceIn(0, 2)) / 2f
                    Box(Modifier.size(starSize)) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_star_filled),
                            contentDescription = null,
                            tint = Color(0xFF4D4D50),
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (fill > 0f) {
                            Icon(
                                painter = painterResource(R.drawable.fluent_star_filled),
                                contentDescription = null,
                                tint = KiroAccent,
                                modifier = Modifier.fillMaxSize().drawWithContent {
                                    clipRect(right = size.width * fill) { this@drawWithContent.drawContent() }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(45.dp))
            val enabled = picked > 0
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) KiroAccent else Color(0xFF2E2E30))
                    .clickable(enabled = enabled) {
                        onSelect(picked)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Confirm",
                    color = if (enabled) Color.Black else Color(0xFF5E5E63),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
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
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSheet(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
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
        Column(modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            val options: List<String?> = com.biaruuu.kiro.data.ShowStatus.ALL + listOf(null)
            options.forEach { opt ->
                if (opt == null && current == null) return@forEach
                val selected = opt == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onPick(opt)
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
                                tint = KiroAccent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Text(
                        if (opt == null) "Clear status" else com.biaruuu.kiro.data.ShowStatus.label(opt),
                        color = if (selected) Color.White else Color(0xFFD0D0D4),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
