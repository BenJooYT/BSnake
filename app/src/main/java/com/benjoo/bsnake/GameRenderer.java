package com.benjoo.bsnake;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

class GameRenderer {

    private final GameState state;
    private final PersistenceManager persistence;
    private final Paint paint = new Paint();

    GameRenderer(GameState state, PersistenceManager persistence) {
        this.state = state;
        this.persistence = persistence;
        paint.setAntiAlias(true);
    }

    void draw(Canvas canvas, float t) {
        if (canvas == null) return;
        canvas.drawColor(Color.BLACK);
        switch (state.currentState) {
            case MENU: drawMenu(canvas); break;
            case PLAY_MENU: drawPlayMenu(canvas); break;
            case MODE_SELECT: drawModeSelect(canvas); break;
            case LEADERBOARD: drawLeaderboard(canvas); break;
            case SETTINGS: drawSettings(canvas); break;
            case MP_MENU: drawMpMenu(canvas); break;
            case MP_HOST: drawHostScreen(canvas); break;
            case MP_JOIN: drawJoinScreen(canvas); break;
            case MP_LOBBY: drawLobby(canvas); break;
            case PLAYING:
                drawGameField(canvas, t, false);
                drawPauseIcon(canvas);
                break;
            case MP_PLAYING:
                drawGameField(canvas, t, false);
                break;
            case PAUSED:
                drawGameField(canvas, 1f, false);
                drawDim(canvas);
                drawPausedOverlay(canvas);
                break;
            case GAME_OVER:
                drawGameField(canvas, 1f, false);
                drawDim(canvas);
                drawGameOverOverlay(canvas);
                break;
            case MP_GAME_OVER:
                drawGameField(canvas, 1f, true);
                drawDim(canvas);
                drawMpGameOverOverlay(canvas);
                break;
            case COLOR_PICKER:
                drawColorPicker(canvas);
                break;
        }
    }

    private void drawGameField(Canvas canvas, float t, boolean mpGameOver) {
        int savedCellSize = state.cellSize;
        float savedViewportW = state.viewportWidthCells;
        float savedViewportH = state.viewportHeightCells;
        boolean spectator = state.currentState == GameState.State.MP_GAME_OVER
                || (state.currentState == GameState.State.MP_PLAYING
                && !state.snakes[state.playerIndex].alive);
        if (!state.isClassicMode() && (spectator || state.cameraMode != GameState.CameraMode.CLASSIC_ZOOM)) {
            state.cellSize = state.fullAreaCellSize;
            state.viewportWidthCells = state.screenW / (float) state.cellSize;
            state.viewportHeightCells = state.screenH / (float) state.cellSize;
        }
        updateCamera(t);
        drawBoard(canvas);
        float viewCameraX = state.snakes[state.playerIndex].body.isEmpty() ? state.cols / 2f - 0.5f : state.cameraX;
        float viewCameraY = state.snakes[state.playerIndex].body.isEmpty() ? state.rows / 2f - 0.5f : state.cameraY;
        canvas.save();
        clipToWorld(canvas, viewCameraX, viewCameraY);

        // Draw both snakes
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive && !mpGameOver) continue;
            if (sd.body.isEmpty()) continue;
            int n = Math.min(sd.body.size(), sd.prevBody.size());
            for (int i = 0; i < n; i++) {
                paint.setColor(i == 0 ? sd.headColor : sd.bodyColor);
                Point cur = sd.body.get(i);
                Point prev = sd.prevBody.size() > i ? sd.prevBody.get(i) : cur;
                float dx = cur.x - prev.x;
                float dy = cur.y - prev.y;
                boolean wrapped = Math.abs(dx) > 1 || Math.abs(dy) > 1;
                float worldX, worldY;
                if (wrapped) { worldX = cur.x; worldY = cur.y; }
                else { worldX = prev.x + dx * t; worldY = prev.y + dy * t; }
                float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f
                        + wrappedDelta(worldX - viewCameraX, state.cols));
                float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f
                        + wrappedDelta(worldY - viewCameraY, state.rows));
                canvas.drawRect(px, py, px + state.cellSize - 1, py + state.cellSize - 1, paint);
            }
            // "YOU" label on local player's head (multiplayer only)
            if (si == state.playerIndex && state.mpLabelVisible && !sd.body.isEmpty()) {
                Point cur = sd.body.get(0);
                Point prev = sd.prevBody.size() > 0 ? sd.prevBody.get(0) : cur;
                float dx = cur.x - prev.x;
                float dy = cur.y - prev.y;
                boolean wrapped = Math.abs(dx) > 1 || Math.abs(dy) > 1;
                float worldX = wrapped ? cur.x : prev.x + dx * t;
                float worldY = wrapped ? cur.y : prev.y + dy * t;
                float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f
                        + wrappedDelta(worldX - viewCameraX, state.cols));
                float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f
                        + wrappedDelta(worldY - viewCameraY, state.rows));
                paint.setColor(Color.WHITE);
                paint.setTextSize(state.cellSize * 0.35f);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText("YOU", px, py - 6, paint);
            }
        }

        // Food
        paint.setColor(Color.RED);
        for (Point f : state.foods) {
            float foodDx = f.x - viewCameraX;
            float foodDy = f.y - viewCameraY;
            if (Math.abs(foodDx) >= state.viewportWidthCells / 2f
                    || Math.abs(foodDy) >= state.viewportHeightCells / 2f) {
                drawFoodArrow(canvas, foodDx, foodDy);
                continue;
            }
            float cx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f + foodDx);
            float cy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f + foodDy);
            canvas.drawCircle(cx, cy, Math.max(4, state.cellSize / 2f - 4), paint);
        }

        // Boss — drawn as a snake with type-specific colors
        if (state.boss.alive && !state.boss.body.isEmpty()) {
            boolean isWallBuilder = state.boss.type == GameState.BossType.WALL_BUILDER;
            for (int i = 0; i < state.boss.body.size(); i++) {
                Point seg = state.boss.body.get(i);
                float bDx = seg.x - viewCameraX;
                float bDy = seg.y - viewCameraY;
                if (Math.abs(bDx) >= state.viewportWidthCells / 2f
                        || Math.abs(bDy) >= state.viewportHeightCells / 2f) continue;
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + bDy);
                if (i == 0) {
                    paint.setColor(isWallBuilder ? Color.rgb(0, 140, 255) : Color.rgb(200, 60, 220));
                } else {
                    if (isWallBuilder) {
                        int dim = Math.max(120, 255 - i * 20);
                        paint.setColor(Color.rgb(255, dim / 2, 0));
                    } else {
                        int dim = Math.max(80, 180 - i * 15);
                        paint.setColor(Color.rgb(dim, dim / 3, dim));
                    }
                }
                canvas.drawRect(bx, by, bx + state.cellSize - 1, by + state.cellSize - 1, paint);
            }
            // Boss segment count label above head
            Point head = state.boss.body.get(0);
            float bDx = head.x - viewCameraX;
            float bDy = head.y - viewCameraY;
            if (Math.abs(bDx) < state.viewportWidthCells / 2f
                    && Math.abs(bDy) < state.viewportHeightCells / 2f) {
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + bDy);
                paint.setColor(Color.WHITE);
                paint.setTextSize(state.cellSize * 0.5f);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText("B" + state.boss.body.size(), bx, by - 4, paint);
            }
        }

        // Boss pathfinding visualization
        if (state.showBossPathfinding && state.boss.alive && !state.boss.body.isEmpty()
                && state.bossTargetX >= 0) {
            Point head = state.boss.body.get(0);
            float hx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + (head.x - viewCameraX));
            float hy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + (head.y - viewCameraY));
            // Target marker
            float tdx = state.bossTargetX - viewCameraX;
            float tdy = state.bossTargetY - viewCameraY;
            if (Math.abs(tdx) < state.viewportWidthCells / 2f && Math.abs(tdy) < state.viewportHeightCells / 2f) {
                float tx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + tdx);
                float ty = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + tdy);
                paint.setColor(Color.argb(180, 0, 255, 100));
                canvas.drawCircle(tx + state.cellSize / 2f, ty + state.cellSize / 2f, state.cellSize * 0.8f, paint);
                // Line from boss head to target
                paint.setColor(Color.argb(120, 0, 255, 100));
                paint.setStrokeWidth(2);
                canvas.drawLine(hx + state.cellSize / 2f, hy + state.cellSize / 2f,
                        tx + state.cellSize / 2f, ty + state.cellSize / 2f, paint);
                paint.setStrokeWidth(0);
            }
            // Direction arrow
            float arrowLen = state.cellSize * 1.5f;
            float ax = hx + state.cellSize / 2f + state.boss.dirX * arrowLen;
            float ay = hy + state.cellSize / 2f + state.boss.dirY * arrowLen;
            paint.setColor(Color.argb(200, 255, 255, 0));
            paint.setStrokeWidth(3);
            canvas.drawLine(hx + state.cellSize / 2f, hy + state.cellSize / 2f, ax, ay, paint);
            paint.setStrokeWidth(0);
            // Danger radius around player
            for (int si = 0; si < 2; si++) {
                if (!state.snakes[si].alive || state.snakes[si].body.isEmpty()) continue;
                Point ph = state.snakes[si].body.get(0);
                float pdx = ph.x - viewCameraX;
                float pdy = ph.y - viewCameraY;
                if (Math.abs(pdx) >= state.viewportWidthCells / 2f
                        || Math.abs(pdy) >= state.viewportHeightCells / 2f) continue;
                float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + pdx);
                float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + pdy);
                float r = 7 * state.cellSize;
                paint.setColor(Color.argb(60, 255, 50, 50));
                canvas.drawCircle(px + state.cellSize / 2f, py + state.cellSize / 2f, r, paint);
            }
        }

        // Trail
        for (GameState.BossTrailCell tc : state.bossTrail) {
            float tDx = tc.x - viewCameraX;
            float tDy = tc.y - viewCameraY;
            if (Math.abs(tDx) >= state.viewportWidthCells / 2f
                    || Math.abs(tDy) >= state.viewportHeightCells / 2f) continue;
            float tx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + tDx);
            float ty = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + tDy);
            float age = (state.tickCount - tc.createdAtTick) / 40f;
            int alpha = (int) (255 * (1f - age * 0.5f));
            alpha = Math.max(120, Math.min(255, alpha));
            paint.setColor(Color.argb(alpha, 255, 200, 50));
            float inset = state.cellSize * 0.15f;
            canvas.drawRect(tx + inset, ty + inset, tx + state.cellSize - 1 - inset,
                    ty + state.cellSize - 1 - inset, paint);
        }

        // Walls — draw with grow/death animations
        for (GameState.WallCell w : state.walls) {
            float wDx = w.x - viewCameraX;
            float wDy = w.y - viewCameraY;
            if (Math.abs(wDx) >= state.viewportWidthCells / 2f
                    || Math.abs(wDy) >= state.viewportHeightCells / 2f) continue;
            float wx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + wDx);
            float wy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + wDy);
            int cellS = state.cellSize;

            if (w.dying) {
                // Death animation: shrink + fade
                int deathElapsed = state.tickCount - w.deathStartTick;
                float deathT = Math.min(1f, deathElapsed / 15f);
                int alpha = (int) (255 * (1f - deathT));
                float scale = 1f - deathT * 0.5f;
                float inset = cellS * (1f - scale) / 2f;
                paint.setColor(Color.argb(alpha, 255, 50, 50));
                canvas.drawRect(wx + inset, wy + inset, wx + cellS - 1 - inset, wy + cellS - 1 - inset, paint);
            } else {
                // Quick grow-in, starts near full size
                int growElapsed = state.tickCount - w.createdAtTick;
                float growT = Math.min(1f, growElapsed / 3f);
                float scale = 0.85f + growT * 0.15f;
                float inset = cellS * (1f - scale) / 2f;
                paint.setColor(Color.RED);
                canvas.drawRect(wx + inset, wy + inset, wx + cellS - 1 - inset, wy + cellS - 1 - inset, paint);
            }
        }

        // Wall preview flash
        if (state.wallPreviewActive) {
            for (Point preview : state.wallPreviewPositions) {
                float pDx = preview.x - viewCameraX;
                float pDy = preview.y - viewCameraY;
                if (Math.abs(pDx) >= state.viewportWidthCells / 2f
                        || Math.abs(pDy) >= state.viewportHeightCells / 2f) continue;
                float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + pDx);
                float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + pDy);
                int elapsed = state.tickCount - state.wallPreviewStartTick;
                float flash = (float) Math.sin(elapsed * Math.PI * 0.5);
                int alpha = (int) (100 + 155 * Math.abs(flash));
                // Red flash warns if adjacent to player head
                boolean warning = isWallPreviewAdjacentToPlayer(preview.x, preview.y);
                paint.setColor(warning ? Color.argb(alpha, 255, 60, 60) : Color.argb(alpha, 255, 150, 80));
                canvas.drawRect(px, py, px + state.cellSize - 1, py + state.cellSize - 1, paint);
            }
        }

        canvas.restore();

        // Score label
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        paint.setTypeface(Typeface.DEFAULT);
        String scoreLabel;
        if (state.currentState == GameState.State.MP_PLAYING || state.currentState == GameState.State.MP_GAME_OVER) {
            int sum = state.snakes[0].score + state.snakes[1].score;
            int youIdx = state.playerIndex;
            int partnerIdx = 1 - youIdx;
            scoreLabel = "YOU:" + state.snakes[youIdx].score + " PARTNER:" + state.snakes[partnerIdx].score + " SUM:" + sum;
        } else {
            scoreLabel = "Score: " + state.snakes[0].score;
            if (state.devMode) scoreLabel += " [DEV]";
        }
        canvas.drawText(scoreLabel, 10, 40, paint);

        state.cellSize = savedCellSize;
        state.viewportWidthCells = savedViewportW;
        state.viewportHeightCells = savedViewportH;
    }

    private void drawBoard(Canvas canvas) {
        float boardWidth = state.screenW;
        float boardHeight = state.screenH;
        float centerX = state.boardLeft + boardWidth / 2f;
        float centerY = state.boardTop + boardHeight / 2f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(Color.rgb(70, 70, 70));
        canvas.save();
        clipToWorld(canvas, state.cameraX, state.cameraY);
        for (int worldX = 0; worldX < state.cols - 1; worldX++) {
            float x = centerX + (worldX + 0.5f - state.cameraX) * state.cellSize;
            canvas.drawLine(x, state.boardTop, x, state.boardTop + boardHeight, paint);
        }
        for (int worldY = 0; worldY < state.rows - 1; worldY++) {
            float y = centerY + (worldY + 0.5f - state.cameraY) * state.cellSize;
            canvas.drawLine(state.boardLeft, y, state.boardLeft + boardWidth, y, paint);
        }
        canvas.restore();
        paint.setStrokeWidth(6);
        paint.setColor(Color.RED);
        float leftEdge = centerX + (-0.5f - state.cameraX) * state.cellSize;
        float rightEdge = centerX + (state.cols - 0.5f - state.cameraX) * state.cellSize;
        float topEdge = centerY + (-0.5f - state.cameraY) * state.cellSize;
        float bottomEdge = centerY + (state.rows - 0.5f - state.cameraY) * state.cellSize;
        canvas.drawLine(leftEdge, state.boardTop, leftEdge, state.boardTop + boardHeight, paint);
        canvas.drawLine(rightEdge, state.boardTop, rightEdge, state.boardTop + boardHeight, paint);
        canvas.drawLine(state.boardLeft, topEdge, state.boardLeft + boardWidth, topEdge, paint);
        canvas.drawLine(state.boardLeft, bottomEdge, state.boardLeft + boardWidth, bottomEdge, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void clipToWorld(Canvas canvas, float viewCameraX, float viewCameraY) {
        float centerX = state.boardLeft + state.screenW / 2f;
        float centerY = state.boardTop + state.screenH / 2f;
        float left = centerX + (-0.5f - viewCameraX) * state.cellSize;
        float right = centerX + (state.cols - 0.5f - viewCameraX) * state.cellSize;
        float top = centerY + (-0.5f - viewCameraY) * state.cellSize;
        float bottom = centerY + (state.rows - 0.5f - viewCameraY) * state.cellSize;
        canvas.clipRect(Math.max(0, left), Math.max(0, top),
                Math.min(state.screenW, right), Math.min(state.screenH, bottom));
    }

    private void updateCamera(float t) {
        if (state.isClassicMode()) {
            state.cameraX = state.cols / 2f - 0.5f;
            state.cameraY = state.rows / 2f - 0.5f;
            return;
        }
        boolean spectator = state.currentState == GameState.State.MP_GAME_OVER
                || (state.currentState == GameState.State.MP_PLAYING
                && !state.snakes[state.playerIndex].alive);
        if (spectator || state.cameraMode == GameState.CameraMode.FULL_PLAY_AREA) {
            state.cameraX = state.cols / 2f - 0.5f;
            state.cameraY = state.rows / 2f - 0.5f;
            return;
        }
        GameState.SnakeData sd = state.snakes[state.playerIndex];
        if (sd.body.isEmpty() || sd.prevBody.isEmpty()) return;
        Point prev = sd.prevBody.get(0);
        Point cur = sd.body.get(0);
        float dx = cur.x - prev.x;
        float dy = cur.y - prev.y;
        boolean wrapped = Math.abs(dx) > 1 || Math.abs(dy) > 1;
        if (state.cameraMode == GameState.CameraMode.FIT_VERTICAL) {
            state.cameraX = wrapped ? cur.x : prev.x + dx * t;
            state.cameraY = state.rows / 2f - 0.5f;
            return;
        }
        if (wrapped) { state.cameraX = cur.x; state.cameraY = cur.y; }
        else { state.cameraX = prev.x + dx * t; state.cameraY = prev.y + dy * t; }
    }

    private float wrappedDelta(float delta, int size) {
        while (delta > size / 2f) delta -= size;
        while (delta < -size / 2f) delta += size;
        return delta;
    }

    private boolean isWallPreviewAdjacentToPlayer(int wx, int wy) {
        for (int si = 0; si < 2; si++) {
            if (!state.snakes[si].alive || state.snakes[si].body.isEmpty()) continue;
            Point head = state.snakes[si].body.get(0);
            int dx = Math.abs(wx - head.x);
            int dy = Math.abs(wy - head.y);
            if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) return true;
        }
        return false;
    }

    private void drawFoodArrow(Canvas canvas, float dx, float dy) {
        float length = Math.min(state.screenW, state.screenH) / 2f - state.cellSize;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance == 0) return;
        float dirX = dx / distance;
        float dirY = dy / distance;
        float centerX = state.boardLeft + state.screenW / 2f;
        float centerY = state.boardTop + state.screenH / 2f;
        float tipX = centerX + dirX * length;
        float tipY = centerY + dirY * length;
        float sideX = -dirY * state.cellSize * 0.35f;
        float sideY = dirX * state.cellSize * 0.35f;
        float backX = tipX - dirX * state.cellSize * 0.75f;
        float backY = tipY - dirY * state.cellSize * 0.75f;
        android.graphics.Path arrow = new android.graphics.Path();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(backX + sideX, backY + sideY);
        arrow.lineTo(backX - sideX, backY - sideY);
        arrow.close();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(arrow, paint);
    }

    private void drawDim(Canvas canvas) {
        paint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRect(0, 0, state.screenW, state.screenH, paint);
    }

    private void drawMenu(Canvas canvas) {
        drawTitle(canvas, state.screenH * 0.20f);
        drawButton(canvas, state.playBtn, "PLAY");
        drawButton(canvas, state.speedBtn, "SPEED: " + state.speedLabels[state.speedIndex]);
        drawButton(canvas, state.settingsBtn, "SETTINGS");
        drawButton(canvas, state.leaderboardBtn, "LEADERBOARD");
        drawButton(canvas, state.exitBtn, "EXIT");
        if (state.devMode) {
            drawCenteredText(canvas, "DEV MODE", state.screenW / 2f, state.screenH * 0.17f, 28, Color.RED, true);
            drawButton(canvas, state.devScoreBtn, "START SCORE: " + state.devScoreText);
            String[] bossLabels = {"BOSS: RANDOM", "BOSS: CHASER", "BOSS: WALL"};
            drawButton(canvas, state.devBossBtn, bossLabels[state.devForcedBossType]);
            drawButton(canvas, state.devPathBtn, "PATH: " + (state.showBossPathfinding ? "ON" : "OFF"));
        }
    }

    private void drawPlayMenu(Canvas canvas) {
        drawCenteredText(canvas, "PLAY", state.screenW / 2f, state.screenH * 0.20f, 48, Color.GREEN, true);
        drawButton(canvas, state.singleplayerBtn, "SINGLEPLAYER");
        drawButton(canvas, state.multiplayerBtn, "MULTIPLAYER");
        drawButton(canvas, state.playBackBtn, "BACK");
    }

    private void drawModeSelect(Canvas canvas) {
        drawCenteredText(canvas, "SELECT MODE", state.screenW / 2f, state.screenH * 0.20f, 48, Color.GREEN, true);

        // Mode buttons — highlight the selected one
        int arcadeBg = state.selectedModeIndex == 0 ? Color.GREEN : Color.DKGRAY;
        int classicBg = state.selectedModeIndex == 1 ? Color.GREEN : Color.DKGRAY;
        paint.setColor(arcadeBg);
        if (state.arcadeBtn != null)
            canvas.drawRect(state.arcadeBtn.left, state.arcadeBtn.top,
                    state.arcadeBtn.right - 2, state.arcadeBtn.bottom - 2, paint);
        drawCenteredText(canvas, "ARCADE", state.arcadeBtn.centerX(),
                state.arcadeBtn.centerY(), 36, Color.BLACK, true);
        paint.setColor(classicBg);
        if (state.classicBtn != null)
            canvas.drawRect(state.classicBtn.left, state.classicBtn.top,
                    state.classicBtn.right - 2, state.classicBtn.bottom - 2, paint);
        drawCenteredText(canvas, "CLASSIC", state.classicBtn.centerX(),
                state.classicBtn.centerY(), 36, Color.BLACK, true);

        // Description for the selected mode
        String desc;
        if (state.selectedModeIndex == 0) {
            desc = "A fixed 32x32 grid.\nBosses, progression, and\npure fun guaranteed!";
        } else {
            desc = "The Classic Snake Experience.\nNo bosses, no gimmicks.\nRelive the way Snake was\nmeant to be played.";
        }
        float descY = state.classicBtn.bottom + (state.modePlayBtn.top - state.classicBtn.bottom) * 0.35f;
        drawCenteredText(canvas, desc, state.screenW / 2f, descY, 24, Color.LTGRAY, false);

        drawButton(canvas, state.modePlayBtn, "PLAY");
        drawButton(canvas, state.modeBackBtn, "BACK");
    }

    private void drawMpMenu(Canvas canvas) {
        drawCenteredText(canvas, "LOCAL MULTIPLAYER", state.screenW / 2f, state.screenH * 0.20f, 40, Color.GREEN, true);
        drawButton(canvas, state.hostBtn, "HOST GAME");
        drawButton(canvas, state.joinBtn, "JOIN GAME");
        drawButton(canvas, state.backBtn, "BACK");
    }

    private void drawHostScreen(Canvas canvas) {
        drawCenteredText(canvas, "HOST GAME", state.screenW / 2f, state.screenH * 0.25f, 48, Color.GREEN, true);
        String status = state.mpStatus != null && !state.mpStatus.isEmpty()
                ? state.mpStatus : "Waiting for player to join...";
        drawCenteredText(canvas, status, state.screenW / 2f, state.screenH * 0.45f, 28, Color.WHITE, false);
        drawButton(canvas, state.cancelBtn, "CANCEL");
    }

    private void drawJoinScreen(Canvas canvas) {
        drawCenteredText(canvas, "JOIN GAME", state.screenW / 2f, state.screenH * 0.25f, 48, Color.GREEN, true);
        if (state.opponentConnected) {
            drawCenteredText(canvas, "Connected!", state.screenW / 2f, state.screenH * 0.35f, 28, Color.GREEN, false);
        } else {
            String status = state.mpStatus != null && !state.mpStatus.isEmpty()
                    ? state.mpStatus : "Scanning for hosts...";
            drawCenteredText(canvas, status, state.screenW / 2f, state.screenH * 0.35f, 24, Color.WHITE, false);
        }
        // Draw discovered hosts list
        state.hostItemRects.clear();
        if (!state.discoveredHosts.isEmpty()) {
            float listY = state.screenH * 0.43f;
            float itemH = state.uiCellSize * 1.4f;
            float gap = state.uiCellSize * 0.3f;
            float itemW = Math.min(state.screenW * 0.85f, 400);
            float left = (state.screenW - itemW) / 2f;
            for (int i = 0; i < state.discoveredHosts.size(); i++) {
                GameState.DiscoveredHost dh = state.discoveredHosts.get(i);
                float cy = listY + i * (itemH + gap);
                RectF r = new RectF(left, cy - itemH / 2f, left + itemW, cy + itemH / 2f);
                state.hostItemRects.add(r);
                paint.setColor(dh.resolved ? Color.GREEN : Color.DKGRAY);
                canvas.drawRect(r.left, r.top, r.right - 2, r.bottom - 2, paint);
                drawCenteredText(canvas, dh.name, r.centerX(), r.centerY(), 28,
                        dh.resolved ? Color.BLACK : Color.GRAY, true);
            }
        }
        drawButton(canvas, state.cancelBtn, "CANCEL");
    }

    private void drawLobby(Canvas canvas) {
        drawCenteredText(canvas, "LOBBY", state.screenW / 2f, state.screenH * 0.12f, 48, Color.GREEN, true);

        String myLabel = state.isHost ? "Player 1" : "Player 2";
        String oppLabel = state.isHost ? "Player 2" : "Player 1";
        int myHead = state.headColor;
        int myBody = state.bodyColor;
        int oppHead = state.clientColor;
        int oppBody = state.clientBodyColor;

        drawPlayerRow(canvas, myLabel, myHead, myBody, state.localReady, state.screenH * 0.26f);
        drawPlayerRow(canvas, oppLabel, oppHead, oppBody, state.opponentReady, state.screenH * 0.36f);

        drawButton(canvas, state.readyBtn, state.localReady ? "UN-READY" : "READY");
        if (state.isHost) {
            drawButton(canvas, state.forceStartBtn, "FORCE START");
        }
        drawButton(canvas, state.cancelBtn, "DISCONNECT");
    }

    private void drawPlayerRow(Canvas canvas, String label, int headColor, int bodyColor,
                                boolean ready, float centerY) {
        float previewSize = Math.min(state.uiCellSize * 1.2f, state.screenH * 0.055f);
        float nameX = state.screenW * 0.08f;

        // Player name on the left, vertically centered
        paint.setColor(Color.WHITE);
        paint.setTextSize(32);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = centerY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, nameX, textY, paint);

        // Snake preview to the right of the name
        float previewLeft = state.screenW * 0.42f;
        float previewTop = centerY - previewSize / 2f;
        RectF previewRect = new RectF(previewLeft, previewTop,
                previewLeft + previewSize * 3, previewTop + previewSize);
        drawSnakePreview(canvas, previewRect, headColor, bodyColor);

        // Ready status on the right side
        paint.setColor(ready ? Color.GREEN : Color.RED);
        paint.setTextSize(28);
        paint.setTypeface(Typeface.DEFAULT);
        String status = ready ? "READY" : "NOT READY";
        float statusX = state.screenW * 0.80f;
        canvas.drawText(status, statusX, textY, paint);
    }

    private void drawMpGameOverOverlay(Canvas canvas) {
        drawCenteredText(canvas, "GAME OVER", state.screenW / 2f, state.screenH * 0.30f, 60, Color.RED, true);
        String result;
        if (state.mpWinner == -1) result = "DRAW!";
        else if (state.mpWinner == state.playerIndex) result = "YOU WIN!";
        else result = "YOU LOSE";
        drawCenteredText(canvas, result, state.screenW / 2f, state.screenH * 0.30f + 60, 44, Color.GREEN, true);
        drawCenteredText(canvas, "P1: " + state.mpLastScore0 + "  P2: " + state.mpLastScore1,
                state.screenW / 2f, state.screenH * 0.30f + 110, 36, Color.WHITE, false);
        if (state.isHost) {
            drawButton(canvas, state.mpRestartBtn, "REMATCH");
        }
        drawButton(canvas, state.mpMenuBtn, "MENU");
    }

    private void drawPausedOverlay(Canvas canvas) {
        drawCenteredText(canvas, "PAUSED", state.screenW / 2f, state.screenH * 0.36f, 64, Color.GREEN, true);
        drawButton(canvas, state.resumeBtn, "RESUME");
        drawButton(canvas, state.pauseMenuBtn, "MENU");
    }

    private void drawGameOverOverlay(Canvas canvas) {
        drawCenteredText(canvas, "GAME OVER", state.screenW / 2f, state.screenH * 0.36f, 60, Color.RED, true);
        drawCenteredText(canvas, "SCORE: " + state.lastScore, state.screenW / 2f, state.screenH * 0.36f + 56, 40, Color.WHITE, false);
        drawButton(canvas, state.restartBtn, "RESTART");
        drawButton(canvas, state.overMenuBtn, "MENU");
    }

    private void drawPauseIcon(Canvas canvas) {
        paint.setColor(Color.GREEN);
        float w = state.pauseIcon.width();
        float h = state.pauseIcon.height();
        float barW = w * 0.28f;
        canvas.drawRect(state.pauseIcon.left, state.pauseIcon.top, state.pauseIcon.left + barW, state.pauseIcon.top + h, paint);
        canvas.drawRect(state.pauseIcon.right - barW, state.pauseIcon.top, state.pauseIcon.right, state.pauseIcon.top + h, paint);
    }

    private void drawTitle(Canvas canvas, float y) {
        drawCenteredText(canvas, "SNAKE", state.screenW / 2f, y, 96, Color.GREEN, true);
    }

    private void drawButton(Canvas canvas, RectF r, String label) {
        if (r == null) return;
        paint.setColor(Color.GREEN);
        canvas.drawRect(r.left, r.top, r.right - 2, r.bottom - 2, paint);
        drawCenteredText(canvas, label, r.centerX(), r.centerY(), 36, Color.BLACK, true);
    }

    private void drawCenteredText(Canvas canvas, String text, float cx, float cy, float size, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, cx, textY, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSettings(Canvas canvas) {
        drawCenteredText(canvas, "SETTINGS", state.screenW / 2f, state.screenH * 0.10f, 64, Color.GREEN, true);
        drawButton(canvas, state.snakeColorBtn, "SNAKE COLOR");
        drawSnakePreview(canvas, state.snakePreviewRect, state.headColor, state.bodyColor);
        drawCenteredText(canvas, "CAMERA MODE", state.screenW / 2f, state.cameraModeBtn.top - 10, 26, Color.WHITE, false);
        String camLabel;
        switch (state.cameraMode) {
            case FULL_PLAY_AREA: camLabel = "FULL AREA"; break;
            case FIT_VERTICAL:   camLabel = "FIT VERTICAL"; break;
            default:             camLabel = "CLASSIC ZOOM"; break;
        }
        drawButton(canvas, state.cameraModeBtn, camLabel);
        drawVolumeSlider(canvas, "MUSIC", state.musicSliderTrack, state.musicVolume);
        drawVolumeSlider(canvas, "SFX", state.sfxSliderTrack, state.sfxVolume);
        drawButton(canvas, state.settingsBackBtn, "BACK");
    }

    private void drawSnakePreview(Canvas canvas, RectF rect, int headColor, int bodyColor) {
        if (rect == null) return;
        float h = rect.height();
        float y = rect.centerY();
        float segW = h;
        float segGap = 0;
        float totalW = segW * 3 + segGap * 2;
        float startX = rect.centerX() - totalW / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(headColor);
        canvas.drawRect(startX, y - h / 2f, startX + segW, y + h / 2f, paint);

        paint.setColor(bodyColor);
        canvas.drawRect(startX + segW, y - h / 2f,
                startX + segW * 2, y + h / 2f, paint);
        canvas.drawRect(startX + segW * 2, y - h / 2f,
                startX + segW * 3, y + h / 2f, paint);
    }

    private void drawVolumeSlider(Canvas canvas, String label, RectF track, float volume) {
        if (track == null) return;
        float cy = track.centerY();
        drawCenteredText(canvas, label, state.screenW / 2f, track.top - 6, 20, Color.WHITE, true);
        float barTop = cy - 4;
        float barBot = cy + 4;
        float left = track.left + 8;
        float right = track.right - 8;
        float fillX = left + (right - left) * volume;
        float radius = 14;
        paint.setColor(Color.DKGRAY);
        canvas.drawRect(left, barTop, right, barBot, paint);
        paint.setColor(Color.GREEN);
        canvas.drawRect(left, barTop, fillX, barBot, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(fillX, cy, radius, paint);
    }

    private void drawColorPicker(Canvas canvas) {
        drawCenteredText(canvas, "SNAKE COLOR", state.screenW / 2f, state.screenH * 0.04f,
                48, Color.GREEN, true);

        // Head/Body toggle
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(state.pickerTarget == 0 ? Color.GREEN : Color.DKGRAY);
        canvas.drawRect(state.pickerHeadBtn.left, state.pickerHeadBtn.top,
                state.pickerHeadBtn.right - 2, state.pickerHeadBtn.bottom - 2, paint);
        drawCenteredText(canvas, "HEAD", state.pickerHeadBtn.centerX(),
                state.pickerHeadBtn.centerY(), 28, Color.BLACK, true);

        paint.setColor(state.pickerTarget == 1 ? Color.GREEN : Color.DKGRAY);
        canvas.drawRect(state.pickerBodyBtn.left, state.pickerBodyBtn.top,
                state.pickerBodyBtn.right - 2, state.pickerBodyBtn.bottom - 2, paint);
        drawCenteredText(canvas, "BODY", state.pickerBodyBtn.centerX(),
                state.pickerBodyBtn.centerY(), 28, Color.BLACK, true);

        // Snake preview
        int pH = state.pickerTarget == 0 ? state.pickerColor : state.headColor;
        int pB = state.pickerTarget == 1 ? state.pickerColor : state.bodyColor;
        drawSnakePreview(canvas, state.pickerSnakePreview, pH, pB);

        // Color swatch
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(state.pickerColor);
        canvas.drawRect(state.pickerSwatch.left, state.pickerSwatch.top,
                state.pickerSwatch.right - 2, state.pickerSwatch.bottom - 2, paint);

        // Hex field
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(state.pickerEditingHex ? Color.GREEN : Color.GRAY);
        canvas.drawRect(state.pickerHexField.left, state.pickerHexField.top,
                state.pickerHexField.right - 2, state.pickerHexField.bottom - 2, paint);
        paint.setStyle(Paint.Style.FILL);
        drawCenteredText(canvas, state.pickerHex, state.pickerHexField.centerX(),
                state.pickerHexField.centerY(), 30, Color.WHITE, true);

        // Hue bar
        drawColorSlider(canvas, state.pickerHueBar, getHueColors(),
                state.pickerHue / 360f, Color.WHITE);

        float[] hsv = new float[]{ state.pickerHue, 1f, 1f };
        int fullHue = Color.HSVToColor(hsv);

        // Sat bar
        int gray = Color.rgb(128, 128, 128);
        drawColorSlider(canvas, state.pickerSatBar, new int[]{ gray, fullHue },
                state.pickerSat, Color.WHITE);

        // Val bar
        int black = Color.rgb(0, 0, 0);
        hsv[1] = state.pickerSat;
        hsv[2] = 1f;
        int fullColor = Color.HSVToColor(hsv);
        drawColorSlider(canvas, state.pickerValBar, new int[]{ black, fullColor },
                state.pickerVal, Color.WHITE);

        // Apply / Cancel
        drawButton(canvas, state.pickerApplyBtn, "APPLY");
        drawButton(canvas, state.pickerCancelBtn, "CANCEL");
    }

    private int[] getHueColors() {
        return new int[]{ Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, Color.MAGENTA, Color.RED };
    }

    private void drawColorSlider(Canvas canvas, RectF rect, int[] colors,
                                  float fraction, int thumbColor) {
        if (rect == null) return;
        LinearGradient lg = new LinearGradient(
                rect.left, rect.top, rect.right, rect.top,
                colors, null, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(rect.left, rect.top, rect.right - 2, rect.bottom - 2, paint);
        paint.setShader(null);

        float thumbX = rect.left + (rect.right - rect.left) * fraction;
        float thumbY = rect.centerY();
        paint.setColor(thumbColor);
        canvas.drawCircle(thumbX, thumbY, 12, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(thumbX, thumbY, 12, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawLeaderboard(Canvas canvas) {
        drawCenteredText(canvas, "LEADERBOARD", state.screenW / 2f, state.screenH * 0.10f, 60, Color.GREEN, true);
        // Mode tabs
        int arcadeBg = state.leaderboardMode == 0 ? Color.GREEN : Color.DKGRAY;
        int classicBg = state.leaderboardMode == 1 ? Color.GREEN : Color.DKGRAY;
        paint.setColor(arcadeBg);
        if (state.lbArcadeBtn != null)
            canvas.drawRect(state.lbArcadeBtn.left, state.lbArcadeBtn.top,
                    state.lbArcadeBtn.right - 2, state.lbArcadeBtn.bottom - 2, paint);
        drawCenteredText(canvas, "ARCADE", state.lbArcadeBtn.centerX(),
                state.lbArcadeBtn.centerY(), 26, Color.BLACK, true);
        paint.setColor(classicBg);
        if (state.lbClassicBtn != null)
            canvas.drawRect(state.lbClassicBtn.left, state.lbClassicBtn.top,
                    state.lbClassicBtn.right - 2, state.lbClassicBtn.bottom - 2, paint);
        drawCenteredText(canvas, "CLASSIC", state.lbClassicBtn.centerX(),
                state.lbClassicBtn.centerY(), 26, Color.BLACK, true);
        drawButton(canvas, state.lbSortBtn, "SORT: " + (state.sortMode == GameState.SortMode.HIGH_SCORE ? "HIGH SCORE" : "RECENT"));
        ArrayList<GameState.ScoreEntry> list = persistence.loadScores(state.leaderboardMode);
        Collections.sort(list, (a, b) -> {
            if (state.sortMode == GameState.SortMode.HIGH_SCORE) {
                return Integer.compare(b.score, a.score);
            } else {
                return Long.compare(b.timestamp, a.timestamp);
            }
        });
        if (list.isEmpty()) {
            drawCenteredText(canvas, "No scores yet!", state.screenW / 2f, state.screenH * 0.5f, 40, Color.WHITE, false);
        } else {
            float startY = state.screenH * 0.38f;
            float rowH = state.cellSize * 1.2f;
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            int maxShow = Math.min(list.size(), 8);
            for (int i = 0; i < maxShow; i++) {
                GameState.ScoreEntry entry = list.get(i);
                String dateStr = sdf.format(new Date(entry.timestamp));
                String text = (i + 1) + ".  Score: " + entry.score + "   "
                        + entry.difficulty + "   " + dateStr;
                drawCenteredText(canvas, text, state.screenW / 2f, startY + i * rowH, 32, Color.WHITE, false);
            }
        }
        drawButton(canvas, state.lbBackBtn, "BACK");
    }
}
