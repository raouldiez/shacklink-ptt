package org.shacklink.pttbridge;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Sonim XP-series PTT key -> stock Mumla's own talk command.
 *
 * Mumla (se.lublin.mumla) listens, while connected, for the broadcast
 *   se.lublin.mumla.action.TALK  with extra "status" = "on" | "off" | "toggle".
 * We send "on" on key-down and "off" on key-up, never "toggle", so a missed event
 * can't invert the state. A 3-minute failsafe sends "off" if a key-up is lost.
 */
public class PttReceiver extends BroadcastReceiver {
    static final String MUMLA = "se.lublin.mumla";
    static final String TALK = "se.lublin.mumla.action.TALK";
    static final long MAX_TX_MS = 3 * 60 * 1000;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction() == null ? "" : intent.getAction().toLowerCase();
        if (a.endsWith("_down") || a.endsWith(".down")) talk(ctx, true);
        else if (a.endsWith("_up") || a.endsWith(".up")) talk(ctx, false);
    }

    static void talk(Context ctx, boolean on) {
        Intent t = new Intent(TALK).setPackage(MUMLA).putExtra("status", on ? "on" : "off");
        ctx.sendBroadcast(t);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, new Intent(ctx, Failsafe.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (on) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + MAX_TX_MS, pi);
        else am.cancel(pi);
        MainActivity.note(ctx, on ? "PTT down -> Mumla TALK on" : "PTT up -> Mumla TALK off");
    }

    /** Fired by the alarm if the key was held (or its key-up lost) for MAX_TX_MS. */
    public static class Failsafe extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            ctx.sendBroadcast(new Intent(TALK).setPackage(MUMLA).putExtra("status", "off"));
            MainActivity.note(ctx, "3-minute limit -> Mumla TALK off");
        }
    }
}
