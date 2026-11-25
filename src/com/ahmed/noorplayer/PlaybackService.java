package com.ahmed.noorplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service that keeps the app alive while a video plays in the background
 * (after leaving the app or turning off the screen) and shows the media notification
 * with back / play-pause / forward / close buttons. The player itself stays in PlayerActivity.
 */
public class PlaybackService extends Service {
    static final int NOTIFICATION_ID = 7;
    private static final String CHANNEL_ID = "playback";
    static final String ACTION_SHOW_PLAYER = "com.ahmed.noorplayer.SHOW_PLAYER";

    /** What the notification shows. Set by PlayerActivity before start/update. */
    static final class State {
        String title;
        boolean playing;
        MediaSession.Token token;
        Bitmap art;
    }

    private static State sState;
    private static boolean sRunning;

    static synchronized void start(Context c, State state) {
        sState = state;
        Intent i = new Intent(c, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Context.class.getMethod("startForegroundService", Intent.class).invoke(c, i);
                return;
            } catch (Throwable t) {
                // fall through to a plain start (Android may refuse a background start)
            }
        }
        c.startService(i);
    }

    /** Refreshes the notification if the service is running. */
    static synchronized void update(Context c, State state) {
        sState = state;
        if (!sRunning) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, build(c.getApplicationContext(), state));
        } catch (RuntimeException ignored) {
            // the notification simply keeps its previous look
        }
    }

    static synchronized void stop(Context c) {
        sState = null;
        try {
            c.stopService(new Intent(c, PlaybackService.class));
        } catch (RuntimeException ignored) {
            // not running
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        State s;
        synchronized (PlaybackService.class) {
            s = sState;
        }
        if (s == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, build(this, s));
            synchronized (PlaybackService.class) {
                sRunning = true;
            }
        } catch (RuntimeException e) {
            // Android refused a foreground service; playback continues as long as the app is alive
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        PlayerActivity.onPipCommand(PipActionReceiver.CMD_CLOSE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        synchronized (PlaybackService.class) {
            sRunning = false;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static Notification build(Context c, State s) {
        Notification.Builder b = newBuilder(c);
        Intent open = new Intent(c, PlayerActivity.class).setAction(ACTION_SHOW_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(c, 100, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(s.title != null ? s.title : c.getString(R.string.app_name))
                .setContentText(c.getString(s.playing ? R.string.notif_playing : R.string.notif_paused))
                .setContentIntent(content)
                .setDeleteIntent(PipActionReceiver.pending(c, PipActionReceiver.CMD_CLOSE))
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setColor(c.getColor(R.color.accent));
        if (s.art != null) b.setLargeIcon(s.art);
        b.addAction(action(c, R.drawable.ic_fast_rewind, R.string.player_rewind, PipActionReceiver.CMD_REWIND));
        b.addAction(action(c, s.playing ? R.drawable.ic_pause : R.drawable.ic_play,
                s.playing ? R.string.player_pause : R.string.player_play, PipActionReceiver.CMD_TOGGLE));
        b.addAction(action(c, R.drawable.ic_fast_forward, R.string.player_forward, PipActionReceiver.CMD_FORWARD));
        b.addAction(action(c, R.drawable.ic_close, R.string.notif_close, PipActionReceiver.CMD_CLOSE));
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (s.token != null) style.setMediaSession(s.token);
        b.setStyle(style);
        return b.build();
    }

    private static Notification.Action action(Context c, int icon, int title, int cmd) {
        return new Notification.Action.Builder(Icon.createWithResource(c, icon), c.getString(title),
                PipActionReceiver.pending(c, cmd)).build();
    }

    /** Notification channels exist from Android 8 (API 26); created by reflection because the app builds against API 23. */
    private static Notification.Builder newBuilder(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
                Class<?> chCls = Class.forName("android.app.NotificationChannel");
                Object ch = chCls.getConstructor(String.class, CharSequence.class, int.class)
                        .newInstance(CHANNEL_ID, c.getString(R.string.notif_channel), 2 /* IMPORTANCE_LOW */);
                chCls.getMethod("setShowBadge", boolean.class).invoke(ch, false);
                NotificationManager.class.getMethod("createNotificationChannel", chCls).invoke(nm, ch);
                return Notification.Builder.class
                        .getConstructor(Context.class, String.class).newInstance(c, CHANNEL_ID);
            } catch (Throwable t) {
                // fall back to the classic builder
            }
        }
        return new Notification.Builder(c).setPriority(Notification.PRIORITY_LOW);
    }
}
