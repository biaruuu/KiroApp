package com.biaruuu.kiro.data.stream;

import androidx.annotation.NonNull;

/** A subtitle file that goes with a stream (WebVTT or SubRip). */
public final class SubtitleTrack {

    private final String url;
    private final String label;
    private final String language;
    private final String mimeType;

    public SubtitleTrack(@NonNull String url, @NonNull String label, @NonNull String language, @NonNull String mimeType) {
        this.url = url;
        this.label = label;
        this.language = language;
        this.mimeType = mimeType;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @NonNull
    public String getLanguage() {
        return language;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }
}
