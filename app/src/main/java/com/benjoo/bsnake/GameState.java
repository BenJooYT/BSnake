package com.benjoo.bsnake;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.RectF;

import java.util.ArrayList;

public class GameState {

    enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEADERBOARD, SETTINGS,
                 MP_MENU, MP_HOST, MP_JOIN, MP_LOBBY, MP_PLAYING, MP_GAME_OVER }
    volatile State currentState = State.MENU;

    enum SortMode { HIGH_SCORE, RECENT }
    SortMode sortMode = SortMode.HIGH_SCORE;

    enum CameraMode { CLASSIC_ZOOM, FULL_PLAY_AREA, FIT_VERTICAL }
    CameraMode cameraMode = CameraMode.CLASSIC_ZOOM;

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

    // A single snake's mutable state
    static class SnakeData {
        ArrayList<Point> body = new ArrayList<>();
        ArrayList<Point> prevBody = new ArrayList<>();
        int dirX = 1, dirY = 0;
        ArrayList<Point> inputQueue = new ArrayList<>();
        int score = 0;
        int headColor = Color.GREEN;
        int bodyColor = Color.GREEN;
        boolean alive = true;
    }

    // Two snakes: index 0 = host/local, index 1 = client/remote
    SnakeData[] snakes = new SnakeData[]{ new SnakeData(), new SnakeData() };
    int playerIndex = 0; // 0 or 1

    ArrayList<Point> foods = new ArrayList<>();
    int cellSize = 40;
    int uiCellSize = 40;
    int cols = 32, rows = 32;
    float viewportWidthCells, viewportHeightCells;
    float boardLeft, boardTop;
    float cameraX, cameraY;
    boolean cameraInitialized;

    final long[] speedDelays = {220, 150, 90};
    final String[] speedLabels = {"EASY", "NORMAL", "HARD"};
    int speedIndex = 1;
    long tickDelay;

    int score = 0;
    int lastScore = 0;
    int screenW, screenH;

    int classicCellSize, fullAreaCellSize, fitVerticalCellSize;

    RectF startBtn, speedBtn, settingsBtn, leaderboardBtn, exitBtn;
    RectF headInputBtn, bodyInputBtn, settingsApplyBtn, settingsBackBtn, cameraModeBtn;
    RectF resumeBtn, pauseMenuBtn;
    RectF restartBtn, overMenuBtn;
    RectF lbSortBtn, lbBackBtn;
    RectF pauseIcon;

    // Multiplayer menu buttons
    RectF mpBtn, backBtn;
    RectF hostBtn, joinBtn;
    RectF cancelBtn, readyBtn, forceStartBtn;
    RectF mpRestartBtn, mpMenuBtn;

    float downX, downY;
    int headColor = Color.GREEN;
    int bodyColor = Color.GREEN;
    String headHex = "#00FF00";
    String bodyHex = "#00FF00";
    int editingColor = -1;

    // Multiplayer state
    volatile boolean isHost;
    int clientColor = Color.GREEN;
    volatile boolean opponentReady;
    volatile boolean localReady;
    volatile boolean opponentConnected;
    volatile boolean mpLabelVisible;
    volatile boolean mpGameOverSent;
    int mpWinner = -1;
    int mpLastScore0, mpLastScore1;

    static class BossFruit {
        int x = -1, y = -1;
        int hp = 5;
        boolean alive = false;
        int lastMoveTick = 0;
        ArrayList<Point> getTiles() {
            ArrayList<Point> tiles = new ArrayList<>();
            tiles.add(new Point(x, y));
            tiles.add(new Point(x + 1, y));
            tiles.add(new Point(x, y + 1));
            tiles.add(new Point(x + 1, y + 1));
            return tiles;
        }
    }

    static class BossTrailCell {
        int x, y;
        int createdAtTick;
        BossTrailCell(int x, int y, int tick) { this.x = x; this.y = y; this.createdAtTick = tick; }
    }

    BossFruit boss = new BossFruit();
    int bossGrowthPending = 0;
    ArrayList<BossTrailCell> bossTrail = new ArrayList<>();
    int nextBossSpawnScore = 125;
    int tickCount = 0;

    boolean devMode = false;
    int devStartScore = 0;
    String devScoreText = "0";
    boolean editingDevScore = false;
    RectF devScoreBtn;

    float musicVolume = 0.25f;
    float sfxVolume = 0.5f;
    RectF musicSliderTrack, sfxSliderTrack;

    GameState() {
        tickDelay = speedDelays[speedIndex];
    }

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
        mpBtn = makeBtn(cx, startY - bh - gap, bw, bh);

        backBtn = makeBtn(cx, screenH * 0.90f, bw, bh);
        hostBtn = makeBtn(cx, startY, bw, bh);
        joinBtn = makeBtn(cx, startY + bh + gap, bw, bh);

        cancelBtn = makeBtn(cx, screenH * 0.80f, bw, bh);
        readyBtn = makeBtn(cx, screenH * 0.60f, bw, bh);
        forceStartBtn = makeBtn(cx, screenH * 0.72f, bw, bh);

        mpRestartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        mpMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        headInputBtn = makeBtn(cx, screenH * 0.30f, bw, bh);
        bodyInputBtn = makeBtn(cx, screenH * 0.40f, bw, bh);
        cameraModeBtn = makeBtn(cx, screenH * 0.55f, bw, bh);

        float sliderW = bw * 0.85f;
        float sliderH = 40;
        musicSliderTrack = new RectF(cx - sliderW / 2f, screenH * 0.64f - sliderH / 2f,
                                     cx + sliderW / 2f, screenH * 0.64f + sliderH / 2f);
        sfxSliderTrack = new RectF(cx - sliderW / 2f, screenH * 0.73f - sliderH / 2f,
                                   cx + sliderW / 2f, screenH * 0.73f + sliderH / 2f);

        settingsApplyBtn = makeBtn(cx, screenH * 0.82f, bw, bh);
        settingsBackBtn = makeBtn(cx, screenH * 0.92f, bw, bh);

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

    static RectF makeBtn(float cx, float cy, float w, float h) {
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }
}
