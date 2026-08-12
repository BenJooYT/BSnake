package com.benjoo.bsnake.openworld;

import java.util.Random;

// ---------------------------------------------------------------------------
// WORLD SEED & DETERMINISTIC GENERATION FOUNDATION
// ---------------------------------------------------------------------------
// Every Open World gets a seed generated at creation and stored in the Open
// World state. The seed is independent of Java's global Random state so later
// procedural-generation systems can derive deterministic content from it.
//
// Determinism contract: the same (seed, world coordinates) must always produce
// the same generated result. Utilities here intentionally avoid any mutable
// global random state.
// ---------------------------------------------------------------------------
public final class WorldSeed {

    private WorldSeed() { }

    // Generates a fresh random seed (uses its own Random, never the global one).
    public static long generate() {
        return new Random().nextLong();
    }

    // Deterministic hash of (seed, x, y) into a 32-bit value. This is the
    // foundational primitive later generators build on: terrain, biomes,
    // points of interest, obstacles, etc. The same seed + coordinates always
    // yields the same result. Uses long arithmetic so the full stored seed
    // (OpenWorldState.seed) feeds every generation decision.
    public static int deterministic(long seed, int x, int y) {
        long h = seed;
        h += x * 0x9E3779B97F4A7C15L;
        h += y * 0xC2B2AE3D27D4EB4FL;
        h += (x + y) * 0x165667B19E3779F9L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (int) h;
    }

    // A per-chunk Random instance that is fully deterministic from the seed and
    // chunk coordinates. Callers can drive local content generation without
    // mutating any shared generator. Passing 0..32 from deterministic() gives a
    // stable distribution per chunk.
    public static Random seededRandom(long seed, int chunkX, int chunkY) {
        long combined = (((long) deterministic(seed, chunkX, chunkY)) << 32)
                ^ (deterministic(seed, chunkX + 1, chunkY + 1) & 0xFFFFFFFFL);
        return new Random(combined);
    }
}

