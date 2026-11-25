package com.ahmed.noorplayer;

import android.content.Context;
import android.content.SharedPreferences;

import com.ahmed.noorplayer.core.HistoryCodec;
import com.ahmed.noorplayer.core.HistoryEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Settings and watch history, stored in the app's private preferences. */
final class Prefs {
    private static Prefs sInstance;

    static synchronized Prefs get(Context c) {
        if (sInstance == null) sInstance = new Prefs(c.getApplicationContext());
        return sInstance;
    }

    private final SharedPreferences sp;
    private List<HistoryEntry> history;

    private Prefs(Context c) {
        sp = c.getSharedPreferences("noor_player", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- settings

    boolean resume() { return sp.getBoolean("resume", true); }
    void setResume(boolean v) { sp.edit().putBoolean("resume", v).apply(); }

    boolean autoplayNext() { return sp.getBoolean("autoplay_next", true); }
    void setAutoplayNext(boolean v) { sp.edit().putBoolean("autoplay_next", v).apply(); }

    boolean gestures() { return sp.getBoolean("gestures", true); }
    void setGestures(boolean v) { sp.edit().putBoolean("gestures", v).apply(); }

    boolean autoPip() { return sp.getBoolean("auto_pip", true); }
    void setAutoPip(boolean v) { sp.edit().putBoolean("auto_pip", v).apply(); }

    boolean autoRotate() { return sp.getBoolean("auto_rotate", true); }
    void setAutoRotate(boolean v) { sp.edit().putBoolean("auto_rotate", v).apply(); }

    boolean tipShown() { return sp.getBoolean("tip_shown", false); }
    void setTipShown() { sp.edit().putBoolean("tip_shown", true).apply(); }

    boolean askedPermission() { return sp.getBoolean("asked_permission", false); }
    void setAskedPermission() { sp.edit().putBoolean("asked_permission", true).apply(); }

    int seekStepSec() {
        int v = sp.getInt("seek_step", 10);
        return v < 1 || v > 120 ? 10 : v;
    }
    void setSeekStepSec(int v) { sp.edit().putInt("seek_step", v).apply(); }

    /** 0 small, 1 medium, 2 large, 3 extra large. */
    int subSize() {
        int v = sp.getInt("sub_size", 1);
        return v < 0 || v > 3 ? 1 : v;
    }
    void setSubSize(int v) { sp.edit().putInt("sub_size", v).apply(); }

    int sortMode() {
        int v = sp.getInt("sort", 0);
        return v < 0 || v > 3 ? 0 : v;
    }
    void setSortMode(int v) { sp.edit().putInt("sort", v).apply(); }

    // ---------------------------------------------------------------- history

    private void ensureHistory() {
        if (history == null) {
            List<HistoryEntry> h;
            try {
                h = HistoryCodec.decode(sp.getString("history", null));
            } catch (RuntimeException e) {
                h = new ArrayList<>();
            }
            history = h;
        }
    }

    private void persist() {
        sp.edit().putString("history", HistoryCodec.encode(history)).apply();
    }

    /** Copy of the history, newest first. */
    synchronized List<HistoryEntry> history() {
        ensureHistory();
        List<HistoryEntry> copy = new ArrayList<>(history.size());
        for (HistoryEntry e : history) copy.add(e.copy());
        return copy;
    }

    synchronized Map<String, HistoryEntry> historyMap() {
        ensureHistory();
        Map<String, HistoryEntry> map = new HashMap<>();
        for (HistoryEntry e : history) map.put(e.uri, e.copy());
        return map;
    }

    synchronized HistoryEntry findHistory(String uri) {
        ensureHistory();
        HistoryEntry e = HistoryCodec.find(history, uri);
        return e == null ? null : e.copy();
    }

    synchronized void savePlayback(String uri, String title, long mediaId, long positionMs, long durationMs, boolean finished) {
        if (uri == null || uri.isEmpty()) return;
        ensureHistory();
        HistoryEntry old = HistoryCodec.find(history, uri);
        HistoryEntry e = new HistoryEntry();
        e.uri = uri;
        e.title = title != null && !title.isEmpty() ? title : (old != null ? old.title : uri);
        e.positionMs = Math.max(0, positionMs);
        e.durationMs = durationMs > 0 ? durationMs : (old != null ? old.durationMs : 0);
        e.finished = finished;
        e.mediaId = mediaId > 0 ? mediaId : (old != null ? old.mediaId : -1);
        e.lastPlayedMs = System.currentTimeMillis();
        HistoryCodec.upsert(history, e);
        persist();
    }

    synchronized void removeHistory(String uri) {
        ensureHistory();
        HistoryCodec.remove(history, uri);
        persist();
    }

    synchronized void clearHistory() {
        history = new ArrayList<>();
        persist();
    }
}
