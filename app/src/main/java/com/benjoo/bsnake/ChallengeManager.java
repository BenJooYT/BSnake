package com.benjoo.bsnake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// Tracks the 3 active challenge objectives for the current Arcade run. All
// logic is event driven — the engine reports gameplay events (food eaten, boss
// defeated, wall captured, ...) and this class updates progress, completes
// objectives, and hands out rewards. It contains no hardcoded challenge data;
// definitions come from ChallengeDefinitions.
class ChallengeManager {

    // A completed/failed challenge lingers in the HUD this long before it is
    // removed and replaced by a fresh objective.
    private static final long SETTLE_MS = 5000;

    // How long the full challenge list stays open before collapsing to the
    // dot strip when opened automatically (run start / challenge settled).
    private static final long CHALLENGE_HUD_SHOW_MS = 4000;

    private final GameState state;
    private final Map<GameState.Fruit, Long> fruitSpawnTimes = new HashMap<>();
    private final ArrayList<ActiveChallenge> active = new ArrayList<>();
    private final Set<String> usedIds = new HashSet<>();
    private SoundEffects sound;

    private boolean activeRun = false;
    private long lastTickTime = 0;
    private long elapsedMs = 0;
    private int lastScore = -1;
    private int lastLength = -1;

    // Per-boss fight facts used by several boss-related objectives
    private boolean bossActive = false;
    private long bossSpawnTime = 0;
    private int bossMinLength = Integer.MAX_VALUE;
    private boolean bossGreenSpawned = false;
    private boolean bossPlayerAteGreen = false;

    ChallengeManager(GameState state) {
        this.state = state;
    }

    void setSoundEffects(SoundEffects sound) {
        this.sound = sound;
    }

    boolean isActive() {
        return activeRun;
    }

    // ----- run lifecycle -----

    // Called at the start of every Arcade run. Selects 3 random challenges.
    void startRun() {
        active.clear();
        usedIds.clear();
        List<ChallengeDefinition> pool = new ArrayList<>(ChallengeDefinitions.getAll());
        Collections.shuffle(pool, new Random());
        int count = Math.min(3, pool.size());
        for (int i = 0; i < count; i++) {
            ChallengeDefinition def = pool.get(i);
            ActiveChallenge ac = new ActiveChallenge(def);
            if (def.type == ChallengeDefinition.ChallengeType.DIRECTION_LOCK) {
                ac.forbiddenDir = new Random().nextInt(4);
            }
            usedIds.add(def.id);
            active.add(ac);
        }
        state.activeChallenges.clear();
        state.activeChallenges.addAll(active);

        activeRun = true;
        elapsedMs = 0;
        lastTickTime = System.currentTimeMillis();
        lastScore = state.snakes[0].score;
        lastLength = state.snakes[0].body.size();
        bossActive = false;
        fruitSpawnTimes.clear();
        state.challengePopups.clear();
        revealChallengePanel();
    }

    // Opens the challenge HUD and schedules it to collapse back to the dot
    // strip after a few seconds.
    private void revealChallengePanel() {
        state.challengePanelOpen = true;
        state.challengeAutoHideUntil = System.currentTimeMillis() + CHALLENGE_HUD_SHOW_MS;
    }

    // Stops all tracking (new non-Arcade run, returning to menu, etc.)
    void reset() {
        activeRun = false;
        active.clear();
        usedIds.clear();
        state.activeChallenges.clear();
        state.challengePopups.clear();
        state.challengePanelOpen = false;
        state.challengeAutoHideUntil = 0;
        fruitSpawnTimes.clear();
        bossActive = false;
        lastTickTime = 0;
    }

    // ----- per-tick updates (host only) -----

    // Called once per host game tick. Handles elapsed-time bookkeeping, score /
    // length change detection, timer-based objectives, and popup cleanup.
    void update() {
        if (!activeRun) return;

        long now = System.currentTimeMillis();
        long deltaMs = 0;
        if (lastTickTime > 0) deltaMs = now - lastTickTime;
        lastTickTime = now;
        if (deltaMs > 0) elapsedMs += deltaMs;

        int score = state.snakes[0].score;
        if (score != lastScore) {
            lastScore = score;
            onScoreChanged(score);
        }
        int length = state.snakes[0].body.size();
        if (length != lastLength) {
            lastLength = length;
            onLengthChanged(length);
        }

        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            switch (ac.def.type) {
                case SPEEDRUNNER:
                    if (elapsedMs > ac.def.params[0] && ac.progress < ac.def.requiredProgress) {
                        failChallenge(ac);
                    }
                    break;
                case EDGE_WALKER:
                    if (state.snakes[0].body.isEmpty()) break;
                    GameState.SnakeData sd = state.snakes[0];
                    if (isNearBorder(sd.body.get(0).x, sd.body.get(0).y, ac.def.params[0])) {
                        ac.edgeWalkerMs += deltaMs;
                        ac.progress = (int) (ac.edgeWalkerMs / 1000);
                        if (ac.progress >= ac.def.requiredProgress) completeChallenge(ac);
                    }
                    break;
            }
        }

        // Completed/failed challenges linger briefly, then get replaced.
        rotateSettledChallenges();

        if (!state.challengePopups.isEmpty()) {
            long t = System.currentTimeMillis();
            for (int i = state.challengePopups.size() - 1; i >= 0; i--) {
                GameState.ChallengePopup p = state.challengePopups.get(i);
                if (t - p.startMs >= p.durationMs) state.challengePopups.remove(i);
            }
        }
    }

    // ----- gameplay events -----

    void onFoodSpawned(GameState.Fruit fruit) {
        if (!activeRun) return;
        fruitSpawnTimes.put(fruit, System.currentTimeMillis());
        if (bossActive && fruit.type == GameState.FruitType.HEAL) bossGreenSpawned = true;
    }

    void onFoodEaten(GameState.Fruit fruit, boolean byPlayer) {
        if (!activeRun) return;
        Long spawnMs = fruitSpawnTimes.remove(fruit);
        long now = System.currentTimeMillis();
        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            switch (ac.def.type) {
                case PERFECT_TIMING:
                    if (byPlayer && spawnMs != null && (now - spawnMs) <= ac.def.params[0]) {
                        ac.progress++;
                        if (ac.progress >= ac.def.requiredProgress) completeChallenge(ac);
                    }
                    break;
                case FRUIT_COLLECTOR:
                    if (byPlayer && fruit.type == GameState.FruitType.NORMAL) {
                        ac.progress++;
                        if (ac.progress >= ac.def.requiredProgress) completeChallenge(ac);
                    }
                    break;
            }
        }
        if (byPlayer && bossActive && fruit.type == GameState.FruitType.HEAL) {
            bossPlayerAteGreen = true;
        }
    }

    // Marks a challenge as failed and triggers a red screen flash so the
    // player sees the penalty moment.
    private void failChallenge(ActiveChallenge ac) {
        if (ac.failed) return;
        ac.failed = true;
        ac.settledAt = System.currentTimeMillis();
        state.flashAlpha = 1f;
        state.flashColor = android.graphics.Color.argb(140, 255, 50, 40);
        if (sound != null) sound.playChallengeFailed();
        revealChallengePanel();
    }

    void onSegmentLost() {
        if (!activeRun) return;
        for (ActiveChallenge ac : active) {
            if (ac.def.type == ChallengeDefinition.ChallengeType.NO_MISTAKES) {
                ac.lostSegment = true;
                failChallenge(ac);
            }
        }
    }

    void onPlayerMoved(int dirX, int dirY) {
        if (!activeRun) return;
        int dirIdx = dirToIndex(dirX, dirY);
        if (dirIdx < 0) return;
        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            if (ac.def.type == ChallengeDefinition.ChallengeType.DIRECTION_LOCK
                    && dirIdx == ac.forbiddenDir) {
                failChallenge(ac);
            }
        }
    }

    void onBossSpawned(GameState.BossType type, int playerLength) {
        if (!activeRun) return;
        bossActive = true;
        bossSpawnTime = System.currentTimeMillis();
        bossMinLength = playerLength;
        bossGreenSpawned = false;
        bossPlayerAteGreen = false;
    }

    void onBossDefeated(GameState.BossType type, int playerLength) {
        if (!activeRun) return;
        long now = System.currentTimeMillis();
        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            boolean done = false;
            switch (ac.def.type) {
                case MULTI_BOSS:
                case BOSS_RUSH:
                    ac.progress++;
                    done = ac.progress >= ac.def.requiredProgress;
                    break;
                case NO_SAFETY_NET:
                    done = !bossPlayerAteGreen;
                    if (done) ac.progress++;
                    break;
                case BOSS_SLAYER:
                    done = bossMinLength > 3;
                    if (done) ac.progress++;
                    break;
                case SPEED_KILL:
                    done = (now - bossSpawnTime) <= ac.def.params[0];
                    if (done) ac.progress++;
                    break;
                case HEALER_DENIAL:
                    done = type == GameState.BossType.HEALER && !bossGreenSpawned;
                    if (done) ac.progress++;
                    break;
                case MINIMALIST:
                    done = playerLength == 3;
                    if (done) ac.progress++;
                    break;
            }
            if (done) completeChallenge(ac);
        }
        bossActive = false;
    }

    void onBossAteFood() {
        if (!activeRun) return;
        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            if (ac.def.type == ChallengeDefinition.ChallengeType.HUNGRY_BOSS) {
                ac.progress++;
                if (ac.progress >= ac.def.requiredProgress) completeChallenge(ac);
            }
        }
    }

    // The player snake died — consecutive-defeat streaks are broken.
    void onPlayerDied() {
        if (!activeRun) return;
        for (ActiveChallenge ac : active) {
            if (ac.def.type == ChallengeDefinition.ChallengeType.BOSS_RUSH) {
                ac.progress = 0;
            }
        }
    }

    void onWallGroupCaptured(int groupSize) {
        if (!activeRun) return;
        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            switch (ac.def.type) {
                case COUNTER_ATTACK:
                    ac.progress++;
                    if (ac.progress >= ac.def.requiredProgress) completeChallenge(ac);
                    break;
                case TERRITORY_CONTROL:
                    if (groupSize >= ac.def.params[0]) {
                        ac.progress++;
                        if (ac.progress >= ac.def.requiredProgress) completeChallenge(ac);
                    }
                    break;
            }
        }
    }

    // ----- internal helpers -----

    // Removes challenges that have been settled (completed/failed) for more than
    // SETTLE_MS and appends a fresh objective in their place, keeping the run's
    // active list topped up. The remaining quests shift up; the new one goes on
    // the bottom of the HUD list.
    private void rotateSettledChallenges() {
        long now = System.currentTimeMillis();
        for (int i = active.size() - 1; i >= 0; i--) {
            ActiveChallenge ac = active.get(i);
            if (ac.settledAt >= 0 && now - ac.settledAt >= SETTLE_MS) {
                active.remove(i);
                state.activeChallenges.remove(ac);
                ActiveChallenge replacement = pickNewChallenge();
                if (replacement != null) {
                    active.add(replacement);
                    state.activeChallenges.add(replacement);
                }
            }
        }
    }

    // Picks a challenge definition not yet used this run. Returns null when the
    // whole pool has been cycled through.
    private ActiveChallenge pickNewChallenge() {
        List<ChallengeDefinition> pool = new ArrayList<>(ChallengeDefinitions.getAll());
        Collections.shuffle(pool, new Random());
        for (ChallengeDefinition def : pool) {
            if (usedIds.contains(def.id)) continue;
            ActiveChallenge ac = new ActiveChallenge(def);
            if (def.type == ChallengeDefinition.ChallengeType.DIRECTION_LOCK) {
                ac.forbiddenDir = new Random().nextInt(4);
            }
            usedIds.add(def.id);
            return ac;
        }
        return null;
    }

    private void onScoreChanged(int score) {
        for (ActiveChallenge ac : active) {
            if (ac.completed || ac.failed) continue;
            switch (ac.def.type) {
                case SPEEDRUNNER:
                case DIRECTION_LOCK:
                case NO_MISTAKES:
                case HIGH_ROLLER:
                case TINY_SNAKE:
                    ac.progress = Math.min(score, ac.def.requiredProgress);
                    if (score >= ac.def.requiredProgress) completeChallenge(ac);
                    break;
            }
        }
    }

    private void onLengthChanged(int length) {
        for (ActiveChallenge ac : active) {
            if (ac.completed) continue;
            switch (ac.def.type) {
                case LONG_RUN:
                    ac.progress = Math.min(length, ac.def.requiredProgress);
                    if (length >= ac.def.requiredProgress) completeChallenge(ac);
                    break;
                case GIANT_SNAKE:
                    ac.progress = Math.min(length, ac.def.requiredProgress);
                    if (length >= ac.def.requiredProgress) completeChallenge(ac);
                    break;
                case TINY_SNAKE:
                    if (ac.maxLength < length) ac.maxLength = length;
                    if (length > ac.def.params[0]) failChallenge(ac);
                    break;
            }
        }
        if (bossActive && length < bossMinLength) bossMinLength = length;
    }

    private boolean isNearBorder(int x, int y, int dist) {
        return x < dist || x >= state.cols - dist || y < dist || y >= state.rows - dist;
    }

    private int dirToIndex(int dx, int dy) {
        if (dx == 0 && dy == -1) return 0;
        if (dx == 1 && dy == 0) return 1;
        if (dx == 0 && dy == 1) return 2;
        if (dx == -1 && dy == 0) return 3;
        return -1;
    }

    private void completeChallenge(ActiveChallenge ac) {
        if (ac.completed) return;
        ac.completed = true;
        ac.settledAt = System.currentTimeMillis();
        // Reward is added straight to the run score (no separate bonus pool).
        state.snakes[0].score += ac.def.reward;
        state.score = state.snakes[0].score;
        state.scorePulseMs = System.currentTimeMillis();
        if (sound != null) sound.playChallengeComplete();
        addPopup("+" + ac.def.reward);
        // Green flash so the success moment is felt.
        state.flashAlpha = 1f;
        state.flashColor = android.graphics.Color.argb(140, 60, 255, 90);
        revealChallengePanel();
    }

    private void addPopup(String text) {
        if (state.screenW <= 0 || state.screenH <= 0) return;
        state.challengePopups.add(new GameState.ChallengePopup(
                text, System.currentTimeMillis(), 1800,
                state.screenW / 2f, state.screenH * 0.40f));
    }
}
