package com.ahmed.noorplayer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Stores watch history as one compact text value, and keeps the list ordered and bounded. */
public final class HistoryCodec {
    private HistoryCodec() {}

    public static final int MAX_ENTRIES = 300;
    private static final String HEADER = "H1";

    public static String encode(List<HistoryEntry> list) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (HistoryEntry e : list) {
            if (e == null || e.uri == null || e.uri.isEmpty()) continue;
            sb.append('\n')
                    .append(escape(e.uri)).append('\t')
                    .append(escape(e.title)).append('\t')
                    .append(e.positionMs).append('\t')
                    .append(e.durationMs).append('\t')
                    .append(e.lastPlayedMs).append('\t')
                    .append(e.finished ? '1' : '0').append('\t')
                    .append(e.mediaId);
        }
        return sb.toString();
    }

    public static List<HistoryEntry> decode(String s) {
        List<HistoryEntry> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        String[] lines = s.split("\n", -1);
        for (String line : lines) {
            if (line.isEmpty() || line.equals(HEADER)) continue;
            String[] p = line.split("\t", -1);
            if (p.length < 6) continue;
            try {
                HistoryEntry e = new HistoryEntry();
                e.uri = unescape(p[0]);
                if (e.uri.isEmpty()) continue;
                e.title = unescape(p[1]);
                e.positionMs = Long.parseLong(p[2]);
                e.durationMs = Long.parseLong(p[3]);
                e.lastPlayedMs = Long.parseLong(p[4]);
                e.finished = "1".equals(p[5]);
                e.mediaId = p.length > 6 && !p[6].isEmpty() ? Long.parseLong(p[6]) : -1;
                out.add(e);
            } catch (NumberFormatException ignored) {
                // skip a damaged line, keep the rest
            }
        }
        sortAndTrim(out);
        return out;
    }

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '%': sb.append("%25"); break;
                case '\t': sb.append("%09"); break;
                case '\n': sb.append("%0A"); break;
                case '\r': sb.append("%0D"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        if (s == null || s.indexOf('%') < 0) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    sb.append((char) (hi * 16 + lo));
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Newest first, no duplicate URIs (keeps the newest), at most MAX_ENTRIES. */
    public static void sortAndTrim(List<HistoryEntry> list) {
        Collections.sort(list, new Comparator<HistoryEntry>() {
            @Override
            public int compare(HistoryEntry a, HistoryEntry b) {
                return Long.compare(b.lastPlayedMs, a.lastPlayedMs);
            }
        });
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        Iterator<HistoryEntry> it = list.iterator();
        while (it.hasNext()) {
            HistoryEntry e = it.next();
            if (!seen.add(e.uri)) it.remove();
        }
        while (list.size() > MAX_ENTRIES) list.remove(list.size() - 1);
    }

    public static HistoryEntry find(List<HistoryEntry> list, String uri) {
        if (uri == null) return null;
        for (HistoryEntry e : list) if (uri.equals(e.uri)) return e;
        return null;
    }

    /**
     * The same video opened through a different link (for example from another app each time):
     * newest entry with the same title and nearly the same length, or null.
     */
    public static HistoryEntry findSimilar(List<HistoryEntry> list, String title, long durationMs) {
        if (title == null || title.trim().isEmpty() || durationMs <= 0) return null;
        for (HistoryEntry e : list) {
            if (e.durationMs > 0 && title.equals(e.title) && Math.abs(e.durationMs - durationMs) <= 1500) return e;
        }
        return null;
    }

    /** Inserts or replaces the entry for e.uri and re-sorts. */
    public static void upsert(List<HistoryEntry> list, HistoryEntry e) {
        remove(list, e.uri);
        list.add(0, e);
        sortAndTrim(list);
    }

    public static void remove(List<HistoryEntry> list, String uri) {
        if (uri == null) return;
        Iterator<HistoryEntry> it = list.iterator();
        while (it.hasNext()) {
            HistoryEntry e = it.next();
            if (e == null || uri.equals(e.uri)) it.remove();
        }
    }
}
