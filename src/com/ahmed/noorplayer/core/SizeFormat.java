package com.ahmed.noorplayer.core;

import java.util.Locale;

/** Formats byte counts with decimal (1000-based) units, like Android's own file sizes. */
public final class SizeFormat {
    private SizeFormat() {}

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    public static String format(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1000) return bytes + " B";
        double v = bytes / 1000.0;
        int u = 0;
        while (v >= 999.5 && u < UNITS.length - 1) {
            v /= 1000.0;
            u++;
        }
        if (v < 9.95) return String.format(Locale.US, "%.1f %s", v, UNITS[u]);
        return String.format(Locale.US, "%.0f %s", v, UNITS[u]);
    }
}
