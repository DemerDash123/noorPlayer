package com.ahmed.noorplayer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Loads video thumbnails in the background with a memory cache. The most recently requested
 * thumbnails (the rows on screen) are loaded first.
 */
final class ThumbnailLoader {
    private static ThumbnailLoader sInstance;

    static synchronized ThumbnailLoader get(Context c) {
        if (sInstance == null) sInstance = new ThumbnailLoader(c.getApplicationContext());
        return sInstance;
    }

    private static final int MAX_WIDTH = 360;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    // main thread only
    private final Set<String> inFlight = new HashSet<>();
    private final Map<String, WeakReference<ImageView>> waiting = new HashMap<>();
    private final ThreadPoolExecutor pool;

    private ThumbnailLoader(Context app) {
        this.app = app;
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L / 8L);
        cache = new LruCache<String, Bitmap>(Math.max(4 * 1024, maxKb)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
        pool = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LifoQueue(), new ThreadFactory() {
            private int n;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "thumbs-" + (++n));
                t.setDaemon(true);
                return t;
            }
        });
        pool.allowCoreThreadTimeOut(true);
    }

    /** Most recent request first. */
    private static final class LifoQueue extends LinkedBlockingDeque<Runnable> {
        @Override
        public boolean offer(Runnable r) {
            return super.offerFirst(r);
        }
    }

    static String keyForMedia(long mediaId) {
        return "m:" + mediaId;
    }

    static String keyForUri(String uri) {
        return "u:" + uri;
    }

    /**
     * Shows the thumbnail for a video in the ImageView (or clears it while loading).
     * mediaId > 0 uses the media library; otherwise a frame is read from content:// or file:// uris.
     */
    void load(ImageView view, final String key, final long mediaId, final String uri) {
        view.setTag(R.id.tag_thumb_key, key);
        if (key == null) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        if (failed.contains(key)) return;
        waiting.put(key, new WeakReference<>(view));
        if (inFlight.contains(key)) return;
        inFlight.add(key);
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    Bitmap b;
                    try {
                        b = decode(mediaId, uri);
                    } catch (Throwable t) {
                        b = null;
                    }
                    final Bitmap result = b;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            onLoaded(key, result);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key);
        }
    }

    private void onLoaded(String key, Bitmap result) {
        inFlight.remove(key);
        if (result != null) cache.put(key, result);
        else failed.add(key);
        WeakReference<ImageView> ref = waiting.remove(key);
        ImageView v = ref != null ? ref.get() : null;
        if (v != null && result != null && key.equals(v.getTag(R.id.tag_thumb_key))) {
            v.setImageBitmap(result);
        }
    }

    void forgetFailures() {
        failed.clear();
    }

    private Bitmap decode(long mediaId, String uri) {
        ContentResolver cr = app.getContentResolver();
        Bitmap b = null;
        if (mediaId > 0) {
            if (Build.VERSION.SDK_INT >= 29) {
                Uri mu = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId);
                b = Compat.loadThumbnail(cr, mu, 320, 180);
            } else {
                b = MediaStore.Video.Thumbnails.getThumbnail(cr, mediaId, MediaStore.Video.Thumbnails.MINI_KIND, null);
            }
        }
        if (b == null && uri != null && (uri.startsWith("content://") || uri.startsWith("file://"))) {
            b = frameFromVideo(Uri.parse(uri));
        }
        return scaleDown(b);
    }

    private Bitmap frameFromVideo(Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(app, uri);
            Bitmap f = r.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (f == null) f = r.getFrameAtTime();
            return f;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) {
                // nothing to do
            }
        }
    }

    private static Bitmap scaleDown(Bitmap b) {
        if (b == null || b.getWidth() <= MAX_WIDTH) return b;
        float s = MAX_WIDTH / (float) b.getWidth();
        int h = Math.max(1, Math.round(b.getHeight() * s));
        Bitmap scaled = Bitmap.createScaledBitmap(b, MAX_WIDTH, h, true);
        if (scaled != b) b.recycle();
        return scaled;
    }
}
