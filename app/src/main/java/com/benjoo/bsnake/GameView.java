package com.benjoo.bsnake;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
        while (running) {
            long now = System.currentTimeMillis();

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
            if ((isPlaying || isMpHost) && now - lastTick >= state.tickDelay) {
                engine.update();
                if (state.isHost) {
                    if (state.currentState == GameState.State.MP_PLAYING) {
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
                state.snakes[state.playerIndex].alive = savedAlive;
                soundEffects.setMuted(false);
                lastTick = now;
                now = System.currentTimeMillis();
            } else if (!isPlaying && !isMpHost && !isMpClient) {
                lastTick = now;
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
            Canvas canvas = holder.getSurface().isValid() ? holder.lockCanvas() : null;
            renderer.draw(canvas, t);
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
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
                    if (state.localReady && state.opponentReady) {
                        startMpGame();
                    }
                    break;
                case "clientState":
                    if (state.currentState == GameState.State.MP_PLAYING) {
                        JSONArray bodyArr = obj.getJSONArray("body");
                        ArrayList<Point> newBody = NetworkMessage.jsonToBody(bodyArr);
                        if (newBody.isEmpty()) break;
                        GameState.SnakeData sd = state.snakes[1];
                        sd.prevBody.clear();
                        for (Point p : sd.body) sd.prevBody.add(new Point(p));
                        sd.body = newBody;
                        sd.dirX = obj.getInt("dirX");
                        sd.dirY = obj.getInt("dirY");
                        sd.score = obj.getInt("score");
                        sd.alive = obj.getBoolean("alive");
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
                    state.currentState = GameState.State.MP_GAME_OVER;
                    break;
                case "ready":
                    state.opponentReady = obj.optBoolean("ready", false);
                    break;
                case "start":
                    state.opponentReady = true;
                    state.localReady = true;
                    state.mpGameOverSent = false;
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
            // Own snake: save prevBody for interpolation, keep local body
            state.snakes[1].prevBody.clear();
            for (Point p : state.snakes[1].body) state.snakes[1].prevBody.add(new Point(p));
            JSONArray scArr = obj.getJSONArray("scores");
            state.snakes[0].score = scArr.getInt(0);
            state.snakes[1].score = scArr.getInt(1);
            JSONArray drArr = obj.getJSONArray("dirs");
            state.snakes[0].dirX = drArr.getJSONArray(0).getInt(0);
            state.snakes[0].dirY = drArr.getJSONArray(0).getInt(1);
            JSONArray fdArr = obj.getJSONArray("foods");
            state.foods.clear();
            for (int i = 0; i < fdArr.length(); i++) {
                JSONArray pt = fdArr.getJSONArray(i);
                state.foods.add(new Point(pt.getInt(0), pt.getInt(1)));
            }
            if (obj.has("boss")) {
                JSONObject bj = obj.getJSONObject("boss");
                state.boss.body = NetworkMessage.jsonToBody(bj.getJSONArray("body"));
                state.boss.dirX = bj.getInt("dirX");
                state.boss.dirY = bj.getInt("dirY");
                state.boss.lastMoveTick = bj.getInt("lastMoveTick");
                state.boss.growthPending = bj.getInt("growthPending");
                state.boss.type = GameState.BossType.values()[bj.optInt("type", 0)];
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
        engine.resetSinglePlayer();
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
    public void toggleDevMode() {
        state.devMode = !state.devMode;
        if (state.devMode) { state.devScoreText = "0"; showDevScoreInput(); }
        else hideKeyboardInternal();
    }

    @Override
    public void cycleDevBossType() {
        state.devForcedBossType = (state.devForcedBossType + 1) % 3;
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
                state.opponentConnected = false;
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
        String msg = NetworkMessage.ready(state.localReady);
        if (msg != null) {
            if (state.isHost && server != null) server.send(msg);
            else if (client != null) client.send(msg);
        }
        if (state.isHost && state.localReady && state.opponentReady) {
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
        engine.resetGame();
        state.currentState = GameState.State.MP_PLAYING;
        // Force camera to local player's head position
        if (!state.snakes[state.playerIndex].body.isEmpty()) {
            Point h = state.snakes[state.playerIndex].body.get(0);
            state.cameraX = h.x;
            state.cameraY = h.y;
        }
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
