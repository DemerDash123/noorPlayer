package com.ahmed.noorplayer.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LogicTest {
    private static final float EPS = 0.01f;

    @Test
    public void fitModes() {
        // 16:9 video in a portrait 1080x2400 view
        float[] fit = VideoFit.displaySize(VideoFit.FIT, 1080, 2400, 1920, 1080);
        assertEquals(1080, fit[0], EPS);
        assertEquals(607.5, fit[1], EPS);
        float[] fill = VideoFit.displaySize(VideoFit.FILL, 1080, 2400, 1920, 1080);
        assertEquals(2400, fill[1], EPS);
        assertEquals(4266.67, fill[0], 0.5);
        float[] stretch = VideoFit.displaySize(VideoFit.STRETCH, 1080, 2400, 1920, 1080);
        assertEquals(1080, stretch[0], EPS);
        assertEquals(2400, stretch[1], EPS);
        // 4:3 forced in a landscape 1600x720 view
        float[] r43 = VideoFit.displaySize(VideoFit.RATIO_4_3, 1600, 720, 1920, 1080);
        assertEquals(960, r43[0], EPS);
        assertEquals(720, r43[1], EPS);
        float[] orig = VideoFit.displaySize(VideoFit.ORIGINAL, 1600, 720, 640, 360);
        assertEquals(640, orig[0], EPS);
        // unknown video size falls back to the whole view
        float[] unknown = VideoFit.displaySize(VideoFit.FIT, 1600, 720, 0, 0);
        assertEquals(1600, unknown[0], EPS);
    }

    @Test
    public void transformCentersAndClampsPan() {
        float[] t = VideoFit.transform(VideoFit.FIT, 1600, 720, 1920, 1080, 1f, 500, 500);
        // fit: 1280x720 centred, no pan possible
        assertEquals(1280f / 1600f, t[0], EPS);
        assertEquals(1f, t[1], EPS);
        assertEquals(160f, t[2], EPS);
        assertEquals(0f, t[3], EPS);
        // zoom 2x: content 2560x1440, pan limited to +-480 x +-360
        float[] z = VideoFit.transform(VideoFit.FIT, 1600, 720, 1920, 1080, 2f, 10_000, -10_000);
        assertEquals(2560f / 1600f, z[0], EPS);
        assertEquals((1600 - 2560) / 2f + 480, z[2], EPS);
        assertEquals((720 - 1440) / 2f - 360, z[3], EPS);
        assertEquals(VideoFit.MAX_ZOOM, VideoFit.clampZoom(99f), EPS);
        assertEquals(VideoFit.MIN_ZOOM, VideoFit.clampZoom(Float.NaN), EPS);
        float[] bad = VideoFit.transform(VideoFit.FIT, 0, 0, 1920, 1080, 1f, 0, 0);
        assertEquals(1f, bad[0], EPS);
    }

    @Test
    public void zoomKeepsFocusPoint() {
        // focus 100px right of centre, zoom 1 -> 2, no previous pan: point should stay under the finger
        float pan = VideoFit.panForZoom(0f, 1f, 2f, 100f);
        assertEquals(-100f, pan, EPS);
        assertEquals(0f, VideoFit.panForZoom(0f, 1f, 1f, 100f), EPS);
    }

    @Test
    public void resume() {
        assertEquals(0, ResumePolicy.resumePosition(3000, 600_000, false));
        assertEquals(120_000, ResumePolicy.resumePosition(120_000, 600_000, false));
        assertEquals(0, ResumePolicy.resumePosition(597_000, 600_000, false));
        assertEquals(0, ResumePolicy.resumePosition(120_000, 600_000, true));
        assertEquals(0, ResumePolicy.resumePosition(8_000, 10_000, false));
        assertEquals(120_000, ResumePolicy.resumePosition(120_000, 0, false));
        assertEquals(0.5f, ResumePolicy.watchedFraction(300, 600, false), EPS);
        assertEquals(1f, ResumePolicy.watchedFraction(0, 600, true), EPS);
        assertEquals(0f, ResumePolicy.watchedFraction(300, 0, false), EPS);
        assertEquals(1f, ResumePolicy.watchedFraction(900, 600, false), EPS);
    }

    @Test
    public void historyRoundTrip() {
        List<HistoryEntry> list = new ArrayList<>();
        HistoryEntry a = entry("content://media/external/video/media/12", "فيلم\tمع\nسطر %2", 1000, 5000, 100, false, 12);
        HistoryEntry b = entry("https://x.com/a.mp4?x=1&y=%20", "Clip", 0, 0, 200, true, -1);
        list.add(a);
        list.add(b);
        String enc = HistoryCodec.encode(list);
        assertEquals(3, enc.split("\n").length); // header + 2 lines: tabs/newlines were escaped
        List<HistoryEntry> dec = HistoryCodec.decode(enc);
        assertEquals(2, dec.size());
        assertEquals(b.uri, dec.get(0).uri); // newest first
        HistoryEntry da = HistoryCodec.find(dec, a.uri);
        assertNotNull(da);
        assertEquals(a.title, da.title);
        assertEquals(1000, da.positionMs);
        assertEquals(5000, da.durationMs);
        assertEquals(12, da.mediaId);
        assertTrue(HistoryCodec.find(dec, b.uri).finished);
        assertNull(HistoryCodec.find(dec, "nope"));
    }

    @Test
    public void historyIsRobust() {
        assertEquals(0, HistoryCodec.decode(null).size());
        assertEquals(0, HistoryCodec.decode("").size());
        List<HistoryEntry> dec = HistoryCodec.decode("H1\nbroken line\nu\tt\tx\t1\t2\t0\t-1\nu2\tt\t1\t2\t3\t0\t-1\n\n");
        assertEquals(1, dec.size());
        assertEquals("u2", dec.get(0).uri);
        // older lines without the media id column still load
        assertEquals(1, HistoryCodec.decode("H1\nu\tt\t1\t2\t3\t1").size());
    }

    @Test
    public void historyUpsertDedupesAndTrims() {
        List<HistoryEntry> list = new ArrayList<>();
        for (int i = 0; i < HistoryCodec.MAX_ENTRIES + 20; i++) {
            HistoryCodec.upsert(list, entry("u" + i, "t", 0, 0, i, false, -1));
        }
        assertEquals(HistoryCodec.MAX_ENTRIES, list.size());
        HistoryCodec.upsert(list, entry("u5", "again", 10, 20, 100_000, false, -1));
        assertEquals("u5", list.get(0).uri);
        assertEquals(HistoryCodec.MAX_ENTRIES, list.size());
        int count = 0;
        for (HistoryEntry e : list) if (e.uri.equals("u5")) count++;
        assertEquals(1, count);
        HistoryCodec.remove(list, "u5");
        assertNull(HistoryCodec.find(list, "u5"));
        HistoryCodec.remove(list, null); // no crash
    }

    @Test
    public void playlistNavigation() {
        List<PlayItem> items = new ArrayList<>();
        items.add(new PlayItem("a", "A", 1, null, null));
        items.add(null);
        items.add(new PlayItem("b", "B", 2, null, null));
        items.add(new PlayItem("c", "C", 3, null, null));
        Playlist p = new Playlist(items, 1);
        assertEquals(3, p.size());
        assertEquals("b", p.current().uri);
        assertTrue(p.hasPrevious());
        assertTrue(p.moveNext(Playlist.REPEAT_OFF));
        assertEquals("c", p.current().uri);
        assertFalse(p.hasNext(Playlist.REPEAT_OFF));
        assertFalse(p.moveNext(Playlist.REPEAT_OFF));
        assertTrue(p.hasNext(Playlist.REPEAT_ALL));
        assertEquals("a", p.peekNext(Playlist.REPEAT_ALL).uri);
        assertTrue(p.moveNext(Playlist.REPEAT_ALL));
        assertEquals("a", p.current().uri);
        assertFalse(p.movePrevious());
        Playlist one = Playlist.single(new PlayItem("x", "X", -1, null, null));
        assertFalse(one.hasNext(Playlist.REPEAT_ALL));
        Playlist empty = new Playlist(null, 5);
        assertNull(empty.current());
        assertEquals(0, empty.size());
        Playlist clamped = new Playlist(items, 99);
        assertEquals("c", clamped.current().uri);
    }

    @Test
    public void urls() {
        assertEquals("https://example.com/v.mp4", UrlCheck.normalize("  https://example.com/v.mp4 "));
        assertEquals("https://example.com/v.mp4", UrlCheck.normalize("example.com/v.mp4"));
        assertEquals("http://192.168.1.5:8080/movie.mkv", UrlCheck.normalize("http://192.168.1.5:8080/movie.mkv"));
        assertEquals("http://localhost/a.m3u8", UrlCheck.normalize("http://localhost/a.m3u8"));
        assertEquals("rtsp://cam.local/live", UrlCheck.normalize("rtsp://cam.local/live"));
        assertEquals("https://x.com/My%20Clip.mp4", UrlCheck.normalize("https://x.com/My Clip.mp4"));
        assertEquals("https://example.com/a", UrlCheck.normalize("<https://example.com/a>"));
        assertEquals("content://media/external/video/media/5", UrlCheck.normalize("content://media/external/video/media/5"));
        assertNull(UrlCheck.normalize("hello"));
        assertNull(UrlCheck.normalize(""));
        assertNull(UrlCheck.normalize(null));
        assertNull(UrlCheck.normalize("ftp://example.com/file"));
        assertNull(UrlCheck.normalize("https://"));
        assertNull(UrlCheck.normalize("javascript://alert(1)"));
        assertNull(UrlCheck.normalize("two\nlines"));
        assertTrue(UrlCheck.looksLikeUrl("https://example.com/video.mp4"));
        assertFalse(UrlCheck.looksLikeUrl("check https://example.com"));
        assertFalse(UrlCheck.looksLikeUrl("example.com/video.mp4"));
    }

    @Test
    public void urlTitles() {
        assertEquals("My Clip.mp4", UrlCheck.titleFromUrl("https://x.com/videos/My%20Clip.mp4?token=1#t=5"));
        assertEquals("x.com", UrlCheck.titleFromUrl("https://x.com/"));
        assertEquals("x.com", UrlCheck.titleFromUrl("https://x.com"));
        assertEquals("a+b.mp4", UrlCheck.titleFromUrl("https://x.com/a+b.mp4"));
        assertEquals("", UrlCheck.titleFromUrl(null));
        assertEquals("bad%zz.mp4", UrlCheck.titleFromUrl("https://x.com/bad%zz.mp4"));
    }

    @Test
    public void gestures() {
        assertEquals(1f, GestureMath.brightness(0.5f, -2000, 1000), EPS);
        assertEquals(0.01f, GestureMath.brightness(0.5f, 2000, 1000), EPS);
        assertEquals(0.5f + 150f / 750f, GestureMath.brightness(0.5f, -150, 1000), EPS);
        assertEquals(15f, GestureMath.volume(5f, -5000, 1000, 15), EPS);
        assertEquals(0f, GestureMath.volume(5f, 5000, 1000, 15), EPS);
        assertEquals(5f, GestureMath.volume(5f, 100, 1000, 0), EPS);
        // half-width swipe on a long video = 90 s
        assertEquals(190_000, GestureMath.seekTarget(100_000, 500, 1000, 3_600_000));
        // short video: range is the video length
        assertEquals(30_000, GestureMath.seekTarget(0, 1000, 1000, 30_000));
        assertEquals(0, GestureMath.seekTarget(10_000, -1000, 1000, 3_600_000));
        assertEquals(500, GestureMath.seekTarget(500, 100, 0, 1000));
    }

    @Test
    public void sortingAndFolders() {
        List<VideoItem> items = new ArrayList<>();
        items.add(video(1, "Episode 10", 300, 5_000, 1_000, "b1", "Series"));
        items.add(video(2, "Episode 2", 100, 9_000, 2_000, "b1", "Series"));
        items.add(video(3, "Birthday", 200, 1_000, 3_000, "b2", "Camera"));
        items.add(video(4, "Loose", 50, 100, 10, null, null));

        // dates: #1=1000, #2=2000, #3=3000, #4=10; sizes: #2 biggest; durations: #1 longest
        VideoSorter.sort(items, VideoSorter.SORT_DATE);
        assertEquals(3, items.get(0).id);
        assertEquals(4, items.get(3).id);
        VideoSorter.sort(items, VideoSorter.SORT_NAME);
        assertEquals("Birthday", items.get(0).title);
        assertEquals("Episode 2", items.get(1).title);
        assertEquals("Episode 10", items.get(2).title);
        VideoSorter.sort(items, VideoSorter.SORT_SIZE);
        assertEquals(2, items.get(0).id);
        VideoSorter.sort(items, VideoSorter.SORT_DURATION);
        assertEquals(1, items.get(0).id);

        List<FolderInfo> folders = VideoSorter.groupFolders(items, "Other");
        assertEquals(3, folders.size());
        FolderInfo series = null, other = null, camera = null;
        for (FolderInfo f : folders) {
            if ("Series".equals(f.name)) series = f;
            if ("Other".equals(f.name)) other = f;
            if ("Camera".equals(f.name)) camera = f;
        }
        assertNotNull(series);
        assertNotNull(other);
        assertNotNull(camera);
        assertEquals(2, series.count);
        assertEquals(14_000, series.totalSize);
        assertEquals(400, series.totalDurationMs);
        assertEquals(2, series.cover.id); // newest in the folder
        assertEquals(2, VideoSorter.inFolder(items, series.id).size());
        assertEquals(1, VideoSorter.inFolder(items, other.id).size());
        assertEquals(0, VideoSorter.inFolder(items, null).size());

        VideoSorter.sortFolders(folders, VideoSorter.SORT_NAME);
        assertEquals("Camera", folders.get(0).name);
        assertEquals("Series", folders.get(2).name);
        VideoSorter.sortFolders(folders, VideoSorter.SORT_DATE);
        assertSame(camera, folders.get(0));
        assertSame(other, folders.get(2));
        VideoSorter.sortFolders(folders, VideoSorter.SORT_SIZE);
        assertSame(series, folders.get(0));
    }

    @Test
    public void searchMatching() {
        assertTrue(VideoSorter.matches("Family Trip 2024", ""));
        assertTrue(VideoSorter.matches("Family Trip 2024", "trip"));
        assertTrue(VideoSorter.matches("Family Trip 2024", "2024 family"));
        assertFalse(VideoSorter.matches("Family Trip 2024", "beach"));
        assertTrue(VideoSorter.matches("رحلة العائلة", "العائلة"));
        assertFalse(VideoSorter.matches(null, "x"));
        assertTrue(VideoSorter.matches(null, null));
    }

    private static HistoryEntry entry(String uri, String title, long pos, long dur, long last, boolean fin, long id) {
        HistoryEntry e = new HistoryEntry();
        e.uri = uri;
        e.title = title;
        e.positionMs = pos;
        e.durationMs = dur;
        e.lastPlayedMs = last;
        e.finished = fin;
        e.mediaId = id;
        return e;
    }

    private static VideoItem video(long id, String title, long dur, long size, long date, String bucket, String bucketName) {
        VideoItem v = new VideoItem();
        v.id = id;
        v.uri = "content://media/external/video/media/" + id;
        v.title = title;
        v.durationMs = dur;
        v.sizeBytes = size;
        v.dateAddedSec = date;
        v.bucketId = bucket;
        v.bucketName = bucketName;
        return v;
    }
}
