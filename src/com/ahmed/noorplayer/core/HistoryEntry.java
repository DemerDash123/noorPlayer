package com.ahmed.noorplayer.core;

/** A watched video: where it stopped and when it was last played. */
public final class HistoryEntry {
    public String uri;
    public String title;
    public long positionMs;
    public long durationMs;
    public long lastPlayedMs;
    public boolean finished;
    /** Media library id when known (for thumbnails), otherwise -1. */
    public long mediaId = -1;

    public HistoryEntry copy() {
        HistoryEntry e = new HistoryEntry();
        e.uri = uri;
        e.title = title;
        e.positionMs = positionMs;
        e.durationMs = durationMs;
        e.lastPlayedMs = lastPlayedMs;
        e.finished = finished;
        e.mediaId = mediaId;
        return e;
    }
}
