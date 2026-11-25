package com.ahmed.noorplayer.core;

import java.util.Locale;

/** Formats media times. Always uses Western digits so the timeline reads the same in every language. */
public final class TimeFormat {
    private TimeFormat() {}

    /** Formats milliseconds as m:ss, or h:mm:ss when an hour or longer. Negative values show as 0:00. */
    public static String format(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /** Formats a signed offset such as "+0:10" or "-1:05". */
    public static String formatDelta(long deltaMs) {
        return (deltaMs < 0 ? "-" : "+") + format(Math.abs(deltaMs));
    }

    /** Formats an offset in seconds with one decimal, e.g. "+0.5 s". */
    public static String formatOffsetSeconds(long ms) {
        return String.format(Locale.US, "%+.1f s", ms / 1000.0);
    }
}
