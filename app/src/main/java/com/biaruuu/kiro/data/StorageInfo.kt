package com.biaruuu.kiro.data

import android.content.Context
import java.io.File

/** Sizes for the storage cleaner (Me > Storage) and the cache clearing behind its buttons. */
object StorageInfo {
    private fun size(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { size(it) } ?: 0L
    }

    private fun caches(c: Context) = listOfNotNull(c.cacheDir, c.externalCacheDir)

    fun cacheBytes(c: Context): Long = caches(c).sumOf { size(it) }

    fun clearCache(c: Context): Long {
        val before = cacheBytes(c)
        caches(c).forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
        return before - cacheBytes(c)
    }

    /** Everything else Kiro keeps (settings, saved lists) apart from downloads and cache. */
    fun appDataBytes(c: Context): Long {
        val files = c.filesDir
        val dl = size(File(files, "downloads")) + size(File(files, "dl_subs"))
        val prefs = size(File(c.applicationInfo.dataDir, "shared_prefs")) + size(File(c.applicationInfo.dataDir, "databases"))
        return (size(files) - dl).coerceAtLeast(0L) + prefs
    }

    /** Finished downloads whose episode is already marked watched. */
    fun watchedDownloads(c: Context): List<Downloads.Item> =
        Downloads.items.value.filter { it.completed && it.episode in WatchedStore.watchedEpisodes(c, it.animeId) }

    fun removeWatchedDownloads(c: Context): Int {
        val list = watchedDownloads(c)
        list.forEach { Downloads.remove(c, it.animeId, it.episode) }
        return list.size
    }
}
