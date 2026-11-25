package com.ahmed.noorplayer.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/**
 * Turns subtitle file bytes into text. Handles BOMs, UTF-16, strict UTF-8, and falls back to
 * Windows-1256 for old Arabic subtitles (or Windows-1252 for Western European ones).
 */
public final class TextDecoder {
    private TextDecoder() {}

    public static String decode(byte[] data) {
        if (data == null || data.length == 0) return "";
        int n = data.length;
        if (n >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new String(data, 3, n - 3, Charset.forName("UTF-8"));
        }
        if (n >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, n - 2, Charset.forName("UTF-16LE"));
        }
        if (n >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, n - 2, Charset.forName("UTF-16BE"));
        }
        String utf16 = guessUtf16WithoutBom(data);
        if (utf16 != null) return new String(data, Charset.forName(utf16));
        try {
            return Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException notUtf8) {
            return decodeLegacy(data);
        }
    }

    private static String guessUtf16WithoutBom(byte[] d) {
        int n = Math.min(d.length, 4000) & ~1;
        if (n < 8) return null;
        int zeroEven = 0, zeroOdd = 0;
        for (int i = 0; i < n; i++) {
            if (d[i] == 0) {
                if ((i & 1) == 0) zeroEven++;
                else zeroOdd++;
            }
        }
        int half = n / 2;
        if (zeroOdd > half * 0.4 && zeroEven < half * 0.05) return "UTF-16LE";
        if (zeroEven > half * 0.4 && zeroOdd < half * 0.05) return "UTF-16BE";
        return null;
    }

    private static String decodeLegacy(byte[] data) {
        String arabic = tryDecode(data, "windows-1256");
        if (arabic != null && looksLikeArabic(data, arabic)) return arabic;
        String western = tryDecode(data, "windows-1252");
        if (western != null) return western;
        if (arabic != null) return arabic;
        return new String(data, Charset.forName("ISO-8859-1"));
    }

    /**
     * Arabic text in Windows-1256 is made of whole words of high bytes (runs of letters), while
     * Western European text has isolated accented letters between plain ASCII ones.
     */
    static boolean looksLikeArabic(byte[] data, String decoded1256) {
        int high = 0, adjacent = 0;
        for (int i = 0; i < data.length; i++) {
            if ((data[i] & 0xFF) < 0x80) continue;
            high++;
            boolean prevHigh = i > 0 && (data[i - 1] & 0xFF) >= 0x80;
            boolean nextHigh = i + 1 < data.length && (data[i + 1] & 0xFF) >= 0x80;
            if (prevHigh || nextHigh) adjacent++;
        }
        if (high == 0) return false;
        int letters = 0;
        for (int i = 0; i < decoded1256.length(); i++) {
            char c = decoded1256.charAt(i);
            if (c >= 'ء' && c <= 'ي') letters++;
        }
        return adjacent * 2 >= high && letters * 5 >= high * 2;
    }

    private static String tryDecode(byte[] data, String charset) {
        try {
            if (!Charset.isSupported(charset)) return null;
            return new String(data, Charset.forName(charset));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
