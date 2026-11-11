package org.shacklink.pttbridge;

import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * USB MIDI controller PTT (CTR2-MIDI's 3.5 mm key jack) -> Mumla TALK, like the Sonim key.
 *
 * The CTR2-MIDI sends note 20 "on" when the switch is pressed and note 20 "off" on release.
 * We listen to every MIDI device's output ports (Android lets ShackLink in Chrome keep its own
 * connection to the same controller) and only react to that one note. Unplugging the controller
 * while keyed un-keys at once; the 3-minute limit in PttReceiver still applies.
 */
class MidiPtt {
    static final int PTT_NOTE = 20;

    interface Listener { void onPtt(boolean down); }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MidiDevice> open = new ArrayList<>();
    private final List<MidiOutputPort> ports = new ArrayList<>();
    private MidiManager mm;
    private boolean down = false;

    private final MidiManager.DeviceCallback cb = new MidiManager.DeviceCallback() {
        @Override public void onDeviceAdded(MidiDeviceInfo info) { openDevice(info); }
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if (down) set(false);                        // pulled the plug while talking: un-key
            MainActivity.note(ctx, "MIDI device removed");
        }
    };

    MidiPtt(Context ctx, Listener l) { this.ctx = ctx.getApplicationContext(); this.listener = l; }

    void start() {
        mm = (MidiManager) ctx.getSystemService(Context.MIDI_SERVICE);
        if (mm == null) { MainActivity.note(ctx, "No MIDI support on this phone"); return; }
        mm.registerDeviceCallback(cb, main);
        for (MidiDeviceInfo info : mm.getDevices()) openDevice(info);
    }

    void stop() {
        if (down) set(false);
        if (mm != null) mm.unregisterDeviceCallback(cb);
        for (MidiOutputPort p : ports) try { p.close(); } catch (Exception ignored) {}
        for (MidiDevice d : open) try { d.close(); } catch (Exception ignored) {}
        ports.clear(); open.clear();
    }

    private void openDevice(MidiDeviceInfo info) {
        if (info.getOutputPortCount() == 0) return;     // nothing comes from it
        String name = String.valueOf(info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME));
        mm.openDevice(info, dev -> {
            if (dev == null) { MainActivity.note(ctx, "Couldn't open MIDI " + name); return; }
            open.add(dev);
            for (int i = 0; i < info.getOutputPortCount(); i++) {
                MidiOutputPort p = dev.openOutputPort(i);
                if (p == null) continue;
                p.connect(new Parser());
                ports.add(p);
            }
            MainActivity.note(ctx, "MIDI PTT listening on " + name + " (note " + PTT_NOTE + ")");
        }, main);
    }

    private void set(boolean d) {
        if (d == down) return;
        down = d;
        main.post(() -> listener.onPtt(d));
    }

    /** Minimal MIDI byte parser: handles several messages per packet and running status. */
    private class Parser extends MidiReceiver {
        int status = 0, need = 0, have = 0;
        final int[] data = new int[2];

        @Override
        public void onSend(byte[] msg, int off, int count, long ts) {
            for (int i = off; i < off + count; i++) {
                int b = msg[i] & 0xff;
                if (b >= 0xf8) continue;                 // real-time bytes (clock etc.) can appear anywhere
                if (b >= 0x80) {                         // new status byte
                    status = b;
                    int hi = b & 0xf0;
                    need = (hi == 0xc0 || hi == 0xd0) ? 1 : (b >= 0xf0 ? 0 : 2);
                    have = 0;
                    continue;
                }
                if (need == 0) continue;
                data[have++] = b;
                if (have < need) continue;
                have = 0;                                // complete message (running status keeps 'status')
                int hi = status & 0xf0;
                if (data[0] == PTT_NOTE && (hi == 0x90 || hi == 0x80))
                    set(hi == 0x90 && data[1] > 0);      // note on vel>0 = press; note off / vel 0 = release
            }
        }
    }
}
