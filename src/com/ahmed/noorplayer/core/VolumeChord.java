package com.ahmed.noorplayer.core;

/**
 * Detects "Volume Up and Volume Down pressed together" so it can toggle pause/play.
 * A single press still changes the volume; when the second key joins, the volume goes back to
 * where it was before the first press and playback toggles.
 *
 * Two inputs are supported:
 *  - key events while the player is on screen ({@link #onKeyDown}/{@link #onKeyUp}), and
 *  - volume steps delivered to the media session in the background ({@link #onStep}),
 *    where only "up" or "down" steps with times are known.
 */
public final class VolumeChord {
    public static final int ADJUST = 0;  // do the normal volume step
    public static final int IGNORE = 1;  // swallow the event
    public static final int TOGGLE = 2;  // restore volume, then pause/play

    /** Both keys must go down within this time for the volume to be restored. */
    public static final long RESTORE_WINDOW_MS = 600;
    /** Background: opposite steps closer than this count as a chord. */
    public static final long STEP_WINDOW_MS = 400;
    /** Background: after a chord, steps are ignored until the keys are quiet this long. */
    public static final long QUIET_MS = 350;

    /**
     * A key counts as held only if we saw it recently. Key-up events can be lost (for example to the
     * system volume panel), so a "held" key with no news for this long is treated as released.
     * Longer than Android's 500 ms wait before key repeats start.
     */
    public static final long HELD_STALE_MS = 700;

    private boolean upHeld, downHeld, fired;
    private long upSeenAt, downSeenAt;
    private long firstDownAt;
    private int volumeBefore = -1;

    private int lastDir;
    private long lastStepAt = Long.MIN_VALUE / 2;
    private int stepVolumeBefore = -1;
    private long runStartAt;
    private boolean suppressing;

    /** Volume to put back after a TOGGLE, or -1 to leave the volume alone. */
    private int restoreTo = -1;

    public int restoreVolume() {
        return restoreTo;
    }

    // ---------------------------------------------------------------- on-screen key events

    public int onKeyDown(boolean up, boolean repeat, long nowMs, int currentVolume) {
        boolean otherHeld = held(!up, nowMs);
        // Nothing is really held any more: forget a chord whose key-up events never arrived.
        if (!otherHeld && !held(up, nowMs)) fired = false;
        if (up) {
            upHeld = true;
            upSeenAt = nowMs;
        } else {
            downHeld = true;
            downSeenAt = nowMs;
        }
        if (repeat) return fired || otherHeld ? IGNORE : ADJUST;
        if (otherHeld) {
            if (fired) return IGNORE;
            fired = true;
            restoreTo = nowMs - firstDownAt <= RESTORE_WINDOW_MS ? volumeBefore : -1;
            return TOGGLE;
        }
        fired = false;
        firstDownAt = nowMs;
        volumeBefore = currentVolume;
        return ADJUST;
    }

    public void onKeyUp(boolean up) {
        if (up) upHeld = false;
        else downHeld = false;
        if (!upHeld && !downHeld) fired = false;
    }

    private boolean held(boolean up, long nowMs) {
        return up ? upHeld && nowMs - upSeenAt < HELD_STALE_MS : downHeld && nowMs - downSeenAt < HELD_STALE_MS;
    }

    // ---------------------------------------------------------------- background volume steps

    /** direction: +1 louder, -1 quieter. */
    public int onStep(int direction, long nowMs, int currentVolume) {
        if (direction == 0) return IGNORE;
        long gap = nowMs - lastStepAt;
        if (suppressing) {
            lastStepAt = nowMs;
            if (gap < QUIET_MS) return IGNORE;
            suppressing = false;
            lastDir = 0;
            gap = Long.MAX_VALUE;
        }
        if (lastDir != 0 && direction == -lastDir && gap <= STEP_WINDOW_MS) {
            restoreTo = nowMs - runStartAt <= RESTORE_WINDOW_MS ? stepVolumeBefore : -1;
            suppressing = true;
            lastDir = 0;
            lastStepAt = nowMs;
            return TOGGLE;
        }
        if (direction != lastDir || gap > STEP_WINDOW_MS) {
            stepVolumeBefore = currentVolume;
            runStartAt = nowMs;
        }
        lastDir = direction;
        lastStepAt = nowMs;
        return ADJUST;
    }
}
