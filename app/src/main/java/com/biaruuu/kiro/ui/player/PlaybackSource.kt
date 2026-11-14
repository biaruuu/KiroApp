package com.biaruuu.kiro.ui.player

/**
 * Where episode videos come from. There is no streaming provider connected yet, so every episode
 * plays this public sample video. When a real source exists, return its URL (or HLS playlist)
 * here and nothing else in the player needs to change.
 */
object PlaybackSource {
    private const val SAMPLE =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    @Suppress("UNUSED_PARAMETER")
    fun urlFor(animeId: Int, episode: Int): String = SAMPLE
}
