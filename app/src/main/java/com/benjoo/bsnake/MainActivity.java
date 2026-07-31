package com.benjoo.bsnake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private static final String UPDATE_URL =
            "https://raw.githubusercontent.com/BenJooYT/BSnake/main/version.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setNavigationBarColor(Color.BLACK);
            getWindow().setStatusBarColor(Color.BLACK);
        }

        FrameLayout root = new FrameLayout(this);
        GameView gameView = new GameView(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        EditText keyboardInput = new EditText(this);
        keyboardInput.setSingleLine(true);
        keyboardInput.setTextColor(Color.TRANSPARENT);
        keyboardInput.setCursorVisible(false);
        keyboardInput.setBackgroundColor(Color.TRANSPARENT);
        keyboardInput.setAlpha(0.02f);
        FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(2, 2);
        root.addView(keyboardInput, inputParams);
        gameView.setKeyboardInput(keyboardInput);
        setContentView(root);

        checkForUpdate();
    }

    @SuppressWarnings("deprecation")
    private void checkForUpdate() {
        int versionCode;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        UpdateChecker.check(versionCode, UPDATE_URL, info -> {
            if (info.needsReinstall) {
                showReinstallDialog(info);
            } else {
                showUpdateDialog(info);
            }
        });
    }

    private void showUpdateDialog(UpdateChecker.UpdateInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("Update Available")
                .setMessage("Version " + info.versionName + "\n\n" + info.changelog)
                .setPositiveButton("Update", (dialog, which) ->
                        UpdateChecker.openDownloadUrl(this, info.downloadUrl))
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private void showReinstallDialog(UpdateChecker.UpdateInfo info) {
        String message = "Version " + info.versionName
                + " requires a reinstall.\n\n"
                + info.changelog + "\n\n"
                + "Please uninstall the current version and install the new APK.";
        new AlertDialog.Builder(this)
                .setTitle("Reinstall Required")
                .setMessage(message)
                .setPositiveButton("Download New Version", (dialog, which) ->
                        UpdateChecker.openDownloadUrl(this, info.downloadUrl))
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }
}