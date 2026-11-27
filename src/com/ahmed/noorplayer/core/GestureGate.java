package com.ahmed.noorplayer.core;

/**
 * Decides what a finger drag on the video does, once it has moved past the touch slop.
 *  - Sideways seeking only happens if the finger was first held still for a moment ("armed"),
 *    so an accidental swipe never moves the video.
 *  - Up/down on the left half is brightness, on the right half is volume.
 *  - Drags starting at the very top or bottom edge are left to the system (status bar, navigation).
 */
public final class GestureGate {
    private GestureGate() {}

    public static final int IGNORE = 0;
    public static final int SEEK = 1;
    public static final int BRIGHTNESS = 2;
    public static final int VOLUME = 3;

    /**
     * @param armed       finger was held still long enough before moving
     * @param dx          horizontal movement since touch-down (pixels)
     * @param dy          vertical movement since touch-down (pixels)
     * @param startX      touch-down x
     * @param startY      touch-down y
     * @param width       gesture area width
     * @param height      gesture area height
     * @param edge        size of the ignored strip at the top and bottom edges
     * @param gesturesOn  user setting for swipe gestures
     * @param canSeek     a seekable video is loaded
     */
    public static int decide(boolean armed, float dx, float dy, float startX, float startY, float width, float height,
                             float edge, boolean gesturesOn, boolean canSeek) {
        if (!gesturesOn || width <= 0 || height <= 0) return IGNORE;
        if (startY < edge || startY > height - edge) return IGNORE;
        if (armed) return canSeek ? SEEK : IGNORE;
        if (Math.abs(dx) > Math.abs(dy)) return IGNORE; // quick sideways swipe without holding first
        return startX < width / 2f ? BRIGHTNESS : VOLUME;
    }
}
