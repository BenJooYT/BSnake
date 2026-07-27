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
    private static final int BOSS_INITIAL_SEGMENTS = 5;

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

            // Head-on collision with boss head
            boolean hitBoss = false;
            if (state.boss.alive) {
                Point bh = state.boss.body.get(0);
                if (nx == bh.x && ny == bh.y) {
                    hitBoss = true;
                }
                if (hitBoss) {
                    sd.score += BOSS_HIT_SCORE;
                    state.score = sd.score;
                    boolean killingBlow = state.boss.body.size() <= 2;
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
            if (state.bossGrowthPending > 0 && si == 0) {
                state.bossGrowthPending--;
            } else if (!ateFood && !ateTrail && !hitBoss) {
                sd.body.remove(sd.body.size() - 1);
            }
        }

        // Boss auto-movement: move toward food
        if (state.boss.alive && state.tickCount - state.boss.lastMoveTick >= BOSS_MOVE_INTERVAL) {
            moveBoss();
            state.boss.lastMoveTick = state.tickCount;

            // After moving, check if boss head overlaps a player snake segment
            Point bh = state.boss.body.get(0);
            boolean bossHitPlayer = false;
            for (int si = 0; si < 2; si++) {
                if (!state.snakes[si].alive) continue;
                for (Point p : state.snakes[si].body) {
                    if (p.x == bh.x && p.y == bh.y) { bossHitPlayer = true; break; }
                }
                if (bossHitPlayer) break;
            }
            if (bossHitPlayer) {
                boolean killingBlow = state.boss.body.size() <= 2;
                damageBoss();
                if (sound != null) {
                    if (killingBlow) sound.playBossDefeat();
                    else sound.playBossDamage();
                }
            }

            // Boss eats food at new head position
            if (state.boss.alive) {
                Point head = state.boss.body.get(0);
                for (int i = state.foods.size() - 1; i >= 0; i--) {
                    if (state.foods.get(i).x == head.x && state.foods.get(i).y == head.y) {
                        state.foods.remove(i);
                        state.boss.growthPending++;
                        break;
                    }
                }
            }
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

    // ----- boss helpers -----

    private void spawnBoss() {
        // Find a valid head position, then extend 4 segments behind
        int[] dirsX = {0, 0, -1, 1};
        int[] dirsY = {-1, 1, 0, 0};
        int startDir = rand.nextInt(4);
        for (int d = 0; d < 4; d++) {
            int dir = (startDir + d) % 4;
            int hx = state.cols / 2 + dirsX[dir] * 6;
            int hy = state.rows / 2 + dirsY[dir] * 6;
            hx = (hx + state.cols) % state.cols;
            hy = (hy + state.rows) % state.rows;
            ArrayList<Point> body = new ArrayList<>();
            boolean valid = true;
            for (int s = 0; s < BOSS_INITIAL_SEGMENTS; s++) {
                int sx = hx - dirsX[dir] * s;
                int sy = hy - dirsY[dir] * s;
                if (sx < 0) sx += state.cols;
                if (sx >= state.cols) sx -= state.cols;
                if (sy < 0) sy += state.rows;
                if (sy >= state.rows) sy -= state.rows;
                if (overlapsSnake(sx, sy)) { valid = false; break; }
                body.add(new Point(sx, sy));
            }
            if (valid && !body.isEmpty()) {
                state.boss.body = body;
                state.boss.dirX = dirsX[dir];
                state.boss.dirY = dirsY[dir];
                state.boss.alive = true;
                state.boss.lastMoveTick = state.tickCount;
                state.boss.growthPending = 0;
                return;
            }
        }
        // Fallback: random single-point spawn
        for (int attempts = 0; attempts < 100; attempts++) {
            int hx = rand.nextInt(state.cols);
            int hy = rand.nextInt(state.rows);
            if (!overlapsSnake(hx, hy)) {
                state.boss.body.clear();
                state.boss.body.add(new Point(hx, hy));
                state.boss.dirX = 0;
                state.boss.dirY = 1;
                state.boss.alive = true;
                state.boss.lastMoveTick = state.tickCount;
                state.boss.growthPending = 0;
                return;
            }
        }
    }

    private void moveBoss() {
        Point head = state.boss.body.get(0);
        Point target = findNearestFood(head.x, head.y);
        if (target == null) return;

        int dx = wrappedDir(head.x, target.x, state.cols);
        int dy = wrappedDir(head.y, target.y, state.rows);

        // Try directions in order of preference toward food
        int[][] pref;
        if (dx == 0 && dy == 0) return;
        if (dx != 0 && dy != 0) {
            if (state.boss.dirX != 0) pref = new int[][]{{dx, 0}, {0, dy}};
            else pref = new int[][]{{0, dy}, {dx, 0}};
        } else if (dx != 0) {
            pref = new int[][]{{dx, 0}, {0, 1}, {0, -1}, {-dx, 0}};
        } else {
            pref = new int[][]{{0, dy}, {1, 0}, {-1, 0}, {0, -dy}};
        }

        for (int[] m : pref) {
            int nx = head.x + m[0];
            int ny = head.y + m[1];
            if (nx < 0) nx = state.cols - 1;
            if (nx >= state.cols) nx = 0;
            if (ny < 0) ny = state.rows - 1;
            if (ny >= state.rows) ny = 0;

            if (isBossMoveValid(nx, ny)) {
                state.boss.body.add(0, new Point(nx, ny));
                state.boss.dirX = m[0];
                state.boss.dirY = m[1];
                if (state.boss.growthPending > 0) {
                    state.boss.growthPending--;
                } else {
                    state.boss.body.remove(state.boss.body.size() - 1);
                }
                return;
            }
        }
    }

    private void damageBoss() {
        int removed = 0;
        while (removed < 2 && state.boss.body.size() > 0) {
            state.boss.body.remove(state.boss.body.size() - 1);
            removed++;
        }

        if (state.boss.body.isEmpty()) {
            state.boss.alive = false;
            state.snakes[0].score += BOSS_DEFEAT_SCORE;
            state.score = state.snakes[0].score;
            state.bossGrowthPending += BOSS_DEFEAT_GROWTH;
            state.nextBossSpawnScore += BOSS_SPAWN_INTERVAL;
        } else {
            spawnBossTrailAtBody();
            teleportBoss();
        }
    }

    private void spawnBossTrailAtBody() {
        for (Point p : state.boss.body) {
            if (!overlapsSnake(p.x, p.y) && !overlapsTrail(p.x, p.y)) {
                state.bossTrail.add(new GameState.BossTrailCell(p.x, p.y, state.tickCount));
            }
        }
    }

    private void teleportBoss() {
        int segs = state.boss.body.size();
        if (segs == 0) return;

        int[] dirsX = {0, 0, -1, 1};
        int[] dirsY = {-1, 1, 0, 0};

        for (int attempts = 0; attempts < 100; attempts++) {
            int hx = rand.nextInt(state.cols);
            int hy = rand.nextInt(state.rows);
            if (overlapsSnake(hx, hy)) continue;

            // Try to place remaining segments in a straight line
            int startDir = rand.nextInt(4);
            for (int d = 0; d < 4; d++) {
                int dir = (startDir + d) % 4;
                ArrayList<Point> newBody = new ArrayList<>();
                boolean valid = true;
                for (int s = 0; s < segs; s++) {
                    int sx = hx - dirsX[dir] * s;
                    int sy = hy - dirsY[dir] * s;
                    if (sx < 0) sx += state.cols;
                    if (sx >= state.cols) sx -= state.cols;
                    if (sy < 0) sy += state.rows;
                    if (sy >= state.rows) sy -= state.rows;
                    if (overlapsSnake(sx, sy)) { valid = false; break; }
                    newBody.add(new Point(sx, sy));
                }
                if (valid && newBody.size() == segs) {
                    state.boss.body = newBody;
                    state.boss.dirX = dirsX[dir];
                    state.boss.dirY = dirsY[dir];
                    return;
                }
            }
        }
        // If can't place, just leave the boss where it is
    }

    private boolean isBossMoveValid(int x, int y) {
        for (int i = 1; i < state.boss.body.size(); i++) {
            Point p = state.boss.body.get(i);
            if (p.x == x && p.y == y) return false;
        }
        return true;
    }

    private Point findNearestFood(int bx, int by) {
        Point nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (Point f : state.foods) {
            int dx = Math.abs(f.x - bx);
            int dy = Math.abs(f.y - by);
            if (dx > state.cols / 2) dx = state.cols - dx;
            if (dy > state.rows / 2) dy = state.rows - dy;
            int dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                nearest = f;
            }
        }
        return nearest;
    }

    private int wrappedDir(int from, int to, int size) {
        int direct = to - from;
        int wrap = direct > 0 ? direct - size : direct + size;
        return Math.abs(wrap) < Math.abs(direct) ? Integer.signum(wrap) : Integer.signum(direct);
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
        for (Point p : state.boss.body) if (p.x == x && p.y == y) return true;
        return false;
    }
}
