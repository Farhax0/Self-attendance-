package com.selfattendance;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Methods the web page calls through window.Android. */
public class Bridge {
    private final Activity activity;
    private final WebView web;

    Bridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    @JavascriptInterface
    public void setReminder(boolean on, String shiftIn) {
        SharedPreferences p = Reminder.prefs(activity);
        p.edit().putBoolean("on", on).putString("time", shiftIn).apply();
        if (!on) Reminder.cancel(activity);
        Reminder.schedule(activity);
    }

    @JavascriptInterface
    public void checkedIn(String dateKey, String time) {
        SharedPreferences p = Reminder.prefs(activity);
        p.edit().putString("inDate", dateKey).apply();
        if (p.getBoolean("on", false)) {
            Reminder.show(activity, "Checked in at " + time, "Working now · tap to Check Out when you leave");
        }
    }

    @JavascriptInterface
    public void checkedOut(String dateKey) {
        Reminder.prefs(activity).edit().putString("inDate", dateKey).apply();
        Reminder.cancel(activity);
    }

    @JavascriptInterface
    public void saveFile(String name, String mime, String base64) {
        byte[] data = Base64.decode(base64, Base64.DEFAULT);
        try {
            Uri uri = null;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = activity.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (OutputStream os = cr.openOutputStream(uri)) { os.write(data); }
            } else {
                if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    activity.runOnUiThread(() -> activity.requestPermissions(
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2));
                    toast("Allow storage access, then tap export again");
                    return;
                }
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(data); }
            }
            toast("Saved to Downloads/" + name);
            if (uri != null) {
                Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try { activity.startActivity(Intent.createChooser(view, "Open " + name)); } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    private void toast(String msg) {
        String safe = msg.replace("\\", "\\\\").replace("'", "\\'");
        web.post(() -> web.evaluateJavascript("window.appToast && appToast('" + safe + "')", null));
    }
}
