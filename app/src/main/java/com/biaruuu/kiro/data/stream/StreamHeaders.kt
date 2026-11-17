package com.biaruuu.kiro.data.stream

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Some streaming servers only answer when the request carries the right Referer / Origin. The
 * player and the downloader both go through [wrap], which adds those headers: the ones registered
 * for the address's host, or else the ones of the stream that is active in that slot.
 */
object StreamHeaders {

    enum class Slot { Playback, Download }

    private val byHost = ConcurrentHashMap<String, Map<String, String>>()
    @Volatile private var playback: Map<String, String> = emptyMap()
    @Volatile private var download: Map<String, String> = emptyMap()

    private fun host(url: String): String = try {
        java.net.URI(url).host.orEmpty()
    } catch (_: Exception) {
        ""
    }

    /** Remembers the headers of [url] and makes them the active ones for [slot]. */
    fun activate(slot: Slot, url: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) host(url).takeIf { it.isNotEmpty() }?.let { byHost[it] = headers }
        if (slot == Slot.Playback) playback = headers else download = headers
    }

    /** Only remembers them for the address's host (used when restoring saved downloads). */
    fun remember(url: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) host(url).takeIf { it.isNotEmpty() }?.let { byHost[it] = headers }
    }

    private fun headersFor(spec: DataSpec, slot: Slot): Map<String, String> {
        val scheme = spec.uri.scheme.orEmpty()
        if (scheme != "http" && scheme != "https") return emptyMap()
        return byHost[spec.uri.host.orEmpty()]?.takeIf { slot == Slot.Download || it === playback || playback.isNotEmpty() }
            ?: if (slot == Slot.Playback) playback else download
    }

    fun wrap(upstream: DataSource.Factory, slot: Slot): DataSource.Factory =
        ResolvingDataSource.Factory(upstream) { spec ->
            val extra = headersFor(spec, slot)
            if (extra.isEmpty()) spec else spec.withAdditionalHeaders(extra)
        }
}
