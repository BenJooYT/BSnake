package com.benjoo.bsnake;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.RectF;

import java.util.ArrayList;

public class GameState {

    enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEADERBOARD, SETTINGS,
                 MP_MENU, MP_HOST, MP_JOIN, MP_LOBBY, MP_PLAYING, MP_GAME_OVER,
                 COLOR_PICKER, PLAY_MENU, MODE_SELECT }
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
        ArrayList<Point> mpHostBody = new ArrayList<>();
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

    RectF startBtn, speedBtn, snakeColorBtn, settingsBtn, leaderboardBtn, exitBtn;
    RectF playBtn, singleplayerBtn, multiplayerBtn, playBackBtn;
    RectF arcadeBtn, modeBackBtn;
    RectF settingsBackBtn, cameraModeBtn;
    RectF snakePreviewRect;
    RectF resumeBtn, pauseMenuBtn;
    RectF restartBtn, overMenuBtn;
    RectF lbSortBtn, lbBackBtn;
    RectF pauseIcon;

    // Multiplayer menu buttons
    RectF backBtn;
    RectF hostBtn, joinBtn;
    RectF cancelBtn, readyBtn, forceStartBtn;
    RectF mpRestartBtn, mpMenuBtn;

    float downX, downY;
    int headColor = Color.GREEN;
    int bodyColor = Color.GREEN;
    String headHex = "#00FF00";
    String bodyHex = "#00FF00";
    int editingColor = -1;

    // Color picker state
    int pickerOrigHeadColor = Color.GREEN;
    int pickerOrigBodyColor = Color.GREEN;
    int pickerTarget = 0; // 0 = head, 1 = body
    float pickerHue = 120f;
    float pickerSat = 1f;
    float pickerVal = 1f;
    int pickerColor = Color.GREEN;
    String pickerHex = "#00FF00";
    boolean pickerEditingHex = false;
    RectF pickerHeadBtn, pickerBodyBtn;
    RectF pickerSnakePreview;
    RectF pickerSwatch;
    RectF pickerHueBar, pickerSatBar, pickerValBar;
    RectF pickerHexField;
    RectF pickerApplyBtn, pickerCancelBtn;

    // A discovered host for the join list
    static class DiscoveredHost {
        String name;
        String host;
        int port;
        boolean resolved;
        DiscoveredHost(String name) { this.name = name; }
    }

    // Multiplayer state
    volatile boolean isHost;
    String mpStatus = "";
    ArrayList<DiscoveredHost> discoveredHosts = new ArrayList<>();
    ArrayList<RectF> hostItemRects = new ArrayList<>();
    int clientColor = Color.GREEN;
    int clientBodyColor = Color.rgb(0, 160, 0);
    volatile boolean opponentReady;
    volatile boolean localReady;
    volatile boolean opponentConnected;
    volatile boolean mpLabelVisible;
    volatile boolean mpGameOverSent;
    int mpWinner = -1;
    int mpLastScore0, mpLastScore1;
    volatile long mpLastStateTime;

    static class BossSnake {
        ArrayList<Point> body = new ArrayList<>();
        int dirX = 0, dirY = 1;
        boolean alive = false;
        int lastMoveTick = 0;
        int growthPending = 0;
        BossType type = BossType.CHASER;
        int evasionCooldown = 0;
        boolean isEvading = false;
        int hesitationTicks = 0;
    }

    enum BossType { CHASER, WALL_BUILDER }

    static class WallCell {
        int x, y;
        int createdAtTick;
        boolean dying;
        int deathStartTick;
        WallCell(int x, int y, int tick) {
            this.x = x; this.y = y; this.createdAtTick = tick;
            this.dying = false; this.deathStartTick = 0;
        }
    }

    static class BossTrailCell {
        int x, y;
        int createdAtTick;
        BossTrailCell(int x, int y, int tick) { this.x = x; this.y = y; this.createdAtTick = tick; }
    }

    BossSnake boss = new BossSnake();
    int bossGrowthPending = 0;
    ArrayList<BossTrailCell> bossTrail = new ArrayList<>();
    int nextBossSpawnScore = 125;
    int tickCount = 0;

    // Wall builder state
    ArrayList<WallCell> walls = new ArrayList<>();
    int maxWalls = 15;
    int wallPlaceInterval = 60;
    int nextWallTick = 0;
    ArrayList<Point> wallPreviewPositions = new ArrayList<>();
    int wallPreviewStartTick = 0;
    boolean wallPreviewActive = false;
    boolean wallsDying = false;

    boolean devMode = false;
    int devStartScore = 0;
    String devScoreText = "0";
    boolean editingDevScore = false;
    RectF devScoreBtn;
    int devForcedBossType = 0; // 0=RANDOM, 1=CHASER, 2=WALL_BUILDER
    boolean showBossPathfinding = false;
    RectF devBossBtn, devPathBtn;
    int bossTargetX = -1, bossTargetY = -1; // for pathfinding viz

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
        playBtn = makeBtn(cx, startY - bh - gap, bw, bh);

        // Play menu
        singleplayerBtn = makeBtn(cx, startY, bw, bh);
        multiplayerBtn = makeBtn(cx, startY + bh + gap, bw, bh);
        playBackBtn = makeBtn(cx, screenH * 0.80f, bw, bh);

        // Mode select
        arcadeBtn = makeBtn(cx, startY, bw, bh);
        modeBackBtn = makeBtn(cx, screenH * 0.80f, bw, bh);

        backBtn = makeBtn(cx, screenH * 0.90f, bw, bh);
        hostBtn = makeBtn(cx, startY, bw, bh);
        joinBtn = makeBtn(cx, startY + bh + gap, bw, bh);

        cancelBtn = makeBtn(cx, screenH * 0.80f, bw, bh);
        readyBtn = makeBtn(cx, screenH * 0.60f, bw, bh);
        forceStartBtn = makeBtn(cx, screenH * 0.72f, bw, bh);

        mpRestartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        mpMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        snakeColorBtn = makeBtn(cx, screenH * 0.22f, bw, bh);
        float previewSize = bh * 1.0f;
        float previewGap = uiCellSize * 0.3f;
        float previewTop = snakeColorBtn.bottom + previewGap;
        snakePreviewRect = new RectF(cx - bw * 0.35f, previewTop,
                                     cx + bw * 0.35f, previewTop + previewSize);

        cameraModeBtn = makeBtn(cx, screenH * 0.38f, bw, bh);

        float sliderW = bw * 0.85f;
        float sliderH = 40;
        musicSliderTrack = new RectF(cx - sliderW / 2f, screenH * 0.48f - sliderH / 2f,
                                     cx + sliderW / 2f, screenH * 0.48f + sliderH / 2f);
        sfxSliderTrack = new RectF(cx - sliderW / 2f, screenH * 0.57f - sliderH / 2f,
                                   cx + sliderW / 2f, screenH * 0.57f + sliderH / 2f);

        settingsBackBtn = makeBtn(cx, screenH * 0.70f, bw, bh);

        resumeBtn = makeBtn(cx, screenH * 0.5f, bw, bh);
        pauseMenuBtn = makeBtn(cx, screenH * 0.5f + bh + gap, bw, bh);

        restartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        overMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        lbSortBtn = makeBtn(cx, screenH * 0.20f, bw, bh * 0.8f);
        lbBackBtn = makeBtn(cx, screenH * 0.88f, bw, bh);

        devScoreBtn = makeBtn(cx, startY + (bh + gap) * 5, bw, bh * 0.8f);
        devBossBtn = makeBtn(cx, startY + (bh + gap) * 6 + uiCellSize * 0.2f, bw, bh * 0.8f);
        devPathBtn = makeBtn(cx, startY + (bh + gap) * 7 + uiCellSize * 0.4f, bw, bh * 0.8f);

        float iconSize = uiCellSize * 1.1f;
        pauseIcon = new RectF(screenW - iconSize - 16, 16, screenW - 16, 16 + iconSize);

        // Color picker layout
        float pbw = Math.min(screenW * 0.85f, 420);
        float pbh = uiCellSize * 1.5f;
        float pSliderH = Math.max(64, uiCellSize * 1.6f);
        float gap2 = uiCellSize * 0.6f;
        float swatchH = uiCellSize * 4f;
        pickerHeadBtn = makeBtn(cx - pbw * 0.25f, screenH * 0.08f, pbw * 0.4f, pbh);
        pickerBodyBtn = makeBtn(cx + pbw * 0.25f, screenH * 0.08f, pbw * 0.4f, pbh);
        float pPreviewY = screenH * 0.14f;
        float pPreviewSize = pbh * 0.7f;
        pickerSnakePreview = new RectF(cx - pbw * 0.3f, pPreviewY,
                                       cx + pbw * 0.3f, pPreviewY + pPreviewSize);
        pickerSwatch = new RectF(cx - pbw / 2f, pickerSnakePreview.bottom + gap2,
                                 cx + pbw / 2f, pickerSnakePreview.bottom + gap2 + swatchH);
        float sliderLeft = cx - pbw / 2f;
        float sliderRight = cx + pbw / 2f;
        float sliderTop = pickerSwatch.bottom + gap2;
        pickerHexField = new RectF(cx - pbw * 0.35f, sliderTop, cx + pbw * 0.35f,
                                   sliderTop + pbh);
        pickerHueBar = new RectF(sliderLeft, sliderTop + pbh + gap2,
                                 sliderRight, sliderTop + pbh + gap2 + pSliderH);
        pickerSatBar = new RectF(sliderLeft, pickerHueBar.bottom + gap2,
                                 sliderRight, pickerHueBar.bottom + gap2 + pSliderH);
        pickerValBar = new RectF(sliderLeft, pickerSatBar.bottom + gap2,
                                 sliderRight, pickerSatBar.bottom + gap2 + pSliderH);
        pickerApplyBtn = makeBtn(cx - pbw * 0.25f, pickerValBar.bottom + gap2 + pbh / 2f,
                                 pbw * 0.4f, pbh);
        pickerCancelBtn = makeBtn(cx + pbw * 0.25f, pickerValBar.bottom + gap2 + pbh / 2f,
                                  pbw * 0.4f, pbh);
    }

    static RectF makeBtn(float cx, float cy, float w, float h) {
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }
}
