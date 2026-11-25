package com.ahmed.noorplayer.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.Charset;
import java.util.List;
import org.junit.Test;

public class SubtitleTest {
    private static final String SRT = "1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Hello</i> there\r\n\r\n"
            + "2\r\n00:00:04,000 --> 00:00:06,000\r\n{\\an8}Second &amp; line\r\nwith two rows\r\n\r\n"
            + "3\r\n00:01:00,250 --> 00:01:02,000\r\nمرحبا بك\r\n";

    @Test
    public void parsesSrt() {
        List<SubtitleCue> cues = SubtitleParser.parseCues(SRT);
        assertEquals(3, cues.size());
        assertEquals(1000, cues.get(0).startMs);
        assertEquals(3500, cues.get(0).endMs);
        assertEquals("Hello there", cues.get(0).text);
        assertEquals("Second & line\nwith two rows", cues.get(1).text);
        assertEquals(60_250, cues.get(2).startMs);
        assertEquals("مرحبا بك", cues.get(2).text);
    }

    @Test
    public void lookupByTime() {
        SubtitleTrack t = SubtitleParser.parse(SRT);
        assertNull(t.textAt(0));
        assertEquals("Hello there", t.textAt(1000));
        assertEquals("Hello there", t.textAt(3499));
        assertNull(t.textAt(3500));
        assertNull(t.textAt(3900));
        assertEquals("Second & line\nwith two rows", t.textAt(5000));
        assertEquals("مرحبا بك", t.textAt(61_000));
        assertNull(t.textAt(10_000_000));
    }

    @Test
    public void overlappingCuesShowTogether() {
        String s = "1\n00:00:01,000 --> 00:00:10,000\nLong\n\n2\n00:00:02,000 --> 00:00:03,000\nShort\n";
        SubtitleTrack t = SubtitleParser.parse(s);
        assertEquals("Long\nShort", t.textAt(2500));
        assertEquals("Long", t.textAt(5000));
    }

    @Test
    public void parsesVtt() {
        String vtt = "WEBVTT\n\nNOTE a comment\n\nintro\n00:01.000 --> 00:02.500 align:start position:10%\n"
                + "<v Roger>Hi <b>you</b></v>\n\n00:00:03.000 --> 00:00:04.000\nBye\n";
        List<SubtitleCue> cues = SubtitleParser.parseCues(vtt);
        assertEquals(2, cues.size());
        assertEquals(1000, cues.get(0).startMs);
        assertEquals(2500, cues.get(0).endMs);
        assertEquals("Hi you", cues.get(0).text);
        assertEquals("Bye", cues.get(1).text);
    }

    @Test
    public void toleratesMissingBlankLinesAndBom() {
        String s = "﻿1\n00:00:01,000 --> 00:00:02,000\nA\n2\n00:00:03,000 --> 00:00:04,000\nB\n";
        List<SubtitleCue> cues = SubtitleParser.parseCues(s);
        assertEquals(2, cues.size());
        assertEquals("A", cues.get(0).text);
        assertEquals("B", cues.get(1).text);
    }

    @Test
    public void badInputGivesNoCues() {
        assertEquals(0, SubtitleParser.parseCues("this is not a subtitle file").size());
        assertEquals(0, SubtitleParser.parseCues("").size());
        assertEquals(0, SubtitleParser.parseCues(null).size());
        assertEquals(0, SubtitleParser.parse("garbage --> more garbage\ntext").size());
    }

    @Test
    public void timeParsing() {
        assertEquals(3_723_456, SubtitleParser.parseTime("01:02:03,456"));
        assertEquals(3_723_456, SubtitleParser.parseTime("1:02:03.456"));
        assertEquals(62_500, SubtitleParser.parseTime("01:02.5"));
        assertEquals(5_000, SubtitleParser.parseTime("00:00:05"));
        assertEquals(-1, SubtitleParser.parseTime("abc"));
        assertEquals(-1, SubtitleParser.parseTime("1:2:3:4"));
        assertEquals(-1, SubtitleParser.parseTime(""));
    }

    @Test
    public void endBeforeStartIsRepaired() {
        List<SubtitleCue> cues = SubtitleParser.parseCues("00:00:05,000 --> 00:00:01,000\nOops\n");
        assertEquals(1, cues.size());
        assertEquals(7000, cues.get(0).endMs);
    }

    @Test
    public void decodesUtf8AndBoms() {
        String text = "مرحبا Hello";
        assertEquals(text, TextDecoder.decode(text.getBytes(Charset.forName("UTF-8"))));
        byte[] withBom = concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, text.getBytes(Charset.forName("UTF-8")));
        assertEquals(text, TextDecoder.decode(withBom));
        byte[] le = concat(new byte[]{(byte) 0xFF, (byte) 0xFE}, text.getBytes(Charset.forName("UTF-16LE")));
        assertEquals(text, TextDecoder.decode(le));
        byte[] be = concat(new byte[]{(byte) 0xFE, (byte) 0xFF}, text.getBytes(Charset.forName("UTF-16BE")));
        assertEquals(text, TextDecoder.decode(be));
        String ascii = "00:00:01,000 --> 00:00:02,000\nplain text subtitle\n";
        assertEquals(ascii, TextDecoder.decode(ascii.getBytes(Charset.forName("UTF-16LE"))));
    }

    @Test
    public void decodesLegacyArabicAndWestern() {
        String arabic = "1\n00:00:01,000 --> 00:00:02,000\nكيف حالك يا صديقي؟\n";
        assertEquals(arabic, TextDecoder.decode(arabic.getBytes(Charset.forName("windows-1256"))));
        String french = "1\n00:00:01,000 --> 00:00:02,000\nCafé crème, très déjà vu à Noël\n";
        assertEquals(french, TextDecoder.decode(french.getBytes(Charset.forName("windows-1252"))));
        String spanish = "Mañana señor, ¿está aquí? Sí, más días.";
        assertEquals(spanish, TextDecoder.decode(spanish.getBytes(Charset.forName("windows-1252"))));
        String german = "Größe Übung für Mädchen";
        assertEquals(german, TextDecoder.decode(german.getBytes(Charset.forName("windows-1252"))));
        String arabicLong = "1\n00:00:01,000 --> 00:00:03,000\nهل تريد أن تشاهد الفيلم الليلة؟\n\n"
                + "2\n00:00:04,000 --> 00:00:05,000\nنعم، بالتأكيد!\n";
        assertEquals(arabicLong, TextDecoder.decode(arabicLong.getBytes(Charset.forName("windows-1256"))));
        assertEquals("", TextDecoder.decode(new byte[0]));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
