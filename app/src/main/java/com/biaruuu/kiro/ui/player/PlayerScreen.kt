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
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import com.biaruuu.kiro.data.EpisodeInfo
import com.biaruuu.kiro.data.WatchHistory
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.widthIn
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
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.biaruuu.kiro.data.stream.StreamSource
import com.biaruuu.kiro.ui.detail.DetailViewModel
import com.biaruuu.kiro.ui.detail.releasedEpisodes
import com.biaruuu.kiro.ui.theme.KiroAccent
import kotlinx.coroutines.delay
import java.util.Locale

private val PanelBackground = Color(0xFF18181B)
private val TrackGrey = Color(0xFF3A3A3F)
private val MutedText = Color(0xFF8E8E93)

private const val NO_SOURCE = "This episode isn't available right now"

private enum class Panel { Menu, Tracks, Episodes, Speed, Quality, SubStyle, Boost, Hdr, AutoSkip, Party, Members }

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
    partyCode: String? = null,
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
    val title = episode?.heading ?: ui.base?.displayTitle.orEmpty().ifEmpty { com.biaruuu.kiro.data.Downloads.item(animeId, current)?.title.orEmpty() }
    val nextEpisode = episodes.firstOrNull { it.number > current }

    val exo = remember {
        // Phone decoders first; the bundled FFmpeg decoders only step in for audio the phone
        // can't play (AC3, E-AC3, DTS, FLAC...), so a stream is never silent.
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // Reads downloaded episodes from the offline cache; everything else comes from the network.
        // Auto adapts to the real connection speed and stops at 1080p so it never stalls trying to start
        // a huge 4K stream; pick 1440p / 2160p by hand in Quality to play those (every size is listed).
        val selector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, AUTO_MAX_HEIGHT)
                .clearViewportSizeConstraints()
                .apply {
                    // The subtitle choice from last time: off, or a language.
                    when (val sp = com.biaruuu.kiro.data.AppSettings.subtitlePref(context)) {
                        "" -> Unit
                        "off" -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        else -> setPreferredTextLanguage(sp)
                    }
                }
                .build()
        }
        // Start playing after 1.5 s of data, recover quickly, and keep a deep buffer ahead on good Wi-Fi.
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val meter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()
        ExoPlayer.Builder(context, renderers)
            .setTrackSelector(selector)
            .setLoadControl(load)
            .setBandwidthMeter(meter)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(com.biaruuu.kiro.data.Downloads.dataSourceFactory(context)))
            .build()
    }
    // Choices from last time: playback speed and subtitles.
    LaunchedEffect(exo) { exo.setPlaybackSpeed(com.biaruuu.kiro.data.AppSettings.playSpeed(context)) }
    // The player listener lives for the whole screen, so it must read the latest values.
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestCurrent by rememberUpdatedState(current)
    var mediaEpisode by remember { mutableIntStateOf(0) } // the episode that is really loaded in the player
    val latestUi by rememberUpdatedState(ui)
    val latestMediaEpisode = rememberUpdatedState(mediaEpisode)

    // Remembers where you stopped so Home can offer "Continue Watching".
    fun saveProgress() {
        val ep = latestMediaEpisode.value
        val dur = exo.duration
        val pos = exo.currentPosition
        if (ep <= 0 || dur <= 0 || pos <= 0) return
        val base = latestUi.base ?: return
        com.biaruuu.kiro.data.Achievements.recordWatch(context)
        val info = latestEpisodes.firstOrNull { it.number == ep }
        if (pos >= dur * 0.93) {
            val total = if (base.episodes > 0) base.episodes
                else if ("FINISHED".equals(base.status, true)) (latestEpisodes.maxOfOrNull { it.number } ?: 0) else 0
            com.biaruuu.kiro.data.WatchedStore.markWatched(context, animeId, base.displayTitle, base.posterUrl.orEmpty(), ep, total)
            // Finished: carry on from the next episode, or drop it if that was the last one.
            val next = latestEpisodes.firstOrNull { it.number > ep }
            if (next != null) {
                WatchHistory.save(
                    context,
                    WatchHistory.Entry(animeId, next.number, 0L, 0L, base.displayTitle, base.heroUrl, next.thumbnailUrl, System.currentTimeMillis()),
                )
            } else {
                WatchHistory.remove(context, animeId)
            }
        } else {
            WatchHistory.save(
                context,
                WatchHistory.Entry(animeId, ep, pos, dur, base.displayTitle, base.heroUrl, info?.thumbnailUrl, System.currentTimeMillis()),
            )
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    // What went wrong on this episode, for the automatic broken-episode report.
    var failLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var stalled by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var speed by remember { mutableFloatStateOf(com.biaruuu.kiro.data.AppSettings.playSpeed(context)) }

    // Watch time for the stats page, streak and XP: counted while the video really plays.
    val latestPlaying by rememberUpdatedState(isPlaying && !buffering && error == null)
    val latestSpeed by rememberUpdatedState(speed)
    LaunchedEffect(Unit) {
        var acc = 0f
        try {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (latestPlaying) {
                    acc += 5f * latestSpeed
                    if (acc >= 30f) {
                        com.biaruuu.kiro.data.WatchStats.addWatchTime(context, acc.toInt(), latestUi.base?.genres.orEmpty())
                        acc = 0f
                    }
                }
            }
        } finally {
            if (acc >= 1f) com.biaruuu.kiro.data.WatchStats.addWatchTime(context, acc.toInt(), latestUi.base?.genres.orEmpty())
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var seeking by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    // Intro / outro / recap times for this exact episode (Aniskip), looked up once the real length is known.
    var autoSkip by remember { mutableStateOf(com.biaruuu.kiro.data.AppSettings.autoSkip(context)) }
    val handledSkips = remember(current) { mutableStateListOf<String>() }
    val autoProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var skipSegments by remember { mutableStateOf<List<com.biaruuu.kiro.data.SkipTimes.Segment>>(emptyList()) }
    val skipMalId = ui.detail?.malId ?: 0
    val hasDuration = duration > 0
    // Looked up once per episode when the length is known; the length wobbling afterwards (streams
    // refine it as they load) must not clear the markers or the skip button.
    LaunchedEffect(current, skipMalId, hasDuration) {
        if (!hasDuration) { skipSegments = emptyList(); return@LaunchedEffect }
        val found = com.biaruuu.kiro.data.SkipTimes.load(skipMalId, current, duration / 1000.0)
        skipSegments = found
    }

    var sources by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var sourceIndex by remember { mutableIntStateOf(0) }
    var resolving by remember { mutableStateOf(true) }
    var pendingSeek by remember { mutableLongStateOf(0L) }
    val prefs = remember { context.getSharedPreferences("kiro_user_data", android.content.Context.MODE_PRIVATE) }
    var boostDb by remember { mutableIntStateOf(prefs.getInt("audio_boost_db", 0)) }
    var hdrOn by remember { mutableStateOf(prefs.getBoolean("hdr_tunneling", false)) }
    var subStyle by remember { mutableStateOf(SubtitleStyleStore.load(context)) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var quality by remember { mutableIntStateOf(0) } // 0 = Auto, otherwise a video height
    val latestSources by rememberUpdatedState(sources)
    val latestIndex by rememberUpdatedState(sourceIndex)

    val watchedVersion by com.biaruuu.kiro.data.WatchedStore.version.collectAsState()
    val watchedEps = remember(watchedVersion, animeId) { com.biaruuu.kiro.data.WatchedStore.watchedEpisodes(context, animeId) }
    var rewatchEp by remember { mutableStateOf<Int?>(null) }
    fun playEpisode(number: Int) {
        if (number == current) return
        saveProgress() // keep the place in the episode being left
        current = number
        position = 0
    }

    rewatchEp?.let { n ->
        com.biaruuu.kiro.ui.components.RewatchDialog(
            onNo = { rewatchEp = null },
            onYes = {
                rewatchEp = null
                playEpisode(n)
                panel = null
            },
        )
    }

    // ---- Watch party: the host's player drives everyone else's ----
    val party by com.biaruuu.kiro.data.WatchParty.session.collectAsState()
    val partyRemote by com.biaruuu.kiro.data.WatchParty.remote.collectAsState()
    val partyMembers by com.biaruuu.kiro.data.WatchParty.members.collectAsState()
    val partyMessage by com.biaruuu.kiro.data.WatchParty.message.collectAsState()
    val isGuest = party?.host == false
    fun guestBlocked(): Boolean {
        if (isGuest) com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Only the host controls the watch party", Toast.LENGTH_SHORT).show()
        return isGuest
    }
    LaunchedEffect(partyCode) {
        if (partyCode != null) com.biaruuu.kiro.data.WatchParty.enter(context, partyCode)
    }
    DisposableEffect(Unit) { onDispose { com.biaruuu.kiro.data.WatchParty.leave() } }
    LaunchedEffect(partyMessage) {
        partyMessage?.let {
            com.biaruuu.kiro.ui.i18n.I18nToast.make(context, it, Toast.LENGTH_LONG).show()
            com.biaruuu.kiro.data.WatchParty.message.value = null
        }
    }
    // The host's player reports every change at once (play, pause, seek, buffering) ...
    val partyHost = party?.host == true
    DisposableEffect(exo, partyHost) {
        fun pushNow() {
            if (!partyHost) return
            com.biaruuu.kiro.data.WatchParty.push(
                animeId, latestCurrent, exo.currentPosition,
                exo.playWhenReady && exo.playbackState == Player.STATE_READY,
            )
        }
        val l = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = pushNow()
            override fun onPlaybackStateChanged(playbackState: Int) = pushNow()
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) pushNow()
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    // ... and sends its position every 1.5 seconds while playing, so nobody drifts.
    LaunchedEffect(party?.code, partyHost, current) {
        if (!partyHost) return@LaunchedEffect
        com.biaruuu.kiro.data.WatchParty.push(animeId, current, exo.currentPosition, exo.playWhenReady && exo.playbackState == Player.STATE_READY)
        while (true) {
            delay(1500)
            if (exo.isPlaying) com.biaruuu.kiro.data.WatchParty.push(animeId, latestCurrent, exo.currentPosition, true)
        }
    }
    // Guest: match the host's episode, play / pause and position. This runs the instant an update
    // arrives and again every 0.25 s; the time the update spent travelling is added to the host's
    // position, so a pause or a skip lands within a fraction of a second.
    val latestParty by rememberUpdatedState(party)
    val latestRemote by rememberUpdatedState(partyRemote)
    val lastSeek = remember { longArrayOf(0L) }
    fun syncGuest() {
        val p = latestParty ?: return
        val r = latestRemote ?: return
        if (p.host) return
        if (r.episode != latestCurrent) {
            playEpisode(r.episode)
            return
        }
        if (exo.playWhenReady != r.playing) exo.playWhenReady = r.playing
        if (exo.playbackState == Player.STATE_IDLE || exo.duration <= 0L) return
        val travelled = System.currentTimeMillis() - r.sentAt
        // Phone clocks can differ; only trust the host's timestamp when it is plausible.
        val elapsed = if (r.sentAt > 0L && travelled in 0L..6000L) travelled
        else android.os.SystemClock.elapsedRealtime() - r.receivedAt
        val target = r.positionMs + if (r.playing) elapsed else 0L
        val now = android.os.SystemClock.elapsedRealtime()
        val allowed = if (r.playing) 700L else 250L
        if (kotlin.math.abs(exo.currentPosition - target) > allowed && now - lastSeek[0] > 800L) {
            lastSeek[0] = now
            exo.seekTo(target.coerceIn(0L, exo.duration))
        }
    }
    LaunchedEffect(partyRemote, hasDuration) { syncGuest() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            syncGuest()
        }
    }

    // Sub and dub come as separate streams; this jumps to the best one of the other kind.
    fun switchAudio(language: String) {
        val idx = latestSources.indexOfFirst { audioLanguage(it) == language }
        if (idx < 0 || idx == latestIndex) return
        prefs.edit().putBoolean("audio_dub", language != "Japanese").apply()
        pendingSeek = exo.currentPosition
        sourceIndex = idx
    }

    // 1. Ask every provider for this episode; the best (quality + speed) comes first.
    LaunchedEffect(current, retry) {
        // Stop the old episode at once and wait a moment, so tapping Next over and over only
        // ever looks up the episode you land on (and never hammers the streaming server).
        exo.stop()
        exo.clearMediaItems()
        duration = 0
        error = null
        failLog = emptyList()
        buffering = true
        resolving = true
        sources = emptyList()
        sourceIndex = 0
        delay(350)
        pendingSeek = WatchHistory.positionFor(context, animeId, current)
        val request = StreamRequest(
            animeId,
            current,
            ui.base?.displayTitle.orEmpty(),
            ui.base?.titleEnglish,
            ui.base?.titleRomaji,
            ui.base?.seasonYear ?: 0,
            ui.detail?.malId ?: 0,
            ui.base?.episodes ?: 0,
        )
        // A downloaded episode plays from the phone, with no lookup and no network.
        val offline = com.biaruuu.kiro.data.Downloads.playbackSource(context, animeId, current)
        val found = if (offline != null) listOf(offline) else try {
            StreamRepository.resolve(context, request)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        sources = found
        // Start in the language chosen last time, when this episode has it.
        if (prefs.getBoolean("audio_dub", false)) {
            val dubIdx = found.indexOfFirst { it.isDub && audioLanguage(it) == "English" }
                .takeIf { it >= 0 } ?: found.indexOfFirst { it.isDub }
            if (dubIdx > 0) sourceIndex = dubIdx
        }
        resolving = false
        if (found.isEmpty()) {
            buffering = false
            error = NO_SOURCE
        }
    }

    // 2. Play the chosen source (also when switching source or adding a subtitle file).
    LaunchedEffect(sources, sourceIndex) {
        val source = sources.getOrNull(sourceIndex) ?: return@LaunchedEffect
        val subtitles = source.subtitles.mapIndexed { i, t ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(t.url))
                .setMimeType(t.mimeType)
                .setLanguage(t.language)
                .setLabel(t.label)
                .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        // Some servers only answer with their own Referer / Origin; the player adds them.
        com.biaruuu.kiro.data.stream.StreamHeaders.activate(com.biaruuu.kiro.data.stream.StreamHeaders.Slot.Playback, source.url, source.headers)
        error = null
        buffering = true
        // The quality picked last time (the best one at or below it when this stream has no such size).
        quality = com.biaruuu.kiro.data.AppSettings.qualityPref(context)
        applyQuality(exo, quality)
        exo.setMediaItem(
            MediaItem.Builder()
                .setUri(source.url)
                // The API's proxy address has no ".m3u8" ending, so ExoPlayer can't guess it is HLS.
                .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .setSubtitleConfigurations(subtitles)
                .build(),
            pendingSeek,
        )
        pendingSeek = 0
        mediaEpisode = current
        exo.prepare()
        // A guest waits for the host: it only plays while the host is playing.
        val guestNow = party?.host == false || (partyCode != null && party == null)
        exo.playWhenReady = if (guestNow) partyRemote?.playing == true else true
    }

    // Audio booster: Android's LoudnessEnhancer on the player's audio session (+10 / +20 dB).
    var audioSession by remember { mutableIntStateOf(exo.audioSessionId) }
    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioSession = audioSessionId
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }
    DisposableEffect(audioSession, boostDb) {
        prefs.edit().putInt("audio_boost_db", boostDb).apply()
        val enhancer = if (boostDb > 0 && audioSession != C.AUDIO_SESSION_ID_UNSET) {
            try {
                android.media.audiofx.LoudnessEnhancer(audioSession).apply {
                    setTargetGain(boostDb * 100) // millibels
                    enabled = true
                }
            } catch (_: Exception) {
                null
            }
        } else null
        onDispose { enhancer?.release() }
    }

    // Lets the developer's admin panel show what is being watched right now.
    val watchTitle = ui.base?.displayTitle.orEmpty()
    DisposableEffect(animeId, current, watchTitle) {
        com.biaruuu.kiro.data.Presence.watching.value = com.biaruuu.kiro.data.Presence.Watching(animeId, watchTitle, current)
        onDispose { com.biaruuu.kiro.data.Presence.watching.value = null }
    }

    // HDR / Dolby Vision: hardware tunneling plus an HDR window, both left to the phone's decoder.
    DisposableEffect(hdrOn) {
        prefs.edit().putBoolean("hdr_tunneling", hdrOn).apply()
        (exo.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector)?.let { sel ->
            sel.parameters = sel.buildUponParameters().setTunnelingEnabled(hdrOn).build()
        }
        val window = (context as? android.app.Activity)?.window
        if (hdrOn && android.os.Build.VERSION.SDK_INT >= 26) {
            window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_HDR
        }
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
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
                // An error from an episode we already left must not mark the new one as broken.
                if (resolving) return
                val http = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                failLog = failLog + "${latestSources.getOrNull(latestIndex)?.providerName ?: "?"}: ${e.errorCodeName}" +
                    (if (http != null) " (HTTP $http)" else "")
                // Instant failover: try the next-best source before giving up.
                if (latestIndex + 1 < latestSources.size) {
                    pendingSeek = exo.currentPosition
                    sourceIndex = latestIndex + 1
                } else {
                    error = "Couldn't play this video (${e.errorCodeName})"
                }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        exo.addListener(listener)
        onDispose {
            saveProgress()
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Buffering for 25 s with no error is a broken episode too.
    LaunchedEffect(buffering, resolving, error, sourceIndex, current, retry) {
        stalled = false
        if (buffering && !resolving && error == null) {
            delay(25_000)
            stalled = true
        }
    }

    // Progress ticker.
    LaunchedEffect(exo) {
        var ticks = 0
        while (true) {
            if (!seeking) position = exo.currentPosition
            if (exo.duration > 0) duration = exo.duration
            if (++ticks % 20 == 0 && exo.isPlaying) saveProgress() // every 5 s
            delay(250)
        }
    }

    // Pause when the app leaves the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveProgress()
                // Picture in picture keeps playing; only a normal leave pauses.
                if (!PipState.inPip.value) exo.pause()
            } else if (event == Lifecycle.Event.ON_STOP) {
                exo.pause() // the small window was closed or the screen turned off
            }
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

    // Picture in picture: allowed while a video plays; controls are hidden in the small window.
    val inPip by PipState.inPip.collectAsState()
    DisposableEffect(isPlaying) {
        PipState.canEnter = isPlaying
        onDispose { PipState.canEnter = false }
    }
    LaunchedEffect(inPip) {
        if (inPip) {
            controlsVisible = false
            panel = null
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
        if (guestBlocked()) return
        val target = (exo.currentPosition + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        exo.seekTo(target)
        position = target
        if (party?.host == true) com.biaruuu.kiro.data.WatchParty.push(animeId, current, target, exo.isPlaying)
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

        // The spinner is drawn here only while the controls are hidden; with the controls up it
        // sits inside them (below), so the dimming layer never turns it grey.
        if (buffering && error == null) {
            if (!controlsVisible) {
                com.biaruuu.kiro.ui.components.KiroSpinner(Modifier.align(Alignment.Center), size = 48.dp)
            }
            if (resolving) {
                Text(
                    "Finding the best source...",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center).padding(top = 84.dp),
                )
            }
        }

        // A report is only offered when the episode really is broken, and it carries the cause.
        val reportKey = "$animeId:$current"
        fun reportBroken() {
            val src = sources.getOrNull(sourceIndex)
            val cause = when {
                error == NO_SOURCE -> "No provider returned a stream"
                stalled -> "Stuck buffering for over 25 s on ${src?.providerName ?: "?"}"
                else -> error ?: "Playback failed"
            }
            reported = reported + reportKey
            com.biaruuu.kiro.data.BrokenReport.send(
                context,
                com.biaruuu.kiro.data.BrokenReport.Info(
                    animeId = animeId,
                    title = title,
                    episode = current,
                    provider = src?.providerName ?: "none found",
                    quality = if (quality == 0) "Auto" else "${quality}p",
                    triedProviders = sources.map { it.providerName }.distinct(),
                    problem = cause + if (failLog.isNotEmpty()) ". Errors: " + failLog.joinToString("; ") else "",
                ),
            ) { sent ->
                com.biaruuu.kiro.ui.i18n.I18nToast.make(
                    context,
                    if (sent) "Thanks! Report sent." else "Opening your email app to send the report",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        val alreadyReported = reportKey in reported

        error?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Retry",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(KiroAccent)
                            .clickable { retry++ }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Text(
                        if (alreadyReported) "Reported" else "Report",
                        color = if (alreadyReported) Color(0xFF9A9A9F) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3A3A3E))
                            .clickable(enabled = !alreadyReported) { reportBroken() }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (stalled && error == null) {
            Text(
                if (alreadyReported) "Reported" else "Stuck loading? Report this episode",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC1E1E22))
                    .clickable(enabled = !alreadyReported) { reportBroken() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ---- Skip Intro / Outro / Recap while inside one of the segments ----
        if (!locked && error == null) {
            val posSec = position / 1000.0
            val active = skipSegments.firstOrNull { posSec >= it.start - 0.5 && posSec < it.end - 1.0 }
            val activeKey = active?.let { "${it.type}:${it.start}" }
            val skipNow = { seg: com.biaruuu.kiro.data.SkipTimes.Segment ->
                val target = (seg.end * 1000).toLong().coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
                exo.seekTo(target)
                position = target
                handledSkips.add("${seg.type}:${seg.start}")
            }
            // Auto skip: a short, visible countdown, then jump. Each part is skipped once, so
            // going back into it with the seek bar is never fought over.
            LaunchedEffect(activeKey, autoSkip) {
                autoProgress.snapTo(0f)
                if (active != null && autoSkip && activeKey !in handledSkips) {
                    autoProgress.animateTo(1f, androidx.compose.animation.core.tween(2500, easing = androidx.compose.animation.core.LinearEasing))
                    skipNow(active)
                }
            }
            val counting = active != null && autoSkip && activeKey !in handledSkips
            val bottomPad by androidx.compose.animation.core.animateDpAsState(
                if (controlsVisible) 118.dp else 40.dp,
                androidx.compose.animation.core.tween(220),
                label = "skip-bottom",
            )
            AnimatedVisibility(
                visible = active != null,
                enter = fadeIn() + androidx.compose.animation.slideInHorizontally { it / 2 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = bottomPad),
            ) {
                val seg = active
                val shape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(shape)
                        .background(Color.White)
                        .drawBehind {
                            if (counting) {
                                drawRect(KiroAccent.copy(alpha = 0.35f), size = androidx.compose.ui.geometry.Size(size.width * autoProgress.value, size.height))
                            }
                        }
                        .clickable { if (seg != null) skipNow(seg) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (counting) "Skipping ${seg?.noun?.lowercase() ?: ""}..." else (seg?.label ?: "Skip"),
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.fluent_next_regular),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // ---- Next episode prompt: appears 15 seconds before the end and fills as the end nears ----
        run {
            val next = nextEpisode
            var dismissed by remember(current) { mutableStateOf(false) }
            val remaining = duration - position
            val show = !locked && error == null && next != null && !dismissed && duration > 60_000L &&
                position > 0 && remaining in 0..15_000L
            val posSec = position / 1000.0
            val skipShowing = skipSegments.any { posSec >= it.start - 0.5 && posSec < it.end - 1.0 }
            val bottomPad by androidx.compose.animation.core.animateDpAsState(
                (if (controlsVisible) 118.dp else 40.dp) + (if (skipShowing) 56.dp else 0.dp),
                androidx.compose.animation.core.tween(220),
                label = "next-bottom",
            )
            AnimatedVisibility(
                visible = show,
                enter = fadeIn() + androidx.compose.animation.slideInHorizontally { it / 2 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = bottomPad),
            ) {
                val fill = (1f - remaining / 15_000f).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { dismissed = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_dismiss_regular),
                            contentDescription = "Dismiss",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .drawBehind {
                                drawRect(KiroAccent.copy(alpha = 0.35f), size = androidx.compose.ui.geometry.Size(size.width * fill, size.height))
                            }
                            .clickable { if (next != null && !guestBlocked()) playEpisode(next.number) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Next Episode${if (next != null) " ${next.number}" else ""}",
                                color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.fluent_next_regular),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
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
                visible = controlsVisible && !inPip,
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
                    skipSegments = skipSegments,
                    onClose = onClose,
                    onMore = { panel = Panel.Menu },
                    onTogglePlay = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (!guestBlocked()) {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            interaction++
                        }
                    },
                    onBack10 = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        seekBy(-10_000L)
                    },
                    onForward10 = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        seekBy(10_000L)
                    },
                    onSeekStart = { seeking = true },
                    onSeekPreview = { position = it },
                    onSeekEnd = {
                        if (!guestBlocked()) {
                            exo.seekTo(it)
                            position = it
                            if (party?.host == true) com.biaruuu.kiro.data.WatchParty.push(animeId, current, it, exo.isPlaying)
                        }
                        seeking = false
                        interaction++
                    },
                    onTracks = { panel = Panel.Tracks },
                    onEpisodes = { panel = Panel.Episodes },
                    onNext = {
                        val next = nextEpisode
                        if (guestBlocked()) Unit
                        else if (next != null) playEpisode(next.number)
                        else com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "No more episodes", Toast.LENGTH_SHORT).show()
                    },
                    onLock = {
                        locked = true
                        controlsVisible = true
                        interaction++
                    },
                )
            }
        }

        // A chip with the avatars of everyone in the watch party; tap it to see who they are.
        if (party != null && partyMembers.isNotEmpty() && !inPip && panel == null) {
            PartyChip(
                partyMembers,
                Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 20.dp),
            ) { panel = Panel.Members }
        }

        // ---- Side panels ----
        panel?.let { open ->
            if (open == Panel.Tracks) TracksDialog(
                exo, tracks,
                languages = sources.map { audioLanguage(it) }.distinct().sortedBy { if (it == "Japanese") 0 else 1 },
                currentLanguage = sources.getOrNull(sourceIndex)?.let { audioLanguage(it) } ?: "Japanese",
                onPickLanguage = { language -> switchAudio(language); panel = null },
            ) { panel = null } else if (open == Panel.Episodes) EpisodeShelf(
                heading = ui.base?.displayTitle.orEmpty(),
                episodes = episodes,
                current = current,
                watched = watchedEps,
                onClose = { panel = null },
            ) { ep ->
                if (guestBlocked()) {
                    // Guests follow the host.
                } else if (ep.number in watchedEps && ep.number != current) {
                    rewatchEp = ep.number
                } else {
                    playEpisode(ep.number)
                    panel = null
                }
            } else SidePanel(
                title = when (open) {
                    Panel.Menu -> ""
                    Panel.Quality -> "Quality"
                    Panel.SubStyle -> "Subtitle style"
                    Panel.Boost -> "Audio boost"
                    Panel.Hdr -> "HDR & Dolby Vision"
                    Panel.AutoSkip -> "Auto Skip Intro/Outro"
                    Panel.Tracks -> "Audio & Subtitles"
                    Panel.Episodes -> "Episodes"
                    Panel.Speed -> "Playback speed"
                    Panel.Party -> "Watch party"
                    Panel.Members -> "In this party"
                },
                onClose = { panel = null },
                wide = open == Panel.Episodes,
            ) {
                when (open) {
                    Panel.Menu -> LazyColumn {
                        item {
                            MenuRow("Auto Skip Intro/Outro", if (autoSkip) "On" else "Off") { panel = Panel.AutoSkip }
                        }
                        item {
                            MenuRow("Quality", if (quality == 0) "Auto" else "${quality}p") { panel = Panel.Quality }
                        }
                        item {
                            MenuRow(
                                "Subtitle style",
                                subStyle.font,
                            ) { panel = Panel.SubStyle }
                        }
                        item { MenuRow("Audio boost", if (boostDb == 0) "Off" else "+$boostDb dB") { panel = Panel.Boost } }
                        item { MenuRow("HDR & Dolby Vision", if (hdrOn) "On" else "Auto") { panel = Panel.Hdr } }
                        item { MenuRow("Playback speed", if (speed == 1f) "Normal" else "${speed}x") { panel = Panel.Speed } }
                        item {
                            MenuRow("Watch party", party?.code ?: "") { panel = Panel.Party }
                        }
                    }
                    Panel.Quality -> {
                        val heights = remember(tracks) { videoHeights(tracks) }
                        LazyColumn {
                            item {
                                PanelRow(label = "Auto", selected = quality == 0) {
                                    quality = 0
                                    com.biaruuu.kiro.data.AppSettings.setQualityPref(context, 0)
                                    applyQuality(exo, 0)
                                    panel = null
                                }
                            }
                            items(heights) { h ->
                                PanelRow(label = "${h}p", selected = quality == h) {
                                    quality = h
                                    com.biaruuu.kiro.data.AppSettings.setQualityPref(context, h)
                                    applyQuality(exo, h)
                                    panel = null
                                }
                            }
                        }
                    }
                    Panel.SubStyle -> SubtitleStylePanel(subStyle) { new ->
                        subStyle = new
                        SubtitleStyleStore.save(context, new)
                    }
                    Panel.Tracks -> Unit
                    Panel.Members -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
                        items(partyMembers, key = { it.uid }) { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.biaruuu.kiro.ui.components.DecoratedAvatar(m.avatar, m.name, 40.dp, m.decoration)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    m.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = com.biaruuu.kiro.data.NameStyles.family(context, m.nameFont),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (m.host) {
                                    Text(
                                        "Host", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(KiroAccent).padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                    Panel.Party -> Column(
                        Modifier.fillMaxWidth().widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val p = party
                        fun shareCode(code: String) {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Watch ${ui.base?.displayTitle.orEmpty()} with me on Kiro! Open Me > Watch party and enter the code $code",
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        }
                        if (p == null) {
                            Text("Watch together", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Start a party and send the code to your friends. Their player follows yours: play, pause, seeking and next episodes. Everyone needs to be logged in.",
                                color = Color(0xFF8E8E93), fontSize = 12.sp, textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(18.dp))
                            PartyButton("Start a watch party", true) {
                                com.biaruuu.kiro.data.WatchParty.host(context, animeId, current, ui.base?.displayTitle.orEmpty()) { code, err ->
                                    if (code != null) shareCode(code)
                                    else com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err ?: "Couldn't start the party", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Text(if (p.host) "You are hosting" else "You are in a party", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                p.code, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 5.sp,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF18181B)).padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (partyMembers.size <= 1) "Waiting for friends to join..."
                                else "${partyMembers.size} watching. Tap the avatars at the top to see who.",
                                color = Color(0xFFD0D0D4), fontSize = 12.sp, textAlign = TextAlign.Center,
                            )
                            if (!p.host) {
                                Spacer(Modifier.height(6.dp))
                                Text("The host controls playback.", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(18.dp))
                            PartyButton("Share code", true) { shareCode(p.code) }
                            Spacer(Modifier.height(10.dp))
                            PartyButton(if (p.host) "End party" else "Leave party", false) {
                                com.biaruuu.kiro.data.WatchParty.leave()
                                panel = null
                            }
                        }
                    }
                    Panel.Boost -> LazyColumn {
                        items(listOf(0, 10, 20)) { db ->
                            PanelRow(label = if (db == 0) "Off" else "+$db dB", selected = boostDb == db) {
                                boostDb = db
                                panel = null
                            }
                        }
                        item {
                            Text(
                                "Makes quiet dialogue louder. Loud scenes may distort at +20 dB.",
                                color = Color(0xFF6A6A70), fontSize = 12.sp, lineHeight = 18.sp,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    Panel.AutoSkip -> LazyColumn {
                        items(listOf(true, false)) { on ->
                            PanelRow(label = if (on) "On" else "Off", selected = autoSkip == on) {
                                autoSkip = on
                                com.biaruuu.kiro.data.AppSettings.setAutoSkip(context, on)
                                panel = null
                            }
                        }
                        item {
                            Text(
                                "When on, Kiro skips the intro, outro and recap for you after a short countdown. Only parts that are confirmed for this exact episode are skipped; nothing is guessed.",
                                color = Color(0xFF6A6A70), fontSize = 12.sp, lineHeight = 18.sp,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    Panel.Hdr -> LazyColumn {
                        items(listOf(false, true)) { on ->
                            PanelRow(label = if (on) "On (hardware tunneling)" else "Auto", selected = hdrOn == on) {
                                hdrOn = on
                                panel = null
                            }
                        }
                        item {
                            Text(
                                "HDR10 and Dolby Vision play through your phone's hardware decoder when the stream and screen support them. \"On\" also turns on hardware tunneling and an HDR display mode.",
                                color = Color(0xFF6A6A70), fontSize = 12.sp, lineHeight = 18.sp,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    Panel.Episodes -> {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                            initialFirstVisibleItemIndex = episodes.indexOfFirst { it.number == current }.coerceAtLeast(0),
                        )
                        LazyColumn(state = listState) {
                            items(episodes, key = { it.number }) { ep ->
                                EpisodeRow(ep, ep.number == current, ep.number in watchedEps) {
                                    if (guestBlocked()) {
                                        // Guests follow the host.
                                    } else if (ep.number in watchedEps && ep.number != current) {
                                        rewatchEp = ep.number
                                    } else {
                                        playEpisode(ep.number)
                                        panel = null
                                    }
                                }
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
                                com.biaruuu.kiro.data.AppSettings.setPlaySpeed(context, s)
                                exo.setPlaybackSpeed(s)
                                panel = null
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Distinct video heights the stream offers, tallest first. */
private fun videoHeights(tracks: Tracks): List<Int> {
    val out = sortedSetOf<Int>()
    tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { g ->
        for (i in 0 until g.length) {
            val h = g.getTrackFormat(i).height
            if (g.isTrackSupported(i) && h > 0) out += h
        }
    }
    return out.toList().sortedDescending()
}

private const val AUTO_MAX_HEIGHT = 1080

/** 0 = Auto (adapts to the connection); otherwise the best track at or below that height. */
private fun applyQuality(exo: ExoPlayer, height: Int) {
    val p = exo.trackSelectionParameters.buildUpon()
    if (height == 0) p.setMaxVideoSize(Int.MAX_VALUE, AUTO_MAX_HEIGHT) else p.setMaxVideoSize(Int.MAX_VALUE, height)
    exo.trackSelectionParameters = p.build()
}

@Composable
private fun Controls(
    title: String,
    isPlaying: Boolean,
    buffering: Boolean,
    position: Long,
    duration: Long,
    hasNext: Boolean,
    skipSegments: List<com.biaruuu.kiro.data.SkipTimes.Segment>,
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
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.6f),
                0.35f to Color.Black.copy(alpha = 0.22f),
                0.65f to Color.Black.copy(alpha = 0.22f),
                1f to Color.Black.copy(alpha = 0.75f),
            ),
        ),
    ) {
        val sidePad = 24.dp

        // Top bar: close, title, more.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad).padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.fluent_dismiss_regular),
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClose),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.fluent_more_vertical_regular),
                contentDescription = "More",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onMore),
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
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack10),
            )
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                if (buffering) {
                    com.biaruuu.kiro.ui.components.KiroSpinner(size = 48.dp)
                } else {
                    if (isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .width(8.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White),
                                )
                            }
                        }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.fluent_play_filled),
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Icon(
                painter = painterResource(R.drawable.fluent_skip_forward_10_regular),
                contentDescription = "Forward 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onForward10),
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
                Text(formatTime(position), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(14.dp))
                SeekBar(
                    position = position,
                    duration = duration,
                    skipSegments = skipSegments,
                    onSeekStart = onSeekStart,
                    onSeekPreview = onSeekPreview,
                    onSeekEnd = onSeekEnd,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                Text(formatTime(duration), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val tint = if (enabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
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

/** Thin line with white progress and a small white thumb. Tap or drag to seek. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    skipSegments: List<com.biaruuu.kiro.data.SkipTimes.Segment>,
    onSeekStart: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thumb = 14.dp
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
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
        // Amber stretches on the line mark what can be skipped (intro, outro, recap).
        if (duration > 0) {
            val usable = maxWidth - thumb
            for (seg in skipSegments) {
                val from = (seg.start * 1000 / duration).toFloat().coerceIn(0f, 1f)
                val to = (seg.end * 1000 / duration).toFloat().coerceIn(from, 1f)
                Box(
                    modifier = Modifier
                        .padding(start = thumb / 2 + usable * from)
                        .width(usable * (to - from))
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SkipAmber),
                )
            }
        }
        Box(modifier = Modifier.width(thumb / 2 + travel * fraction).height(3.dp).clip(CircleShape).background(Color.White))
        Box(
            modifier = Modifier
                .padding(start = travel * fraction)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SidePanel(title: String, onClose: () -> Unit, wide: Boolean = false, content: @Composable () -> Unit) {
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        // The panel grows with the screen (phones, tablets, fold-outs) instead of a fixed width.
        val panelWidth = if (wide) (maxWidth * 0.5f).coerceIn(380.dp, 560.dp) else (maxWidth * 0.42f).coerceIn(300.dp, 440.dp)
        AnimatedVisibility(
            visibleState = shown,
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { it } + fadeIn(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(Color(0xFF0B0B0D))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(top = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (title.isEmpty()) Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.fluent_dismiss_regular),
                        contentDescription = "Close",
                        tint = Color(0xFFB4B4BA),
                        modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClose),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222226)))
                content()
            }
        }
    }
}

/**
 * Episodes shelf over the paused picture: the show's name, then a row of episode cards (picture,
 * "1. Title", length). The one playing has a white outline. Cards size themselves to the screen.
 */
@Composable
private fun EpisodeShelf(
    heading: String,
    episodes: List<EpisodeInfo>,
    current: Int,
    watched: Set<Int>,
    onClose: () -> Unit,
    onPick: (EpisodeInfo) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        val card = (maxWidth / (if (maxWidth >= 700.dp) 3.4f else 2.3f)).coerceIn(150.dp, 300.dp)
        val state = androidx.compose.foundation.lazy.rememberLazyListState(
            initialFirstVisibleItemIndex = episodes.indexOfFirst { it.number == current }.coerceAtLeast(0),
        )
        Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
            Text(
                heading,
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 64.dp, bottom = 16.dp),
            )
            androidx.compose.foundation.lazy.LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(episodes, key = { it.number }) { ep ->
                    val playing = ep.number == current
                    Column(
                        modifier = Modifier
                            .width(card)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(ep) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF18181B))
                                .then(if (playing) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier),
                        ) {
                            if (!ep.thumbnailUrl.isNullOrEmpty()) {
                                coil.compose.AsyncImage(
                                    model = ep.thumbnailUrl, contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().padding(if (playing) 3.dp else 0.dp).clip(RoundedCornerShape(if (playing) 6.dp else 8.dp)),
                                )
                            }
                            if (ep.number in watched) com.biaruuu.kiro.ui.components.WatchedBadge(Modifier.align(Alignment.TopStart))
                        }
                        Text(
                            ep.heading,
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (ep.durationMinutes > 0) {
                            Text("${ep.durationMinutes}m", color = Color(0xFF9A9AA2), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        Icon(
            painter = painterResource(R.drawable.fluent_dismiss_regular),
            contentDescription = "Close",
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(24.dp).clip(CircleShape).clickable(onClick = onClose),
        )
    }
}

/** One episode like the Netflix list: thumbnail, "1. Title", length, and the plot. */
@Composable
private fun EpisodeRow(ep: EpisodeInfo, playing: Boolean, watched: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (playing) Color(0xFF18181B) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF262628)),
            contentAlignment = Alignment.Center,
        ) {
            if (!ep.thumbnailUrl.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = ep.thumbnailUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fluent_play_filled),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (watched) com.biaruuu.kiro.ui.components.WatchedBadge(Modifier.align(Alignment.TopStart))
            if (playing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ep.heading,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ep.durationMinutes > 0) {
                    Text(
                        "${ep.durationMinutes}m",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            val plot = ep.overview
            if (!plot.isNullOrBlank()) {
                Text(
                    plot,
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
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
            color = if (selected) Color.White else Color(0xFF9A9AA2),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Audio & Subtitles as a dark rounded card with two columns. Picks are held until Apply;
 * Cancel (or the X) closes without changing anything.
 */
@Composable
private fun TracksDialog(
    exo: ExoPlayer,
    tracks: Tracks,
    languages: List<String>,
    currentLanguage: String,
    onPickLanguage: (String) -> Unit,
    onClose: () -> Unit,
) {
    class Choice(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

    fun build(type: Int, withOff: Boolean): List<Choice> {
        val out = mutableListOf<Choice>()
        val groups = tracks.groups.filter { it.type == type }
        if (withOff) out += Choice("Off", null, -1, groups.none { it.isSelected })
        val fallback = if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles"
        var n = 0
        groups.forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                n++
                val format = group.getTrackFormat(i)
                val name = format.label
                    ?: format.language?.let { Locale.forLanguageTag(it).displayLanguage }?.takeIf { it.isNotBlank() }
                    ?: "$fallback $n"
                out += Choice(name, group, i, group.isTrackSelected(i))
            }
        }
        return out
    }

    val ctx = LocalContext.current
    val audio = remember(tracks) { build(C.TRACK_TYPE_AUDIO, false) }
    val subs = remember(tracks) { build(C.TRACK_TYPE_TEXT, true) }
    // When the episode has a Japanese and an English stream, the Audio list is just those two
    // languages (English is the dub, Japanese the original); choosing one switches stream.
    val twoLanguages = languages.size > 1 || languages.firstOrNull().let { it != null && it != "Japanese" }
    var pickLanguage by remember { mutableIntStateOf(languages.indexOf(currentLanguage).coerceAtLeast(0)) }
    var pickAudio by remember(tracks) { mutableIntStateOf(audio.indexOfFirst { it.selected }.coerceAtLeast(0)) }
    var pickSub by remember(tracks) { mutableIntStateOf(subs.indexOfFirst { it.selected }.coerceAtLeast(0)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 780.dp)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0B0B0D))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
                Row(modifier = Modifier.weight(1f)) {
                    if (twoLanguages) {
                        TrackColumn("Audio", languages, pickLanguage, { pickLanguage = it }, Modifier.weight(1f))
                    } else {
                        TrackColumn("Audio", audio.map { it.label }, pickAudio, { pickAudio = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(24.dp))
                    TrackColumn("Subtitles", subs.map { it.label }, pickSub, { pickSub = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(28.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancel",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(24.dp))
                            .clickable(onClick = onClose)
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Apply",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .clickable {
                                if (twoLanguages && languages.getOrNull(pickLanguage) != currentLanguage) {
                                    onPickLanguage(languages[pickLanguage])
                                    return@clickable
                                }
                                val params = exo.trackSelectionParameters.buildUpon()
                                audio.getOrNull(pickAudio)?.takeIf { !twoLanguages }?.group?.let { g ->
                                    params.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    params.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, audio[pickAudio].index))
                                }
                                subs.getOrNull(pickSub)?.let { c ->
                                    if (c.group == null) {
                                        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        com.biaruuu.kiro.data.AppSettings.setSubtitlePref(ctx, "off")
                                    } else {
                                        com.biaruuu.kiro.data.AppSettings.setSubtitlePref(
                                            ctx, c.group.getTrackFormat(c.index).language ?: "",
                                        )
                                        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        params.setOverrideForType(TrackSelectionOverride(c.group.mediaTrackGroup, c.index))
                                    }
                                }
                                exo.trackSelectionParameters = params.build()
                                onClose()
                            }
                            .padding(horizontal = 26.dp, vertical = 10.dp),
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.fluent_dismiss_regular),
                contentDescription = "Close",
                tint = Color(0xFFB4B4BA),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
            )
        }
    }
}

@Composable
private fun TrackColumn(title: String, labels: List<String>, picked: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
        if (labels.isEmpty()) {
            Text("None available", color = Color(0xFF8E8E93), fontSize = 14.sp, modifier = Modifier.padding(start = 40.dp, top = 8.dp))
        }
        LazyColumn {
            items(labels.size) { i ->
                val selected = i == picked
                Row(
                    modifier = Modifier.fillMaxWidth().height(46.dp).clickable { onPick(i) },
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
                        labels[i],
                        color = if (selected) Color.White else Color(0xFF9A9AA2),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val SkipAmber = Color(0xFFFFC857)

@Composable
private fun MenuRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xFF9A9AA2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_left_regular),
            contentDescription = null,
            tint = Color(0xFF5E5E63),
            modifier = Modifier.padding(start = 6.dp).size(16.dp).rotate(180f),
        )
    }
}

/**
 * Subtitle style as a list: FONT (each name shown in its own typeface), SIZE, BACKGROUND and TEXT
 * STYLE, with the chosen row ticked. Changes are saved and applied to the captions at once.
 */
@Composable
private fun SubtitleStylePanel(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit) {
    val context = LocalContext.current
    val trebuchet = remember { FontFamily(SubtitleOptions.typeface(context, SubtitleOptions.TREBUCHET)) }
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
                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "That file isn't a usable font", Toast.LENGTH_SHORT).show()
            }
        }
    }

    androidx.compose.material3.ProvideTextStyle(TextStyle(fontFamily = trebuchet)) {
    Column(Modifier.fillMaxSize()) {
    CaptionPreview(style)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        item { SectionLabel("Font") }
        items(SubtitleOptions.fonts) { font ->
            val selected = style.font == font
            val isCustom = font == SubtitleOptions.CUSTOM
            val typeface = remember(font) { if (isCustom) null else SubtitleOptions.typeface(context, font) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isCustom && customName == null) pickFont.launch(arrayOf("*/*")) else onChange(style.copy(font = font))
                    }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isCustom) (customName ?: "Your own font") else font,
                    color = if (selected) Color.White else Color(0xFF9A9AA2),
                    fontFamily = typeface?.let { FontFamily(it) },
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isCustom) {
                    Text(
                        if (customName == null) "Choose file" else "Change",
                        color = Color(0xFF9A9AA2),
                        fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { pickFont.launch(arrayOf("*/*")) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (selected) Box(Modifier.padding(start = 8.dp).size(6.dp).clip(CircleShape).background(Color.White))
            }
        }

        item { SectionLabel("Size") }
        item { Chips(SubtitleOptions.sizes.map { it.first }, style.size) { onChange(style.copy(size = it)) } }
        item { SectionLabel("Background") }
        item { Chips(SubtitleOptions.backgrounds.map { it.first }, style.background) { onChange(style.copy(background = it)) } }
        item { SectionLabel("Text style") }
        item { Chips(SubtitleOptions.edges.map { it.first }, style.edge) { onChange(style.copy(edge = it)) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
    }
    }
}

/** Japanese for the original, Filipino for the developer's own dub, English for every other dub. */
private fun audioLanguage(s: com.biaruuu.kiro.data.stream.StreamSource): String = when {
    !s.isDub -> "Japanese"
    s.providerName.startsWith("Filipino") -> "Filipino"
    else -> "English"
}

/** A live sample of the captions, pinned above the options so every change shows at once. */
@Composable
private fun CaptionPreview(style: SubtitleStyle) {
    val context = LocalContext.current
    val family = remember(style.font, style.edge) { FontFamily(SubtitleOptions.typeface(context, style.font)) }
    val bg = SubtitleOptions.backgrounds[style.background].second
    val shadow = when (style.edge) {
        1 -> androidx.compose.ui.graphics.Shadow(Color(0xD9000000), androidx.compose.ui.geometry.Offset(2f, 2f), 4f)
        2 -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 0f), 7f)
        else -> androidx.compose.ui.graphics.Shadow(Color.Transparent, androidx.compose.ui.geometry.Offset.Zero, 0f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18181B)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Sample caption",
            color = Color.White,
            fontFamily = family,
            fontSize = (SubtitleOptions.sizes[style.size].second * 0.8f).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = TextStyle(shadow = shadow),
            modifier = Modifier
                .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                .background(Color(bg))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MutedText,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** One-line choice: pills, the chosen one filled white. Scrolls sideways if the panel is narrow. */
@Composable
private fun Chips(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(if (on) Modifier.background(Color.White) else Modifier.border(1.dp, Color(0xFF2E2E33), RoundedCornerShape(20.dp)))
                    .clickable { onPick(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PartyButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color.Black else Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(if (filled) Modifier.background(Color.White) else Modifier.border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(24.dp)))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun PartyChip(members: List<com.biaruuu.kiro.data.WatchParty.Member>, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
            members.take(3).forEach { m ->
                com.biaruuu.kiro.ui.components.DecoratedAvatar(m.avatar, m.name, 26.dp, m.decoration)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (members.size > 3) "+${members.size - 3}" else "${members.size}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        )
    }
}
