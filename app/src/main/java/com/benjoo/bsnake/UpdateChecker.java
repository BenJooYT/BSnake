package com.benjoo.bsnake;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

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
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(intent);
    }
}