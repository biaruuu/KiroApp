package com.biaruuu.kiro.data.stream;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Streams from addresses the user typed in (their own server, a licensed provider, ...). */
public final class CustomProvider implements StreamProvider {

    private final Context context;

    public CustomProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public String getName() {
        return "My sources";
    }

    @NonNull
    @Override
    public List<StreamSource> resolve(@NonNull StreamRequest request) {
        List<StreamSource> out = new ArrayList<>();
        int n = 0;
        for (String template : StreamSettings.getTemplates(context)) {
            n++;
            String url = template
                    .replace("{id}", String.valueOf(request.getAnimeId()))
                    .replace("{episode}", String.valueOf(request.getEpisode()));
            boolean hls = url.toLowerCase(Locale.ROOT).contains(".m3u8");
            // Quality is unknown for a typed-in address; assume HD until measured otherwise.
            out.add(new StreamSource("My source " + n, url, 1080, hls, Collections.emptyList()));
        }
        return out;
    }
}
