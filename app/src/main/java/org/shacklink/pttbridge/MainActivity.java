package org.shacklink.pttbridge;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Status + a hold-to-talk test button. Opening it once after install is required. */
public class MainActivity extends Activity {
    private TextView log;

    static void note(Context ctx, String msg) {
        SharedPreferences p = ctx.getSharedPreferences("log", MODE_PRIVATE);
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        String old = p.getString("lines", "");
        String all = line + "\n" + old;
        if (all.length() > 2000) all = all.substring(0, 2000);
        p.edit().putString("lines", all).apply();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(48, 96, 48, 48);
        TextView help = new TextView(this);
        help.setTextSize(16);
        help.setText("ShackLink PTT bridge\n\n"
                + "1. Sonim Settings > Programmable Keys > Select PTT Key App > ShackLink PTT\n"
                + "2. Mumla: connect, transmit mode Push To Talk\n"
                + "3. Hold the PTT key: Mumla transmits in the background.\n\n"
                + "Releasing the key un-keys; a 3-minute limit un-keys if a release is ever missed.\n");
        Button test = new Button(this);
        test.setText("Hold to test (keys Mumla)");
        test.setOnTouchListener((btn, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) PttReceiver.talk(this, true);
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                PttReceiver.talk(this, false);
                refresh();
            }
            return true;
        });
        log = new TextView(this);
        log.setTextSize(13);
        log.setPadding(0, 32, 0, 0);
        v.addView(help);
        v.addView(test);
        v.addView(log);
        setContentView(v);
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        log.setText("Recent:\n" + getSharedPreferences("log", MODE_PRIVATE).getString("lines", "(nothing yet)"));
    }
}
