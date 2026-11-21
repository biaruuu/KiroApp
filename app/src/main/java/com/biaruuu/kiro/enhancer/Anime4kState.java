package com.biaruuu.kiro.enhancer;

/** Shared between the player (writes the tier) and the GL thread (reads it every frame). */
public final class Anime4kState {
    /** 0 off (plain copy), 1 light, 2 balanced, 3 quality. */
    public volatile int tier = 0;
    /** Enlargement times 10 (10 = none). Read when the effect is configured for a video size. */
    public volatile int scale10 = 10;
    /** Set when the shaders could not be built on this GPU. */
    public volatile boolean failed = false;
}
