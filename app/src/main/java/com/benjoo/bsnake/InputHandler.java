package com.benjoo.bsnake;

import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;

// Processes touch events and drives state-machine transitions.
// Simple state/data changes are applied directly; complex operations
// (settings, keyboard, app exit) are delegated through GameActions.
class InputHandler {

    // Callback interface that GameView implements for actions requiring
    // Android framework access (keyboard, Activity, etc.).
    interface GameActions {
        void startNewGame();
        void cycleSpeed();
        void openSettingsScreen();
        void applyColors();
        void editColorField(int index);
        void dismissKeyboard();
        void exitApp();
        void toggleDevMode();
        void showDevScoreInput();
        void toggleCameraMode();
        void playClick();
        void setMusicVolume(float volume);
        void setSfxVolume(float volume);
    }

    private final GameState state;
    private final SnakeEngine engine;
    private final GameActions actions;

    // Triple-tap detection for dev mode (stores last 3 tap timestamps)
    private final long[] titleTapTimes = new long[3];
    private int titleTapIndex = 0;

    // Slider dragging state: 0 = none, 1 = music, 2 = sfx
    private int draggingSlider = 0;

    InputHandler(GameState state, SnakeEngine engine, GameActions actions) {
        this.state = state;
        this.engine = engine;
        this.actions = actions;
    }

    // Record touch-down coordinates; start slider drag if on a volume slider.
    boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                state.downX = event.getX();
                state.downY = event.getY();
                if (state.currentState == GameState.State.SETTINGS) {
                    checkSliderDown(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (draggingSlider != 0) {
                    handleSliderDrag(event.getX());
                }
                break;
            case MotionEvent.ACTION_UP:
                draggingSlider = 0;
                handleTouchUp(event.getX(), event.getY());
                break;
        }
        return true;
    }

    // Start dragging a slider if the touch falls inside its track rect.
    private void checkSliderDown(float x, float y) {
        if (state.musicSliderTrack != null && state.musicSliderTrack.contains(x, y)) {
            draggingSlider = 1;
            float vol = (x - state.musicSliderTrack.left) / state.musicSliderTrack.width();
            actions.setMusicVolume(Math.max(0, Math.min(1, vol)));
        } else if (state.sfxSliderTrack != null && state.sfxSliderTrack.contains(x, y)) {
            draggingSlider = 2;
            float vol = (x - state.sfxSliderTrack.left) / state.sfxSliderTrack.width();
            actions.setSfxVolume(Math.max(0, Math.min(1, vol)));
        }
    }

    // Update volume while the finger is dragged across a slider.
    private void handleSliderDrag(float x) {
        RectF track = (draggingSlider == 1) ? state.musicSliderTrack : state.sfxSliderTrack;
        float vol = (x - track.left) / track.width();
        vol = Math.max(0, Math.min(1, vol));
        if (draggingSlider == 1) actions.setMusicVolume(vol);
        else actions.setSfxVolume(vol);
    }

    // Route the touch-up event based on the current screen state.
    private void handleTouchUp(float upX, float upY) {
        switch (state.currentState) {
            case MENU:
                if (contains(state.startBtn, upX, upY)) {
                    actions.playClick();
                    actions.startNewGame();
                } else if (contains(state.speedBtn, upX, upY)) {
                    actions.playClick();
                    actions.cycleSpeed();
                } else if (contains(state.settingsBtn, upX, upY)) {
                    actions.playClick();
                    actions.openSettingsScreen();
                } else if (contains(state.leaderboardBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.LEADERBOARD;
                } else if (contains(state.exitBtn, upX, upY)) {
                    actions.playClick();
                    actions.exitApp();
                } else if (state.devMode && contains(state.devScoreBtn, upX, upY)) {
                    actions.playClick();
                    actions.showDevScoreInput();
                } else if (isTapOnTitle(upX, upY)) {
                    titleTapTimes[titleTapIndex % 3] = System.currentTimeMillis();
                    titleTapIndex++;
                    if (titleTapIndex >= 3) {
                        long now = System.currentTimeMillis();
                        if (now - titleTapTimes[0] <= 500) {
                            actions.playClick();
                            actions.toggleDevMode();
                            titleTapIndex = 0;
                        }
                    }
                }
                break;

            case LEADERBOARD:
                if (contains(state.lbSortBtn, upX, upY)) {
                    actions.playClick();
                    state.sortMode = (state.sortMode == GameState.SortMode.HIGH_SCORE)
                            ? GameState.SortMode.RECENT : GameState.SortMode.HIGH_SCORE;
                } else if (contains(state.lbBackBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case SETTINGS:
                if (contains(state.headInputBtn, upX, upY)) {
                    actions.playClick();
                    actions.editColorField(0);
                } else if (contains(state.bodyInputBtn, upX, upY)) {
                    actions.playClick();
                    actions.editColorField(1);
                } else if (contains(state.cameraModeBtn, upX, upY)) {
                    actions.playClick();
                    actions.toggleCameraMode();
                } else if (contains(state.settingsApplyBtn, upX, upY)) {
                    actions.playClick();
                    actions.applyColors();
                } else if (contains(state.settingsBackBtn, upX, upY)) {
                    actions.playClick();
                    actions.dismissKeyboard();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case PAUSED:
                if (contains(state.resumeBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.PLAYING;
                } else if (contains(state.pauseMenuBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case GAME_OVER:
                if (contains(state.restartBtn, upX, upY)) {
                    actions.playClick();
                    actions.startNewGame();
                } else if (contains(state.overMenuBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.MENU;
                }
                break;

            // During gameplay: a small tap on the pause icon pauses;
            // a swipe determines the next direction and enqueues it
            // (max depth 2, 180-degree reversals rejected).
            case PLAYING:
                float dx = upX - state.downX;
                float dy = upY - state.downY;
                boolean smallMove = Math.abs(dx) < 20 && Math.abs(dy) < 20;
                if (smallMove && contains(state.pauseIcon, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.PAUSED;
                    return;
                }

                int ndx = 0, ndy = 0;
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) { ndx = 1; ndy = 0; }
                    else if (dx < 0) { ndx = -1; ndy = 0; }
                } else {
                    if (dy > 0) { ndx = 0; ndy = 1; }
                    else if (dy < 0) { ndx = 0; ndy = -1; }
                }

                if (ndx != 0 || ndy != 0) {
                    Point lastDir = state.inputQueue.isEmpty()
                            ? new Point(state.dirX, state.dirY)
                            : state.inputQueue.get(state.inputQueue.size() - 1);
                    if (!(ndx == -lastDir.x && ndy == -lastDir.y) && state.inputQueue.size() < 2) {
                        if (!(ndx == lastDir.x && ndy == lastDir.y)) {
                            state.inputQueue.add(new Point(ndx, ndy));
                        }
                    }
                }
                break;
        }
    }

    // Check if the tap landed in the "SNAKE" title area (top-center)
    private boolean isTapOnTitle(float x, float y) {
        float titleY = state.screenH * 0.24f;
        float halfW = 200;
        return x > state.screenW / 2f - halfW && x < state.screenW / 2f + halfW
                && y > titleY - 60 && y < titleY + 60;
    }

    // Hit-test: true if the point (x, y) falls inside rect r.
    private boolean contains(RectF r, float x, float y) {
        return r != null && r.contains(x, y);
    }
}
