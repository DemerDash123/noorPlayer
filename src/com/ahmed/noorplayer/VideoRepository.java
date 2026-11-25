package com.ahmed.noorplayer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.ahmed.noorplayer.core.VideoItem;

import java.util.ArrayList;
import java.util.List;

/** Reads every video the phone's media library knows about (internal storage and SD card). */
final class VideoRepository {
    private VideoRepository() {}

    private static final String[] FULL = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            "duration",
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            "bucket_id",
            "bucket_display_name",
            "width",
            "height",
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
    };

    private static final String[] MINIMAL = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
    };

    /** Runs on a background thread. Returns an empty list if nothing can be read. */
    static List<VideoItem> loadAll(Context c) {
        List<VideoItem> list = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        Uri base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Cursor cur = query(cr, base, FULL);
        if (cur == null) cur = query(cr, base, MINIMAL);
        if (cur == null) return list;
        try {
            int iId = cur.getColumnIndex(MediaStore.Video.Media._ID);
            int iName = cur.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
            int iDur = cur.getColumnIndex("duration");
            int iSize = cur.getColumnIndex(MediaStore.Video.Media.SIZE);
            int iDate = cur.getColumnIndex(MediaStore.Video.Media.DATE_ADDED);
            int iBucket = cur.getColumnIndex("bucket_id");
            int iBucketName = cur.getColumnIndex("bucket_display_name");
            int iW = cur.getColumnIndex("width");
            int iH = cur.getColumnIndex("height");
            int iData = cur.getColumnIndex(MediaStore.Video.Media.DATA);
            int iMime = cur.getColumnIndex(MediaStore.Video.Media.MIME_TYPE);
            if (iId < 0) return list;
            while (cur.moveToNext()) {
                VideoItem v = new VideoItem();
                v.id = getLong(cur, iId);
                if (v.id <= 0) continue;
                v.uri = ContentUris.withAppendedId(base, v.id).toString();
                v.displayName = getString(cur, iName);
                if (v.displayName == null || v.displayName.isEmpty()) v.displayName = "Video " + v.id;
                v.title = VideoItem.stripExtension(v.displayName);
                v.durationMs = getLong(cur, iDur);
                v.sizeBytes = getLong(cur, iSize);
                v.dateAddedSec = getLong(cur, iDate);
                v.bucketId = getString(cur, iBucket);
                v.bucketName = getString(cur, iBucketName);
                v.width = (int) getLong(cur, iW);
                v.height = (int) getLong(cur, iH);
                v.path = getString(cur, iData);
                v.mime = getString(cur, iMime);
                list.add(v);
            }
        } catch (RuntimeException e) {
            // keep whatever was read before the problem
        } finally {
            cur.close();
        }
        return list;
    }

    private static Cursor query(ContentResolver cr, Uri uri, String[] projection) {
        try {
            return cr.query(uri, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String getString(Cursor c, int i) {
        try {
            return i < 0 || c.isNull(i) ? null : c.getString(i);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long getLong(Cursor c, int i) {
        try {
            return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
