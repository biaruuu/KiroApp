package com.biaruuu.kiro.data.stream;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Video addresses the user adds themselves, one per line. {id} is the AniList id, {episode} the
 * episode number. For example {@code https://my-server.example/{id}/{episode}.m3u8}.
 */
public final class StreamSettings {

    private static final String PREFS = "kiro_user_data";
    private static final String KEY = "stream_templates";

    private StreamSettings() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public static String getRaw(@NonNull Context c) {
        return prefs(c).getString(KEY, "");
    }

    public static void setRaw(@NonNull Context c, @NonNull String value) {
        prefs(c).edit().putString(KEY, value).apply();
    }

    @NonNull
    public static List<String> getTemplates(@NonNull Context c) {
        List<String> out = new ArrayList<>();
        for (String line : getRaw(c).split("\n")) {
            String t = line.trim();
            if (t.startsWith("http://") || t.startsWith("https://")) {
                out.add(t);
            }
        }
        return out;
    }
}
