package com.benjoo.bsnake;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.Random;

public class SnakeEngine {

    private final GameState state;
    private final PersistenceManager persistence;
    private final Random rand = new Random();
    private SoundEffects sound;
    private static final int BOSS_MOVE_INTERVAL = 6;
    private static final int TRAIL_MAX_AGE = 40;
    private static final int BOSS_SPAWN_INTERVAL = 125;
    private static final int BOSS_DEFEAT_SCORE = 25;
    private static final int BOSS_DEFEAT_GROWTH = 5;
    private static final int BOSS_HIT_SCORE = 5;
    private static final int BOSS_HIT_SHRINK = 3;
    private static final int TRAIL_CELLS_PER_HIT = 3;

    SnakeEngine(GameState state, PersistenceManager persistence) {
        this.state = state;
        this.persistence = persistence;
    }

    void setSoundEffects(SoundEffects sound) {
        this.sound = sound;
    }

    void resetGame() {
        state.mpLabelVisible = true;
        for (int i = 0; i < 2; i++) {
            state.snakes[i] = new GameState.SnakeData();
            state.snakes[i].headColor = (i == 0) == state.isHost ? state.headColor : state.clientColor;
            state.snakes[i].bodyColor = (i == 0) == state.isHost ? state.bodyColor : state.clientColor;
        }
        state.score = state.devMode ? state.devStartScore : 0;
        state.snakes[0].score = state.score;
        int startX = state.cols / 2;
        int startY = state.rows / 2;
        for (int i = 0; i < 3; i++) {
            state.snakes[0].body.add(new Point(startX - Math.min(i, 2), startY));
            state.snakes[1].body.add(new Point(startX + Math.min(i, 2) + 3, startY));
        }
        state.cameraX = startX;
        state.cameraY = startY;
        state.cameraInitialized = true;
        state.snakes[0].dirX = 1;
        state.snakes[0].dirY = 0;
        state.snakes[1].dirX = -1;
        state.snakes[1].dirY = 0;
        state.snakes[0].inputQueue.clear();
        state.snakes[1].inputQueue.clear();
        state.boss.alive = false;
        state.bossGrowthPending = 0;
        state.bossTrail.clear();
        state.nextBossSpawnScore = BOSS_SPAWN_INTERVAL;
        state.tickCount = 0;
        placeFood();
        for (int i = 0; i < 2; i++) {
            state.snakes[i].prevBody.clear();
            for (Point p : state.snakes[i].body)
                state.snakes[i].prevBody.add(new Point(p));
            state.snakes[i].alive = true;
        }
    }

    void resetSinglePlayer() {
        state.mpLabelVisible = false;
        for (int i = 0; i < 2; i++) {
            state.snakes[i] = new GameState.SnakeData();
        }
        state.snakes[0].headColor = state.headColor;
        state.snakes[0].bodyColor = state.bodyColor;
        state.score = state.devMode ? state.devStartScore : 0;
        state.snakes[0].score = state.score;
        int startX = state.cols / 2;
        int startY = state.rows / 2;
        for (int i = 0; i < 3; i++) {
            state.snakes[0].body.add(new Point(startX - Math.min(i, 2), startY));
        }
        state.cameraX = startX;
        state.cameraY = startY;
        state.cameraInitialized = true;
        state.snakes[0].dirX = 1;
        state.snakes[0].dirY = 0;
        state.snakes[0].inputQueue.clear();
        state.boss.alive = false;
        state.bossGrowthPending = 0;
        state.bossTrail.clear();
        state.nextBossSpawnScore = BOSS_SPAWN_INTERVAL;
        state.tickCount = 0;
        placeFood();
        state.snakes[0].prevBody.clear();
        for (Point p : state.snakes[0].body)
            state.snakes[0].prevBody.add(new Point(p));
        state.snakes[0].alive = true;
        state.snakes[1].alive = false;
    }

    void update() {
        state.tickCount++;

        // Process each alive snake
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive) continue;

            // Consume one queued direction
            if (!sd.inputQueue.isEmpty()) {
                Point nextDir = sd.inputQueue.remove(0);
                sd.dirX = nextDir.x;
                sd.dirY = nextDir.y;
            }

            // Snapshot for interpolation
            sd.prevBody.clear();
            for (Point p : sd.body) sd.prevBody.add(new Point(p));

            // Compute new head position with toroidal wrap
            Point head = sd.body.get(0);
            int nx = head.x + sd.dirX;
            int ny = head.y + sd.dirY;
            if (nx < 0) nx = state.cols - 1;
            if (nx >= state.cols) nx = 0;
            if (ny < 0) ny = state.rows - 1;
            if (ny >= state.rows) ny = 0;

            // Self-collision
            for (Point p : sd.body) {
                if (p.x == nx && p.y == ny) {
                    sd.alive = false;
                    break;
                }
            }
            if (!sd.alive) continue;

            // Snake-vs-snake body collision (check against the OTHER snake's body,
            // even if the other is already dead — its body still blocks)
            int oi = 1 - si;
            GameState.SnakeData other = state.snakes[oi];
            for (Point p : other.body) {
                if (p.x == nx && p.y == ny) {
                    sd.alive = false;
                    break;
                }
            }
            if (!sd.alive) continue;

            // Head-on: only if both are alive
            if (other.alive) {
                Point oh = other.body.get(0);
                int onx = oh.x + other.dirX;
                int ony = oh.y + other.dirY;
                if (onx < 0) onx = state.cols - 1;
                if (onx >= state.cols) onx = 0;
                if (ony < 0) ony = state.rows - 1;
                if (ony >= state.rows) ony = 0;
                if (nx == onx && ny == ony) {
                    if (sd.body.size() > other.body.size()) {
                        other.alive = false;
                    } else if (other.body.size() > sd.body.size()) {
                        sd.alive = false;
                    } else {
                        sd.alive = false;
                        other.alive = false;
                    }
                    if (!sd.alive) continue;
                }
            }

            // Prepend new head
            sd.body.add(0, new Point(nx, ny));

            // Food eating (only snake[0] gets score for now; snake[1]'s score comes from host)
            boolean ateFood = false;
            Point eatenFood = null;
            for (Point f : state.foods) {
                if (nx == f.x && ny == f.y) { eatenFood = f; break; }
            }
            if (eatenFood != null) {
                state.foods.remove(eatenFood);
                ateFood = true;
                sd.score++;
                state.score = sd.score;
                if (sound != null && si == 0) sound.playCrunch();
            }

            // Boss collision
            boolean hitBoss = false;
            if (state.boss.alive) {
                for (Point tile : state.boss.getTiles()) {
                    if (nx == tile.x && ny == tile.y) { hitBoss = true; break; }
                }
                if (hitBoss) {
                    sd.score += BOSS_HIT_SCORE;
                    state.score = sd.score;
                    boolean killingBlow = state.boss.hp <= 1;
                    damageBoss();
                    if (sound != null) {
                        if (killingBlow) sound.playBossDefeat();
                        else sound.playBossDamage();
                    }
                }
            }

            // Trail eating
            boolean ateTrail = false;
            for (int i = state.bossTrail.size() - 1; i >= 0; i--) {
                GameState.BossTrailCell tc = state.bossTrail.get(i);
                if (nx == tc.x && ny == tc.y) {
                    state.bossTrail.remove(i);
                    ateTrail = true;
                    sd.score++;
                    state.score = sd.score;
                    break;
                }
            }

            // Growth / shrink / detach
            if (hitBoss) {
                int shrink = BOSS_HIT_SHRINK;
                while (shrink > 0 && sd.body.size() > 3) {
                    sd.body.remove(sd.body.size() - 1);
                    shrink--;
                }
            } else if (state.bossGrowthPending > 0 && si == 0) {
                state.bossGrowthPending--;
            } else if (!ateFood && !ateTrail) {
                sd.body.remove(sd.body.size() - 1);
            }
        }

        // Boss auto-movement
        if (state.boss.alive && state.tickCount - state.boss.lastMoveTick >= BOSS_MOVE_INTERVAL) {
            moveBoss();
            state.boss.lastMoveTick = state.tickCount;
        }

        // Trail expiry
        for (int i = state.bossTrail.size() - 1; i >= 0; i--) {
            if (state.tickCount - state.bossTrail.get(i).createdAtTick >= TRAIL_MAX_AGE) {
                state.bossTrail.remove(i);
            }
        }

        // Boss spawn check (using snake[0]'s score for spawn thresholds)
        if (!state.boss.alive && state.snakes[0].score >= state.nextBossSpawnScore) {
            spawnBoss();
        }

        // Refill food
        int targetFoodCount = getTargetFoodCount(state.snakes[0].score);
        while (state.foods.size() < targetFoodCount) {
            spawnFood();
        }

        // Check game over
        boolean allDead = true;
        for (int i = 0; i < 2; i++) {
            if (state.snakes[i].alive) { allDead = false; break; }
        }
        if (allDead) {
            state.lastScore = state.snakes[0].score;
            if (state.isHost) {
                state.mpWinner = state.snakes[0].score > state.snakes[1].score ? 0 :
                                 state.snakes[1].score > state.snakes[0].score ? 1 : -1;
                state.mpLastScore0 = state.snakes[0].score;
                state.mpLastScore1 = state.snakes[1].score;
                state.currentState = GameState.State.MP_GAME_OVER;
            } else {
                if (!state.devMode)
                    persistence.saveScore(state.snakes[0].score, state.speedLabels[state.speedIndex]);
                state.currentState = GameState.State.GAME_OVER;
            }
        }
    }

    // ----- boss helpers (unchanged logic) -----

    private void spawnBoss() {
        Point pos = findBossPosition();
        if (pos == null) return;
        state.boss.x = pos.x;
        state.boss.y = pos.y;
        state.boss.hp = 5;
        state.boss.alive = true;
        state.boss.lastMoveTick = state.tickCount;
    }

    private void damageBoss() {
        state.boss.hp--;
        if (state.boss.hp <= 0) {
            state.boss.alive = false;
            state.snakes[0].score += BOSS_DEFEAT_SCORE;
            state.score = state.snakes[0].score;
            state.bossGrowthPending += BOSS_DEFEAT_GROWTH;
            state.nextBossSpawnScore += BOSS_SPAWN_INTERVAL;
        } else {
            spawnBossTrail();
            teleportBoss();
        }
    }

    private void teleportBoss() {
        Point pos = findTeleportPosition();
        if (pos != null) { state.boss.x = pos.x; state.boss.y = pos.y; }
    }

    private void spawnBossTrail() {
        ArrayList<Point> tiles = state.boss.getTiles();
        for (int i = tiles.size() - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            Point tmp = tiles.get(i);
            tiles.set(i, tiles.get(j));
            tiles.set(j, tmp);
        }
        int dropped = 0;
        for (Point t : tiles) {
            if (dropped >= TRAIL_CELLS_PER_HIT) break;
            if (!overlapsSnake(t.x, t.y) && !overlapsTrail(t.x, t.y)) {
                state.bossTrail.add(new GameState.BossTrailCell(t.x, t.y, state.tickCount));
                dropped++;
            }
        }
    }

    private void moveBoss() {
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        int start = rand.nextInt(4);
        for (int i = 0; i < 4; i++) {
            int dir = (start + i) % 4;
            int nx = state.boss.x + dx[dir];
            int ny = state.boss.y + dy[dir];
            if (isBossPositionValid(nx, ny)) {
                state.boss.x = nx;
                state.boss.y = ny;
                return;
            }
        }
    }

    private Point findBossPosition() {
        int maxX = state.cols - 2;
        int maxY = state.rows - 2;
        if (maxX < 0 || maxY < 0) return null;
        int attempts = 0;
        while (attempts < 100) {
            int px = rand.nextInt(maxX + 1);
            int py = rand.nextInt(maxY + 1);
            if (isBossPositionValid(px, py)) return new Point(px, py);
            attempts++;
        }
        return null;
    }

    private Point findTeleportPosition() {
        int maxX = state.cols - 2;
        int maxY = state.rows - 2;
        if (maxX < 0 || maxY < 0) return null;
        int attempts = 0;
        while (attempts < 100) {
            int px = rand.nextInt(maxX + 1);
            int py = rand.nextInt(maxY + 1);
            if (px == state.boss.x && py == state.boss.y) continue;
            boolean onSnake = false;
            for (int dy = 0; dy < 2 && !onSnake; dy++) {
                for (int dx = 0; dx < 2 && !onSnake; dx++) {
                    if (overlapsSnake(px + dx, py + dy)) onSnake = true;
                }
            }
            if (!onSnake) return new Point(px, py);
            attempts++;
        }
        return null;
    }

    private boolean isBossPositionValid(int bx, int by) {
        if (bx < 0 || by < 0 || bx + 1 >= state.cols || by + 1 >= state.rows) return false;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int cx = bx + dx;
                int cy = by + dy;
                if (overlapsSnake(cx, cy)) return false;
                if (overlapsFood(cx, cy)) return false;
                if (overlapsTrail(cx, cy)) return false;
            }
        }
        return true;
    }

    private boolean overlapsSnake(int x, int y) {
        for (int si = 0; si < 2; si++) {
            if (!state.snakes[si].alive) continue;
            for (Point p : state.snakes[si].body) {
                if (p.x == x && p.y == y) return true;
            }
        }
        return false;
    }

    private boolean overlapsFood(int x, int y) {
        for (Point f : state.foods) if (f.x == x && f.y == y) return true;
        return false;
    }

    private boolean overlapsTrail(int x, int y) {
        for (GameState.BossTrailCell tc : state.bossTrail) if (tc.x == x && tc.y == y) return true;
        return false;
    }

    // ----- food helpers -----

    private int getTargetFoodCount(int score) {
        for (int fc = 6; fc >= 2; fc--) {
            if (score >= 50 * Math.pow(2, fc - 2)) return fc;
        }
        return 1;
    }

    private void placeFood() {
        state.foods.clear();
        int target = getTargetFoodCount(state.snakes[0].score);
        while (state.foods.size() < target) spawnFood();
    }

    private void spawnFood() {
        int fx, fy;
        boolean coll;
        do {
            fx = rand.nextInt(state.cols);
            fy = rand.nextInt(state.rows);
            coll = overlapsSnake(fx, fy) || overlapsFood(fx, fy) || overlapsTrail(fx, fy) || overlapsBoss(fx, fy);
        } while (coll);
        state.foods.add(new Point(fx, fy));
    }

    private boolean overlapsBoss(int x, int y) {
        if (!state.boss.alive) return false;
        for (Point tile : state.boss.getTiles()) if (tile.x == x && tile.y == y) return true;
        return false;
    }
}
