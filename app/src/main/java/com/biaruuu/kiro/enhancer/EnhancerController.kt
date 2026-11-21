@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.biaruuu.kiro.enhancer

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.biaruuu.kiro.core.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Runs the Anime4K line sharpener for one player and keeps it from ever costing smoothness:
 * the strongest tier is chosen from the phone's GPU, memory, battery saver and heat, and while
 * playing it steps down the moment frames start to drop. The user can pick Off or a fixed tier.
 *
 * Modes: 0 Off, 1 Auto, 2 Light, 3 Balanced, 4 Quality.
 */
class EnhancerController(private val context: Context, private val exo: ExoPlayer) {
    private val prefs = context.getSharedPreferences("kiro_enhancer", Context.MODE_PRIVATE)
    private val state = Anime4kState()
    private val effect = Anime4kEffect(state)

    private var profile: DeviceProfile? = null
    private var attached = false
    private var hdr = false
    private var videoHeight = 0
    private var videoWidth = 0
    private var disabledForSession = false
    private var downgrades = 0
    private var calmSecs = 0
    private var dropped = 0

    /** Saved mode, 1 (Auto) by default. */
    var mode: Int = prefs.getInt("mode", 1)
        private set

    /** Tier currently drawn (0 when off or not attached), for the menu label. */
    val activeTier: Int get() = if (attached) state.tier else 0

    private val player = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoHeight = videoSize.height
            videoWidth = videoSize.width
            refresh()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isEnhancerError(error)) return
            // The picture effect failed on this phone: turn it off and carry on without it.
            disabledForSession = true
            state.failed = true
            detach()
            exo.prepare()
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            dropped += droppedFrames
        }
    }

    init {
        exo.addListener(player)
        exo.addAnalyticsListener(analytics)
    }

    fun isEnhancerError(e: PlaybackException): Boolean =
        attached && (e.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED || state.failed)

    fun setMode(newMode: Int) {
        mode = newMode
        prefs.edit().putInt("mode", newMode).apply()
        downgrades = 0
        calmSecs = 0
        refresh()
    }

    /** HDR / tunneling playback cannot take picture effects, so the enhancer steps aside. */
    fun setHdr(on: Boolean) {
        hdr = on
        refresh()
    }

    /** Reads the phone once (off the main thread), then applies. */
    suspend fun start() {
        if (profile == null) profile = withContext(Dispatchers.IO) { DeviceProfile.load(context) }
        refresh()
        governor()
    }

    fun release() {
        exo.removeListener(player)
        exo.removeAnalyticsListener(analytics)
    }

    private fun startTier(): Int {
        val p = profile ?: return 0
        if (mode == 0 || hdr || disabledForSession) return 0
        val userMode = if (mode >= 2) mode - 1 else 0
        val h = if (videoHeight > 0) videoHeight else 720
        val fps = exo.videoFormat?.frameRate?.takeIf { it > 0f } ?: 24f
        val ceiling = if (userMode > 0) 3 else p.ceiling
        return NativeCore.enhancerStartTier(
            ceiling, h, (fps * 10).toInt(),
            DeviceProfile.batterySaver(context), DeviceProfile.thermal(context), userMode,
        )
    }

    private fun refresh() {
        if (profile == null) return
        var t = startTier()
        // A phone that already struggled this session does not jump back to full strength.
        if (downgrades > 0 && attached) t = minOf(t, state.tier)
        // Enlarge small videos before sharpening, but only where a bigger picture really shows.
        val screen = maxOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        var scale = if (videoWidth > 0) NativeCore.upscaleFactor(t, videoWidth, screen) else 10
        // Upscaling is only worth its cost at balanced and above.
        if (t in 1..1 && scale > 10) scale = 10
        if (attached && scale != state.scale10) detach() // the output size changes, so start the effect fresh
        state.scale10 = scale
        state.tier = t
        if (t > 0 && !attached) attach() else if (t == 0 && attached) detach()
    }

    private fun attach() {
        try {
            exo.setVideoEffects(listOf(effect))
            attached = true
        } catch (_: Throwable) {
            disabledForSession = true
        }
    }

    private fun detach() {
        try {
            exo.setVideoEffects(emptyList())
        } catch (_: Throwable) {
        }
        attached = false
    }

    /** Every 5 s while playing: measure dropped frames and move the tier up or down. */
    private suspend fun governor() {
        while (true) {
            delay(5_000)
            if (!attached || !exo.isPlaying) {
                dropped = 0
                continue
            }
            val fps = exo.videoFormat?.frameRate?.takeIf { it > 0f } ?: 24f
            val shown = fps * 5f
            val permille = ((dropped * 1000f) / shown).toInt().coerceIn(0, 1000)
            dropped = 0
            val cur = state.tier
            calmSecs = if (permille <= 5) calmSecs + 5 else 0
            // Heat and battery saver can change mid-episode, so the ceiling is checked again.
            val ceiling = startTier()
            var next = NativeCore.governorNext(cur, ceiling, permille, calmSecs, downgrades)
            // An enlarged picture costs a lot at every level, so below balanced it is simply switched off.
            if (state.scale10 > 10 && next == 1) next = 0
            if (next < cur) { downgrades++; calmSecs = 0 }
            else if (next > cur) calmSecs = 0
            if (next != cur) {
                state.tier = next
                if (next == 0) detach()
            }
        }
    }
}
