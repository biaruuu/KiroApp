package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Episode names, air dates and thumbnails from MyAnimeList (Jikan). Every season has its own MAL
 * entry, so the list always matches the season being viewed.
 */
public final class EpisodeApi {

    private static final String BASE = "https://api.jikan.moe/v4/anime/";
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private EpisodeApi() {
    }

    @NonNull
    public static AniListApi.Cancelable load(
            int malId, @NonNull AniListApi.Callback<List<EpisodeInfo>> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        POOL.execute(() -> {
            try {
                // Names and thumbnails are separate lists on MAL; fetch both at once.
                final Map<Integer, String> thumbs = new HashMap<>();
                Thread thumbThread = new Thread(() -> {
                    try {
                        pages(malId + "/videos/episodes", 4, cancelled, item -> {
                            JSONObject jpg = item.optJSONObject("images") != null
                                    ? item.optJSONObject("images").optJSONObject("jpg") : null;
                            String url = jpg != null ? jpg.optString("image_url", "") : "";
                            if (!url.isEmpty()) {
                                synchronized (thumbs) {
                                    thumbs.put(item.optInt("mal_id"), url);
                                }
                            }
                        });
                    } catch (Exception ignored) {
                        // Thumbnails are optional.
                    }
                });
                thumbThread.start();

                final List<EpisodeInfo> raw = new ArrayList<>();
                final List<String> titles = new ArrayList<>();
                final List<Integer> numbers = new ArrayList<>();
                final List<String> dates = new ArrayList<>();
                pages(malId + "/episodes", 20, cancelled, item -> {
                    numbers.add(item.optInt("mal_id"));
                    titles.add(item.isNull("title") ? null : item.optString("title", null));
                    String aired = item.isNull("aired") ? "" : item.optString("aired", "");
                    dates.add(aired.length() >= 10 ? aired.substring(0, 10).replace('-', '.') : null);
                });
                thumbThread.join(8000);

                for (int i = 0; i < numbers.size(); i++) {
                    String thumb;
                    synchronized (thumbs) {
                        thumb = thumbs.get(numbers.get(i));
                    }
                    raw.add(new EpisodeInfo(numbers.get(i), clean(titles.get(i)), thumb, 0, dates.get(i)));
                }
                if (!cancelled.get()) {
                    callback.onSuccess(raw);
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    callback.onError(e);
                }
            }
        });
        return () -> cancelled.set(true);
    }

    /** MAL uses "Episode 4" as a placeholder name; treat that as no name. */
    @Nullable
    private static String clean(@Nullable String title) {
        if (title == null) {
            return null;
        }
        String t = title.trim();
        return t.isEmpty() || t.matches("(?i)episode\\s*\\d+") ? null : t;
    }

    private interface ItemSink {
        void accept(JSONObject item);
    }

    private static void pages(String path, int maxPages, AtomicBoolean cancelled, ItemSink sink)
            throws Exception {
        for (int page = 1; page <= maxPages && !cancelled.get(); page++) {
            JSONObject root = fetch(BASE + path + "?page=" + page);
            JSONArray data = root.optJSONArray("data");
            for (int i = 0; data != null && i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item != null) {
                    sink.accept(item);
                }
            }
            JSONObject pagination = root.optJSONObject("pagination");
            if (pagination == null || !pagination.optBoolean("has_next_page")) {
                return;
            }
            Thread.sleep(400); // Jikan allows about 3 requests per second
        }
    }

    private static JSONObject fetch(String url) throws Exception {
        for (int attempt = 0; ; attempt++) {
            Request request = new Request.Builder().url(url).header("Accept", "application/json").build();
            try (Response r = HttpClientProvider.get().newCall(request).execute()) {
                if (r.code() == 429 && attempt < 2) {
                    Thread.sleep(1200);
                    continue;
                }
                if (!r.isSuccessful()) {
                    throw new IOException("HTTP " + r.code());
                }
                ResponseBody b = r.body();
                return new JSONObject(b != null ? b.string() : "{}");
            }
        }
    }
}
