package com.ahmed.noorplayer.core;

/** Something the player can open: a phone video, a picked file, or a network link. */
public final class PlayItem {
    public final String uri;
    public final String title;
    public final long mediaId;
    public final String path;
    public final String mime;

    public PlayItem(String uri, String title, long mediaId, String path, String mime) {
        this.uri = uri;
        this.title = title;
        this.mediaId = mediaId;
        this.path = path;
        this.mime = mime;
    }

    public static PlayItem of(VideoItem v) {
        return new PlayItem(v.uri, v.title, v.id, v.path, v.mime);
    }

    public boolean isNetwork() {
        if (uri == null) return false;
        String u = uri.toLowerCase(java.util.Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("rtsp://");
    }
}
