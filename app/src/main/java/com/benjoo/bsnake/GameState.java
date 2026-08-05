package com.benjoo.bsnake;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.RectF;

import java.util.ArrayList;

public class GameState {

    enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEADERBOARD, SETTINGS,
                 MP_MENU, MP_HOST, MP_JOIN, MP_LOBBY, MP_PLAYING, MP_GAME_OVER,
                 COLOR_PICKER, PLAY_MENU, MODE_SELECT, BOSS_UPGRADE, BOSS_DEATH_CINEMATIC }
    volatile State currentState = State.MENU;

    enum SortMode { HIGH_SCORE, RECENT }
    SortMode sortMode = SortMode.HIGH_SCORE;

    enum CameraMode { CLASSIC_ZOOM, FULL_PLAY_AREA, FIT_VERTICAL }
    CameraMode cameraMode = CameraMode.CLASSIC_ZOOM;

    enum GameMode { ARCADE, CLASSIC }
    volatile GameMode gameMode = GameMode.ARCADE;
    int lastPlayedMode = -1; // -1 = none, 0 = ARCADE, 1 = CLASSIC
    int leaderboardMode = 0; // 0 = Arcade, 1 = Classic

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

    // Fruit types — add new special fruit types here and handle them in the
    // engine (eating effects) and renderer (appearance).
    enum FruitType { NORMAL, HEAL }

    static class Fruit {
        FruitType type;
        int x, y;
        // Wall-clock time the fruit appeared, used for spawn scale-in + pulse.
        long bornMs = 0;
        Fruit(FruitType type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.bornMs = System.currentTimeMillis();
        }
    }

    // Small visual-only particles (eat bursts, expanding rings). Position is in
    // world-cell coordinates; velocity is in cells/second.
    static class Particle {
        float x, y, vx, vy;
        long startMs, lifeMs;
        int color;
        float size;
        boolean ring;
        float rotation, rotSpeed;
        boolean glow;
        Particle(float x, float y, float vx, float vy, long startMs, long lifeMs,
                 int color, float size, boolean ring) {
            this(x, y, vx, vy, startMs, lifeMs, color, size, ring, 0, 0, false);
        }
        Particle(float x, float y, float vx, float vy, long startMs, long lifeMs,
                 int color, float size, boolean ring,
                 float rotation, float rotSpeed, boolean glow) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.startMs = startMs;
            this.lifeMs = lifeMs;
            this.color = color;
            this.size = size;
            this.ring = ring;
            this.rotation = rotation;
            this.rotSpeed = rotSpeed;
            this.glow = glow;
        }
    }

    // Body of a snake that just died, kept for the dissolve animation. The
    // renderer draws it fading out cell-by-cell while deathPending is set.
    static class DeathSnake {
        ArrayList<Point> body = new ArrayList<>();
        int headColor = Color.GREEN;
        int bodyColor = Color.GREEN;
    }

    // How long the death dissolve plays before the game-over panel appears.
    static final long DEATH_ANIM_MS = 1400;

    // A single snake's mutable state
    static class SnakeData {
        ArrayList<Point> body = new ArrayList<>();
        ArrayList<Point> prevBody = new ArrayList<>();
        ArrayList<Point> mpHostBody = new ArrayList<>();
        int dirX = 1, dirY = 0;
        ArrayList<Point> inputQueue = new ArrayList<>();
        int score = 0;
        int growthPending = 0; // ticks the tail stays attached (e.g. +2 from HEAL fruit)
        int headColor = Color.GREEN;
        int bodyColor = Color.GREEN;
        boolean alive = true;
    }

    // Two snakes: index 0 = host/local, index 1 = client/remote
    SnakeData[] snakes = new SnakeData[]{ new SnakeData(), new SnakeData() };
    int playerIndex = 0; // 0 or 1

    // Copies a dying snake's body so the renderer can play the dissolve
    // animation after the live body is cleared.
    void recordDeath(SnakeData sd) {
        death.body.clear();
        for (Point p : sd.body) death.body.add(new Point(p));
        death.headColor = sd.headColor;
        death.bodyColor = sd.bodyColor;
        deathStartMs = System.currentTimeMillis();
    }

    ArrayList<Fruit> foods = new ArrayList<>();
    // Visual-only particles spawned by eating food / boss hits.
    ArrayList<Particle> particles = new ArrayList<>();
    // Snapshot of the most recently killed snake for the dissolve animation.
    DeathSnake death = new DeathSnake();
    long deathStartMs = 0;
    volatile boolean deathPending = false;
    // Full-screen fade-in after a state change (0..1, decays in the run loop).
    float transitionFade = 0f;
    // Red tint flashed when a challenge fails (0..1, decays in the run loop).
    float flashAlpha = 0f;
    int flashColor = Color.argb(140, 255, 50, 40);
    // Challenge objectives active in the current Arcade run (empty otherwise).
    // Populated by ChallengeManager; read by the renderer for the HUD.
    ArrayList<ActiveChallenge> activeChallenges = new ArrayList<>();
    // Short-lived floating reward notifications (e.g. "+30").
    ArrayList<ChallengePopup> challengePopups = new ArrayList<>();
    // Challenge HUD: collapsed to a dot strip by default; a tap toggles the full
    // list, and challengeAutoHideUntil auto-collapses it again after a few sec.
    boolean challengePanelOpen = false;
    long challengeAutoHideUntil = 0;
    RectF challengeStripRect = new RectF();
    RectF challengePanelRect = new RectF();
    // Coin-meter animations: scorePulseMs drives a scale "pop" on the score
    // badge, scorePopMs drives a "+1" rising off the badge when food is eaten.
    long scorePulseMs = 0;
    long scorePopMs = 0;
    int scorePopAmount = 1;

    static class ChallengePopup {
        String text;
        long startMs;
        int durationMs;
        float x, y;
        ChallengePopup(String text, long startMs, int durationMs, float x, float y) {
            this.text = text;
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.x = x;
            this.y = y;
        }
    }

    // Rarity tiers for the post-boss upgrade cards. Colors used by the renderer.
    enum UpgradeRarity { COMMON, RARE, EPIC }

    // A single post-boss upgrade card: immutable flavor + mutable stack count.
    static class UpgradeCard {
        final String id;
        final String name;
        final String description;
        final String flavor;
        final UpgradeRarity rarity;
        final int maxStack;
        int stack = 0;
        UpgradeCard(String id, String name, String description, String flavor,
                    UpgradeRarity rarity, int maxStack) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.flavor = flavor;
            this.rarity = rarity;
            this.maxStack = maxStack;
        }
    }

    // Entrance animation pacing for the post-boss upgrade cards. Shared between
    // the renderer (which drives the visuals) and the input handler (which
    // refuses taps until a card has actually landed).
    static final long UPGRADE_CARD_DELAY_MS = 90;   // stagger between cards
    static final long UPGRADE_CARD_ENTRY_MS = 320;  // per-card fly-in
    static final long UPGRADE_SKIP_EXTRA_MS = 160;  // skip appears after cards

    // Post-boss upgrade selection screen.
    ArrayList<UpgradeCard> upgradeOffers = new ArrayList<>();
    RectF[] upgradeCardRects = new RectF[3];
    RectF upgradeChooseBtn;   // appears below the cards once one is selected
    RectF upgradeSkipBtn;     // always-present "skip" option at the bottom
    // Wall-clock time the offer first appeared, driving the cards' entry
    // animation. Reset to 0 when closed.
    long upgradeOpenAt = 0;
    // Index of the highlighted card (-1 = none). Picking only happens after
    // the player confirms with the Choose button.
    volatile int upgradeSelectedIndex = -1;
    // When the current selection changed + a seed for the one-shot particle
    // burst, so the pop animation and sparks are deterministic per selection.
    long upgradeSelectMs = 0;
    int upgradeSelectSeed = 0;
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
    RectF arcadeBtn, classicBtn, modeBackBtn, modePlayBtn;
    int selectedModeIndex = 0; // 0 = ARCADE, 1 = CLASSIC (mode select screen)
    RectF settingsBackBtn, cameraModeBtn;
    RectF directionButtonsBtn;
    RectF snakePreviewRect;
    RectF resumeBtn, pauseMenuBtn;
    RectF restartBtn, overMenuBtn;
    RectF lbSortBtn, lbBackBtn;
    RectF lbArcadeBtn, lbClassicBtn;
    RectF pauseIcon;
    // Larger hit target around the pause icon so it's easier to tap.
    RectF pauseHitRect;

    // On-screen direction buttons (bottom-middle row) — the button opposite to
    // the snake's current direction is hidden to reinforce the no-180° rule.
    boolean directionButtons = false;
    RectF dpadLeftBtn, dpadUpBtn, dpadDownBtn, dpadRightBtn;
    // Height (px) of the system navigation bar reported via window insets.
    // Used to keep on-screen controls above the nav bar / gesture pill.
    int navBarBottom = 0;

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
    // True once an MP run is actually in progress (between start and game-over),
    // so engine/game-view can branch on "is this a networked session".
    volatile boolean inMp;
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
    volatile boolean clientBossHit;
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
        int storedFruits = 0; // HEALER: normal fruits eaten but not respawned
        int healFruitCap = 6; // HEALER: max green healing fruits on the board
        // Highest length reached this fight — used for the health bar fraction.
        int maxSegments = 5;
        // Fractional move interval accumulator, so speed-modifying upgrades
        // (Slow Pressure) work even at sub-tick precision.
        float moveAccum = 0;
    }

    enum BossType { CHASER, WALL_BUILDER, HEALER }

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

    // Boss fight visuals
    static final long BOSS_WARNING_MS = 1000;   // red telegraph before a spawn
    long bossWarningStartMs = 0;                // >0 while "BOSS INCOMING" shows
    long bossSpawnRingStartMs = 0;              // expanding shockwave right at spawn
    float shakeMagnitude = 0f;                  // screen shake intensity (px)
    long shakeUntilMs = 0;                      // when the shake stops
    int bossFlashTicks = 0;                     // frames the boss renders white after a hit

    // Cinematic boss death sequence timing (total ~1.6s)
    static final long BOSS_DEATH_HIT_STOP_MS = 140;
    static final long BOSS_DEATH_CAMERA_WINDUP_MS = 250;  // camera lunge
    static final long BOSS_DEATH_COMPRESS_MS = 50;         // squash before explosion
    static final long BOSS_DEATH_EXPLOSION_MS = 700;
    static final long BOSS_DEATH_HOLD_MS = 160;
    static final long BOSS_DEATH_TRANSITION_MS = 300;
    static final long BOSS_DEATH_CAMERA_END_MS = BOSS_DEATH_HIT_STOP_MS
            + BOSS_DEATH_CAMERA_WINDUP_MS;
    static final long BOSS_DEATH_TOTAL_MS = BOSS_DEATH_CAMERA_END_MS
            + BOSS_DEATH_EXPLOSION_MS + BOSS_DEATH_HOLD_MS
            + BOSS_DEATH_TRANSITION_MS;

    // Cinematic state
    long cinematicStartMs = 0;
    float cinematicFocusX, cinematicFocusY;
    float cinematicCameraStartX, cinematicCameraStartY;
    int cinematicBossColor;
    ArrayList<Point> cinematicBossBody = new ArrayList<>();
    boolean cinematicExplosionTriggered = false;
    float cinematicCameraZoom = 1f;
    // Host-only: whether the boss-death cinematic has already been pushed to the
    // remote player for this death, so we don't re-send it every game tick.
    volatile boolean bossCinematicSynced = false;
    // Shockwave visual — lifecycle managed by the renderer over wall-clock time
    long cinematicShockwaveAt = 0;

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

    boolean isClassicMode() {
        return gameMode == GameMode.CLASSIC;
    }

    void configureBoard() {
        uiCellSize = Math.max(16, screenW / 20);
        if (gameMode == GameMode.CLASSIC) {
            int cellSz = Math.max(8, Math.min(screenW, screenH) / 18);
            cols = screenW / cellSz;
            rows = screenH / cellSz;
            cellSize = cellSz;
        } else {
            cols = 32;
            rows = 32;
            classicCellSize = Math.max(8, screenW / 10);
            fullAreaCellSize = Math.max(4, Math.min(screenW / cols, screenH / rows));
            fitVerticalCellSize = Math.max(4, screenH / rows);
            cellSize = classicCellSize;
        }
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
        classicBtn = makeBtn(cx, startY + bh + gap, bw, bh);
        modePlayBtn = makeBtn(cx, screenH * 0.70f, bw, bh);
        modeBackBtn = makeBtn(cx, screenH * 0.82f, bw, bh);

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
        directionButtonsBtn = makeBtn(cx, screenH * 0.64f, bw, bh * 0.8f);

        resumeBtn = makeBtn(cx, screenH * 0.5f, bw, bh);
        pauseMenuBtn = makeBtn(cx, screenH * 0.5f + bh + gap, bw, bh);

        restartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        overMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        lbSortBtn = makeBtn(cx, screenH * 0.28f, bw, bh * 0.8f);
        lbArcadeBtn = makeBtn(cx - bw * 0.28f, screenH * 0.18f, bw * 0.45f, bh * 0.8f);
        lbClassicBtn = makeBtn(cx + bw * 0.28f, screenH * 0.18f, bw * 0.45f, bh * 0.8f);
        lbBackBtn = makeBtn(cx, screenH * 0.88f, bw, bh);

        devScoreBtn = makeBtn(cx, startY + (bh + gap) * 5, bw, bh * 0.8f);
        devBossBtn = makeBtn(cx, startY + (bh + gap) * 6 + uiCellSize * 0.2f, bw, bh * 0.8f);
        devPathBtn = makeBtn(cx, startY + (bh + gap) * 7 + uiCellSize * 0.4f, bw, bh * 0.8f);

        float iconSize = uiCellSize * 1.1f;
        pauseIcon = new RectF(screenW - iconSize - 16, 16, screenW - 16, 16 + iconSize);
        pauseHitRect = new RectF(
                pauseIcon.left - 16, pauseIcon.top - 12,
                pauseIcon.right + 16, pauseIcon.bottom + 12);

        // On-screen direction buttons — a 4-way cross in the bottom-middle.
        float dSize = Math.max(96, uiCellSize * 2.4f);
        float dGap = Math.max(18, uiCellSize * 0.45f);
        float dCx = screenW / 2f;
        float dCy = screenH - navBarBottom - Math.max(64, uiCellSize * 1.6f) - dSize / 2f - 110;
        dpadUpBtn = new RectF(dCx - dSize / 2f, dCy - dSize - dGap - dSize / 2f,
                              dCx + dSize / 2f, dCy - dSize - dGap + dSize / 2f);
        dpadDownBtn = new RectF(dCx - dSize / 2f, dCy + dSize + dGap - dSize / 2f,
                                dCx + dSize / 2f, dCy + dSize + dGap + dSize / 2f);
        dpadLeftBtn = new RectF(dCx - dSize - dGap - dSize / 2f, dCy - dSize / 2f,
                                dCx - dSize - dGap + dSize / 2f, dCy + dSize / 2f);
        dpadRightBtn = new RectF(dCx + dSize + dGap - dSize / 2f, dCy - dSize / 2f,
                                 dCx + dSize + dGap + dSize / 2f, dCy + dSize / 2f);

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
