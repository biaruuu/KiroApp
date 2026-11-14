package com.biaruuu.kiro.data.stream;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One playable video address offered by a provider. */
public final class StreamSource {

    private final String providerName;
    private final String url;
    private final int height;
    private final boolean hls;
    private final List<SubtitleTrack> subtitles;
    private final int latencyMs;

    public StreamSource(
            @NonNull String providerName,
            @NonNull String url,
            int height,
            boolean hls,
            @NonNull List<SubtitleTrack> subtitles) {
        this(providerName, url, height, hls, subtitles, -1);
    }

    private StreamSource(
            String providerName, String url, int height, boolean hls, List<SubtitleTrack> subtitles, int latencyMs) {
        this.providerName = providerName;
        this.url = url;
        this.height = height;
        this.hls = hls;
        this.subtitles = Collections.unmodifiableList(new ArrayList<>(subtitles));
        this.latencyMs = latencyMs;
    }

    @NonNull
    public StreamSource withLatency(int ms) {
        return new StreamSource(providerName, url, height, hls, subtitles, ms);
    }

    @NonNull
    public String getProviderName() {
        return providerName;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    /** Vertical resolution in pixels (the best one for adaptive streams); 0 when unknown. */
    public int getHeight() {
        return height;
    }

    public boolean isHls() {
        return hls;
    }

    @NonNull
    public List<SubtitleTrack> getSubtitles() {
        return subtitles;
    }

    /** Measured time to the first byte, in ms; -1 if not measured. */
    public int getLatencyMs() {
        return latencyMs;
    }

    @NonNull
    public String getQualityLabel() {
        return height > 0 ? height + "p" : "Auto";
    }
}
