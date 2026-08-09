package com.benjoo.bsnake;

import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;

class InputHandler {

    interface GameActions {
        void startNewGame();
        void cycleSpeed();
        void openSettingsScreen();
        void dismissKeyboard();
        void exitApp();
        void toggleDevMode();
        void showDevScoreInput();
        void cycleDevBossType();
        void toggleDevPathfinding();
        void toggleCameraMode();
        void toggleDirectionButtons();
        void playClick();
        void playBossDamage();
        void playPause();
        void setMusicVolume(float volume);
        void setSfxVolume(float volume);
        void openMpMenu();
        void startHost();
        void startJoin();
        void cancelMp();
        void toggleReady();
        void forceStart();
        void rematch();
        void sendSwipe(int dx, int dy);
        void connectToHost(int index);
        void openColorPicker();
        void applyColorPicker();
        void setPickerHue(float hue);
        void setPickerSat(float sat);
        void setPickerVal(float val);
        void togglePickerTarget();
        void editPickerHex();
        void onUpgradeCardTap(int index);
        void onUpgradeChoose();
        void onUpgradeSkip();
    }

    private final GameState state;
    private final SnakeEngine engine;
    private final GameActions actions;

    private final long[] titleTapTimes = new long[3];
    private int titleTapIndex = 0;
    private int draggingSlider = 0;
    // True when the current touch gesture started inside a direction-pad
    // button, so a swipe from the pad is ignored (only taps count there).
    private boolean touchStartedOnDpad = false;

    InputHandler(GameState state, SnakeEngine engine, GameActions actions) {
        this.state = state;
        this.engine = engine;
        this.actions = actions;
    }

    boolean onTouchEvent(MotionEvent event) {
        // Ignore all input during the cinematic boss death sequence
        if (state.currentState == GameState.State.BOSS_DEATH_CINEMATIC) return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                state.downX = event.getX();
                state.downY = event.getY();
                touchStartedOnDpad = state.directionButtons
                        && (state.currentState == GameState.State.PLAYING
                            || state.currentState == GameState.State.MP_PLAYING)
                        && inDpadRegion(event.getX(), event.getY());
                if (state.currentState == GameState.State.SETTINGS) {
                    checkSliderDown(event.getX(), event.getY());
                } else if (state.currentState == GameState.State.COLOR_PICKER) {
                    checkPickerSliderDown(event.getX(), event.getY());
                } else if (state.currentState == GameState.State.BOSS_UPGRADE) {
                    checkUpgradeCardDown(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (draggingSlider != 0) handleSliderDrag(event.getX());
                break;
            case MotionEvent.ACTION_UP:
                if (draggingSlider != 0) {
                    draggingSlider = 0;
                } else {
                    handleTouchUp(event.getX(), event.getY());
                }
                break;
        }
        return true;
    }

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

    // Tapping a card selects it (press-down for instant feedback). Taps are
    // ignored until that card has finished flying in.
    private void checkUpgradeCardDown(float x, float y) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < state.upgradeOffers.size(); i++) {
            if (i < state.upgradeCardRects.length && contains(state.upgradeCardRects[i], x, y)) {
                long readyAt = state.upgradeOpenAt + i * GameState.UPGRADE_CARD_DELAY_MS
                        + GameState.UPGRADE_CARD_ENTRY_MS;
                if (now >= readyAt) actions.onUpgradeCardTap(i);
                return;
            }
        }
    }

    private void checkPickerSliderDown(float x, float y) {
        if (state.pickerHueBar != null && state.pickerHueBar.contains(x, y)) {
            draggingSlider = 3;
            float v = (x - state.pickerHueBar.left) / state.pickerHueBar.width() * 360f;
            actions.setPickerHue(Math.max(0, Math.min(360, v)));
        } else if (state.pickerSatBar != null && state.pickerSatBar.contains(x, y)) {
            draggingSlider = 4;
            float v = (x - state.pickerSatBar.left) / state.pickerSatBar.width();
            actions.setPickerSat(Math.max(0, Math.min(1, v)));
        } else if (state.pickerValBar != null && state.pickerValBar.contains(x, y)) {
            draggingSlider = 5;
            float v = (x - state.pickerValBar.left) / state.pickerValBar.width();
            actions.setPickerVal(Math.max(0, Math.min(1, v)));
        }
    }

    private void handleSliderDrag(float x) {
        if (draggingSlider == 1 || draggingSlider == 2) {
            RectF track = (draggingSlider == 1) ? state.musicSliderTrack : state.sfxSliderTrack;
            float vol = (x - track.left) / track.width();
            vol = Math.max(0, Math.min(1, vol));
            if (draggingSlider == 1) actions.setMusicVolume(vol);
            else actions.setSfxVolume(vol);
        } else {
            RectF track;
            float val;
            if (draggingSlider == 3) {
                track = state.pickerHueBar;
                val = (x - track.left) / track.width() * 360f;
                actions.setPickerHue(Math.max(0, Math.min(360, val)));
            } else if (draggingSlider == 4) {
                track = state.pickerSatBar;
                val = (x - track.left) / track.width();
                actions.setPickerSat(Math.max(0, Math.min(1, val)));
            } else if (draggingSlider == 5) {
                track = state.pickerValBar;
                val = (x - track.left) / track.width();
                actions.setPickerVal(Math.max(0, Math.min(1, val)));
            }
        }
    }

    private void handleTouchUp(float upX, float upY) {
        // Ignore all input during the cinematic boss death sequence
        if (state.currentState == GameState.State.BOSS_DEATH_CINEMATIC) return;
        switch (state.currentState) {
            case MENU:
                if (contains(state.playBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.PLAY_MENU;
                } else if (contains(state.speedBtn, upX, upY)) {
                    actions.playClick();
                    actions.cycleSpeed();
                } else if (contains(state.settingsBtn, upX, upY)) {
                    actions.playClick();
                    actions.openSettingsScreen();
                } else if (contains(state.leaderboardBtn, upX, upY)) {
                    actions.playClick();
                    state.leaderboardMode = state.lastPlayedMode >= 0 ? state.lastPlayedMode : 0;
                    state.currentState = GameState.State.LEADERBOARD;
                } else if (contains(state.exitBtn, upX, upY)) {
                    actions.playClick();
                    actions.exitApp();
                } else if (state.devMode && contains(state.devScoreBtn, upX, upY)) {
                    actions.playClick();
                    actions.showDevScoreInput();
                } else if (state.devMode && contains(state.devBossBtn, upX, upY)) {
                    actions.playClick();
                    actions.cycleDevBossType();
                } else if (state.devMode && contains(state.devPathBtn, upX, upY)) {
                    actions.playClick();
                    actions.toggleDevPathfinding();
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

            case PLAY_MENU:
                if (contains(state.singleplayerBtn, upX, upY)) {
                    actions.playClick();
                    state.selectedModeIndex = state.lastPlayedMode >= 0 ? state.lastPlayedMode : 0;
                    state.currentState = GameState.State.MODE_SELECT;
                } else if (contains(state.multiplayerBtn, upX, upY)) {
                    actions.playClick();
                    actions.openMpMenu();
                } else if (contains(state.playBackBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case MODE_SELECT:
                if (contains(state.arcadeBtn, upX, upY)) {
                    actions.playClick();
                    state.selectedModeIndex = 0;
                } else if (contains(state.classicBtn, upX, upY)) {
                    actions.playClick();
                    state.selectedModeIndex = 1;
                } else if (contains(state.modePlayBtn, upX, upY)) {
                    actions.playClick();
                    state.gameMode = state.selectedModeIndex == 0
                            ? GameState.GameMode.ARCADE : GameState.GameMode.CLASSIC;
                    actions.startNewGame();
                } else if (contains(state.modeBackBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.PLAY_MENU;
                }
                break;

            case MP_MENU:
                if (contains(state.hostBtn, upX, upY)) {
                    actions.playClick();
                    actions.startHost();
                } else if (contains(state.joinBtn, upX, upY)) {
                    actions.playClick();
                    actions.startJoin();
                } else if (contains(state.backBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case MP_HOST:
                if (contains(state.cancelBtn, upX, upY)) {
                    actions.playClick();
                    actions.cancelMp();
                }
                break;

            case MP_JOIN:
                if (contains(state.cancelBtn, upX, upY)) {
                    actions.playClick();
                    actions.cancelMp();
                } else {
                    for (int i = 0; i < state.hostItemRects.size(); i++) {
                        if (contains(state.hostItemRects.get(i), upX, upY)) {
                            actions.playClick();
                            actions.connectToHost(i);
                            break;
                        }
                    }
                }
                break;

            case MP_LOBBY:
                if (contains(state.readyBtn, upX, upY)) {
                    actions.playClick();
                    actions.toggleReady();
                } else if (state.isHost && contains(state.forceStartBtn, upX, upY)) {
                    actions.playClick();
                    actions.forceStart();
                } else if (contains(state.cancelBtn, upX, upY)) {
                    actions.playClick();
                    actions.cancelMp();
                }
                break;

            case MP_GAME_OVER:
                if (state.isHost && contains(state.mpRestartBtn, upX, upY)) {
                    actions.playClick();
                    actions.rematch();
                } else if (contains(state.mpMenuBtn, upX, upY)) {
                    actions.playClick();
                    actions.cancelMp();
                }
                break;

            case LEADERBOARD:
                if (contains(state.lbArcadeBtn, upX, upY)) {
                    actions.playClick();
                    state.leaderboardMode = 0;
                } else if (contains(state.lbClassicBtn, upX, upY)) {
                    actions.playClick();
                    state.leaderboardMode = 1;
                } else if (contains(state.lbSortBtn, upX, upY)) {
                    actions.playClick();
                    state.sortMode = (state.sortMode == GameState.SortMode.HIGH_SCORE)
                            ? GameState.SortMode.RECENT : GameState.SortMode.HIGH_SCORE;
                } else if (contains(state.lbBackBtn, upX, upY)) {
                    actions.playClick();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case SETTINGS:
                if (contains(state.cameraModeBtn, upX, upY)) {
                    actions.playClick();
                    actions.toggleCameraMode();
                } else if (contains(state.directionButtonsBtn, upX, upY)) {
                    actions.playClick();
                    actions.toggleDirectionButtons();
                } else if (contains(state.snakeColorBtn, upX, upY)) {
                    actions.playClick();
                    actions.openColorPicker();
                } else if (contains(state.settingsBackBtn, upX, upY)) {
                    actions.playClick();
                    actions.dismissKeyboard();
                    state.currentState = GameState.State.MENU;
                }
                break;

            case BOSS_UPGRADE:
                if (state.upgradeSelectedIndex >= 0 && contains(state.upgradeChooseBtn, upX, upY)) {
                    actions.onUpgradeChoose();
                } else if (contains(state.upgradeSkipBtn, upX, upY)) {
                    actions.onUpgradeSkip();
                }
                break;

            case PAUSED:
                if (contains(state.resumeBtn, upX, upY)) {
                    actions.playPause();
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

            case PLAYING:
            case MP_PLAYING:
                float dx = upX - state.downX;
                float dy = upY - state.downY;
                boolean smallMove = Math.abs(dx) < 20 && Math.abs(dy) < 20;
                if (state.currentState == GameState.State.PLAYING
                        && smallMove && contains(state.pauseHitRect, upX, upY)) {
                    actions.playPause();
                    state.currentState = GameState.State.PAUSED;
                    return;
                }

                // Challenge HUD: tapping the collapsed dot strip opens the full
                // list; tapping the open panel collapses it. Swipes still pass
                // through as movement input.
                if (smallMove && !state.activeChallenges.isEmpty()) {
                    if (state.challengePanelOpen && contains(state.challengePanelRect, upX, upY)) {
                        state.challengePanelOpen = false;
                        state.challengeAutoHideUntil = 0;
                        actions.playClick();
                        return;
                    } else if (!state.challengePanelOpen && contains(state.challengeStripRect, upX, upY)) {
                        state.challengePanelOpen = true;
                        state.challengeAutoHideUntil = 0;
                        actions.playClick();
                        return;
                    }
                }

                // On-screen direction buttons: a tap on a visible button is a
                // direction input. The button opposite to the snake's current
                // direction is not drawn, so it can't be tapped here.
                // Swipes that start on the pad are ignored entirely. A press
                // that began on the pad counts as a tap unless the finger
                // travels a clear distance, and is hit-tested at the press-down
                // point so a small wobble can't miss the button.
                int ndx = 0, ndy = 0;
                if (state.directionButtons) {
                    if (touchStartedOnDpad) {
                        boolean padSwipe = Math.abs(dx) > 90 || Math.abs(dy) > 90;
                        if (padSwipe) return;
                        if (contains(state.dpadUpBtn, state.downX, state.downY)) {
                            ndy = -1;
                        } else if (contains(state.dpadDownBtn, state.downX, state.downY)) {
                            ndy = 1;
                        } else if (contains(state.dpadLeftBtn, state.downX, state.downY)) {
                            ndx = -1;
                        } else if (contains(state.dpadRightBtn, state.downX, state.downY)) {
                            ndx = 1;
                        }
                        if (ndx == 0 && ndy == 0) return;
                        actions.playClick();
                    } else if (smallMove) {
                        if (contains(state.dpadUpBtn, upX, upY)) {
                            ndy = -1;
                        } else if (contains(state.dpadDownBtn, upX, upY)) {
                            ndy = 1;
                        } else if (contains(state.dpadLeftBtn, upX, upY)) {
                            ndx = -1;
                        } else if (contains(state.dpadRightBtn, upX, upY)) {
                            ndx = 1;
                        }
                        if (ndx != 0 || ndy != 0) actions.playClick();
                    }
                }
                if (ndx == 0 && ndy == 0) {
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) ndx = 1;
                        else if (dx < 0) ndx = -1;
                    } else {
                        if (dy > 0) ndy = 1;
                        else if (dy < 0) ndy = -1;
                    }
                }
                if (ndx == 0 && ndy == 0) return;

                if (state.currentState == GameState.State.MP_PLAYING && !state.isHost) {
                    // Client: send input to host
                    actions.sendSwipe(ndx, ndy);
                } else {
                    // Single-player or host: enqueue locally
                    int si = state.isHost ? state.playerIndex : 0;
                    GameState.SnakeData sd = state.snakes[si];
                    if (!sd.alive) return;
                    Point lastDir = sd.inputQueue.isEmpty()
                            ? new Point(sd.dirX, sd.dirY)
                            : sd.inputQueue.get(sd.inputQueue.size() - 1);
                    if (!(ndx == -lastDir.x && ndy == -lastDir.y) && sd.inputQueue.size() < 2) {
                        if (!(ndx == lastDir.x && ndy == lastDir.y)) {
                            sd.inputQueue.add(new Point(ndx, ndy));
                        }
                    }
                }
                break;

            case COLOR_PICKER:
                if (contains(state.pickerHeadBtn, upX, upY)) {
                    if (state.pickerTarget != 0) {
                        actions.playClick();
                        actions.togglePickerTarget();
                    }
                } else if (contains(state.pickerBodyBtn, upX, upY)) {
                    if (state.pickerTarget != 1) {
                        actions.playClick();
                        actions.togglePickerTarget();
                    }
                } else if (contains(state.pickerHexField, upX, upY)) {
                    actions.playClick();
                    actions.editPickerHex();
                } else if (contains(state.pickerHueBar, upX, upY)) {
                    float v = (upX - state.pickerHueBar.left) / state.pickerHueBar.width() * 360f;
                    actions.setPickerHue(Math.max(0, Math.min(360, v)));
                } else if (contains(state.pickerSatBar, upX, upY)) {
                    float v = (upX - state.pickerSatBar.left) / state.pickerSatBar.width();
                    actions.setPickerSat(Math.max(0, Math.min(1, v)));
                } else if (contains(state.pickerValBar, upX, upY)) {
                    float v = (upX - state.pickerValBar.left) / state.pickerValBar.width();
                    actions.setPickerVal(Math.max(0, Math.min(1, v)));
                } else if (contains(state.pickerApplyBtn, upX, upY)) {
                    actions.playClick();
                    actions.applyColorPicker();
                } else if (contains(state.pickerCancelBtn, upX, upY)) {
                    actions.playClick();
                    actions.dismissKeyboard();
                    state.headColor = state.pickerOrigHeadColor;
                    state.bodyColor = state.pickerOrigBodyColor;
                    state.currentState = GameState.State.MENU;
                }
                break;
        }
    }

    private boolean isTapOnTitle(float x, float y) {
        float titleY = state.screenH * 0.20f;
        float halfW = 200;
        return x > state.screenW / 2f - halfW && x < state.screenW / 2f + halfW
                && y > titleY - 60 && y < titleY + 60;
    }

    private boolean contains(RectF r, float x, float y) {
        return r != null && r.contains(x, y);
    }

    private boolean inDpadRegion(float x, float y) {
        return contains(state.dpadLeftBtn, x, y)
                || contains(state.dpadUpBtn, x, y)
                || contains(state.dpadDownBtn, x, y)
                || contains(state.dpadRightBtn, x, y);
    }
}
