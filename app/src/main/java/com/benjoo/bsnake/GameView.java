package com.benjoo.bsnake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;

import java.util.Locale;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback, InputHandler.GameActions {

    Thread thread;
    SurfaceHolder holder;
    volatile boolean running = false;

    GameState state;
    PersistenceManager persistence;
    SnakeEngine engine;
    GameRenderer renderer;
    InputHandler input;

    EditText keyboardInput;

    public GameView(Context context) {
        super(context);
        initComponents(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initComponents(context);
        init();
    }

    private void initComponents(Context context) {
        state = new GameState();
        persistence = new PersistenceManager(context);
        engine = new SnakeEngine(state, persistence);
        renderer = new GameRenderer(state, persistence);
        input = new InputHandler(state, engine, this);
        persistence.loadColors(state);
    }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        state.screenW = getWidth();
        state.screenH = getHeight();
        state.configureBoard();
        state.layoutButtons();
        state.currentState = GameState.State.MENU;
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        long lastTick = System.currentTimeMillis();
        while (running) {
            long now = System.currentTimeMillis();
            if (state.currentState == GameState.State.PLAYING && now - lastTick >= state.tickDelay) {
                engine.update();
                lastTick = now;
                now = System.currentTimeMillis();
            } else if (state.currentState != GameState.State.PLAYING) {
                lastTick = now;
            }
            float t = Math.min(1f, (now - lastTick) / (float) state.tickDelay);
            Canvas canvas = null;
            if (holder.getSurface().isValid()) {
                canvas = holder.lockCanvas();
            }
            renderer.draw(canvas, t);
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
            try { Thread.sleep(8); } catch (InterruptedException e) { }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        state.screenW = width;
        state.screenH = height;
        state.configureBoard();
        state.layoutButtons();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException e) { }
    }

    // -------------------- input --------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return input.onTouchEvent(event);
    }

    // -------------------- GameActions implementation --------------------

    @Override
    public void startNewGame() {
        engine.resetGame();
        state.currentState = GameState.State.PLAYING;
    }

    @Override
    public void cycleSpeed() {
        state.speedIndex = (state.speedIndex + 1) % state.speedLabels.length;
        state.tickDelay = state.speedDelays[state.speedIndex];
    }

    @Override
    public void openSettingsScreen() {
        state.headHex = String.format(Locale.US, "#%06X", state.headColor & 0xFFFFFF);
        state.bodyHex = String.format(Locale.US, "#%06X", state.bodyColor & 0xFFFFFF);
        state.editingColor = -1;
        state.currentState = GameState.State.SETTINGS;
    }

    @Override
    public void applyColors() {
        Integer newHeadColor = persistence.parseHexColor(state.headHex);
        Integer newBodyColor = persistence.parseHexColor(state.bodyHex);
        if (newHeadColor == null || newBodyColor == null) return;
        state.headColor = newHeadColor;
        state.bodyColor = newBodyColor;
        persistence.saveColors(state.headColor, state.bodyColor);
        hideKeyboardInternal();
        state.currentState = GameState.State.MENU;
    }

    @Override
    public void editColorField(int index) {
        if (keyboardInput == null) return;
        state.editingColor = index;
        keyboardInput.setText(index == 0 ? state.headHex : state.bodyHex);
        keyboardInput.setSelection(keyboardInput.length());
        keyboardInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void dismissKeyboard() {
        hideKeyboardInternal();
    }

    @Override
    public void exitApp() {
        Context ctx = getContext();
        if (ctx instanceof Activity) {
            ((Activity) ctx).finish();
        }
    }

    private void hideKeyboardInternal() {
        if (keyboardInput == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(keyboardInput.getWindowToken(), 0);
        keyboardInput.clearFocus();
        state.editingColor = -1;
    }

    // -------------------- keyboard setup --------------------

    public void setKeyboardInput(EditText input) {
        keyboardInput = input;
        keyboardInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (state.editingColor == 0) state.headHex = s.toString();
                if (state.editingColor == 1) state.bodyHex = s.toString();
                invalidate();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    EditText makeColorInput(Activity activity, String hint, int color) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
        return input;
    }
}
