package org.shacklink.pttbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Floating PTT button drawn over other apps (for phones without a PTT key, e.g. a Z Fold).
 *
 * Two parts: a small grip on top to drag the button around, and the round button itself.
 * Only the round button keys (hold = Mumla TALK on, release = off), so moving it never transmits.
 * Runs as a foreground service so Android keeps the button on screen; its notification has "Hide".
 */
public class FloatService extends Service {
    static final String ACTION_HIDE = "org.shacklink.pttbridge.HIDE";
    private static final String CHANNEL = "float";
    private static final int NOTIF_ID = 7;

    private WindowManager wm;
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private TextView button;
    private boolean keyed = false;
    private MidiPtt midi;                                       // CTR2-MIDI PTT jack (USB MIDI)

    static boolean running = false;

    static void start(Context ctx) {
        Intent i = new Intent(ctx, FloatService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, FloatService.class));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        if (root == null) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                MainActivity.note(this, "Floating button needs 'Display over other apps'");
                stopSelf();
                return START_NOT_STICKY;
            }
            addButton();
        }
        running = true;
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Floating PTT button", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hide = PendingIntent.getService(this, 1,
                new Intent(this, FloatService.class).setAction(ACTION_HIDE), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("ShackLink PTT button is on")
                .setContentText("Hold the floating button to talk in Mumla")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Hide", hide).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIF_ID, n);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(colour);
        g.setStroke(dp(2), Color.argb(200, 255, 255, 255));
        return g;
    }

    private void paint() {
        button.setBackground(round(keyed ? Color.rgb(229, 57, 53) : Color.argb(220, 33, 38, 45)));
        button.setText(keyed ? "TX" : "PTT");
    }

    private void addButton() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView grip = new TextView(this);                    // drag here; never keys
        grip.setText("≡");                                // three-bar "grip" symbol
        grip.setTextColor(Color.WHITE);
        grip.setTextSize(18);
        grip.setGravity(Gravity.CENTER);
        GradientDrawable gb = new GradientDrawable();
        gb.setCornerRadius(dp(10));
        gb.setColor(Color.argb(170, 33, 38, 45));
        grip.setBackground(gb);
        root.addView(grip, new LinearLayout.LayoutParams(dp(56), dp(26)));

        button = new TextView(this);                           // hold to talk
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(76), dp(76));
        bl.topMargin = dp(4);
        root.addView(button, bl);
        paint();

        SharedPreferences p = getSharedPreferences("float", MODE_PRIVATE);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        lp.x = p.getInt("x", dm.widthPixels - dp(100));
        lp.y = p.getInt("y", dm.heightPixels / 2);

        grip.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float tx, ty;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = lp.x; sy = lp.y; tx = e.getRawX(); ty = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = sx + Math.round(e.getRawX() - tx);
                        lp.y = sy + Math.round(e.getRawY() - ty);
                        wm.updateViewLayout(root, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        p.edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        return true;
                }
                return false;
            }
        });

        button.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    keyed = true; paint();
                    PttReceiver.talk(this, true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_OUTSIDE:
                    if (keyed) { keyed = false; paint(); PttReceiver.talk(this, false); }
                    return true;
            }
            return true;
        });

        wm.addView(root, lp);
        MainActivity.note(this, "Floating PTT button shown");

        // A USB MIDI controller's PTT switch (CTR2-MIDI key jack) keys exactly like holding the button.
        midi = new MidiPtt(this, down -> {
            if (down == keyed) return;
            keyed = down; paint();
            PttReceiver.talk(this, down);
        });
        midi.start();
    }

    @Override
    public void onDestroy() {
        if (midi != null) { midi.stop(); midi = null; }
        if (keyed) { keyed = false; PttReceiver.talk(this, false); }   // never leave Mumla keyed
        if (root != null && wm != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        root = null;
        running = false;
        MainActivity.note(this, "Floating PTT button hidden");
        super.onDestroy();
    }
}
