package com.ahmed.noorplayer.core;

import java.util.ArrayList;
import java.util.List;

/** Ordered list of items with a current position, next/previous and repeat handling. */
public final class Playlist {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    private final List<PlayItem> items = new ArrayList<>();
    private int index;

    public Playlist(List<PlayItem> source, int startIndex) {
        if (source != null) {
            for (PlayItem p : source) {
                if (p != null && p.uri != null && !p.uri.isEmpty()) items.add(p);
            }
        }
        index = items.isEmpty() ? 0 : Math.max(0, Math.min(startIndex, items.size() - 1));
    }

    public static Playlist single(PlayItem item) {
        List<PlayItem> l = new ArrayList<>(1);
        l.add(item);
        return new Playlist(l, 0);
    }

    public int size() {
        return items.size();
    }

    public int index() {
        return index;
    }

    public PlayItem current() {
        return items.isEmpty() ? null : items.get(index);
    }

    public PlayItem peekNext(int repeatMode) {
        if (index < items.size() - 1) return items.get(index + 1);
        if (repeatMode == REPEAT_ALL && items.size() > 1) return items.get(0);
        return null;
    }

    public boolean hasNext(int repeatMode) {
        return peekNext(repeatMode) != null;
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean moveNext(int repeatMode) {
        if (index < items.size() - 1) {
            index++;
            return true;
        }
        if (repeatMode == REPEAT_ALL && items.size() > 1) {
            index = 0;
            return true;
        }
        return false;
    }

    public boolean movePrevious() {
        if (index > 0) {
            index--;
            return true;
        }
        return false;
    }
}
