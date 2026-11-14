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
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final ConcurrentHashMap<Integer, Integer> TV_IDS = new ConcurrentHashMap<>();

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

    /**
     * Episodes of this anime's season from TMDB: names, overviews, stills, runtime and air dates.
     * [startDate] is AniList's "2012.07.08" (or "2012.07", "2012", "").
     */
    @NonNull
    public static AniListApi.Cancelable findTmdbEpisodes(
            @NonNull Anime anime,
            @NonNull String startDate,
            @NonNull AniListApi.Callback<List<EpisodeInfo>> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        POOL.execute(() -> {
            List<EpisodeInfo> list;
            try {
                list = episodesFromTmdb(anime, startDate, cancelled);
            } catch (Exception e) {
                list = new ArrayList<>();
            }
            if (!cancelled.get()) {
                callback.onSuccess(list);
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
    // TMDB episodes
    // ---------------------------------------------------------------------------------------

    @Nullable
    private static LocalDate parseStart(@NonNull String s) {
        try {
            String[] p = s.trim().split("\\.");
            if (p.length == 3) {
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            if (p.length == 2) {
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 1);
            }
            if (p.length == 1 && !p[0].isEmpty()) {
                return LocalDate.of(Integer.parseInt(p[0]), 1, 1);
            }
        } catch (Exception ignored) {
            // Unknown start date.
        }
        return null;
    }

    @Nullable
    private static LocalDate parseIso(@Nullable String s) {
        try {
            return s == null || s.length() < 10 ? null : LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /** TMDB id of the series this anime belongs to (later seasons are matched without a year). */
    private static int tvIdFor(@NonNull Anime anime, @NonNull String key, @NonNull AtomicBoolean cancelled)
            throws Exception {
        Integer cached = TV_IDS.get(anime.getId());
        if (cached != null) {
            return cached;
        }
        Set<String> titles = new LinkedHashSet<>();
        addTitle(titles, anime.getTitleEnglish());
        addTitle(titles, anime.getTitleRomaji());
        final List<String> known = new ArrayList<>(titles);
        addTitle(titles, anime.getTitleNative());
        for (String title : titles) {
            if (cancelled.get()) {
                break;
            }
            String url = TMDB + "/search/tv?query=" + URLEncoder.encode(title, "UTF-8")
                    + "&include_adult=false";
            JSONObject match = pickMatch(get(url, key), known);
            if (match != null) {
                TV_IDS.put(anime.getId(), match.optInt("id"));
                return match.optInt("id");
            }
        }
        return 0;
    }

    @NonNull
    private static List<EpisodeInfo> episodesFromTmdb(
            @NonNull Anime anime, @NonNull String startDate, @NonNull AtomicBoolean cancelled)
            throws Exception {
        List<EpisodeInfo> out = new ArrayList<>();
        String key = BuildConfig.TMDB_API_KEY;
        if (key == null || key.isEmpty() || "MOVIE".equals(anime.getFormat())) {
            return out;
        }
        int tvId = tvIdFor(anime, key, cancelled);
        if (tvId == 0 || cancelled.get()) {
            return out;
        }
        LocalDate start = parseStart(startDate);

        // Which TMDB season is this? The last one that began on or before this anime started.
        JSONArray seasons = get(TMDB + "/tv/" + tvId, key).optJSONArray("seasons");
        int chosen = -1;
        LocalDate chosenDate = null;
        for (int i = 0; seasons != null && i < seasons.length(); i++) {
            JSONObject sn = seasons.optJSONObject(i);
            if (sn == null || sn.optInt("season_number") <= 0) {
                continue;
            }
            LocalDate air = parseIso(sn.optString("air_date", null));
            if (chosen == -1) {
                chosen = sn.optInt("season_number");
                chosenDate = air;
            }
            if (start != null && air != null && !air.isAfter(start.plusDays(45))
                    && (chosenDate == null || air.isAfter(chosenDate) || !chosenDate.isAfter(air))) {
                chosen = sn.optInt("season_number");
                chosenDate = air;
            }
        }
        if (chosen < 0) {
            return out;
        }

        JSONArray eps = get(TMDB + "/tv/" + tvId + "/season/" + chosen, key).optJSONArray("episodes");
        int limit = anime.getEpisodes() > 0 ? anime.getEpisodes() : Integer.MAX_VALUE;
        int number = 0;
        for (int i = 0; eps != null && i < eps.length() && number < limit; i++) {
            JSONObject e = eps.optJSONObject(i);
            if (e == null) {
                continue;
            }
            LocalDate air = parseIso(e.optString("air_date", null));
            // Split seasons ("Part 2") start part-way through the TMDB season.
            if (start != null && air != null && air.isBefore(start.minusDays(14))) {
                continue;
            }
            number++;
            String name = e.optString("name", "").trim();
            if (name.matches("(?i)episode\\s*\\d+")) {
                name = "";
            }
            String still = e.isNull("still_path") ? "" : e.optString("still_path", "");
            String overview = e.optString("overview", "").trim();
            out.add(new EpisodeInfo(
                    number,
                    name.isEmpty() ? null : name,
                    still.isEmpty() ? null : "https://image.tmdb.org/t/p/w300" + still,
                    e.isNull("runtime") ? 0 : e.optInt("runtime"),
                    air == null ? null : String.format(Locale.US, "%04d.%02d.%02d",
                            air.getYear(), air.getMonthValue(), air.getDayOfMonth()),
                    overview.isEmpty() ? null : overview));
        }
        return out;
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
