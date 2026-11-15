@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.biaruuu.kiro.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.stream.StreamRepository
import com.biaruuu.kiro.data.stream.StreamRequest
import com.biaruuu.kiro.data.stream.StreamSettings
import com.biaruuu.kiro.data.stream.StreamSource
import com.biaruuu.kiro.ui.detail.DetailViewModel
import com.biaruuu.kiro.ui.detail.releasedEpisodes
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.delay
import java.util.Locale

private val PanelBackground = Color(0xFF161616)
private val TrackGrey = Color(0xFF5C5C5E)
private val MutedText = Color(0xFF8E8E93)

private const val NO_SOURCE = "No streaming source is set up yet"

private enum class Panel { Menu, Tracks, Episodes, Speed, Sources, SubStyle }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * Full-screen landscape video player (Media3 ExoPlayer) with custom controls: close, title and
 * more at the top, rewind / play-pause / forward in the middle, a seek bar with times, and the
 * Audio & Subtitles / Episodes / Next Episode / Lock actions underneath.
 */
@Composable
fun PlayerScreen(
    animeId: Int,
    startEpisode: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Same view model the details page uses, so the episode list is already loaded.
    val vm: DetailViewModel = viewModel(key = "detail-$animeId", factory = DetailViewModel.Factory(animeId))
    val ui by vm.state.collectAsState()
    val episodes = releasedEpisodes(
        ui.episodes.ifEmpty { ui.detail?.episodes.orEmpty() },
        ui.detail?.nextEpisode ?: 0,
        ui.base?.status,
    )

    var current by rememberSaveable { mutableIntStateOf(startEpisode) }
    val episode = episodes.firstOrNull { it.number == current }
    val title = episode?.heading ?: ui.base?.displayTitle.orEmpty()
    val nextEpisode = episodes.firstOrNull { it.number > current }

    val exo = remember { ExoPlayer.Builder(context).build() }
    // The player listener lives for the whole screen, so it must read the latest values.
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestCurrent by rememberUpdatedState(current)

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var speed by remember { mutableFloatStateOf(1f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var seeking by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    var sources by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var sourceIndex by remember { mutableIntStateOf(0) }
    var resolving by remember { mutableStateOf(true) }
    var userSubs by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var pendingSeek by remember { mutableLongStateOf(0L) }
    var subStyle by remember { mutableStateOf(SubtitleStyleStore.load(context)) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var showSourceDialog by remember { mutableStateOf(false) }
    val latestSources by rememberUpdatedState(sources)
    val latestIndex by rememberUpdatedState(sourceIndex)

    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val mime = when {
                name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            pendingSeek = exo.currentPosition
            userSubs = userSubs + (uri to mime)
        }
    }

    fun playEpisode(number: Int) {
        current = number
        position = 0
    }

    // 1. Ask every provider for this episode; the best (quality + speed) comes first.
    LaunchedEffect(current, retry) {
        error = null
        buffering = true
        resolving = true
        sources = emptyList()
        sourceIndex = 0
        pendingSeek = 0
        userSubs = emptyList()
        val request = StreamRequest(
            animeId,
            current,
            ui.base?.displayTitle.orEmpty(),
            ui.base?.titleEnglish,
            ui.base?.titleRomaji,
            ui.base?.seasonYear ?: 0,
        )
        val found = try {
            StreamRepository.resolve(context, request)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        sources = found
        resolving = false
        if (found.isEmpty()) {
            buffering = false
            error = NO_SOURCE
        }
    }

    // 2. Play the chosen source (also when switching source or adding a subtitle file).
    LaunchedEffect(sources, sourceIndex, userSubs) {
        val source = sources.getOrNull(sourceIndex) ?: return@LaunchedEffect
        val subtitles = source.subtitles.mapIndexed { i, t ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(t.url))
                .setMimeType(t.mimeType)
                .setLanguage(t.language)
                .setLabel(t.label)
                .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        } + userSubs.mapIndexed { i, (uri, mime) ->
            MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setLabel("My subtitles ${i + 1}")
                .setSelectionFlags(if (i == userSubs.lastIndex) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        error = null
        buffering = true
        exo.setMediaItem(
            MediaItem.Builder().setUri(source.url).setSubtitleConfigurations(subtitles).build(),
            pendingSeek,
        )
        pendingSeek = 0
        exo.prepare()
        exo.playWhenReady = true
    }

    // Keep the caption look in sync with the saved style.
    LaunchedEffect(playerView, subStyle) {
        playerView?.subtitleView?.applyKiroStyle(context, subStyle)
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (exo.duration > 0) duration = exo.duration
                if (state == Player.STATE_ENDED) {
                    val next = latestEpisodes.firstOrNull { it.number > latestCurrent }
                    if (next != null) playEpisode(next.number) else controlsVisible = true
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                // Instant failover: try the next-best source before giving up.
                if (latestIndex + 1 < latestSources.size) {
                    pendingSeek = exo.currentPosition
                    sourceIndex = latestIndex + 1
                } else {
                    error = "Couldn't play this video"
                }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Progress ticker.
    LaunchedEffect(exo) {
        while (true) {
            if (!seeking) position = exo.currentPosition
            if (exo.duration > 0) duration = exo.duration
            delay(250)
        }
    }

    // Pause when the app leaves the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Landscape + immersive while the player is open.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Hide the controls a few seconds after the last touch while playing.
    LaunchedEffect(controlsVisible, isPlaying, interaction, panel) {
        if (controlsVisible && isPlaying && panel == null) {
            delay(3500)
            controlsVisible = false
        }
    }

    BackHandler(enabled = panel != null) { panel = null }

    fun seekBy(deltaMs: Long) {
        val target = (exo.currentPosition + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        exo.seekTo(target)
        position = target
        interaction++
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    keepScreenOn = true
                    playerView = this
                }
            },
        )

        // Touch layer: tap shows / hides controls, double tap skips 10 seconds.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interaction++
                        },
                        onDoubleTap = { offset ->
                            if (!locked) seekBy(if (offset.x < size.width / 2f) -10_000L else 10_000L)
                        },
                    )
                },
        )

        if (buffering && error == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        error?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (message == NO_SOURCE) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add the address of a video source you have the right to use.",
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Add my source",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(KiroPurple)
                            .clickable { showSourceDialog = true }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "Retry",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(KiroPurple)
                        .clickable { retry++ }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }

        // ---- Locked: only the unlock button ----
        if (locked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp),
            ) {
                RoundButton(R.drawable.fluent_lock_closed_regular, "Unlock") {
                    locked = false
                    interaction++
                }
            }
        } else {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Controls(
                    title = title,
                    isPlaying = isPlaying,
                    buffering = buffering,
                    position = position,
                    duration = duration,
                    hasNext = nextEpisode != null,
                    onClose = onClose,
                    onMore = { panel = Panel.Menu },
                    onTogglePlay = {
                        if (exo.isPlaying) exo.pause() else exo.play()
                        interaction++
                    },
                    onBack10 = { seekBy(-10_000L) },
                    onForward10 = { seekBy(10_000L) },
                    onSeekStart = { seeking = true },
                    onSeekPreview = { position = it },
                    onSeekEnd = {
                        exo.seekTo(it)
                        position = it
                        seeking = false
                        interaction++
                    },
                    onTracks = { panel = Panel.Tracks },
                    onEpisodes = { panel = Panel.Episodes },
                    onNext = {
                        val next = nextEpisode
                        if (next != null) playEpisode(next.number)
                        else Toast.makeText(context, "No more episodes", Toast.LENGTH_SHORT).show()
                    },
                    onLock = {
                        locked = true
                        controlsVisible = true
                        interaction++
                    },
                )
            }
        }

        // ---- Side panels ----
        panel?.let { open ->
            SidePanel(
                title = when (open) {
                    Panel.Menu -> "Settings"
                    Panel.Sources -> "Source"
                    Panel.SubStyle -> "Subtitle style"
                    Panel.Tracks -> "Audio & Subtitles"
                    Panel.Episodes -> "Episodes"
                    Panel.Speed -> "Playback speed"
                },
                onClose = { panel = null },
            ) {
                when (open) {
                    Panel.Menu -> LazyColumn {
                        val activeSource = sources.getOrNull(sourceIndex)
                        item {
                            MenuRow(
                                "Source",
                                activeSource?.let { "${it.providerName} · ${it.qualityLabel}" } ?: "Finding sources…",
                            ) { panel = Panel.Sources }
                        }
                        item {
                            MenuRow(
                                "Subtitle style",
                                "${subStyle.font} · ${SubtitleOptions.sizes[subStyle.size].first} · ${SubtitleOptions.backgrounds[subStyle.background].first}",
                            ) { panel = Panel.SubStyle }
                        }
                        item {
                            MenuRow("Load subtitle file", ".srt · .vtt · .ass") {
                                panel = null
                                pickSubtitle.launch(arrayOf("*/*"))
                            }
                        }
                        item { MenuRow("Playback speed", if (speed == 1f) "Normal" else "${speed}x") { panel = Panel.Speed } }
                    }
                    Panel.Sources -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (resolving) {
                            Text("Finding the best source…", color = MutedText, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
                        }
                        sources.forEachIndexed { i, source ->
                            PanelRow(
                                label = "${source.providerName}" + (if (source.audio.isNotEmpty()) " · ${source.audio.replaceFirstChar { it.uppercase() }}" else "") + " · ${source.qualityLabel}" +
                                    (if (source.isHls) " · HLS" else "") +
                                    (if (i == 0) " · best" else "") +
                                    (if (source.latencyMs >= 0) " · ${source.latencyMs} ms" else ""),
                                selected = i == sourceIndex,
                            ) {
                                if (i != sourceIndex) {
                                    pendingSeek = exo.currentPosition
                                    sourceIndex = i
                                }
                                panel = null
                            }
                        }
                        MenuRow("Add my own source", "your server's address") { showSourceDialog = true }
                    }
                    Panel.SubStyle -> SubtitleStylePanel(subStyle) { new ->
                        subStyle = new
                        SubtitleStyleStore.save(context, new)
                    }
                    Panel.Tracks -> TrackList(exo, tracks) { panel = null }
                    Panel.Episodes -> LazyColumn {
                        items(episodes, key = { it.number }) { ep ->
                            PanelRow(
                                label = ep.heading,
                                selected = ep.number == current,
                            ) {
                                playEpisode(ep.number)
                                panel = null
                            }
                        }
                    }
                    Panel.Speed -> LazyColumn {
                        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { s ->
                            PanelRow(
                                label = if (s == 1f) "Normal" else "${s}x",
                                selected = s == speed,
                            ) {
                                speed = s
                                exo.setPlaybackSpeed(s)
                                panel = null
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSourceDialog) {
        var text by remember { mutableStateOf(StreamSettings.getRaw(context)) }
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            containerColor = PanelBackground,
            title = { Text("My sources", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "One address per line, for video you have the right to watch (your own server, a licensed " +
                            "service). Use {id} for the AniList id and {episode} for the episode number, e.g. " +
                            "https://my-server.example/{id}/{episode}.m3u8",
                        color = MutedText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    StreamSettings.setRaw(context, text)
                    showSourceDialog = false
                    panel = null
                    retry++
                }) { Text("Save", color = KiroPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("Cancel", color = Color.White) }
            },
        )
    }
}

@Composable
private fun Controls(
    title: String,
    isPlaying: Boolean,
    buffering: Boolean,
    position: Long,
    duration: Long,
    hasNext: Boolean,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onTogglePlay: () -> Unit,
    onBack10: () -> Unit,
    onForward10: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onTracks: () -> Unit,
    onEpisodes: () -> Unit,
    onNext: () -> Unit,
    onLock: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        val sidePad = 24.dp

        // Top bar: close, title, more.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad).padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.fluent_dismiss_regular),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onClose),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.fluent_more_vertical_regular),
                contentDescription = "More",
                tint = Color.White,
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onMore),
            )
        }

        // Centre: rewind 10, play / pause, forward 10.
        Row(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(maxWidth * 0.17f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.fluent_skip_back_10_regular),
                contentDescription = "Back 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onBack10),
            )
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                if (!buffering) {
                    if (isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White),
                                )
                            }
                        }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.fluent_play_filled),
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
            Icon(
                painter = painterResource(R.drawable.fluent_skip_forward_10_regular),
                contentDescription = "Forward 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onForward10),
            )
        }

        // Bottom: times + seek bar, then the four actions.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(position), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(14.dp))
                SeekBar(
                    position = position,
                    duration = duration,
                    onSeekStart = onSeekStart,
                    onSeekPreview = onSeekPreview,
                    onSeekEnd = onSeekEnd,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                Text(formatTime(duration), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Action(R.drawable.fluent_closed_caption_regular, "Audio & Subtitles", true, onTracks)
                Action(R.drawable.fluent_video_clip_multiple_regular, "Episodes", true, onEpisodes)
                Action(R.drawable.fluent_next_regular, "Next Episode", hasNext, onNext)
                Action(R.drawable.fluent_lock_open_regular, "Lock", true, onLock)
            }
        }
    }
}

@Composable
private fun Action(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Thin grey line, purple progress and a round purple thumb. Tap or drag to seek. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeekStart: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thumb = 20.dp
    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) {
                        onSeekEnd((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                    }
                }
            }
            .pointerInput(duration) {
                var last = 0L
                detectDragGestures(
                    onDragStart = { offset ->
                        onSeekStart()
                        last = if (duration > 0) (offset.x / size.width * duration).toLong().coerceIn(0L, duration) else 0L
                        onSeekPreview(last)
                    },
                    onDragEnd = { onSeekEnd(last) },
                    onDragCancel = { onSeekEnd(last) },
                    onDrag = { change, _ ->
                        change.consume()
                        if (duration > 0) {
                            last = (change.position.x / size.width * duration).toLong().coerceIn(0L, duration)
                            onSeekPreview(last)
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - thumb
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(TrackGrey))
        Box(modifier = Modifier.width(thumb / 2 + travel * fraction).height(2.dp).background(KiroPurple))
        Box(
            modifier = Modifier
                .padding(start = travel * fraction)
                .size(thumb)
                .clip(CircleShape)
                .background(KiroPurple),
        )
    }
}

@Composable
private fun SidePanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(PanelBackground)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(top = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.fluent_dismiss_regular),
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onClose),
                )
            }
            content()
        }
    }
}

@Composable
private fun PanelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(32.dp)) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.fluent_checkmark_regular),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFD0D0D4),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Audio tracks and subtitles the video offers, with an "Off" choice for subtitles. */
@Composable
private fun TrackList(exo: ExoPlayer, tracks: Tracks, onDone: () -> Unit) {
    data class Choice(val label: String, val type: Int, val group: Tracks.Group?, val index: Int, val selected: Boolean)

    val choices = remember(tracks) {
        val out = mutableListOf<Choice>()
        for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            val groups = tracks.groups.filter { it.type == type }
            if (groups.isEmpty()) continue
            if (type == C.TRACK_TYPE_TEXT) {
                out += Choice("Subtitles off", type, null, -1, groups.none { it.isSelected })
            }
            groups.forEach { group ->
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val name = format.label
                        ?: format.language?.let { Locale.forLanguageTag(it).displayLanguage }?.takeIf { it.isNotBlank() }
                        ?: (if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles") + " ${out.size + 1}"
                    out += Choice(name, type, group, i, group.isTrackSelected(i))
                }
            }
        }
        out.toList()
    }

    if (choices.isEmpty()) {
        Text(
            "This video has no other audio or subtitle tracks.",
            color = MutedText,
            fontSize = 13.sp,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    LazyColumn {
        items(choices) { c ->
            PanelRow(label = c.label, selected = c.selected) {
                val params = exo.trackSelectionParameters.buildUpon()
                if (c.group == null) {
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    params.setTrackTypeDisabled(c.type, false)
                    params.setOverrideForType(TrackSelectionOverride(c.group.mediaTrackGroup, c.index))
                }
                exo.trackSelectionParameters = params.build()
                onDone()
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Subtitle style as a list: FONT (each name shown in its own typeface), SIZE, BACKGROUND and TEXT
 * STYLE, with the chosen row ticked. Changes are saved and applied to the captions at once.
 */
@Composable
private fun SubtitleStylePanel(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit) {
    val context = LocalContext.current
    var customName by remember { mutableStateOf(SubtitleOptions.customName(context)) }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: uri.lastPathSegment.orEmpty()
            if (SubtitleOptions.importCustomFont(context, uri, name)) {
                customName = SubtitleOptions.customName(context)
                onChange(style.copy(font = SubtitleOptions.CUSTOM))
            } else {
                Toast.makeText(context, "That file isn't a usable font", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionLabel("FONT") }
        items(SubtitleOptions.fonts) { font ->
            val selected = style.font == font
            if (font == SubtitleOptions.CUSTOM) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (customName != null) onChange(style.copy(font = font)) else pickFont.launch(arrayOf("*/*"))
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                customName?.let { "Custom: $it" } ?: "Custom Font (.ttf / .otf)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                if (customName != null) "Tap to use it, or browse for another file"
                                else "Load your own font file (.ttf / .otf)",
                                color = MutedText,
                                fontSize = 11.sp,
                            )
                        }
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.fluent_checkmark_regular),
                                contentDescription = null,
                                tint = KiroPurple,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            "Browse",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { pickFont.launch(arrayOf("*/*")) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            } else {
                val typeface = remember(font) { SubtitleOptions.typeface(context, font) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(style.copy(font = font)) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            font,
                            color = if (selected) KiroPurple else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.fluent_checkmark_regular),
                                contentDescription = null,
                                tint = KiroPurple,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(
                        "The quick brown fox jumps over the lazy dog",
                        color = if (selected) KiroPurple.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.6f),
                        fontFamily = FontFamily(typeface),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item { SectionLabel("SIZE") }
        items(SubtitleOptions.sizes.indices.toList()) { i ->
            StyleRow(SubtitleOptions.sizes[i].first, style.size == i) { onChange(style.copy(size = i)) }
        }
        item { SectionLabel("BACKGROUND") }
        items(SubtitleOptions.backgrounds.indices.toList()) { i ->
            StyleRow(SubtitleOptions.backgrounds[i].first, style.background == i) { onChange(style.copy(background = i)) }
        }
        item { SectionLabel("TEXT STYLE") }
        items(SubtitleOptions.edges.indices.toList()) { i ->
            StyleRow(SubtitleOptions.edges[i].first, style.edge == i) { onChange(style.copy(edge = i)) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MutedText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun StyleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) KiroPurple else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.fluent_checkmark_regular),
                contentDescription = null,
                tint = KiroPurple,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
