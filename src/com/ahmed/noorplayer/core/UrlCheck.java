package com.ahmed.noorplayer.core;

import java.net.URLDecoder;
import java.util.Locale;

/** Cleans up a link the user typed or pasted, and derives a title for it. */
public final class UrlCheck {
    private UrlCheck() {}

    /** Returns a playable link, or null if the text is not a usable http/https/rtsp/content/file link. */
    public static String normalize(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.length() >= 2 && s.startsWith("<") && s.endsWith(">")) s = s.substring(1, s.length() - 1).trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return null;
        if (s.indexOf('\n') >= 0 || s.indexOf('\t') >= 0) return null;
        s = s.replace(" ", "%20");
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:") || lower.startsWith("content:")) {
            return lower.startsWith("content://") || lower.startsWith("file:/") ? s : null;
        }
        int sep = lower.indexOf("://");
        if (sep < 0) {
            s = "https://" + s;
            lower = "https://" + lower;
            sep = lower.indexOf("://");
        }
        String scheme = lower.substring(0, sep);
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("rtsp")) return null;
        String rest = s.substring(sep + 3);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String host = rest.substring(0, end);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        if (host.isEmpty()) return null;
        String hostOnly = host;
        if (hostOnly.startsWith("[")) {
            // IPv6 literal like [::1]:8080
            return hostOnly.indexOf(']') > 1 ? s : null;
        }
        int colon = hostOnly.indexOf(':');
        boolean hasPort = colon >= 0;
        if (hasPort) hostOnly = hostOnly.substring(0, colon);
        if (hostOnly.isEmpty()) return null;
        if (!hostOnly.contains(".") && !hostOnly.equalsIgnoreCase("localhost") && !hasPort) return null;
        return s;
    }

    /** True for text that is clearly a single web/stream link (used to pre-fill from the clipboard). */
    public static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 10 || t.length() > 4000) return false;
        for (int i = 0; i < t.length(); i++) if (Character.isWhitespace(t.charAt(i))) return false;
        String l = t.toLowerCase(Locale.ROOT);
        return (l.startsWith("http://") || l.startsWith("https://") || l.startsWith("rtsp://")) && normalize(t) != null;
    }

    /** "https://x.com/videos/My%20Clip.mp4?token=1" -> "My Clip.mp4"; falls back to the host name. */
    public static String titleFromUrl(String url) {
        if (url == null) return "";
        String s = url;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int sep = s.indexOf("://");
        String afterScheme = sep >= 0 ? s.substring(sep + 3) : s;
        int slash = afterScheme.lastIndexOf('/');
        String last = slash >= 0 ? afterScheme.substring(slash + 1) : afterScheme;
        try {
            last = URLDecoder.decode(last.replace("+", "%2B"), "UTF-8");
        } catch (Exception ignored) {
            // keep the raw text
        }
        last = last.trim();
        return last.isEmpty() ? url : last;
    }
}
