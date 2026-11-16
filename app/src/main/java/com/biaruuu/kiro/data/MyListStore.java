package com.biaruuu.kiro.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * On-device storage for "My List" and the user's own star ratings. Lives in SharedPreferences,
 * so it survives restarts and needs no account.
 */
public final class MyListStore {

    private static final String FILE = "kiro_user_data";
    private static final String KEY_LIST = "my_list";
    private static final String KEY_RATINGS = "ratings";
    private static final String KEY_RATED_META = "rated_meta";

    /** What the list remembers about an anime. */
    public static final class Entry {
        public final int id;
        public final String title;
        public final String posterUrl;
        /** When it was added to My List / rated (ms). */
        public final long addedAt;
        /** AniList average score 0-100 when it was saved. */
        public final int score;
        /** The user's own stars (1-5), 0 when not rated. */
        public final int rating;

        Entry(int id, String title, String posterUrl, long addedAt, int score, int rating) {
            this.id = id;
            this.title = title;
            this.posterUrl = posterUrl;
            this.addedAt = addedAt;
            this.score = score;
            this.rating = rating;
        }
    }

    private MyListStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static JSONObject read(SharedPreferences p, String key) {
        try {
            return new JSONObject(p.getString(key, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static boolean isInList(@NonNull Context context, int animeId) {
        return read(prefs(context), KEY_LIST).has(String.valueOf(animeId));
    }

    /** Adds the anime if missing, removes it if present. Returns true when it is now in the list. */
    public static boolean toggle(@NonNull Context context, @NonNull Anime anime) {
        if (!CloudSync.allowed(context)) return false;
        SharedPreferences p = prefs(context);
        JSONObject list = read(p, KEY_LIST);
        String key = String.valueOf(anime.getId());
        boolean nowInList;
        if (list.has(key)) {
            list.remove(key);
            nowInList = false;
        } else {
            try {
                JSONObject entry = new JSONObject();
                entry.put("title", anime.getDisplayTitle());
                entry.put("poster", anime.getPosterUrl() != null ? anime.getPosterUrl() : "");
                entry.put("at", System.currentTimeMillis());
                entry.put("score", anime.getAverageScore());
                list.put(key, entry);
            } catch (JSONException ignored) {
                return false;
            }
            nowInList = true;
        }
        p.edit().putString(KEY_LIST, list.toString()).apply();
        CloudSync.libraryChanged(context);
        return nowInList;
    }

    /** Everything in My List, in no particular order (the Library tab sorts it). */
    @NonNull
    public static List<Entry> all(@NonNull Context context) {
        JSONObject list = read(prefs(context), KEY_LIST);
        List<Entry> out = new ArrayList<>();
        Iterator<String> keys = list.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject e = list.optJSONObject(key);
            if (e != null) {
                try {
                    int id = Integer.parseInt(key);
                    out.add(new Entry(id, e.optString("title"), e.optString("poster"),
                            e.optLong("at"), e.optInt("score"), getRating(context, id)));
                } catch (NumberFormatException ignored) {
                    // skip a corrupt key
                }
            }
        }
        return out;
    }

    /** The user's stars (1-5) for this anime, or 0 when not rated. */
    public static int getRating(@NonNull Context context, int animeId) {
        return read(prefs(context), KEY_RATINGS).optInt(String.valueOf(animeId), 0);
    }

    /** Saves 1-5 stars; 0 clears the rating. */
    public static void setRating(@NonNull Context context, int animeId, int stars) {
        if (!CloudSync.allowed(context)) return;
        SharedPreferences p = prefs(context);
        JSONObject ratings = read(p, KEY_RATINGS);
        String key = String.valueOf(animeId);
        try {
            if (stars <= 0) {
                ratings.remove(key);
            } else {
                ratings.put(key, Math.min(stars, 5));
            }
        } catch (JSONException ignored) {
            return;
        }
        p.edit().putString(KEY_RATINGS, ratings.toString()).apply();
        CloudSync.libraryChanged(context);
    }

    /** Takes an anime out of My List. */
    public static void remove(@NonNull Context context, int animeId) {
        SharedPreferences p = prefs(context);
        JSONObject list = read(p, KEY_LIST);
        list.remove(String.valueOf(animeId));
        p.edit().putString(KEY_LIST, list.toString()).apply();
        CloudSync.libraryChanged(context);
    }

    /** Saves 1-5 stars and remembers the title and poster so the Rated tab can show it. */
    public static void setRating(@NonNull Context context, @NonNull Anime anime, int stars) {
        if (!CloudSync.allowed(context)) return;
        setRating(context, anime.getId(), stars);
        SharedPreferences p = prefs(context);
        JSONObject meta = read(p, KEY_RATED_META);
        String key = String.valueOf(anime.getId());
        try {
            if (stars <= 0) {
                meta.remove(key);
            } else {
                JSONObject e = new JSONObject();
                e.put("title", anime.getDisplayTitle());
                e.put("poster", anime.getPosterUrl() != null ? anime.getPosterUrl() : "");
                e.put("at", System.currentTimeMillis());
                e.put("score", anime.getAverageScore());
                meta.put(key, e);
            }
        } catch (JSONException ignored) {
            return;
        }
        p.edit().putString(KEY_RATED_META, meta.toString()).apply();
        CloudSync.libraryChanged(context);
    }

    /** Everything the user has rated (newest first). */
    @NonNull
    public static List<Entry> allRated(@NonNull Context context) {
        SharedPreferences p = prefs(context);
        JSONObject meta = read(p, KEY_RATED_META);
        JSONObject ratings = read(p, KEY_RATINGS);
        List<Entry> out = new ArrayList<>();
        Iterator<String> keys = meta.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject e = meta.optJSONObject(key);
            int stars = ratings.optInt(key, 0);
            if (e == null || stars <= 0) {
                continue;
            }
            try {
                out.add(new Entry(Integer.parseInt(key), e.optString("title"), e.optString("poster"),
                        e.optLong("at"), e.optInt("score"), stars));
            } catch (NumberFormatException ignored) {
                // skip a corrupt key
            }
        }
        java.util.Collections.sort(out, (x, y) -> Long.compare(y.addedAt, x.addedAt));
        return out;
    }

    /** Forgets the Rated tab's saved title/poster for this anime (used with setRating(id, 0)). */
    public static void clearRatedMeta(@NonNull Context context, int animeId) {
        SharedPreferences p = prefs(context);
        JSONObject meta = read(p, KEY_RATED_META);
        meta.remove(String.valueOf(animeId));
        p.edit().putString(KEY_RATED_META, meta.toString()).apply();
        CloudSync.libraryChanged(context);
    }
}
