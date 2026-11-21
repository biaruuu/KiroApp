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
    private final String audio;
    private final java.util.Map<String, String> headers;

    public StreamSource(
            @NonNull String providerName,
            @NonNull String url,
            int height,
            boolean hls,
            @NonNull List<SubtitleTrack> subtitles) {
        this(providerName, url, height, hls, subtitles, "", -1);
    }

    public StreamSource(
            @NonNull String providerName,
            @NonNull String url,
            int height,
            boolean hls,
            @NonNull List<SubtitleTrack> subtitles,
            @NonNull String audio) {
        this(providerName, url, height, hls, subtitles, audio, -1);
    }

    private StreamSource(
            String providerName, String url, int height, boolean hls, List<SubtitleTrack> subtitles,
            String audio, int latencyMs) {
        this(providerName, url, height, hls, subtitles, audio, latencyMs, Collections.<String, String>emptyMap());
    }

    private StreamSource(
            String providerName, String url, int height, boolean hls, List<SubtitleTrack> subtitles,
            String audio, int latencyMs, java.util.Map<String, String> headers) {
        this.headers = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(headers));
        this.audio = audio;
        this.providerName = providerName;
        this.url = url;
        this.height = height;
        this.hls = hls;
        this.subtitles = Collections.unmodifiableList(new ArrayList<>(subtitles));
        this.latencyMs = latencyMs;
    }

    @NonNull
    public StreamSource withLatency(int ms) {
        return new StreamSource(providerName, url, height, hls, subtitles, audio, ms, headers);
    }

    @NonNull
    public StreamSource withSubtitles(@NonNull List<SubtitleTrack> tracks) {
        return new StreamSource(providerName, url, height, hls, tracks, audio, latencyMs, headers);
    }

    /** Request headers (Referer, Origin, User-Agent) the address needs; empty for most. */
    @NonNull
    public java.util.Map<String, String> getHeaders() {
        return headers;
    }

    @NonNull
    public StreamSource withHeaders(@NonNull java.util.Map<String, String> newHeaders) {
        return new StreamSource(providerName, url, height, hls, subtitles, audio, latencyMs, newHeaders);
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

    /** "sub", "dub", or empty when the provider doesn't say. */
    @NonNull
    public String getAudio() {
        return audio;
    }

    public boolean isDub() {
        return "dub".equals(audio);
    }

    @NonNull
    public String getQualityLabel() {
        return height > 0 ? height + "p" : "Auto";
    }
}
