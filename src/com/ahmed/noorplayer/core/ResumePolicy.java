package com.ahmed.noorplayer.core;

/** Decides where a video should continue from and how much of it has been watched. */
public final class ResumePolicy {
    private ResumePolicy() {}

    /** Ignore saved positions closer than this to the start or the end. */
    public static final long EDGE_MS = 5000;

    public static long resumePosition(long savedMs, long durationMs, boolean finished) {
        if (finished || savedMs < EDGE_MS) return 0;
        if (durationMs > 0) {
            if (savedMs > durationMs - EDGE_MS) return 0;
            if (savedMs >= durationMs * 0.98) return 0;
        }
        return savedMs;
    }

    /** Watched fraction 0..1 for progress bars. */
    public static float watchedFraction(long posMs, long durationMs, boolean finished) {
        if (finished) return 1f;
        if (durationMs <= 0 || posMs <= 0) return 0f;
        float f = (float) posMs / (float) durationMs;
        return f < 0 ? 0f : (f > 1f ? 1f : f);
    }
}
