package com.biaruuu.kiro.data.stream

import com.biaruuu.kiro.data.TagalogDub

/**
 * The developer's own Tagalog dub library (see [TagalogDub]). It is the only copy of that audio, so
 * the address is offered even when the quick check is slow or refused; the player then reports it.
 * English captions are lent to it by the resolver from the other sources.
 */
class TagalogDubProvider : StreamProvider {
    override fun getName() = "Tagalog Dub"

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val e = TagalogDub.lookup(request.animeId, request.episode, listOfNotNull(request.titleEnglish, request.titleRomaji, request.title)) ?: return emptyList()
        val headers = if (e.referer.startsWith("http")) streamHeaders(e.referer) else mapOf("User-Agent" to CHROME)
        val checked = try { source(getName(), "Tagalog", e.url, headers, "dub", emptyList(), e.quality) } catch (_: Exception) { null }
        if (checked != null) return listOf(checked)
        val hls = e.url.substringBefore('?').endsWith(".m3u8")
        return listOf(StreamSource("${getName()} · Tagalog", e.url, e.quality, hls, emptyList(), "dub").withLatency(2500).withHeaders(headers))
    }
}
