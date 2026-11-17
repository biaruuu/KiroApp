package com.biaruuu.kiro.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.biaruuu.kiro.data.stream.StreamSource
import com.biaruuu.kiro.data.stream.SubtitleTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Episodes saved on the phone for offline viewing. The video itself (a plain file or every HLS
 * segment of the chosen quality) is stored by Media3's download manager in one cache; the player
 * reads that same cache, so a downloaded episode plays without any network.
 */
object Downloads {

    class Item(
        val id: String,
        val animeId: Int,
        val episode: Int,
        val title: String,
        val poster: String,
        val episodeTitle: String,
        val url: String,
        val hls: Boolean,
        val quality: String,
        val state: Int,
        val percent: Float,
        val bytes: Long,
        val waitingForNetwork: Boolean,
        val subtitles: List<SubtitleTrack>,
    ) {
        val completed get() = state == Download.STATE_COMPLETED
        val failed get() = state == Download.STATE_FAILED
        val active get() = !completed && !failed
    }

    private const val PREFS = "kiro_downloads"
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var database: DatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    @Synchronized
    private fun db(context: Context): DatabaseProvider =
        database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

    @Synchronized
    fun cache(context: Context): Cache =
        cache ?: SimpleCache(File(context.applicationContext.filesDir, "downloads"), NoOpCacheEvictor(), db(context)).also { cache = it }

    /** Data source for the player: reads downloaded parts from the cache, everything else from the network. */
    fun dataSourceFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Synchronized
    fun manager(context: Context): DownloadManager {
        manager?.let { return it }
        val app = context.applicationContext
        val dm = DownloadManager(
            app,
            db(app),
            cache(app),
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            Executors.newFixedThreadPool(4),
        )
        dm.maxParallelDownloads = 2
        dm.requirements = requirements(app)
        dm.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = refresh(app)
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) = refresh(app)
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh(app)
            override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = refresh(app)
        })
        manager = dm
        return dm
    }

    private fun requirements(context: Context) =
        Requirements(if (AppSettings.downloadWifiOnly(context)) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    /** Call at app start so finished downloads are listed and unfinished ones carry on. */
    fun init(context: Context) {
        val app = context.applicationContext
        manager(app)
        refresh(app)
        io.execute {
            try {
                if (manager(app).currentDownloads.isNotEmpty()) {
                    main.post {
                        try {
                            DownloadService.start(app, DownloadsService::class.java)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Applies the Wi-Fi only setting to downloads that are running or waiting. */
    fun applyRequirements(context: Context) {
        manager(context).requirements = requirements(context.applicationContext)
    }

    // ---- state ----

    fun item(animeId: Int, episode: Int): Item? = _items.value.firstOrNull { it.id == idOf(animeId, episode) }

    fun idOf(animeId: Int, episode: Int) = "$animeId:$episode"

    private fun refresh(context: Context) {
        io.execute {
            try {
                val dm = manager(context)
                val metas = loadMeta(context)
                val notMet = dm.notMetRequirements != 0
                val list = ArrayList<Item>()
                dm.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val d = cursor.download
                        val m = metas.optJSONObject(d.request.id) ?: continue
                        val subs = ArrayList<SubtitleTrack>()
                        val arr = m.optJSONArray("subs")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val s = arr.optJSONObject(i) ?: continue
                                if (File(s.optString("path")).exists()) {
                                    subs.add(SubtitleTrack(Uri.fromFile(File(s.optString("path"))).toString(), s.optString("label"), s.optString("lang"), s.optString("mime")))
                                }
                            }
                        }
                        list.add(
                            Item(
                                id = d.request.id,
                                animeId = m.optInt("animeId"),
                                episode = m.optInt("episode"),
                                title = m.optString("title"),
                                poster = m.optString("poster"),
                                episodeTitle = m.optString("epTitle"),
                                url = m.optString("url"),
                                hls = m.optBoolean("hls"),
                                quality = m.optString("quality"),
                                state = d.state,
                                percent = d.percentDownloaded.coerceAtLeast(0f),
                                bytes = d.bytesDownloaded,
                                waitingForNetwork = notMet && d.state == Download.STATE_QUEUED,
                                subtitles = subs,
                            ),
                        )
                    }
                }
                _items.value = list.sortedWith(compareBy({ it.title }, { it.episode }))
            } catch (_: Exception) {
            }
        }
    }

    // ---- starting and removing ----

    /** Saves [source] for offline viewing. [maxHeight] limits the picture quality (0 = best). */
    fun start(
        context: Context,
        animeId: Int,
        title: String,
        poster: String,
        episode: Int,
        episodeTitle: String,
        source: StreamSource,
        maxHeight: Int,
        onError: (String) -> Unit,
    ) {
        val app = context.applicationContext
        val id = idOf(animeId, episode)
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        main.post {
            val helper = DownloadHelper.forMediaItem(
                app, mediaItem, DefaultRenderersFactory(app),
                DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            )
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    try {
                        if (source.isHls && maxHeight > 0 && helper.periodCount > 0) {
                            helper.clearTrackSelections(0)
                            helper.addTrackSelection(
                                0,
                                DefaultTrackSelector.Parameters.Builder(app)
                                    .setMaxVideoSize(Int.MAX_VALUE, maxHeight)
                                    .setForceHighestSupportedBitrate(true)
                                    .build(),
                            )
                        }
                        val request = helper.getDownloadRequest(id, null)
                        helper.release()
                        saveMeta(app, id, JSONObject()
                            .put("animeId", animeId).put("episode", episode)
                            .put("title", title).put("poster", poster).put("epTitle", episodeTitle)
                            .put("url", source.url).put("hls", source.isHls)
                            .put("quality", source.qualityLabel))
                        DownloadService.sendAddDownload(app, DownloadsService::class.java, request, true)
                        saveSubtitles(app, id, source)
                        refresh(app)
                    } catch (e: Exception) {
                        helper.release()
                        onError("Couldn't start the download.")
                    }
                }

                override fun onPrepareError(helper: DownloadHelper, e: java.io.IOException) {
                    helper.release()
                    onError("Couldn't start the download. Check your connection.")
                }
            })
        }
    }

    private fun saveSubtitles(context: Context, id: String, source: StreamSource) {
        if (source.subtitles.isEmpty()) return
        io.execute {
            val dir = File(context.filesDir, "dl_subs").apply { mkdirs() }
            val saved = JSONArray()
            source.subtitles.forEachIndexed { i, t ->
                try {
                    val ext = if (t.mimeType.contains("vtt", true)) "vtt" else "srt"
                    val file = File(dir, "${id.replace(':', '_')}_$i.$ext")
                    HttpClientProvider.get().newCall(Request.Builder().url(t.url).build()).execute().use { r ->
                        if (!r.isSuccessful) return@use
                        r.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                    if (file.length() > 0) {
                        saved.put(JSONObject().put("path", file.absolutePath).put("label", t.label).put("lang", t.language).put("mime", t.mimeType))
                    }
                } catch (_: Exception) {
                }
            }
            val meta = loadMeta(context)
            meta.optJSONObject(id)?.put("subs", saved)
            storeMeta(context, meta)
            refresh(context)
        }
    }

    fun remove(context: Context, animeId: Int, episode: Int) {
        val app = context.applicationContext
        val id = idOf(animeId, episode)
        DownloadService.sendRemoveDownload(app, DownloadsService::class.java, id, false)
        dropMeta(app, id)
    }

    fun removeAll(context: Context) {
        val app = context.applicationContext
        DownloadService.sendRemoveAllDownloads(app, DownloadsService::class.java, false)
        storeMeta(app, JSONObject())
        File(app.filesDir, "dl_subs").deleteRecursively()
        _items.value = emptyList()
    }

    /** A finished download as a playable source, or null. */
    fun playbackSource(context: Context, animeId: Int, episode: Int): StreamSource? {
        val item = item(animeId, episode)?.takeIf { it.completed } ?: return null
        return StreamSource("Offline", item.url, 0, item.hls, item.subtitles)
    }

    // ---- small metadata store (title, poster, source address) ----

    @Synchronized
    private fun loadMeta(context: Context): JSONObject = try {
        JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("meta", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    @Synchronized
    private fun storeMeta(context: Context, meta: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("meta", meta.toString()).apply()
    }

    @Synchronized
    private fun saveMeta(context: Context, id: String, value: JSONObject) {
        val meta = loadMeta(context)
        meta.put(id, value)
        storeMeta(context, meta)
    }

    @Synchronized
    private fun dropMeta(context: Context, id: String) {
        val meta = loadMeta(context)
        meta.optJSONObject(id)?.optJSONArray("subs")?.let { arr ->
            for (i in 0 until arr.length()) File(arr.optJSONObject(i)?.optString("path").orEmpty()).delete()
        }
        meta.remove(id)
        storeMeta(context, meta)
        refresh(context)
    }

    fun toast(context: Context, message: String) {
        main.post { Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show() }
    }
}
