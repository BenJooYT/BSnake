package com.benjoo.bsnake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.benjoo.bsnake.ChallengeDefinition.ChallengeType;

// ---------------------------------------------------------------------------
// CHALLENGE DEFINITION FILE
// ---------------------------------------------------------------------------
// The single place where all challenge objectives live. The ChallengeManager
// loads this pool and picks 3 random objectives per Arcade run.
//
// To add a new challenge, just add one entry below — no gameplay code changes
// are required. Each entry supplies everything the system needs:
//   id                unique identifier
//   name              short title shown in the HUD
//   description       one line of player-facing instructions
//   reward            points granted on completion
//   requiredProgress  amount needed to complete (score, length, count, seconds)
//   type              which gameplay event tracks progress (ChallengeType)
//   params            optional per-type tuning (see ChallengeType comments)
// ---------------------------------------------------------------------------
public class ChallengeDefinitions {

    private static final List<ChallengeDefinition> ALL = build();

    private ChallengeDefinitions() { }

    public static List<ChallengeDefinition> getAll() {
        return ALL;
    }

    private static List<ChallengeDefinition> build() {
        ArrayList<ChallengeDefinition> list = new ArrayList<>();

        // ---- 10 points ----
        list.add(new ChallengeDefinition(
                "perfect_timing", "Perfect Timing",
                "Collect food within 1 second of spawning",
                10, 1, ChallengeType.PERFECT_TIMING, 1000));

        list.add(new ChallengeDefinition(
                "counter_attack", "Counter Attack",
                "Destroy a Wall Builder wall group by enclosing it",
                10, 1, ChallengeType.COUNTER_ATTACK));

        list.add(new ChallengeDefinition(
                "hungry_boss", "Hungry Boss",
                "Allow a boss to eat 5 food items before defeat",
                10, 5, ChallengeType.HUNGRY_BOSS));

        // ---- 15 points ----
        list.add(new ChallengeDefinition(
                "long_run", "Long Run",
                "Reach 50 snake length",
                15, 50, ChallengeType.LONG_RUN));

        // ---- 25 points ----
        list.add(new ChallengeDefinition(
                "no_safety_net", "No Safety Net",
                "Complete a boss fight without healing",
                25, 1, ChallengeType.NO_SAFETY_NET));

        // ---- 30 points ----
        list.add(new ChallengeDefinition(
                "fruit_collector", "Fruit Collector",
                "Eat 100 normal fruits",
                30, 100, ChallengeType.FRUIT_COLLECTOR));

        list.add(new ChallengeDefinition(
                "boss_slayer", "Boss Slayer",
                "Defeat a boss without going down to minimum (3) length",
                30, 1, ChallengeType.BOSS_SLAYER));

        // ---- 35 points ----
        list.add(new ChallengeDefinition(
                "speedrunner", "Speedrunner",
                "Reach 250 score within 10 minutes",
                35, 250, ChallengeType.SPEEDRUNNER, 600000));

        // ---- 40 points ----
        list.add(new ChallengeDefinition(
                "edge_walker", "Edge Walker",
                "Stay within 3 tiles of the border for 60 seconds",
                40, 60, ChallengeType.EDGE_WALKER, 3));

        list.add(new ChallengeDefinition(
                "speed_kill", "Speed Kill",
                "Defeat a boss within 30 seconds of spawning",
                40, 1, ChallengeType.SPEED_KILL, 30000));

        list.add(new ChallengeDefinition(
                "healer_denial", "Healer Denial",
                "Defeat HEALER without allowing healing fruit to spawn",
                40, 1, ChallengeType.HEALER_DENIAL));

        list.add(new ChallengeDefinition(
                "minimalist", "Minimalist",
                "Defeat a boss with starting (3) snake length",
                40, 1, ChallengeType.MINIMALIST));

        // ---- 50 points ----
        list.add(new ChallengeDefinition(
                "direction_lock", "Direction Lock",
                "Reach 150 score without moving in one randomly selected direction",
                50, 150, ChallengeType.DIRECTION_LOCK));

        list.add(new ChallengeDefinition(
                "no_mistakes", "No Mistakes",
                "Reach 200 score without losing any body segments",
                50, 200, ChallengeType.NO_MISTAKES));

        list.add(new ChallengeDefinition(
                "high_roller", "High Roller",
                "Reach 1000 score",
                50, 1000, ChallengeType.HIGH_ROLLER));

        list.add(new ChallengeDefinition(
                "giant_snake", "Giant Snake",
                "Reach 512 snake length",
                50, 512, ChallengeType.GIANT_SNAKE));

        // ---- 100 points ----
        list.add(new ChallengeDefinition(
                "territory_control", "Territory Control",
                "Capture a Wall Builder wall group containing 10 or more tiles",
                100, 1, ChallengeType.TERRITORY_CONTROL, 10));

        list.add(new ChallengeDefinition(
                "tiny_snake", "Tiny Snake",
                "Reach 500 score while staying under 125 segments",
                100, 500, ChallengeType.TINY_SNAKE, 125));

        // ---- 150 points ----
        list.add(new ChallengeDefinition(
                "multi_boss", "Multi Boss",
                "Defeat 3 bosses in one run",
                150, 3, ChallengeType.MULTI_BOSS));

        // ---- 200 points ----
        list.add(new ChallengeDefinition(
                "boss_rush", "Boss Rush",
                "Defeat 5 bosses consecutively",
                200, 5, ChallengeType.BOSS_RUSH));

        return Collections.unmodifiableList(list);
    }
}
