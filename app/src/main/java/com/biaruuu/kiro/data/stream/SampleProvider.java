package com.biaruuu.kiro.data.stream;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Public demo videos (Blender open movies) at several qualities, plus a generated sample subtitle
 * file. It exists so the player, the quality/speed ranking, failover and subtitle styling can all
 * be tried before a real provider is connected.
 */
public final class SampleProvider implements StreamProvider {

    private final Context context;

    public SampleProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public String getName() {
        return "Sample";
    }

    @NonNull
    @Override
    public List<StreamSource> resolve(@NonNull StreamRequest request) throws Exception {
        List<SubtitleTrack> subs = new ArrayList<>();
        File vtt = writeSampleSubtitles();
        if (vtt != null) {
            subs.add(new SubtitleTrack(vtt.toURI().toString(), "English (sample)", "en", "text/vtt"));
        }
        List<StreamSource> out = new ArrayList<>();
        out.add(new StreamSource("Sample A",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                720, false, subs));
        out.add(new StreamSource("Sample B",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                720, false, subs));
        out.add(new StreamSource("Sample HLS",
                "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                1080, true, subs));
        out.add(new StreamSource("Sample Lite",
                "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                180, false, subs));
        return out;
    }

    private File writeSampleSubtitles() {
        try {
            File file = new File(context.getCacheDir(), "kiro-sample.vtt");
            StringBuilder sb = new StringBuilder("WEBVTT\n\n");
            String[] lines = {
                    "This is a sample subtitle.",
                    "Open the menu to change its font, size and color.",
                    "You can also load your own .srt or .vtt file.",
                    "The background and edge style change here too.",
            };
            for (int i = 0; i < 40; i++) {
                int start = 1 + i * 6;
                sb.append(time(start)).append(" --> ").append(time(start + 4)).append('\n')
                        .append(lines[i % lines.length]).append("\n\n");
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    private static String time(int seconds) {
        return String.format(Locale.US, "%02d:%02d:%02d.000", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
