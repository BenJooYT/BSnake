package com.benjoo.bsnake;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    public interface UpdateCheckCallback {
        void onUpdateAvailable(UpdateInfo info);
    }

    public static class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String downloadUrl;
        public String changelog;
        public boolean needsReinstall;
    }

    public static void check(int currentVersionCode, String checkUrl, UpdateCheckCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(checkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    conn.disconnect();
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                UpdateInfo info = new UpdateInfo();
                info.versionCode = json.getInt("versionCode");
                info.versionName = json.getString("versionName");
                info.downloadUrl = json.getString("downloadUrl");
                info.changelog = json.getString("changelog");
                info.needsReinstall = json.getBoolean("needsReinstall");

                if (info.versionCode > currentVersionCode) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onUpdateAvailable(info));
                }

            } catch (Exception ignored) {
            }
        }).start();
    }

    public static void openDownloadUrl(Context context, String url) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;

        String fileName = "BSnake-update.apk";
        long downloadId = dm.enqueue(new DownloadManager.Request(Uri.parse(url))
                .setTitle("BSnake Update")
                .setDescription("Downloading update...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive"));

        Context appContext = context.getApplicationContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;

                try {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor c = dm.query(query);
                    if (c != null && c.moveToFirst()) {
                        int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            Uri fileUri = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                String localUri = c.getString(c.getColumnIndex("local_uri"));
                                if (localUri != null) fileUri = Uri.parse(localUri);
                            }
                            if (fileUri == null) {
                                String filePath = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
                                if (filePath != null) fileUri = Uri.fromFile(new java.io.File(filePath));
                            }
                            if (fileUri != null) {
                                Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                                install.setData(fileUri);
                                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                ctx.startActivity(install);
                            }
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    Log.e("UpdateChecker", "auto-install failed", e);
                }
                try {
                    appContext.unregisterReceiver(this);
                } catch (IllegalArgumentException ignored) {}
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }
}