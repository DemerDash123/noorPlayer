package com.ahmed.noorplayer.core;

/**
 * Geometry for showing a video inside a view: fit / fill / stretch / fixed ratios / 100%,
 * plus pinch-zoom and panning, expressed as a scale + translation for the video texture.
 */
public final class VideoFit {
    private VideoFit() {}

    public static final int FIT = 0;
    public static final int FILL = 1;
    public static final int STRETCH = 2;
    public static final int RATIO_16_9 = 3;
    public static final int RATIO_4_3 = 4;
    public static final int ORIGINAL = 5;
    public static final int MODE_COUNT = 6;

    public static final float MIN_ZOOM = 1f;
    public static final float MAX_ZOOM = 5f;

    /** Size {width, height} the video should occupy before zooming. */
    public static float[] displaySize(int mode, float viewW, float viewH, float videoW, float videoH) {
        if (viewW <= 0 || viewH <= 0) return new float[]{Math.max(viewW, 0), Math.max(viewH, 0)};
        if (mode == STRETCH) return new float[]{viewW, viewH};
        if (videoW <= 0 || videoH <= 0) return new float[]{viewW, viewH};
        if (mode == ORIGINAL) return new float[]{videoW, videoH};
        float aspect;
        if (mode == RATIO_16_9) aspect = 16f / 9f;
        else if (mode == RATIO_4_3) aspect = 4f / 3f;
        else aspect = videoW / videoH;
        float viewAspect = viewW / viewH;
        boolean viewWider = viewAspect > aspect;
        if (mode == FILL) {
            return viewWider ? new float[]{viewW, viewW / aspect} : new float[]{viewH * aspect, viewH};
        }
        return viewWider ? new float[]{viewH * aspect, viewH} : new float[]{viewW, viewW / aspect};
    }

    /** Largest allowed pan (in pixels, either direction) for content of the given size. */
    public static float maxPan(float contentSize, float viewSize) {
        return Math.max(0f, (contentSize - viewSize) / 2f);
    }

    public static float clampPan(float pan, float contentSize, float viewSize) {
        float m = maxPan(contentSize, viewSize);
        if (pan > m) return m;
        if (pan < -m) return -m;
        return pan;
    }

    public static float clampZoom(float z) {
        if (Float.isNaN(z)) return MIN_ZOOM;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    /**
     * Transform for a texture that by default is stretched over the whole view.
     * Returns {scaleX, scaleY, translateX, translateY} for Matrix.setScale(sx, sy) then postTranslate(tx, ty).
     * The pan values are clamped so the picture never drifts away from the screen.
     */
    public static float[] transform(int mode, float viewW, float viewH, float videoW, float videoH,
                                    float zoom, float panX, float panY) {
        if (viewW <= 0 || viewH <= 0) return new float[]{1f, 1f, 0f, 0f};
        float[] d = displaySize(mode, viewW, viewH, videoW, videoH);
        float z = clampZoom(zoom);
        float cw = d[0] * z, ch = d[1] * z;
        float px = clampPan(panX, cw, viewW);
        float py = clampPan(panY, ch, viewH);
        float sx = cw / viewW, sy = ch / viewH;
        float tx = (viewW - cw) / 2f + px;
        float ty = (viewH - ch) / 2f + py;
        return new float[]{sx, sy, tx, ty};
    }

    /**
     * New pan value so that the content point under the pinch focus stays put when zoom changes.
     * focusOffset is the focus position relative to the view centre.
     */
    public static float panForZoom(float pan, float oldZoom, float newZoom, float focusOffset) {
        if (oldZoom <= 0) return pan;
        return focusOffset - (focusOffset - pan) * (newZoom / oldZoom);
    }
}
