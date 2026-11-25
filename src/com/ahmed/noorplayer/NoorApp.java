package com.ahmed.noorplayer;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Date;

/** Records any crash to a private file so the next launch can show it (and the user can send it on). */
public class NoorApp extends Application {
    static final String CRASH_FILE = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    writeCrash(t, e);
                } catch (Throwable ignored) {
                    // never let the crash logger itself fail the shutdown
                }
                if (previous != null) {
                    previous.uncaughtException(t, e);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    private void writeCrash(Thread t, Throwable e) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Noor Player " + versionName(this));
        pw.println("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Time: " + new Date());
        pw.println("Thread: " + t.getName());
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        FileOutputStream out = openFileOutput(CRASH_FILE, MODE_PRIVATE);
        try {
            out.write(sw.toString().getBytes(Charset.forName("UTF-8")));
        } finally {
            out.close();
        }
    }

    static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static String readCrash(Context c) {
        File f = new File(c.getFilesDir(), CRASH_FILE);
        if (!f.isFile()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            int len = (int) Math.min(f.length(), 64 * 1024);
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) {
                int r = in.read(buf, off, len - off);
                if (r < 0) break;
                off += r;
            }
            String s = new String(buf, 0, off, Charset.forName("UTF-8")).trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }

    static void clearCrash(Context c) {
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), CRASH_FILE).delete();
    }
}
