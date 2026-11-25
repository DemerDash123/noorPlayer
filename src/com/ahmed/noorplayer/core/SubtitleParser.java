package com.ahmed.noorplayer.core;

import java.util.ArrayList;
import java.util.List;

/** Parses SubRip (.srt) and WebVTT (.vtt) subtitles. Tolerant of the usual real-world sloppiness. */
public final class SubtitleParser {
    private SubtitleParser() {}

    public static SubtitleTrack parse(String content) {
        return new SubtitleTrack(parseCues(content));
    }

    public static List<SubtitleCue> parseCues(String content) {
        List<SubtitleCue> cues = new ArrayList<>();
        if (content == null) return cues;
        String text = content;
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);

        long start = -1, end = -1;
        StringBuilder body = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.contains("-->")) {
                long[] times = parseTiming(trimmed);
                if (times != null) {
                    flush(cues, start, end, body);
                    start = times[0];
                    end = times[1];
                    body = new StringBuilder();
                    continue;
                }
            }
            if (body == null) continue; // header, cue identifiers, NOTE/STYLE blocks
            if (trimmed.isEmpty()) {
                flush(cues, start, end, body);
                body = null;
                continue;
            }
            // A cue number directly followed by a timing line (missing blank line): not part of the text.
            if (isAllDigits(trimmed) && i + 1 < lines.length && lines[i + 1].contains("-->")
                    && parseTiming(lines[i + 1].trim()) != null) {
                continue;
            }
            if (body.length() > 0) body.append('\n');
            body.append(trimmed);
        }
        flush(cues, start, end, body);
        return cues;
    }

    private static void flush(List<SubtitleCue> cues, long start, long end, StringBuilder body) {
        if (body == null || start < 0) return;
        String cleaned = clean(body.toString());
        if (cleaned.isEmpty()) return;
        if (end <= start) end = start + 2000;
        cues.add(new SubtitleCue(start, end, cleaned));
    }

    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    /** "00:01:02,500 --> 00:01:04,000 align:start" -> {62500, 64000}; null if not a timing line. */
    static long[] parseTiming(String line) {
        int arrow = line.indexOf("-->");
        if (arrow < 0) return null;
        String left = line.substring(0, arrow).trim();
        String right = line.substring(arrow + 3).trim();
        int sp = indexOfWhitespace(right);
        if (sp >= 0) right = right.substring(0, sp);
        int lsp = lastIndexOfWhitespace(left);
        if (lsp >= 0) left = left.substring(lsp + 1);
        long a = parseTime(left);
        long b = parseTime(right);
        if (a < 0 || b < 0) return null;
        return new long[]{a, b};
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    private static int lastIndexOfWhitespace(String s) {
        for (int i = s.length() - 1; i >= 0; i--) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    /** Parses "h:mm:ss,mmm", "mm:ss.mmm" and similar into milliseconds; -1 when invalid. */
    static long parseTime(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().replace(',', '.');
        if (s.isEmpty()) return -1;
        String main = s, frac = "";
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            main = s.substring(0, dot);
            frac = s.substring(dot + 1);
        }
        String[] parts = main.split(":", -1);
        try {
            long h = 0, m, sec;
            if (parts.length == 3) {
                h = parseNonNegative(parts[0]);
                m = parseNonNegative(parts[1]);
                sec = parseNonNegative(parts[2]);
            } else if (parts.length == 2) {
                m = parseNonNegative(parts[0]);
                sec = parseNonNegative(parts[1]);
            } else {
                return -1;
            }
            if (h < 0 || m < 0 || sec < 0) return -1;
            long ms = 0;
            if (!frac.isEmpty()) {
                if (frac.length() > 9 || !isAllDigits(frac)) return -1;
                String f = frac.length() > 3 ? frac.substring(0, 3) : frac;
                StringBuilder sb = new StringBuilder(f);
                while (sb.length() < 3) sb.append('0');
                ms = Long.parseLong(sb.toString());
            }
            return ((h * 60 + m) * 60 + sec) * 1000 + ms;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseNonNegative(String s) {
        String t = s.trim();
        if (t.isEmpty() || !isAllDigits(t)) return -1;
        return Long.parseLong(t);
    }

    /** Removes formatting tags ({\an8}, <i>, <font ...>) and decodes common HTML entities. */
    static String clean(String s) {
        String out = s.replaceAll("\\{[^}]*\\}", "");
        out = out.replaceAll("<[^>]*>", "");
        out = out.replace("\\N", "\n").replace("\\n", "\n");
        out = out.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        StringBuilder sb = new StringBuilder();
        for (String line : out.split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
        }
        return sb.toString();
    }
}
