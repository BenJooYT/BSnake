package com.benjoo.bsnake;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class SnakeEngine {

    private final GameState state;
    private final PersistenceManager persistence;
    private final ChallengeManager challenges;
    private final UpgradeManager upgrades;
    private final Random rand = new Random();
    private SoundEffects sound;
    private static final int BOSS_MOVE_INTERVAL = 3;
    private static final int TRAIL_MAX_AGE = 40;
    private static final int BOSS_SPAWN_INTERVAL = 100;
    private static final int BOSS_DEFEAT_SCORE = 25;
    private static final int BOSS_DEFEAT_GROWTH = 5;
    private static final int BOSS_HIT_SCORE = 5;
    private static final int BOSS_HIT_SHRINK = 3;
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
        this.challenges = new ChallengeManager(state);
        this.upgrades = new UpgradeManager(state);
    }

    void setSoundEffects(SoundEffects sound) {
        this.sound = sound;
        challenges.setSoundEffects(sound);
        upgrades.setSoundEffects(sound);
    }

    // Recomputes the tick delay so speed-affecting upgrades (Heavy Body) apply
    // on top of the chosen speed setting.
    private void recomputeSpeed() {
        state.tickDelay = (long) (state.speedDelays[state.speedIndex] * upgrades.speedMultiplier());
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
        state.boss.storedFruits = 0;
        state.bossTrail.clear();
        state.walls.clear();
        state.wallPreviewPositions.clear();
        state.wallPreviewActive = false;
        state.wallsDying = false;
        state.nextWallTick = 0;
        state.nextBossSpawnScore = BOSS_SPAWN_INTERVAL;
        state.tickCount = 0;
        challenges.reset();
        upgrades.reset();
        if (state.inMp) {
            // Multiplayer is an endless shooter over the shared run — give it the
            // post-boss upgrade economy like arcade single-player.
            challenges.startRun();
            upgrades.startRun();
        } else if (!state.isClassicMode()) {
            challenges.startRun();
            upgrades.startRun();
        }
        state.bossWarningStartMs = 0;
        state.bossSpawnRingStartMs = 0;
        state.shakeUntilMs = 0;
        state.bossFlashTicks = 0;
        state.death.body.clear();
        state.deathPending = false;
        state.particles.clear();
        resetCinematicState();
        placeFood();        for (int i = 0; i < 2; i++) {
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
        state.boss.storedFruits = 0;
        state.bossTrail.clear();
        state.walls.clear();
        state.wallPreviewPositions.clear();
        state.wallPreviewActive = false;
        state.wallsDying = false;
        state.nextWallTick = 0;
        state.nextBossSpawnScore = BOSS_SPAWN_INTERVAL;
        state.tickCount = 0;
        if (state.isClassicMode()) {
            challenges.reset();
            upgrades.reset();
        } else {
            challenges.startRun();
            upgrades.startRun();
        }
        recomputeSpeed();
        state.bossWarningStartMs = 0;
        state.bossSpawnRingStartMs = 0;
        state.shakeUntilMs = 0;
        state.bossFlashTicks = 0;
        state.death.body.clear();
        state.deathPending = false;
        state.particles.clear();
        resetCinematicState();
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

// Ids of the currently-offered upgrade cards, for syncing to the MP client.
    ArrayList<String> upgradeOfferIds() {
        return upgrades.offeredIds();
    }

    // Client-side: adopt the host's card offer verbatim so both players see the
    // same cards and a shared index can be applied by either.
    void applyExternalOffer(ArrayList<String> ids) {
        upgrades.offerByIds(ids);
        state.upgradeSelectedIndex = -1;
    }

    // Tapping a card highlights it (and shows the Choose button). Tapping the
    // same card again drops the selection. No upgrade is applied yet.
    void onUpgradeCardTap(int index) {
        if (index < 0 || index >= state.upgradeOffers.size()) return;
        if (state.upgradeSelectedIndex == index) {
            state.upgradeSelectedIndex = -1;
            return;
        }
        state.upgradeSelectedIndex = index;
        state.upgradeSelectMs = System.currentTimeMillis();
        state.upgradeSelectSeed = rand.nextInt(1000);
        if (sound != null) sound.playUpgradeSelect();
    }

    // Confirms the highlighted card (or -1 if none was selected).
    void onUpgradeChoose() {
        applyUpgrade(state.upgradeSelectedIndex);
    }

    // The Skip button — confirms "no upgrade".
    void onUpgradeSkip() {
        applyUpgrade(-1);
    }

    // Applies the picked card (index) or discard (index -1), recomputes any
    // speed effects, and resumes the run. Used both locally and (on the host)
    // when the remote player confirms a pick.
    void applyUpgrade(int index) {
        boolean picked = upgrades.applyPick(index);
        upgrades.clearOffer();
        recomputeSpeed();
        state.upgradeSelectedIndex = -1;
        state.currentState = state.inMp
                ? GameState.State.MP_PLAYING : GameState.State.PLAYING;
        if (picked) {
            state.scorePulseMs = System.currentTimeMillis();
            state.scorePopMs = state.scorePulseMs;
            state.flashAlpha = 0.6f;
            state.flashColor = android.graphics.Color.argb(160, 90, 230, 90);
            if (sound != null) sound.playUpgradePick();
        } else {
            if (sound != null) sound.playPause();
        }
    }

    // Marks a snake dead, snapshots its body for the dissolve animation, then
    // clears the live body. All death paths go through here so the renderer can
    // play the death effect before the game-over panel appears.
    private void killSnake(GameState.SnakeData sd) {
        sd.alive = false;
        if (!sd.body.isEmpty()) state.recordDeath(sd);
        sd.body.clear();
    }

    // Small visual burst when food is eaten: a ring expanding from the fruit
    // plus a spray of dots. Purely cosmetic.
    private void spawnFoodBurst(int x, int y, boolean heal) {
        int color = heal ? android.graphics.Color.rgb(0, 220, 90)
                         : android.graphics.Color.rgb(255, 70, 50);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            float speed = 0.8f + rand.nextFloat() * 1.6f;
            state.particles.add(new GameState.Particle(
                    x, y,
                    (float) Math.cos(a) * speed, (float) Math.sin(a) * speed,
                    now, 500 + rand.nextInt(300), color,
                    0.12f + rand.nextFloat() * 0.1f, false));
        }
        state.particles.add(new GameState.Particle(
                x, y, 0, 0, now, 450, color, 0.4f, true));
    }

    // Boss-specific particle burst. Big (defeat) versions throw more dots and
    // an extra ring.
    private void spawnBossBurst(int x, int y, int color, boolean big) {
        long now = System.currentTimeMillis();
        int count = big ? 28 : 12;
        for (int i = 0; i < count; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            float speed = (big ? 1.5f : 1.0f) + rand.nextFloat() * (big ? 2.5f : 1.6f);
            state.particles.add(new GameState.Particle(
                    x, y,
                    (float) Math.cos(a) * speed, (float) Math.sin(a) * speed,
                    now, 500 + rand.nextInt(400), color,
                    0.14f + rand.nextFloat() * 0.12f, false));
        }
        state.particles.add(new GameState.Particle(x, y, 0, 0, now, 500, color, 0.4f, true));
        if (big) state.particles.add(new GameState.Particle(x, y, 0, 0, now + 80, 650, color, 0.7f, true));
    }

    private int bossColor() {
        switch (state.boss.type) {
            case WALL_BUILDER: return android.graphics.Color.rgb(0, 140, 255);
            case HEALER: return android.graphics.Color.rgb(0, 200, 90);
            default: return android.graphics.Color.rgb(200, 60, 220);
        }
    }

    void update(boolean predict) {
        state.tickCount++;

        // Prune expired visual particles.
        long nowMs = System.currentTimeMillis();
        for (int i = state.particles.size() - 1; i >= 0; i--) {
            GameState.Particle p = state.particles.get(i);
            if (nowMs - p.startMs >= p.lifeMs) state.particles.remove(i);
        }
        if (state.bossFlashTicks > 0) state.bossFlashTicks--;

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
                if (sd.body.isEmpty()) { killSnake(sd); continue; }
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
                    if (p.x == lnx && p.y == lny) { killSnake(local); break; }
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
                            killSnake(sd);
                        } else if (sd.body.size() > local.body.size()) {
                            killSnake(local);
                        } else {
                            killSnake(local);
                            killSnake(sd);
                        }
                    }
                }
                // On host: remove food at client's current head (food client already ate)
                if (!predict) {
                    Point h = sd.body.get(0);
                    for (int fi = 0; fi < state.foods.size(); fi++) {
                        GameState.Fruit f = state.foods.get(fi);
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
            if (sd.body.isEmpty()) { killSnake(sd); continue; }
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
            if (!sd.alive) { killSnake(sd); continue; }

            // Snake-vs-snake body collision
            int oi = 1 - si;
            GameState.SnakeData other = state.snakes[oi];
            for (Point p : other.body) {
                if (p.x == nx && p.y == ny) {
                    sd.alive = false;
                    break;
                }
            }
            if (!sd.alive) { killSnake(sd); continue; }

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
                        killSnake(other);
                    } else if (other.body.size() > sd.body.size()) {
                        killSnake(sd);
                    } else {
                        killSnake(sd);
                        killSnake(other);
                    }
                    if (!sd.alive) continue;
                }
            }

            // Prepend new head
            sd.body.add(0, new Point(nx, ny));
            if (si == 0) challenges.onPlayerMoved(sd.dirX, sd.dirY);

            // Food eating (only snake[0] gets score for now; snake[1]'s score comes from host)
            boolean ateFood = false;
            int eatNetGrowth = 0; // upgrade-driven growth delta for this piece
            GameState.Fruit eatenFood = null;
            for (GameState.Fruit f : state.foods) {
                if (nx == f.x && ny == f.y) { eatenFood = f; break; }
            }
                if (eatenFood != null) {
                    state.foods.remove(eatenFood);
                    spawnFoodBurst(nx, ny, eatenFood.type == GameState.FruitType.HEAL);
                    if (eatenFood.type == GameState.FruitType.HEAL) {
                        // Green healing fruit: grows the snake, gives no score
                        sd.growthPending += 2;
                    } else {
                        ateFood = true;
                        int[] eat = upgrades.onEatNormal(si);
                        int gained = 1 + eat[0];
                        sd.score += gained;
                        state.score = sd.score;
                        state.scorePulseMs = System.currentTimeMillis();
                        state.scorePopMs = state.scorePulseMs;
                        state.scorePopAmount = gained;
                        if (eat[2] > 0 && si == 0 && state.screenW > 0) {
                            state.challengePopups.add(new GameState.ChallengePopup(
                                    "+5 LUCKY!", System.currentTimeMillis(), 1000,
                                    state.screenW / 2f, state.screenH * 0.50f));
                        }
                        eatNetGrowth = eat[1];
                    }
                    if (si == 0) challenges.onFoodEaten(eatenFood, true);
                    if (sound != null && si == 0) {
                        if (eatenFood.type == GameState.FruitType.HEAL) sound.playHeal();
                        else sound.playCrunch();
                    }
                }

            // Boss collision — head-on damages boss, body kills player
            boolean hitBoss = false;
            boolean bossKillingBlow = false;
            if (state.boss.alive) {
                Point bh = state.boss.body.get(0);
                if (nx == bh.x && ny == bh.y) {
                    hitBoss = true;
                    bossKillingBlow = state.boss.body.size() <= 2;
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
            if (!sd.alive) { killSnake(sd); continue; }

            // Wall collision — touching walls kills player
            for (GameState.WallCell w : state.walls) {
                if (!w.dying && nx == w.x && ny == w.y) {
                    killSnake(sd);
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
                    sd.score += 1 + upgrades.onEatTrail(si);
                    state.score = sd.score;
                    break;
                }
            }

            // Growth / shrink / detach
            if (hitBoss && !bossKillingBlow) {
                // Boss hit shrinks the player by BOSS_HIT_SHRINK (minus any
                // defensive reduction), but never below 1 head + 2 body.
                // Skipped on the killing blow: boss defeat only rewards score/growth.
                int shrink = Math.max(0, BOSS_HIT_SHRINK - upgrades.damageReduction());
                int removed = 0;
                while (removed < shrink && sd.body.size() > 3) {
                    sd.body.remove(sd.body.size() - 1);
                    removed++;
                }
                if (removed > 0) {
                    upgrades.onPlayerTakenDamage();
                    if (si == 0) {
                        challenges.onSegmentLost();
                        if (sound != null) sound.playSegmentLost();
                    }
                }
            } else if (sd.growthPending > 0) {
                sd.growthPending--;
            } else if (state.bossGrowthPending > 0 && si == 0) {
                state.bossGrowthPending--;
            } else if (ateFood) {
                // Normal food already grows +1 (tail is kept). Upgrades may add
                // extra growth or, on a "no growth" interval, remove the tail.
                if (eatNetGrowth > 0) {
                    sd.growthPending += eatNetGrowth;
                } else if (eatNetGrowth < 0) {
                    sd.body.remove(sd.body.size() - 1);
                }
            } else {
                sd.body.remove(sd.body.size() - 1);
            }
        }

        // Boss auto-movement, spawn, and food refill: host only
        if (!predict) {
            challenges.update();
            upgrades.tick();
            if (state.boss.alive) {
                state.boss.moveAccum += 1f;
                float moveInterval = BOSS_MOVE_INTERVAL * upgrades.bossInterval();
                if (state.boss.moveAccum >= moveInterval) {
                    state.boss.moveAccum -= moveInterval;
                    if (state.boss.type == GameState.BossType.WALL_BUILDER) {
                        moveWallBuilder();
                    } else if (state.boss.type == GameState.BossType.HEALER) {
                        moveHealer();
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
                        GameState.Fruit f = state.foods.get(i);
                        if (f.x == head.x && f.y == head.y) {
                            state.foods.remove(i);
                            if (state.boss.type == GameState.BossType.HEALER
                                    && f.type == GameState.FruitType.NORMAL) {
                                // HEALER stores normal fruit, reducing the on-board
                                // food cap until released as green healing fruit on
                                // damage — but still grows one like the other bosses.
                                if (state.boss.storedFruits < state.boss.healFruitCap) {
                                    state.boss.storedFruits++;
                                }
                                state.boss.growthPending++;
                            } else if (f.type == GameState.FruitType.HEAL) {
                                // Green healing fruit grows the boss too
                                state.boss.growthPending += 2;
                            } else {
                                state.boss.growthPending++;
                            }
                            challenges.onBossAteFood();
                            break;
                        }
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

            // Wall capture: any player snake that fully surrounds a connected
            // wall group destroys it. Runs after player movement and wall
            // creation so the freshest layout is evaluated.
            checkWallCaptures();

            // Boss spawn check (uses sum score in multiplayer). A red telegraph
            // plays first, then the boss actually spawns after BOSS_WARNING_MS.
            int progressionScore = state.snakes[0].score + state.snakes[1].score;
            if (!state.isClassicMode() && !state.boss.alive && progressionScore >= state.nextBossSpawnScore) {
                if (state.bossWarningStartMs == 0) {
                    state.bossWarningStartMs = System.currentTimeMillis();
                    if (sound != null) sound.playBossWarning();
                }
            }
            if (state.bossWarningStartMs > 0
                    && System.currentTimeMillis() - state.bossWarningStartMs >= GameState.BOSS_WARNING_MS) {
                state.bossWarningStartMs = 0;
                spawnBoss();
            }

            // Refill food
            int targetFoodCount = getTargetFoodCount(progressionScore);
            refillFood(targetFoodCount);
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
            if (allDead && !state.deathPending) {
                // Start the death dissolve; GameView switches to the game-over
                // panel once DEATH_ANIM_MS has elapsed.
                state.deathPending = true;
                challenges.onPlayerDied();
                if (sound != null) sound.playDeath();
                state.lastScore = state.snakes[0].score;
                state.mpLastScore0 = state.snakes[0].score;
                state.mpLastScore1 = state.snakes[1].score;
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
                challenges.onBossSpawned(state.boss.type, state.snakes[0].body.size());
                bossSpawnedEffects();
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
                challenges.onBossSpawned(state.boss.type, state.snakes[0].body.size());
                bossSpawnedEffects();
                return;
            }
        }
    }

    // Flash/ring/shake when the boss materializes.
    private void bossSpawnedEffects() {
        state.boss.maxSegments = state.boss.body.size();
        state.boss.moveAccum = 0;
        upgrades.onBossSpawned();
        state.bossSpawnRingStartMs = System.currentTimeMillis();
        state.bossFlashTicks = 8;
        startBossShake();
        if (sound != null) sound.playBossSpawn();
    }

    private void startBossShake() {
        state.shakeMagnitude = 14f;
        state.shakeUntilMs = System.currentTimeMillis() + 280;
    }

    private void resetCinematicState() {
        state.cinematicStartMs = 0;
        state.cinematicBossBody.clear();
        state.cinematicExplosionTriggered = false;
        state.cinematicCameraZoom = 1f;
        state.cinematicCameraStartX = 0;
        state.cinematicCameraStartY = 0;
        state.cinematicShockwaveAt = 0;
        state.bossCinematicSynced = false;
    }

    // Spawns the cinematic explosion particle burst. Called once when the
    // explosion phase of the death sequence begins. Every visible boss segment
    // shatters into particles.
    void triggerBossDeathExplosion() {
        int color = state.cinematicBossColor;
        long now = System.currentTimeMillis();
        state.cinematicShockwaveAt = now;

        // Explode every boss body segment into particles
        for (int si = 0; si < state.cinematicBossBody.size(); si++) {
            Point seg = state.cinematicBossBody.get(si);
            float sx = seg.x;
            float sy = seg.y;
            boolean isHead = (si == 0);
            // Head gets more + larger fragments
            int perSeg = isHead ? 14 : 8;
            for (int i = 0; i < perSeg; i++) {
                double a = rand.nextDouble() * Math.PI * 2;
                float speed = (isHead ? 2.0f : 1.2f) + rand.nextFloat() * (isHead ? 4.5f : 3.0f);
                float size;
                int sizeRoll = rand.nextInt(100);
                if (sizeRoll < 15) {
                    size = 0.30f + rand.nextFloat() * 0.30f;  // large chunks
                } else if (sizeRoll < 45) {
                    size = 0.18f + rand.nextFloat() * 0.15f;  // medium fragments
                } else {
                    size = 0.07f + rand.nextFloat() * 0.10f;  // tiny debris
                }
                // Larger chunks take longer to fade and slow down more gradually
                long life = (long)(500 + size * 1200 + rand.nextInt(400));
                float rotSpeed = (float)((rand.nextDouble() - 0.5) * 12.0);
                float ox = (float)(Math.cos(a) * 0.2f * rand.nextFloat());
                float oy = (float)(Math.sin(a) * 0.2f * rand.nextFloat());
                state.particles.add(new GameState.Particle(
                        sx + ox, sy + oy,
                        (float) Math.cos(a) * speed,
                        (float) Math.sin(a) * speed,
                        now, life, color, size, false,
                        rand.nextFloat() * 360f, rotSpeed,
                        size > 0.25f));  // large chunks glow
            }
        }

        // Expanding shockwave rings
        state.particles.add(new GameState.Particle(
                state.cinematicFocusX, state.cinematicFocusY, 0, 0,
                now, 450, color, 0.5f, true));
        state.particles.add(new GameState.Particle(
                state.cinematicFocusX, state.cinematicFocusY, 0, 0,
                now + 60, 550, color, 0.8f, true));
        state.particles.add(new GameState.Particle(
                state.cinematicFocusX, state.cinematicFocusY, 0, 0,
                now + 130, 650, color, 1.1f, true));

        // Lingering glowing embers (few, slow, long life)
        for (int i = 0; i < 12; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            float speed = 0.3f + rand.nextFloat() * 0.8f;
            state.particles.add(new GameState.Particle(
                    state.cinematicFocusX, state.cinematicFocusY,
                    (float) Math.cos(a) * speed,
                    (float) Math.sin(a) * speed,
                    now + 300 + rand.nextInt(200),
                    900 + rand.nextInt(600),
                    color, 0.06f + rand.nextFloat() * 0.08f, false,
                    rand.nextFloat() * 360f, (float)(rand.nextDouble() - 0.5) * 6f,
                    true));  // embers glow
        }

        // Screen shake: strong initial jolt followed by rapid decay
        state.shakeMagnitude = 22f;
        state.shakeUntilMs = now + 550;

        // Sound
        if (sound != null) sound.playBossDefeat();
    }

    // Ends the cinematic sequence and transitions to the upgrade selection
    // screen. Called once the death animation has fully played out.
    void finishBossDefeatTransition() {
        // On the MP client the transition is commanded by the host: it neither
        // rolls its own offer nor changes state here — it waits for the host's
        // bossUpgrade message to move off the cinematic.
        if (state.inMp && !state.isHost) return;
        if (upgrades.isActive()) {
            upgrades.offer();
            boolean hasEpic = false;
            for (GameState.UpgradeCard c : state.upgradeOffers) {
                if (c.rarity == GameState.UpgradeRarity.EPIC) hasEpic = true;
            }
            if (hasEpic) {
                if (sound != null) sound.playUpgradeEpic();
                state.flashAlpha = 1f;
                state.flashColor = android.graphics.Color.argb(200, 190, 120, 255);
            } else {
                if (sound != null) sound.playUpgrade();
            }
            state.currentState = GameState.State.BOSS_UPGRADE;
        } else {
            // If upgrades aren't active (e.g. classic mode), just go back to playing
            state.currentState = state.inMp
                    ? GameState.State.MP_PLAYING : GameState.State.PLAYING;
        }
        resetCinematicState();
    }

    private void moveBoss() {
        Point head = state.boss.body.get(0);
        Point target = getBossTarget(head);
        if (target != null) { state.bossTargetX = target.x; state.bossTargetY = target.y; }
        moveBossWithAI(head, target);
    }

    private void moveWallBuilder() {
        Point head = state.boss.body.get(0);
        Point target = getBossTarget(head);
        if (target != null) { state.bossTargetX = target.x; state.bossTargetY = target.y; }
        moveBossWithAI(head, target);
    }

    // HEALER AI: nearest reachable green healing fruit always wins; otherwise it
    // behaves like the wall builder (chase food/players) to stock up on stores.
    private void moveHealer() {
        Point head = state.boss.body.get(0);
        Point target = getBossTarget(head);
        if (target != null) { state.bossTargetX = target.x; state.bossTargetY = target.y; }
        moveBossWithAI(head, target);
    }

    // Type-aware boss objective. For the HEALER, a reachable green fruit
    // overrides every other objective.
    private Point getBossTarget(Point head) {
        if (state.boss.type == GameState.BossType.HEALER) {
            GameState.Fruit green = findNearestReachableGreen(head.x, head.y);
            if (green != null) return new Point(green.x, green.y);
            return findBestTarget(head.x, head.y);
        }
        if (state.boss.type == GameState.BossType.WALL_BUILDER) {
            return findBestTarget(head.x, head.y);
        }
        return findNearestFood(head.x, head.y);
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
        if (state.boss.body.size() > state.boss.maxSegments) state.boss.maxSegments = state.boss.body.size();
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
        Point target = getBossTarget(head);
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
        state.bossFlashTicks = 4;
        Point head = state.boss.body.isEmpty() ? null : state.boss.body.get(0);
        int color = bossColor();
        if (head != null) spawnBossBurst(head.x, head.y, color, killingBlow);

        // Upgrade hooks: first-hit bonus (Focused Strike) and periodic extra
        // damage (Heavy Hit).
        int[] hit = upgrades.onBossHit();
        if (creditScore && hit[0] > 0) {
            state.snakes[hitterIndex].score += hit[0];
            state.score = state.snakes[hitterIndex].score;
        }

        // Save boss body snapshot before removals, for cinematic rendering
        ArrayList<Point> preDamageBody = new ArrayList<>(state.boss.body);

        // Spawn trail at pre-damage body positions before removing segments
        spawnBossTrailAtBody();

        while (removed < 2 + hit[1] && state.boss.body.size() > 0) {
            state.boss.body.remove(state.boss.body.size() - 1);
            removed++;
        }

        if (state.boss.body.isEmpty()) {
            state.boss.alive = false;
            state.bossTargetX = -1;
            state.bossTargetY = -1;
            // Stored fruit is lost when the HEALER is destroyed, so the food cap
            // recovers for the next boss cycle
            state.boss.storedFruits = 0;
            if (state.boss.type == GameState.BossType.WALL_BUILDER) {
                startWallDeathAnimation();
            }
            if (creditScore) {
                state.snakes[hitterIndex].score += BOSS_DEFEAT_SCORE;
                state.score = state.snakes[hitterIndex].score;
            }
            // Upgrade rewards: bonus score (Boss Hunter) + extra growth
            // (Boss Bounty, Quick Recovery) ride on the same defeat.
            int[] reward = upgrades.onBossDefeat();
            if (creditScore && reward[0] > 0) {
                state.snakes[hitterIndex].score += reward[0];
                state.score = state.snakes[hitterIndex].score;
            }
            state.bossGrowthPending += BOSS_DEFEAT_GROWTH + reward[1];
            state.nextBossSpawnScore += BOSS_SPAWN_INTERVAL;
            challenges.onBossDefeated(state.boss.type, state.snakes[hitterIndex].body.size());
            // BOSS DEFEATED popup
            if (state.screenW > 0) {
                state.challengePopups.add(new GameState.ChallengePopup(
                        "BOSS DEFEATED +" + BOSS_DEFEAT_SCORE, System.currentTimeMillis(),
                        2200, state.screenW / 2f, state.screenH * 0.35f));
            }
            // Start the cinematic death sequence instead of transitioning
            // immediately to the upgrade screen.
            if (head != null) {
                state.cinematicBossBody.clear();
                state.cinematicBossBody.addAll(preDamageBody);
                state.cinematicFocusX = head.x;
                state.cinematicFocusY = head.y;
                state.cinematicBossColor = color;
                state.cinematicStartMs = System.currentTimeMillis();
                state.cinematicExplosionTriggered = false;
                state.cinematicCameraZoom = 1f;
                // Save camera start position (player's head) for smooth pan to boss
                GameState.SnakeData sd = state.snakes[hitterIndex];
                if (sd != null && !sd.body.isEmpty()) {
                    Point playerHead = sd.body.get(0);
                    state.cinematicCameraStartX = playerHead.x;
                    state.cinematicCameraStartY = playerHead.y;
                } else {
                    state.cinematicCameraStartX = state.cameraX;
                    state.cinematicCameraStartY = state.cameraY;
                }
                // Initial shake for the hit stop
                state.shakeMagnitude = 14f;
                state.shakeUntilMs = System.currentTimeMillis() + 200;
                // Small flash on the killing blow
                state.flashAlpha = 0.5f;
                state.flashColor = android.graphics.Color.argb(180, 255, 255, 255);
                state.currentState = GameState.State.BOSS_DEATH_CINEMATIC;
            } else {
                // Fallback: no head position, skip cinematic
                if (upgrades.isActive()) {
                    finishBossDefeatTransition();
                }
            }
        } else {
            teleportBoss();
            // HEALER releases its stored fruit as green healing fruit after damage
            if (state.boss.type == GameState.BossType.HEALER) {
                spawnHealerFruits();
            }
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
        return findNearestFood(bx, by, null);
    }

    private Point findNearestFood(int bx, int by, GameState.FruitType type) {
        Point nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (GameState.Fruit f : state.foods) {
            if (type != null && f.type != type) continue;
            int dx = Math.abs(f.x - bx);
            int dy = Math.abs(f.y - by);
            if (dx > state.cols / 2) dx = state.cols - dx;
            if (dy > state.rows / 2) dy = state.rows - dy;
            int dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                nearest = new Point(f.x, f.y);
            }
        }
        return nearest;
    }

    // BFS from the boss head over cells the boss could actually move through,
    // returning the nearest green healing fruit (or null if none is reachable).
    private GameState.Fruit findNearestReachableGreen(int bx, int by) {
        boolean anyGreen = false;
        for (GameState.Fruit f : state.foods) {
            if (f.type == GameState.FruitType.HEAL) { anyGreen = true; break; }
        }
        if (!anyGreen) return null;
        boolean[] visited = new boolean[state.cols * state.rows];
        int[] qx = new int[state.cols * state.rows];
        int[] qy = new int[state.cols * state.rows];
        int qh = 0, qt = 0;
        qx[qt] = bx;
        qy[qt] = by;
        qt++;
        visited[by * state.cols + bx] = true;
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (qh < qt) {
            int cx = qx[qh];
            int cy = qy[qh];
            qh++;
            for (GameState.Fruit f : state.foods) {
                if (f.type == GameState.FruitType.HEAL && f.x == cx && f.y == cy) return f;
            }
            for (int d = 0; d < 4; d++) {
                int nx = cx + dx[d];
                int ny = cy + dy[d];
                if (nx < 0) nx = state.cols - 1;
                if (nx >= state.cols) nx = 0;
                if (ny < 0) ny = state.rows - 1;
                if (ny >= state.rows) ny = 0;
                if (visited[ny * state.cols + nx]) continue;
                if (nx == bx && ny == by) continue;
                if (!isBossMoveValid(nx, ny)) continue;
                visited[ny * state.cols + nx] = true;
                qx[qt] = nx;
                qy[qt] = ny;
                qt++;
            }
        }
        return null;
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
        for (GameState.Fruit f : state.foods) if (f.x == x && f.y == y) return true;
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
        refillFood(getTargetFoodCount(state.snakes[0].score));
    }

    // Keeps the board stocked: normal food is limited by the base cap minus any
    // fruit the HEALER is storing and any green healing fruit currently on the
    // board, so the fruit economy stays consistent.
    private void refillFood(int targetFoodCount) {
        int normalCount = 0;
        int greenCount = 0;
        for (GameState.Fruit f : state.foods) {
            if (f.type == GameState.FruitType.HEAL) greenCount++;
            else normalCount++;
        }
        int allowedNormal = targetFoodCount - state.boss.storedFruits - greenCount;
        if (allowedNormal < 0) allowedNormal = 0;
        while (normalCount < allowedNormal) {
            spawnFood(GameState.FruitType.NORMAL);
            normalCount++;
        }
    }

    private void spawnFood(GameState.FruitType type) {
        int fx, fy;
        boolean coll;
        int attempts = 0;
        // Food Sense: biased toward the snake's head, scaling with the card's
        // stack count. Falls back to the uniform random spawn on a miss.
        int sense = upgrades.foodSenseStacks();
        if (type == GameState.FruitType.NORMAL && sense > 0 && rand.nextInt(100) < sense * 20) {
            GameState.SnakeData sd = state.snakes[0];
            if (sd.alive && !sd.body.isEmpty()) {
                Point h = sd.body.get(0);
                for (int tries = 0; tries < 80; tries++) {
                    int r = 2 + rand.nextInt(6);
                    fx = h.x + rand.nextInt(2 * r + 1) - r;
                    fy = h.y + rand.nextInt(2 * r + 1) - r;
                    if (fx < 0) fx += state.cols;
                    if (fx >= state.cols) fx -= state.cols;
                    if (fy < 0) fy += state.rows;
                    if (fy >= state.rows) fy -= state.rows;
                    if (overlapsSnake(fx, fy) || overlapsFood(fx, fy) || overlapsTrail(fx, fy)
                            || overlapsBoss(fx, fy) || overlapsWall(fx, fy)) continue;
                    GameState.Fruit f = new GameState.Fruit(type, fx, fy);
                    state.foods.add(f);
                    challenges.onFoodSpawned(f);
                    return;
                }
            }
        }
        do {
            fx = rand.nextInt(state.cols);
            fy = rand.nextInt(state.rows);
            coll = overlapsSnake(fx, fy) || overlapsFood(fx, fy) || overlapsTrail(fx, fy) || overlapsBoss(fx, fy) || overlapsWall(fx, fy);
            attempts++;
        } while (coll && attempts < 300);
        if (!coll) {
            GameState.Fruit f = new GameState.Fruit(type, fx, fy);
            state.foods.add(f);
            challenges.onFoodSpawned(f);
        }
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

    // ----- HEALER helpers -----

    // Releases stored fruit as green healing fruit after the boss takes damage.
    // Spawned well away from the boss head (the fresh teleport position) and
    // capped so the board never floods with special fruit.
    private void spawnHealerFruits() {
        if (state.boss.storedFruits <= 0) return;
        int greenCount = 0;
        for (GameState.Fruit f : state.foods) {
            if (f.type == GameState.FruitType.HEAL) greenCount++;
        }
        int toSpawn = Math.min(state.boss.storedFruits, state.boss.healFruitCap - greenCount);
        if (toSpawn < 0) toSpawn = 0;
        Point head = state.boss.body.get(0);
        for (int i = 0; i < toSpawn; i++) {
            spawnHealFruit(head.x, head.y);
        }
        state.boss.storedFruits = 0;
    }

    private void spawnHealFruit(int bx, int by) {
        for (int attempts = 0; attempts < 300; attempts++) {
            int fx = rand.nextInt(state.cols);
            int fy = rand.nextInt(state.rows);
            if (overlapsSnake(fx, fy) || overlapsFood(fx, fy) || overlapsTrail(fx, fy)
                    || overlapsBoss(fx, fy) || overlapsWall(fx, fy)) continue;
            int dx = Math.abs(fx - bx);
            int dy = Math.abs(fy - by);
            if (dx > state.cols / 2) dx = state.cols - dx;
            if (dy > state.rows / 2) dy = state.rows - dy;
            // Keep healing fruit clear of the boss head
            if (dx * dx + dy * dy < 256) continue;
            GameState.Fruit healFruit = new GameState.Fruit(GameState.FruitType.HEAL, fx, fy);
            state.foods.add(healFruit);
            challenges.onFoodSpawned(healFruit);
            return;
        }
    }

    // ----- Wall builder methods -----

    private void selectBossType() {
        state.boss.isEvading = false;
        state.boss.evasionCooldown = 0;
        state.boss.hesitationTicks = 0;
        state.boss.storedFruits = 0;
        if (state.devForcedBossType == 1) {
            state.boss.type = GameState.BossType.CHASER;
        } else if (state.devForcedBossType == 2) {
            state.boss.type = GameState.BossType.WALL_BUILDER;
        } else if (state.devForcedBossType == 3) {
            state.boss.type = GameState.BossType.HEALER;
        } else {
            int r = rand.nextInt(100);
            if (r < 30) state.boss.type = GameState.BossType.WALL_BUILDER;
            else if (r < 55) state.boss.type = GameState.BossType.HEALER;
            else state.boss.type = GameState.BossType.CHASER;
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
        // Find nearest food (HEALER hunts normal fruit to stock its stores)
        Point nearestFood = state.boss.type == GameState.BossType.HEALER
                ? findNearestFood(bx, by, GameState.FruitType.NORMAL)
                : findNearestFood(bx, by);
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

    // ----- Wall capture (players destroy fully surrounded wall groups) -----

    // A wall group is captured when every tile of the group is sealed inside a
    // complete snake loop: there is no path from the group to the outside that
    // does not cross a snake body. The world is toroidal, so the check runs on
    // a 3x3 unwrapped copy of the board, letting loops that cross map edges and
    // reconnect still count. Walls themselves are passable for the test (only
    // snake bodies form the barrier), so touching or partial surround never
    // triggers.
    private void checkWallCaptures() {
        int wallCount = 0;
        for (GameState.WallCell w : state.walls) {
            if (!w.dying) wallCount++;
        }
        if (wallCount == 0) return;

        int gw = state.cols * 3;
        int gh = state.rows * 3;

        // Snake bodies are the only barrier. Mark every copy of each body cell.
        boolean[] blocked = new boolean[gw * gh];
        int snakeCells = 0;
        for (int si = 0; si < 2; si++) {
            GameState.SnakeData sd = state.snakes[si];
            if (!sd.alive) continue;
            for (Point p : sd.body) {
                snakeCells++;
                int baseX = p.x;
                int baseY = p.y;
                for (int i = 0; i < 3; i++) {
                    int gx = baseX + i * state.cols;
                    for (int j = 0; j < 3; j++) {
                        blocked[(baseY + j * state.rows) * gw + gx] = true;
                    }
                }
            }
        }
        // A loop needs at least a 3x3 ring of body cells to enclose anything.
        if (snakeCells < 8) return;

        // Flood the "outside" region in from the boundary of the unwrapped
        // board over every non-snake cell (walls are passable here).
        boolean[] reached = new boolean[gw * gh];
        int[] qx = new int[gw * gh];
        int[] qy = new int[gw * gh];
        int qHead = 0, qTail = 0;
        for (int gx = 0; gx < gw; gx++) {
            qTail = pushFloodCell(gx, 0, gw, gh, blocked, reached, qx, qy, qTail);
            qTail = pushFloodCell(gx, gh - 1, gw, gh, blocked, reached, qx, qy, qTail);
        }
        for (int gy = 0; gy < gh; gy++) {
            qTail = pushFloodCell(0, gy, gw, gh, blocked, reached, qx, qy, qTail);
            qTail = pushFloodCell(gw - 1, gy, gw, gh, blocked, reached, qx, qy, qTail);
        }
        while (qHead < qTail) {
            int x = qx[qHead];
            int y = qy[qHead];
            qHead++;
            if (x > 0) qTail = pushFloodCell(x - 1, y, gw, gh, blocked, reached, qx, qy, qTail);
            if (x < gw - 1) qTail = pushFloodCell(x + 1, y, gw, gh, blocked, reached, qx, qy, qTail);
            if (y > 0) qTail = pushFloodCell(x, y - 1, gw, gh, blocked, reached, qx, qy, qTail);
            if (y < gh - 1) qTail = pushFloodCell(x, y + 1, gw, gh, blocked, reached, qx, qy, qTail);
        }

        // Index every live wall by its board position for fast grouping.
        int boardCells = state.cols * state.rows;
        int[] wallIndex = new int[boardCells];
        Arrays.fill(wallIndex, -1);
        for (int i = 0; i < state.walls.size(); i++) {
            GameState.WallCell w = state.walls.get(i);
            if (!w.dying) wallIndex[w.y * state.cols + w.x] = i;
        }

        // Collect 8-connected wall groups; a group is captured only if every
        // member tile is unreachable by the outside flood (its central copy).
        boolean[] groupVisited = new boolean[state.walls.size()];
        int[] stack = new int[state.walls.size()];
        for (int i = 0; i < state.walls.size(); i++) {
            if (state.walls.get(i).dying || groupVisited[i]) continue;
            ArrayList<GameState.WallCell> group = new ArrayList<>();
            int sp = 0;
            stack[sp++] = i;
            groupVisited[i] = true;
            while (sp > 0) {
                GameState.WallCell cw = state.walls.get(stack[--sp]);
                group.add(cw);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cw.x + dx;
                        int ny = cw.y + dy;
                        if (nx < 0) nx += state.cols;
                        if (nx >= state.cols) nx -= state.cols;
                        if (ny < 0) ny += state.rows;
                        if (ny >= state.rows) ny -= state.rows;
                        int gi = wallIndex[ny * state.cols + nx];
                        if (gi >= 0 && !groupVisited[gi]) {
                            groupVisited[gi] = true;
                            stack[sp++] = gi;
                        }
                    }
                }
            }
            boolean captured = true;
            for (GameState.WallCell cw : group) {
                // Central copy (1,1) of the 3x3 unwrapped board.
                if (reached[(cw.y + state.rows) * gw + (cw.x + state.cols)]) {
                    captured = false;
                    break;
                }
            }
            if (captured) {
                destroyWallGroup(group);
            }
        }
    }

    // Returns the next queue tail index. Marks a flood cell reached if it is
    // inside the board, free (not a snake body), and not already reached.
    private int pushFloodCell(int x, int y, int gw, int gh, boolean[] blocked,
                              boolean[] reached, int[] qx, int[] qy, int qTail) {
        int idx = y * gw + x;
        if (blocked[idx] || reached[idx]) return qTail;
        reached[idx] = true;
        qx[qTail] = x;
        qy[qTail] = y;
        return qTail + 1;
    }

    private void destroyWallGroup(ArrayList<GameState.WallCell> group) {
        for (GameState.WallCell w : group) {
            if (w.dying) continue;
            w.dying = true;
            w.deathStartTick = state.tickCount;
        }
        if (sound != null) sound.playWallDestroyed();
        challenges.onWallGroupCaptured(group.size());
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
