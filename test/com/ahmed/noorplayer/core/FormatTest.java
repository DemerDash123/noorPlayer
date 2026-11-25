package com.ahmed.noorplayer.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import org.junit.Test;

public class FormatTest {
    @Test
    public void timeFormat() {
        assertEquals("0:00", TimeFormat.format(0));
        assertEquals("0:00", TimeFormat.format(-5000));
        assertEquals("0:59", TimeFormat.format(59_999));
        assertEquals("1:00", TimeFormat.format(60_000));
        assertEquals("12:34", TimeFormat.format((12 * 60 + 34) * 1000L));
        assertEquals("1:00:00", TimeFormat.format(3_600_000));
        assertEquals("2:03:04", TimeFormat.format(((2 * 60 + 3) * 60 + 4) * 1000L));
        assertEquals("+0:10", TimeFormat.formatDelta(10_000));
        assertEquals("-1:05", TimeFormat.formatDelta(-65_000));
        assertEquals("+0.5 s", TimeFormat.formatOffsetSeconds(500));
        assertEquals("-1.2 s", TimeFormat.formatOffsetSeconds(-1200));
        assertEquals("+0.0 s", TimeFormat.formatOffsetSeconds(0));
    }

    @Test
    public void timeFormatIgnoresArabicLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("ar", "OM"));
            assertEquals("12:34", TimeFormat.format((12 * 60 + 34) * 1000L));
            assertEquals("1.5 GB", SizeFormat.format(1_500_000_000L));
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    public void sizeFormat() {
        assertEquals("0 B", SizeFormat.format(0));
        assertEquals("0 B", SizeFormat.format(-1));
        assertEquals("999 B", SizeFormat.format(999));
        assertEquals("1.0 KB", SizeFormat.format(1000));
        assertEquals("9.9 KB", SizeFormat.format(9_949));
        assertEquals("10 KB", SizeFormat.format(9_950));
        assertEquals("245 MB", SizeFormat.format(245_000_000));
        assertEquals("1.0 MB", SizeFormat.format(999_600));
        assertEquals("1.5 GB", SizeFormat.format(1_500_000_000L));
        assertEquals("12 GB", SizeFormat.format(12_000_000_000L));
        assertEquals("3.0 TB", SizeFormat.format(3_000_000_000_000L));
    }

    @Test
    public void quality() {
        assertEquals("", VideoItem.qualityOf(0, 0));
        assertEquals("4K", VideoItem.qualityOf(3840, 2160));
        assertEquals("4K", VideoItem.qualityOf(3840, 1600));
        assertEquals("1080p", VideoItem.qualityOf(1920, 1080));
        assertEquals("1080p", VideoItem.qualityOf(1080, 1920));
        assertEquals("1080p", VideoItem.qualityOf(1920, 800));
        assertEquals("720p", VideoItem.qualityOf(1280, 720));
        assertEquals("720p", VideoItem.qualityOf(1280, 534));
        assertEquals("480p", VideoItem.qualityOf(854, 480));
        assertEquals("480p", VideoItem.qualityOf(640, 480));
        assertEquals("360p", VideoItem.qualityOf(640, 360));
        assertEquals("240p", VideoItem.qualityOf(426, 240));
    }

    @Test
    public void stripExtension() {
        assertEquals("Movie.2023", VideoItem.stripExtension("Movie.2023.mkv"));
        assertEquals("clip", VideoItem.stripExtension("clip.mp4"));
        assertEquals(".hidden", VideoItem.stripExtension(".hidden"));
        assertEquals("noext", VideoItem.stripExtension("noext"));
        assertEquals("name.verylongext", VideoItem.stripExtension("name.verylongext"));
        assertEquals("", VideoItem.stripExtension(null));
        assertEquals("فيلم", VideoItem.stripExtension("فيلم.mp4"));
    }

    @Test
    public void naturalOrder() {
        NaturalOrder n = NaturalOrder.INSTANCE;
        assertTrue(n.compare("Episode 2", "Episode 10") < 0);
        assertTrue(n.compare("episode 2", "Episode 10") < 0);
        assertTrue(n.compare("a", "B") < 0);
        assertTrue(n.compare("clip9", "clip09b") < 0);
        assertTrue(n.compare("file", "file1") < 0);
        assertEquals(0, n.compare("same", "same"));
        assertTrue(n.compare("01", "1") != 0); // total order: tie broken deterministically
        assertTrue(n.compare(null, "a") < 0);
        // Arabic-Indic digits compare by value
        assertTrue(n.compare("حلقة ٢", "حلقة ١٠") < 0);
        // transitivity spot check
        String[] s = {"x10", "x2", "x1", "X3", "x02"};
        java.util.Arrays.sort(s, n);
        assertEquals("x1", s[0]);
        assertEquals("x10", s[s.length - 1]);
    }
}
