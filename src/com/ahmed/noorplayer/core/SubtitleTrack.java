package com.ahmed.noorplayer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** A parsed subtitle file with fast lookup of the text to show at a given time. */
public final class SubtitleTrack {
    private final long[] starts;
    private final long[] ends;
    private final String[] texts;
    private final long maxDuration;

    public SubtitleTrack(List<SubtitleCue> cues) {
        List<SubtitleCue> sorted = new ArrayList<>(cues);
        Collections.sort(sorted, new Comparator<SubtitleCue>() {
            @Override
            public int compare(SubtitleCue a, SubtitleCue b) {
                int r = Long.compare(a.startMs, b.startMs);
                return r != 0 ? r : Long.compare(a.endMs, b.endMs);
            }
        });
        int n = sorted.size();
        starts = new long[n];
        ends = new long[n];
        texts = new String[n];
        long max = 0;
        for (int i = 0; i < n; i++) {
            SubtitleCue c = sorted.get(i);
            starts[i] = c.startMs;
            ends[i] = c.endMs;
            texts[i] = c.text;
            max = Math.max(max, c.endMs - c.startMs);
        }
        maxDuration = max;
    }

    public int size() {
        return starts.length;
    }

    /** Text of every cue active at time t (joined by new lines, in start order), or null when none. */
    public String textAt(long t) {
        int n = starts.length;
        if (n == 0) return null;
        int lo = 0, hi = n - 1, idx = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (starts[mid] <= t) {
                idx = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (idx < 0) return null;
        List<String> active = null;
        for (int i = idx; i >= 0 && starts[i] >= t - maxDuration; i--) {
            if (ends[i] > t) {
                if (active == null) active = new ArrayList<>(2);
                active.add(texts[i]);
            }
        }
        if (active == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = active.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(active.get(i));
        }
        return sb.toString();
    }
}
