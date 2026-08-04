package com.benjoo.bsnake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Tracks the post-boss upgrade cards for the current Arcade run. The engine
// feeds it gameplay events (food eaten, boss hit/defeat, damage taken) and it
// hands back the resulting score/growth/speed modifiers. Card *flavor* comes
// from UpgradeDefinitions; all effect logic keys on each card's id.
class UpgradeManager {

    private static final int PATIENT_SECONDS_MS = 10000;

    private final GameState state;
    private final ArrayList<GameState.UpgradeCard> cards = new ArrayList<>();
    private SoundEffects sound;

    private boolean activeRun = false;

    // Per-run counters feeding interval-style cards.
    private int normalFoodsEaten = 0;
    private int perBossHits = 0;
    private long lastDamageTakenMs = 0;

    UpgradeManager(GameState state) {
        this.state = state;
        for (GameState.UpgradeCard def : UpgradeDefinitions.getAll()) {
            cards.add(new GameState.UpgradeCard(
                    def.id, def.name, def.description, def.flavor, def.rarity, def.maxStack));
        }
    }

    void setSoundEffects(SoundEffects sound) {
        this.sound = sound;
    }

    boolean isActive() {
        return activeRun;
    }

    // ----- run lifecycle -----

    // Starts a new Arcade run: clears all stacks and counters.
    void startRun() {
        for (GameState.UpgradeCard c : cards) c.stack = 0;
        normalFoodsEaten = 0;
        perBossHits = 0;
        lastDamageTakenMs = 0;
        activeRun = true;
    }

    // Stops tracking (Classic run / returning to menu / multiplayer).
    void reset() {
        for (GameState.UpgradeCard c : cards) c.stack = 0;
        normalFoodsEaten = 0;
        perBossHits = 0;
        lastDamageTakenMs = 0;
        activeRun = false;
        state.upgradeOffers.clear();
        state.upgradeOpenAt = 0;
        state.upgradeSelectedIndex = -1;
    }

    int stackOf(String id) {
        GameState.UpgradeCard c = findCard(id);
        return c == null ? 0 : c.stack;
    }

    private GameState.UpgradeCard findCard(String id) {
        for (GameState.UpgradeCard c : cards) if (c.id.equals(id)) return c;
        return null;
    }

    // ----- offer / pick -----

    // Builds the selection of up to 3 still-eligible (stack < max) unique
    // cards. The Discard option is always rendered separately by the UI.
    void offer() {
        ArrayList<GameState.UpgradeCard> pool = new ArrayList<>();
        for (GameState.UpgradeCard c : cards) {
            if (c.stack < c.maxStack) pool.add(c);
        }
        Collections.shuffle(pool);
        state.upgradeOffers.clear();
        int count = Math.min(3, pool.size());
        for (int i = 0; i < count; i++) state.upgradeOffers.add(pool.get(i));
        state.upgradeOpenAt = System.currentTimeMillis();
    }

    // Applies the picked card stack (0..2). Returns true if an upgrade applied.
    boolean applyPick(int index) {
        if (index < 0 || index >= state.upgradeOffers.size()) return false;
        GameState.UpgradeCard c = state.upgradeOffers.get(index);
        if (c.stack >= c.maxStack) return false;
        c.stack++;
        return true;
    }

    void clearOffer() {
        state.upgradeOffers.clear();
        state.upgradeOpenAt = 0;
        state.upgradeSelectedIndex = -1;
    }

    // ----- gameplay hooks -----

    // Normal food eaten. Returns { bonusScore, netGrowth, luckyPop } where:
    //   bonusScore  extra score from upgrades this piece
    //   netGrowth   +k = grow EXTRA k, -1 = no growth, 0 = normal +1
    //   luckyPop    5 if Lucky Fruit triggered this piece, 0 otherwise
    int[] onEatNormal(int snakeIndex) {
        GameState.UpgradeCard rich = findCard("rich_food");
        GameState.UpgradeCard greedy = findCard("greedy");
        GameState.UpgradeCard big = findCard("big_bite");
        GameState.UpgradeCard lucky = findCard("lucky_fruit");
        GameState.UpgradeCard light = findCard("light_appetite");
        GameState.UpgradeCard small = findCard("small_appetite");
        GameState.UpgradeCard efficient = findCard("efficient_growth");

        int bonus = rich == null ? 0 : rich.stack;
        int growth = (big == null ? 0 : big.stack) + (greedy == null ? 0 : greedy.stack);
        if (greedy != null) bonus += 2 * greedy.stack;

        normalFoodsEaten++;
        int n = normalFoodsEaten;

        int luckyPop = 0;
        if (lucky != null && lucky.stack > 0 && n % luckyInterval(lucky.stack) == 0) {
            luckyPop = 5;
            bonus += luckyPop;
        }

        // "No growth" variants override growth entirely on their interval.
        boolean noGrow = false;
        if (light != null && light.stack > 0 && n % lightInterval(light.stack) == 0) noGrow = true;
        if (small != null && small.stack > 0 && n % 5 == 0) noGrow = true;
        if (efficient != null && efficient.stack > 0 && n % 8 == 0) noGrow = true;

        return new int[]{ bonus, noGrow ? -1 : growth, luckyPop };
    }

    // Trail fruit eaten: bonus score.
    int onEatTrail(int snakeIndex) {
        GameState.UpgradeCard c = findCard("trail_hunter");
        return c == null ? 0 : c.stack;
    }

    // A boss was hit/damaged (any player or the boss running into the snake).
    // Returns { focusBonusScore, extraDamageSegments }. Tracks per-boss hit count
    // for the first-hit (Focused Strike) and periodic (Heavy Hit) effects.
    int[] onBossHit() {
        perBossHits++;
        int extraDamage = 0;
        int focus = 0;
        GameState.UpgradeCard focused = findCard("focused_strike");
        if (focused != null && focused.stack > 0 && perBossHits == 1) {
            focus = 2 * focused.stack;
        }
        GameState.UpgradeCard heavy = findCard("heavy_hit");
        if (heavy != null && heavy.stack > 0 && perBossHits % heavyInterval(heavy.stack) == 0) {
            extraDamage = 1;
        }
        return new int[]{ focus, extraDamage };
    }

    // Boss defeated. Returns { bonusScore, extraGrowth } — extraGrowth is added
    // to the boss defeat growth pool (applies over the next few ticks).
    int[] onBossDefeat() {
        GameState.UpgradeCard hunter = findCard("boss_hunter");
        GameState.UpgradeCard bounty = findCard("boss_bounty");
        GameState.UpgradeCard recovery = findCard("quick_recovery");
        int bonus = hunter == null ? 0 : hunter.stack * 5;
        int growth = (bounty == null ? 0 : bounty.stack) + (recovery != null && recovery.stack > 0 ? 2 : 0);
        return new int[]{ bonus, growth };
    }

    // New boss spawned — resets the per-encounter hit counter.
    void onBossSpawned() {
        perBossHits = 0;
    }

    // Player took boss damage this tick — resets the Patient clock.
    void onPlayerTakenDamage() {
        lastDamageTakenMs = System.currentTimeMillis();
    }

    // How many fewer body segments boss damage removes (Thick Skin / Heavy Body).
    int damageReduction() {
        GameState.UpgradeCard thick = findCard("thick_skin");
        GameState.UpgradeCard heavy = findCard("heavy_body");
        return (thick != null && thick.stack > 0) || (heavy != null && heavy.stack > 0) ? 1 : 0;
    }

    // Boss move-interval multiplier (>1 = slower). Slow Pressure.
    float bossInterval() {
        GameState.UpgradeCard c = findCard("slow_pressure");
        if (c == null || c.stack <= 0) return 1f;
        return 1f + 0.03f * c.stack;
    }

    // Snake tick-delay multiplier (<1 = faster). Heavy Body.
    float speedMultiplier() {
        GameState.UpgradeCard c = findCard("heavy_body");
        if (c == null || c.stack <= 0) return 1f;
        return 1f - 0.03f * c.stack;
    }

    // Food Sense: how strongly food is biased toward the snake.
    int foodSenseStacks() {
        GameState.UpgradeCard c = findCard("food_sense");
        return c == null ? 0 : c.stack;
    }

    private int luckyInterval(int stacks) {
        return Math.max(8, 10 - stacks);
    }

    private int lightInterval(int stacks) {
        return Math.max(2, 5 - stacks);
    }

    private int heavyInterval(int stacks) {
        return Math.max(3, 5 - stacks);
    }

    // Called once per host game tick while playing. Grants the Patient snake
    // reward when enough time has passed without the player taking damage.
    void tick() {
        if (!activeRun) return;
        GameState.UpgradeCard patient = findCard("patient");
        if (patient == null || patient.stack <= 0 || !state.snakes[0].alive) return;
        long now = System.currentTimeMillis();
        if (lastDamageTakenMs <= 0) { lastDamageTakenMs = now; return; }
        if (now - lastDamageTakenMs >= PATIENT_SECONDS_MS) {
            int gain = 2 * patient.stack;
            state.snakes[0].score += gain;
            state.score = state.snakes[0].score;
            lastDamageTakenMs = now;
        }
    }
}