package com.ahmed.noorplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaFormat;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.PlaybackParams;
import android.media.TimedText;
import android.media.VolumeProvider;
import android.media.audiofx.LoudnessEnhancer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ahmed.noorplayer.core.GestureGate;
import com.ahmed.noorplayer.core.GestureMath;
import com.ahmed.noorplayer.core.HistoryEntry;
import com.ahmed.noorplayer.core.PlayItem;
import com.ahmed.noorplayer.core.Playlist;
import com.ahmed.noorplayer.core.ResumePolicy;
import com.ahmed.noorplayer.core.SizeFormat;
import com.ahmed.noorplayer.core.SubtitleParser;
import com.ahmed.noorplayer.core.SubtitleTrack;
import com.ahmed.noorplayer.core.TextDecoder;
import com.ahmed.noorplayer.core.TimeFormat;
import com.ahmed.noorplayer.core.UrlCheck;
import com.ahmed.noorplayer.core.VideoFit;
import com.ahmed.noorplayer.core.VideoItem;
import com.ahmed.noorplayer.core.VolumeChord;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Full-screen video player with gestures, subtitles, tracks, speed, zoom, lock and picture-in-picture. */
public class PlayerActivity extends Activity implements TextureView.SurfaceTextureListener {
    static final String EXTRA_TOKEN = "noor.token";
    static final String EXTRA_INDEX = "noor.index";
    static final String EXTRA_TITLE = "noor.title";
    static final String EXTRA_FROM_START = "noor.from_start";
    static final String EXTRA_MEDIA_ID = "noor.media_id";
    static final String EXTRA_PATH = "noor.path";

    private static final int REQ_SUBTITLE = 11;
    private static final int REQ_WRITE = 12;

    private static final int G_NONE = 0, G_SEEK = 1, G_BRIGHTNESS = 2, G_VOLUME = 3, G_ZOOM = 4, G_IGNORE = 5;
    private static final int ORIENT_AUTO = 0, ORIENT_LANDSCAPE = 1, ORIENT_PORTRAIT = 2, ORIENT_SENSOR = 3;

    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f};
    private static final int[] SUB_SIZES_SP = {16, 20, 25, 31};
    private static final int[] BOOST_MB = {0, 400, 800, 1200};
    private static final int[] SLEEP_MINUTES = {15, 30, 45, 60, 90};
    private static final long HIDE_DELAY_MS = 3500;
    private static final long MAX_SUBTITLE_BYTES = 8L * 1024 * 1024;

    private static WeakReference<PlayerActivity> sActive = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final PlayerListener listener = new PlayerListener();

    private Prefs prefs;
    private AudioManager audio;
    private MediaSession session;

    // views
    private TextureView videoView;
    private View gestureLayer, controls, centerControls, bottomBar, centerInfo, buffering, resumeBanner, errorView;
    private TextView subtitleView, titleView, positionView, durationView, speedBadge, centerInfoText, resumeText,
            tipView, errorText, rewindLabel, forwardLabel;
    private ImageView centerInfoIcon;
    private ProgressBar centerInfoBar;
    private SeekBar seekBar;
    private ImageButton playPauseBtn, prevBtn, nextBtn, lockBtn, unlockBtn, pipBtn;
    private Button errorNext;

    // playback
    private Playlist playlist;
    private MediaPlayer player;
    private Surface surface;
    private boolean prepared, completed, playWhenReady = true, fromStartOnce;
    private long durationMs, lastKnownPosition, startPositionMs = -1;
    private int videoW, videoH, serverDiedRetries;
    private String openedUri;
    private boolean seekInFlight, pendingSeekPrecise;
    private long pendingSeekMs = -1, lastSeekTarget, seekStartedAt, lastScrubSeek = -1;
    private float speed = 1f, appliedSpeed = 1f;
    private int boostIndex;
    private LoudnessEnhancer enhancer;
    private int repeatMode = Playlist.REPEAT_OFF;
    private final List<Integer> audioTracks = new ArrayList<>();
    private final List<Integer> textTracks = new ArrayList<>();
    private MediaPlayer.TrackInfo[] trackInfos;
    private int wantedAudioTrack = -1, wantedTextTrack = -1, selectedTextTrack = -1;

    // subtitles
    private SubtitleTrack externalSubs;
    private long subDelayMs;
    private String lastSubText;

    // ui state
    private boolean controlsVisible, locked, inPip, stopped = true, userSeeking, showRemaining, launchingInternal,
            errorVisible, pipSupported, noisyRegistered, hasAudioFocus, pausedByFocusLoss, ducked, sleepAtEnd;
    private int aspectMode = VideoFit.FIT;
    private float zoom = 1f, panX, panY;
    private int orientationMode = ORIENT_AUTO;
    private int sleepMinutes;
    private Bitmap pendingShot;

    // background playback and volume-key pause
    private boolean backgroundMode;
    private Bitmap notificationArt;
    private final VolumeChord chord = new VolumeChord();
    private VolumeProvider volumeProvider;
    private long lastAutoSave;
    private int midPlayRecoveries;
    /**
     * Paused with the two volume keys while in the background. Android only sends volume keys to an app
     * whose media session says it is playing, so the session keeps saying "playing" (at speed 0) until
     * the next key chord, another app taking the sound, returning to the app, or 20 minutes.
     */
    private boolean chordPaused;
    private static final long CHORD_HOLD_MS = 20 * 60_000L;
    private final Runnable releaseChordHoldRunnable = new Runnable() {
        @Override
        public void run() {
            releaseChordHold();
        }
    };

    // gestures
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleDetector;
    private int gestureMode = G_NONE, touchSlop, edgeIgnorePx, lastVolumeIndex = -1;
    private long gestureStartPos, gestureSeekTarget;
    private float gestureStartBrightness, gestureStartVolume, lastFocusX, lastFocusY;

    /** Sideways seeking only starts after the finger has been held still this long (avoids accidental seeks). */
    private static final long SEEK_HOLD_MS = 1000;
    private boolean seekArmed, suppressNextTap;
    private ImageButton autoNextBtn;
    private final Runnable armSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (gestureMode != G_NONE || locked || inPip || player == null || !prepared || durationMs <= 0) return;
            seekArmed = true;
            suppressNextTap = true;
            gestureLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showCenterInfo(R.drawable.ic_touch_hold, getString(R.string.hold_seek_ready), -1);
        }
    };

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setContentView(R.layout.activity_player);
        bindViews();
        setupControls();
        setupGestures();
        videoView.setSurfaceTextureListener(this);
        pipSupported = Compat.supportsPip(this);
        pipBtn.setVisibility(pipSupported ? View.VISIBLE : View.GONE);
        setupSession();
        sActive = new WeakReference<>(this);
        orientationMode = prefs.autoRotate() ? ORIENT_AUTO : ORIENT_SENSOR;
        if (orientationMode == ORIENT_SENSOR) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        if (savedInstanceState != null) playWhenReady = false;
        if (PlaybackService.ACTION_SHOW_PLAYER.equals(getIntent().getAction())) {
            // the notification was tapped but the player no longer exists
            PlaybackService.stop(this);
            Ui.safeStart(this, new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (!loadFromIntent(getIntent())) {
            Ui.toast(this, R.string.nothing_to_play);
            finish();
            return;
        }
        updateSeekLabels();
        applySubtitleSize();
        hideSystemUi();
        showControls();
        maybeShowTip();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (PlaybackService.ACTION_SHOW_PLAYER.equals(intent.getAction())) {
            // back from the notification: keep playing exactly where we are
            showControls();
            return;
        }
        Playlist previous = playlist;
        savePosition(false);
        if (!loadFromIntent(intent)) {
            playlist = previous;
            return;
        }
        setIntent(intent);
        PlayItem now = playlist.current();
        if (player != null && prepared && now != null && now.uri.equals(openedUri)) {
            // the same video is already open (e.g. playing in the background): just continue
            if (fromStartOnce) seekToInternal(0, true);
            fromStartOnce = false;
            updatePrevNext();
            play();
            showControls();
            return;
        }
        playWhenReady = true;
        startPositionMs = -1;
        if (stopped) releasePlayer();
        else openCurrent();
        showControls();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopped = false;
        registerNoisy();
        setSessionActive(true);
        if (player == null && playlist != null && playlist.current() != null && !isFinishing()) openCurrent();
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        launchingInternal = false;
        exitBackgroundMode();
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing() && !launchingInternal && prefs.backgroundPlay() && isPlaying()) {
            enterBackgroundMode();
        } else if (!isInPipNow()) {
            savePosition(false, true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopped = true;
        if (backgroundMode) {
            savePosition(false, true);
        } else if (!isInPipNow()) {
            stopPlaybackForBackground();
        }
    }

    /** Leaving the screen while playing: keep the sound going under a foreground service and notification. */
    private void enterBackgroundMode() {
        if (backgroundMode) return;
        backgroundMode = true;
        savePosition(false, true);
        notificationArt = captureFrame(320);
        setSessionActive(true);
        updateSessionMetadata(playlist.current());
        useRemoteVolume(true);
        try {
            PlaybackService.start(this, notificationState());
        } catch (RuntimeException e) {
            // Android refused the service; the sound keeps playing while the app stays alive
        }
    }

    private void exitBackgroundMode() {
        releaseChordHold();
        if (!backgroundMode) return;
        backgroundMode = false;
        PlaybackService.stop(this);
        useRemoteVolume(false);
    }

    private PlaybackService.State notificationState() {
        PlaybackService.State st = new PlaybackService.State();
        PlayItem item = playlist != null ? playlist.current() : null;
        st.title = item != null ? item.title : null;
        st.playing = isPlaying();
        st.token = session != null ? session.getSessionToken() : null;
        st.art = notificationArt;
        return st;
    }

    private Bitmap captureFrame(int maxWidth) {
        if (videoW <= 0 || videoH <= 0 || !videoView.isAvailable()) return null;
        try {
            float scale = Math.min(1f, maxWidth / (float) Math.max(videoW, videoH));
            return videoView.getBitmap(Math.max(1, Math.round(videoW * scale)), Math.max(1, Math.round(videoH * scale)));
        } catch (Throwable t) {
            return null;
        }
    }

    /** "Close" on the notification (or the app being swiped away while playing in the background). */
    private void closeFromNotification() {
        if (player != null && prepared) savePosition(false, true);
        playWhenReady = false;
        exitBackgroundMode();
        releasePlayer();
        abandonAudioFocus();
        setSessionActive(false);
        updatePlayPause();
        if (stopped || inPip) finish();
    }

    /** Called when the player is no longer visible: remember where we were and free the decoder. */
    private void stopPlaybackForBackground() {
        exitBackgroundMode();
        unregisterNoisy();
        handler.removeCallbacks(progressRunnable);
        handler.removeCallbacks(sleepRunnable);
        sleepMinutes = 0;
        sleepAtEnd = false;
        if (player != null && prepared) {
            startPositionMs = safePosition();
            savePosition(false, true);
        }
        playWhenReady = false;
        releasePlayer();
        abandonAudioFocus();
        setSessionActive(false);
        updatePlayPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null && prepared) savePosition(false, true);
        exitBackgroundMode();
        releasePlayer();
        abandonAudioFocus();
        unregisterNoisy();
        if (session != null) {
            try {
                session.release();
            } catch (RuntimeException ignored) {
                // already released
            }
        }
        io.shutdownNow();
        if (keyWakeLock != null) {
            try {
                if (keyWakeLock.isHeld()) keyWakeLock.release();
            } catch (RuntimeException ignored) {
                // already released
            }
        }
        if (sActive.get() == this) sActive = new WeakReference<>(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUi();
        videoView.post(new Runnable() {
            @Override
            public void run() {
                applyTransform();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (locked) {
            showUnlockButton();
            Ui.toast(this, R.string.locked_hint);
            return;
        }
        savePosition(false, true);
        finish();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!launchingInternal && pipSupported && prefs.autoPip() && isPlaying()) enterPip();
    }

    /** Called by Android when the floating window opens or closes (API 24+; the newer variant forwards here). */
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        inPip = isInPictureInPictureMode;
        if (inPip) {
            hideControlsNow();
            centerInfo.setVisibility(View.GONE);
            resumeBanner.setVisibility(View.GONE);
            tipView.setVisibility(View.GONE);
            unlockBtn.setVisibility(View.GONE);
            applySubtitleSize();
        } else {
            applySubtitleSize();
            // If the floating window was closed (not expanded), the activity stays stopped.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (stopped && !inPip && !isFinishing()) {
                        stopPlaybackForBackground();
                        finish();
                    } else if (!stopped) {
                        showControls();
                    }
                }
            }, 500);
        }
    }

    private boolean isInPipNow() {
        return inPip || Compat.isInPip(this);
    }

    /** Buttons on the floating window and the notification. Returns false if no player is open. */
    static boolean onPipCommand(int cmd) {
        PlayerActivity a = sActive.get();
        if (a == null || a.isFinishing() || a.isDestroyed()) return false;
        long step = a.prefs.seekStepSec() * 1000L;
        if (cmd == PipActionReceiver.CMD_REWIND) a.seekBy(-step);
        else if (cmd == PipActionReceiver.CMD_TOGGLE) a.togglePlay();
        else if (cmd == PipActionReceiver.CMD_FORWARD) a.seekBy(step);
        else if (cmd == PipActionReceiver.CMD_CLOSE) a.closeFromNotification();
        return true;
    }

    // ================================================================ intent

    private boolean loadFromIntent(Intent intent) {
        if (intent == null) return false;
        Uri data = intent.getData();
        if (data == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) {
                data = (Uri) stream;
            } else {
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                String url = text != null ? UrlCheck.normalize(text.toString()) : null;
                if (url != null && UrlCheck.looksLikeUrl(url)) data = Uri.parse(url);
            }
        }
        if (data == null) return false;
        String uri = data.toString();
        List<PlayItem> list = PlaylistHolder.take(intent.getLongExtra(EXTRA_TOKEN, 0));
        int index = intent.getIntExtra(EXTRA_INDEX, 0);
        Playlist next;
        if (list != null && index >= 0 && index < list.size() && uri.equals(list.get(index).uri)) {
            next = new Playlist(list, index);
        } else {
            String title = intent.getStringExtra(EXTRA_TITLE);
            if (title == null || title.isEmpty()) title = resolveTitle(data);
            next = Playlist.single(new PlayItem(uri, title, intent.getLongExtra(EXTRA_MEDIA_ID, -1),
                    intent.getStringExtra(EXTRA_PATH), intent.getType()));
        }
        if (next.current() == null) return false;
        playlist = next;
        fromStartOnce = intent.getBooleanExtra(EXTRA_FROM_START, false);
        return true;
    }

    private String resolveTitle(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("content")) {
            Cursor c = null;
            try {
                c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (c != null && c.moveToFirst()) {
                    String n = c.getString(0);
                    if (n != null && !n.isEmpty()) return VideoItem.stripExtension(n);
                }
            } catch (RuntimeException ignored) {
                // fall back below
            } finally {
                if (c != null) c.close();
            }
        }
        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("rtsp")) {
            return UrlCheck.titleFromUrl(uri.toString());
        }
        String last = uri.getLastPathSegment();
        return last != null && !last.isEmpty() ? VideoItem.stripExtension(last) : uri.toString();
    }

    // ================================================================ views

    private void bindViews() {
        videoView = (TextureView) findViewById(R.id.video_view);
        gestureLayer = findViewById(R.id.gesture_layer);
        controls = findViewById(R.id.controls);
        centerControls = findViewById(R.id.center_controls);
        bottomBar = findViewById(R.id.bottom_bar);
        subtitleView = (TextView) findViewById(R.id.subtitle_text);
        titleView = (TextView) findViewById(R.id.title_text);
        positionView = (TextView) findViewById(R.id.position_text);
        durationView = (TextView) findViewById(R.id.duration_text);
        speedBadge = (TextView) findViewById(R.id.speed_badge);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        playPauseBtn = (ImageButton) findViewById(R.id.play_pause_btn);
        prevBtn = (ImageButton) findViewById(R.id.prev_btn);
        nextBtn = (ImageButton) findViewById(R.id.next_btn);
        lockBtn = (ImageButton) findViewById(R.id.lock_btn);
        unlockBtn = (ImageButton) findViewById(R.id.unlock_btn);
        pipBtn = (ImageButton) findViewById(R.id.pip_btn);
        autoNextBtn = (ImageButton) findViewById(R.id.autonext_btn);
        rewindLabel = (TextView) findViewById(R.id.rewind_label);
        forwardLabel = (TextView) findViewById(R.id.forward_label);
        centerInfo = findViewById(R.id.center_info);
        centerInfoIcon = (ImageView) findViewById(R.id.center_info_icon);
        centerInfoText = (TextView) findViewById(R.id.center_info_text);
        centerInfoBar = (ProgressBar) findViewById(R.id.center_info_bar);
        buffering = findViewById(R.id.buffering);
        resumeBanner = findViewById(R.id.resume_banner);
        resumeText = (TextView) findViewById(R.id.resume_text);
        tipView = (TextView) findViewById(R.id.tip_text);
        errorView = findViewById(R.id.error_view);
        errorText = (TextView) findViewById(R.id.error_text);
        errorNext = (Button) findViewById(R.id.error_next);
        centerInfoText.setGravity(Gravity.CENTER);
        controls.setVisibility(View.GONE);
        videoView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) applyTransform();
            }
        });
    }

    private void setupControls() {
        click(R.id.back_btn, new Runnable() { public void run() { onBackPressed(); } });
        click(R.id.play_pause_btn, new Runnable() { public void run() { togglePlay(); } });
        click(R.id.prev_btn, new Runnable() { public void run() { previous(); } });
        click(R.id.next_btn, new Runnable() { public void run() { next(); } });
        click(R.id.rewind_btn, new Runnable() { public void run() { seekBy(-prefs.seekStepSec() * 1000L); } });
        click(R.id.forward_btn, new Runnable() { public void run() { seekBy(prefs.seekStepSec() * 1000L); } });
        click(R.id.lock_btn, new Runnable() { public void run() { setLocked(true); } });
        click(R.id.unlock_btn, new Runnable() { public void run() { setLocked(false); } });
        click(R.id.aspect_btn, new Runnable() { public void run() { cycleAspect(); } });
        click(R.id.pip_btn, new Runnable() { public void run() { enterPip(); } });
        click(R.id.shot_btn, new Runnable() { public void run() { takeScreenshot(); } });
        click(R.id.autonext_btn, new Runnable() { public void run() { toggleAutoNext(); } });
        click(R.id.audio_btn, new Runnable() { public void run() { showAudioDialog(); } });
        click(R.id.subs_btn, new Runnable() { public void run() { showSubtitleDialog(); } });
        click(R.id.speed_btn, new Runnable() { public void run() { showSpeedDialog(); } });
        click(R.id.error_open_with, new Runnable() { public void run() { openWithOtherApp(); } });
        click(R.id.error_next, new Runnable() { public void run() { next(); } });
        click(R.id.error_close, new Runnable() { public void run() { finish(); } });
        click(R.id.resume_start_over, new Runnable() {
            public void run() {
                resumeBanner.setVisibility(View.GONE);
                seekToInternal(0, true);
            }
        });
        click(R.id.duration_text, new Runnable() {
            public void run() {
                showRemaining = !showRemaining;
                updateProgress();
            }
        });
        click(R.id.tip_text, new Runnable() { public void run() { tipView.setVisibility(View.GONE); } });
        findViewById(R.id.rotate_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRotationMenu(v);
                scheduleHide();
            }
        });
        findViewById(R.id.more_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu(v);
                scheduleHide();
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                positionView.setText(TimeFormat.format(progress));
                if (lastScrubSeek < 0 || Math.abs(progress - lastScrubSeek) >= 1000) {
                    lastScrubSeek = progress;
                    seekToInternal(progress, false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userSeeking = true;
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                lastScrubSeek = -1;
                seekToInternal(sb.getProgress(), true);
                scheduleHide();
            }
        });
    }

    private void click(int id, final Runnable action) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
                scheduleHide();
            }
        });
    }

    // ================================================================ player

    private final class PlayerListener implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
            MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnVideoSizeChangedListener,
            MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnTimedTextListener {

        @Override
        public void onPrepared(MediaPlayer mp) {
            if (mp == player) handlePrepared();
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            if (mp == player && prepared) handleCompletion();
        }

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            if (mp == player) handleError(what, extra);
            return true;
        }

        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            if (mp != player) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) showBuffering(true);
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                showBuffering(false);
            }
            return false;
        }

        @Override
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            if (mp != player || width <= 0 || height <= 0) return;
            boolean first = videoW <= 0;
            videoW = width;
            videoH = height;
            applyTransform();
            if (first) applyAutoOrientation();
            updatePipParams();
        }

        @Override
        public void onSeekComplete(MediaPlayer mp) {
            if (mp != player) return;
            seekInFlight = false;
            if (pendingSeekMs >= 0) {
                long t = pendingSeekMs;
                pendingSeekMs = -1;
                seekToInternal(t, pendingSeekPrecise);
            } else {
                updateProgress();
                updateSessionState();
            }
        }

        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            if (mp != player || durationMs <= 0) return;
            PlayItem item = playlist.current();
            if (item != null && item.isNetwork()) {
                seekBar.setSecondaryProgress((int) Math.min(Integer.MAX_VALUE, durationMs * Math.max(0, Math.min(100, percent)) / 100));
            }
        }

        @Override
        public void onTimedText(MediaPlayer mp, TimedText text) {
            if (mp != player || externalSubs != null || selectedTextTrack < 0) return;
            showSubtitle(text != null ? text.getText() : null);
        }
    }

    private void openCurrent() {
        releasePlayer();
        hideError();
        PlayItem item = playlist.current();
        if (item == null) {
            finish();
            return;
        }
        boolean sameItem = item.uri.equals(openedUri);
        openedUri = item.uri;
        if (!sameItem) {
            resetSubtitles();
            wantedAudioTrack = -1;
            wantedTextTrack = -1;
            serverDiedRetries = 0;
            midPlayRecoveries = 0;
            zoom = 1f;
            panX = 0;
            panY = 0;
            videoW = 0;
            videoH = 0;
            resumeBanner.setVisibility(View.GONE);
        }
        titleView.setText(item.title);
        prepared = false;
        completed = false;
        durationMs = 0;
        lastKnownPosition = startPositionMs > 0 ? startPositionMs : 0;
        lastSeekTarget = lastKnownPosition;
        seekInFlight = false;
        pendingSeekMs = -1;
        lastScrubSeek = -1;
        appliedSpeed = 1f;
        selectedTextTrack = -1;
        audioTracks.clear();
        textTracks.clear();
        trackInfos = null;
        seekBar.setProgress(0);
        seekBar.setSecondaryProgress(0);
        positionView.setText(TimeFormat.format(startPositionMs > 0 ? startPositionMs : 0));
        durationView.setText(TimeFormat.format(0));
        updatePrevNext();
        updatePlayPause();
        showBuffering(true);
        applyTransform();

        MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setOnPreparedListener(listener);
        mp.setOnCompletionListener(listener);
        mp.setOnErrorListener(listener);
        mp.setOnInfoListener(listener);
        mp.setOnVideoSizeChangedListener(listener);
        mp.setOnSeekCompleteListener(listener);
        mp.setOnBufferingUpdateListener(listener);
        mp.setOnTimedTextListener(listener);
        try {
            mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        } catch (RuntimeException ignored) {
            // without it playback still works while the screen is on
        }
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
        } catch (RuntimeException ignored) {
            // default attributes are fine
        }
        try {
            mp.setDataSource(this, Uri.parse(item.uri));
            if (surface != null) mp.setSurface(surface);
            mp.prepareAsync();
        } catch (SecurityException e) {
            showError(getString(R.string.err_permission));
            return;
        } catch (FileNotFoundException e) {
            showError(getString(R.string.err_not_found));
            return;
        } catch (IOException e) {
            showError(getString(R.string.err_io));
            return;
        } catch (RuntimeException e) {
            showError(getString(R.string.cannot_open));
            return;
        }
        if (!sameItem) tryAutoSubtitles(item);
        updateSessionMetadata(item);
    }

    private void handlePrepared() {
        prepared = true;
        serverDiedRetries = 0;
        durationMs = safeDuration();
        boolean seekable = durationMs > 0;
        seekBar.setMax(seekable ? (int) Math.min(Integer.MAX_VALUE, durationMs) : 0);
        seekBar.setEnabled(seekable);
        durationView.setText(seekable ? TimeFormat.format(durationMs) : getString(R.string.live));

        PlayItem item = playlist.current();
        long start = 0;
        if (startPositionMs >= 0) {
            start = startPositionMs;
            startPositionMs = -1;
        } else if (!fromStartOnce && prefs.resume() && item != null) {
            HistoryEntry h = prefs.findHistory(item.uri);
            if (h == null) h = prefs.findSimilarHistory(item.title, durationMs);
            if (h != null) {
                start = ResumePolicy.resumePosition(h.positionMs, durationMs, h.finished);
                if (start > 0) showResumeBanner(start);
            }
        }
        fromStartOnce = false;
        if (start > 0 && seekable) seekToInternal(Math.min(start, durationMs), true);

        if (videoW <= 0 || videoH <= 0) {
            try {
                videoW = player.getVideoWidth();
                videoH = player.getVideoHeight();
            } catch (RuntimeException ignored) {
                // size arrives later via onVideoSizeChanged
            }
            applyAutoOrientation();
        }
        applyTransform();
        readTracks();
        restoreTrackChoices();
        applyBoost(false);
        showBuffering(false);
        if (playWhenReady) {
            play();
        } else {
            updatePlayPause();
            showControls();
        }
        updateProgress();
        updateSessionMetadata(item);
        updateSessionState();
        updatePipParams();
    }

    private void handleCompletion() {
        completed = true;
        savePosition(true);
        if (repeatMode == Playlist.REPEAT_ONE) {
            completed = false;
            seekToInternal(0, true);
            play();
            return;
        }
        if (sleepAtEnd) {
            sleepAtEnd = false;
            playWhenReady = false;
            updatePlayPause();
            keepScreenOn(false);
            showControls();
            Ui.toast(this, R.string.sleep_done);
            return;
        }
        if ((prefs.autoplayNext() || repeatMode == Playlist.REPEAT_ALL) && playlist.hasNext(repeatMode)) {
            playlist.moveNext(repeatMode);
            PlayItem n = playlist.current();
            if (n != null) Ui.toast(this, getString(R.string.next_playing, n.title));
            playWhenReady = true;
            startPositionMs = -1;
            fromStartOnce = false;
            openCurrent();
            return;
        }
        playWhenReady = false;
        updatePlayPause();
        keepScreenOn(false);
        showControls();
        updateSessionState();
        updatePipParams();
    }

    private void handleError(int what, int extra) {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED && serverDiedRetries < 1) {
            serverDiedRetries++;
            startPositionMs = lastKnownPosition;
            openCurrent();
            return;
        }
        // A video that was already playing failed (for example the phone dropped the picture while the
        // app was in the background): reopen it at the same second instead of stopping.
        if (prepared && midPlayRecoveries < 2) {
            midPlayRecoveries++;
            startPositionMs = safePosition();
            openCurrent();
            return;
        }
        String msg;
        if (extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED || extra == MediaPlayer.MEDIA_ERROR_MALFORMED
                || (what == MediaPlayer.MEDIA_ERROR_UNKNOWN && extra == Integer.MIN_VALUE)) {
            msg = getString(R.string.err_unsupported);
        } else if (extra == MediaPlayer.MEDIA_ERROR_IO) {
            msg = getString(R.string.err_io);
        } else if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
            msg = getString(R.string.err_timeout);
        } else {
            msg = getString(R.string.err_generic, what, extra);
        }
        showError(msg);
    }

    private void releasePlayer() {
        if (enhancer != null) {
            try {
                enhancer.release();
            } catch (RuntimeException ignored) {
                // already gone
            }
            enhancer = null;
        }
        MediaPlayer mp = player;
        player = null;
        prepared = false;
        seekInFlight = false;
        pendingSeekMs = -1;
        if (mp != null) {
            try {
                mp.release();
            } catch (RuntimeException ignored) {
                // nothing else to free
            }
        }
        keepScreenOn(false);
    }

    private boolean isPlaying() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private long safeDuration() {
        if (player == null) return 0;
        try {
            int d = player.getDuration();
            return d > 0 ? d : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private long safePosition() {
        if (seekInFlight || pendingSeekMs >= 0) return lastSeekTarget;
        if (player == null || !prepared) return lastKnownPosition;
        try {
            long p = player.getCurrentPosition();
            if (p >= 0) lastKnownPosition = p;
        } catch (IllegalStateException ignored) {
            // keep the last known value
        }
        return lastKnownPosition;
    }

    private void play() {
        playWhenReady = true;
        chordPaused = false;
        handler.removeCallbacks(releaseChordHoldRunnable);
        if (player == null || !prepared) return;
        requestAudioFocus();
        try {
            player.start();
        } catch (IllegalStateException e) {
            return;
        }
        completed = false;
        pausedByFocusLoss = false;
        applySpeedIfNeeded();
        updatePlayPause();
        keepScreenOn(true);
        scheduleHide();
        updateSessionState();
        updatePipParams();
    }

    private void pausePlayback(boolean byUser) {
        playWhenReady = false;
        chordPaused = false;
        handler.removeCallbacks(releaseChordHoldRunnable);
        if (byUser) pausedByFocusLoss = false;
        if (player != null && prepared) {
            try {
                if (player.isPlaying()) player.pause();
            } catch (IllegalStateException ignored) {
                // not playing
            }
        }
        updatePlayPause();
        keepScreenOn(false);
        savePosition(false);
        updateSessionState();
        updatePipParams();
        if (!inPip) showControls();
    }

    private void togglePlay() {
        if (isPlaying()) pausePlayback(true);
        else play();
    }

    private void seekToInternal(long ms, boolean precise) {
        long target = Math.max(0, ms);
        if (durationMs > 0) target = Math.min(target, durationMs);
        lastSeekTarget = target;
        if (player == null || !prepared) {
            startPositionMs = target;
            return;
        }
        if (seekInFlight && SystemClock.uptimeMillis() - seekStartedAt < 1500) {
            pendingSeekMs = target;
            pendingSeekPrecise = precise;
            return;
        }
        seekInFlight = true;
        seekStartedAt = SystemClock.uptimeMillis();
        try {
            Compat.seekTo(player, target, precise);
            if (completed && target < durationMs) completed = false;
        } catch (IllegalStateException e) {
            seekInFlight = false;
        }
        updateProgress();
        lastSubText = null;
        updateExternalSubtitle();
    }

    private void seekBy(long deltaMs) {
        if (player == null || !prepared || durationMs <= 0) return;
        long target = Math.max(0, Math.min(durationMs, safePosition() + deltaMs));
        seekToInternal(target, true);
        if (!inPip) {
            int seconds = (int) Math.abs(deltaMs / 1000);
            showCenterInfo(deltaMs < 0 ? R.drawable.ic_fast_rewind : R.drawable.ic_fast_forward,
                    (deltaMs < 0 ? "-" : "+") + seconds + "s", -1);
            hideCenterInfoLater(650);
        }
    }

    private void next() {
        if (!playlist.hasNext(repeatMode)) return;
        savePosition(false);
        playlist.moveNext(repeatMode);
        playWhenReady = true;
        startPositionMs = -1;
        fromStartOnce = false;
        openCurrent();
        showControls();
    }

    private void previous() {
        if ((prepared && safePosition() > 5000) || !playlist.hasPrevious()) {
            seekToInternal(0, true);
            return;
        }
        savePosition(false);
        playlist.movePrevious();
        playWhenReady = true;
        startPositionMs = -1;
        fromStartOnce = false;
        openCurrent();
        showControls();
    }

    private void savePosition(boolean finished) {
        savePosition(finished, false);
    }

    /** sync = write to storage immediately (when leaving), so the position survives the app being killed. */
    private void savePosition(boolean finished, boolean sync) {
        PlayItem item = playlist != null ? playlist.current() : null;
        if (item == null || player == null || !prepared) return;
        long pos = finished ? durationMs : safePosition();
        prefs.savePlayback(item.uri, item.title, item.mediaId, pos, durationMs, finished, sync);
        lastAutoSave = SystemClock.uptimeMillis();
    }

    private void applySpeedIfNeeded() {
        if (player == null || !prepared || Math.abs(appliedSpeed - speed) < 0.001f) return;
        try {
            PlaybackParams p = player.getPlaybackParams();
            p.setSpeed(speed);
            player.setPlaybackParams(p);
            appliedSpeed = speed;
        } catch (RuntimeException e) {
            try {
                player.setPlaybackParams(new PlaybackParams().setSpeed(speed));
                appliedSpeed = speed;
            } catch (RuntimeException e2) {
                speed = appliedSpeed;
                updateSpeedBadge();
            }
        }
    }

    private void applyBoost(boolean announce) {
        int gain = BOOST_MB[Math.max(0, Math.min(BOOST_MB.length - 1, boostIndex))];
        if (gain == 0) {
            if (enhancer != null) {
                try {
                    enhancer.setEnabled(false);
                } catch (RuntimeException ignored) {
                    // released
                }
            }
            return;
        }
        if (player == null || !prepared) return;
        try {
            if (enhancer == null) enhancer = new LoudnessEnhancer(player.getAudioSessionId());
            enhancer.setTargetGain(gain);
            enhancer.setEnabled(true);
        } catch (Throwable t) {
            enhancer = null;
            boostIndex = 0;
            if (announce) Ui.toast(this, R.string.boost_unsupported);
        }
    }

    private void keepScreenOn(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ================================================================ tracks

    private void readTracks() {
        audioTracks.clear();
        textTracks.clear();
        try {
            trackInfos = player.getTrackInfo();
        } catch (RuntimeException e) {
            trackInfos = null;
        }
        if (trackInfos == null) return;
        for (int i = 0; i < trackInfos.length; i++) {
            if (trackInfos[i] == null) continue;
            int type;
            try {
                type = trackInfos[i].getTrackType();
            } catch (RuntimeException e) {
                continue;
            }
            if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) audioTracks.add(i);
            else if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) textTracks.add(i);
        }
    }

    private void restoreTrackChoices() {
        if (player == null) return;
        if (wantedAudioTrack >= 0 && audioTracks.contains(wantedAudioTrack)) {
            try {
                player.selectTrack(wantedAudioTrack);
            } catch (RuntimeException ignored) {
                // keep the default track
            }
        }
        if (externalSubs == null && wantedTextTrack >= 0 && textTracks.contains(wantedTextTrack)) {
            try {
                player.selectTrack(wantedTextTrack);
                selectedTextTrack = wantedTextTrack;
            } catch (RuntimeException ignored) {
                selectedTextTrack = -1;
            }
        }
    }

    private String trackLabel(int position, int trackIndex, int nameRes) {
        StringBuilder sb = new StringBuilder(getString(nameRes, String.valueOf(position + 1)));
        if (trackInfos != null && trackIndex >= 0 && trackIndex < trackInfos.length && trackInfos[trackIndex] != null) {
            String lang = languageName(trackInfos[trackIndex]);
            if (!lang.isEmpty()) sb.append(" · ").append(lang);
            try {
                MediaFormat f = trackInfos[trackIndex].getFormat();
                if (f != null && f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    int ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    sb.append(" · ").append(ch == 1 ? "mono" : ch == 2 ? "stereo" : ch == 6 ? "5.1" : ch == 8 ? "7.1" : ch + "ch");
                }
            } catch (RuntimeException ignored) {
                // format details are optional
            }
        }
        return sb.toString();
    }

    private static String languageName(MediaPlayer.TrackInfo info) {
        String lang;
        try {
            lang = info.getLanguage();
        } catch (RuntimeException e) {
            return "";
        }
        if (lang == null || lang.isEmpty() || lang.equalsIgnoreCase("und")) return "";
        String code = lang.toLowerCase(Locale.ROOT);
        String[][] map = {{"ara", "ar"}, {"eng", "en"}, {"fre", "fr"}, {"fra", "fr"}, {"ger", "de"}, {"deu", "de"},
                {"spa", "es"}, {"ita", "it"}, {"jpn", "ja"}, {"kor", "ko"}, {"chi", "zh"}, {"zho", "zh"},
                {"rus", "ru"}, {"por", "pt"}, {"hin", "hi"}, {"urd", "ur"}, {"tur", "tr"}, {"per", "fa"},
                {"fas", "fa"}, {"ind", "id"}, {"may", "ms"}, {"msa", "ms"}, {"ben", "bn"}, {"tha", "th"},
                {"vie", "vi"}, {"dut", "nl"}, {"nld", "nl"}, {"swe", "sv"}, {"pol", "pl"}, {"heb", "he"}};
        for (String[] m : map) {
            if (m[0].equals(code)) {
                code = m[1];
                break;
            }
        }
        String name = new Locale(code).getDisplayLanguage();
        return name == null || name.isEmpty() ? lang : name;
    }

    private void showAudioDialog() {
        if (player == null || !prepared) return;
        if (audioTracks.isEmpty()) {
            Ui.toast(this, R.string.audio_none);
            return;
        }
        if (audioTracks.size() == 1) {
            Ui.toast(this, R.string.audio_single);
            return;
        }
        int selected = -1;
        try {
            selected = player.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        } catch (RuntimeException ignored) {
            // unknown
        }
        final String[] labels = new String[audioTracks.size()];
        int checked = -1;
        for (int k = 0; k < audioTracks.size(); k++) {
            labels[k] = trackLabel(k, audioTracks.get(k), R.string.audio_track_n);
            if (audioTracks.get(k) == selected) checked = k;
        }
        Ui.dialog(this).setTitle(R.string.player_audio)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (player == null || which < 0 || which >= audioTracks.size()) return;
                        int idx = audioTracks.get(which);
                        try {
                            player.selectTrack(idx);
                            wantedAudioTrack = idx;
                        } catch (RuntimeException e) {
                            Ui.toast(PlayerActivity.this, R.string.cannot_open);
                        }
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    // ================================================================ subtitles

    private void resetSubtitles() {
        externalSubs = null;
        subDelayMs = 0;
        selectedTextTrack = -1;
        showSubtitle(null);
    }

    private void showSubtitle(String text) {
        if (text == null || text.trim().isEmpty()) {
            subtitleView.setVisibility(View.GONE);
            subtitleView.setText("");
        } else {
            subtitleView.setText(text);
            subtitleView.setVisibility(View.VISIBLE);
        }
    }

    private void updateExternalSubtitle() {
        if (externalSubs == null) return;
        String text = externalSubs.textAt(safePosition() - subDelayMs);
        if (text == null ? lastSubText != null : !text.equals(lastSubText)) {
            lastSubText = text;
            showSubtitle(text);
        }
    }

    private void applySubtitleSize() {
        int sp = inPip ? 11 : SUB_SIZES_SP[prefs.subSize()];
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        int margin = Ui.dp(this, inPip ? 6 : 28);
        ((android.widget.FrameLayout.LayoutParams) subtitleView.getLayoutParams()).bottomMargin = margin;
        subtitleView.requestLayout();
    }

    private void moveSubtitles(boolean aboveControls) {
        float target = aboveControls && bottomBar.getHeight() > 0 ? -(bottomBar.getHeight() - Ui.dp(this, 12)) : 0;
        subtitleView.animate().translationY(target).setDuration(150).start();
    }

    private void showSubtitleDialog() {
        final List<Runnable> actions = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        boolean noneSelected = externalSubs == null && selectedTextTrack < 0;
        labels.add((noneSelected ? "✓  " : "") + getString(R.string.subs_off));
        actions.add(new Runnable() { public void run() { disableSubtitles(); } });
        for (int k = 0; k < textTracks.size(); k++) {
            final int idx = textTracks.get(k);
            boolean sel = externalSubs == null && selectedTextTrack == idx;
            labels.add((sel ? "✓  " : "") + trackLabel(k, idx, R.string.subs_embedded));
            actions.add(new Runnable() { public void run() { selectEmbeddedSubtitle(idx); } });
        }
        labels.add((externalSubs != null ? "✓  " : "") + getString(R.string.subs_load));
        actions.add(new Runnable() { public void run() { pickSubtitleFile(); } });
        labels.add(getString(R.string.subs_size));
        actions.add(new Runnable() { public void run() { showSubtitleSizeDialog(); } });
        if (externalSubs != null) {
            labels.add(getString(R.string.subs_delay) + "  (" + TimeFormat.formatOffsetSeconds(subDelayMs) + ")");
            actions.add(new Runnable() { public void run() { showSubtitleDelayDialog(); } });
        }
        Ui.dialog(this).setTitle(R.string.player_subtitles)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < actions.size()) actions.get(which).run();
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    private void disableSubtitles() {
        externalSubs = null;
        wantedTextTrack = -1;
        if (player != null && selectedTextTrack >= 0) {
            try {
                player.deselectTrack(selectedTextTrack);
            } catch (RuntimeException ignored) {
                // nothing selected
            }
        }
        selectedTextTrack = -1;
        lastSubText = null;
        showSubtitle(null);
    }

    private void selectEmbeddedSubtitle(int idx) {
        if (player == null || !prepared) return;
        try {
            if (selectedTextTrack >= 0 && selectedTextTrack != idx) player.deselectTrack(selectedTextTrack);
            player.selectTrack(idx);
            selectedTextTrack = idx;
            wantedTextTrack = idx;
            externalSubs = null;
            lastSubText = null;
            showSubtitle(null);
        } catch (RuntimeException e) {
            Ui.toast(this, R.string.subs_failed);
        }
    }

    private void pickSubtitleFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        launchingInternal = true;
        try {
            startActivityForResult(i, REQ_SUBTITLE);
        } catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.addCategory(Intent.CATEGORY_OPENABLE);
            g.setType("*/*");
            try {
                startActivityForResult(g, REQ_SUBTITLE);
            } catch (ActivityNotFoundException e2) {
                launchingInternal = false;
                Ui.toast(this, R.string.no_app_found);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SUBTITLE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            loadSubtitleAsync(new SubtitleSource() {
                @Override
                public InputStream open() throws IOException {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new FileNotFoundException(uri.toString());
                    return in;
                }
            }, false);
        }
    }

    private interface SubtitleSource {
        InputStream open() throws IOException;
    }

    /** Reads, decodes and parses a subtitle file off the main thread, then shows it if the same video is still open. */
    private void loadSubtitleAsync(final SubtitleSource source, final boolean auto) {
        final String forUri = openedUri;
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    SubtitleTrack track = null;
                    try {
                        byte[] bytes = readAll(source.open());
                        track = SubtitleParser.parse(TextDecoder.decode(bytes));
                    } catch (Throwable ignored) {
                        // reported below
                    }
                    final SubtitleTrack result = track;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onSubtitleLoaded(result, auto, forUri);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            // activity is closing
        }
    }

    private void onSubtitleLoaded(SubtitleTrack track, boolean auto, String forUri) {
        if (isFinishing() || forUri == null || !forUri.equals(openedUri)) return;
        if (track == null || track.size() == 0) {
            if (!auto) Ui.toast(this, R.string.subs_failed);
            return;
        }
        if (auto && (externalSubs != null || selectedTextTrack >= 0)) return;
        if (player != null && selectedTextTrack >= 0) {
            try {
                player.deselectTrack(selectedTextTrack);
            } catch (RuntimeException ignored) {
                // fine
            }
        }
        selectedTextTrack = -1;
        wantedTextTrack = -1;
        externalSubs = track;
        subDelayMs = 0;
        lastSubText = null;
        updateExternalSubtitle();
        Ui.toast(this, auto ? R.string.subs_auto_loaded : R.string.subs_loaded);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            long total = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > MAX_SUBTITLE_BYTES) throw new IOException("subtitle file too large");
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Looks for "video.srt", "video.ar.srt", ... next to the video file (works where the file system allows it). */
    private void tryAutoSubtitles(PlayItem item) {
        if (item.path == null || item.path.isEmpty() || item.isNetwork()) return;
        String base = item.path;
        int dot = base.lastIndexOf('.');
        int slash = base.lastIndexOf('/');
        if (dot > slash) base = base.substring(0, dot);
        final String[] suffixes = {".srt", ".vtt", ".ar.srt", ".ara.srt", ".en.srt", ".eng.srt"};
        for (String s : suffixes) {
            final File f = new File(base + s);
            boolean readable;
            try {
                readable = f.isFile() && f.canRead();
            } catch (SecurityException e) {
                readable = false;
            }
            if (readable) {
                loadSubtitleAsync(new SubtitleSource() {
                    @Override
                    public InputStream open() throws IOException {
                        return new FileInputStream(f);
                    }
                }, true);
                return;
            }
        }
    }

    private void showSubtitleSizeDialog() {
        String[] labels = {getString(R.string.subs_size_small), getString(R.string.subs_size_medium),
                getString(R.string.subs_size_large), getString(R.string.subs_size_xlarge)};
        Ui.dialog(this).setTitle(R.string.subs_size)
                .setSingleChoiceItems(labels, prefs.subSize(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.setSubSize(which);
                        applySubtitleSize();
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    private void showSubtitleDelayDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final TextView value = new TextView(this);
        value.setGravity(Gravity.CENTER);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        value.setText(TimeFormat.formatOffsetSeconds(subDelayMs));
        box.addView(value);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        long[] steps = {-500, -100, 100, 500};
        String[] names = {"−0.5", "−0.1", "+0.1", "+0.5"};
        for (int i = 0; i < steps.length; i++) {
            Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
            b.setText(names[i]);
            b.setTextColor(getColor(R.color.accent));
            final long step = steps[i];
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    subDelayMs = Math.max(-600_000L, Math.min(600_000L, subDelayMs + step));
                    value.setText(TimeFormat.formatOffsetSeconds(subDelayMs));
                    lastSubText = null;
                    updateExternalSubtitle();
                }
            });
            row.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(row);
        TextView hint = new TextView(this);
        hint.setText(R.string.subs_delay_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        box.addView(hint);
        Ui.dialog(this).setTitle(R.string.subs_delay_title)
                .setView(box)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.subs_reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        subDelayMs = 0;
                        lastSubText = null;
                        updateExternalSubtitle();
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    // ================================================================ speed, sleep, repeat, boost, info

    private void showSpeedDialog() {
        String[] labels = new String[SPEEDS.length];
        int checked = 3;
        for (int i = 0; i < SPEEDS.length; i++) {
            String l = Ui.speedLabel(SPEEDS[i]);
            labels[i] = SPEEDS[i] == 1f ? getString(R.string.speed_normal, l) : l;
            if (Math.abs(SPEEDS[i] - speed) < 0.01f) checked = i;
        }
        Ui.dialog(this).setTitle(R.string.player_speed)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which < 0 || which >= SPEEDS.length) return;
                        speed = SPEEDS[which];
                        if (isPlaying()) applySpeedIfNeeded();
                        updateSpeedBadge();
                        updateSessionState();
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    private void updateSpeedBadge() {
        if (Math.abs(speed - 1f) < 0.001f) {
            speedBadge.setVisibility(View.GONE);
        } else {
            speedBadge.setText(Ui.speedLabel(speed));
            speedBadge.setVisibility(View.VISIBLE);
        }
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        MenuItem autoNext = menu.getMenu().add(0, 8, 0, R.string.player_autoplay);
        autoNext.setCheckable(true);
        autoNext.setChecked(prefs.autoplayNext());
        menu.getMenu().add(0, 1, 1, R.string.more_sleep);
        menu.getMenu().add(0, 2, 2, R.string.more_repeat);
        menu.getMenu().add(0, 3, 3, R.string.more_boost);
        menu.getMenu().add(0, 4, 4, R.string.more_screenshot);
        menu.getMenu().add(0, 5, 5, R.string.more_info);
        menu.getMenu().add(0, 6, 6, R.string.more_open_with);
        menu.getMenu().add(0, 7, 7, prefs.backgroundPlay() ? R.string.more_background_on : R.string.more_background_off);
        if (prefs.volumeKeysPause()) menu.getMenu().add(0, 9, 9, R.string.more_volume_help);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1: showSleepDialog(); return true;
                    case 2: showRepeatDialog(); return true;
                    case 3: showBoostDialog(); return true;
                    case 4: takeScreenshot(); return true;
                    case 5: showVideoInfo(); return true;
                    case 6: openWithOtherApp(); return true;
                    case 8:
                        toggleAutoNext();
                        return true;
                    case 9:
                        showVolumeHelp();
                        return true;
                    case 7:
                        prefs.setBackgroundPlay(!prefs.backgroundPlay());
                        Ui.toast(PlayerActivity.this, prefs.backgroundPlay() ? R.string.more_background_on : R.string.more_background_off);
                        return true;
                    default: return false;
                }
            }
        });
        menu.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu m) {
                hideSystemUi();
            }
        });
        menu.show();
    }

    private void showSleepDialog() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.sleep_off));
        int checked = sleepMinutes == 0 && !sleepAtEnd ? 0 : -1;
        for (int i = 0; i < SLEEP_MINUTES.length; i++) {
            labels.add(Ui.plural(this, R.plurals.minutes, SLEEP_MINUTES[i]));
            if (!sleepAtEnd && sleepMinutes == SLEEP_MINUTES[i]) checked = i + 1;
        }
        labels.add(getString(R.string.sleep_end));
        if (sleepAtEnd) checked = labels.size() - 1;
        final int endIndex = labels.size() - 1;
        Ui.dialog(this).setTitle(R.string.more_sleep)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        handler.removeCallbacks(sleepRunnable);
                        sleepAtEnd = false;
                        sleepMinutes = 0;
                        if (which == 0) {
                            Ui.toast(PlayerActivity.this, R.string.sleep_cancelled);
                        } else if (which == endIndex) {
                            sleepAtEnd = true;
                            Ui.toast(PlayerActivity.this, getString(R.string.sleep_set, getString(R.string.sleep_end)));
                        } else {
                            sleepMinutes = SLEEP_MINUTES[which - 1];
                            handler.postDelayed(sleepRunnable, sleepMinutes * 60_000L);
                            Ui.toast(PlayerActivity.this, getString(R.string.sleep_set,
                                    Ui.plural(PlayerActivity.this, R.plurals.minutes, sleepMinutes)));
                        }
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    private final Runnable sleepRunnable = new Runnable() {
        @Override
        public void run() {
            sleepMinutes = 0;
            if (isPlaying()) pausePlayback(true);
            Ui.toast(PlayerActivity.this, R.string.sleep_done);
        }
    };

    private void showRepeatDialog() {
        String[] labels = {getString(R.string.repeat_off), getString(R.string.repeat_one), getString(R.string.repeat_all)};
        Ui.dialog(this).setTitle(R.string.more_repeat)
                .setSingleChoiceItems(labels, repeatMode, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        repeatMode = which;
                        updatePrevNext();
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    private void showBoostDialog() {
        String[] labels = {getString(R.string.boost_off), getString(R.string.boost_low),
                getString(R.string.boost_medium), getString(R.string.boost_high)};
        Ui.dialog(this).setTitle(R.string.more_boost)
                .setSingleChoiceItems(labels, boostIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boostIndex = which;
                        applyBoost(true);
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    private void showVideoInfo() {
        PlayItem item = playlist.current();
        if (item == null) return;
        String res = videoW > 0 && videoH > 0
                ? videoW + " × " + videoH + "  (" + VideoItem.qualityOf(videoW, videoH) + ")" : "";
        StringBuilder formats = new StringBuilder();
        if (trackInfos != null) {
            for (MediaPlayer.TrackInfo t : trackInfos) {
                if (t == null) continue;
                try {
                    MediaFormat f = t.getFormat();
                    String mime = f != null && f.containsKey(MediaFormat.KEY_MIME) ? f.getString(MediaFormat.KEY_MIME) : null;
                    if (mime != null) {
                        if (formats.length() > 0) formats.append(", ");
                        formats.append(mime);
                    }
                } catch (RuntimeException ignored) {
                    // skip unknown track
                }
            }
        }
        String size = "";
        long bytes = querySize(Uri.parse(item.uri));
        if (bytes > 0) size = SizeFormat.format(bytes);
        String location = item.path != null ? item.path : item.uri;
        Ui.showDetails(this, getString(R.string.info_title), new String[][]{
                {getString(R.string.info_name), item.title},
                {getString(R.string.info_resolution), res},
                {getString(R.string.info_duration), durationMs > 0 ? TimeFormat.format(durationMs) : ""},
                {getString(R.string.info_size), size},
                {getString(R.string.info_format), formats.toString()},
                {getString(R.string.info_tracks), getString(R.string.tracks_summary,
                        String.valueOf(audioTracks.size()), String.valueOf(textTracks.size()))},
                {getString(R.string.info_path), location},
        });
    }

    private long querySize(Uri uri) {
        if (!"content".equals(uri.getScheme())) {
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new File(uri.getPath()).length();
            return 0;
        }
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (RuntimeException ignored) {
            // unknown size
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    private void openWithOtherApp() {
        PlayItem item = playlist.current();
        if (item == null) return;
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(Uri.parse(item.uri), item.mime != null && !item.mime.isEmpty() ? item.mime : "video/*");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(view, getString(R.string.more_open_with));
        chooser.putExtra("android.intent.extra.EXCLUDE_COMPONENTS",
                new ComponentName[]{new ComponentName(this, PlayerActivity.class)});
        if (isPlaying()) pausePlayback(true);
        launchingInternal = true;
        if (!Ui.safeStart(this, chooser)) launchingInternal = false;
    }

    // ================================================================ screenshot

    private void takeScreenshot() {
        if (player == null || !prepared || videoW <= 0 || videoH <= 0) {
            Ui.toast(this, R.string.shot_failed);
            return;
        }
        float scale = Math.min(1f, 1920f / Math.max(videoW, videoH));
        int w = Math.max(1, Math.round(videoW * scale));
        int h = Math.max(1, Math.round(videoH * scale));
        Bitmap bmp;
        try {
            bmp = videoView.getBitmap(w, h);
        } catch (Throwable t) {
            bmp = null;
        }
        if (bmp == null) {
            Ui.toast(this, R.string.shot_failed);
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingShot = bmp;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }
        saveScreenshot(bmp);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_WRITE) return;
        Bitmap bmp = pendingShot;
        pendingShot = null;
        if (bmp == null) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) saveScreenshot(bmp);
        else Ui.toast(this, R.string.shot_perm);
    }

    private void saveScreenshot(final Bitmap bmp) {
        final Context app = getApplicationContext();
        final String name = "NoorPlayer_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    final boolean ok = writeScreenshot(app, bmp, name);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Ui.toast(app, ok ? R.string.shot_saved : R.string.shot_failed);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            Ui.toast(this, R.string.shot_failed);
        }
    }

    private static boolean writeScreenshot(Context c, Bitmap bmp, String name) {
        OutputStream out = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = c.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put("relative_path", Environment.DIRECTORY_PICTURES + "/Noor Player");
                v.put("is_pending", 1);
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                out = cr.openOutputStream(uri);
                if (out == null) return false;
                boolean ok = bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
                out.close();
                out = null;
                ContentValues done = new ContentValues();
                done.put("is_pending", 0);
                cr.update(uri, done, null, null);
                return ok;
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Noor Player");
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File f = new File(dir, name);
            out = new FileOutputStream(f);
            boolean ok = bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
            out.close();
            out = null;
            MediaScannerConnection.scanFile(c, new String[]{f.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
            return ok;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
    }

    // ================================================================ screen: controls, lock, rotation, aspect

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying() && !userSeeking) hideControls();
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && prepared) {
                if (controlsVisible || userSeeking) updateProgress();
                updateExternalSubtitle();
                if (SystemClock.uptimeMillis() - lastAutoSave > 5000 && isPlaying()) savePosition(false);
            }
            handler.postDelayed(this, externalSubs != null ? 100 : 500);
        }
    };

    private final Runnable hideCenterInfoRunnable = new Runnable() {
        @Override
        public void run() {
            centerInfo.setVisibility(View.GONE);
        }
    };

    private final Runnable hideUnlockRunnable = new Runnable() {
        @Override
        public void run() {
            unlockBtn.setVisibility(View.GONE);
        }
    };

    private final Runnable hideResumeRunnable = new Runnable() {
        @Override
        public void run() {
            resumeBanner.setVisibility(View.GONE);
        }
    };

    /** Dialogs make Android show the status/navigation bars; hide them again afterwards. */
    private final DialogInterface.OnDismissListener restoreUiOnDismiss = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            hideSystemUi();
            scheduleHide();
        }
    };

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showControls() {
        if (inPip || locked || errorVisible) return;
        controls.animate().cancel();
        if (!controlsVisible || controls.getVisibility() != View.VISIBLE) {
            controls.setAlpha(0f);
            controls.setVisibility(View.VISIBLE);
            controls.animate().alpha(1f).setDuration(150).start();
        }
        controlsVisible = true;
        updateProgress();
        moveSubtitles(true);
        scheduleHide();
    }

    private void hideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        if (!controlsVisible) return;
        controlsVisible = false;
        controls.animate().cancel();
        controls.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!controlsVisible) controls.setVisibility(View.GONE);
            }
        }).start();
        moveSubtitles(false);
    }

    private void hideControlsNow() {
        handler.removeCallbacks(hideControlsRunnable);
        controlsVisible = false;
        controls.animate().cancel();
        controls.setVisibility(View.GONE);
        subtitleView.setTranslationY(0);
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControlsRunnable);
        if (controlsVisible && isPlaying() && !userSeeking) handler.postDelayed(hideControlsRunnable, HIDE_DELAY_MS);
    }

    private void updateProgress() {
        if (player == null || !prepared) return;
        long pos = userSeeking ? seekBar.getProgress() : safePosition();
        if (!userSeeking && durationMs > 0) seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, pos));
        positionView.setText(TimeFormat.format(pos));
        if (durationMs > 0) {
            durationView.setText(showRemaining ? "-" + TimeFormat.format(Math.max(0, durationMs - pos))
                    : TimeFormat.format(durationMs));
        }
    }

    private void updatePlayPause() {
        boolean playing = isPlaying();
        if (playing) {
            playPauseBtn.setImageResource(R.drawable.ic_pause);
            playPauseBtn.setContentDescription(getString(R.string.player_pause));
        } else {
            playPauseBtn.setImageResource(completed ? R.drawable.ic_replay : R.drawable.ic_play);
            playPauseBtn.setContentDescription(getString(R.string.player_play));
        }
    }

    private void toggleAutoNext() {
        prefs.setAutoplayNext(!prefs.autoplayNext());
        updateAutoNextButton();
        boolean on = prefs.autoplayNext();
        showCenterInfo(R.drawable.ic_playlist_play, getString(on ? R.string.autoplay_on : R.string.autoplay_off), -1);
        hideCenterInfoLater(1200);
    }

    /** Lit (gold) when the next video plays automatically, dimmed when it doesn't. */
    private void updateAutoNextButton() {
        boolean on = prefs.autoplayNext();
        autoNextBtn.setImageTintList(android.content.res.ColorStateList.valueOf(on ? getColor(R.color.accent) : 0x80FFFFFF));
        autoNextBtn.setContentDescription(getString(on ? R.string.autoplay_on : R.string.autoplay_off));
    }

    private void updatePrevNext() {
        boolean many = playlist != null && playlist.size() > 1;
        prevBtn.setVisibility(many ? View.VISIBLE : View.GONE);
        nextBtn.setVisibility(many ? View.VISIBLE : View.GONE);
        autoNextBtn.setVisibility(many ? View.VISIBLE : View.GONE);
        updateAutoNextButton();
        if (many) {
            boolean hasNext = playlist.hasNext(repeatMode);
            nextBtn.setEnabled(hasNext);
            nextBtn.setAlpha(hasNext ? 1f : 0.35f);
        }
    }

    private void updateSeekLabels() {
        String s = String.valueOf(prefs.seekStepSec());
        rewindLabel.setText(s);
        forwardLabel.setText(s);
    }

    private void showBuffering(boolean show) {
        buffering.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showCenterInfo(int iconRes, String text, int percent) {
        handler.removeCallbacks(hideCenterInfoRunnable);
        centerInfoIcon.setImageResource(iconRes);
        centerInfoText.setText(text);
        if (percent >= 0) {
            centerInfoBar.setProgress(Math.max(0, Math.min(100, percent)));
            centerInfoBar.setVisibility(View.VISIBLE);
        } else {
            centerInfoBar.setVisibility(View.GONE);
        }
        centerInfo.setVisibility(View.VISIBLE);
    }

    private void hideCenterInfoLater(long delayMs) {
        handler.removeCallbacks(hideCenterInfoRunnable);
        handler.postDelayed(hideCenterInfoRunnable, delayMs);
    }

    private void showResumeBanner(long positionMs) {
        resumeText.setText(getString(R.string.resume_from, TimeFormat.format(positionMs)));
        resumeBanner.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideResumeRunnable);
        handler.postDelayed(hideResumeRunnable, 6000);
    }

    private void showVolumeHelp() {
        prefs.setVolumeHelpShown();
        Ui.dialog(this).setTitle(R.string.vol_help_title)
                .setMessage(R.string.vol_help_text)
                .setPositiveButton(R.string.vol_help_open, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isPlaying()) pausePlayback(true);
                        launchingInternal = true;
                        if (!Ui.safeStart(PlayerActivity.this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
                            launchingInternal = false;
                        }
                    }
                })
                .setNegativeButton(R.string.not_now, null)
                .setOnDismissListener(restoreUiOnDismiss)
                .show();
    }

    /** True if Android's "hold both volume keys" accessibility shortcut is set (it swallows the key chord). */
    private boolean volumeShortcutLikelyOn() {
        try {
            String v = Settings.Secure.getString(getContentResolver(), "accessibility_shortcut_target_service");
            return v != null && !v.trim().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private void maybeShowVolumeHelp() {
        if (!prefs.volumeKeysPause() || prefs.volumeHelpShown() || !prefs.tipShown()) return;
        if (!volumeShortcutLikelyOn()) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !stopped && !inPip) showVolumeHelp();
            }
        }, 1500);
    }

    private void maybeShowTip() {
        if (prefs.tipShown()) {
            maybeShowVolumeHelp();
            return;
        }
        prefs.setTipShown();
        tipView.setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tipView.setVisibility(View.GONE);
            }
        }, 9000);
    }

    private void showError(String message) {
        showBuffering(false);
        hideControlsNow();
        playWhenReady = false;
        keepScreenOn(false);
        errorText.setText(message);
        errorNext.setVisibility(playlist != null && playlist.hasNext(repeatMode) ? View.VISIBLE : View.GONE);
        errorView.setVisibility(View.VISIBLE);
        errorVisible = true;
        updateSessionState();
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
        errorVisible = false;
    }

    private void setLocked(boolean lock) {
        locked = lock;
        if (lock) {
            hideControlsNow();
            showUnlockButton();
        } else {
            handler.removeCallbacks(hideUnlockRunnable);
            unlockBtn.setVisibility(View.GONE);
            showControls();
        }
    }

    private void showUnlockButton() {
        unlockBtn.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideUnlockRunnable);
        handler.postDelayed(hideUnlockRunnable, 2500);
    }

    private void showRotationMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        // menu ids are the orientation modes + 1 (0 is reserved for "no id")
        menu.getMenu().add(1, ORIENT_AUTO + 1, 0, R.string.rot_auto);
        menu.getMenu().add(1, ORIENT_LANDSCAPE + 1, 1, R.string.rot_landscape);
        menu.getMenu().add(1, ORIENT_PORTRAIT + 1, 2, R.string.rot_portrait);
        menu.getMenu().add(1, ORIENT_SENSOR + 1, 3, R.string.rot_sensor);
        menu.getMenu().setGroupCheckable(1, true, true);
        MenuItem current = menu.getMenu().findItem(orientationMode + 1);
        if (current != null) current.setChecked(true);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                setOrientationMode(item.getItemId() - 1);
                return true;
            }
        });
        menu.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu m) {
                hideSystemUi();
            }
        });
        menu.show();
    }

    private void setOrientationMode(int mode) {
        orientationMode = mode;
        switch (mode) {
            case ORIENT_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
            case ORIENT_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case ORIENT_SENSOR:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                break;
            default:
                orientationMode = ORIENT_AUTO;
                if (videoW > 0 && videoH > 0) applyAutoOrientation();
                else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
        }
    }

    private void applyAutoOrientation() {
        if (orientationMode != ORIENT_AUTO || videoW <= 0 || videoH <= 0 || inPip) return;
        setRequestedOrientation(videoW >= videoH ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void cycleAspect() {
        aspectMode = (aspectMode + 1) % VideoFit.MODE_COUNT;
        zoom = 1f;
        panX = 0;
        panY = 0;
        applyTransform();
        showCenterInfo(R.drawable.ic_aspect, aspectName(aspectMode), -1);
        hideCenterInfoLater(900);
    }

    private String aspectName(int mode) {
        switch (mode) {
            case VideoFit.FILL: return getString(R.string.aspect_fill);
            case VideoFit.STRETCH: return getString(R.string.aspect_stretch);
            case VideoFit.RATIO_16_9: return getString(R.string.aspect_16_9);
            case VideoFit.RATIO_4_3: return getString(R.string.aspect_4_3);
            case VideoFit.ORIGINAL: return getString(R.string.aspect_original);
            default: return getString(R.string.aspect_fit);
        }
    }

    private void applyTransform() {
        int vw = videoView.getWidth(), vh = videoView.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float[] size = VideoFit.displaySize(aspectMode, vw, vh, videoW, videoH);
        zoom = VideoFit.clampZoom(zoom);
        panX = VideoFit.clampPan(panX, size[0] * zoom, vw);
        panY = VideoFit.clampPan(panY, size[1] * zoom, vh);
        float[] t = VideoFit.transform(aspectMode, vw, vh, videoW, videoH, zoom, panX, panY);
        Matrix m = new Matrix();
        m.setScale(t[0], t[1]);
        m.postTranslate(t[2], t[3]);
        videoView.setTransform(m);
        videoView.invalidate();
    }

    private void enterPip() {
        if (!pipSupported || errorVisible) return;
        Object params = Compat.buildPipParams(this, videoW, videoH, isPlaying());
        if (params != null) Compat.enterPip(this, params);
    }

    private void updatePipParams() {
        if (!pipSupported) return;
        Object params = Compat.buildPipParams(this, videoW, videoH, isPlaying());
        if (params != null) Compat.setPipParams(this, params);
    }

    // ================================================================ gestures

    private void setupGestures() {
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        edgeIgnorePx = Ui.dp(this, 36);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                onSingleTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                onDoubleTapAt(e.getX());
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return onDrag(e1, e2);
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                return onZoomBegin(d);
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                onZoom(d);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector d) {
                hideCenterInfoLater(700);
            }
        });
        gestureLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return onGestureTouch(e);
            }
        });
    }

    private boolean onGestureTouch(MotionEvent e) {
        if (inPip) return false;
        int action = e.getActionMasked();
        if (locked) {
            if (action == MotionEvent.ACTION_UP) showUnlockButton();
            return true;
        }
        if (errorVisible) return true;
        if (action == MotionEvent.ACTION_DOWN) {
            gestureMode = G_NONE;
            seekArmed = false;
            suppressNextTap = false;
            handler.removeCallbacks(armSeekRunnable);
            if (prefs.gestures()) handler.postDelayed(armSeekRunnable, SEEK_HOLD_MS);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            handler.removeCallbacks(armSeekRunnable);
        }
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) finishGesture();
        return true;
    }

    private void onSingleTap() {
        if (suppressNextTap) {
            // the finger was held to start seeking and then lifted without sliding
            suppressNextTap = false;
            return;
        }
        if (tipView.getVisibility() == View.VISIBLE) {
            tipView.setVisibility(View.GONE);
            return;
        }
        if (controlsVisible) hideControls();
        else showControls();
    }

    private void onDoubleTapAt(float x) {
        if (player == null || !prepared) return;
        float w = gestureLayer.getWidth();
        long step = prefs.seekStepSec() * 1000L;
        if (x < w * 0.35f) seekBy(-step);
        else if (x > w * 0.65f) seekBy(step);
        else togglePlay();
    }

    private boolean onDrag(MotionEvent e1, MotionEvent e2) {
        if (e1 == null || e2 == null) return false;
        if (gestureMode == G_ZOOM || gestureMode == G_IGNORE) return false;
        if (scaleDetector.isInProgress() || e2.getPointerCount() > 1) return false;
        float dx = e2.getX() - e1.getX();
        float dy = e2.getY() - e1.getY();
        int w = gestureLayer.getWidth(), h = gestureLayer.getHeight();
        if (w <= 0 || h <= 0) return false;
        if (gestureMode == G_NONE) {
            if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) return false;
            // the finger moved: it was not a still hold, so it can no longer arm seeking
            handler.removeCallbacks(armSeekRunnable);
            int decision = GestureGate.decide(seekArmed, dx, dy, e1.getX(), e1.getY(), w, h, edgeIgnorePx,
                    prefs.gestures(), player != null && prepared && durationMs > 0);
            if (decision == GestureGate.SEEK) {
                gestureMode = G_SEEK;
                gestureStartPos = safePosition();
                gestureSeekTarget = gestureStartPos;
                handler.removeCallbacks(hideControlsRunnable);
            } else if (decision == GestureGate.BRIGHTNESS) {
                gestureMode = G_BRIGHTNESS;
                gestureStartBrightness = currentBrightness();
            } else if (decision == GestureGate.VOLUME) {
                gestureMode = G_VOLUME;
                gestureStartVolume = currentVolumeIndex();
                lastVolumeIndex = -1;
            } else {
                // includes a quick sideways swipe without holding first, so the video doesn't jump by mistake
                gestureMode = G_IGNORE;
                if (seekArmed) hideCenterInfoLater(300);
                return false;
            }
        }
        switch (gestureMode) {
            case G_SEEK: {
                long target = GestureMath.seekTarget(gestureStartPos, dx, w, durationMs);
                gestureSeekTarget = target;
                showCenterInfo(target >= gestureStartPos ? R.drawable.ic_fast_forward : R.drawable.ic_fast_rewind,
                        TimeFormat.format(target) + "\n" + TimeFormat.formatDelta(target - gestureStartPos), -1);
                if (lastScrubSeek < 0 || Math.abs(target - lastScrubSeek) >= 1000) {
                    lastScrubSeek = target;
                    seekToInternal(target, false);
                }
                return true;
            }
            case G_BRIGHTNESS: {
                float b = GestureMath.brightness(gestureStartBrightness, dy, h);
                setWindowBrightness(b);
                showCenterInfo(R.drawable.ic_brightness, getString(R.string.info_brightness, Ui.percent(b)),
                        Math.round(b * 100));
                return true;
            }
            case G_VOLUME: {
                int max = maxVolume();
                if (max <= 0) return false;
                int idx = Math.round(GestureMath.volume(gestureStartVolume, dy, h, max));
                if (idx != lastVolumeIndex) {
                    lastVolumeIndex = idx;
                    setVolumeIndex(idx);
                }
                float f = idx / (float) max;
                showCenterInfo(idx == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume,
                        getString(R.string.info_volume, Ui.percent(f)), Math.round(f * 100));
                return true;
            }
            default:
                return false;
        }
    }

    private void finishGesture() {
        handler.removeCallbacks(armSeekRunnable);
        if (seekArmed && gestureMode == G_NONE) hideCenterInfoLater(300);
        seekArmed = false;
        if (gestureMode == G_SEEK) {
            lastScrubSeek = -1;
            seekToInternal(gestureSeekTarget, true);
            scheduleHide();
        }
        if (gestureMode == G_SEEK || gestureMode == G_BRIGHTNESS || gestureMode == G_VOLUME) hideCenterInfoLater(500);
        gestureMode = G_NONE;
    }

    private boolean onZoomBegin(ScaleGestureDetector d) {
        if (inPip || locked || errorVisible) return false;
        handler.removeCallbacks(armSeekRunnable);
        seekArmed = false;
        if (gestureMode == G_SEEK) {
            lastScrubSeek = -1;
            seekToInternal(gestureSeekTarget, true);
        }
        gestureMode = G_ZOOM;
        lastFocusX = d.getFocusX();
        lastFocusY = d.getFocusY();
        return true;
    }

    private void onZoom(ScaleGestureDetector d) {
        float vw = videoView.getWidth(), vh = videoView.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float old = zoom;
        float nz = VideoFit.clampZoom(zoom * d.getScaleFactor());
        float fx = d.getFocusX() - vw / 2f, fy = d.getFocusY() - vh / 2f;
        panX = VideoFit.panForZoom(panX, old, nz, fx) + (d.getFocusX() - lastFocusX);
        panY = VideoFit.panForZoom(panY, old, nz, fy) + (d.getFocusY() - lastFocusY);
        lastFocusX = d.getFocusX();
        lastFocusY = d.getFocusY();
        zoom = nz;
        applyTransform();
        showCenterInfo(R.drawable.ic_zoom, getString(R.string.info_zoom, Ui.percent(zoom)), -1);
    }

    private float currentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        if (b >= 0f) return b;
        try {
            int sys = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return GestureMath.clamp(sys / 255f, 0.01f, 1f);
        } catch (Throwable t) {
            return 0.5f;
        }
    }

    private void setWindowBrightness(float b) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b;
        getWindow().setAttributes(lp);
    }

    private int maxVolume() {
        try {
            return audio != null ? audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private float currentVolumeIndex() {
        try {
            return audio != null ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void setVolumeIndex(int idx) {
        try {
            if (audio != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
        } catch (RuntimeException ignored) {
            // e.g. Do Not Disturb restrictions
        }
    }

    // ================================================================ keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long step = prefs.seekStepSec() * 1000L;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (prefs.volumeKeysPause() && audio != null) {
                    onVolumeKeyDown(keyCode == KeyEvent.KEYCODE_VOLUME_UP, event.getRepeatCount() > 0);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pausePlayback(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                next();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previous();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(step);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-step);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!controlsVisible && !locked) {
                    seekBy(-step);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!controlsVisible && !locked) {
                    seekBy(step);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!controlsVisible && !locked) {
                    showControls();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && prefs.volumeKeysPause() && audio != null) {
            chord.onKeyUp(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** On screen: a single volume key works as usual; both keys together pause or play. */
    private void onVolumeKeyDown(boolean up, boolean repeat) {
        int action = chord.onKeyDown(up, repeat, SystemClock.uptimeMillis(), (int) currentVolumeIndex());
        if (action == VolumeChord.ADJUST) {
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            } catch (RuntimeException ignored) {
                // e.g. Do Not Disturb restrictions
            }
            showVolumeInfo();
        } else if (action == VolumeChord.TOGGLE) {
            onVolumeChord();
        }
    }

    /** In the background the media session receives the volume steps (screen off or another app open). */
    private void onSessionVolumeStep(int direction) {
        int dir = direction > 0 && direction <= 1 ? 1 : (direction < 0 && direction >= -1 ? -1 : 0);
        if (dir == 0) return;
        int cur = (int) currentVolumeIndex();
        int action = chord.onStep(dir, SystemClock.uptimeMillis(), cur);
        if (action == VolumeChord.ADJUST) {
            setVolumeIndex(Math.max(0, Math.min(maxVolume(), cur + dir)));
        } else if (action == VolumeChord.TOGGLE) {
            onVolumeChord();
        }
        if (volumeProvider != null) {
            try {
                volumeProvider.setCurrentVolume((int) currentVolumeIndex());
            } catch (RuntimeException ignored) {
                // display only
            }
        }
    }

    private void onVolumeChord() {
        int restore = chord.restoreVolume();
        if (restore >= 0) setVolumeIndex(restore);
        boolean wasPlaying = isPlaying();
        togglePlay();
        if (wasPlaying && (backgroundMode || stopped || inPip)) {
            // keep receiving the volume keys so the next chord can resume
            chordPaused = true;
            handler.removeCallbacks(releaseChordHoldRunnable);
            handler.postDelayed(releaseChordHoldRunnable, CHORD_HOLD_MS);
            updateSessionState();
        }
        if (!stopped && !inPip) {
            boolean playing = playWhenReady;
            showCenterInfo(playing ? R.drawable.ic_play : R.drawable.ic_pause,
                    getString(playing ? R.string.state_playing : R.string.state_paused), -1);
            hideCenterInfoLater(800);
        }
    }

    private PowerManager.WakeLock keyWakeLock;

    /** Screen off: keep the processor awake a moment so a volume-key chord is handled right away. */
    private void briefWakeLock() {
        try {
            if (keyWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null) return;
                keyWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoorPlayer:volumeKeys");
                keyWakeLock.setReferenceCounted(false);
            }
            keyWakeLock.acquire(3000);
        } catch (RuntimeException ignored) {
            // the chord still works, just possibly a little later
        }
    }

    private void releaseChordHold() {
        handler.removeCallbacks(releaseChordHoldRunnable);
        if (!chordPaused) return;
        chordPaused = false;
        updateSessionState();
    }

    private void showVolumeInfo() {
        if (stopped || inPip) return;
        int max = maxVolume();
        if (max <= 0) return;
        int idx = (int) currentVolumeIndex();
        float f = idx / (float) max;
        showCenterInfo(idx == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume,
                getString(R.string.info_volume, Ui.percent(f)), Math.round(f * 100));
        hideCenterInfoLater(900);
    }

    /**
     * In the background, let the media session handle the volume keys (so both keys together can
     * pause). The real media volume is still changed, one step per press.
     */
    private void useRemoteVolume(boolean on) {
        if (session == null) return;
        try {
            if (on && prefs.volumeKeysPause()) {
                int max = maxVolume();
                if (max <= 0) return;
                volumeProvider = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, max, (int) currentVolumeIndex()) {
                    @Override
                    public void onAdjustVolume(final int direction) {
                        briefWakeLock();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                onSessionVolumeStep(direction);
                            }
                        });
                    }

                    @Override
                    public void onSetVolumeTo(final int volume) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                setVolumeIndex(volume);
                                if (volumeProvider != null) volumeProvider.setCurrentVolume((int) currentVolumeIndex());
                            }
                        });
                    }
                };
                session.setPlaybackToRemote(volumeProvider);
            } else if (volumeProvider != null) {
                volumeProvider = null;
                session.setPlaybackToLocal(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build());
            }
        } catch (RuntimeException ignored) {
            volumeProvider = null;
        }
    }

    // ================================================================ audio focus, headphones, media session

    private final AudioManager.OnAudioFocusChangeListener focusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int change) {
            switch (change) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseChordHold();
                    hasAudioFocus = false;
                    pausedByFocusLoss = false;
                    if (isPlaying()) pausePlayback(false);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    releaseChordHold();
                    if (isPlaying()) {
                        pausePlayback(false);
                        pausedByFocusLoss = true;
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    setPlayerVolume(0.3f);
                    ducked = true;
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    hasAudioFocus = true;
                    if (ducked) {
                        setPlayerVolume(1f);
                        ducked = false;
                    }
                    if (pausedByFocusLoss && !stopped) {
                        pausedByFocusLoss = false;
                        play();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void setPlayerVolume(float v) {
        if (player == null) return;
        try {
            player.setVolume(v, v);
        } catch (IllegalStateException ignored) {
            // player not ready
        }
    }

    @SuppressWarnings("deprecation")
    private void requestAudioFocus() {
        if (hasAudioFocus || audio == null) return;
        try {
            int r = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            hasAudioFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (RuntimeException ignored) {
            // play anyway
        }
    }

    @SuppressWarnings("deprecation")
    private void abandonAudioFocus() {
        if (!hasAudioFocus || audio == null) return;
        try {
            audio.abandonAudioFocus(focusListener);
        } catch (RuntimeException ignored) {
            // nothing to do
        }
        hasAudioFocus = false;
    }

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && isPlaying()) {
                pausePlayback(true);
            }
        }
    };

    private void registerNoisy() {
        if (noisyRegistered) return;
        try {
            registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            noisyRegistered = true;
        } catch (RuntimeException ignored) {
            // headphone unplug pause is optional
        }
    }

    private void unregisterNoisy() {
        if (!noisyRegistered) return;
        try {
            unregisterReceiver(noisyReceiver);
        } catch (RuntimeException ignored) {
            // already unregistered
        }
        noisyRegistered = false;
    }

    private void setupSession() {
        try {
            session = new MediaSession(this, "NoorPlayer");
            session.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    play();
                }

                @Override
                public void onPause() {
                    if (chordPaused) play();
                    else pausePlayback(true);
                }

                @Override
                public void onSkipToNext() {
                    next();
                }

                @Override
                public void onSkipToPrevious() {
                    previous();
                }

                @Override
                public void onFastForward() {
                    seekBy(prefs.seekStepSec() * 1000L);
                }

                @Override
                public void onRewind() {
                    seekBy(-prefs.seekStepSec() * 1000L);
                }

                @Override
                public void onSeekTo(long pos) {
                    seekToInternal(pos, true);
                }

                @Override
                public void onStop() {
                    pausePlayback(true);
                }
            });
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        } catch (RuntimeException e) {
            session = null;
        }
    }

    private void setSessionActive(boolean active) {
        if (session == null) return;
        try {
            session.setActive(active);
        } catch (RuntimeException ignored) {
            // optional
        }
    }

    private void updateSessionState() {
        if (session == null) return;
        try {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND
                    | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
            int state = errorVisible ? PlaybackState.STATE_ERROR
                    : isPlaying() || chordPaused ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            session.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, safePosition(), isPlaying() ? speed : 0f, SystemClock.elapsedRealtime())
                    .build());
        } catch (RuntimeException ignored) {
            // optional
        }
        if (backgroundMode) PlaybackService.update(this, notificationState());
    }

    private void updateSessionMetadata(PlayItem item) {
        if (session == null || item == null) return;
        try {
            MediaMetadata.Builder mb = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, item.title);
            if (prepared && durationMs > 0) mb.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
            if (notificationArt != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ART, notificationArt);
            session.setMetadata(mb.build());
        } catch (RuntimeException ignored) {
            // optional
        }
    }

    // ================================================================ video surface

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        if (surface != null) surface.release();
        surface = new Surface(st);
        if (player != null) {
            try {
                player.setSurface(surface);
            } catch (RuntimeException ignored) {
                // player is being released
            }
        }
        applyTransform();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        applyTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (player != null) {
            try {
                player.setSurface(null);
            } catch (RuntimeException ignored) {
                // player is being released
            }
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
        // nothing to do per frame
    }
}
