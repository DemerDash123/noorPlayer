package com.ahmed.noorplayer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sorting, folder grouping and search filtering for the video library. */
public final class VideoSorter {
    private VideoSorter() {}

    public static final int SORT_DATE = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_SIZE = 2;
    public static final int SORT_DURATION = 3;

    public static Comparator<VideoItem> comparator(final int mode) {
        return new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem a, VideoItem b) {
                int r;
                switch (mode) {
                    case SORT_NAME:
                        r = 0;
                        break;
                    case SORT_SIZE:
                        r = Long.compare(b.sizeBytes, a.sizeBytes);
                        break;
                    case SORT_DURATION:
                        r = Long.compare(b.durationMs, a.durationMs);
                        break;
                    case SORT_DATE:
                    default:
                        r = Long.compare(b.dateAddedSec, a.dateAddedSec);
                        break;
                }
                if (r != 0) return r;
                r = NaturalOrder.INSTANCE.compare(a.title, b.title);
                if (r != 0) return r;
                return Long.compare(a.id, b.id);
            }
        };
    }

    public static void sort(List<VideoItem> list, int mode) {
        Collections.sort(list, comparator(mode));
    }

    /** Groups videos by folder. Videos without a folder id are grouped by folder name. */
    public static List<FolderInfo> groupFolders(List<VideoItem> items, String unknownName) {
        Map<String, FolderInfo> map = new LinkedHashMap<>();
        for (VideoItem v : items) {
            String key = v.bucketId != null && !v.bucketId.isEmpty() ? v.bucketId
                    : ("name:" + (v.bucketName == null ? "" : v.bucketName));
            FolderInfo f = map.get(key);
            if (f == null) {
                f = new FolderInfo();
                f.id = key;
                f.name = v.bucketName == null || v.bucketName.isEmpty() ? unknownName : v.bucketName;
                map.put(key, f);
            }
            f.count++;
            f.totalSize += Math.max(0, v.sizeBytes);
            f.totalDurationMs += Math.max(0, v.durationMs);
            if (f.cover == null || v.dateAddedSec > f.latestDateSec) {
                f.latestDateSec = v.dateAddedSec;
                f.cover = v;
            }
        }
        return new ArrayList<>(map.values());
    }

    public static void sortFolders(List<FolderInfo> folders, final int mode) {
        Collections.sort(folders, new Comparator<FolderInfo>() {
            @Override
            public int compare(FolderInfo a, FolderInfo b) {
                int r;
                switch (mode) {
                    case SORT_NAME:
                        r = 0;
                        break;
                    case SORT_SIZE:
                        r = Long.compare(b.totalSize, a.totalSize);
                        break;
                    case SORT_DURATION:
                        r = Long.compare(b.totalDurationMs, a.totalDurationMs);
                        break;
                    case SORT_DATE:
                    default:
                        r = Long.compare(b.latestDateSec, a.latestDateSec);
                        break;
                }
                if (r != 0) return r;
                r = NaturalOrder.INSTANCE.compare(a.name, b.name);
                if (r != 0) return r;
                return a.id.compareTo(b.id);
            }
        });
    }

    /** Videos inside one folder (by folder id as produced by {@link #groupFolders}). */
    public static List<VideoItem> inFolder(List<VideoItem> items, String folderId) {
        List<VideoItem> out = new ArrayList<>();
        if (folderId == null) return out;
        for (VideoItem v : items) {
            String key = v.bucketId != null && !v.bucketId.isEmpty() ? v.bucketId
                    : ("name:" + (v.bucketName == null ? "" : v.bucketName));
            if (folderId.equals(key)) out.add(v);
        }
        return out;
    }

    /** True when every word of the query appears in the text (case-insensitive). Empty query matches all. */
    public static boolean matches(String text, String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        for (String word : q.split("\\s+")) {
            if (!word.isEmpty() && !t.contains(word)) return false;
        }
        return true;
    }
}
