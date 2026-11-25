package com.ahmed.noorplayer;

import com.ahmed.noorplayer.core.PlayItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands the list of videos from the library to the player without squeezing it through an Intent.
 * If the app process was restarted in between, the token will not match and the player falls back
 * to the single video in the Intent.
 */
final class PlaylistHolder {
    private PlaylistHolder() {}

    private static List<PlayItem> items;
    private static long token;
    private static long counter;

    static synchronized long put(List<PlayItem> list) {
        items = new ArrayList<>(list);
        counter++;
        token = (System.currentTimeMillis() << 8) ^ counter;
        if (token == 0) token = 1;
        return token;
    }

    static synchronized List<PlayItem> take(long t) {
        if (t == 0 || t != token || items == null) return null;
        return new ArrayList<>(items);
    }
}
