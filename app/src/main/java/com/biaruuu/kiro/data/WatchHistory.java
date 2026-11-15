package com.biaruuu.kiro.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Continue watching": the latest episode and position per anime, newest first, kept on the
 * device. Positions under 10 seconds are not worth resuming; finished episodes are dropped.
 */
public final class WatchHistory {

    private static final String PREFS = "kiro_user_data";
    private static final String KEY = "watch_history";
    private static final int MAX = 20;
    private static final long MIN_RESUME_MS = 10_000L;

    private WatchHistory() {
    }

    public static final class Entry {
        public final int animeId;
        public final int episode;
        public final long positionMs;
        public final long durationMs;
        @NonNull public final String title;
        @Nullable public final String image;
        @Nullable public final String thumb;
        public final long updatedAt;

        public Entry(int animeId, int episode, long positionMs, long durationMs,
                     @NonNull String title, @Nullable String image, @Nullable String thumb, long updatedAt) {
            this.animeId = animeId;
            this.episode = episode;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.title = title;
            this.image = image;
            this.thumb = thumb;
            this.updatedAt = updatedAt;
        }

        /** 0..1 of the episode that has been watched. */
        public float progress() {
            return durationMs > 0 ? Math.min(1f, (float) positionMs / durationMs) : 0f;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Newest first. */
    @NonNull
    public static synchronized List<Entry> all(@NonNull Context context) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(
                        o.getInt("a"), o.getInt("e"), o.optLong("p"), o.optLong("d"),
                        o.optString("t", ""),
                        o.isNull("i") ? null : o.optString("i", null),
                        o.isNull("h") ? null : o.optString("h", null),
                        o.optLong("u")));
            }
        } catch (Exception ignored) {
            // Corrupt data: start over.
        }
        Collections.sort(out, (x, y) -> Long.compare(y.updatedAt, x.updatedAt));
        return out;
    }

    /** Saves progress for an anime (replacing its older entry). */
    public static synchronized void save(@NonNull Context context, @NonNull Entry e) {
        List<Entry> list = all(context);
        List<Entry> next = new ArrayList<>();
        next.add(e);
        for (Entry old : list) {
            if (old.animeId != e.animeId && next.size() < MAX) {
                next.add(old);
            }
        }
        write(context, next);
    }

    public static synchronized void remove(@NonNull Context context, int animeId) {
        List<Entry> next = new ArrayList<>();
        for (Entry old : all(context)) {
            if (old.animeId != animeId) {
                next.add(old);
            }
        }
        write(context, next);
    }

    /** Where to resume this episode (0 = from the start). */
    public static long positionFor(@NonNull Context context, int animeId, int episode) {
        for (Entry e : all(context)) {
            if (e.animeId == animeId && e.episode == episode) {
                boolean nearEnd = e.durationMs > 0 && e.positionMs >= e.durationMs * 0.93;
                return (e.positionMs < MIN_RESUME_MS || nearEnd) ? 0L : e.positionMs;
            }
        }
        return 0L;
    }

    private static void write(Context context, List<Entry> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : list) {
                arr.put(new JSONObject()
                        .put("a", e.animeId).put("e", e.episode)
                        .put("p", e.positionMs).put("d", e.durationMs)
                        .put("t", e.title)
                        .put("i", e.image == null ? JSONObject.NULL : e.image)
                        .put("h", e.thumb == null ? JSONObject.NULL : e.thumb)
                        .put("u", e.updatedAt));
            }
            prefs(context).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
            // Best effort.
        }
    }
}
