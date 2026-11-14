package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Backup trailer source. When AniList has no YouTube trailer for an anime, MyAnimeList (through
 * the free Jikan API, no key needed) is asked for one using the anime's MAL id.
 */
public final class TrailerApi {

    private static final String ENDPOINT = "https://api.jikan.moe/v4/anime/";

    private TrailerApi() {
    }

    /** Calls back with a YouTube id, or an error when MAL has no embeddable trailer either. */
    @NonNull
    public static AniListApi.Cancelable findYoutubeId(
            int malId, @NonNull AniListApi.Callback<String> callback) {
        Request request = new Request.Builder()
                .url(ENDPOINT + malId)
                .header("Accept", "application/json")
                .build();
        final Call call = HttpClientProvider.get().newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call c, @NonNull IOException e) {
                if (!c.isCanceled()) {
                    callback.onError(e);
                }
            }

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response response) {
                final String id;
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        throw new IOException("MyAnimeList error (HTTP " + r.code() + ")");
                    }
                    ResponseBody body = r.body();
                    JSONObject root = new JSONObject(body != null ? body.string() : "{}");
                    JSONObject data = root.optJSONObject("data");
                    JSONObject trailer = data != null ? data.optJSONObject("trailer") : null;
                    String youtube = (trailer == null || trailer.isNull("youtube_id"))
                            ? null
                            : trailer.optString("youtube_id", null);
                    if (youtube == null || youtube.isEmpty()) {
                        throw new IOException("No trailer on MyAnimeList");
                    }
                    id = youtube;
                } catch (Exception e) {
                    if (!c.isCanceled()) {
                        callback.onError(e);
                    }
                    return;
                }
                callback.onSuccess(id);
            }
        });
        return call::cancel;
    }
}
