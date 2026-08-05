package com.benjoo.bsnake;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
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
            case BOSS_DEATH_CINEMATIC:
                drawGameField(canvas, 1f, false);
                drawBossDeathCinematic(canvas);
                break;
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
            case BOSS_UPGRADE:
                drawGameField(canvas, 1f, false);
                drawUpgradeScreen(canvas);
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

        // Full-screen fade-in/out when switching screens.
        if (state.transitionFade > 0) {
            canvas.drawColor(Color.argb((int) (255 * state.transitionFade), 0, 0, 0));
        }
    }

    private void drawGameField(Canvas canvas, float t, boolean mpGameOver) {
        int savedCellSize = state.cellSize;
        float savedViewportW = state.viewportWidthCells;
        float savedViewportH = state.viewportHeightCells;
        // Screen shake (boss spawn / defeat) offsets the whole field.
        canvas.save();
        long nowMs = System.currentTimeMillis();
        if (state.shakeUntilMs > nowMs) {
            float fade = (state.shakeUntilMs - nowMs) / 280f;
            float mag = state.shakeMagnitude * Math.min(1f, fade);
            canvas.translate((float) ((Math.random() * 2 - 1) * mag),
                             (float) ((Math.random() * 2 - 1) * mag));
        }
        boolean isCinematic = state.currentState == GameState.State.BOSS_DEATH_CINEMATIC;
        boolean spectator = state.currentState == GameState.State.MP_GAME_OVER
                || (state.currentState == GameState.State.MP_PLAYING
                && !state.snakes[state.playerIndex].alive);
        boolean fitVertical = !state.isClassicMode() && !isCinematic
                && state.cameraMode == GameState.CameraMode.FIT_VERTICAL
                && !spectator;
        if (!state.isClassicMode() && !isCinematic && (spectator || state.cameraMode != GameState.CameraMode.CLASSIC_ZOOM)) {
            // FIT_VERTICAL fills the screen height exactly and scrolls
            // horizontally; everything else shows the whole play area.
            state.cellSize = fitVertical ? state.fitVerticalCellSize : state.fullAreaCellSize;
            state.viewportWidthCells = state.screenW / (float) state.cellSize;
            state.viewportHeightCells = state.screenH / (float) state.cellSize;
        }
        // Apply cinematic camera zoom for boss death sequence
        if (state.currentState == GameState.State.BOSS_DEATH_CINEMATIC && state.cinematicCameraZoom > 1f) {
            state.cellSize = (int) (state.cellSize * state.cinematicCameraZoom);
            state.viewportWidthCells = state.screenW / (float) state.cellSize;
            state.viewportHeightCells = state.screenH / (float) state.cellSize;
        }
        updateCamera(t);
        drawBoard(canvas);
        // During the death dissolve the live body is cleared, but we keep the
        // frozen camera where the snake died rather than snapping to center.
        boolean deathView = state.deathPending && !state.death.body.isEmpty();
        float viewCameraX = (state.snakes[state.playerIndex].body.isEmpty() && !deathView)
                ? state.cols / 2f - 0.5f : state.cameraX;
        float viewCameraY = (state.snakes[state.playerIndex].body.isEmpty() && !deathView)
                ? state.rows / 2f - 0.5f : state.cameraY;
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

        // Dissolving body of a snake that just died
        drawDeath(canvas, viewCameraX, viewCameraY);

        // Food — pulsing with a soft glow and a scale-in on spawn
        for (GameState.Fruit f : state.foods) {            float foodDx = f.x - viewCameraX;
            float foodDy = f.y - viewCameraY;
            if (Math.abs(foodDx) >= state.viewportWidthCells / 2f
                    || Math.abs(foodDy) >= state.viewportHeightCells / 2f) {
                drawFoodArrow(canvas, foodDx, foodDy);
                continue;
            }
            float cx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f + foodDx);
            float cy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f + foodDy);
            long bornAge = System.currentTimeMillis() - f.bornMs;
            float scaleIn = Math.min(1f, bornAge / 250f);
            float pulse = 1f + 0.1f * (float) Math.sin(bornAge * 0.008);
            if (f.type == GameState.FruitType.HEAL) {
                paint.setColor(Color.rgb(0, 220, 90));
                float r = Math.max(5, state.cellSize / 2f - 3) * scaleIn * pulse;
                canvas.drawCircle(cx, cy, r, paint);
                float glowR = Math.max(1f, Math.max(8, state.cellSize / 2f + 4) * scaleIn * pulse);
                paint.setColor(Color.WHITE);
                paint.setShader(new RadialGradient(cx, cy, glowR,
                        Color.argb(150, 0, 255, 120), Color.argb(0, 0, 255, 120),
                        Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, glowR, paint);
                paint.setShader(null);
            } else {
                paint.setColor(Color.RED);
                float r = Math.max(4, state.cellSize / 2f - 4) * scaleIn * pulse;
                canvas.drawCircle(cx, cy, r, paint);
                float glowR = Math.max(1f, Math.max(7, state.cellSize / 2f) * scaleIn * pulse);
                paint.setColor(Color.WHITE);
                paint.setShader(new RadialGradient(cx, cy, glowR,
                        Color.argb(120, 255, 60, 40), Color.argb(0, 255, 60, 40),
                        Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, glowR, paint);
                paint.setShader(null);
            }
        }

        drawParticles(canvas, viewCameraX, viewCameraY);

        // Boss — drawn as a snake with type-specific colors
        if (state.boss.alive && !state.boss.body.isEmpty()) {
            boolean isWallBuilder = state.boss.type == GameState.BossType.WALL_BUILDER;
            boolean isHealer = state.boss.type == GameState.BossType.HEALER;
            boolean flashed = state.bossFlashTicks > 0;
            long now = System.currentTimeMillis();
            float auraPulse = 0.5f + 0.5f * (float) Math.sin(now * 0.006);
            int auraColor = isWallBuilder ? Color.rgb(0, 140, 255)
                    : isHealer ? Color.rgb(0, 200, 90) : Color.rgb(200, 60, 220);

            // Pulsing aura glow behind the body
            for (int i = 0; i < state.boss.body.size(); i++) {
                Point seg = state.boss.body.get(i);
                float aDx = seg.x - viewCameraX;
                float aDy = seg.y - viewCameraY;
                if (Math.abs(aDx) >= state.viewportWidthCells / 2f
                        || Math.abs(aDy) >= state.viewportHeightCells / 2f) continue;
                float ax = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + aDx);
                float ay = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + aDy);
                int aAlpha = (i == 0 ? 90 : 35) + (int) (30 * auraPulse);
                paint.setColor(Color.argb(aAlpha, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor)));
                float inset = -state.cellSize * 0.18f;
                canvas.drawRect(ax + inset, ay + inset,
                        ax + state.cellSize - 1 - inset, ay + state.cellSize - 1 - inset, paint);
            }

            for (int i = 0; i < state.boss.body.size(); i++) {
                Point seg = state.boss.body.get(i);
                float bDx = seg.x - viewCameraX;
                float bDy = seg.y - viewCameraY;
                if (Math.abs(bDx) >= state.viewportWidthCells / 2f
                        || Math.abs(bDy) >= state.viewportHeightCells / 2f) continue;
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + bDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + bDy);
                if (flashed) {
                    paint.setColor(Color.argb(235, 255, 255, 255));
                } else if (i == 0) {
                    paint.setColor(isWallBuilder ? Color.rgb(0, 140, 255)
                            : isHealer ? Color.rgb(0, 200, 90) : Color.rgb(200, 60, 220));
                } else {
                    if (isWallBuilder) {
                        int dim = Math.max(120, 255 - i * 20);
                        paint.setColor(Color.rgb(255, dim / 2, 0));
                    } else if (isHealer) {
                        int dim = Math.max(70, 190 - i * 15);
                        paint.setColor(Color.rgb(dim / 2, dim, dim / 2));
                    } else {
                        int dim = Math.max(80, 180 - i * 15);
                        paint.setColor(Color.rgb(dim, dim / 3, dim));
                    }
                }
                canvas.drawRect(bx, by, bx + state.cellSize - 1, by + state.cellSize - 1, paint);
            }
            // Head glow
            Point head = state.boss.body.get(0);
            float hDx = head.x - viewCameraX;
            float hDy = head.y - viewCameraY;
            if (Math.abs(hDx) < state.viewportWidthCells / 2f
                    && Math.abs(hDy) < state.viewportHeightCells / 2f) {
                float hx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + hDx);
                float hy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + hDy);
                float glowR = state.cellSize * (1.2f + 0.3f * auraPulse);
                paint.setColor(Color.WHITE);
                paint.setShader(new RadialGradient(hx + state.cellSize / 2f, hy + state.cellSize / 2f, glowR,
                        Color.argb(120, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor)),
                        Color.argb(0, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor)),
                        Shader.TileMode.CLAMP));
                canvas.drawCircle(hx + state.cellSize / 2f, hy + state.cellSize / 2f, glowR, paint);
                paint.setShader(null);
            }
            // Boss segment count label above head
            if (Math.abs(hDx) < state.viewportWidthCells / 2f
                    && Math.abs(hDy) < state.viewportHeightCells / 2f) {
                float bx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + hDx);
                float by = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + hDy);
                paint.setColor(Color.WHITE);
                paint.setTextSize(state.cellSize * 0.5f);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText("B" + state.boss.body.size(), bx, by - 4, paint);
            }
            // Expanding shockwave ring right after spawn
            if (state.bossSpawnRingStartMs > 0) {
                long ringAge = now - state.bossSpawnRingStartMs;
                if (ringAge >= 0 && ringAge < 600) {
                    float p = ringAge / 600f;
                    float radius = state.cellSize * (0.5f + p * 4.5f);
                    int alpha = (int) (200 * (1 - p));
                    float hx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + hDx);
                    float hy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + hDy);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2, state.cellSize * 0.15f));
                    paint.setColor(Color.argb(alpha, Color.red(auraColor), Color.green(auraColor), Color.blue(auraColor)));
                    canvas.drawCircle(hx + state.cellSize / 2f, hy + state.cellSize / 2f, radius, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setStrokeWidth(0);
                }
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

        // Coin meter score badge
        drawScoreMeter(canvas);

        drawChallenges(canvas);
        drawChallengePopups(canvas);
        drawBossHealthBar(canvas);
        if (state.flashAlpha > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(
                    (int) (255 * state.flashAlpha),
                    Color.red(state.flashColor),
                    Color.green(state.flashColor),
                    Color.blue(state.flashColor)));
            canvas.drawRect(0, 0, state.screenW, state.screenH, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        drawBossWarning(canvas);
        drawDirectionPad(canvas);

        canvas.restore();
        state.cellSize = savedCellSize;
        state.viewportWidthCells = savedViewportW;
        state.viewportHeightCells = savedViewportH;
    }

    // Coin meter: a coin badge in the owning snake's head colour showing the
    // score, mid-left of the screen. The badge scales up with a quick "pop" on
    // every point earned, and a "+1" floats up off the badge whenever food is
    // eaten. In multiplayer each player gets their own coin plus a running SUM.
    private void drawScoreMeter(Canvas canvas) {
        long now = System.currentTimeMillis();
        float s = 1.5f;
        float coinR = 15 * s;
        float textSize = 40 * s;
        float x = 10;
        float centerY = state.screenH / 2f;
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.DEFAULT);

        float pulse = 1f;
        if (state.scorePulseMs > 0) {
            float p = (now - state.scorePulseMs) / 220f;
            if (p >= 1f) {
                state.scorePulseMs = 0;
            } else {
                float t = 1f - p;
                pulse = 1f + 0.35f * t * t;
            }
        }

        if (state.scorePopMs > 0) {
            float p = (now - state.scorePopMs) / 500f;
            if (p >= 1f) {
                state.scorePopMs = 0;
                state.scorePopAmount = 1;
            } else {
                int alpha = (int) (255 * (1 - p));
                paint.setColor(Color.argb(alpha, 255, 210, 90));
                paint.setTextSize(28 * s);
                canvas.drawText("+" + state.scorePopAmount, x + coinR * 2 + 6, centerY - 24 * s - 30 * s * p, paint);
                paint.setTextSize(textSize);
            }
        }

        boolean mp = state.currentState == GameState.State.MP_PLAYING
                || state.currentState == GameState.State.MP_GAME_OVER;

        canvas.save();
        canvas.scale(pulse, pulse, x + coinR, centerY);

        if (!mp) {
            drawCoin(canvas, x + coinR, centerY, coinR, state.snakes[0].headColor);
            paint.setColor(Color.WHITE);
            String score = String.valueOf(state.snakes[0].score);
            float labelX = x + coinR * 2 + 10;
            canvas.drawText(score, labelX, centerY + textSize * 0.35f, paint);
            if (state.devMode) {
                paint.setColor(Color.rgb(255, 120, 120));
                paint.setTextSize(20 * s);
                canvas.drawText("DEV", labelX + paint.measureText(score) + 12, centerY + 10, paint);
                paint.setTextSize(textSize);
            }
        } else {
            int youIdx = state.playerIndex;
            int partnerIdx = 1 - youIdx;
            drawCoin(canvas, x + coinR, centerY, coinR, state.snakes[youIdx].headColor);
            paint.setColor(Color.WHITE);
            float labelX = x + coinR * 2 + 8;
            canvas.drawText(String.valueOf(state.snakes[youIdx].score), labelX, centerY + textSize * 0.35f, paint);
            labelX += paint.measureText(String.valueOf(state.snakes[youIdx].score)) + 34 * s;
            drawCoin(canvas, labelX + coinR, centerY, coinR, state.snakes[partnerIdx].headColor);
            labelX += coinR * 2 + 8;
            canvas.drawText(String.valueOf(state.snakes[partnerIdx].score), labelX, centerY + textSize * 0.35f, paint);
            labelX += paint.measureText(String.valueOf(state.snakes[partnerIdx].score)) + 34 * s;
            paint.setColor(Color.rgb(255, 215, 90));
            canvas.drawText("SUM " + (state.snakes[0].score + state.snakes[1].score),
                    labelX, centerY + textSize * 0.35f, paint);
        }

        canvas.restore();
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT);
    }

    // A simple coin: base disc, darker rim, lighter inner disc, and a highlight.
    private void drawCoin(Canvas canvas, float cx, float cy, float r, int base) {
        paint.setColor(base);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, r * 0.18f));
        paint.setColor(Color.argb(255,
                Math.max(0, Color.red(base) - 55),
                Math.max(0, Color.green(base) - 55),
                Math.max(0, Color.blue(base) - 55)));
        canvas.drawCircle(cx, cy, r - 1, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(255,
                Math.min(255, Color.red(base) + 40),
                Math.min(255, Color.green(base) + 40),
                Math.min(255, Color.blue(base) + 40)));
        canvas.drawCircle(cx, cy, r * 0.5f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.16f, paint);
    }

    // Arcade challenge objectives HUD.
    private void drawChallenges(Canvas canvas) {
        if (state.isClassicMode() || state.activeChallenges.isEmpty()) return;
        paint.setTextAlign(Paint.Align.LEFT);
        float x = 10;
        // Kept below the top-center boss health bar + its "BOSS" label.
        float stripY = 78;
        float dotR = 7;
        float dotGap = 20;

        // Collapsed: a translucent pill holding one dot per challenge. The hit
        // area is larger than the visible pill so it's easy to tap.
        float stripW = 16 + state.activeChallenges.size() * dotGap + 16;
        float pillTop = stripY - 15;
        float pillBottom = stripY + 17;
        state.challengeStripRect.set(x - 24, pillTop - 16, x + stripW + 24, pillBottom + 16);

        if (!state.challengePanelOpen) {
            paint.setColor(Color.argb(90, 0, 0, 0));
            canvas.drawRoundRect(x, pillTop, x + stripW, pillBottom, 14, 14, paint);
            float dx = x + 12 + dotR;
            for (ActiveChallenge ac : state.activeChallenges) {
                paint.setColor(challengeColor(ac));
                canvas.drawCircle(dx, stripY, dotR, paint);
                dx += dotGap;
            }
            // small chevron hinting the strip can be tapped
            paint.setColor(Color.argb(150, 255, 255, 255));
            paint.setTextSize(18);
            canvas.drawText(">", x + stripW - 16, stripY + 6, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // Expanded: compact rows over a near-transparent backing.
        float nameSize = 30;
        float descSize = 23;
        float rowH = 70;
        float panelWidth = 0;
        for (ActiveChallenge ac : state.activeChallenges) {
            paint.setTextSize(nameSize);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            float w = paint.measureText(ac.def.name + "  +" + ac.def.reward);
            paint.setTextSize(descSize);
            paint.setTypeface(Typeface.DEFAULT);
            float w2 = paint.measureText(descFor(ac));
            float w3 = paint.measureText(ac.progress + "/" + ac.def.requiredProgress);
            float ww = Math.max(w, w2) + w3 + 30;
            if (ww > panelWidth) panelWidth = ww;
        }
        float panelHeight = state.activeChallenges.size() * rowH + 14;
        state.challengePanelRect.set(x, 66, x + panelWidth + 10, 66 + panelHeight);
        paint.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(state.challengePanelRect, 8, 8, paint);

        float ty = 84;
        for (ActiveChallenge ac : state.activeChallenges) {
            paint.setColor(challengeColor(ac));
            canvas.drawCircle(x + 12, ty - 10, 6, paint);
            paint.setTextSize(nameSize);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(ac.def.name + "  +" + ac.def.reward, x + 26, ty, paint);
            paint.setColor(Color.argb(200, 255, 255, 255));
            paint.setTextSize(descSize);
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(descFor(ac), x + 26, ty + 24, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(ac.progress + "/" + ac.def.requiredProgress, x + panelWidth, ty + 24, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            ty += rowH;
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT);
    }

    // Status colour for a challenge: green = done, grey = failed, amber = in
    // progress, red = fresh and untouched.
    private int challengeColor(ActiveChallenge ac) {
        if (ac.completed) return Color.rgb(90, 230, 120);
        if (ac.failed) return Color.rgb(150, 150, 150);
        if (ac.progress > 0) return Color.rgb(255, 215, 70);
        return Color.rgb(235, 90, 80);
    }

    // HUD description for a challenge, with the DIRECTION_LOCK forbidden
    // direction substituted in so players know which one to avoid.
    private String descFor(ActiveChallenge ac) {
        String desc = ac.def.description;
        if (ac.def.type == ChallengeDefinition.ChallengeType.DIRECTION_LOCK) {
            String[] names = {"up", "right", "down", "left"};
            String dir = ac.forbiddenDir >= 0 && ac.forbiddenDir < names.length
                    ? names[ac.forbiddenDir] : "?";
            desc = desc.replace("{dir}", dir);
        }
        return desc;
    }

    // On-screen direction buttons shown in the bottom-middle while playing.
    // The button pointing opposite to the snake's current direction is hidden
    // (turning back onto yourself is illegal), which doubles as a hint.
    private void drawDirectionPad(Canvas canvas) {
        if (!state.directionButtons) return;
        if (state.currentState != GameState.State.PLAYING
                && state.currentState != GameState.State.MP_PLAYING) return;
        GameState.SnakeData sd = state.snakes[state.playerIndex];
        if (!sd.alive || sd.body.isEmpty()) return;

        int dirX = sd.dirX, dirY = sd.dirY;
        if (!sd.inputQueue.isEmpty()) {
            Point last = sd.inputQueue.get(sd.inputQueue.size() - 1);
            dirX = last.x;
            dirY = last.y;
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(state.dpadUpBtn != null ? state.dpadUpBtn.height() * 0.5f : 40);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        RectF[] buttons = {state.dpadLeftBtn, state.dpadUpBtn, state.dpadDownBtn, state.dpadRightBtn};
        String[] glyphs = {"\u25C0", "\u25B2", "\u25BC", "\u25B6"};
        int[] dirs = {-1, 0, 0, 1};
        int[] dirYs = {0, -1, 1, 0};
        for (int i = 0; i < 4; i++) {
            if (dirs[i] == -dirX && dirYs[i] == -dirY) continue;
            RectF r = buttons[i];
            if (r == null) continue;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(110, 0, 0, 0));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, 14, 14, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.argb(200, 255, 255, 255));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, 14, 14, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(220, 255, 255, 255));
            canvas.drawText(glyphs[i], r.centerX(), r.centerY() + 14, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setStrokeWidth(0);
    }

    // The most recently killed snake dissolving cell-by-cell, with a flashing
    // head at the start. Plays while deathPending is set.
    private void drawDeath(Canvas canvas, float viewCameraX, float viewCameraY) {
        if (state.death.body.isEmpty()) return;
        long now = System.currentTimeMillis();
        float progress = (float) (now - state.deathStartMs) / GameState.DEATH_ANIM_MS;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;
        int n = state.death.body.size();
        for (int i = 0; i < n; i++) {
            // Dissolve head-to-tail: each segment fades when the wave reaches it.
            float segProgress = progress * n - i;
            if (segProgress >= 1) continue;
            int alpha = segProgress <= 0 ? 255 : (int) (255 * (1 - segProgress));
            float flicker = 0.6f + 0.4f * (float) Math.sin(now * 0.02 + i * 0.9);
            alpha = (int) (alpha * flicker);
            if (alpha < 0) alpha = 0;
            Point seg = state.death.body.get(i);
            float dDx = wrappedDelta(seg.x - viewCameraX, state.cols);
            float dDy = wrappedDelta(seg.y - viewCameraY, state.rows);
            if (Math.abs(dDx) >= state.viewportWidthCells / 2f
                    || Math.abs(dDy) >= state.viewportHeightCells / 2f) continue;
            float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + dDx);
            float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + dDy);
            int color = i == 0 ? state.death.headColor : state.death.bodyColor;
            paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawRect(px, py, px + state.cellSize - 1, py + state.cellSize - 1, paint);
            // Flashing head: pulse white/red while the dissolve is early.
            if (i == 0 && progress < 0.3f) {
                float blink = 0.5f + 0.5f * (float) Math.sin(now * 0.03);
                int hc = blink > 0.5 ? Color.WHITE : Color.rgb(255, 80, 60);
                paint.setColor(Color.argb(255, Color.red(hc), Color.green(hc), Color.blue(hc)));
                canvas.drawRect(px, py, px + state.cellSize - 1, py + state.cellSize - 1, paint);
            }
        }
    }

    // Visual-only particles (eat bursts): dots that arc away and fade, and a
    // ring that expands from the impact point.
    private void drawParticles(Canvas canvas, float viewCameraX, float viewCameraY) {
        if (state.particles.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (GameState.Particle p : state.particles) {
            float age = now - p.startMs;
            if (age < 0 || age >= p.lifeMs) continue;
            float prog = age / (float) p.lifeMs;
            float wx = p.x + p.vx * age / 1000f;
            float wy = p.y + p.vy * age / 1000f;
            float dx = wx - viewCameraX;
            float dy = wy - viewCameraY;
            if (Math.abs(dx) >= state.viewportWidthCells / 2f
                    || Math.abs(dy) >= state.viewportHeightCells / 2f) continue;
            float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f + dx);
            float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f + dy);
            if (p.ring) {
                float radius = p.size * state.cellSize * 5f * prog;
                int alpha = (int) (140 * (1 - prog));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2, state.cellSize * 0.12f));
                paint.setColor(Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
                canvas.drawCircle(px, py, radius, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeWidth(0);
            } else {
                int alpha = (int) (255 * (1 - prog));
                float r = Math.max(1.5f, p.size * state.cellSize * (1f - 0.4f * prog));

                // Glow on large fragments and embers
                if (p.glow && r > 2f) {
                    paint.setColor(Color.argb(alpha / 2, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
                    canvas.drawCircle(px, py, r * 2.2f, paint);
                }

                // Rotated fragments drawn as small oriented rectangles
                if (p.rotSpeed != 0) {
                    canvas.save();
                    canvas.rotate(p.rotation + p.rotSpeed * age / 1000f, px, py);
                    float half = r * 0.7f;
                    paint.setColor(Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
                    canvas.drawRect(px - half, py - half, px + half, py + half, paint);
                    canvas.restore();
                } else {
                    paint.setColor(Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
                    canvas.drawCircle(px, py, r, paint);
                }
            }
        }
    }

    // Boss segment-count label above head
    private void drawBossHealthBar(Canvas canvas) {
        if (state.isClassicMode() || !state.boss.alive || state.boss.body.isEmpty()) return;
        float w = Math.min(state.screenW * 0.5f, 480);
        float h = Math.max(10, state.cellSize * 0.3f);
        float x = (state.screenW - w) / 2f;
        float y = 12;
        float frac = state.boss.body.size() / (float) Math.max(1, state.boss.maxSegments);
        frac = Math.max(0, Math.min(1, frac));
        paint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawRoundRect(x - 3, y - 3, x + w + 3, y + h + 3, 6, 6, paint);
        int color;
        switch (state.boss.type) {
            case WALL_BUILDER: color = Color.rgb(0, 140, 255); break;
            case HEALER: color = Color.rgb(0, 200, 90); break;
            default: color = Color.rgb(200, 60, 220);
        }
        paint.setColor(color);
        canvas.drawRoundRect(x, y, x + w * frac, y + h, 4, 4, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(x, y, x + w, y + h, 4, 4, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(h + 6);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setColor(Color.WHITE);
        canvas.drawText("BOSS", state.screenW / 2f, y + h + 16, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
    }

    // Red vignette + "BOSS INCOMING" banner during the spawn telegraph.
    private void drawBossWarning(Canvas canvas) {
        if (state.bossWarningStartMs <= 0) return;
        long now = System.currentTimeMillis();
        float progress = (now - state.bossWarningStartMs) / (float) GameState.BOSS_WARNING_MS;
        if (progress < 0 || progress > 1) return;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.02);
        float cx = state.screenW / 2f;
        float cy = state.screenH / 2f;
        float radius = (float) Math.sqrt(cx * cx + cy * cy);
        paint.setColor(Color.WHITE);
        paint.setShader(new RadialGradient(cx, cy, radius * 0.85f,
                Color.argb(0, 255, 40, 40), Color.argb(70, 255, 30, 30),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, state.screenW, state.screenH, paint);
        paint.setShader(null);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(72);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        int ta = (int) (255 * (0.6f + 0.4f * pulse));
        paint.setColor(Color.argb(ta, 255, 60, 60));
        canvas.drawText("BOSS INCOMING", state.screenW / 2f, state.screenH * 0.30f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
    }

    // Cinematic boss death sequence overlay. Rendered on top of the frozen
    // game field. Emotional rhythm: impact → freeze → build-up → rush →
    // compression → explosion → shockwave → debris → calm → reward.
    private void drawBossDeathCinematic(Canvas canvas) {
        long now = System.currentTimeMillis();
        long elapsed = now - state.cinematicStartMs;
        float w = state.screenW;
        float h = state.screenH;
        float cx = w / 2f;
        float cy = h / 2f;
        float viewX = state.cameraX;
        float viewY = state.cameraY;

        long ht = GameState.BOSS_DEATH_HIT_STOP_MS;
        long le = GameState.BOSS_DEATH_CAMERA_END_MS;
        long ex = le;
        long hd = ex + GameState.BOSS_DEATH_EXPLOSION_MS;
        long tr = hd + GameState.BOSS_DEATH_HOLD_MS;
        int bossColor = state.cinematicBossColor;

        // ------ Phase 1 & 2: boss visible (hit stop, lunge, compression) ------
        if (elapsed < le && !state.cinematicBossBody.isEmpty()) {
            canvas.save();
            // Compression squash: X = 1.18, Y = 0.82 during the last COMPRESS_MS
            // before the explosion, centered on the boss head
            long compressStart = le - GameState.BOSS_DEATH_COMPRESS_MS;
            if (elapsed >= compressStart) {
                float cp = (elapsed - compressStart) / (float) GameState.BOSS_DEATH_COMPRESS_MS;
                float easeIn = cp * cp;
                float sx = 1f + 0.18f * easeIn;
                float sy = 1f - 0.18f * easeIn;
                Point head = state.cinematicBossBody.get(0);
                float hDx = wrappedDelta(head.x - viewX, state.cols);
                float hDy = wrappedDelta(head.y - viewY, state.rows);
                float hpx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + hDx);
                float hpy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + hDy);
                canvas.scale(sx, sy, hpx + state.cellSize / 2f, hpy + state.cellSize / 2f);
            }

            // Draw boss body from the cinematic snapshot
            for (int i = 0; i < state.cinematicBossBody.size(); i++) {
                Point seg = state.cinematicBossBody.get(i);
                float dDx = wrappedDelta(seg.x - viewX, state.cols);
                float dDy = wrappedDelta(seg.y - viewY, state.rows);
                if (Math.abs(dDx) >= state.viewportWidthCells / 2f
                        || Math.abs(dDy) >= state.viewportHeightCells / 2f) continue;
                float px = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + dDx);
                float py = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + dDy);

                // Base body colour — dim toward the tail
                int dim = (i == 0) ? 255 : Math.max(80, 180 - i * 15);
                int segColor = Color.rgb(
                        Color.red(bossColor) * dim / 255,
                        Color.green(bossColor) * dim / 255,
                        Color.blue(bossColor) * dim / 255);

                if (elapsed < ht) {
                    // Hit stop: bright white flash blended with boss colour
                    float flashP = 1f - elapsed / (float) ht;
                    int flashWhite = (int) (200 * flashP);
                    paint.setColor(Color.argb(235,
                            Math.min(255, Color.red(segColor) + flashWhite),
                            Math.min(255, Color.green(segColor) + flashWhite),
                            Math.min(255, Color.blue(segColor) + flashWhite)));
                } else {
                    // Lunge phase: boss colour with a white energy glow build-up
                    float lungeP = (elapsed - ht) / (float) GameState.BOSS_DEATH_CAMERA_WINDUP_MS;
                    int glowAdd = (int) (60 * lungeP);
                    paint.setColor(Color.argb(235,
                            Math.min(255, Color.red(segColor) + glowAdd),
                            Math.min(255, Color.green(segColor) + glowAdd),
                            Math.min(255, Color.blue(segColor) + glowAdd)));
                }
                canvas.drawRect(px, py, px + state.cellSize - 1, py + state.cellSize - 1, paint);
            }

            // Head glow ramp-up during the lunge
            if (elapsed >= ht) {
                drawBossHeadGlow(canvas, viewX, viewY, bossColor,
                        (elapsed - ht) / (float) GameState.BOSS_DEATH_CAMERA_WINDUP_MS);
            }
            canvas.restore();
        }

        // ------ Phase 3–5: explosion, debris, transition ------
        if (elapsed >= le) {
            // Background dim (subtle, ramps up quickly)
            float dimP = Math.min(1f, (elapsed - le) / 150f);
            int dimAlpha = (int) (110 * dimP);
            paint.setColor(Color.argb(dimAlpha, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, paint);

            // Brief chromatic aberration flash at the explosion moment
            if (elapsed < le + 100) {
                float caP = 1f - (elapsed - le) / 100f;
                int caA = (int) (80 * caP);
                float offset = 8 * caP;
                paint.setColor(Color.argb(caA, 255, 0, 0));
                canvas.drawRect(-offset, 0, w - offset, h, paint);
                paint.setColor(Color.argb(caA, 0, 0, 255));
                canvas.drawRect(offset, 0, w + offset, h, paint);
                paint.setColor(Color.argb(caA, 0, 255, 0));
                canvas.drawRect(0, 0, w, h, paint);
            }

            // Draw explosion particles on top of dim so they pop
            drawParticles(canvas, viewX, viewY);

            // Shockwave ring at explosion center
            if (state.cinematicShockwaveAt > 0) {
                long swAge = now - state.cinematicShockwaveAt;
                if (swAge < 550) {
                    float swP = swAge / 550f;
                    float fx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f
                            + wrappedDelta(state.cinematicFocusX - viewX, state.cols));
                    float fy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f
                            + wrappedDelta(state.cinematicFocusY - viewY, state.rows));
                    float radius = state.cellSize * (0.5f + swP * 5f);
                    int swAlpha = (int) (200 * (1f - swP));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(3, state.cellSize * 0.12f) * (1f - swP * 0.5f));
                    paint.setColor(Color.argb(swAlpha,
                            Color.red(bossColor), Color.green(bossColor), Color.blue(bossColor)));
                    canvas.drawCircle(fx, fy, radius, paint);
                    // Outer glow ring
                    paint.setStrokeWidth(paint.getStrokeWidth() * 1.8f);
                    paint.setColor(Color.argb(swAlpha / 3,
                            Color.red(bossColor), Color.green(bossColor), Color.blue(bossColor)));
                    canvas.drawCircle(fx, fy + 1, radius + 2, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setStrokeWidth(0);
                }
            }

            // Coloured explosion flash (fades quickly)
            if (elapsed < le + 250) {
                float fp = 1f - (elapsed - le) / 250f;
                int fa = (int) (100 * fp * fp);
                paint.setColor(Color.argb(fa,
                        Color.red(bossColor), Color.green(bossColor), Color.blue(bossColor)));
                canvas.drawRect(0, 0, w, h, paint);
            }

            // "BOSS DEFEATED" text
            if (elapsed < hd) {
                float textP = Math.min(1f, (elapsed - le) / 250f);
                float rise = (1f - textP) * 30f;
                int ta = (int) (255 * textP);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(48);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6);
                paint.setColor(Color.argb(ta, 0, 0, 0));
                canvas.drawText("BOSS DEFEATED", cx, cy * 0.45f - rise, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(ta, 255, 215, 80));
                canvas.drawText("BOSS DEFEATED", cx, cy * 0.45f - rise, paint);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setStrokeWidth(0);
            }

            // Transition fade to black
            if (elapsed >= tr) {
                float fadeP = Math.min(1f, (elapsed - tr) / (float) GameState.BOSS_DEATH_TRANSITION_MS);
                paint.setColor(Color.argb((int) (255 * fadeP), 0, 0, 0));
                canvas.drawRect(0, 0, w, h, paint);
            }
        }

        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
    }

    // Draws the pulsing head glow during the camera lunge phase
    private void drawBossHeadGlow(Canvas canvas, float viewX, float viewY, int color, float progress) {
        if (state.cinematicBossBody.isEmpty()) return;
        Point head = state.cinematicBossBody.get(0);
        float hDx = wrappedDelta(head.x - viewX, state.cols);
        float hDy = wrappedDelta(head.y - viewY, state.rows);
        if (Math.abs(hDx) >= state.viewportWidthCells / 2f
                || Math.abs(hDy) >= state.viewportHeightCells / 2f) return;
        float hx = state.boardLeft + state.cellSize * (state.viewportWidthCells / 2f - 0.5f + hDx);
        float hy = state.boardTop + state.cellSize * (state.viewportHeightCells / 2f - 0.5f + hDy);
        float glowR = state.cellSize * (1.5f + progress * 1.5f);
        int glowA = (int) (80 + 120 * progress);
        paint.setColor(Color.WHITE);
        paint.setShader(new RadialGradient(hx + state.cellSize / 2f, hy + state.cellSize / 2f, glowR,
                Color.argb(glowA, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                Shader.TileMode.CLAMP));
        canvas.drawCircle(hx + state.cellSize / 2f, hy + state.cellSize / 2f, glowR, paint);
        paint.setShader(null);
    }

    // Floating reward notifications (e.g. "+30") that rise above the middle of
    // the screen and fade out over their duration.
    private void drawChallengePopups(Canvas canvas) {
        if (state.challengePopups.isEmpty()) return;
        long now = System.currentTimeMillis();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(34);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        for (GameState.ChallengePopup p : state.challengePopups) {
            float progress = (float) (now - p.startMs) / p.durationMs;
            if (progress < 0 || progress > 1) continue;
            int alpha = progress < 0.7f ? 255 : (int) (255 * (1 - progress) / 0.3f);
            float rise = progress * 46;
            paint.setColor(Color.argb(alpha, 0, 0, 0));
            canvas.drawText(p.text, p.x + 2, p.y - rise + 2, paint);
            paint.setColor(Color.argb(alpha, 255, 215, 80));
            canvas.drawText(p.text, p.x, p.y - rise, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
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
        if (state.currentState == GameState.State.BOSS_DEATH_CINEMATIC) {
            long now = System.currentTimeMillis();
            long elapsed = now - state.cinematicStartMs;
            float cameraZoom = 1f;

            // Smoothly pan from the player's position to the boss head over
            // the hit stop and camera lunge phases combined.
            long panDuration = GameState.BOSS_DEATH_HIT_STOP_MS + GameState.BOSS_DEATH_CAMERA_WINDUP_MS;
            float panP = Math.min(1f, elapsed / (float) panDuration);
            // Ease-out cubic for a "lunging" feel that rushes in and settles
            float panEase = 1f - (1f - panP) * (1f - panP) * (1f - panP);
            float fromX = state.cinematicCameraStartX;
            float fromY = state.cinematicCameraStartY;
            state.cameraX = fromX + (state.cinematicFocusX - fromX) * panEase;
            state.cameraY = fromY + (state.cinematicFocusY - fromY) * panEase;

            // Camera zoom — only during the lunge phase (after hit stop),
            // using an ease-out-back overshoot for momentum
            if (elapsed > GameState.BOSS_DEATH_HIT_STOP_MS) {
                float windupElapsed = elapsed - GameState.BOSS_DEATH_HIT_STOP_MS;
                float windupP = Math.min(1f, windupElapsed / (float) GameState.BOSS_DEATH_CAMERA_WINDUP_MS);
                // easeOutBack for overshoot: rushes past target then settles back
                float c1 = 1.70158f;
                float c3 = c1 + 1f;
                float u = windupP - 1f;
                float ease = 1f + c3 * u * u * u + c1 * u * u;
                cameraZoom = 1f + 0.18f * ease;
            }
            state.cinematicCameraZoom = cameraZoom;
            return;
        }
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
            String[] bossLabels = {"BOSS: RANDOM", "BOSS: CHASER", "BOSS: WALL", "BOSS: HEALER"};
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

    // Post-boss upgrade selection: a reward-chest moment. Cards fly in one by
    // one over a dimmed, breathing backdrop; tapping a card highlights it and
    // reveals a Choose button; the Skip option lands last. Rects are stored on
    // state each frame for touch hit-testing.
    private void drawUpgradeScreen(Canvas canvas) {
        long now = System.currentTimeMillis();
        int n = state.upgradeOffers.size();
        float w = state.screenW;
        float h = state.screenH;
        float cx = w / 2f;

        drawUpgradeBackdrop(canvas, now);

        // Title fades in with a gentle rise.
        float titleP = clamp01((now - state.upgradeOpenAt) / 300f);
        drawUpgradeTitle(canvas, "CHOOSE AN UPGRADE",
                cx, h * 0.115f + (1f - titleP) * 26f, titleP);

        // Card column.
        float margin = Math.max(24f, w * 0.06f);
        float top = h * 0.19f;
        float bottom = h * 0.76f;
        float gap = h * 0.012f;
        float cardH = n > 0 ? (bottom - top - gap * (n - 1)) / n : 0;
        float left = margin;
        float right = w - margin;
        float cardW = right - left;

        for (int i = 0; i < n; i++) {
            float cy = top + cardH * i + gap * i + cardH / 2f;
            RectF r = new RectF(left, cy - cardH / 2f, right, cy + cardH / 2f);
            state.upgradeCardRects[i] = r;
            boolean selected = i == state.upgradeSelectedIndex;
            boolean epic = state.upgradeOffers.get(i).rarity == GameState.UpgradeRarity.EPIC;

            // Staggered fly-in: cards rise and scale in one after another.
            float p = clamp01((now - state.upgradeOpenAt
                    - i * GameState.UPGRADE_CARD_DELAY_MS) / (float) GameState.UPGRADE_CARD_ENTRY_MS);
            float ease = easeOut(p);
            float rise = (1f - ease) * 44f;
            float scale = epic ? entranceScale(p) : 0.88f + 0.12f * ease;
            float alpha = p;

            // Selection pop: quick overshoot that settles slightly enlarged.
            float pop = selected ? clamp01((now - state.upgradeSelectMs) / 220f) : 0f;
            float selScale = 1f + 0.05f * easeOutBack(pop);

            canvas.save();
            canvas.translate(0, rise);
            canvas.scale(scale * selScale, scale * selScale, r.centerX(), r.centerY());
            drawUpgradeCard(canvas, r, state.upgradeOffers.get(i), selected, alpha, now);
            canvas.restore();
        }

        // Choose + Skip buttons live in reserved bands below the cards so the
        // layout never jumps when a card is highlighted.
        float btnW = cardW * 0.5f;
        float btnH = h * 0.048f;
        state.upgradeChooseBtn = new RectF(cx - btnW / 2f, h * 0.772f,
                cx + btnW / 2f, h * 0.772f + btnH);
        float skipW = cardW * 0.56f;
        state.upgradeSkipBtn = new RectF(cx - skipW / 2f, h * 0.852f,
                cx + skipW / 2f, h * 0.852f + btnH);

        // Skip button — lands after the cards.
        float skipAt = (n - 1) * GameState.UPGRADE_CARD_DELAY_MS
                + GameState.UPGRADE_CARD_ENTRY_MS + GameState.UPGRADE_SKIP_EXTRA_MS;
        float skipP = clamp01((now - state.upgradeOpenAt - skipAt) / 220f);
        drawSkipButton(canvas, state.upgradeSkipBtn, skipP);

        // Choose button — once a card is highlighted.
        if (state.upgradeSelectedIndex >= 0 && state.upgradeSelectedIndex < n) {
            drawChooseButton(canvas, state.upgradeChooseBtn, state.upgradeSelectedIndex, now);
        }

        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(Color.WHITE);
    }

    // Dim veil + breathing radial glow + slow ambient motes. The paused game
    // stays dimly visible so the player remembers they're mid-run.
    private void drawUpgradeBackdrop(Canvas canvas, long now) {
        float w = state.screenW;
        float h = state.screenH;
        float cx = w / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(178, 0, 0, 0));
        canvas.drawRect(0, 0, w, h, paint);

        // Breathing radial glow behind the card stack.
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.0012);
        float glowR = Math.max(w, h) * (0.6f + 0.10f * pulse);
        int glowA = (int) (26 + 18 * pulse);
        paint.setShader(new RadialGradient(cx, h * 0.5f, glowR,
                Color.argb(glowA, 255, 215, 90), Color.argb(0, 255, 215, 90),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Epic reveal flash rides above the veil so it reads as a reward pop.
        if (state.flashAlpha > 0) {
            paint.setColor(Color.argb(
                    (int) (255 * state.flashAlpha),
                    Color.red(state.flashColor),
                    Color.green(state.flashColor),
                    Color.blue(state.flashColor)));
            canvas.drawRect(0, 0, w, h, paint);
        }

        drawAmbientMotes(canvas, now);
    }

    // Slow drifting dust motes, fully deterministic from wall-clock time so
    // there is no particle state to update or prune.
    private void drawAmbientMotes(Canvas canvas, long now) {
        float w = state.screenW;
        float h = state.screenH;
        paint.setStyle(Paint.Style.FILL);
        float t = now * 0.00016f;
        for (int i = 0; i < 16; i++) {
            float x = w * frac(i * 0.618f + t * (0.4f + (i % 5) * 0.10f));
            float y = h * frac(i * 0.314f - t * (0.3f + (i % 4) * 0.08f));
            float tw = 0.5f + 0.5f * (float) Math.sin(now * 0.002f + i * 1.7f);
            int alpha = (int) (20 + 30 * tw);
            float size = 1.6f + (i % 3) * 1.3f;
            paint.setColor(Color.argb(alpha, 255, 238, 180));
            canvas.drawCircle(x, y, size, paint);
        }
    }

    private void drawUpgradeTitle(Canvas canvas, String text, float cx, float cy, float alpha) {
        paint.setAlpha((int) (255 * alpha));
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        fitTextSize(text, 42f, state.screenW * 0.92f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        // Dark halo so the gold title reads over the dimmed game.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setColor(Color.argb((int) (200 * alpha), 0, 0, 0));
        canvas.drawText(text, cx, textY, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 216, 96));
        canvas.drawText(text, cx, textY, paint);
        paint.setAlpha(255);
    }

    private void drawSkipButton(Canvas canvas, RectF r, float alpha) {
        if (r == null) return;
        paint.setAlpha((int) (255 * alpha));
        float radius = 12;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(30, 33, 39));
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.rgb(130, 140, 150));
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(200, 206, 214));
        paint.setTextSize(22);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = r.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("SKIP — NO UPGRADE", r.centerX(), textY, paint);
        paint.setAlpha(255);
    }

    private void drawChooseButton(Canvas canvas, RectF r, int cardIndex, long now) {
        if (r == null) return;
        GameState.UpgradeCard card = state.upgradeOffers.get(cardIndex);
        int rc = rarityColor(card.rarity);
        // Pop in with a touch of the rarity color.
        float p = clamp01((now - state.upgradeSelectMs) / 220f);
        float scale = 0.92f + 0.08f * easeOutBack(p);
        canvas.save();
        canvas.scale(scale, scale, r.centerX(), r.centerY());
        float radius = 12;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(Color.rgb(28, 30, 36), rc, 0.16f));
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9);
        paint.setColor(Color.argb(70, Color.red(rc), Color.green(rc), Color.blue(rc)));
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setStrokeWidth(3);
        paint.setColor(rc);
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(22);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = r.centerY() - (fm.ascent + fm.descent) / 2f - 1;
        canvas.drawText("CHOOSE", r.centerX(), textY, paint);
        // Little chevron pointing up at the cards.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(rc);
        canvas.drawLine(r.centerX(), r.top - 8, r.centerX() - 6, r.top, paint);
        canvas.drawLine(r.centerX(), r.top - 8, r.centerX() + 6, r.top, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        canvas.restore();
        paint.setAlpha(255);
    }

    private int rarityColor(GameState.UpgradeRarity rarity) {
        switch (rarity) {
            case EPIC: return Color.rgb(200, 90, 255);
            case RARE: return Color.rgb(80, 150, 255);
            default:   return Color.rgb(175, 185, 195);
        }
    }

    // Shrinks textSize until `text` fits within maxWidth, so long card strings
    // never spill past the card edges regardless of screen size/card shape.
    private float fitTextSize(String text, float textSize, float maxWidth) {
        float size = textSize;
        paint.setTextSize(textSize);
        while (size > 1f && paint.measureText(text) > maxWidth) {
            size *= 0.9f;
            if (size < 1f) break;
        }
        paint.setTextSize(size);
        return size;
    }

    // --- small animation/color helpers (shared by the upgrade screen) ---

    private float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private float frac(float v) { return v - (float) Math.floor(v); }
    private float easeOut(float p) { return 1f - (1f - p) * (1f - p); }
    private float easeOutBack(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = p - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
    // Epic cards bounce in with an overshoot, then settle at exactly 1.0.
    private float entranceScale(float p) {
        float base = 0.78f + 0.22f * easeOut(p);
        float pop = (float) Math.sin(Math.min(1f, p) * Math.PI);
        return base + 0.08f * pop;
    }
    private int blend(int c1, int c2, float t) {
        int r = (int) (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
        int b = (int) (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
        return Color.rgb(r, g, b);
    }

    private void drawUpgradeCard(Canvas canvas, RectF r, GameState.UpgradeCard card,
                                 boolean selected, float alpha, long now) {
        int rc = rarityColor(card.rarity);
        boolean epic = card.rarity == GameState.UpgradeRarity.EPIC;
        float h = r.height();
        float cx = r.centerX();
        float radius = Math.max(12f, h * 0.10f);
        int cardAlpha = (int) (255 * alpha);

        paint.setAlpha(cardAlpha);

        drawCardShadow(canvas, r, radius);

        // Body gradient — brightens slightly while selected.
        int topC = selected ? Color.rgb(58, 64, 78) : Color.rgb(35, 39, 49);
        int botC = selected ? Color.rgb(28, 32, 40) : Color.rgb(16, 18, 24);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(r.left, r.top, r.left, r.bottom,
                topC, botC, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setShader(null);

        // Rarity glow halo — thicker and brighter when selected, strongest for
        // Epic.
        int haloW = epic ? (selected ? 22 : 15) : (selected ? 13 : 9);
        int haloA = epic ? (selected ? 95 : 60) : (selected ? 75 : 40);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(haloW);
        paint.setColor(Color.argb((int) (haloA * alpha), Color.red(rc), Color.green(rc), Color.blue(rc)));
        canvas.drawRoundRect(r, radius, radius, paint);

        // Thin bright border.
        paint.setStrokeWidth(selected ? 4 : 3);
        paint.setColor(Color.argb(cardAlpha, Color.red(rc), Color.green(rc), Color.blue(rc)));
        canvas.drawRoundRect(r, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        float pad = h * 0.10f;
        float innerL = r.left + pad * 1.4f;
        float innerR = r.right - pad * 1.4f;
        float maxTextW = innerR - innerL;

        // Top row: rarity label (left) + stack counter (right).
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        fitTextSize(card.rarity.name(), h * 0.12f, maxTextW * 0.45f);
        paint.setColor(Color.argb(cardAlpha, Color.red(rc), Color.green(rc), Color.blue(rc)));
        canvas.drawText(card.rarity.name(), innerL, r.top + h * 0.14f, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        String stackText = card.stack + " / " + card.maxStack;
        fitTextSize(stackText, h * 0.12f, maxTextW * 0.45f);
        paint.setColor(Color.argb(cardAlpha, 230, 236, 244));
        canvas.drawText(stackText, innerR, r.top + h * 0.14f, paint);

        // Name — the visual anchor.
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        fitTextSize(card.name, h * 0.19f, maxTextW);
        paint.setColor(Color.argb(cardAlpha, 255, 255, 255));
        canvas.drawText(card.name, cx, r.top + h * 0.36f, paint);

        // Divider under the name.
        paint.setStrokeWidth(Math.max(1.5f, h * 0.007f));
        paint.setColor(Color.argb((int) (65 * alpha), Color.red(rc), Color.green(rc), Color.blue(rc)));
        canvas.drawLine(cx - maxTextW * 0.26f, r.top + h * 0.41f,
                cx + maxTextW * 0.26f, r.top + h * 0.41f, paint);
        paint.setStrokeWidth(0);
        paint.setStyle(Paint.Style.FILL);

        // Effect description (1-2 lines, each width-fitted).
        String[] lines = card.description.split("\n");
        float lineH = Math.min(h * 0.12f, h * 0.10f + 2f);
        float descTop = r.top + h * 0.46f;
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setColor(Color.argb(cardAlpha, 255, 255, 255));
        for (int i = 0; i < lines.length; i++) {
            fitTextSize(lines[i], Math.min(h * 0.10f, 30f), maxTextW);
            canvas.drawText(lines[i], cx, descTop + i * lineH, paint);
        }

        // Flavor text — italic, visible.
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        paint.setColor(Color.argb(cardAlpha, 196, 202, 214));
        String flavor = "\u201C" + card.flavor + "\u201D";
        fitTextSize(flavor, Math.min(h * 0.08f, 24f), maxTextW);
        // Position flavor text based on description line count so it never overlaps
        float descBottom = descTop + lines.length * lineH;
        float flavorY = Math.max(r.top + h * 0.82f, descBottom + h * 0.06f);
        canvas.drawText(flavor, cx, flavorY, paint);

        // Stack pips along the bottom edge.
        float pipR = Math.min(h * 0.025f, 6f);
        float pipGap = pipR * 3.2f;
        float pipY = r.bottom - Math.max(8, h * 0.04f);
        float pipStart = cx - (card.maxStack - 1) * pipGap / 2f;
        for (int j = 0; j < card.maxStack; j++) {
            float px = pipStart + j * pipGap;
            if (j < card.stack) {
                paint.setColor(Color.argb(cardAlpha, Color.red(rc), Color.green(rc), Color.blue(rc)));
            } else {
                paint.setColor(Color.argb((int) (55 * alpha), Color.red(rc), Color.green(rc), Color.blue(rc)));
            }
            canvas.drawCircle(px, pipY, pipR, paint);
        }

        // Rarity ambience around the highlighted card.
        if (selected) drawCardSparkles(canvas, r, card, now);

        paint.setAlpha(255);
    }

    // Two offset translucent rounds fake a soft drop shadow cheaply.
    private void drawCardShadow(Canvas canvas, RectF r, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 0, 0, 0));
        canvas.drawRoundRect(r.left + 2, r.top + 4, r.right + 2, r.bottom + 4,
                radius, radius, paint);
        paint.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(r.left + 5, r.top + 8, r.right + 5, r.bottom + 8,
                radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // Continuous rarity ambience around the highlighted card, plus the one-shot
    // burst when it was just tapped. Everything is time-driven — no state.
    private void drawCardSparkles(Canvas canvas, RectF r, GameState.UpgradeCard card, long now) {
        int rc = rarityColor(card.rarity);
        float cx = r.centerX();
        float cy = r.centerY();
        if (card.rarity == GameState.UpgradeRarity.EPIC) {
            // Rising purple embers.
            for (int i = 0; i < 6; i++) {
                float t = frac(now * 0.0007f + i * 0.29f);
                float ex = cx + (float) Math.sin(i * 1.9f + t * 9.0f) * r.width() * 0.20f;
                float ey = r.bottom - 6 - t * r.height() * 1.05f;
                int a = (int) (130 * (1f - t));
                paint.setColor(Color.argb(a, 200, 130, 255));
                canvas.drawCircle(ex, ey, 2.2f + (i % 3) * 0.8f, paint);
            }
            // Bright glitter ring.
            for (int i = 0; i < 5; i++) {
                float a = now * 0.0014f + i * (float) (Math.PI * 2 / 5);
                float ox = cx + r.width() * 0.56f * (float) Math.cos(a);
                float oy = cy + r.height() * 0.64f * (float) Math.sin(a) * 0.7f;
                int aa = 60 + (int) (50 * (0.5 + 0.5 * Math.sin(now * 0.005 + i * 2.1)));
                paint.setColor(Color.argb(aa, Color.red(rc), Color.green(rc), Color.blue(rc)));
                canvas.drawCircle(ox, oy, 2.2f, paint);
            }
        } else if (card.rarity == GameState.UpgradeRarity.RARE) {
            // Subtle blue drifting sparks.
            for (int i = 0; i < 4; i++) {
                float t = frac(now * 0.0011f + i * 0.37f);
                float ox = cx + r.width() * 0.52f * (float) Math.cos(i * 2.4f + t * 4f);
                float oy = cy + r.height() * 0.60f * (float) Math.sin(i * 1.7f + t * 5f) * 0.7f;
                int aa = 50 + (int) (45 * (0.5 + 0.5 * Math.sin(now * 0.004 + i * 2.6)));
                paint.setColor(Color.argb(aa, Color.red(rc), Color.green(rc), Color.blue(rc)));
                canvas.drawCircle(ox, oy, 1.8f, paint);
            }
        } else {
            // COMMON: subtle gray drift so these cards don't look empty.
            for (int i = 0; i < 3; i++) {
                float t = frac(now * 0.0009f + i * 0.41f);
                float ox = cx + r.width() * 0.48f * (float) Math.cos(i * 2.7f + t * 3f);
                float oy = cy + r.height() * 0.55f * (float) Math.sin(i * 1.9f + t * 4f) * 0.7f;
                int aa = 30 + (int) (25 * (0.5 + 0.5 * Math.sin(now * 0.003 + i * 3.1)));
                paint.setColor(Color.argb(aa, Color.red(rc), Color.green(rc), Color.blue(rc)));
                canvas.drawCircle(ox, oy, 1.4f, paint);
            }
        }
        drawSelectBurst(canvas, r, now);
    }

    // One-shot spark burst radiating from the card the instant it's tapped.
    private void drawSelectBurst(Canvas canvas, RectF r, long now) {
        long age = now - state.upgradeSelectMs;
        if (age < 0 || age >= 520) return;
        if (state.upgradeSelectedIndex < 0
                || state.upgradeSelectedIndex >= state.upgradeOffers.size()) return;
        float p = age / 520f;
        GameState.UpgradeCard card = state.upgradeOffers.get(state.upgradeSelectedIndex);
        if (card == null) return;
        int rc = rarityColor(card.rarity);
        float cx = r.centerX();
        float cy = r.centerY();
        float seed = state.upgradeSelectSeed;
        for (int i = 0; i < 10; i++) {
            float ang = (float) (i / 10.0 * Math.PI * 2 + seed * 0.001f);
            float dist = 26 + 70 * p * (0.5f + 0.5f * (float) Math.sin(i * 2.4f + seed));
            float x = cx + (float) Math.cos(ang) * dist;
            float y = cy + (float) Math.sin(ang) * dist * 0.6f;
            int a = (int) (220 * (1f - p));
            paint.setColor(Color.argb(a, Color.red(rc), Color.green(rc), Color.blue(rc)));
            canvas.drawCircle(x, y, 3f * (1f - 0.5f * p), paint);
        }
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
        drawCenteredText(canvas, "DIRECTION BUTTONS", state.screenW / 2f, state.directionButtonsBtn.top - 10, 26, Color.WHITE, false);
        drawButton(canvas, state.directionButtonsBtn, state.directionButtons ? "ON" : "OFF");
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
