package com.benjoo.bsnake;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.Random;

public class SnakeEngine {

    private final GameState state;
    private final PersistenceManager persistence;
    private final Random rand = new Random();
    private SoundEffects sound;
    private static final int BOSS_MOVE_INTERVAL = 3;
    private static final int TRAIL_MAX_AGE = 40;
    private static final int BOSS_SPAWN_INTERVAL = 100;
    private static final int BOSS_DEFEAT_SCORE = 25;
    private static final int BOSS_DEFEAT_GROWTH = 5;
    private static final int BOSS_HIT_SCORE = 5;
    private static final int BOSS_INITIAL_SEGMENTS = 5;

    // Wall builder constants
    private static final int WALL_PREVIEW_DURATION = 10;
    private static final int WALL_PLACE_RANGE = 6;
    private static final int WALL_GROW_DURATION = 3;

    // Boss AI constants
    private static final int DANGER_RADIUS = 7;
    private static final int DANGER_RADIUS_SQ = DANGER_RADIUS * DANGER_RADIUS;
    private static final int EVASION_COOLDOWN = 12;
    private static final int WALL_DEATH_DURATION = 15;

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
            state.snakes[i].bodyColor = (i == 0) == state.isHost ? state.bodyColor : state.clientBodyColor;
        }
        state.score = state.devMode ? state.devStartScore : 0;
        state.snakes[0].score = state.score;
        int startX = state.cols / 2;
        int startY = state.rows / 2;
        for (int i = 0; i < 3; i++) {
            state.snakes[0].body.add(new Point(startX, startY - i));
            state.snakes[1].body.add(new Point(startX + 3, startY + i));
        }
        state.cameraX = startX;
        state.cameraY = startY;
        state.cameraInitialized = true;
        state.snakes[0].dirX = 0;
        state.snakes[0].dirY = 1;
        state.snakes[1].dirX = 0;
        state.snakes[1].dirY = -1;
        state.snakes[0].inputQueue.clear();
        state.snakes[1].inputQueue.clear();
        state.boss.alive = false;
        state.bossGrowthPending = 0;
        state.bossTrail.clear();
        state.walls.clear();
        state.wallPreviewPositions.clear();
        state.wallPreviewActive = false;
        state.wallsDying = false;
        state.nextWallTick = 0;
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
        if (state.isClassicMode()) {
            state.cameraX = state.cols / 2f - 0.5f;
            state.cameraY = state.rows / 2f - 0.5f;
        } else {
            state.cameraX = startX;
            state.cameraY = startY;
        }
        state.cameraInitialized = true;
        state.snakes[0].dirX = 1;
        state.snakes[0].dirY = 0;
        state.snakes[0].inputQueue.clear();
        state.boss.alive = false;
        state.bossGrowthPending = 0;
        state.bossTrail.clear();
        state.walls.clear();
        state.wallPreviewPositions.clear();
        state.wallPreviewActive = false;
        state.wallsDying = false;
        state.nextWallTick = 0;
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
        update(false);
    }

    void update(boolean predict) {
        state.tickCount++;

        // Process each alive snake
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive) continue;

            // In multiplayer, snake[1] is owned by the remote client — use its body as-is
            // (no local movement, just collision detection against snake[0])
            boolean isRemote = state.currentState == GameState.State.MP_PLAYING
                    && state.isHost && si == 1;
            // Also on client side in prediction, snake[0] is from host — use as-is
            boolean isHostControlled = state.currentState == GameState.State.MP_PLAYING
                    && !state.isHost && si == 0;

            if (isRemote || isHostControlled) {
                // Save prevBody for interpolation
                sd.prevBody.clear();
                for (Point p : sd.body) sd.prevBody.add(new Point(p));
                // Sanity check
                if (sd.body.isEmpty()) { sd.alive = false; continue; }
                // Collision: other snake (the locally-controlled one) hits this body
                int li = 1 - si;
                GameState.SnakeData local = state.snakes[li];
                if (!local.alive || local.body.isEmpty()) continue;
                Point lh = local.body.get(0);
                int lnx = lh.x + local.dirX;
                int lny = lh.y + local.dirY;
                if (lnx < 0) lnx = state.cols - 1;
                if (lnx >= state.cols) lnx = 0;
                if (lny < 0) lny = state.rows - 1;
                if (lny >= state.rows) lny = 0;
                // Local hits remote body
                for (Point p : sd.body) {
                    if (p.x == lnx && p.y == lny) { local.alive = false; local.body.clear(); break; }
                }
                // Head-on: compare local new head with remote new head
                if (local.alive && !sd.body.isEmpty()) {
                    Point rh = sd.body.get(0);
                    int rnx = rh.x + sd.dirX;
                    int rny = rh.y + sd.dirY;
                    if (rnx < 0) rnx = state.cols - 1;
                    if (rnx >= state.cols) rnx = 0;
                    if (rny < 0) rny = state.rows - 1;
                    if (rny >= state.rows) rny = 0;
                    if (lnx == rnx && lny == rny) {
                        if (local.body.size() > sd.body.size()) {
                            sd.alive = false;
                            sd.body.clear();
                        } else if (sd.body.size() > local.body.size()) {
                            local.alive = false;
                            local.body.clear();
                        } else {
                            local.alive = false;
                            local.body.clear();
                            sd.alive = false;
                            sd.body.clear();
                        }
                    }
                }
                // On host: remove food at client's current head (food client already ate)
                if (!predict) {
                    Point h = sd.body.get(0);
                    for (int fi = 0; fi < state.foods.size(); fi++) {
                        Point f = state.foods.get(fi);
                        if (f.x == h.x && f.y == h.y) { state.foods.remove(fi); break; }
                    }
                }
                continue;
            }

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
            if (sd.body.isEmpty()) { sd.alive = false; continue; }
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
            if (!sd.alive) { sd.body.clear(); continue; }

            // Snake-vs-snake body collision
            int oi = 1 - si;
            GameState.SnakeData other = state.snakes[oi];
            for (Point p : other.body) {
                if (p.x == nx && p.y == ny) {
                    sd.alive = false;
                    break;
                }
            }
            if (!sd.alive) { sd.body.clear(); continue; }

            // Head-on: only if both are alive
            if (other.alive && !other.body.isEmpty()) {
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
                        other.body.clear();
                    } else if (other.body.size() > sd.body.size()) {
                        sd.alive = false;
                        sd.body.clear();
                    } else {
                        sd.alive = false;
                        sd.body.clear();
                        other.alive = false;
                        other.body.clear();
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

            // Boss collision — head-on damages boss, body kills player
            if (state.boss.alive) {
                Point bh = state.boss.body.get(0);
                if (nx == bh.x && ny == bh.y) {
                    if (!predict) {
                        sd.score += BOSS_HIT_SCORE;
                        state.score = sd.score;
                    }
                    damageBoss(si, !predict);
                    if (predict && si == state.playerIndex) {
                        // The client landed the hit locally — ask the host to apply it
                        state.clientBossHit = true;
                    }
                } else {
                    for (int i = 1; i < state.boss.body.size(); i++) {
                        if (nx == state.boss.body.get(i).x && ny == state.boss.body.get(i).y) {
                            sd.alive = false;
                            break;
                        }
                    }
                }
            }
            if (!sd.alive) { sd.body.clear(); continue; }

            // Wall collision — touching walls kills player
            for (GameState.WallCell w : state.walls) {
                if (!w.dying && nx == w.x && ny == w.y) {
                    sd.alive = false;
                    sd.body.clear();
                    break;
                }
            }
            if (!sd.alive) continue;

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
            } else if (!ateFood) {
                sd.body.remove(sd.body.size() - 1);
            }
        }

        // Boss auto-movement, spawn, and food refill: host only
        if (!predict) {
            if (state.boss.alive && state.tickCount - state.boss.lastMoveTick >= BOSS_MOVE_INTERVAL) {
                if (state.boss.type == GameState.BossType.WALL_BUILDER) {
                    moveWallBuilder();
                } else {
                    moveBoss();
                }
                state.boss.lastMoveTick = state.tickCount;

                // After moving, check if boss head overlaps a player snake segment
                Point bh = state.boss.body.get(0);
                int bossHitIdx = -1;
                for (int si = 0; si < 2; si++) {
                    if (!state.snakes[si].alive) continue;
                    for (Point p : state.snakes[si].body) {
                        if (p.x == bh.x && p.y == bh.y) { bossHitIdx = si; break; }
                    }
                    if (bossHitIdx >= 0) break;
                }
                if (bossHitIdx >= 0) {
                    damageBoss(bossHitIdx, true);
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

            // Wall builder: wall placement and preview
            if (state.boss.alive && state.boss.type == GameState.BossType.WALL_BUILDER) {
                if (state.wallPreviewActive && state.tickCount - state.wallPreviewStartTick >= WALL_PREVIEW_DURATION) {
                    placePreviewWall();
                }
                if (!state.wallPreviewActive && state.tickCount >= state.nextWallTick) {
                    tryPlaceWall();
                }
            }

            // Remove fully decayed dying walls
            for (int i = state.walls.size() - 1; i >= 0; i--) {
                GameState.WallCell w = state.walls.get(i);
                if (w.dying && state.tickCount - w.deathStartTick >= WALL_DEATH_DURATION) {
                    state.walls.remove(i);
                }
            }

            // Boss spawn check (uses sum score in multiplayer)
            int progressionScore = state.snakes[0].score + state.snakes[1].score;
            if (!state.isClassicMode() && !state.boss.alive && progressionScore >= state.nextBossSpawnScore) {
                spawnBoss();
            }

            // Refill food
            int targetFoodCount = getTargetFoodCount(progressionScore);
            while (state.foods.size() < targetFoodCount) {
                spawnFood();
            }
        }

        // Trail expiry (both sides) — skip in Classic mode
        if (!state.isClassicMode()) {
            for (int i = state.bossTrail.size() - 1; i >= 0; i--) {
                if (state.tickCount - state.bossTrail.get(i).createdAtTick >= TRAIL_MAX_AGE) {
                    state.bossTrail.remove(i);
                }
            }
        }

        // Check game over (skip during client prediction — host is authoritative)
        if (!predict) {
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
                        persistence.saveScore(state.snakes[0].score, state.speedLabels[state.speedIndex], state.gameMode.ordinal());
                    state.currentState = GameState.State.GAME_OVER;
                }
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
                selectBossType();
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
                selectBossType();
                return;
            }
        }
    }

    private void moveBoss() {
        Point head = state.boss.body.get(0);
        Point target = findNearestFood(head.x, head.y);
        if (target != null) { state.bossTargetX = target.x; state.bossTargetY = target.y; }
        moveBossWithAI(head, target);
    }

    private void moveWallBuilder() {
        Point head = state.boss.body.get(0);
        Point target = findBestTarget(head.x, head.y);
        if (target != null) { state.bossTargetX = target.x; state.bossTargetY = target.y; }
        moveBossWithAI(head, target);
    }

    // ----- Boss AI: evasion, turn limits, hesitation -----

    private void moveBossWithAI(Point head, Point target) {
        if (target == null) return;

        // Update evasion/hesitation state
        updateBossEvasion(head);

        // Determine desired direction
        int desDx, desDy;
        if (state.boss.isEvading) {
            Point player = findNearestPlayer(head.x, head.y);
            if (player != null) {
                int awayX = -wrappedDir(head.x, player.x, state.cols);
                int awayY = -wrappedDir(head.y, player.y, state.rows);
                int taskX = wrappedDir(head.x, target.x, state.cols);
                int taskY = wrappedDir(head.y, target.y, state.rows);
                // Blend: 40% evade, 60% task progress
                desDx = clampDir(awayX * 4 + taskX * 6);
                desDy = clampDir(awayY * 4 + taskY * 6);
            } else {
                desDx = wrappedDir(head.x, target.x, state.cols);
                desDy = wrappedDir(head.y, target.y, state.rows);
            }
        } else {
            desDx = wrappedDir(head.x, target.x, state.cols);
            desDy = wrappedDir(head.y, target.y, state.rows);
        }
        if (desDx == 0 && desDy == 0) return;

        // Hesitation: skip this move tick entirely
        if (state.boss.hesitationTicks > 0) {
            state.boss.hesitationTicks--;
            return;
        }

        // Build candidate directions
        int[][] candidates;
        if (desDx != 0 && desDy != 0) {
            if (state.boss.dirX != 0) candidates = new int[][]{{desDx, 0}, {0, desDy}, {0, -desDy}, {-desDx, 0}};
            else candidates = new int[][]{{0, desDy}, {desDx, 0}, {-desDx, 0}, {0, -desDy}};
        } else if (desDx != 0) {
            candidates = new int[][]{{desDx, 0}, {0, 1}, {0, -1}, {-desDx, 0}};
        } else {
            candidates = new int[][]{{0, desDy}, {1, 0}, {-1, 0}, {0, -desDy}};
        }

        // Score each candidate and pick the best valid one
        boolean imperfect = !state.boss.isEvading && rand.nextInt(100) < 12;
        int bestScore = Integer.MIN_VALUE;
        int bestDx = 0, bestDy = 0;

        for (int[] m : candidates) {
            int nx = head.x + m[0];
            int ny = head.y + m[1];
            if (nx < 0) nx = state.cols - 1;
            if (nx >= state.cols) nx = 0;
            if (ny < 0) ny = state.rows - 1;
            if (ny >= state.rows) ny = 0;
            if (!isBossMoveValid(nx, ny)) continue;

            int score = 100;
            // Reward alignment with desired direction (must outweigh turn penalty)
            int alignment = m[0] * desDx + m[1] * desDy;
            score += alignment * 40;
            // Heavy penalty for 180 reversal
            if (m[0] == -state.boss.dirX && m[1] == -state.boss.dirY) score -= 100;
            // Light turn speed bias: only a gentle tiebreaker
            if (m[0] != state.boss.dirX || m[1] != state.boss.dirY) score -= 10;
            // Prefer to avoid player body (but not hard-blocked)
            if (overlapsPlayerBody(nx, ny)) score -= 60;
            else if (adjacentToPlayerBody(nx, ny)) score -= 30;
            // Random imperfection (outside evasion)
            if (imperfect) score -= rand.nextInt(60);

            if (score > bestScore) {
                bestScore = score;
                bestDx = m[0];
                bestDy = m[1];
            }
        }

        if (bestScore == Integer.MIN_VALUE) {
            // Fallback: no scored direction was valid — brute-force scan all 4
            for (int[] m : candidates) {
                int nx = head.x + m[0];
                int ny = head.y + m[1];
                if (nx < 0) nx = state.cols - 1;
                if (nx >= state.cols) nx = 0;
                if (ny < 0) ny = state.rows - 1;
                if (ny >= state.rows) ny = 0;
                if (isBossMoveValid(nx, ny)) {
                    bestDx = m[0]; bestDy = m[1];
                    bestScore = 0;
                    break;
                }
            }
            if (bestScore == Integer.MIN_VALUE) return;
        }

        // Execute best move
        int nx = head.x + bestDx;
        int ny = head.y + bestDy;
        if (nx < 0) nx = state.cols - 1;
        if (nx >= state.cols) nx = 0;
        if (ny < 0) ny = state.rows - 1;
        if (ny >= state.rows) ny = 0;

        state.boss.body.add(0, new Point(nx, ny));
        state.boss.dirX = bestDx;
        state.boss.dirY = bestDy;
        if (state.boss.growthPending > 0) {
            state.boss.growthPending--;
        } else {
            state.boss.body.remove(state.boss.body.size() - 1);
        }
    }

    private void updateBossEvasion(Point head) {
        if (state.boss.evasionCooldown > 0) state.boss.evasionCooldown--;

        Point player = findNearestPlayer(head.x, head.y);
        if (player == null) {
            state.boss.isEvading = false;
            return;
        }

        int distSq = wrappedDistSq(head.x, head.y, player.x, player.y);

        // Check if task is nearly complete (within 2 cells)
        Point target = state.boss.type == GameState.BossType.WALL_BUILDER
                ? findBestTarget(head.x, head.y) : findNearestFood(head.x, head.y);
        boolean taskNearlyComplete = target != null && wrappedDistSq(head.x, head.y, target.x, target.y) <= 2;

        // Enter evasion if player is within danger radius
        if (distSq < DANGER_RADIUS_SQ && state.boss.evasionCooldown <= 0 && !taskNearlyComplete) {
            state.boss.isEvading = true;
            state.boss.evasionCooldown = EVASION_COOLDOWN;
            // 10% chance to hesitate briefly when evading
            if (rand.nextInt(100) < 10) {
                state.boss.hesitationTicks = 1;
            }
        }

        // Exit evasion when player is far enough away
        if (distSq > DANGER_RADIUS_SQ * 3) {
            state.boss.isEvading = false;
        }
    }

    private Point findNearestPlayer(int bx, int by) {
        Point nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (int si = 0; si < 2; si++) {
            if (!state.snakes[si].alive || state.snakes[si].body.isEmpty()) continue;
            Point head = state.snakes[si].body.get(0);
            int dx = Math.abs(head.x - bx);
            int dy = Math.abs(head.y - by);
            if (dx > state.cols / 2) dx = state.cols - dx;
            if (dy > state.rows / 2) dy = state.rows - dy;
            int dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                nearest = head;
            }
        }
        return nearest;
    }

    private int clampDir(int v) {
        if (v > 0) return 1;
        if (v < 0) return -1;
        return 0;
    }

    private void damageBoss(int hitterIndex, boolean creditScore) {
        int removed = 0;
        boolean killingBlow = state.boss.body.size() <= 2;

        // Spawn trail at pre-damage body positions before removing segments
        spawnBossTrailAtBody();

        while (removed < 2 && state.boss.body.size() > 0) {
            state.boss.body.remove(state.boss.body.size() - 1);
            removed++;
        }

        if (state.boss.body.isEmpty()) {
            state.boss.alive = false;
            state.bossTargetX = -1;
            state.bossTargetY = -1;
            if (state.boss.type == GameState.BossType.WALL_BUILDER) {
                startWallDeathAnimation();
            }
            if (creditScore) {
                state.snakes[hitterIndex].score += BOSS_DEFEAT_SCORE;
                state.score = state.snakes[hitterIndex].score;
            }
            state.bossGrowthPending += BOSS_DEFEAT_GROWTH;
            state.nextBossSpawnScore += BOSS_SPAWN_INTERVAL;
            if (sound != null) sound.playBossDefeat();
        } else {
            teleportBoss();
            if (sound != null) sound.playBossDamage();
        }
    }

    // Called on the host when the client reports it hit the boss head
    void clientHitBoss() {
        if (!state.boss.alive) return;
        state.snakes[1].score += BOSS_HIT_SCORE;
        state.score = state.snakes[1].score;
        damageBoss(1, true);
    }

    private void spawnBossTrailAtBody() {
        for (Point p : state.boss.body) {
            if (!overlapsSnake(p.x, p.y) && !overlapsTrail(p.x, p.y)) {
                state.bossTrail.add(new GameState.BossTrailCell(p.x, p.y, state.tickCount));
            }
        }
    }

    private void teleportBoss() {
        if (state.boss.body.isEmpty()) return;
        // Prefer a spot where neither the head nor any body segment lands inside
        // a player's danger zone (single and multiplayer alike).
        if (placeBossBody(400, true)) return;
        // If no such spot exists, fall back to a random tp that simply avoids
        // ending up inside the player bodies.
        if (placeBossBody(400, false)) return;
        // If can't place, just leave the boss where it is
    }

    // Attempts to place the boss head + straight-line body such that no segment
    // overlaps a player body, and (when avoidDangerZone) no segment sits within a
    // player's danger radius. Returns true if a placement was applied.
    private boolean placeBossBody(int maxAttempts, boolean avoidDangerZone) {
        int segs = state.boss.body.size();
        int[] dirsX = {0, 0, -1, 1};
        int[] dirsY = {-1, 1, 0, 0};

        for (int attempts = 0; attempts < maxAttempts; attempts++) {
            int hx = rand.nextInt(state.cols);
            int hy = rand.nextInt(state.rows);
            if (overlapsSnake(hx, hy)) continue;
            if (avoidDangerZone && inPlayerDangerZone(hx, hy)) continue;

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
                    if (avoidDangerZone && inPlayerDangerZone(sx, sy)) { valid = false; break; }
                    newBody.add(new Point(sx, sy));
                }
                if (valid && newBody.size() == segs) {
                    state.boss.body = newBody;
                    state.boss.dirX = dirsX[dir];
                    state.boss.dirY = dirsY[dir];
                    return true;
                }
            }
        }
        return false;
    }

    // True if the cell lies within DANGER_RADIUS of any alive player's head
    // (toroidal distance, matching the boss evasion logic).
    private boolean inPlayerDangerZone(int x, int y) {
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive || sd.body.isEmpty()) continue;
            Point head = sd.body.get(0);
            int dx = Math.abs(x - head.x);
            int dy = Math.abs(y - head.y);
            if (dx > state.cols / 2) dx = state.cols - dx;
            if (dy > state.rows / 2) dy = state.rows - dy;
            if (dx * dx + dy * dy < DANGER_RADIUS_SQ) return true;
        }
        return false;
    }

    private boolean isBossMoveValid(int x, int y) {
        for (int i = 1; i < state.boss.body.size() - 1; i++) {
            Point p = state.boss.body.get(i);
            if (p.x == x && p.y == y) return false;
        }
        // Wall builder also avoids walls (can walk through them, but shouldn't sit on them)
        if (overlapsWall(x, y)) return false;
        return true;
    }

    private boolean overlapsPlayerBody(int x, int y) {
        return overlapsSnake(x, y);
    }

    private boolean adjacentToPlayerBody(int x, int y) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx < 0) nx = state.cols - 1;
            if (nx >= state.cols) nx = 0;
            if (ny < 0) ny = state.rows - 1;
            if (ny >= state.rows) ny = 0;
            if (overlapsSnake(nx, ny)) return true;
        }
        return false;
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
        int[] thresholds = {825, 550, 375, 175, 50};
        for (int i = 0; i < thresholds.length; i++) {
            if (score >= thresholds[i]) return thresholds.length - i + 1;
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
            coll = overlapsSnake(fx, fy) || overlapsFood(fx, fy) || overlapsTrail(fx, fy) || overlapsBoss(fx, fy) || overlapsWall(fx, fy);
        } while (coll);
        state.foods.add(new Point(fx, fy));
    }

    private boolean overlapsBoss(int x, int y) {
        if (!state.boss.alive) return false;
        for (Point p : state.boss.body) if (p.x == x && p.y == y) return true;
        return false;
    }

    private boolean overlapsWall(int x, int y) {
        for (GameState.WallCell w : state.walls) {
            if (!w.dying && w.x == x && w.y == y) return true;
        }
        return false;
    }

    // ----- Wall builder methods -----

    private void selectBossType() {
        state.boss.isEvading = false;
        state.boss.evasionCooldown = 0;
        state.boss.hesitationTicks = 0;
        if (state.devForcedBossType == 1) {
            state.boss.type = GameState.BossType.CHASER;
        } else if (state.devForcedBossType == 2) {
            state.boss.type = GameState.BossType.WALL_BUILDER;
        } else {
            state.boss.type = rand.nextInt(100) < 40
                    ? GameState.BossType.WALL_BUILDER
                    : GameState.BossType.CHASER;
        }
        if (state.boss.type == GameState.BossType.WALL_BUILDER) {
            initWallDifficulty();
            state.nextWallTick = state.tickCount + state.wallPlaceInterval;
        }
    }

    private void initWallDifficulty() {
        switch (state.speedIndex) {
            case 0:
                state.wallPlaceInterval = 90;
                state.maxWalls = 15;
                break;
            case 1:
                state.wallPlaceInterval = 60;
                state.maxWalls = 22;
                break;
            case 2:
                state.wallPlaceInterval = 40;
                state.maxWalls = 30;
                break;
        }
    }

    private Point findBestTarget(int bx, int by) {
        // Find nearest food
        Point nearestFood = findNearestFood(bx, by);
        // Find nearest alive player head
        Point nearestPlayer = null;
        int bestPlayerDist = Integer.MAX_VALUE;
        for (int si = 0; si < 2; si++) {
            if (!state.snakes[si].alive || state.snakes[si].body.isEmpty()) continue;
            Point head = state.snakes[si].body.get(0);
            int hdx = Math.abs(head.x - bx);
            int hdy = Math.abs(head.y - by);
            if (hdx > state.cols / 2) hdx = state.cols - hdx;
            if (hdy > state.rows / 2) hdy = state.rows - hdy;
            int dist = hdx * hdx + hdy * hdy;
            if (dist < bestPlayerDist) {
                bestPlayerDist = dist;
                nearestPlayer = head;
            }
        }
        // Pick closer target, with slight bias toward player (multiply food dist by 1.3)
        if (nearestFood == null) return nearestPlayer;
        if (nearestPlayer == null) return nearestFood;
        int foodDist = wrappedDistSq(bx, by, nearestFood.x, nearestFood.y);
        int playerDist = wrappedDistSq(bx, by, nearestPlayer.x, nearestPlayer.y);
        return playerDist <= foodDist * 1.3f ? nearestPlayer : nearestFood;
    }

    private int wrappedDistSq(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        if (dx > state.cols / 2) dx = state.cols - dx;
        if (dy > state.rows / 2) dy = state.rows - dy;
        return dx * dx + dy * dy;
    }

    private void tryPlaceWall() {
        if (state.wallPreviewActive) return;

        // Collect valid positions within WALL_PLACE_RANGE of boss body
        ArrayList<Point> candidates = new ArrayList<>();
        int[] dirsX = {0, 0, -1, 1};
        int[] dirsY = {-1, 1, 0, 0};
        for (Point seg : state.boss.body) {
            for (int dx = -WALL_PLACE_RANGE; dx <= WALL_PLACE_RANGE; dx++) {
                for (int dy = -WALL_PLACE_RANGE; dy <= WALL_PLACE_RANGE; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int wx = seg.x + dx;
                    int wy = seg.y + dy;
                    if (wx < 0) wx += state.cols;
                    if (wx >= state.cols) wx -= state.cols;
                    if (wy < 0) wy += state.rows;
                    if (wy >= state.rows) wy -= state.rows;
                    if (!isValidWallPosition(wx, wy)) continue;
                    Point candidate = new Point(wx, wy);
                    if (!containsPoint(candidates, candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        // Score and pick top 3
        int[] topScores = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        Point[] topPoints = {null, null, null};
        for (Point c : candidates) {
            int score = scoreWallPosition(c.x, c.y);
            for (int i = 0; i < 3; i++) {
                if (score > topScores[i]) {
                    // Shift lower scores down
                    for (int j = 2; j > i; j--) {
                        topScores[j] = topScores[j - 1];
                        topPoints[j] = topPoints[j - 1];
                    }
                    topScores[i] = score;
                    topPoints[i] = c;
                    break;
                }
            }
        }

        // Set previews for top positions
        state.wallPreviewPositions.clear();
        for (Point p : topPoints) {
            if (p != null) state.wallPreviewPositions.add(new Point(p));
        }
        if (state.wallPreviewPositions.isEmpty()) return;
        state.wallPreviewStartTick = state.tickCount;
        state.wallPreviewActive = true;
    }

    private void placePreviewWall() {
        if (!state.wallPreviewActive) return;
        state.wallPreviewActive = false;

        for (Point preview : state.wallPreviewPositions) {
            if (!isValidWallPosition(preview.x, preview.y)) continue;
            while (state.walls.size() >= state.maxWalls && state.walls.size() > 0) {
                state.walls.remove(0);
            }
            state.walls.add(new GameState.WallCell(preview.x, preview.y, state.tickCount));
        }
        state.wallPreviewPositions.clear();
        state.nextWallTick = state.tickCount + state.wallPlaceInterval + rand.nextInt(state.wallPlaceInterval / 2);
    }

    private void startWallDeathAnimation() {
        state.wallsDying = true;
        for (GameState.WallCell w : state.walls) {
            w.dying = true;
            w.deathStartTick = state.tickCount;
        }
    }

    private boolean isValidWallPosition(int x, int y) {
        // Must be on empty tile
        if (overlapsSnake(x, y)) return false;
        if (overlapsBoss(x, y)) return false;
        if (overlapsFood(x, y)) return false;
        if (overlapsWall(x, y)) return false;
        if (overlapsTrail(x, y)) return false;
        // Cannot be directly on any active wall preview
        if (state.wallPreviewActive) {
            for (Point p : state.wallPreviewPositions) {
                if (p.x == x && p.y == y) return false;
            }
        }
        return true;
    }

    private int scoreWallPosition(int wx, int wy) {
        int score = 0;
        // +15 if ahead of primary player's direction
        if (aheadOfPlayer(wx, wy, 0)) score += 15;
        // -20 if within 1 cell of world border (avoid edge placement)
        if (wx <= 1 || wx >= state.cols - 2 || wy <= 1 || wy >= state.rows - 2) score -= 20;
        // +12 if near another wall (creating narrow passage)
        if (nearOtherWall(wx, wy)) score += 12;
        // -30 if adjacent to any player head (dangerous — warning will show)
        if (isAdjacentToPlayerHead(wx, wy)) score -= 30;
        // -50 if would trap any player
        if (wouldTrapPlayer(wx, wy)) score -= 50;
        // Small random factor
        score += rand.nextInt(6);
        return score;
    }

    private boolean aheadOfPlayer(int wx, int wy, int playerIdx) {
        GameState.SnakeData sd = state.snakes[playerIdx];
        if (!sd.alive || sd.body.isEmpty()) return false;
        Point head = sd.body.get(0);
        int dx = wrappedDelta(wx - head.x, state.cols);
        int dy = wrappedDelta(wy - head.y, state.rows);
        return dx * sd.dirX + dy * sd.dirY > 0;
    }

    private int wrappedDelta(int delta, int size) {
        while (delta > size / 2f) delta -= size;
        while (delta < -size / 2f) delta += size;
        return delta;
    }

    private boolean nearOtherWall(int x, int y) {
        for (GameState.WallCell w : state.walls) {
            if (w.dying) continue;
            int dx = Math.abs(w.x - x);
            int dy = Math.abs(w.y - y);
            if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) return true;
        }
        return false;
    }

    private boolean isAdjacentToPlayerHead(int wx, int wy) {
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive || sd.body.isEmpty()) continue;
            Point head = sd.body.get(0);
            int dx = Math.abs(wx - head.x);
            int dy = Math.abs(wy - head.y);
            if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) return true;
        }
        return false;
    }

    private boolean wouldTrapPlayer(int wx, int wy) {
        int[] dirsX = {0, 0, -1, 1};
        int[] dirsY = {-1, 1, 0, 0};
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive || sd.body.isEmpty()) continue;
            Point head = sd.body.get(0);
            int escapeCount = 0;
            for (int d = 0; d < 4; d++) {
                int nx = head.x + dirsX[d];
                int ny = head.y + dirsY[d];
                if (nx == wx && ny == wy) continue;
                if (nx < 0) nx += state.cols;
                if (nx >= state.cols) nx -= state.cols;
                if (ny < 0) ny += state.rows;
                if (ny >= state.rows) ny -= state.rows;
                // Can't go directly backwards
                if (dirsX[d] == -sd.dirX && dirsY[d] == -sd.dirY) continue;
                if (!isCellBlocked(nx, ny)) escapeCount++;
            }
            if (escapeCount < 1) return true;
        }
        return false;
    }

    private boolean isCellBlocked(int x, int y) {
        for (int si = 0; si < 2; si++) {
            if (!state.snakes[si].alive) continue;
            for (Point p : state.snakes[si].body) {
                if (p.x == x && p.y == y) return true;
            }
        }
        if (state.boss.alive) {
            for (Point p : state.boss.body) if (p.x == x && p.y == y) return true;
        }
        if (overlapsWall(x, y)) return true;
        if (state.wallPreviewActive) {
            for (Point p : state.wallPreviewPositions) {
                if (p.x == x && p.y == y) return true;
            }
        }
        return false;
    }

    private boolean containsPoint(ArrayList<Point> list, Point p) {
        for (Point q : list) if (q.x == p.x && q.y == p.y) return true;
        return false;
    }
}
