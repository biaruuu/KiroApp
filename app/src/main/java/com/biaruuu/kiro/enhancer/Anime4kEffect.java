package com.biaruuu.kiro.enhancer;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;

/** Video effect that sharpens anime lines on the GPU. Tier is controlled through {@link Anime4kState}. */
@SuppressLint("UnsafeOptInUsageError")
public final class Anime4kEffect implements GlEffect {
    private final Anime4kState state;

    public Anime4kEffect(Anime4kState state) {
        this.state = state;
    }

    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
            throws VideoFrameProcessingException {
        return new Anime4kProgram(state, useHdr);
    }
}
