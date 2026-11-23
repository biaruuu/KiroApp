package com.biaruuu.kiro.data.stream

import com.biaruuu.kiro.data.TagalogDub

/**
 * The developer's own Filipino dub library (see [TagalogDub]). It is the only copy of that audio, so
 * the address is offered even when the quick check is slow or refused; the player then reports it.
 * Its captions (made automatically, see TagalogCaptions) come with it when they exist.
 */
class TagalogDubProvider : StreamProvider {
    override fun getName() = "Filipino Dub"

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val e = TagalogDub.lookup(request.animeId, request.episode, listOfNotNull(request.titleEnglish, request.titleRomaji, request.title)) ?: return emptyList()
        val headers = if (e.referer.startsWith("http")) streamHeaders(e.referer) else mapOf("User-Agent" to CHROME)
        // Offered at once: it is the only copy of this audio, and the player reports it if it fails.
        val hls = e.url.substringBefore('?').endsWith(".m3u8")
        return listOf(StreamSource("${getName()} · Filipino", e.url, e.quality, hls, listOfNotNull(TagalogDub.captionTrack(e)), "dub").withLatency(800).withHeaders(headers))
    }
}
