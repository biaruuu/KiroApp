package com.biaruuu.kiro.data.stream

import com.biaruuu.kiro.data.TagalogDub

/** The developer's own Tagalog dub library (see [TagalogDub]). Only has what has been added. */
class TagalogDubProvider : StreamProvider {
    override fun getName() = "Tagalog Dub"

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val e = TagalogDub.lookup(request.animeId, request.episode) ?: return emptyList()
        val headers = if (e.referer.startsWith("http")) streamHeaders(e.referer) else mapOf("User-Agent" to CHROME)
        return listOfNotNull(source(getName(), "Tagalog", e.url, headers, "dub", emptyList(), e.quality))
    }
}
