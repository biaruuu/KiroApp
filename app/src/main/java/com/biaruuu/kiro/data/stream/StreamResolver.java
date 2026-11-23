package com.biaruuu.kiro.data.stream;

import android.content.Context;

import androidx.annotation.NonNull;

import com.biaruuu.kiro.core.NativeCore;
import com.biaruuu.kiro.data.AniListApi;
import com.biaruuu.kiro.data.HttpClientProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Asks every provider at the same time, measures how fast each address answers, drops the ones
 * that don't, and orders the rest best first (resolution and speed, scored in Rust). The player
 * plays the first and falls back to the next one if it fails.
 */
public final class StreamResolver {

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final int PROVIDER_TIMEOUT_S = 32;
    /** Once something playable has arrived, slower providers get this long to beat it. */
    private static final long PROVIDER_GRACE_MS = 2000L;
    /** ...and only this long when the first answer is already sharp (1080p+ and quick). */
    private static final long PROVIDER_GRACE_FAST_MS = 700L;
    private static final int PROBE_TIMEOUT_S = 5;
    private static final long CACHE_TTL_MS = 4 * 60_000L;

    private static final class Cached {
        final long at;
        final List<StreamSource> sources;

        Cached(long at, List<StreamSource> sources) {
            this.at = at;
            this.sources = sources;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Cached> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean filesStillThere(List<StreamSource> list) {
        for (StreamSource s : list) {
            for (SubtitleTrack t : s.getSubtitles()) {
                if (t.getUrl().startsWith("file:") && !new java.io.File(android.net.Uri.parse(t.getUrl()).getPath()).exists()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String key(StreamRequest r) {
        return r.getAnimeId() + ":" + r.getEpisode();
    }

    /** Throws away the saved answer for this episode (use before retrying a failed one). */
    public static void forget(int animeId, int episode) {
        CACHE.remove(animeId + ":" + episode);
    }

    private StreamResolver() {
    }

    @NonNull
    public static AniListApi.Cancelable resolve(
            @NonNull Context context,
            @NonNull StreamRequest request,
            @NonNull AniListApi.Callback<List<StreamSource>> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final Context app = context.getApplicationContext();
        POOL.execute(() -> {
            try {
                Cached hit = CACHE.get(key(request));
                if (hit != null && System.currentTimeMillis() - hit.at < CACHE_TTL_MS && filesStillThere(hit.sources)) {
                    if (!cancelled.get()) {
                        callback.onSuccess(hit.sources);
                    }
                    return;
                }
                List<StreamSource> ranked = run(app, request);
                if (!ranked.isEmpty()) {
                    CACHE.put(key(request), new Cached(System.currentTimeMillis(), ranked));
                }
                if (!cancelled.get()) {
                    callback.onSuccess(ranked);
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    callback.onError(e);
                }
            }
        });
        return () -> cancelled.set(true);
    }

    private static List<StreamSource> run(Context context, StreamRequest request) throws Exception {
        // 1. All providers at once.
        List<StreamProvider> providers = new ArrayList<>();
        providers.add(new TagalogDubProvider());
        providers.add(new AnivaultProvider());
        providers.add(new AnimetsuProvider());
        providers.add(new AnimexProvider());
        providers.add(new MiruroProvider());
        providers.add(new AnikotoProvider());
        providers.add(new ReanimeProvider());
        providers.add(new AnimePaheProvider());
        providers.add(new AniZoneProvider());
        providers.add(new GojoProvider());

        // Take answers as they arrive; once one has sources, the others get a short grace period.
        java.util.concurrent.ExecutorCompletionService<List<StreamSource>> done =
                new java.util.concurrent.ExecutorCompletionService<>(POOL);
        List<Future<List<StreamSource>>> pending = new ArrayList<>();
        for (StreamProvider p : providers) {
            pending.add(done.submit(() -> p.resolve(request)));
        }
        List<StreamSource> candidates = new ArrayList<>();
        long deadline = System.currentTimeMillis() + PROVIDER_TIMEOUT_S * 1000L;
        int remaining = pending.size();
        try {
            while (remaining > 0) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) {
                    break;
                }
                Future<List<StreamSource>> f = done.poll(wait, TimeUnit.MILLISECONDS);
                if (f == null) {
                    break;
                }
                remaining--;
                try {
                    List<StreamSource> got = f.get();
                    candidates.addAll(got);
                    boolean subbed = false;
                    for (StreamSource c : got) {
                        if (!c.isDub()) {
                            subbed = true;
                            break;
                        }
                    }
                    // (The instant Filipino dub does not start the clock: the subbed streams are the default.)
                    if (subbed) {
                        // Something playable is in: the others get a short chance to beat it, no more.
                        boolean sharp = false;
                        for (StreamSource c : candidates) {
                            if (!c.isDub() && c.getHeight() >= 1080 && c.getLatencyMs() >= 0 && c.getLatencyMs() < 1500) {
                                sharp = true;
                                break;
                            }
                        }
                        long grace = System.currentTimeMillis() + (sharp ? PROVIDER_GRACE_FAST_MS : PROVIDER_GRACE_MS);
                        deadline = Math.min(deadline, grace);
                    }
                } catch (Exception ignored) {
                    // A provider that fails is simply skipped.
                }
            }
        } finally {
            for (Future<List<StreamSource>> f : pending) {
                f.cancel(true);
            }
        }

        // 2. Measure every address at once.
        List<Callable<StreamSource>> probes = new ArrayList<>();
        for (StreamSource s : candidates) {
            probes.add(() -> probe(s));
        }
        List<StreamSource> alive = new ArrayList<>();
        for (Future<StreamSource> f : POOL.invokeAll(probes, PROBE_TIMEOUT_S, TimeUnit.SECONDS)) {
            try {
                StreamSource s = f.get();
                if (s != null) {
                    alive.add(s);
                }
            } catch (Exception ignored) {
                // Unreachable: dropped.
            }
        }

        // 3. Best first.
        int[] heights = new int[alive.size()];
        int[] latencies = new int[alive.size()];
        boolean[] hls = new boolean[alive.size()];
        for (int i = 0; i < alive.size(); i++) {
            heights[i] = alive.get(i).getHeight();
            latencies[i] = alive.get(i).getLatencyMs();
            hls[i] = alive.get(i).isHls();
        }
        int[] order = NativeCore.rankStreams(heights, latencies, hls);
        // Subtitled first (the default), dubbed after; best-first order is kept inside each group.
        List<StreamSource> ranked = new ArrayList<>();
        List<StreamSource> dubs = new ArrayList<>();
        for (int index : order) {
            if (index >= 0 && index < alive.size()) {
                StreamSource s = alive.get(index);
                if (s.isDub()) {
                    dubs.add(dubCaptions(s));
                } else {
                    ranked.add(s);
                }
            }
        }
        ranked.addAll(dubs);
        prefetchSubtitles(context, ranked);
        return ranked;
    }

    /**
     * English and Japanese caption files of the first few streams are downloaded before the picture starts,
     * so captions are on screen from the first second (a file on the phone, nothing to wait for) instead of
     * being fetched from another server after play begins.
     */
    private static void prefetchSubtitles(Context context, List<StreamSource> ranked) {
        java.io.File dir = new java.io.File(context.getCacheDir(), "subs");
        dir.mkdirs();
        int limit = Math.min(ranked.size(), 3);
        List<Callable<Object[]>> jobs = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            final StreamSource src = ranked.get(i);
            List<SubtitleTrack> tracks = src.getSubtitles();
            for (int j = 0; j < tracks.size(); j++) {
                final SubtitleTrack t = tracks.get(j);
                String lang = t.getLanguage().toLowerCase(java.util.Locale.ROOT);
                String label = t.getLabel().toLowerCase(java.util.Locale.ROOT);
                boolean wanted = lang.startsWith("en") || lang.startsWith("ja") || label.startsWith("english") || label.startsWith("japanese");
                if (!wanted) {
                    continue;
                }
                final int si = i;
                final int ti = j;
                jobs.add(() -> new Object[]{si, ti, fetchSubtitle(dir, src, t)});
            }
        }
        if (jobs.isEmpty()) {
            return;
        }
        List<List<SubtitleTrack>> next = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            next.add(new ArrayList<>(ranked.get(i).getSubtitles()));
        }
        try {
            for (Future<Object[]> f : POOL.invokeAll(jobs, 5, TimeUnit.SECONDS)) {
                try {
                    Object[] r = f.get();
                    next.get((Integer) r[0]).set((Integer) r[1], (SubtitleTrack) r[2]);
                } catch (Exception ignored) {
                    // Not fetched in time: that track stays a normal address.
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (int i = 0; i < limit; i++) {
            ranked.set(i, ranked.get(i).withSubtitles(next.get(i)));
        }
    }

    private static SubtitleTrack fetchSubtitle(java.io.File dir, StreamSource src, SubtitleTrack t) {
        String url = t.getUrl();
        if (!url.startsWith("http")) {
            return t;
        }
        String ext = t.getMimeType().contains("vtt") ? ".vtt" : ".srt";
        java.io.File f = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ext);
        try {
            if (!f.exists() || f.length() == 0) {
                OkHttpClient client = HttpClientProvider.get().newBuilder().callTimeout(4, TimeUnit.SECONDS).build();
                Request.Builder rb = new Request.Builder().url(url);
                for (java.util.Map.Entry<String, String> h : src.getHeaders().entrySet()) {
                    rb.header(h.getKey(), h.getValue());
                }
                try (Response r = client.newCall(rb.build()).execute()) {
                    if (!r.isSuccessful() || r.body() == null) {
                        return t;
                    }
                    byte[] b = r.body().bytes();
                    if (b.length == 0 || b.length > 3_000_000) {
                        return t;
                    }
                    String head = new String(b, 0, Math.min(b.length, 64), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(java.util.Locale.ROOT);
                    if (head.startsWith("<")) {
                        return t; // a web page, not captions
                    }
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    try {
                        out.write(b);
                    } finally {
                        out.close();
                    }
                }
            }
            return new SubtitleTrack(android.net.Uri.fromFile(f).toString(), t.getLabel(), t.getLanguage(), t.getMimeType());
        } catch (Exception e) {
            return t;
        }
    }

    /**
     * English captions that providers attach to a dub are the subbed episode's script (different words,
     * sometimes different timing from what the actors say), so they are dropped. Captions that say
     * they belong to the dub (CC / SDH) and captions in other languages stay. The Filipino dub brings its own.
     */
    private static StreamSource dubCaptions(StreamSource s) {
        if (s.getProviderName().startsWith("Filipino") || s.getSubtitles().isEmpty()) {
            return s;
        }
        List<SubtitleTrack> keep = new ArrayList<>();
        for (SubtitleTrack t : s.getSubtitles()) {
            String label = t.getLabel().toLowerCase(java.util.Locale.ROOT);
            boolean english = t.getLanguage().toLowerCase(java.util.Locale.ROOT).startsWith("en") || label.startsWith("english");
            boolean forDub = label.contains("cc") || label.contains("sdh") || label.contains("dub") || label.contains("closed");
            if (!english || forDub) {
                keep.add(t);
            }
        }
        return keep.size() == s.getSubtitles().size() ? s : s.withSubtitles(keep);
    }

    /** Time to the first byte. Local files count as instant; unreachable addresses return null. */
    private static StreamSource probe(StreamSource source) throws Exception {
        if (source.getLatencyMs() >= 0) {
            return source; // the provider already timed it
        }
        if (!source.getUrl().startsWith("http")) {
            return source.withLatency(0);
        }
        OkHttpClient client = HttpClientProvider.get().newBuilder()
                .callTimeout(PROBE_TIMEOUT_S - 1, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(source.getUrl())
                .header("Range", "bytes=0-1")
                .build();
        long start = System.nanoTime();
        try (Response r = client.newCall(request).execute()) {
            if (!r.isSuccessful()) {
                return trusted(source);
            }
            int ms = (int) ((System.nanoTime() - start) / 1_000_000L);
            return source.withLatency(ms);
        } catch (Exception e) {
            return trusted(source);
        }
    }

    /**
     * Addresses from the Anivault API were already checked by the provider (it read the playlist),
     * so a slow probe only ranks them last instead of dropping them. Other addresses are dropped.
     */
    private static StreamSource trusted(StreamSource source) {
        return source.getProviderName().startsWith("Anivault") || source.getProviderName().startsWith("Filipino") ? source.withLatency(4000) : null;
    }
}
