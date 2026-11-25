package com.ahmed.noorplayer.core;

/** Maps finger movement to brightness, volume and seek changes. */
public final class GestureMath {
    private GestureMath() {}

    /** Dragging this fraction of the screen height covers the whole brightness/volume range. */
    public static final float VERTICAL_RANGE = 0.75f;
    /** A full-width horizontal swipe moves this far (or the whole video if it is shorter). */
    public static final long SWIPE_SEEK_RANGE_MS = 180_000L;

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** dyTotal is the finger's total vertical movement in pixels (negative = up = brighter). */
    public static float brightness(float start, float dyTotal, float height) {
        if (height <= 0) return clamp(start, 0.01f, 1f);
        return clamp(start - dyTotal / (height * VERTICAL_RANGE), 0.01f, 1f);
    }

    /** Returns the new volume as a fractional stream index between 0 and max. */
    public static float volume(float start, float dyTotal, float height, int max) {
        if (height <= 0 || max <= 0) return start;
        return clamp(start - dyTotal / (height * VERTICAL_RANGE) * max, 0f, max);
    }

    public static long seekTarget(long startMs, float dxTotal, float width, long durationMs) {
        if (width <= 0 || durationMs <= 0) return startMs;
        long range = Math.min(durationMs, SWIPE_SEEK_RANGE_MS);
        long target = startMs + (long) (dxTotal / width * range);
        if (target < 0) return 0;
        return Math.min(target, durationMs);
    }
}
