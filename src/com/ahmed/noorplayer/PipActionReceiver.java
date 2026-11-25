package com.ahmed.noorplayer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives the play/pause/skip buttons shown on the floating picture-in-picture window. */
public class PipActionReceiver extends BroadcastReceiver {
    static final String ACTION = "com.ahmed.noorplayer.PIP_ACTION";
    static final String EXTRA_CMD = "cmd";
    static final int CMD_REWIND = 1;
    static final int CMD_TOGGLE = 2;
    static final int CMD_FORWARD = 3;

    static PendingIntent pending(Context c, int cmd) {
        Intent i = new Intent(c, PipActionReceiver.class);
        i.setAction(ACTION + "." + cmd);
        i.putExtra(EXTRA_CMD, cmd);
        return PendingIntent.getBroadcast(c, cmd, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        PlayerActivity.onPipCommand(intent.getIntExtra(EXTRA_CMD, 0));
    }
}
