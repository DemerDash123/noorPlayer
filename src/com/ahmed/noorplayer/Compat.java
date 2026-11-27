package com.ahmed.noorplayer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.Rational;
import android.util.Size;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Access to Android APIs newer than the app's build level (precise seeking, picture-in-picture,
 * fast thumbnails). Every call is guarded by an SDK check and fails safely if unavailable.
 */
final class Compat {
    private Compat() {}

    static final int SDK = Build.VERSION.SDK_INT;

    /** MediaPlayer.SEEK_CLOSEST_SYNC and SEEK_CLOSEST (API 26). */
    private static final int SEEK_CLOSEST_SYNC = 2;
    private static final int SEEK_CLOSEST = 3;

    private static Method seekWithMode;
    private static boolean seekLookedUp;

    /** Seeks to ms; precise = frame-accurate (slower), otherwise the nearest key frame (fast, for scrubbing). */
    static void seekTo(MediaPlayer mp, long ms, boolean precise) {
        long target = Math.max(0, ms);
        if (SDK >= 26) {
            try {
                if (!seekLookedUp) {
                    seekLookedUp = true;
                    seekWithMode = MediaPlayer.class.getMethod("seekTo", long.class, int.class);
                }
                if (seekWithMode != null) {
                    seekWithMode.invoke(mp, target, precise ? SEEK_CLOSEST : SEEK_CLOSEST_SYNC);
                    return;
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IllegalStateException) throw (IllegalStateException) cause;
                // anything else: fall back to the classic seek below
            } catch (Throwable ignored) {
                // fall back to the classic seek below
            }
        }
        mp.seekTo((int) Math.min(Integer.MAX_VALUE, target));
    }

    // ---------------------------------------------------------------- picture-in-picture

    static boolean supportsPip(Context c) {
        if (SDK < 26) return false;
        try {
            return c.getPackageManager().hasSystemFeature("android.software.picture_in_picture");
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isInPip(Activity a) {
        if (SDK < 24) return false;
        try {
            Object r = Activity.class.getMethod("isInPictureInPictureMode").invoke(a);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Builds PictureInPictureParams (API 26) with the video's aspect ratio and play/pause/skip buttons. */
    static Object buildPipParams(Context c, int videoW, int videoH, boolean playing) {
        if (SDK < 26) return null;
        try {
            Class<?> builderCls = Class.forName("android.app.PictureInPictureParams$Builder");
            Object builder = builderCls.getConstructor().newInstance();
            builderCls.getMethod("setAspectRatio", Rational.class).invoke(builder, pipRatio(videoW, videoH));
            List<Object> actions = buildPipActions(c, playing);
            if (actions != null) builderCls.getMethod("setActions", List.class).invoke(builder, actions);
            return builderCls.getMethod("build").invoke(builder);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Android only allows ratios between 1:2.39 and 2.39:1 for the floating window. */
    static Rational pipRatio(int w, int h) {
        if (w <= 0 || h <= 0) return new Rational(16, 9);
        float r = (float) w / h;
        if (r > 2.39f) return new Rational(239, 100);
        if (r < 1f / 2.39f) return new Rational(100, 239);
        return new Rational(w, h);
    }

    private static List<Object> buildPipActions(Context c, boolean playing) {
        try {
            Class<?> ra = Class.forName("android.app.RemoteAction");
            Constructor<?> ctor = ra.getConstructor(Icon.class, CharSequence.class, CharSequence.class, PendingIntent.class);
            List<Object> list = new ArrayList<>(3);
            String back = c.getString(R.string.player_rewind);
            String toggle = c.getString(playing ? R.string.player_pause : R.string.player_play);
            String fwd = c.getString(R.string.player_forward);
            list.add(ctor.newInstance(Icon.createWithResource(c, R.drawable.ic_fast_rewind), back, back,
                    PipActionReceiver.pending(c, PipActionReceiver.CMD_REWIND)));
            list.add(ctor.newInstance(Icon.createWithResource(c, playing ? R.drawable.ic_pause : R.drawable.ic_play),
                    toggle, toggle, PipActionReceiver.pending(c, PipActionReceiver.CMD_TOGGLE)));
            list.add(ctor.newInstance(Icon.createWithResource(c, R.drawable.ic_fast_forward), fwd, fwd,
                    PipActionReceiver.pending(c, PipActionReceiver.CMD_FORWARD)));
            return list;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean enterPip(Activity a, Object params) {
        if (SDK < 26 || params == null) return false;
        try {
            Class<?> p = Class.forName("android.app.PictureInPictureParams");
            Object r = Activity.class.getMethod("enterPictureInPictureMode", p).invoke(a, params);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    static void setPipParams(Activity a, Object params) {
        if (SDK < 26 || params == null) return;
        try {
            Class<?> p = Class.forName("android.app.PictureInPictureParams");
            Activity.class.getMethod("setPictureInPictureParams", p).invoke(a, params);
        } catch (Throwable ignored) {
            // the floating window keeps its previous settings
        }
    }

    // ---------------------------------------------------------------- thumbnails

    private static Method loadThumbnail;
    private static boolean thumbLookedUp;

    /** ContentResolver.loadThumbnail (API 29); null if unavailable or there is no thumbnail. */
    static Bitmap loadThumbnail(ContentResolver cr, Uri uri, int w, int h) {
        if (SDK < 29) return null;
        try {
            synchronized (Compat.class) {
                if (!thumbLookedUp) {
                    thumbLookedUp = true;
                    loadThumbnail = ContentResolver.class.getMethod("loadThumbnail", Uri.class, Size.class,
                            CancellationSignal.class);
                }
            }
            if (loadThumbnail == null) return null;
            Object r = loadThumbnail.invoke(cr, uri, new Size(w, h), null);
            return r instanceof Bitmap ? (Bitmap) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- deleting videos

    /**
     * MediaStore.createDeleteRequest (API 30): a system dialog where the user approves deleting
     * these videos; Android deletes them if approved. Null below Android 11 or on failure.
     */
    static PendingIntent createDeleteRequest(ContentResolver cr, Collection<Uri> uris) {
        if (SDK < 30 || uris == null || uris.isEmpty()) return null;
        try {
            Object r = MediaStore.class.getMethod("createDeleteRequest", ContentResolver.class, Collection.class)
                    .invoke(null, cr, uris);
            return r instanceof PendingIntent ? (PendingIntent) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Android 10 refuses to delete another app's video with a RecoverableSecurityException that carries
     * an "allow" dialog. Returns that dialog's PendingIntent, or null for any other error.
     */
    static PendingIntent recoverableAction(Throwable e) {
        if (e == null || SDK < 26) return null;
        try {
            Class<?> rse = Class.forName("android.app.RecoverableSecurityException");
            if (!rse.isInstance(e)) return null;
            Object action = rse.getMethod("getUserAction").invoke(e);
            Object pi = action.getClass().getMethod("getActionIntent").invoke(action);
            return pi instanceof PendingIntent ? (PendingIntent) pi : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
