package com.benjoo.bsnake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback, InputHandler.GameActions {

    Thread thread;
    SurfaceHolder holder;
    volatile boolean running = false;
    // Set from the UI thread to request a single-player restart. The reset runs
    // on the game thread (see run()) so list mutations never race the update /
    // render loop that iterates the same collections.
    volatile boolean restartPending = false;
    // Same pattern for multiplayer game start / rematch.
    volatile boolean mpStartPending = false;

    GameState state;
    PersistenceManager persistence;
    SnakeEngine engine;
    GameRenderer renderer;
    InputHandler input;
    MenuMusic menuMusic;
    SoundEffects soundEffects;
    GameServer server;
    GameClient client;

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
        menuMusic = new MenuMusic();
        soundEffects = new SoundEffects();
        engine.setSoundEffects(soundEffects);
        persistence.loadVolumes(state);
        soundEffects.setVolume(state.sfxVolume);
        menuMusic.setVolume(state.musicVolume);
        persistence.loadColors(state);
        persistence.loadCameraMode(state);
        persistence.loadDirectionButtons(state);
    }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Keep bottom controls (e.g. the direction pad) above the Android
            // nav bar / gesture pill on edge-to-edge (Android 15+) devices.
            // Detects both the 3-button nav bar and the gesture pill: on
            // Android 11+ WindowInsets.Type.navigationBars() reports either,
            // with getSystemWindowInsetBottom() as the legacy fallback.
            setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bottom = insets.getInsets(
                            android.view.WindowInsets.Type.navigationBars()).bottom;
                }
                bottom = Math.max(bottom, insets.getSystemWindowInsetBottom());
                if (state.navBarBottom != bottom) {
                    state.navBarBottom = bottom;
                    state.layoutButtons();
                }
                return insets;
            });
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        state.screenW = getWidth();
        state.screenH = getHeight();
        state.lastPlayedMode = persistence.loadGameMode();
        if (state.lastPlayedMode >= 0 && state.lastPlayedMode < GameState.GameMode.values().length) {
            state.gameMode = GameState.GameMode.values()[state.lastPlayedMode];
        }
        state.configureBoard();
        state.layoutButtons();
        // Preserve single-player game state across app switches (don't force MENU)
        if (state.currentState != GameState.State.PLAYING
                && state.currentState != GameState.State.PAUSED
                && state.currentState != GameState.State.GAME_OVER) {
            state.currentState = GameState.State.MENU;
        }
        running = true;
        thread = new Thread(this);
        thread.start();
        menuMusic.setVolume(state.musicVolume);
        soundEffects.setVolume(state.sfxVolume);
    }

    @Override
    public void run() {
        long lastTick = System.currentTimeMillis();
        long lastFrameMs = System.currentTimeMillis();
        GameState.State lastState = null;
        while (running) {
            long now = System.currentTimeMillis();

            // Screen-transition fade and challenge-fail flash decay (per frame).
            float frameDt = Math.min(80, now - lastFrameMs);
            lastFrameMs = now;
            if (state.transitionFade > 0) {
                state.transitionFade = Math.max(0, state.transitionFade - frameDt / 350f);
            }
            if (state.flashAlpha > 0) {
                state.flashAlpha = Math.max(0, state.flashAlpha - frameDt / 450f);
            }
            // Challenge HUD auto-collapses back to the dot strip once the reveal
            // timer expires (skipped while the player has it pinned open).
            if (state.challengeAutoHideUntil > 0 && now >= state.challengeAutoHideUntil) {
                state.challengeAutoHideUntil = 0;
                state.challengePanelOpen = false;
            }

            // A restart was requested from the UI thread — perform the state
            // reset here on the game thread so it can't collide with the
            // update/render iteration happening below.
            if (restartPending) {
                restartPending = false;
                state.configureBoard();
                engine.resetSinglePlayer();
                state.currentState = GameState.State.PLAYING;
            }

            // Multiplayer start / rematch — reset and enter MP_PLAYING on the
            // game thread for the same reason as restartPending.
            if (mpStartPending) {
                mpStartPending = false;
                state.inMp = true;
                engine.resetGame();
                state.currentState = GameState.State.MP_PLAYING;
                if (!state.snakes[state.playerIndex].body.isEmpty()) {
                    Point h = state.snakes[state.playerIndex].body.get(0);
                    state.cameraX = h.x;
                    state.cameraY = h.y;
                }
            }

            boolean isMpClient = state.currentState == GameState.State.MP_PLAYING && !state.isHost;

            // Client sends its full snake state BEFORE each tick so the host
            // receives the pre-move position and both sides move in sync
            if (isMpClient && client != null) {
                String cs = NetworkMessage.clientState(
                        state.snakes[1].body,
                        state.snakes[1].dirX, state.snakes[1].dirY,
                        state.snakes[1].score,
                        state.snakes[1].alive);
                if (cs != null) client.send(cs);
            }

            // Network message processing
            processNetworkMessages();

            // Game tick
            boolean isPlaying = state.currentState == GameState.State.PLAYING;
            boolean isMpHost = state.currentState == GameState.State.MP_PLAYING && state.isHost;
            boolean isCinematic = state.currentState == GameState.State.BOSS_DEATH_CINEMATIC;
            if ((isPlaying || isMpHost) && now - lastTick >= state.tickDelay) {
                engine.update();
                if (state.isHost) {
                    if (state.currentState == GameState.State.BOSS_DEATH_CINEMATIC
                            && !state.bossCinematicSynced) {
                        state.bossCinematicSynced = true;
                        String cm = NetworkMessage.bossCinematic(
                                state.cinematicFocusX, state.cinematicFocusY,
                                state.cinematicCameraStartX, state.cinematicCameraStartY,
                                state.cinematicBossColor, state.cinematicBossBody);
                        if (cm != null && server != null) server.send(cm);
                    } else if (state.currentState == GameState.State.MP_PLAYING) {
                        sendHostState();
                    } else if (state.currentState == GameState.State.MP_GAME_OVER && !state.mpGameOverSent) {
                        int[] scores = new int[]{state.mpLastScore0, state.mpLastScore1};
                        String msg = NetworkMessage.gameOver(state.mpWinner, scores);
                        if (msg != null && server != null) server.send(msg);
                        state.mpGameOverSent = true;
                    }
                }
                lastTick = now;
                now = System.currentTimeMillis();
                    } else if (isMpClient && now - lastTick >= state.tickDelay) {
                        // Client prediction: run simulation locally for responsive input
                        soundEffects.setMuted(true);
                        boolean savedAlive = state.snakes[state.playerIndex].alive;
                        engine.update(true);
                        // If prediction cleared the local body the snake died — keep it dead
                        // so the client reports the death and doesn't get stuck as a ghost.
                        if (!state.snakes[state.playerIndex].body.isEmpty()) {
                            state.snakes[state.playerIndex].alive = savedAlive;
                        }
                        soundEffects.setMuted(false);
                        // Prediction detected a boss head-on — notify the host to apply it
                        if (state.clientBossHit && client != null) {
                            String bm = NetworkMessage.bossHit();
                            if (bm != null) client.send(bm);
                            state.clientBossHit = false;
                        }
                        lastTick = now;
                        now = System.currentTimeMillis();
                    } else if (isCinematic) {
                // Cinematic boss death sequence: drive phases by wall-clock time
                long elapsed = now - state.cinematicStartMs;
                long explosionAt = GameState.BOSS_DEATH_CAMERA_END_MS;
                // Extra shake jolt during the compression phase just before explosion
                long compressStart = explosionAt - GameState.BOSS_DEATH_COMPRESS_MS;
                if (elapsed >= compressStart && elapsed < explosionAt) {
                    state.shakeMagnitude = 10f;
                    state.shakeUntilMs = now + 60;
                }
                if (elapsed >= explosionAt && !state.cinematicExplosionTriggered) {
                    state.cinematicExplosionTriggered = true;
                    engine.triggerBossDeathExplosion();
                }
                long total = GameState.BOSS_DEATH_TOTAL_MS;
                if (elapsed >= total) {
                    engine.finishBossDefeatTransition();
                    if (state.inMp && state.isHost && server != null) {
                        String um = NetworkMessage.bossUpgrade(engine.upgradeOfferIds());
                        if (um != null) server.send(um);
                    }
                    lastState = null; // force transition fade
                }
                lastTick = now;
                    } else if (!isPlaying && !isMpHost && !isMpClient && !isCinematic) {
                lastTick = now;
            }

            // Death dissolve finished — swap to the game-over panel.
            if (state.deathPending && now - state.deathStartMs >= GameState.DEATH_ANIM_MS) {
                state.deathPending = false;
                if (state.isHost) {
                    state.mpWinner = state.snakes[0].score > state.snakes[1].score ? 0 :
                                     state.snakes[1].score > state.snakes[0].score ? 1 : -1;
                    state.currentState = GameState.State.MP_GAME_OVER;
                } else {
                    if (!state.devMode)
                        persistence.saveScore(state.snakes[0].score,
                                state.speedLabels[state.speedIndex], state.gameMode.ordinal());
                    state.currentState = GameState.State.GAME_OVER;
                }
                state.inMp = false;
            }

            // Music
            if (state.currentState == GameState.State.MENU || state.currentState == GameState.State.PLAY_MENU
                    || state.currentState == GameState.State.MODE_SELECT
                    || state.currentState == GameState.State.MP_MENU
                    || state.currentState == GameState.State.MP_HOST || state.currentState == GameState.State.MP_JOIN
                    || state.currentState == GameState.State.MP_LOBBY) {
                if (!menuMusic.isPlaying()) menuMusic.start();
            } else {
                if (menuMusic.isPlaying()) menuMusic.stop();
            }

            float t = Math.min(1f, (now - lastTick) / (float) state.tickDelay);

            // Fade-in on a screen change. Detected here — right before the
            // render — so a state set earlier in this frame (e.g. a restart)
            // fades from black on its very first drawn frame.
            if (state.currentState != lastState) {
                state.transitionFade = 1f;
                lastState = state.currentState;
            }

            Surface surface = holder.getSurface();
            boolean hardware = false;
            Canvas canvas = null;
            if (surface != null && surface.isValid()) {
                // Some devices/emulators never present buffers locked through the
                // software path, leaving a black screen — prefer the hardware canvas.
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    canvas = surface.lockHardwareCanvas();
                    hardware = canvas != null;
                }
                if (canvas == null) canvas = holder.lockCanvas();
            }
            renderer.draw(canvas, t);
            if (canvas != null) {
                if (hardware) surface.unlockCanvasAndPost(canvas);
                else holder.unlockCanvasAndPost(canvas);
            }
            try { Thread.sleep(8); } catch (InterruptedException e) { }
        }
    }

    private void processNetworkMessages() {
        if (state.isHost && server != null) {
            String msg;
            while ((msg = server.pollMessage()) != null) {
                handleHostMessage(msg);
            }
        } else if (!state.isHost && client != null) {
            String msg;
            while ((msg = client.pollMessage()) != null) {
                handleClientMessage(msg);
            }
        }
    }

    private void handleHostMessage(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.optString("type", "");
            switch (type) {
                case "hello":
                    state.clientColor = obj.optInt("color", state.headColor);
                    state.clientBodyColor = obj.optInt("bodyColor", state.bodyColor);
                    state.opponentConnected = true;
                    break;
                case "ready":
                    state.opponentReady = obj.optBoolean("ready", false);
                    tryHostStart();
                    break;
                case "clientState":
                    if (state.currentState == GameState.State.MP_PLAYING) {
                        JSONArray bodyArr = obj.getJSONArray("body");
                        ArrayList<Point> newBody = NetworkMessage.jsonToBody(bodyArr);
                        GameState.SnakeData sd = state.snakes[1];
                        boolean clientAlive = obj.optBoolean("alive", true);
                        // Empty body or reported-dead means the client's snake is gone —
                        // otherwise the host would keep a ghost body and never reach
                        // an all-dead game-over state.
                        if (newBody.isEmpty() || !clientAlive) {
                            sd.alive = false;
                            sd.body.clear();
                            break;
                        }
                        // Host already determined the client is dead (e.g. head-on);
                        // ignore stale clientState that would resurrect it mid-game.
                        if (!sd.alive) break;
                        sd.prevBody.clear();
                        for (Point p : sd.body) sd.prevBody.add(new Point(p));
                        sd.body = newBody;
                        sd.dirX = obj.getInt("dirX");
                        sd.dirY = obj.getInt("dirY");
                        sd.score = obj.getInt("score");
                        sd.alive = true;
                    }
                    break;
                case "bossHit":
                    // Client hit the boss head in its prediction — damage the boss
                    // authoritatively and credit the client for the hit.
                    if (state.currentState == GameState.State.MP_PLAYING && state.snakes[1].alive) {
                        engine.clientHitBoss();
                    }
                    break;
                case "upgradePick":
                    // The client confirmed a card (or skipped). Host is authoritative:
                    // apply it and resume the run.
                    if (state.currentState == GameState.State.BOSS_UPGRADE) {
                        engine.applyUpgrade(obj.getInt("index"));
                    }
                    break;
            }
        } catch (Exception e) { }
    }

    private void handleClientMessage(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.optString("type", "");
            switch (type) {
                case "hello":
                    state.clientColor = obj.optInt("color", state.clientColor);
                    state.clientBodyColor = obj.optInt("bodyColor", state.clientBodyColor);
                    break;
                case "state":
                    applyState(obj);
                    break;
                case "gameOver":
                    state.mpWinner = obj.optInt("winner", -1);
                    JSONArray scArr = obj.getJSONArray("scores");
                    state.mpLastScore0 = scArr.getInt(0);
                    state.mpLastScore1 = scArr.getInt(1);
                    state.inMp = false;
                    state.currentState = GameState.State.MP_GAME_OVER;
                    break;
                case "ready":
                    state.opponentReady = obj.optBoolean("ready", false);
                    break;
                case "bossCinematic":
                    // Host killed the boss — mirror the cinematic snapshot so the
                    // remote renders the same death sequence.
                    state.cinematicBossBody = NetworkMessage.jsonToBody(obj.getJSONArray("bossBody"));
                    state.cinematicFocusX = (float) obj.getDouble("focusX");
                    state.cinematicFocusY = (float) obj.getDouble("focusY");
                    state.cinematicCameraStartX = (float) obj.getDouble("camX");
                    state.cinematicCameraStartY = (float) obj.getDouble("camY");
                    state.cinematicBossColor = obj.getInt("color");
                    state.cinematicStartMs = System.currentTimeMillis();
                    state.cinematicExplosionTriggered = false;
                    state.cinematicCameraZoom = 1f;
                    state.cinematicShockwaveAt = 0;
                    state.boss.alive = false;
                    state.shakeMagnitude = 14f;
                    state.shakeUntilMs = System.currentTimeMillis() + 200;
                    state.currentState = GameState.State.BOSS_DEATH_CINEMATIC;
                    break;
                case "bossUpgrade":
                    // Host finished the cinematic and rolled the card offer. Build the
                    // shared offer (empty list = resume play).
                    JSONArray ids = obj.getJSONArray("ids");
                    ArrayList<String> idList = new ArrayList<>();
                    for (int i = 0; i < ids.length(); i++) idList.add(ids.getString(i));
                    engine.applyExternalOffer(idList);
                    state.currentState = idList.isEmpty()
                            ? GameState.State.MP_PLAYING
                            : GameState.State.BOSS_UPGRADE;
                    break;
                case "upgradePick":
                    // The host confirmed a card. Apply the same pick to stay in sync.
                    if (state.currentState == GameState.State.BOSS_UPGRADE) {
                        engine.applyUpgrade(obj.getInt("index"));
                    }
                    break;
                case "start":
                    state.opponentReady = true;
                    state.localReady = true;
                    state.mpGameOverSent = false;
                    state.inMp = true;
                    engine.resetGame();
                    state.currentState = GameState.State.MP_PLAYING;
                    // Force camera to local player's head position
                    if (!state.snakes[state.playerIndex].body.isEmpty()) {
                        Point h = state.snakes[state.playerIndex].body.get(0);
                        state.cameraX = h.x;
                        state.cameraY = h.y;
                    }
                    break;
            }
        } catch (Exception e) { }
    }

    @SuppressWarnings("deprecation")
    private void applyState(JSONObject obj) {
        try {
            if (state.currentState != GameState.State.MP_PLAYING) {
                // First state message — ensure game is initialized even if "start" was lost
                engine.resetGame();
                state.currentState = GameState.State.MP_PLAYING;
            }
            JSONArray bodyArr = obj.getJSONArray("snake");
            if (bodyArr.length() == 0) return;
            GameState.SnakeData sd0 = state.snakes[0];
            ArrayList<Point> prevHost;
            if (sd0.mpHostBody.isEmpty()) {
                prevHost = new ArrayList<>();
                for (Point p : sd0.body) prevHost.add(new Point(p));
            } else {
                prevHost = new ArrayList<>(sd0.mpHostBody);
            }
            sd0.prevBody.clear();
            sd0.prevBody.addAll(prevHost);
            sd0.body.clear();
            for (int j = 0; j < bodyArr.length(); j++) {
                JSONArray pt = bodyArr.getJSONArray(j);
                sd0.body.add(new Point(pt.getInt(0), pt.getInt(1)));
            }
            sd0.mpHostBody.clear();
            for (Point p : sd0.body) sd0.mpHostBody.add(new Point(p));
            // The client's own snake ([]1) is driven by local prediction, and the
            // engine already maintains its prevBody/body for interpolation each
            // tick. Resetting prevBody to body on every host state would snap the
            // snake forward past its in-flight glide and make it stutter.
            JSONArray scArr = obj.getJSONArray("scores");
            state.snakes[0].score = scArr.getInt(0);
            state.snakes[1].score = scArr.getInt(1);
            JSONArray drArr = obj.getJSONArray("dirs");
            state.snakes[0].dirX = drArr.getJSONArray(0).getInt(0);
            state.snakes[0].dirY = drArr.getJSONArray(0).getInt(1);
            JSONArray fdArr = obj.getJSONArray("foods");
            // Rebuild with each food's existing bornMs preserved. Recreating fruit
            // with a fresh bornMs on every host state would restart its spawn
            // scale-in + pulse animation each message, making the food flicker.
            Map<String, Long> bornByKey = new HashMap<>();
            for (GameState.Fruit base : state.foods) {
                bornByKey.put(base.type + ":" + base.x + ":" + base.y, base.bornMs);
            }
            state.foods.clear();
            for (int i = 0; i < fdArr.length(); i++) {
                JSONArray pt = fdArr.getJSONArray(i);
                GameState.FruitType ft = GameState.FruitType.values()[pt.optInt(2, 0)];
                GameState.Fruit f = new GameState.Fruit(ft, pt.getInt(0), pt.getInt(1));
                Long prevBorn = bornByKey.get(ft + ":" + f.x + ":" + f.y);
                if (prevBorn != null) f.bornMs = prevBorn;
                state.foods.add(f);
            }
            if (obj.has("boss")) {
                JSONObject bj = obj.getJSONObject("boss");
                state.boss.body = NetworkMessage.jsonToBody(bj.getJSONArray("body"));
                state.boss.dirX = bj.getInt("dirX");
                state.boss.dirY = bj.getInt("dirY");
                state.boss.lastMoveTick = bj.getInt("lastMoveTick");
                state.boss.growthPending = bj.getInt("growthPending");
                state.boss.type = GameState.BossType.values()[bj.optInt("type", 0)];
                state.boss.storedFruits = bj.optInt("storedFruits", 0);
                state.boss.alive = true;
            } else {
                state.boss.alive = false;
            }
            JSONArray trArr = obj.getJSONArray("trail");
            state.bossTrail.clear();
            for (int i = 0; i < trArr.length(); i++) {
                JSONArray tc = trArr.getJSONArray(i);
                state.bossTrail.add(new GameState.BossTrailCell(tc.getInt(0), tc.getInt(1), tc.getInt(2)));
            }
            // Walls
            state.walls.clear();
            if (obj.has("walls")) {
                JSONArray wlArr = obj.getJSONArray("walls");
                for (int i = 0; i < wlArr.length(); i++) {
                    JSONArray wc = wlArr.getJSONArray(i);
                    GameState.WallCell w = new GameState.WallCell(
                            wc.getInt(0), wc.getInt(1), wc.getInt(2));
                    w.dying = wc.getInt(3) == 1;
                    w.deathStartTick = wc.getInt(4);
                    state.walls.add(w);
                }
            }
            state.wallPreviewPositions.clear();
            if (obj.has("wallPP")) {
                JSONArray wpArr = obj.getJSONArray("wallPP");
                for (int i = 0; i < wpArr.length(); i++) {
                    JSONArray pt = wpArr.getJSONArray(i);
                    state.wallPreviewPositions.add(new Point(pt.getInt(0), pt.getInt(1)));
                }
            }
            state.wallPreviewStartTick = obj.optInt("wallPST", 0);
            state.wallPreviewActive = obj.optBoolean("wallPA", false);
            state.nextWallTick = obj.optInt("nextWT", 0);
            state.tickCount = obj.getInt("tick");
            JSONArray alArr = obj.getJSONArray("alive");
            state.snakes[0].alive = alArr.getBoolean(0);
            state.snakes[1].alive = alArr.getBoolean(1);
            JSONArray hcArr = obj.getJSONArray("headColors");
            state.snakes[0].headColor = hcArr.getInt(0);
            state.snakes[1].headColor = hcArr.getInt(1);
            JSONArray bcArr = obj.getJSONArray("bodyColors");
            state.snakes[0].bodyColor = bcArr.getInt(0);
            state.snakes[1].bodyColor = bcArr.getInt(1);
            state.mpLastStateTime = System.currentTimeMillis();
        } catch (Exception e) { }
    }

    private void sendHostState() {
        if (server == null) return;
        String msg = NetworkMessage.state(
                state.snakes[0].body,
                state.snakes[0].score, state.snakes[1].score,
                state.snakes[0].dirX, state.snakes[0].dirY,
                state.snakes[0].alive, state.snakes[1].alive,
                state.foods, state.boss, state.bossTrail, state.tickCount,
                state.walls,
                state.wallPreviewPositions, state.wallPreviewStartTick,
                state.wallPreviewActive, state.nextWallTick,
                state.snakes[0].headColor, state.snakes[1].headColor,
                state.snakes[0].bodyColor, state.snakes[1].bodyColor);
        if (msg != null) server.send(msg);
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
        if (server != null) { server.stop(); server = null; }
        if (client != null) { client.stop(); client = null; }
        menuMusic.pause();
        soundEffects.stopAll();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        menuMusic.release();
        soundEffects.release();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return input.onTouchEvent(event);
    }

    // ----- GameActions implementation -----

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
        state.playerIndex = 0;
        stopNetworking();
        state.lastPlayedMode = state.gameMode.ordinal();
        persistence.saveGameMode(state.lastPlayedMode);
        // The actual reset (configureBoard + engine.resetSinglePlayer + entering
        // PLAYING) is deferred to the game thread via restartPending to avoid
        // racing the update/render loop.
        restartPending = true;
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
    public void openColorPicker() {
        state.pickerOrigHeadColor = state.headColor;
        state.pickerOrigBodyColor = state.bodyColor;
        state.pickerTarget = 0;
        float[] hsv = new float[3];
        Color.colorToHSV(state.headColor, hsv);
        state.pickerHue = hsv[0];
        state.pickerSat = hsv[1];
        state.pickerVal = hsv[2];
        state.pickerColor = state.headColor;
        state.pickerHex = String.format(Locale.US, "#%06X", state.headColor & 0xFFFFFF);
        state.pickerEditingHex = false;
        state.editingColor = -1;
        state.currentState = GameState.State.COLOR_PICKER;
    }

    @Override
    public void applyColorPicker() {
        if (state.pickerTarget == 0) {
            state.headColor = state.pickerColor;
            state.headHex = state.pickerHex;
        } else {
            state.bodyColor = state.pickerColor;
            state.bodyHex = state.pickerHex;
        }
        persistence.saveColors(state.headColor, state.bodyColor);
        hideKeyboardInternal();
        state.currentState = GameState.State.MENU;
    }

    @Override
    public void setPickerHue(float hue) {
        state.pickerHue = hue;
        state.pickerColor = Color.HSVToColor(new float[]{ state.pickerHue, state.pickerSat, state.pickerVal });
        state.pickerHex = String.format(Locale.US, "#%06X", state.pickerColor & 0xFFFFFF);
        invalidate();
    }

    @Override
    public void setPickerSat(float sat) {
        state.pickerSat = sat;
        state.pickerColor = Color.HSVToColor(new float[]{ state.pickerHue, state.pickerSat, state.pickerVal });
        state.pickerHex = String.format(Locale.US, "#%06X", state.pickerColor & 0xFFFFFF);
        invalidate();
    }

    @Override
    public void setPickerVal(float val) {
        state.pickerVal = val;
        state.pickerColor = Color.HSVToColor(new float[]{ state.pickerHue, state.pickerSat, state.pickerVal });
        state.pickerHex = String.format(Locale.US, "#%06X", state.pickerColor & 0xFFFFFF);
        invalidate();
    }

    @Override
    public void togglePickerTarget() {
        // Save current edits to the current target
        if (state.pickerTarget == 0) {
            state.headColor = state.pickerColor;
            state.headHex = state.pickerHex;
        } else {
            state.bodyColor = state.pickerColor;
            state.bodyHex = state.pickerHex;
        }
        // Switch target
        state.pickerTarget = state.pickerTarget == 0 ? 1 : 0;
        int color = state.pickerTarget == 0 ? state.headColor : state.bodyColor;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        state.pickerHue = hsv[0];
        state.pickerSat = hsv[1];
        state.pickerVal = hsv[2];
        state.pickerColor = color;
        state.pickerHex = String.format(Locale.US, "#%06X", color & 0xFFFFFF);
        state.pickerEditingHex = false;
        invalidate();
    }

    @Override
    public void editPickerHex() {
        if (keyboardInput == null) return;
        state.pickerEditingHex = true;
        state.editingColor = 2; // special value for picker hex
        keyboardInput.setText(state.pickerHex);
        keyboardInput.setSelection(keyboardInput.length());
        keyboardInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        keyboardInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT);
        invalidate();
    }

    @Override
    public void dismissKeyboard() { hideKeyboardInternal(); }

    @Override
    public void exitApp() {
        Context ctx = getContext();
        if (ctx instanceof Activity) ((Activity) ctx).finish();
    }

    @Override
    public void toggleCameraMode() {
        GameState.CameraMode[] modes = GameState.CameraMode.values();
        state.cameraMode = modes[(state.cameraMode.ordinal() + 1) % modes.length];
        persistence.saveCameraMode(state.cameraMode);
        state.configureBoard();
        state.layoutButtons();
    }

    @Override
    public void toggleDirectionButtons() {
        state.directionButtons = !state.directionButtons;
        persistence.saveDirectionButtons(state.directionButtons);
    }

    @Override
    public void toggleDevMode() {
        state.devMode = !state.devMode;
        if (state.devMode) { state.devScoreText = "0"; showDevScoreInput(); }
        else hideKeyboardInternal();
    }

    @Override
    public void cycleDevBossType() {
        state.devForcedBossType = (state.devForcedBossType + 1) % 4;
    }

    @Override
    public void toggleDevPathfinding() {
        state.showBossPathfinding = !state.showBossPathfinding;
    }

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

    private void hideKeyboardInternal() {
        if (keyboardInput == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(keyboardInput.getWindowToken(), 0);
        keyboardInput.clearFocus();
        state.editingColor = -1;
        state.editingDevScore = false;
        state.pickerEditingHex = false;
    }

    @Override
    public void playClick() { soundEffects.playClick(); }

    @Override
    public void playBossDamage() { soundEffects.playBossDamage(); }

    @Override
    public void playPause() { soundEffects.playPause(); }

    @Override
    public void onUpgradeCardTap(int index) { engine.onUpgradeCardTap(index); }

    @Override
    public void onUpgradeChoose() {
        int idx = state.upgradeSelectedIndex;
        engine.onUpgradeChoose();
        broadcastUpgradePick(idx);
    }

    @Override
    public void onUpgradeSkip() {
        engine.onUpgradeSkip();
        broadcastUpgradePick(-1);
    }

    // MP: tell the other player which card (index) was confirmed so both sides
    // apply the same pick and return to MP_PLAYING together.
    private void broadcastUpgradePick(int index) {
        if (!state.inMp) return;
        String m = NetworkMessage.upgradePick(index);
        if (m == null) return;
        if (state.isHost && server != null) server.send(m);
        else if (client != null) client.send(m);
    }

    @Override
    public void setMusicVolume(float vol) {
        state.musicVolume = vol;
        menuMusic.setVolume(vol);
        persistence.saveVolumes(state.musicVolume, state.sfxVolume);
    }

    @Override
    public void setSfxVolume(float vol) {
        state.sfxVolume = vol;
        soundEffects.setVolume(vol);
        persistence.saveVolumes(state.musicVolume, state.sfxVolume);
    }

    // ----- Multiplayer actions -----

    @Override
    public void openMpMenu() {
        state.currentState = GameState.State.MP_MENU;
    }

    @Override
    public void startHost() {
        stopNetworking();
        state.playerIndex = 0;
        state.opponentConnected = false;
        state.opponentReady = false;
        state.localReady = false;
        state.mpStatus = "Starting server...";
        server = new GameServer(getContext(), new GameServer.ServerCallback() {
            @Override
            public void onClientConnected() {
                state.mpStatus = "Client connected!";
                server.send(NetworkMessage.hello(state.headColor, state.bodyColor));
                state.currentState = GameState.State.MP_LOBBY;
            }
            @Override
            public void onMessage(String msg) { }
            @Override
            public void onClientDisconnected() {
                // A fresh lobby must not inherit stale readiness from the old client
                state.opponentConnected = false;
                state.opponentReady = false;
                state.localReady = false;
                state.inMp = false;
                state.currentState = GameState.State.MENU;
            }
        });
        state.isHost = true;
        if (server.start()) {
            String device = android.os.Build.MODEL != null ? android.os.Build.MODEL : "Android";
            state.mpStatus = "Advertising as: BSnake - " + device;
            state.currentState = GameState.State.MP_HOST;
        } else {
            state.mpStatus = "Failed to start server!";
        }
    }

    @Override
    public void startJoin() {
        stopNetworking();
        state.playerIndex = 1;
        state.opponentConnected = false;
        state.opponentReady = false;
        state.localReady = false;
        state.discoveredHosts.clear();
        state.mpStatus = "Scanning for hosts...";
        client = new GameClient(getContext(), new GameClient.ClientCallback() {
            @Override
            public void onDiscoveryStarted() {
                state.mpStatus = "Scanning for hosts...";
            }
            @Override
            public void onDiscoveryFailed() {
                state.mpStatus = "Discovery failed!";
            }
            @Override
            public void onHostFound(String name, String host, int port) {
                for (GameState.DiscoveredHost dh : state.discoveredHosts) {
                    if (dh.host != null && dh.host.equals(host) && dh.port == port) return;
                }
                GameState.DiscoveredHost dh = new GameState.DiscoveredHost(name);
                dh.host = host;
                dh.port = port;
                dh.resolved = true;
                state.discoveredHosts.add(dh);
                state.mpStatus = "Found " + state.discoveredHosts.size() + " host(s)";
            }
            @Override
            public void onConnected() {
                state.opponentConnected = true;
                state.mpStatus = "Connected!";
                client.send(NetworkMessage.hello(state.headColor, state.bodyColor));
                state.currentState = GameState.State.MP_LOBBY;
            }
            @Override
            public void onConnectFailed() {
                state.mpStatus = "Connection failed!";
            }
            @Override
            public void onMessage(String msg) { }
            @Override
            public void onDisconnected() {
                if (state.currentState == GameState.State.MP_PLAYING) {
                    state.inMp = false;
                    state.currentState = GameState.State.MENU;
                }
            }
        });
        state.isHost = false;
        client.startDiscovery();
        state.currentState = GameState.State.MP_JOIN;
    }

    @Override
    public void cancelMp() {
        stopNetworking();
        state.currentState = GameState.State.MENU;
    }

    @Override
    public void toggleReady() {
        state.localReady = !state.localReady;
        // Always tell the other party about our readiness change
        String msg = NetworkMessage.ready(state.localReady);
        if (msg != null) {
            if (state.isHost && server != null) server.send(msg);
            else if (client != null) client.send(msg);
        }
        tryHostStart();
    }

    // The host is the only one who starts the game, and only once both players
    // have confirmed they are ready.
    private void tryHostStart() {
        if (state.isHost && state.currentState == GameState.State.MP_LOBBY
                && state.localReady && state.opponentReady) {
            startMpGame();
        }
    }

    @Override
    public void forceStart() {
        if (state.isHost) {
            startMpGame();
        }
    }

    private void startMpGame() {
        state.mpGameOverSent = false;
        if (state.isHost && server != null) {
            server.send(NetworkMessage.startGame());
        }
        // engine.resetGame() and the MP_PLAYING transition are deferred to the
        // game thread via mpStartPending (startMpGame can be reached from the
        // UI thread via ready/force-start/rematch).
        mpStartPending = true;
    }

    @Override
    public void rematch() {
        if (state.isHost) {
            startMpGame();
        }
    }

    @Override
    public void connectToHost(int index) {
        if (client == null || index < 0 || index >= state.discoveredHosts.size()) return;
        GameState.DiscoveredHost dh = state.discoveredHosts.get(index);
        if (!dh.resolved) return;
        state.mpStatus = "Connecting to " + dh.name + "...";
        client.connectTo(dh.host, dh.port);
    }

    @Override
    public void sendSwipe(int dx, int dy) {
        // Local prediction: enqueue input immediately
        GameState.SnakeData sd = state.snakes[state.playerIndex];
        if (sd.alive) {
            Point lastDir = sd.inputQueue.isEmpty()
                    ? new Point(sd.dirX, sd.dirY)
                    : sd.inputQueue.get(sd.inputQueue.size() - 1);
            if (!(dx == -lastDir.x && dy == -lastDir.y) && sd.inputQueue.size() < 2) {
                if (!(dx == lastDir.x && dy == lastDir.y)) {
                    sd.inputQueue.add(new Point(dx, dy));
                }
            }
        }
    }

    private void stopNetworking() {
        if (server != null) { server.stop(); server = null; }
        if (client != null) { client.stop(); client = null; }
        state.opponentConnected = false;
        state.opponentReady = false;
        state.localReady = false;
        state.isHost = false;
        state.inMp = false;
        state.mpStatus = "";
        state.discoveredHosts.clear();
        state.hostItemRects.clear();
    }

    // ----- Keyboard setup -----

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
                } else if (state.editingColor == 2 && state.currentState == GameState.State.COLOR_PICKER) {
                    state.pickerHex = s.toString();
                    Integer c = persistence.parseHexColor(state.pickerHex);
                    if (c != null) {
                        state.pickerColor = c;
                        float[] hsv = new float[3];
                        Color.colorToHSV(c, hsv);
                        state.pickerHue = hsv[0];
                        state.pickerSat = hsv[1];
                        state.pickerVal = hsv[2];
                    }
                }
                invalidate();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

}
