package com.benjoo.bsnake;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.RectF;

import java.util.ArrayList;

// Central data store for all game state — model, layout, and UI state.
// Owned by GameView and read/written by all subsystems.
public class GameState {

    // Finite-state machine for which screen is active
    enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEADERBOARD, SETTINGS }
    State currentState = State.MENU;

    // Leaderboard sort order toggle
    enum SortMode { HIGH_SCORE, RECENT }
    SortMode sortMode = SortMode.HIGH_SCORE;

    // Camera modes — how the viewport frames the game board
    enum CameraMode { CLASSIC_ZOOM, FULL_PLAY_AREA, FIT_VERTICAL }
    CameraMode cameraMode = CameraMode.CLASSIC_ZOOM;

    // A single leaderboard entry persisted in SharedPreferences
    static class ScoreEntry {
        int score;
        long timestamp;
        String difficulty;

        ScoreEntry(int score, long timestamp, String difficulty) {
            this.score = score;
            this.timestamp = timestamp;
            this.difficulty = difficulty;
        }
    }

    // Snake body segments (head at index 0), previous-tick snapshot, active food, and queued swipe directions
    ArrayList<Point> snake = new ArrayList<>();
    ArrayList<Point> prevSnake = new ArrayList<>();
    ArrayList<Point> foods = new ArrayList<>();
    int dirX = 1, dirY = 0;
    ArrayList<Point> inputQueue = new ArrayList<>();
    // Render cell size for the game field, UI cell size for buttons, grid dimensions, camera state
    int cellSize = 40;
    int uiCellSize = 40;
    int cols = 32, rows = 32;
    float viewportWidthCells, viewportHeightCells;
    float boardLeft, boardTop;
    float cameraX, cameraY;
    boolean cameraInitialized;

    // Tick delays (ms) and labels for each difficulty tier; current active index
    final long[] speedDelays = {220, 150, 90};
    final String[] speedLabels = {"EASY", "NORMAL", "HARD"};
    int speedIndex = 1;
    long tickDelay;

    // Independent score (separate from snake size) — used for leaderboards, spawn thresholds, rewards
    int score = 0;
    // Last score achieved (for game-over display) and screen dimensions in pixels
    int lastScore = 0;
    int screenW, screenH;

    // Cell sizes for each camera mode (computed in configureBoard)
    int classicCellSize, fullAreaCellSize, fitVerticalCellSize;

    // Hit-box rectangles for every interactive button across all screens
    RectF startBtn, speedBtn, settingsBtn, leaderboardBtn, exitBtn;
    RectF headInputBtn, bodyInputBtn, settingsApplyBtn, settingsBackBtn, cameraModeBtn;
    RectF resumeBtn, pauseMenuBtn;
    RectF restartBtn, overMenuBtn;
    RectF lbSortBtn, lbBackBtn;
    RectF pauseIcon;

    // Touch-down coordinates (for swipe detection), snake head/body colors and hex strings, active editing field index
    float downX, downY;
    int headColor = Color.GREEN;
    int bodyColor = Color.GREEN;
    String headHex = "#00FF00";
    String bodyHex = "#00FF00";
    int editingColor = -1;

    // ----- boss fruit system -----

    // A 2x2 boss fruit that spawns every 125 score, has 5 HP, moves randomly,
    // teleports on hit leaving a temporary trail, and rewards +25 score on defeat.
    static class BossFruit {
        int x = -1, y = -1;          // top-left corner of the 2x2 area
        int hp = 5;
        boolean alive = false;
        int lastMoveTick = 0;          // game tick when the boss last moved

        // Return the 4 tile coordinates this boss occupies
        ArrayList<Point> getTiles() {
            ArrayList<Point> tiles = new ArrayList<>();
            tiles.add(new Point(x, y));
            tiles.add(new Point(x + 1, y));
            tiles.add(new Point(x, y + 1));
            tiles.add(new Point(x + 1, y + 1));
            return tiles;
        }
    }

    // A single cell of the boss's trail, dropped when the boss teleports
    static class BossTrailCell {
        int x, y;
        int createdAtTick;
        BossTrailCell(int x, int y, int tick) {
            this.x = x;
            this.y = y;
            this.createdAtTick = tick;
        }
    }

    BossFruit boss = new BossFruit();
    int bossGrowthPending = 0;          // extra growth ticks (consumed 1/tick after boss defeat)
    ArrayList<BossTrailCell> bossTrail = new ArrayList<>();
    int nextBossSpawnScore = 125;       // score threshold for next boss spawn
    int tickCount = 0;                  // total game ticks elapsed

    // ----- developer mode -----
    boolean devMode = false;               // toggled by triple-tapping the title
    int devStartScore = 0;                 // parsed from devScoreText when starting
    String devScoreText = "0";             // raw text from keyboard input
    boolean editingDevScore = false;       // true while keyboard is active for dev score
    RectF devScoreBtn;                     // tap target for the start-score display

    GameState() {
        tickDelay = speedDelays[speedIndex];
    }

    // Scale cell sizes to fit the current screen width.
    // The renderer overrides cellSize during gameplay for non-CLASSIC_ZOOM modes.
    void configureBoard() {
        uiCellSize = Math.max(16, screenW / 20);
        classicCellSize = Math.max(8, screenW / 10);
        fullAreaCellSize = Math.max(4, Math.min(screenW / cols, screenH / rows));
        fitVerticalCellSize = Math.max(4, screenH / rows);
        cellSize = classicCellSize;
        viewportWidthCells = screenW / (float) cellSize;
        viewportHeightCells = screenH / (float) cellSize;
        boardLeft = 0;
        boardTop = 0;
    }

    // Position every button rect based on current screen dimensions
    void layoutButtons() {
        float bw = Math.min(screenW * 0.7f, 420);
        float bh = uiCellSize * 1.5f;
        float gap = uiCellSize * 0.4f;
        float cx = screenW / 2f;
        float startY = screenH * 0.40f;

        startBtn = makeBtn(cx, startY, bw, bh);
        speedBtn = makeBtn(cx, startY + bh + gap, bw, bh);
        settingsBtn = makeBtn(cx, startY + (bh + gap) * 2, bw, bh);
        leaderboardBtn = makeBtn(cx, startY + (bh + gap) * 3, bw, bh);
        exitBtn = makeBtn(cx, startY + (bh + gap) * 4, bw, bh);

        headInputBtn = makeBtn(cx, screenH * 0.30f, bw, bh);
        bodyInputBtn = makeBtn(cx, screenH * 0.40f, bw, bh);
        cameraModeBtn = makeBtn(cx, screenH * 0.54f, bw, bh);
        settingsApplyBtn = makeBtn(cx, screenH * 0.67f, bw, bh);
        settingsBackBtn = makeBtn(cx, screenH * 0.80f, bw, bh);

        resumeBtn = makeBtn(cx, screenH * 0.5f, bw, bh);
        pauseMenuBtn = makeBtn(cx, screenH * 0.5f + bh + gap, bw, bh);

        restartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        overMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        lbSortBtn = makeBtn(cx, screenH * 0.20f, bw, bh * 0.8f);
        lbBackBtn = makeBtn(cx, screenH * 0.88f, bw, bh);

        devScoreBtn = makeBtn(cx, startY + (bh + gap) * 5, bw, bh * 0.8f);

        float iconSize = uiCellSize * 1.1f;
        pauseIcon = new RectF(screenW - iconSize - 16, 16, screenW - 16, 16 + iconSize);
    }

    // Build a centered RectF given center (cx, cy) and dimensions (w, h)
    static RectF makeBtn(float cx, float cy, float w, float h) {
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }
}
