package com.ahmed.noorplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.ahmed.noorplayer.core.FolderInfo;
import com.ahmed.noorplayer.core.HistoryEntry;
import com.ahmed.noorplayer.core.PlayItem;
import com.ahmed.noorplayer.core.ResumePolicy;
import com.ahmed.noorplayer.core.SizeFormat;
import com.ahmed.noorplayer.core.TimeFormat;
import com.ahmed.noorplayer.core.UrlCheck;
import com.ahmed.noorplayer.core.VideoItem;
import com.ahmed.noorplayer.core.VideoSorter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** The video library: all videos, folders and recently watched, plus opening files and links. */
public class MainActivity extends Activity implements LibraryAdapter.Callbacks {
    private static final int REQ_PERMISSION = 1;
    private static final int REQ_OPEN_FILE = 2;

    private static final int TAB_VIDEOS = 0;
    private static final int TAB_FOLDERS = 1;
    private static final int TAB_RECENT = 2;

    private static final int[] SEEK_STEPS = {5, 10, 15, 20, 30};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private Prefs prefs;
    private LibraryAdapter adapter;

    private View headerBack, headerLogo, headerSort, searchBar, emptyView, loadingView, continueBar;
    private TextView headerTitle, emptyText, continueTitle;
    private ImageView emptyIcon;
    private EditText searchInput;
    private ListView list;
    private Button emptyPrimary, emptySecondary;
    private final TextView[] tabTexts = new TextView[3];
    private final View[] tabLines = new View[3];

    private int tab = TAB_VIDEOS;
    private FolderInfo openFolder;
    private String pendingFolderId;
    private String query = "";
    private List<VideoItem> allVideos = new ArrayList<>();
    private List<FolderInfo> folders = new ArrayList<>();
    private boolean loaded, loading, reloadPending, continueDismissed, observerRegistered;
    private HistoryEntry continueEntry;
    private ContentObserver observer;

    private final Runnable reloadRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasVideoPermission()) loadVideos();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = Prefs.get(this);
        bindViews();
        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt("tab", TAB_VIDEOS);
            query = savedInstanceState.getString("query", "");
            pendingFolderId = savedInstanceState.getString("folder");
            if (!query.isEmpty()) {
                searchBar.setVisibility(View.VISIBLE);
                searchInput.setText(query);
            }
        }
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 800);
            }
        };
        render();
        showCrashReportIfAny();
        if (!hasVideoPermission() && !prefs.askedPermission()) requestVideoPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasVideoPermission()) {
            registerObserver();
            loadVideos();
        } else {
            render();
        }
        updateContinueBar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        loader.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("query", query);
        if (openFolder != null) out.putString("folder", openFolder.id);
        else if (pendingFolderId != null) out.putString("folder", pendingFolderId);
    }

    @Override
    public void onBackPressed() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
            return;
        }
        if (openFolder != null) {
            openFolder = null;
            render();
            list.setSelection(0);
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- views

    private void bindViews() {
        headerBack = findViewById(R.id.header_back);
        headerLogo = findViewById(R.id.header_logo);
        headerTitle = (TextView) findViewById(R.id.header_title);
        headerSort = findViewById(R.id.header_sort);
        searchBar = findViewById(R.id.search_bar);
        searchInput = (EditText) findViewById(R.id.search_input);
        emptyView = findViewById(R.id.empty_view);
        emptyIcon = (ImageView) findViewById(R.id.empty_icon);
        emptyText = (TextView) findViewById(R.id.empty_text);
        emptyPrimary = (Button) findViewById(R.id.empty_primary);
        emptySecondary = (Button) findViewById(R.id.empty_secondary);
        loadingView = findViewById(R.id.loading);
        continueBar = findViewById(R.id.continue_bar);
        continueTitle = (TextView) findViewById(R.id.continue_title);
        list = (ListView) findViewById(R.id.list);

        tabTexts[TAB_VIDEOS] = (TextView) findViewById(R.id.tab_videos_text);
        tabTexts[TAB_FOLDERS] = (TextView) findViewById(R.id.tab_folders_text);
        tabTexts[TAB_RECENT] = (TextView) findViewById(R.id.tab_recent_text);
        tabLines[TAB_VIDEOS] = findViewById(R.id.tab_videos_line);
        tabLines[TAB_FOLDERS] = findViewById(R.id.tab_folders_line);
        tabLines[TAB_RECENT] = findViewById(R.id.tab_recent_line);
        setTabClick(R.id.tab_videos, TAB_VIDEOS);
        setTabClick(R.id.tab_folders, TAB_FOLDERS);
        setTabClick(R.id.tab_recent, TAB_RECENT);

        headerBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        findViewById(R.id.header_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchBar.getVisibility() == View.VISIBLE) closeSearch();
                else openSearch();
            }
        });
        findViewById(R.id.header_open).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOpenMenu(v);
            }
        });
        headerSort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortDialog();
            }
        });
        findViewById(R.id.header_more).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu(v);
            }
        });
        findViewById(R.id.search_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSearch();
            }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString();
                if (!q.equals(query)) {
                    query = q;
                    render();
                }
            }
        });
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard();
                    return true;
                }
                return false;
            }
        });

        adapter = new LibraryAdapter(this, ThumbnailLoader.get(this), this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onRowClick(position);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= adapter.getCount()) return false;
                LibraryAdapter.Row row = adapter.getItem(position);
                if (row.type == LibraryAdapter.TYPE_FOLDER) return false;
                onRowMore(view, row);
                return true;
            }
        });

        continueBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (continueEntry != null) playHistory(continueEntry, false);
            }
        });
        findViewById(R.id.continue_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                continueDismissed = true;
                continueBar.setVisibility(View.GONE);
            }
        });
    }

    private void setTabClick(int id, final int which) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tab == which && openFolder == null) {
                    list.setSelection(0);
                    return;
                }
                tab = which;
                openFolder = null;
                pendingFolderId = null;
                render();
                list.setSelection(0);
                updateContinueBar();
            }
        });
    }

    // ---------------------------------------------------------------- permission

    static String videoPermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_VIDEO"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasVideoPermission() {
        return checkSelfPermission(videoPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoPermission() {
        prefs.setAskedPermission();
        requestPermissions(new String[]{videoPermission()}, REQ_PERMISSION);
    }

    private boolean permissionBlocked() {
        return prefs.askedPermission() && !shouldShowRequestPermissionRationale(videoPermission());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_PERMISSION) return;
        if (hasVideoPermission()) {
            ThumbnailLoader.get(this).forgetFailures();
            registerObserver();
            loadVideos();
        } else {
            render();
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
        Ui.safeStart(this, i);
    }

    private void registerObserver() {
        if (observerRegistered) return;
        try {
            getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
            observerRegistered = true;
        } catch (RuntimeException ignored) {
            // live updates are a nice-to-have
        }
    }

    private void unregisterObserver() {
        if (!observerRegistered) return;
        try {
            getContentResolver().unregisterContentObserver(observer);
        } catch (RuntimeException ignored) {
            // already gone
        }
        observerRegistered = false;
    }

    // ---------------------------------------------------------------- loading

    private void loadVideos() {
        if (loading) {
            reloadPending = true;
            return;
        }
        loading = true;
        if (!loaded) render();
        final Context app = getApplicationContext();
        try {
            loader.execute(new Runnable() {
                @Override
                public void run() {
                    List<VideoItem> result;
                    try {
                        result = VideoRepository.loadAll(app);
                    } catch (Throwable t) {
                        result = null;
                    }
                    final List<VideoItem> r = result;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onVideosLoaded(r);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            loading = false;
        }
    }

    private void onVideosLoaded(List<VideoItem> result) {
        loading = false;
        if (isFinishing() || isDestroyed()) return;
        if (result != null) {
            allVideos = result;
            folders = VideoSorter.groupFolders(result, getString(R.string.unknown_folder));
        }
        loaded = true;
        String wanted = pendingFolderId != null ? pendingFolderId : (openFolder != null ? openFolder.id : null);
        if (wanted != null) {
            FolderInfo match = null;
            for (FolderInfo f : folders) {
                if (f.id.equals(wanted)) match = f;
            }
            openFolder = match;
            pendingFolderId = null;
        }
        render();
        if (reloadPending) {
            reloadPending = false;
            loadVideos();
        }
    }

    // ---------------------------------------------------------------- rendering

    private void render() {
        boolean perm = hasVideoPermission();
        boolean inFolder = tab == TAB_FOLDERS && openFolder != null;
        headerBack.setVisibility(inFolder ? View.VISIBLE : View.GONE);
        headerLogo.setVisibility(inFolder ? View.GONE : View.VISIBLE);
        headerTitle.setText(inFolder ? openFolder.name : getString(R.string.app_name));
        headerSort.setVisibility(tab == TAB_RECENT ? View.GONE : View.VISIBLE);
        for (int i = 0; i < 3; i++) {
            boolean sel = i == tab;
            tabTexts[i].setTextColor(getColor(sel ? R.color.text_primary : R.color.text_secondary));
            tabLines[i].setVisibility(sel ? View.VISIBLE : View.INVISIBLE);
        }

        List<LibraryAdapter.Row> rows = new ArrayList<>();
        int sort = prefs.sortMode();
        if (tab == TAB_RECENT) {
            for (HistoryEntry h : prefs.history()) {
                if (VideoSorter.matches(h.title, query)) rows.add(LibraryAdapter.Row.of(h));
            }
        } else if (perm && tab == TAB_FOLDERS && openFolder == null) {
            List<FolderInfo> fs = new ArrayList<>(folders);
            VideoSorter.sortFolders(fs, sort);
            for (FolderInfo f : fs) {
                if (VideoSorter.matches(f.name, query)) rows.add(LibraryAdapter.Row.of(f));
            }
        } else if (perm) {
            List<VideoItem> src = inFolder ? VideoSorter.inFolder(allVideos, openFolder.id) : new ArrayList<>(allVideos);
            VideoSorter.sort(src, sort);
            for (VideoItem v : src) {
                if (VideoSorter.matches(v.title, query) || (!inFolder && VideoSorter.matches(v.bucketName, query) && !query.trim().isEmpty())) {
                    rows.add(LibraryAdapter.Row.of(v));
                }
            }
        }
        adapter.setRows(rows, prefs.historyMap());

        loadingView.setVisibility(View.GONE);
        if (!rows.isEmpty()) {
            emptyView.setVisibility(View.GONE);
            return;
        }
        if (tab != TAB_RECENT && !perm) {
            boolean blocked = permissionBlocked();
            showEmpty(R.drawable.ic_movie, getString(blocked ? R.string.perm_denied_settings : R.string.perm_needed),
                    blocked ? R.string.perm_open_settings : R.string.perm_allow, R.string.open_file);
        } else if (tab != TAB_RECENT && !loaded) {
            emptyView.setVisibility(View.GONE);
            loadingView.setVisibility(View.VISIBLE);
        } else if (!query.trim().isEmpty()) {
            showEmpty(R.drawable.ic_search, getString(R.string.empty_no_results, query.trim()), 0, 0);
        } else if (tab == TAB_RECENT) {
            showEmpty(R.drawable.ic_history, getString(R.string.empty_recent), 0, 0);
        } else if (inFolder) {
            showEmpty(R.drawable.ic_folder, getString(R.string.empty_folder), 0, 0);
        } else {
            showEmpty(R.drawable.ic_movie, getString(R.string.empty_no_videos), R.string.open_file, R.string.open_url);
        }
    }

    private void showEmpty(int icon, String text, final int primaryRes, final int secondaryRes) {
        emptyIcon.setImageResource(icon);
        emptyText.setText(text);
        bindEmptyButton(emptyPrimary, primaryRes);
        bindEmptyButton(emptySecondary, secondaryRes);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void bindEmptyButton(Button b, final int res) {
        if (res == 0) {
            b.setVisibility(View.GONE);
            return;
        }
        b.setVisibility(View.VISIBLE);
        b.setText(res);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (res == R.string.perm_allow) requestVideoPermission();
                else if (res == R.string.perm_open_settings) openAppSettings();
                else if (res == R.string.open_file) openFilePicker();
                else if (res == R.string.open_url) showUrlDialog();
            }
        });
    }

    private void updateContinueBar() {
        continueEntry = null;
        if (!continueDismissed && tab != TAB_RECENT) {
            List<HistoryEntry> h = prefs.history();
            for (int i = 0; i < Math.min(3, h.size()); i++) {
                HistoryEntry e = h.get(i);
                if (!e.finished && ResumePolicy.resumePosition(e.positionMs, e.durationMs, false) > 0) {
                    continueEntry = e;
                    break;
                }
            }
        }
        if (continueEntry == null) {
            continueBar.setVisibility(View.GONE);
        } else {
            String t = continueEntry.title != null ? continueEntry.title : continueEntry.uri;
            continueTitle.setText(t + "  ·  " + TimeFormat.format(continueEntry.positionMs));
            continueBar.setVisibility(View.VISIBLE);
        }
    }

    // ---------------------------------------------------------------- search

    private void openSearch() {
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        hideKeyboard();
        searchInput.setText("");
        searchBar.setVisibility(View.GONE);
        if (!query.isEmpty()) {
            query = "";
            render();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    // ---------------------------------------------------------------- rows

    private void onRowClick(int position) {
        if (position < 0 || position >= adapter.getCount()) return;
        LibraryAdapter.Row row = adapter.getItem(position);
        if (row.type == LibraryAdapter.TYPE_FOLDER) {
            openFolder = row.folder;
            render();
            list.setSelection(0);
        } else if (row.type == LibraryAdapter.TYPE_HISTORY) {
            playHistory(row.history, false);
        } else {
            playFromList(row.video, false);
        }
    }

    @Override
    public void onRowMore(View anchor, final LibraryAdapter.Row row) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 1, R.string.item_play);
        menu.getMenu().add(0, 2, 2, R.string.item_play_start);
        if (row.type == LibraryAdapter.TYPE_VIDEO) menu.getMenu().add(0, 3, 3, R.string.item_info);
        if (row.type == LibraryAdapter.TYPE_VIDEO
                || (row.type == LibraryAdapter.TYPE_HISTORY && isLocalUri(row.history.uri))) {
            menu.getMenu().add(0, 4, 4, R.string.item_share);
        }
        if (row.type == LibraryAdapter.TYPE_HISTORY) menu.getMenu().add(0, 5, 5, R.string.item_remove_recent);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                boolean video = row.type == LibraryAdapter.TYPE_VIDEO;
                switch (item.getItemId()) {
                    case 1:
                    case 2:
                        boolean fromStart = item.getItemId() == 2;
                        if (video) playFromList(row.video, fromStart);
                        else playHistory(row.history, fromStart);
                        return true;
                    case 3:
                        if (video) showVideoDetails(row.video);
                        return true;
                    case 4:
                        share(video ? row.video.uri : row.history.uri, video ? row.video.mime : null);
                        return true;
                    case 5:
                        prefs.removeHistory(row.history.uri);
                        render();
                        updateContinueBar();
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    private static boolean isLocalUri(String uri) {
        return uri != null && (uri.startsWith("content://") || uri.startsWith("file://"));
    }

    private void showVideoDetails(VideoItem v) {
        String res = v.width > 0 && v.height > 0 ? v.width + " × " + v.height + "  (" + v.qualityLabel() + ")" : "";
        String date = v.dateAddedSec > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(v.dateAddedSec * 1000L)) : "";
        Ui.showDetails(this, getString(R.string.info_title), new String[][]{
                {getString(R.string.info_name), v.displayName},
                {getString(R.string.info_folder), v.bucketName},
                {getString(R.string.info_resolution), res},
                {getString(R.string.info_duration), v.durationMs > 0 ? TimeFormat.format(v.durationMs) : ""},
                {getString(R.string.info_size), v.sizeBytes > 0 ? SizeFormat.format(v.sizeBytes) : ""},
                {getString(R.string.info_format), v.mime},
                {getString(R.string.info_date), date},
                {getString(R.string.info_path), v.path},
        });
    }

    private void share(String uri, String mime) {
        if (uri == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime != null && !mime.isEmpty() ? mime : "video/*");
        send.putExtra(Intent.EXTRA_STREAM, Uri.parse(uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Ui.safeStart(this, Intent.createChooser(send, getString(R.string.share_title)));
    }

    // ---------------------------------------------------------------- playing

    /** Plays a video with the rest of the visible list as the playlist (for next/previous). */
    private void playFromList(VideoItem target, boolean fromStart) {
        List<PlayItem> items = new ArrayList<>();
        int index = 0;
        for (LibraryAdapter.Row r : adapter.rows()) {
            if (r.type != LibraryAdapter.TYPE_VIDEO) continue;
            if (r.video == target) index = items.size();
            items.add(PlayItem.of(r.video));
        }
        if (items.isEmpty()) items.add(PlayItem.of(target));
        startPlayer(items, index, fromStart);
    }

    private void playHistory(HistoryEntry e, boolean fromStart) {
        List<PlayItem> items = new ArrayList<>(1);
        items.add(new PlayItem(e.uri, e.title, e.mediaId, null, null));
        startPlayer(items, 0, fromStart);
    }

    private void startPlayer(List<PlayItem> items, int index, boolean fromStart) {
        if (items.isEmpty()) return;
        index = Math.max(0, Math.min(index, items.size() - 1));
        PlayItem item = items.get(index);
        long token = PlaylistHolder.put(items);
        Intent i = new Intent(this, PlayerActivity.class);
        i.setData(Uri.parse(item.uri));
        i.putExtra(PlayerActivity.EXTRA_TOKEN, token);
        i.putExtra(PlayerActivity.EXTRA_INDEX, index);
        i.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
        i.putExtra(PlayerActivity.EXTRA_MEDIA_ID, item.mediaId);
        i.putExtra(PlayerActivity.EXTRA_PATH, item.path);
        i.putExtra(PlayerActivity.EXTRA_FROM_START, fromStart);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Ui.toast(this, R.string.cannot_open);
        }
    }

    // ---------------------------------------------------------------- open file / link

    private void showOpenMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 1, R.string.open_file);
        menu.getMenu().add(0, 2, 2, R.string.open_url);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) openFilePicker();
                else showUrlDialog();
                return true;
            }
        });
        menu.show();
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        try {
            startActivityForResult(i, REQ_OPEN_FILE);
        } catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.addCategory(Intent.CATEGORY_OPENABLE);
            g.setType("video/*");
            try {
                startActivityForResult(g, REQ_OPEN_FILE);
            } catch (ActivityNotFoundException e2) {
                Ui.toast(this, R.string.no_app_found);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // not every picker offers lasting access; it still works for now
        }
        String title = displayName(uri);
        List<PlayItem> items = new ArrayList<>(1);
        items.add(new PlayItem(uri.toString(), title, -1, null, data.getType()));
        startPlayer(items, 0, false);
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return VideoItem.stripExtension(n);
            }
        } catch (RuntimeException ignored) {
            // fall through to the uri text
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last != null ? VideoItem.stripExtension(last) : uri.toString();
    }

    private void showUrlDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_url, null);
        final EditText input = (EditText) v.findViewById(R.id.url_input);
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                    String text = clip.getItemAt(0).getText().toString().trim();
                    if (UrlCheck.looksLikeUrl(text)) {
                        input.setText(text);
                        input.setSelection(text.length());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // clipboard is optional
        }
        final AlertDialog d = Ui.dialog(this)
                .setTitle(R.string.url_title)
                .setView(v)
                .setPositiveButton(R.string.play, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View b) {
                        submitUrl(d, input);
                    }
                });
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView tv, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    submitUrl(d, input);
                    return true;
                }
                return false;
            }
        });
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void submitUrl(AlertDialog d, EditText input) {
        String url = UrlCheck.normalize(input.getText().toString());
        if (url == null) {
            input.setError(getString(R.string.url_invalid));
            return;
        }
        d.dismiss();
        List<PlayItem> items = new ArrayList<>(1);
        items.add(new PlayItem(url, UrlCheck.titleFromUrl(url), -1, null, null));
        startPlayer(items, 0, false);
    }

    // ---------------------------------------------------------------- menus & dialogs

    private void showSortDialog() {
        String[] labels = {getString(R.string.sort_date), getString(R.string.sort_name),
                getString(R.string.sort_size), getString(R.string.sort_duration)};
        Ui.dialog(this).setTitle(R.string.sort_title)
                .setSingleChoiceItems(labels, prefs.sortMode(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.setSortMode(which);
                        dialog.dismiss();
                        render();
                        list.setSelection(0);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 1, R.string.menu_refresh);
        menu.getMenu().add(0, 2, 2, R.string.menu_settings);
        menu.getMenu().add(0, 3, 3, R.string.menu_seek_step);
        menu.getMenu().add(0, 4, 4, R.string.menu_clear_history);
        menu.getMenu().add(0, 5, 5, R.string.menu_about);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        ThumbnailLoader.get(MainActivity.this).forgetFailures();
                        if (hasVideoPermission()) loadVideos();
                        else render();
                        return true;
                    case 2:
                        showSettings();
                        return true;
                    case 3:
                        showSeekStepDialog();
                        return true;
                    case 4:
                        confirmClearHistory();
                        return true;
                    case 5:
                        Ui.dialog(MainActivity.this).setTitle(R.string.app_name)
                                .setMessage(getString(R.string.about_text, NoorApp.versionName(MainActivity.this)))
                                .setPositiveButton(R.string.ok, null).show();
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    private void showSettings() {
        String[] labels = {getString(R.string.set_resume), getString(R.string.set_autoplay),
                getString(R.string.set_gestures), getString(R.string.set_auto_pip), getString(R.string.set_auto_rotate),
                getString(R.string.set_background), getString(R.string.set_volume_keys)};
        boolean[] checked = {prefs.resume(), prefs.autoplayNext(), prefs.gestures(), prefs.autoPip(), prefs.autoRotate(),
                prefs.backgroundPlay(), prefs.volumeKeysPause()};
        Ui.dialog(this).setTitle(R.string.settings_title)
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        switch (which) {
                            case 0: prefs.setResume(isChecked); break;
                            case 1: prefs.setAutoplayNext(isChecked); break;
                            case 2: prefs.setGestures(isChecked); break;
                            case 3: prefs.setAutoPip(isChecked); break;
                            case 4: prefs.setAutoRotate(isChecked); break;
                            case 5: prefs.setBackgroundPlay(isChecked); break;
                            case 6: prefs.setVolumeKeysPause(isChecked); break;
                            default: break;
                        }
                    }
                })
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showSeekStepDialog() {
        String[] labels = new String[SEEK_STEPS.length];
        int checked = 1;
        for (int i = 0; i < SEEK_STEPS.length; i++) {
            labels[i] = Ui.plural(this, R.plurals.seconds, SEEK_STEPS[i]);
            if (SEEK_STEPS[i] == prefs.seekStepSec()) checked = i;
        }
        Ui.dialog(this).setTitle(R.string.menu_seek_step)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.setSeekStepSec(SEEK_STEPS[which]);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearHistory() {
        Ui.dialog(this).setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.clearHistory();
                        render();
                        updateContinueBar();
                        Ui.toast(MainActivity.this, R.string.history_cleared);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCrashReportIfAny() {
        final String crash = NoorApp.readCrash(this);
        if (crash == null) return;
        String preview = crash.length() > 1500 ? crash.substring(0, 1500) + "…" : crash;
        Ui.dialog(this).setTitle(R.string.crash_title)
                .setMessage(getString(R.string.crash_text) + "\n\n" + preview)
                .setPositiveButton(R.string.crash_copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("Noor Player crash", crash));
                            Ui.toast(MainActivity.this, R.string.copied);
                        }
                        NoorApp.clearCrash(MainActivity.this);
                    }
                })
                .setNeutralButton(R.string.crash_share, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_TEXT, crash);
                        NoorApp.clearCrash(MainActivity.this);
                        Ui.safeStart(MainActivity.this, Intent.createChooser(send, getString(R.string.crash_share)));
                    }
                })
                .setNegativeButton(R.string.crash_dismiss, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        NoorApp.clearCrash(MainActivity.this);
                    }
                })
                .setCancelable(false)
                .show();
    }
}
