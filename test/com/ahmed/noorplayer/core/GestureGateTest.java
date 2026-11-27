package com.ahmed.noorplayer.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GestureGateTest {
    private static final float W = 2400, H = 1080, EDGE = 90;

    private static int decide(boolean armed, float dx, float dy, float x, float y) {
        return GestureGate.decide(armed, dx, dy, x, y, W, H, EDGE, true, true);
    }

    @Test
    public void accidentalSidewaysSwipeDoesNotSeek() {
        // the reported problem: a quick drag moved the video
        assertEquals(GestureGate.IGNORE, decide(false, 300, 10, 1200, 500));
        assertEquals(GestureGate.IGNORE, decide(false, -300, 40, 1200, 500));
    }

    @Test
    public void holdingFirstThenSlidingSeeks() {
        assertEquals(GestureGate.SEEK, decide(true, 300, 10, 1200, 500));
        assertEquals(GestureGate.SEEK, decide(true, -300, 10, 1200, 500));
        // once armed, a slightly diagonal slide is still a seek
        assertEquals(GestureGate.SEEK, decide(true, 50, 80, 1200, 500));
    }

    @Test
    public void armedButNothingSeekable() {
        assertEquals(GestureGate.IGNORE, GestureGate.decide(true, 300, 0, 1200, 500, W, H, EDGE, true, false));
    }

    @Test
    public void verticalSwipesStillWorkImmediately() {
        assertEquals(GestureGate.BRIGHTNESS, decide(false, 5, -200, 300, 500));
        assertEquals(GestureGate.VOLUME, decide(false, 5, 200, 2000, 500));
    }

    @Test
    public void edgesAndSettingAreRespected() {
        assertEquals(GestureGate.IGNORE, decide(true, 300, 0, 1200, 40));          // top edge
        assertEquals(GestureGate.IGNORE, decide(false, 0, -300, 300, H - 20));     // bottom edge
        assertEquals(GestureGate.IGNORE, GestureGate.decide(true, 300, 0, 1200, 500, W, H, EDGE, false, true));
        assertEquals(GestureGate.IGNORE, GestureGate.decide(false, 0, 300, 100, 500, 0, 0, EDGE, true, true));
    }
}
