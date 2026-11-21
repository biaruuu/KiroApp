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
    private static final long PROVIDER_GRACE_MS = 5000L;
    private static final int PROBE_TIMEOUT_S = 8;

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
                List<StreamSource> ranked = run(app, request);
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
        // The developer's own Tagalog dub is never cut short by the grace period: it is the
        // only copy of that audio, and its host (archive.org) can be slow to answer.
        Future<List<StreamSource>> own = pending.get(0);
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
                    if (!got.isEmpty() && candidates.isEmpty() && own.isDone()) {
                        deadline = Math.min(deadline, System.currentTimeMillis() + PROVIDER_GRACE_MS);
                    }
                    candidates.addAll(got);
                    if (!candidates.isEmpty() && own.isDone() && deadline - System.currentTimeMillis() > PROVIDER_GRACE_MS) {
                        deadline = System.currentTimeMillis() + PROVIDER_GRACE_MS;
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

        // Tagalog dub carries no captions of its own: lend it English ones from the other sources.
        List<SubtitleTrack> english = new ArrayList<>();
        for (StreamSource c : candidates) {
            if (c.getProviderName().startsWith("Tagalog")) {
                continue;
            }
            for (SubtitleTrack t : c.getSubtitles()) {
                String l = (t.getLanguage() + " " + t.getLabel()).toLowerCase(java.util.Locale.ROOT);
                if ((l.contains("english") || l.matches(".*\\ben\\b.*")) && !l.contains("forced")) {
                    english.add(t);
                }
            }
            if (english.size() >= 3) {
                break;
            }
        }
        if (!english.isEmpty()) {
            for (int i = 0; i < candidates.size(); i++) {
                StreamSource c = candidates.get(i);
                if (c.getProviderName().startsWith("Tagalog") && c.getSubtitles().isEmpty()) {
                    candidates.set(i, c.withSubtitles(english));
                }
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
                (s.isDub() ? dubs : ranked).add(s);
            }
        }
        ranked.addAll(dubs);
        return ranked;
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
        return source.getProviderName().startsWith("Anivault") || source.getProviderName().startsWith("Tagalog") ? source.withLatency(4000) : null;
    }
}
