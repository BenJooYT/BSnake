package com.benjoo.bsnake;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.Random;

// All per-tick game logic: snake movement, collision, food spawning, boss fruit system,
// and score persistence.
public class SnakeEngine {

    private final GameState state;
    private final PersistenceManager persistence;
    private final Random rand = new Random();
    private SoundEffects sound;
    private static final int BOSS_MOVE_INTERVAL = 6;   // ticks between boss auto-moves
    private static final int TRAIL_MAX_AGE = 40;         // ticks before a trail cell vanishes
    private static final int BOSS_SPAWN_INTERVAL = 125;  // score threshold between bosses
    private static final int BOSS_DEFEAT_SCORE = 25;     // score awarded on boss defeat
    private static final int BOSS_DEFEAT_GROWTH = 5;     // growth ticks awarded on boss defeat
    private static final int BOSS_HIT_SCORE = 5;         // score per boss hit
    private static final int BOSS_HIT_SHRINK = 3;        // cells removed per boss hit
    private static final int TRAIL_CELLS_PER_HIT = 3;    // trail cells dropped on boss hit

    public SnakeEngine(GameState state, PersistenceManager persistence) {
        this.state = state;
        this.persistence = persistence;
    }

    void setSoundEffects(SoundEffects sound) {
        this.sound = sound;
    }

    // Reset everything for a new game, including boss state.
    // Score and snake size are independent; dev mode sets the starting score.
    void resetGame() {
        state.snake.clear();
        state.score = state.devMode ? state.devStartScore : 0;
        int startX = state.cols / 2;
        int startY = state.rows / 2;
        for (int i = 0; i < 3; i++) {
            state.snake.add(new Point(startX - Math.min(i, 2), startY));
        }
        state.cameraX = startX;
        state.cameraY = startY;
        state.cameraInitialized = true;
        state.dirX = 1;
        state.dirY = 0;
        state.inputQueue.clear();
        state.boss.alive = false;
        state.bossGrowthPending = 0;
        state.bossTrail.clear();
        state.nextBossSpawnScore = BOSS_SPAWN_INTERVAL;
        state.tickCount = 0;
        placeFood();
        state.prevSnake.clear();
        for (Point p : state.snake) state.prevSnake.add(new Point(p));
    }

    // Advance the simulation by one tick.
    // Order: input -> snapshot -> move head -> self-collision -> food/boss/trail
    //         -> growth/detach -> boss movement -> trail expiry -> boss spawn -> refill food.
    void update() {
        state.tickCount++;

        // Consume one queued direction
        if (!state.inputQueue.isEmpty()) {
            Point nextDir = state.inputQueue.remove(0);
            state.dirX = nextDir.x;
            state.dirY = nextDir.y;
        }

        // Snapshot current snake for interpolation
        state.prevSnake.clear();
        for (Point p : state.snake) state.prevSnake.add(new Point(p));

        // Compute new head position with toroidal wrap
        Point head = state.snake.get(0);
        int nx = head.x + state.dirX;
        int ny = head.y + state.dirY;
        boolean teleported = nx < 0 || nx >= state.cols || ny < 0 || ny >= state.rows;
        if (nx < 0) nx = state.cols - 1;
        if (nx >= state.cols) nx = 0;
        if (ny < 0) ny = state.rows - 1;
        if (ny >= state.rows) ny = 0;
        if (teleported) {
            state.cameraX = nx;
            state.cameraY = ny;
        }

        // Self-collision (skip score save in dev mode)
        for (Point p : state.snake) {
            if (p.x == nx && p.y == ny) {
                state.lastScore = state.score;
                if (!state.devMode) {
                    persistence.saveScore(state.score, state.speedLabels[state.speedIndex]);
                }
                state.currentState = GameState.State.GAME_OVER;
                return;
            }
        }

        // Prepend new head
        state.snake.add(0, new Point(nx, ny));

        // ----- food eating (score + growth) -----
        boolean ateFood = false;
        Point eatenFood = null;
        for (Point f : state.foods) {
            if (nx == f.x && ny == f.y) {
                eatenFood = f;
                break;
            }
        }
        if (eatenFood != null) {
            state.foods.remove(eatenFood);
            ateFood = true;
            state.score++;
            if (sound != null) sound.playCrunch();
        }

        // ----- boss collision (head on any of the 4 boss tiles) -----
        boolean hitBoss = false;
        if (state.boss.alive) {
            for (Point tile : state.boss.getTiles()) {
                if (nx == tile.x && ny == tile.y) {
                    hitBoss = true;
                    break;
                }
            }
            if (hitBoss) {
                state.score += BOSS_HIT_SCORE;
                boolean killingBlow = state.boss.hp <= 1;
                damageBoss();
                if (sound != null) {
                    if (killingBlow) sound.playBossDefeat();
                    else sound.playDamage();
                }
            }
        }

        // ----- trail eating (score only, no growth) -----
        boolean ateTrail = false;
        for (int i = state.bossTrail.size() - 1; i >= 0; i--) {
            GameState.BossTrailCell tc = state.bossTrail.get(i);
            if (nx == tc.x && ny == tc.y) {
                state.bossTrail.remove(i);
                ateTrail = true;
                state.score++;
                break;
            }
        }

        // ----- growth / tail detachment / boss-hit shrink -----
        if (hitBoss) {
            int shrink = BOSS_HIT_SHRINK;
            while (shrink > 0 && state.snake.size() > 3) {
                state.snake.remove(state.snake.size() - 1);
                shrink--;
            }
        } else if (state.bossGrowthPending > 0) {
            state.bossGrowthPending--;
            state.prevSnake.add(new Point(state.prevSnake.get(state.prevSnake.size() - 1)));
        } else if (ateFood) {
            state.prevSnake.add(new Point(state.prevSnake.get(state.prevSnake.size() - 1)));
        } else {
            state.snake.remove(state.snake.size() - 1);
        }

        // ----- boss auto-movement (only when alive) -----
        if (state.boss.alive && state.tickCount - state.boss.lastMoveTick >= BOSS_MOVE_INTERVAL) {
            moveBoss();
            state.boss.lastMoveTick = state.tickCount;
        }

        // ----- trail cell expiry -----
        for (int i = state.bossTrail.size() - 1; i >= 0; i--) {
            if (state.tickCount - state.bossTrail.get(i).createdAtTick >= TRAIL_MAX_AGE) {
                state.bossTrail.remove(i);
            }
        }

        // ----- boss spawn check -----
        if (!state.boss.alive && state.score >= state.nextBossSpawnScore) {
            spawnBoss();
        }

        // ----- refill food to target count -----
        int targetFoodCount = getTargetFoodCount(state.score);
        while (state.foods.size() < targetFoodCount) {
            spawnFood();
        }
    }

    // ----- boss helpers -----

    // Spawn the boss at a random valid 2x2 position.  If no position is available
    // the boss is skipped until the next tick.
    private void spawnBoss() {
        Point pos = findBossPosition();
        if (pos == null) return;
        state.boss.x = pos.x;
        state.boss.y = pos.y;
        state.boss.hp = 5;
        state.boss.alive = true;
        state.boss.lastMoveTick = state.tickCount;
    }

    // Reduce boss HP by 1.  On zero → defeat (add score + growth reward, clear boss).
    // Otherwise → teleport away and leave a trail.
    private void damageBoss() {
        state.boss.hp--;
        if (state.boss.hp <= 0) {
            state.boss.alive = false;
            state.score += BOSS_DEFEAT_SCORE;
            state.bossGrowthPending += BOSS_DEFEAT_GROWTH;
            state.nextBossSpawnScore += BOSS_SPAWN_INTERVAL;
        } else {
            spawnBossTrail();
            teleportBoss();
        }
    }

    // Teleport the boss to a new random position that is not on the snake;
    // if none available the boss stays put.
    private void teleportBoss() {
        Point pos = findTeleportPosition();
        if (pos != null) {
            state.boss.x = pos.x;
            state.boss.y = pos.y;
        }
    }

    // Drop TRAIL_CELLS_PER_HIT trail cells at the boss's current position.
    // Each trail cell occupies one of the 2x2 tiles (skipping duplicates).
    private void spawnBossTrail() {
        ArrayList<Point> tiles = state.boss.getTiles();
        // Shuffle the 4 tiles and drop up to TRAIL_CELLS_PER_HIT
        for (int i = tiles.size() - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            Point tmp = tiles.get(i);
            tiles.set(i, tiles.get(j));
            tiles.set(j, tmp);
        }
        int dropped = 0;
        for (Point t : tiles) {
            if (dropped >= TRAIL_CELLS_PER_HIT) break;
            // Don't place a trail cell that overlaps with the snake or existing trail
            if (!overlapsSnake(t.x, t.y) && !overlapsTrail(t.x, t.y)) {
                state.bossTrail.add(new GameState.BossTrailCell(t.x, t.y, state.tickCount));
                dropped++;
            }
        }
    }

    // Try to move the boss 1 cell in a random cardinal direction.
    // The move is valid only if the entire 2x2 block stays in bounds and does not
    // overlap the snake, food, or active trail cells.
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

    // Find any valid 2x2 position for the boss (bounds + no overlap with snake/food/trail).
    // Tries random positions; gives up after MAX_ATTEMPTS and returns null.
    private Point findBossPosition() {
        int maxX = state.cols - 2;
        int maxY = state.rows - 2;
        if (maxX < 0 || maxY < 0) return null;
        int attempts = 0;
        while (attempts < 100) {
            int px = rand.nextInt(maxX + 1);
            int py = rand.nextInt(maxY + 1);
            if (isBossPositionValid(px, py)) {
                return new Point(px, py);
            }
            attempts++;
        }
        return null;
    }

    // Find a random 2x2 position that stays in bounds and does not overlap the
    // snake (may overlap food/trail). Used for teleport-on-hit.
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

    // Check that the 2x2 block at (bx, by) stays in bounds, is not the boss's
    // current position (forces teleport to actually move), and does not overlap
    // the snake, food, or trail cells.
    private boolean isBossPositionValid(int bx, int by) {
        if (bx < 0 || by < 0 || bx + 1 >= state.cols || by + 1 >= state.rows) return false;
        if (state.boss.alive && state.boss.x == bx && state.boss.y == by) return false;
        boolean overlapsSnake = false;
        boolean overlapsFood = false;
        boolean overlapsTrail = false;
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int cx = bx + dx;
                int cy = by + dy;
                if (overlapsSnake(cx, cy)) overlapsSnake = true;
                if (overlapsFood(cx, cy)) overlapsFood = true;
                if (overlapsTrail(cx, cy)) overlapsTrail = true;
            }
        }
        return !overlapsSnake && !overlapsFood && !overlapsTrail;
    }

    private boolean overlapsSnake(int x, int y) {
        for (Point p : state.snake) {
            if (p.x == x && p.y == y) return true;
        }
        return false;
    }

    private boolean overlapsFood(int x, int y) {
        for (Point f : state.foods) {
            if (f.x == x && f.y == y) return true;
        }
        return false;
    }

    private boolean overlapsTrail(int x, int y) {
        for (GameState.BossTrailCell tc : state.bossTrail) {
            if (tc.x == x && tc.y == y) return true;
        }
        return false;
    }

    // ----- food helpers -----

    private int getTargetFoodCount(int score) {
        for (int fc = 6; fc >= 2; fc--) {
            double threshold = 50 * Math.pow(2, fc - 2);
            if (score >= threshold) {
                return fc;
            }
        }
        return 1;
    }

    private void placeFood() {
        state.foods.clear();
        int target = getTargetFoodCount(state.score);
        while (state.foods.size() < target) {
            spawnFood();
        }
    }

    // Place one food item on a random cell not overlapping the snake, existing food,
    // the boss, or trail cells.
    private void spawnFood() {
        int fx, fy;
        boolean coll;
        do {
            fx = rand.nextInt(state.cols);
            fy = rand.nextInt(state.rows);
            coll = overlapsSnake(fx, fy) || overlapsFood(fx, fy)
                    || overlapsTrail(fx, fy) || overlapsBoss(fx, fy);
        } while (coll);
        state.foods.add(new Point(fx, fy));
    }

    private boolean overlapsBoss(int x, int y) {
        if (!state.boss.alive) return false;
        for (Point tile : state.boss.getTiles()) {
            if (tile.x == x && tile.y == y) return true;
        }
        return false;
    }

}
