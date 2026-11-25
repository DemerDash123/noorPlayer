package com.ahmed.noorplayer.core;

import java.util.Comparator;

/**
 * Case-insensitive "natural" string order: "Episode 2" sorts before "Episode 10".
 * Digit runs are compared by numeric value (any script's digits), everything else by character.
 */
public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {}

    @Override
    public int compare(String a, String b) {
        int r = compareNatural(a, b);
        if (r != 0) return r;
        if (a == null) a = "";
        if (b == null) b = "";
        return a.compareTo(b); // stable tie-breaker so the order is total
    }

    public static int compareNatural(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int i = 0, j = 0;
        final int na = a.length(), nb = b.length();
        while (i < na && j < nb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            boolean da = Character.isDigit(ca), db = Character.isDigit(cb);
            if (da && db) {
                int si = i, sj = j;
                while (si < na && Character.digit(a.charAt(si), 10) == 0) si++;
                while (sj < nb && Character.digit(b.charAt(sj), 10) == 0) sj++;
                int ei = si, ej = sj;
                while (ei < na && Character.isDigit(a.charAt(ei))) ei++;
                while (ej < nb && Character.isDigit(b.charAt(ej))) ej++;
                int lenA = ei - si, lenB = ej - sj;
                if (lenA != lenB) return lenA < lenB ? -1 : 1;
                for (int k = 0; k < lenA; k++) {
                    int d = Character.digit(a.charAt(si + k), 10) - Character.digit(b.charAt(sj + k), 10);
                    if (d != 0) return d < 0 ? -1 : 1;
                }
                // Same value: skip past the whole digit runs (including any leading zeros).
                int endA = i, endB = j;
                while (endA < na && Character.isDigit(a.charAt(endA))) endA++;
                while (endB < nb && Character.isDigit(b.charAt(endB))) endB++;
                i = endA;
                j = endB;
            } else {
                char la = Character.toLowerCase(Character.toUpperCase(ca));
                char lb = Character.toLowerCase(Character.toUpperCase(cb));
                if (la != lb) return la < lb ? -1 : 1;
                i++;
                j++;
            }
        }
        int ra = na - i, rb = nb - j;
        if (ra == rb) return 0;
        return ra < rb ? -1 : 1;
    }
}
