package org.shacklink.pttbridge;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Bluetooth earbud tap (Galaxy Buds, AirPods...) -> Mumla TALK, as a TOGGLE.
 *
 * Earbuds only send "play/pause" when tapped, never a press-and-release, so this can't be
 * hold-to-talk: one tap keys, the next tap un-keys. A double tap (next track) always un-keys,
 * as a safe "stop" that can never key. The 3-minute limit in PttReceiver still applies.
 *
 * Android sends media buttons to the media session of the app that most recently played audio
 * and has a session. Mumla has none, so we play a moment of silence when we start, which makes
 * us that app. Playing music in another app (Tidal, Roon...) takes the buttons back until the
 * floating button is hidden and shown again.
 */
class BudsPtt {
    interface Listener {
        void onToggle();      // single tap
        void onForceOff();    // double tap
    }

    private static final long DEBOUNCE_MS = 400;

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private long lastTap = 0;

    BudsPtt(Context ctx, Listener l) { this.ctx = ctx.getApplicationContext(); this.listener = l; }

    void start() {
        session = new MediaSession(ctx, "ShackLinkPTT");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent k = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (k == null || k.getAction() != KeyEvent.ACTION_DOWN || k.getRepeatCount() > 0) return true;
                MainActivity.note(ctx, "Earbud key: " + KeyEvent.keyCodeToString(k.getKeyCode()));
                switch (k.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastTap < DEBOUNCE_MS) return true;   // one tap can arrive as PLAY then PAUSE
                        lastTap = now;
                        listener.onToggle();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        listener.onForceOff();
                        return true;
                }
                return true;                                  // swallow other keys while we own the buttons
            }
        }, main);
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .build());
        session.setActive(true);
        claimButtons();
        MainActivity.note(ctx, "Earbud tap PTT on (tap = TX on/off, double tap = TX off)");
    }

    void stop() {
        if (session != null) { session.setActive(false); session.release(); session = null; }
    }

    /** Plays ~0.3 s of silence so Android routes media buttons to our session. */
    private void claimButtons() {
        new Thread(() -> {
            int rate = 8000, samples = rate * 3 / 10;
            AudioTrack t = null;
            try {
                t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(samples * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                t.write(new short[samples], 0, samples);
                t.play();
                Thread.sleep(500);
            } catch (Exception e) {
                MainActivity.note(ctx, "Earbud tap PTT: couldn't claim buttons (" + e.getMessage() + ")");
            } finally {
                if (t != null) try { t.stop(); t.release(); } catch (Exception ignored) {}
            }
        }).start();
    }
}
