package com.benjoo.bsnake;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // The game UI is rendered directly by GameView; no XML layout is needed.
        FrameLayout root = new FrameLayout(this);
        GameView gameView = new GameView(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Invisible input used only to bring up the keyboard. The visible fields
        // are drawn by GameView so the settings screen matches the game UI.
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
    }
}
