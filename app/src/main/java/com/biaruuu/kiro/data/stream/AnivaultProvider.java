package com.biaruuu.kiro.data.stream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biaruuu.kiro.core.NativeCore;
import com.biaruuu.kiro.data.HttpClientProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Streams from the Anivault API (https://github.com/biaruuu/Anivault-API) hosted on Render.
 *
 * <p>For one episode it asks every scraper source (Anikoto, MegaPlay, DesiDub, AnimeHeaven) for
 * both sub and dub at the same time. It returns as soon as the first answer is in plus a short
 * grace period for the others, so one slow source never holds playback back. Each answer is a
 * proxied HLS / MP4 address (clean CORS and headers), and the best resolution is read from the
 * HLS master playlist so the ranking can prefer the sharpest picture.
 */
public final class AnivaultProvider implements StreamProvider {

    public static final String BASE_URL = "https://kiroapp-api.onrender.com";

    private static final String[] SOURCES = {"megaplay", "anikoto", "desidub"};
    private static final String[] AUDIO = {"sub", "dub"};
    private static final int FIRST_RESULT_WAIT_S = 28;
    private static final int GRACE_MS = 3500;
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=\\d+x(\\d+)");

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    @NonNull
    @Override
    public String getName() {
        return "Anivault";
    }

    /** Wakes the (free-tier) server early so the first episode doesn't wait for a cold start. */
    public static void warmUp() {
        POOL.execute(() -> {
            try {
                Request r = new Request.Builder().url(BASE_URL + "/api/health").build();
                try (Response ignored = HttpClientProvider.get().newCall(r).execute()) {
                    // Nothing to read; the request itself is the wake-up call.
                }
            } catch (Exception ignored) {
                // Best effort.
            }
        });
    }

    @NonNull
    @Override
    public List<StreamSource> resolve(@NonNull StreamRequest request) {
        final OkHttpClient client = HttpClientProvider.get().newBuilder()
                .callTimeout(FIRST_RESULT_WAIT_S, TimeUnit.SECONDS)
                .readTimeout(FIRST_RESULT_WAIT_S, TimeUnit.SECONDS)
                .build();

        ExecutorCompletionService<StreamSource> done = new ExecutorCompletionService<>(POOL);
        List<Future<StreamSource>> pending = new ArrayList<>();
        for (String source : SOURCES) {
            for (String audio : AUDIO) {
                final String s = source;
                final String a = audio;
                Callable<StreamSource> job = () -> fetch(client, request, s, a);
                pending.add(done.submit(job));
            }
        }

        List<StreamSource> out = new ArrayList<>();
        long deadline = System.currentTimeMillis() + FIRST_RESULT_WAIT_S * 1000L;
        int remaining = pending.size();
        try {
            while (remaining > 0) {
                long wait = out.isEmpty()
                        ? deadline - System.currentTimeMillis()
                        : GRACE_MS;
                if (wait <= 0) {
                    break;
                }
                Future<StreamSource> f = done.poll(wait, TimeUnit.MILLISECONDS);
                if (f == null) {
                    break; // grace period over, or nothing answered in time
                }
                remaining--;
                try {
                    StreamSource s = f.get();
                    if (s != null) {
                        out.add(s);
                    }
                } catch (Exception ignored) {
                    // This source failed: skipped.
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            for (Future<StreamSource> f : pending) {
                f.cancel(true);
            }
        }
        return out;
    }

    @Nullable
    private static StreamSource fetch(OkHttpClient client, StreamRequest req, String source, String audio) {
        // MegaPlay is keyed by the MAL id of this exact season, so it can't pick the wrong one.
        // The title-searching sources are checked against the season first (see seasonMatches).
        boolean byMal = "megaplay".equals(source) && req.getMalId() > 0;
        String idParam = byMal ? "malId=" + req.getMalId() : "anilistId=" + req.getAnimeId();
        if (!byMal && !"megaplay".equals(source) && !seasonMatches(client, req, source)) {
            return null;
        }
        String url = BASE_URL + "/api/watch?source=" + source
                + "&" + idParam
                + "&ep=" + req.getEpisode()
                + "&type=" + audio;
        try (Response r = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                return null;
            }
            JSONObject json = new JSONObject(r.body().string());
            String mode = json.optString("playbackMode", "");
            String stream;
            boolean hls;
            if ("hls".equals(mode)) {
                stream = json.optString("hlsProxyUrl", "");
                hls = true;
            } else if ("mp4".equals(mode)) {
                stream = json.optString("mp4ProxyUrl", json.optString("streamUrl", ""));
                hls = false;
            } else {
                return null; // iframe-only: nothing ExoPlayer can play
            }
            if (stream.isEmpty() || "null".equals(stream)) {
                return null;
            }

            int height = 0;
            if (hls) {
                height = bestHeight(client, stream);
                if (height < 0) {
                    return null; // playlist unreachable or not a playlist
                }
            }

            String server = json.optString("server", "");
            String label = prettySource(source) + (server.isEmpty() || server.equalsIgnoreCase("MegaPlay Direct")
                    ? "" : " " + server);
            return new StreamSource("Anivault · " + label, stream, height, hls, subtitles(json), audio);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Title-searching sources can land on season 1 when season 2 was asked for. Ask which show
     * the source resolved to and accept it only if its name is the same season (Rust) and it
     * really has this episode.
     */
    private static boolean seasonMatches(OkHttpClient client, StreamRequest req, String source) {
        String url = BASE_URL + "/api/episodes?anilistId=" + req.getAnimeId() + "&source=" + source;
        try (Response r = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                return false;
            }
            JSONObject json = new JSONObject(r.body().string());
            String site = json.optString("siteId", "");
            int count = json.optInt("count", 0);
            if (site.isEmpty() || count < req.getEpisode()) {
                return false;
            }
            String[] names = {req.getTitle(), req.getTitleEnglish(), req.getTitleRomaji()};
            int best = 0;
            for (String n : names) {
                if (n != null && !n.isEmpty()) {
                    best = Math.max(best, NativeCore.seasonMatch(n, site));
                }
            }
            return best >= 65;
        } catch (Exception e) {
            return false;
        }
    }

    /** Highest RESOLUTION in an HLS master playlist (0 if it is a plain media playlist, -1 if bad). */
    private static int bestHeight(OkHttpClient client, String playlistUrl) {
        try (Response r = client.newCall(new Request.Builder().url(playlistUrl).build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                return -1;
            }
            String body = r.body().string();
            if (!body.contains("#EXTM3U")) {
                return -1;
            }
            int best = 0;
            Matcher m = RESOLUTION.matcher(body);
            while (m.find()) {
                best = Math.max(best, Integer.parseInt(m.group(1)));
            }
            return best;
        } catch (Exception e) {
            return -1;
        }
    }

    @NonNull
    private static List<SubtitleTrack> subtitles(JSONObject json) {
        List<SubtitleTrack> out = new ArrayList<>();
        JSONArray arr = json.optJSONArray("subtitles");
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.optJSONObject(i);
            if (t == null) {
                continue;
            }
            String url = t.optString("url", "");
            if (url.isEmpty()) {
                continue;
            }
            String lang = t.optString("lang", t.optString("label", "Subtitles"));
            // The API's subtitle proxy always converts to WebVTT.
            out.add(new SubtitleTrack(url, lang, languageCode(lang), "text/vtt"));
        }
        // English first so it is the default track.
        out.sort((a, b) -> Boolean.compare(!"en".equals(a.getLanguage()), !"en".equals(b.getLanguage())));
        return out;
    }

    private static String languageCode(String label) {
        String l = label.toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("english") || l.equals("en")) return "en";
        if (l.startsWith("spanish") || l.startsWith("espa")) return "es";
        if (l.startsWith("portug")) return "pt";
        if (l.startsWith("french") || l.startsWith("fran")) return "fr";
        if (l.startsWith("german") || l.startsWith("deutsch")) return "de";
        if (l.startsWith("indonesian")) return "id";
        if (l.startsWith("arabic")) return "ar";
        return "und";
    }

    private static String prettySource(String source) {
        switch (source) {
            case "anikoto": return "Anikoto";
            case "megaplay": return "MegaPlay";
            case "desidub": return "DesiDub";
            case "animeheaven": return "AnimeHeaven";
            default: return source;
        }
    }
}
