package com.biaruuu.kiro.data;

import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers every anime the app has already loaded, so the details page can show the title,
 * artwork and synopsis instantly while the full details are still downloading.
 */
public final class AnimeCache {

    private static final ConcurrentHashMap<Integer, Anime> CACHE = new ConcurrentHashMap<>();

    private AnimeCache() {
    }

    public static void put(Anime anime) {
        CACHE.put(anime.getId(), anime);
    }

    @Nullable
    public static Anime get(int id) {
        return CACHE.get(id);
    }
}
