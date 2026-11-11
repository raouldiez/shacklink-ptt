package org.shacklink.pttbridge;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-update from the repo's "latest" GitHub release, no browser involved.
 *
 * The release notes carry "versionCode=N" (written by the CI build). If N is newer than this app,
 * the APK is streamed straight into Android's PackageInstaller and Android shows its normal
 * "Do you want to update this app?" prompt. First time only, Android asks to allow this app to
 * install updates ("Install unknown apps").
 */
class Updater {
    static final String LATEST = "https://api.github.com/repos/raouldiez/shacklink-ptt/releases/latest";

    interface Status { void show(String msg, boolean newer); }

    private static final Handler main = new Handler(Looper.getMainLooper());

    static long myVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) { return 0; }
    }

    /** Ask GitHub what the latest build is. install=false only reports; install=true also updates. */
    static void check(Activity a, boolean install, Status st) {
        new Thread(() -> {
            try {
                String json = get(LATEST);
                Matcher vc = Pattern.compile("versionCode=(\\d+)").matcher(json);
                Matcher url = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").matcher(json);
                if (!vc.find() || !url.find()) { post(st, "Couldn't read the latest release", false); return; }
                long latest = Long.parseLong(vc.group(1)), mine = myVersion(a);
                if (latest <= mine) { post(st, "Up to date (build " + mine + ")", false); return; }
                if (!install) { post(st, "Update available: build " + latest + " (you have " + mine + ")", true); return; }
                if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
                    post(st, "Allow \"Install unknown apps\" for ShackLink PTT, then tap Update again", true);
                    main.post(() -> a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + a.getPackageName()))));
                    return;
                }
                post(st, "Downloading build " + latest + "…", true);
                installFrom(a, url.group(1));
                post(st, "Downloaded - confirm the update in Android's prompt", true);
            } catch (Exception e) {
                post(st, "Update failed: " + e.getMessage(), false);
                MainActivity.note(a, "Update failed: " + e);
            }
        }).start();
    }

    private static void post(Status st, String m, boolean newer) { main.post(() -> st.show(m, newer)); }

    private static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setInstanceFollowRedirects(true);              // github.com -> objects.githubusercontent.com
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "ShackLink-PTT");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        return c;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = open(u);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private static void installFrom(Context ctx, String apkUrl) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(ctx.getPackageName());
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            HttpURLConnection c = open(apkUrl);
            try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("update.apk", 0, -1)) {
                byte[] b = new byte[65536]; int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                s.fsync(out);
            } finally { c.disconnect(); }
            Intent done = new Intent(ctx, Result.class);
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                                                    : PendingIntent.FLAG_UPDATE_CURRENT;
            s.commit(PendingIntent.getBroadcast(ctx, id, done, flags).getIntentSender());
        }
    }

    /** PackageInstaller's answer: show Android's confirmation screen, or log the result. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(confirm); }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                MainActivity.note(ctx, "Updated to the latest build");
            } else {
                MainActivity.note(ctx, "Update not installed: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }
}
