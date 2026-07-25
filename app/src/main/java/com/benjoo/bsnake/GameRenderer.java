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

// All Canvas drawing — game field, UI screens, overlays, and text helpers.
class GameRenderer {

    private final GameState state;
    private final PersistenceManager persistence;
    private final Paint paint = new Paint();

    GameRenderer(GameState state, PersistenceManager persistence) {
        this.state = state;
        this.persistence = persistence;
        paint.setAntiAlias(true);
    }

    // Dispatch to the appropriate screen renderer based on the current state.
    // PAUSED and GAME_OVER draw the frozen game field underneath a dim overlay.
    void draw(Canvas canvas, float t) {
        if (canvas == null) return;
        canvas.drawColor(Color.BLACK);

        switch (state.currentState) {
            case MENU:
                drawMenu(canvas);
                break;
            case LEADERBOARD:
                drawLeaderboard(canvas);
                break;
            case SETTINGS:
                drawSettings(canvas);
                break;
            case PLAYING:
                drawGameField(canvas, t);
                drawPauseIcon(canvas);
                break;
            case PAUSED:
                drawGameField(canvas, 1f);
                drawDim(canvas);
                drawPausedOverlay(canvas);
                break;
            case GAME_OVER:
                drawGameField(canvas, 1f);
                drawDim(canvas);
                drawGameOverOverlay(canvas);
                break;
        }
    }

    // The main playing-field renderer.
    // Updates the camera to the tweened head position, draws the grid, then
    // draws each snake segment (interpolated between prevSnake and snake using
    // fraction t, with wrap detection to avoid tweening across the map), red
    // food circles (or off-screen arrows), and the score label at top-left.
    private void drawGameField(Canvas canvas, float t) {
        updateCamera(t);
        drawBoard(canvas);
        float viewCameraX = state.snake.isEmpty() ? state.cols / 2f : state.cameraX;
        float viewCameraY = state.snake.isEmpty() ? state.rows / 2f : state.cameraY;
        canvas.save();
        clipToWorld(canvas, viewCameraX, viewCameraY);
        int n = Math.min(state.snake.size(), state.prevSnake.size());
        for (int i = 0; i < n; i++) {
            paint.setColor(i == 0 ? state.headColor : state.bodyColor);
            Point cur = state.snake.get(i);
            Point prev = state.prevSnake.get(i);

            float dx = cur.x - prev.x;
            float dy = cur.y - prev.y;
            boolean wrapped = Math.abs(dx) > 1 || Math.abs(dy) > 1;

            float worldX, worldY;
            if (wrapped) {
                worldX = cur.x;
                worldY = cur.y;
            } else {
                worldX = prev.x + dx * t;
                worldY = prev.y + dy * t;
            }
            float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f
                    + wrappedDelta(worldX - viewCameraX, state.cols));
            float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f
                    + wrappedDelta(worldY - viewCameraY, state.rows));
            canvas.drawRect(px, py, px + state.cellSize - 1, py + state.cellSize - 1, paint);
        }
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
            float r = state.cellSize / 2f - 4;
            canvas.drawCircle(cx, cy, Math.max(4, r), paint);
        }

        // ----- boss fruit (2x2 purple block with HP indicator) -----
        if (state.boss.alive) {
            for (Point tile : state.boss.getTiles()) {
                float bDx = tile.x - viewCameraX;
                float bDy = tile.y - viewCameraY;
                if (Math.abs(bDx) >= state.viewportWidthCells / 2f
                        || Math.abs(bDy) >= state.viewportHeightCells / 2f) continue;
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f + bDy);
                paint.setColor(Color.rgb(180, 50, 200));
                canvas.drawRect(bx, by, bx + state.cellSize - 1, by + state.cellSize - 1, paint);
            }
            // Draw HP and boss icon above the top-left tile
            float bDx = state.boss.x - viewCameraX;
            float bDy = state.boss.y - viewCameraY;
            if (Math.abs(bDx) < state.viewportWidthCells / 2f
                    && Math.abs(bDy) < state.viewportHeightCells / 2f) {
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f + bDy);
                paint.setColor(Color.WHITE);
                paint.setTextSize(state.cellSize * 0.5f);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText("B" + state.boss.hp, bx, by - 4, paint);
            }
        }

        // ----- boss trail cells (golden, eatable) -----
        for (GameState.BossTrailCell tc : state.bossTrail) {
            float tDx = tc.x - viewCameraX;
            float tDy = tc.y - viewCameraY;
            if (Math.abs(tDx) >= state.viewportWidthCells / 2f
                    || Math.abs(tDy) >= state.viewportHeightCells / 2f) continue;
            float tx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f + tDx);
            float ty = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f + tDy);
            float age = (state.tickCount - tc.createdAtTick) / (float) 40;
            int alpha = (int) (255 * (1f - age * 0.5f));
            alpha = Math.max(120, Math.min(255, alpha));
            paint.setColor(Color.argb(alpha, 255, 200, 50));
            float inset = state.cellSize * 0.15f;
            canvas.drawRect(tx + inset, ty + inset, tx + state.cellSize - 1 - inset,
                    ty + state.cellSize - 1 - inset, paint);
        }

        canvas.restore();

        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        paint.setTypeface(Typeface.DEFAULT);
        String scoreLabel = "Score: " + (state.snake.size() - 3);
        if (state.devMode) scoreLabel += " [DEV]";
        canvas.drawText(scoreLabel, 10, 40, paint);
    }

    // Draw the grey grid lines (clipped to the visible world area) and the
    // red toroidal boundary border.
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

    // Clamp drawing to the visible world rectangle so grid cells outside the
    // viewport are not rendered.
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

    // Set the camera position to the tweened head position each frame so the
    // viewport follows exactly what is drawn. On wrap/teleport the camera snaps
    // directly to cur to avoid interpolating across the whole map.
    private void updateCamera(float t) {
        if (state.snake.isEmpty() || state.prevSnake.isEmpty()) return;
        Point prev = state.prevSnake.get(0);
        Point cur = state.snake.get(0);
        float dx = cur.x - prev.x;
        float dy = cur.y - prev.y;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
            state.cameraX = cur.x;
            state.cameraY = cur.y;
        } else {
            state.cameraX = prev.x + dx * t;
            state.cameraY = prev.y + dy * t;
        }
    }

    // Return the shortest signed distance between delta and the nearest
    // equivalent position on a torus of the given size.
    private float wrappedDelta(float delta, int size) {
        while (delta > size / 2f) delta -= size;
        while (delta < -size / 2f) delta += size;
        return delta;
    }

    // Draw a red directional arrow pointing toward an off-screen food item,
    // positioned at the edge of the viewport in the food's direction.
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

    // Semi-transparent black overlay used for pause and game-over screens.
    private void drawDim(Canvas canvas) {
        paint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRect(0, 0, state.screenW, state.screenH, paint);
    }

    // Main menu: title + START / SPEED / SETTINGS / LEADERBOARD / EXIT buttons.
    // When dev mode is active, also shows a DEV MODE badge and a tappable start-score field.
    private void drawMenu(Canvas canvas) {
        drawTitle(canvas, state.screenH * 0.24f);
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

    // Settings screen: color swatches for head and body, current hex values,
    // APPLY (validates and persists) and BACK buttons.
    private void drawSettings(Canvas canvas) {
        drawCenteredText(canvas, "SETTINGS", state.screenW / 2f, state.screenH * 0.14f, 64, Color.GREEN, true);
        drawCenteredText(canvas, "CUSTOMIZE COLORS", state.screenW / 2f, state.screenH * 0.23f, 30, Color.WHITE, false);
        drawColorField(canvas, state.headInputBtn, "HEAD:  " + state.headHex, state.headColor);
        drawColorField(canvas, state.bodyInputBtn, "BODY:  " + state.bodyHex, state.bodyColor);
        drawButton(canvas, state.settingsApplyBtn, "APPLY");
        drawButton(canvas, state.settingsBackBtn, "BACK");
    }

    // A labeled color swatch: a green-outlined rect with the selected color
    // filled on the left and the hex label text beside it.
    private void drawColorField(Canvas canvas, RectF rect, String label, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.GREEN);
        canvas.drawRect(rect.left, rect.top, rect.right - 2, rect.bottom - 2, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(rect.left + 12, rect.top + 12, rect.left + state.cellSize, rect.bottom - 14, paint);
        drawCenteredText(canvas, label, rect.centerX() + state.cellSize * 0.25f, rect.centerY(), 30, Color.WHITE, true);
    }

    // Leaderboard screen: sort-toggle button, up to 8 ranked entries with
    // score, difficulty, and date, plus a BACK button.
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

    // Pause overlay with RESUME and MENU buttons.
    private void drawPausedOverlay(Canvas canvas) {
        drawCenteredText(canvas, "PAUSED", state.screenW / 2f, state.screenH * 0.36f, 64, Color.GREEN, true);
        drawButton(canvas, state.resumeBtn, "RESUME");
        drawButton(canvas, state.pauseMenuBtn, "MENU");
    }

    // Game-over overlay showing the final score with RESTART and MENU buttons.
    private void drawGameOverOverlay(Canvas canvas) {
        drawCenteredText(canvas, "GAME OVER", state.screenW / 2f, state.screenH * 0.36f, 60, Color.RED, true);
        drawCenteredText(canvas, "SCORE: " + state.lastScore, state.screenW / 2f, state.screenH * 0.36f + 56, 40, Color.WHITE, false);
        drawButton(canvas, state.restartBtn, "RESTART");
        drawButton(canvas, state.overMenuBtn, "MENU");
    }

    // Two vertical green bars (like the snake body) in the top-right corner.
    private void drawPauseIcon(Canvas canvas) {
        paint.setColor(Color.GREEN);
        float w = state.pauseIcon.width();
        float h = state.pauseIcon.height();
        float barW = w * 0.28f;
        canvas.drawRect(state.pauseIcon.left, state.pauseIcon.top, state.pauseIcon.left + barW, state.pauseIcon.top + h, paint);
        canvas.drawRect(state.pauseIcon.right - barW, state.pauseIcon.top, state.pauseIcon.right, state.pauseIcon.top + h, paint);
    }

    // "SNAKE" title drawn at the given Y position.
    private void drawTitle(Canvas canvas, float y) {
        drawCenteredText(canvas, "SNAKE", state.screenW / 2f, y, 96, Color.GREEN, true);
    }

    // Blocky green button with a black label centered inside.
    private void drawButton(Canvas canvas, RectF r, String label) {
        paint.setColor(Color.GREEN);
        canvas.drawRect(r.left, r.top, r.right - 2, r.bottom - 2, paint);
        drawCenteredText(canvas, label, r.centerX(), r.centerY(), 36, Color.BLACK, true);
    }

    // Generic helper: draw a single line of text centered at (cx, cy) with
    // the given size, color, and boldness.
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
}
