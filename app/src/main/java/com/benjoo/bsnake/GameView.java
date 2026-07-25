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

// Thin coordinator that owns all subsystems and wires them together.
// Responsibilities: SurfaceView lifecycle, the game loop thread, keyboard
// input bridge, and implementation of InputHandler.GameActions.
public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback, InputHandler.GameActions {

    // Game-loop infrastructure
    Thread thread;
    SurfaceHolder holder;
    volatile boolean running = false;

    // Subsystem references
    GameState state;
    PersistenceManager persistence;
    SnakeEngine engine;
    GameRenderer renderer;
    InputHandler input;

    // Invisible EditText used to capture hex-color keyboard input on the settings screen
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

    // Create all subsystems in dependency order: State -> Persistence -> Engine -> Renderer -> Input
    private void initComponents(Context context) {
        state = new GameState();
        persistence = new PersistenceManager(context);
        engine = new SnakeEngine(state, persistence);
        renderer = new GameRenderer(state, persistence);
        input = new InputHandler(state, engine, this);
        persistence.loadColors(state);
    }

    // Register the surface callback
    private void init() {
        holder = getHolder();
        holder.addCallback(this);
    }

    // Measure screen, configure board layout, show the menu, and start the game-loop thread
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

    // Fixed-tick game loop with interpolated rendering between ticks.
    // While PLAYING, engine.update() runs every tickDelay ms; other states
    // reset the tick timer each frame. Rendering runs every iteration at
    // up to ~120 FPS (8 ms sleep), using interpolation fraction t to
    // smoothly draw between the previous and current logical tick.
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

    // Recalculate layout when the surface changes size (e.g. orientation)
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        state.screenW = width;
        state.screenH = height;
        state.configureBoard();
        state.layoutButtons();
    }

    // Stop the game loop and wait for the thread to finish
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException e) { }
    }

    // Delegate all touch events to InputHandler
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return input.onTouchEvent(event);
    }

    // ----- InputHandler.GameActions implementation -----

    // Reset the game engine and transition to the PLAYING state.
    // When dev mode is active, parse the entered start score first and dismiss the keyboard.
    @Override
    public void startNewGame() {
        if (state.devMode) {
            try {
                state.devStartScore = Integer.parseInt(state.devScoreText);
                if (state.devStartScore < 0) state.devStartScore = 0;
            } catch (NumberFormatException e) {
                state.devStartScore = 0;
            }
            hideKeyboardInternal();
        }
        engine.resetGame();
        state.currentState = GameState.State.PLAYING;
    }

    // Rotate through EASY / NORMAL / HARD speed tiers
    @Override
    public void cycleSpeed() {
        state.speedIndex = (state.speedIndex + 1) % state.speedLabels.length;
        state.tickDelay = state.speedDelays[state.speedIndex];
    }

    // Format current colors as hex strings and open the settings screen
    @Override
    public void openSettingsScreen() {
        state.headHex = String.format(Locale.US, "#%06X", state.headColor & 0xFFFFFF);
        state.bodyHex = String.format(Locale.US, "#%06X", state.bodyColor & 0xFFFFFF);
        state.editingColor = -1;
        state.currentState = GameState.State.SETTINGS;
    }

    // Validate hex colors, persist them, dismiss keyboard, and return to menu.
    // If either color is invalid, nothing happens (the user keeps editing).
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

    // Show the soft keyboard for editing the head (index=0) or body (index=1) hex color
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

    // Finish the hosting Activity (exits the app)
    @Override
    public void exitApp() {
        Context ctx = getContext();
        if (ctx instanceof Activity) {
            ((Activity) ctx).finish();
        }
    }

    // Cycle between camera modes and reconfigure the board layout.
    @Override
    public void toggleCameraMode() {
        state.cameraMode = state.cameraMode == GameState.CameraMode.CLASSIC_ZOOM
                ? GameState.CameraMode.FULL_PLAY_AREA
                : GameState.CameraMode.CLASSIC_ZOOM;
        state.configureBoard();
        state.layoutButtons();
    }

    // Toggle developer mode on/off.  When activated, show the keyboard for
    // entering a starting score; when deactivated, dismiss the keyboard.
    @Override
    public void toggleDevMode() {
        state.devMode = !state.devMode;
        if (state.devMode) {
            state.devScoreText = "0";
            showDevScoreInput();
        } else {
            hideKeyboardInternal();
        }
    }

    // Show the soft keyboard for editing the dev-mode starting score (numbers only)
    @Override
    public void showDevScoreInput() {
        if (keyboardInput == null) return;
        state.editingDevScore = true;
        state.editingColor = -1;
        keyboardInput.setText(state.devScoreText);
        keyboardInput.setSelection(keyboardInput.length());
        keyboardInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        keyboardInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT);
    }

    // Common keyboard-dismissal logic used by applyColors, BACK, and dev-mode toggle
    private void hideKeyboardInternal() {
        if (keyboardInput == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(keyboardInput.getWindowToken(), 0);
        keyboardInput.clearFocus();
        state.editingColor = -1;
        state.editingDevScore = false;
    }

    // ----- Keyboard setup -----

    // Attach the invisible EditText created by MainActivity and wire a TextWatcher
    // that syncs typed hex strings back to state.headHex / state.bodyHex in real time.
    public void setKeyboardInput(EditText input) {
        keyboardInput = input;
        keyboardInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (state.editingDevScore) {
                    state.devScoreText = s.toString();
                } else if (state.editingColor == 0) {
                    state.headHex = s.toString();
                    Integer c = persistence.parseHexColor(state.headHex);
                    if (c != null) state.headColor = c;
                } else if (state.editingColor == 1) {
                    state.bodyHex = s.toString();
                    Integer c = persistence.parseHexColor(state.bodyHex);
                    if (c != null) state.bodyColor = c;
                }
                invalidate();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    // Factory helper to create a transparent single-line EditText for hex input
    EditText makeColorInput(Activity activity, String hint, int color) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
        return input;
    }
}
