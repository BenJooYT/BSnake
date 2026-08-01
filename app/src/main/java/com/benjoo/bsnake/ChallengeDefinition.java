package com.benjoo.bsnake;

// Static, immutable information about a single challenge objective. All of the
// actual definitions live in ChallengeDefinitions — editing that data file is
// all that is needed to add or change objectives; the tracking system (see
// ChallengeManager) reads from it without any hardcoded challenge knowledge.
public class ChallengeDefinition {

    // Category / tracking hook for the challenge. Each type is handled by the
    // ChallengeManager when the matching gameplay event fires.
    enum ChallengeType {
        PERFECT_TIMING,     // params: [max age ms]
        COUNTER_ATTACK,
        HUNGRY_BOSS,
        LONG_RUN,
        NO_SAFETY_NET,
        FRUIT_COLLECTOR,
        BOSS_SLAYER,
        SPEEDRUNNER,        // params: [time limit ms]
        EDGE_WALKER,        // params: [border distance]
        SPEED_KILL,         // params: [time limit ms]
        HEALER_DENIAL,
        MINIMALIST,
        DIRECTION_LOCK,     // params: [forbidden direction 0=up 1=right 2=down 3=left]
        NO_MISTAKES,
        HIGH_ROLLER,
        GIANT_SNAKE,
        TERRITORY_CONTROL,  // params: [min group size]
        TINY_SNAKE,         // params: [max length]
        MULTI_BOSS,
        BOSS_RUSH
    }

    public final String id;
    public final String name;
    public final String description;
    public final int reward;
    public final int requiredProgress;
    public final ChallengeType type;
    public final int[] params;

    public ChallengeDefinition(String id, String name, String description, int reward,
                               int requiredProgress, ChallengeType type, int... params) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.reward = reward;
        this.requiredProgress = requiredProgress;
        this.type = type;
        this.params = params;
    }
}
