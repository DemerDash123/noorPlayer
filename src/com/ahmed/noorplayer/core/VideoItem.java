package com.ahmed.noorplayer.core;

/** One video found on the phone (from the media library). Plain data, no Android types. */
public final class VideoItem {
    public long id = -1;
    public String uri;
    /** File name without the extension, used as the visible title. */
    public String title;
    /** Original file name including extension. */
    public String displayName;
    public long durationMs;
    public long sizeBytes;
    public long dateAddedSec;
    public String bucketId;
    public String bucketName;
    public int width;
    public int height;
    public String path;
    public String mime;

    public String qualityLabel() {
        return qualityOf(width, height);
    }

    /** Short quality label such as "1080p" or "4K"; empty when the size is unknown. */
    public static String qualityOf(int w, int h) {
        if (w <= 0 || h <= 0) return "";
        int mx = Math.max(w, h), mn = Math.min(w, h);
        if (mx >= 3840 || mn >= 2160) return "4K";
        if (mx >= 2560 || mn >= 1440) return "1440p";
        if (mx >= 1920 || mn >= 1080) return "1080p";
        if (mx >= 1280 || mn >= 720) return "720p";
        if (mx >= 854 || mn >= 480) return "480p";
        return mn + "p";
    }

    /** "Movie.2023.mkv" -> "Movie.2023". Keeps names whose "extension" is long or that start with a dot. */
    public static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name;
        int extLen = name.length() - dot - 1;
        if (extLen < 1 || extLen > 5) return name;
        return name.substring(0, dot);
    }
}
