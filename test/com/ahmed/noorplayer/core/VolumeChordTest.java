package com.ahmed.noorplayer.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VolumeChordTest {

    @Test
    public void singlePressesStillChangeVolume() {
        VolumeChord c = new VolumeChord();
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(true, false, 0, 7));
        c.onKeyUp(true);
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(false, false, 1000, 8));
        c.onKeyUp(false);
        // holding one key keeps changing the volume
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(true, false, 2000, 7));
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(true, true, 2400, 8));
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(true, true, 2450, 9));
        c.onKeyUp(true);
    }

    @Test
    public void bothKeysTogetherToggleAndRestoreVolume() {
        VolumeChord c = new VolumeChord();
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(true, false, 0, 7));   // volume goes 7 -> 8
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(false, false, 80, 8));
        assertEquals(7, c.restoreVolume());
        // repeats while both are held do nothing, and no second toggle
        assertEquals(VolumeChord.IGNORE, c.onKeyDown(false, true, 500, 7));
        c.onKeyUp(true);
        assertEquals(VolumeChord.IGNORE, c.onKeyDown(false, true, 600, 7));
        c.onKeyUp(false);
        // next chord works again
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(false, false, 2000, 7));
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(true, false, 2050, 6));
        assertEquals(7, c.restoreVolume());
    }

    @Test
    public void lateSecondKeyTogglesWithoutUndoingALongHold() {
        VolumeChord c = new VolumeChord();
        c.onKeyDown(true, false, 0, 3);
        c.onKeyDown(true, true, 500, 4);
        c.onKeyDown(true, true, 900, 5);
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(false, false, 1500, 6));
        assertEquals(-1, c.restoreVolume());
    }

    @Test
    public void backgroundStepsDetectChord() {
        VolumeChord c = new VolumeChord();
        assertEquals(VolumeChord.ADJUST, c.onStep(+1, 0, 5));
        assertEquals(VolumeChord.TOGGLE, c.onStep(-1, 60, 6));
        assertEquals(5, c.restoreVolume());
        // key repeats right after the chord are swallowed
        assertEquals(VolumeChord.IGNORE, c.onStep(-1, 200, 5));
        assertEquals(VolumeChord.IGNORE, c.onStep(-1, 300, 5));
        // after the keys are released (quiet gap), single steps work again
        assertEquals(VolumeChord.ADJUST, c.onStep(-1, 1000, 5));
        c.onStep(-1, 3000, 4);
        // a slow up-then-down is two normal steps, not a chord
        assertEquals(VolumeChord.ADJUST, c.onStep(+1, 5000, 3));
        assertEquals(VolumeChord.ADJUST, c.onStep(-1, 5800, 4));
        assertEquals(VolumeChord.IGNORE, c.onStep(0, 6000, 4));
    }

    @Test
    public void backgroundHoldingOneKeyIsNotAChord() {
        VolumeChord c = new VolumeChord();
        for (int i = 0; i < 10; i++) {
            assertEquals(VolumeChord.ADJUST, c.onStep(+1, i * 50L, i));
        }
        assertEquals(VolumeChord.TOGGLE, c.onStep(-1, 480, 10));
        // the restore point is the volume before the most recent run of steps started
        assertEquals(0, c.restoreVolume());
        // a long hold (2 s) followed by the other key toggles but keeps the new volume
        VolumeChord d = new VolumeChord();
        for (int i = 0; i <= 40; i++) d.onStep(+1, 10_000 + i * 50L, i);
        assertEquals(VolumeChord.TOGGLE, d.onStep(-1, 12_030, 15));
        assertEquals(-1, d.restoreVolume());
    }

    @Test
    public void onScreenPauseThenPlayWithTwoChords() {
        VolumeChord c = new VolumeChord();
        // chord 1: pause
        c.onKeyDown(true, false, 0, 7);
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(false, false, 60, 8));
        c.onKeyUp(true);
        c.onKeyUp(false);
        // chord 2 a couple of seconds later: play
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(false, false, 3000, 7));
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(true, false, 3040, 6));
        assertEquals(7, c.restoreVolume());
    }

    @Test
    public void lostKeyUpsDoNotBlockTheNextChord() {
        // The reported bug: the key-up events of the first chord never reach the app.
        VolumeChord c = new VolumeChord();
        c.onKeyDown(true, false, 0, 7);
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(false, false, 50, 8));   // pause
        // no onKeyUp calls at all
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(true, false, 4000, 7));
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(false, false, 4060, 8));   // play
        assertEquals(VolumeChord.ADJUST, c.onKeyDown(false, false, 9000, 7));
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(true, false, 9030, 6));   // pause again
    }

    @Test
    public void holdingBothKeysAfterAChordDoesNotChangeVolume() {
        VolumeChord c = new VolumeChord();
        c.onKeyDown(true, false, 0, 7);
        assertEquals(VolumeChord.TOGGLE, c.onKeyDown(false, false, 50, 8));
        // only the last pressed key repeats; the first one goes "quiet" but is still held
        for (long t = 550; t < 3000; t += 50) {
            assertEquals(VolumeChord.IGNORE, c.onKeyDown(false, true, t, 7));
        }
    }

    @Test
    public void backgroundPauseThenPlay() {
        VolumeChord c = new VolumeChord();
        c.onStep(+1, 0, 5);
        assertEquals(VolumeChord.TOGGLE, c.onStep(-1, 40, 6));     // pause
        assertEquals(VolumeChord.IGNORE, c.onStep(-1, 120, 5));    // leftover from the same press
        assertEquals(VolumeChord.ADJUST, c.onStep(-1, 5000, 5));   // next press, seconds later
        assertEquals(VolumeChord.TOGGLE, c.onStep(+1, 5050, 4));   // play
        assertEquals(5, c.restoreVolume());
    }
}
