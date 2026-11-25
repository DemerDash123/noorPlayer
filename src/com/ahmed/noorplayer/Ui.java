package com.ahmed.noorplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Toast;

import java.util.Locale;

/** Small shared UI helpers. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, R.style.DialogTheme);
    }

    static void toast(Context c, int res) {
        Toast.makeText(c, res, Toast.LENGTH_SHORT).show();
    }

    static void toast(Context c, CharSequence text) {
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
    }

    /** Plural text with the number shown in Western digits, e.g. "12 videos". */
    static String plural(Context c, int pluralsId, int count) {
        return c.getResources().getQuantityString(pluralsId, count, String.valueOf(count));
    }

    static String percent(float fraction) {
        return String.format(Locale.US, "%d%%", Math.round(fraction * 100f));
    }

    /** 1.0 -> "1x", 1.25 -> "1.25x", 0.5 -> "0.5x". */
    static String speedLabel(float speed) {
        String s = String.format(Locale.US, "%.2f", speed);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "x";
    }

    static boolean safeStart(Activity a, Intent intent) {
        try {
            a.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            toast(a, R.string.no_app_found);
            return false;
        }
    }

    /** Shows label/value pairs as a simple details dialog. Rows with an empty value are skipped. */
    static void showDetails(Activity a, CharSequence title, String[][] rows) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int accent = a.getColor(R.color.accent);
        for (String[] r : rows) {
            if (r == null || r.length < 2 || r[1] == null || r[1].trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            int start = sb.length();
            sb.append(r[0]);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(accent), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append('\n').append(r[1]);
        }
        dialog(a).setTitle(title).setMessage(sb).setPositiveButton(R.string.ok, null).show();
    }
}
