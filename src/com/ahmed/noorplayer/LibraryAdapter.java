package com.ahmed.noorplayer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ahmed.noorplayer.core.FolderInfo;
import com.ahmed.noorplayer.core.HistoryEntry;
import com.ahmed.noorplayer.core.ResumePolicy;
import com.ahmed.noorplayer.core.SizeFormat;
import com.ahmed.noorplayer.core.TimeFormat;
import com.ahmed.noorplayer.core.VideoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rows of the library list: videos, folders and recently watched entries. */
final class LibraryAdapter extends BaseAdapter {
    static final int TYPE_VIDEO = 0;
    static final int TYPE_FOLDER = 1;
    static final int TYPE_HISTORY = 2;

    static final class Row {
        final int type;
        final VideoItem video;
        final FolderInfo folder;
        final HistoryEntry history;

        private Row(int type, VideoItem v, FolderInfo f, HistoryEntry h) {
            this.type = type;
            this.video = v;
            this.folder = f;
            this.history = h;
        }

        static Row of(VideoItem v) { return new Row(TYPE_VIDEO, v, null, null); }
        static Row of(FolderInfo f) { return new Row(TYPE_FOLDER, null, f, null); }
        static Row of(HistoryEntry h) { return new Row(TYPE_HISTORY, null, null, h); }
    }

    interface Callbacks {
        void onRowMore(View anchor, Row row);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final ThumbnailLoader thumbs;
    private final Callbacks callbacks;
    private List<Row> rows = new ArrayList<>();
    private Map<String, HistoryEntry> history = Collections.emptyMap();
    private boolean selectionMode;
    private Set<Long> selected = new HashSet<>();

    LibraryAdapter(Context context, ThumbnailLoader thumbs, Callbacks callbacks) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.thumbs = thumbs;
        this.callbacks = callbacks;
    }

    void setRows(List<Row> newRows, Map<String, HistoryEntry> historyMap) {
        rows = newRows != null ? newRows : new ArrayList<Row>();
        history = historyMap != null ? historyMap : Collections.<String, HistoryEntry>emptyMap();
        notifyDataSetChanged();
    }

    List<Row> rows() {
        return rows;
    }

    /** Multi-select for deleting: rows show a check circle and selected ones are highlighted. */
    void setSelection(boolean mode, Set<Long> selectedIds) {
        selectionMode = mode;
        selected = selectedIds != null ? selectedIds : new HashSet<Long>();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type == TYPE_FOLDER ? 1 : 0;
    }

    private static final class Holder {
        View root;
        Drawable rootBackground;
        ImageView check;
        ImageView thumb;
        TextView title;
        TextView meta;
        TextView badge;
        ProgressBar watched;
        ImageButton more;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        boolean folder = row.type == TYPE_FOLDER;
        View v = convertView;
        Holder h;
        if (v == null) {
            v = inflater.inflate(folder ? R.layout.item_folder : R.layout.item_video, parent, false);
            h = new Holder();
            h.root = v;
            h.rootBackground = v.getBackground();
            h.thumb = (ImageView) v.findViewById(R.id.thumb);
            h.title = (TextView) v.findViewById(R.id.item_title);
            h.meta = (TextView) v.findViewById(R.id.item_meta);
            View frame = v.findViewById(R.id.thumb_frame);
            frame.setClipToOutline(true);
            if (!folder) {
                h.badge = (TextView) v.findViewById(R.id.duration_badge);
                h.watched = (ProgressBar) v.findViewById(R.id.watched_bar);
                h.more = (ImageButton) v.findViewById(R.id.item_more);
                h.check = (ImageView) v.findViewById(R.id.select_check);
                h.more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View b) {
                        Object tag = b.getTag();
                        if (tag instanceof Row) callbacks.onRowMore(b, (Row) tag);
                    }
                });
            }
            v.setTag(h);
        } else {
            h = (Holder) v.getTag();
        }
        if (row.type == TYPE_FOLDER) bindFolder(h, row.folder);
        else if (row.type == TYPE_HISTORY) bindHistory(h, row);
        else bindVideo(h, row);
        return v;
    }

    private void bindVideo(Holder h, Row row) {
        VideoItem v = row.video;
        h.title.setText(v.title);
        h.meta.setText(join(v.qualityLabel(), v.sizeBytes > 0 ? SizeFormat.format(v.sizeBytes) : "", v.bucketName));
        setBadge(h, v.durationMs);
        HistoryEntry e = history.get(v.uri);
        setWatched(h, e != null ? ResumePolicy.watchedFraction(e.positionMs, e.durationMs > 0 ? e.durationMs : v.durationMs, e.finished) : 0f);
        h.more.setTag(row);
        showSelection(h, selectionMode, selectionMode && selected.contains(v.id));
        thumbs.load(h.thumb, ThumbnailLoader.keyForMedia(v.id), v.id, v.uri);
    }

    private void showSelection(Holder h, boolean mode, boolean isSelected) {
        h.check.setVisibility(mode ? View.VISIBLE : View.GONE);
        h.more.setVisibility(mode ? View.INVISIBLE : View.VISIBLE);
        if (mode) {
            h.check.setImageResource(isSelected ? R.drawable.ic_check_circle : R.drawable.ic_circle_outline);
            h.check.setImageTintList(ColorStateList.valueOf(isSelected ? context.getColor(R.color.accent) : 0xFFFFFFFF));
        }
        if (isSelected) h.root.setBackgroundColor(0x33FFB300);
        else h.root.setBackground(h.rootBackground);
    }

    private void bindHistory(Holder h, Row row) {
        HistoryEntry e = row.history;
        h.title.setText(e.title != null && !e.title.isEmpty() ? e.title : e.uri);
        String progress = e.durationMs > 0
                ? TimeFormat.format(e.finished ? e.durationMs : e.positionMs) + " / " + TimeFormat.format(e.durationMs)
                : TimeFormat.format(e.positionMs);
        CharSequence when = DateUtils.getRelativeTimeSpanString(e.lastPlayedMs, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
        h.meta.setText(join(progress, when.toString(), null));
        setBadge(h, e.durationMs);
        setWatched(h, ResumePolicy.watchedFraction(e.positionMs, e.durationMs, e.finished));
        h.more.setTag(row);
        showSelection(h, false, false);
        if (e.mediaId > 0) {
            thumbs.load(h.thumb, ThumbnailLoader.keyForMedia(e.mediaId), e.mediaId, e.uri);
        } else if (e.uri.startsWith("content://") || e.uri.startsWith("file://")) {
            thumbs.load(h.thumb, ThumbnailLoader.keyForUri(e.uri), -1, e.uri);
        } else {
            thumbs.load(h.thumb, null, -1, null);
        }
    }

    private void bindFolder(Holder h, FolderInfo f) {
        h.title.setText(f.name);
        String count = Ui.plural(context, R.plurals.video_count, f.count);
        h.meta.setText(join(count, f.totalSize > 0 ? SizeFormat.format(f.totalSize) : "", null));
        VideoItem cover = f.cover;
        if (cover != null) {
            thumbs.load(h.thumb, ThumbnailLoader.keyForMedia(cover.id), cover.id, cover.uri);
        } else {
            thumbs.load(h.thumb, null, -1, null);
        }
    }

    private static void setBadge(Holder h, long durationMs) {
        if (durationMs > 0) {
            h.badge.setText(TimeFormat.format(durationMs));
            h.badge.setVisibility(View.VISIBLE);
        } else {
            h.badge.setVisibility(View.GONE);
        }
    }

    private static void setWatched(Holder h, float fraction) {
        if (fraction > 0.01f) {
            h.watched.setProgress(Math.round(fraction * 1000));
            h.watched.setVisibility(View.VISIBLE);
        } else {
            h.watched.setVisibility(View.GONE);
        }
    }

    private static String join(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        for (String s : new String[]{a, b, c}) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(s);
        }
        return sb.toString();
    }
}
