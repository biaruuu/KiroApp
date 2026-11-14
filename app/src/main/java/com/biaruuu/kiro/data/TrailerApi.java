package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biaruuu.kiro.BuildConfig;
import com.biaruuu.kiro.core.NativeCore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Finds trailers (YouTube ids), best first:
 * <ol>
 *   <li>TMDB: search the title, then read the videos of the match (needs the TMDB key that the
 *       build injects from the TMDB_API_KEY secret).</li>
 *   <li>The trailer AniList already has.</li>
 *   <li>MyAnimeList through the free Jikan API.</li>
 * </ol>
 * The player tries the ids in order, so a video that cannot be embedded just moves on to the next.
 */
public final class TrailerApi {

    private static final String TMDB = "https://api.themoviedb.org/3";
    private static final String JIKAN = "https://api.jikan.moe/v4/anime/";
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private TrailerApi() {
    }

    /** TMDB trailers for this anime (empty when there is no key or no match). Runs off-thread. */
    @NonNull
    public static AniListApi.Cancelable findTmdb(
            @NonNull Anime anime, @NonNull AniListApi.Callback<List<String>> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        POOL.execute(() -> {
            List<String> ids;
            try {
                ids = fromTmdb(anime, cancelled);
            } catch (Exception e) {
                ids = new ArrayList<>();
            }
            if (!cancelled.get()) {
                callback.onSuccess(ids);
            }
        });
        return () -> cancelled.set(true);
    }

    /** MyAnimeList trailer through Jikan (empty list when none). Runs off-thread. */
    @NonNull
    public static AniListApi.Cancelable findJikan(
            int malId, @NonNull AniListApi.Callback<List<String>> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        POOL.execute(() -> {
            List<String> ids = new ArrayList<>();
            try {
                String id = malId > 0 ? fromJikan(malId) : null;
                if (id != null) {
                    ids.add(id);
                }
            } catch (Exception ignored) {
                // No MAL trailer.
            }
            if (!cancelled.get()) {
                callback.onSuccess(ids);
            }
        });
        return () -> cancelled.set(true);
    }

    // ---------------------------------------------------------------------------------------
    // TMDB
    // ---------------------------------------------------------------------------------------

    @NonNull
    private static List<String> fromTmdb(@NonNull Anime anime, @NonNull AtomicBoolean cancelled)
            throws Exception {
        String key = BuildConfig.TMDB_API_KEY;
        if (key == null || key.isEmpty()) {
            return new ArrayList<>();
        }
        boolean movie = "MOVIE".equals(anime.getFormat());
        String type = movie ? "movie" : "tv";

        Set<String> titles = new LinkedHashSet<>();
        addTitle(titles, anime.getTitleEnglish());
        addTitle(titles, anime.getTitleRomaji());
        final List<String> known = new ArrayList<>(titles);
        addTitle(titles, anime.getTitleNative());

        int year = anime.getSeasonYear();
        for (String title : titles) {
            if (cancelled.get()) {
                break;
            }
            // Try with the year first for a precise match, then without it.
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt == 0 && year <= 0) {
                    continue;
                }
                String url = TMDB + "/search/" + type + "?query=" + URLEncoder.encode(title, "UTF-8")
                        + "&include_adult=false";
                if (attempt == 0) {
                    url += (movie ? "&year=" : "&first_air_date_year=") + year;
                }
                JSONObject match = pickMatch(get(url, key), known);
                if (match == null) {
                    continue;
                }
                List<String> videos = videosOf(type, match.optInt("id"), key);
                if (!videos.isEmpty()) {
                    return videos;
                }
            }
        }
        return new ArrayList<>();
    }

    private static void addTitle(Set<String> out, @Nullable String title) {
        if (title != null && !title.trim().isEmpty()) {
            out.add(title.trim());
        }
    }

    /**
     * Best animation result by title similarity (scored in Rust). Rejects weak matches so a
     * trailer from a different show is never used.
     */
    @Nullable
    private static JSONObject pickMatch(@NonNull JSONObject search, @NonNull List<String> known) {
        JSONArray results = search.optJSONArray("results");
        JSONObject best = null;
        int bestScore = 0;
        for (int i = 0; results != null && i < results.length() && i < 10; i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) {
                continue;
            }
            boolean animation = false;
            JSONArray genres = r.optJSONArray("genre_ids");
            for (int g = 0; genres != null && g < genres.length(); g++) {
                if (genres.optInt(g) == 16) {
                    animation = true;
                }
            }
            if (!animation) {
                continue;
            }
            int score = 0;
            for (String field : new String[] {"name", "original_name", "title", "original_title"}) {
                String candidate = r.optString(field, "");
                for (String title : known) {
                    score = Math.max(score, NativeCore.similarity(title, candidate));
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return bestScore >= 50 ? best : null;
    }

    /** YouTube trailers of one TMDB title, ordered: official trailers, trailers, then the rest. */
    @NonNull
    private static List<String> videosOf(@NonNull String type, int tmdbId, @NonNull String key)
            throws Exception {
        String url = TMDB + "/" + type + "/" + tmdbId + "/videos?include_video_language=en,ja,null";
        JSONArray results = get(url, key).optJSONArray("results");
        List<String> official = new ArrayList<>();
        List<String> trailers = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject v = results.optJSONObject(i);
            if (v == null || !"YouTube".equals(v.optString("site"))) {
                continue;
            }
            String name = v.optString("name", "").toLowerCase(Locale.ROOT);
            String id = v.optString("key", "");
            if (id.isEmpty() || name.contains("short") || name.contains("clip")) {
                continue;
            }
            boolean trailer = "Trailer".equals(v.optString("type"));
            if (trailer && v.optBoolean("official")) {
                official.add(id);
            } else if (trailer) {
                trailers.add(id);
            } else {
                others.add(id);
            }
        }
        List<String> all = new ArrayList<>(official);
        all.addAll(trailers);
        all.addAll(others);
        return all.size() > 4 ? new ArrayList<>(all.subList(0, 4)) : all;
    }

    @NonNull
    private static JSONObject get(@NonNull String url, @NonNull String key) throws Exception {
        Request.Builder rb = new Request.Builder().header("Accept", "application/json");
        // A v4 "read access token" is a long JWT; a v3 key is 32 hex characters.
        if (key.length() > 60) {
            rb.url(url).header("Authorization", "Bearer " + key);
        } else {
            rb.url(url + (url.contains("?") ? "&" : "?") + "api_key=" + key);
        }
        return fetch(rb.build());
    }

    // ---------------------------------------------------------------------------------------
    // Jikan (MyAnimeList)
    // ---------------------------------------------------------------------------------------

    @Nullable
    private static String fromJikan(int malId) throws Exception {
        JSONObject root = fetch(new Request.Builder()
                .url(JIKAN + malId)
                .header("Accept", "application/json")
                .build());
        JSONObject data = root.optJSONObject("data");
        JSONObject trailer = data != null ? data.optJSONObject("trailer") : null;
        if (trailer == null || trailer.isNull("youtube_id")) {
            return null;
        }
        String id = trailer.optString("youtube_id", "");
        return id.isEmpty() ? null : id;
    }

    @NonNull
    private static JSONObject fetch(@NonNull Request request) throws Exception {
        Call call = HttpClientProvider.get().newCall(request);
        try (Response r = call.execute()) {
            if (!r.isSuccessful()) {
                throw new IOException("HTTP " + r.code());
            }
            ResponseBody body = r.body();
            return new JSONObject(body != null ? body.string() : "{}");
        }
    }
}
