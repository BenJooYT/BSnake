package com.benjoo.bsnake;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
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
        }
    }

    private void drawGameField(Canvas canvas, float t, boolean mpGameOver) {
        int savedCellSize = state.cellSize;
        float savedViewportW = state.viewportWidthCells;
        float savedViewportH = state.viewportHeightCells;
        if (state.cameraMode != GameState.CameraMode.CLASSIC_ZOOM) {
            if (state.cameraMode == GameState.CameraMode.FIT_VERTICAL) {
                state.cellSize = state.fitVerticalCellSize;
            } else {
                state.cellSize = state.fullAreaCellSize;
            }
            state.viewportWidthCells = state.screenW / (float) state.cellSize;
            state.viewportHeightCells = state.screenH / (float) state.cellSize;
        }
        updateCamera(t);
        drawBoard(canvas);
        float viewCameraX = state.snakes[state.playerIndex].body.isEmpty() ? state.cols / 2f : state.cameraX;
        float viewCameraY = state.snakes[state.playerIndex].body.isEmpty() ? state.rows / 2f : state.cameraY;
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

        // Boss
        if (state.boss.alive) {
            for (Point tile : state.boss.getTiles()) {
                float bDx = tile.x - viewCameraX;
                float bDy = tile.y - viewCameraY;
                if (Math.abs(bDx) >= state.viewportWidthCells / 2f
                        || Math.abs(bDy) >= state.viewportHeightCells / 2f) continue;
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + bDy);
                paint.setColor(Color.rgb(180, 50, 200));
                canvas.drawRect(bx, by, bx + state.cellSize - 1, by + state.cellSize - 1, paint);
            }
            float bDx = state.boss.x - viewCameraX;
            float bDy = state.boss.y - viewCameraY;
            if (Math.abs(bDx) < state.viewportWidthCells / 2f
                    && Math.abs(bDy) < state.viewportHeightCells / 2f) {
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + bDy);
                paint.setColor(Color.WHITE);
                paint.setTextSize(state.cellSize * 0.5f);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText("B" + state.boss.hp, bx, by - 4, paint);
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

        canvas.restore();

        // Score label
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        paint.setTypeface(Typeface.DEFAULT);
        String scoreLabel;
        if (state.currentState == GameState.State.MP_PLAYING || state.currentState == GameState.State.MP_GAME_OVER) {
            scoreLabel = "P1: " + state.snakes[0].score + "  P2: " + state.snakes[1].score;
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
        if (state.cameraMode == GameState.CameraMode.FULL_PLAY_AREA) {
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
        drawButton(canvas, state.mpBtn, "LOCAL MULTIPLAYER");
        drawButton(canvas, state.startBtn, "START");
        drawButton(canvas, state.speedBtn, "SPEED: " + state.speedLabels[state.speedIndex]);
        drawButton(canvas, state.settingsBtn, "SETTINGS");
        drawButton(canvas, state.leaderboardBtn, "LEADERBOARD");
        drawButton(canvas, state.exitBtn, "EXIT");
        if (state.devMode) {
            drawCenteredText(canvas, "DEV MODE", state.screenW / 2f, state.screenH * 0.17f, 28, Color.RED, true);
            drawButton(canvas, state.devScoreBtn, "START SCORE: " + state.devScoreText);
        }
    }

    private void drawMpMenu(Canvas canvas) {
        drawCenteredText(canvas, "LOCAL MULTIPLAYER", state.screenW / 2f, state.screenH * 0.20f, 40, Color.GREEN, true);
        drawButton(canvas, state.hostBtn, "HOST GAME");
        drawButton(canvas, state.joinBtn, "JOIN GAME");
        drawButton(canvas, state.backBtn, "BACK");
    }

    private void drawHostScreen(Canvas canvas) {
        drawCenteredText(canvas, "HOST GAME", state.screenW / 2f, state.screenH * 0.25f, 48, Color.GREEN, true);
        drawCenteredText(canvas, "Waiting for player to join...", state.screenW / 2f, state.screenH * 0.45f, 28, Color.WHITE, false);
        drawButton(canvas, state.cancelBtn, "CANCEL");
    }

    private void drawJoinScreen(Canvas canvas) {
        drawCenteredText(canvas, "JOIN GAME", state.screenW / 2f, state.screenH * 0.25f, 48, Color.GREEN, true);
        if (state.opponentConnected) {
            drawCenteredText(canvas, "Connecting...", state.screenW / 2f, state.screenH * 0.45f, 28, Color.WHITE, false);
        } else {
            drawCenteredText(canvas, "Searching for hosts...", state.screenW / 2f, state.screenH * 0.45f, 28, Color.WHITE, false);
        }
        drawButton(canvas, state.cancelBtn, "CANCEL");
    }

    private void drawLobby(Canvas canvas) {
        drawCenteredText(canvas, "LOBBY", state.screenW / 2f, state.screenH * 0.20f, 48, Color.GREEN, true);
        String status = "Player 1 " + (state.localReady ? "READY" : "NOT READY");
        drawCenteredText(canvas, status, state.screenW / 2f, state.screenH * 0.35f, 28, Color.WHITE, false);
        String oppStatus = "Player 2 " + (state.opponentReady ? "READY" : "NOT READY");
        drawCenteredText(canvas, oppStatus, state.screenW / 2f, state.screenH * 0.42f, 28, Color.WHITE, false);
        drawButton(canvas, state.readyBtn, state.localReady ? "UN-READY" : "READY");
        if (state.isHost) {
            drawButton(canvas, state.forceStartBtn, "FORCE START");
        }
        drawButton(canvas, state.cancelBtn, "DISCONNECT");
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
        drawCenteredText(canvas, "CUSTOMIZE COLORS", state.screenW / 2f, state.screenH * 0.19f, 26, Color.WHITE, false);
        drawColorField(canvas, state.headInputBtn, "HEAD:  " + state.headHex, state.headColor);
        drawColorField(canvas, state.bodyInputBtn, "BODY:  " + state.bodyHex, state.bodyColor);
        drawCenteredText(canvas, "CAMERA MODE", state.screenW / 2f, state.screenH * 0.51f, 26, Color.WHITE, false);
        String camLabel;
        switch (state.cameraMode) {
            case FULL_PLAY_AREA: camLabel = "FULL AREA"; break;
            case FIT_VERTICAL:   camLabel = "FIT VERTICAL"; break;
            default:             camLabel = "CLASSIC ZOOM"; break;
        }
        drawButton(canvas, state.cameraModeBtn, camLabel);
        drawVolumeSlider(canvas, "MUSIC", state.musicSliderTrack, state.musicVolume);
        drawVolumeSlider(canvas, "SFX", state.sfxSliderTrack, state.sfxVolume);
        drawButton(canvas, state.settingsApplyBtn, "APPLY");
        drawButton(canvas, state.settingsBackBtn, "BACK");
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

    private void drawColorField(Canvas canvas, RectF rect, String label, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.GREEN);
        canvas.drawRect(rect.left, rect.top, rect.right - 2, rect.bottom - 2, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(rect.left + 12, rect.top + 12, rect.left + state.uiCellSize, rect.bottom - 14, paint);
        drawCenteredText(canvas, label, rect.centerX() + state.uiCellSize * 0.25f, rect.centerY(), 30, Color.WHITE, true);
    }

    private void drawLeaderboard(Canvas canvas) {
        drawCenteredText(canvas, "LEADERBOARD", state.screenW / 2f, state.screenH * 0.10f, 60, Color.GREEN, true);
        drawButton(canvas, state.lbSortBtn, "SORT: " + (state.sortMode == GameState.SortMode.HIGH_SCORE ? "HIGH SCORE" : "RECENT"));
        ArrayList<GameState.ScoreEntry> list = persistence.loadScores();
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
            float startY = state.screenH * 0.30f;
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
