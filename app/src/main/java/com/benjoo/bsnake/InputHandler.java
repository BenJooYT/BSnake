package com.benjoo.bsnake;

import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;

class InputHandler {

    interface GameActions {
        void startNewGame();
        void cycleSpeed();
        void openSettingsScreen();
        void applyColors();
        void editColorField(int index);
        void dismissKeyboard();
        void exitApp();
    }

    private final GameState state;
    private final SnakeEngine engine;
    private final GameActions actions;

    InputHandler(GameState state, SnakeEngine engine, GameActions actions) {
        this.state = state;
        this.engine = engine;
        this.actions = actions;
    }

    boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                state.downX = event.getX();
                state.downY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                handleTouchUp(event.getX(), event.getY());
                break;
        }
        return true;
    }

    private void handleTouchUp(float upX, float upY) {
        switch (state.currentState) {
            case MENU:
                if (contains(state.startBtn, upX, upY)) {
                    actions.startNewGame();
                } else if (contains(state.speedBtn, upX, upY)) {
                    actions.cycleSpeed();
                } else if (contains(state.settingsBtn, upX, upY)) {
                    actions.openSettingsScreen();
                } else if (contains(state.leaderboardBtn, upX, upY)) {
                    state.currentState = GameState.State.LEADERBOARD;
                } else if (contains(state.exitBtn, upX, upY)) {
                    actions.exitApp();
                }
                break;

            case LEADERBOARD:
                if (contains(state.lbSortBtn, upX, upY)) {
                    state.sortMode = (state.sortMode == GameState.SortMode.HIGH_SCORE)
                            ? GameState.SortMode.RECENT : GameState.SortMode.HIGH_SCORE;
                } else if (contains(state.lbBackBtn, upX, upY)) {
                    state.currentState = GameState.State.MENU;
                }
                break;

            case SETTINGS:
                if (contains(state.headInputBtn, upX, upY)) {
                    actions.editColorField(0);
                } else if (contains(state.bodyInputBtn, upX, upY)) {
                    actions.editColorField(1);
                } else if (contains(state.settingsApplyBtn, upX, upY)) {
                    actions.applyColors();
                } else if (contains(state.settingsBackBtn, upX, upY)) {
                    actions.dismissKeyboard();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case PAUSED:
                if (contains(state.resumeBtn, upX, upY)) {
                    state.currentState = GameState.State.PLAYING;
                } else if (contains(state.pauseMenuBtn, upX, upY)) {
                    state.currentState = GameState.State.MENU;
                }
                break;

            case GAME_OVER:
                if (contains(state.restartBtn, upX, upY)) {
                    actions.startNewGame();
                } else if (contains(state.overMenuBtn, upX, upY)) {
                    state.currentState = GameState.State.MENU;
                }
                break;

            case PLAYING:
                float dx = upX - state.downX;
                float dy = upY - state.downY;
                boolean smallMove = Math.abs(dx) < 20 && Math.abs(dy) < 20;
                if (smallMove && contains(state.pauseIcon, upX, upY)) {
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

    private boolean contains(RectF r, float x, float y) {
        return r != null && r.contains(x, y);
    }
}
