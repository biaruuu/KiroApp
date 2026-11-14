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

    /** What the list remembers about an anime. */
    public static final class Entry {
        public final int id;
        public final String title;
        public final String posterUrl;

        Entry(int id, String title, String posterUrl) {
            this.id = id;
            this.title = title;
            this.posterUrl = posterUrl;
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
                list.put(key, entry);
            } catch (JSONException ignored) {
                return false;
            }
            nowInList = true;
        }
        p.edit().putString(KEY_LIST, list.toString()).apply();
        return nowInList;
    }

    /** Everything in the list (used by the Library tab later). */
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
                    out.add(new Entry(Integer.parseInt(key), e.optString("title"), e.optString("poster")));
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
    }
}
