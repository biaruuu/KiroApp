package com.biaruuu.kiro.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Keeps raw API answers on disk so the app opens instantly, scrolls without waiting and still
 * shows what it last knew when the network drops. A fresh copy (within its TTL) is used without
 * any request; an older one is only a fallback when the network fails.
 */
public final class ApiDiskCache {

    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static volatile File dir;

    private ApiDiskCache() {
    }

    public static void init(@NonNull Context context) {
        File d = new File(context.getCacheDir(), "api");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        dir = d;
        trim(d);
    }

    @NonNull
    public static String key(@NonNull String payload) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    /** The cached answer if it is younger than [maxAgeMs], else null. */
    @Nullable
    public static String get(@NonNull String key, long maxAgeMs) {
        File d = dir;
        if (d == null) {
            return null;
        }
        File f = new File(d, key);
        if (!f.isFile() || System.currentTimeMillis() - f.lastModified() > maxAgeMs) {
            return null;
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static void put(@NonNull String key, @NonNull String text) {
        File d = dir;
        if (d == null) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(new File(d, key))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Best effort.
        }
    }

    /** Deletes the oldest files once the folder grows past the limit. */
    private static void trim(File d) {
        File[] files = d.listFiles();
        if (files == null) {
            return;
        }
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        if (total <= MAX_BYTES) {
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= MAX_BYTES * 3 / 4) {
                break;
            }
            total -= f.length();
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
