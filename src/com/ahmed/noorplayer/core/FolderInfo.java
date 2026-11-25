package com.ahmed.noorplayer.core;

/** A folder (media "bucket") summarised from its videos. */
public final class FolderInfo {
    public String id;
    public String name;
    public int count;
    public long totalSize;
    public long totalDurationMs;
    public long latestDateSec;
    /** Newest video in the folder, used for the folder thumbnail. */
    public VideoItem cover;
}
