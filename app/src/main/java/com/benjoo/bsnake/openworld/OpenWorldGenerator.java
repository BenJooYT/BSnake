package com.benjoo.bsnake.openworld;

import java.util.Random;

// ---------------------------------------------------------------------------
// OPEN WORLD GENERATOR
// ---------------------------------------------------------------------------
// The foundation for deterministic Open World generation. It owns a single
// world seed and exposes the reusable utilities later generator prompts build
// on. The relationship it establishes is:
//
//     World Seed + World Coordinates (chunk/cell) -> Deterministic Content
//
// Determinism contract: the same seed and coordinates always produce the same
// result, and generation never relies on mutable global random state — every
// random draw comes from a per-chunk Random fully derived from the seed.
//
// The full biome/terrain generator is intentionally NOT implemented here. This
// class only establishes the interface and the generation hook; later prompts
// populate OpenWorldChunk's containers (terrain, obstacles, POIs, food,
// enemies) from this foundation.
// ---------------------------------------------------------------------------
public class OpenWorldGenerator {

    private final long seed;

    public OpenWorldGenerator(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    // Deterministic hash of any (seed, x, y) into a 32-bit value. Reusable for
    // any per-cell / per-coordinate decision. Same inputs -> same output.
    public int hash(int x, int y) {
        return WorldSeed.deterministic(seed, x, y);
    }

    // Deterministic per-chunk pseudo-random source. Reusable for any content
    // that wants a stable distribution within a chunk. Same seed + chunk ->
    // same Random.
    public Random randomForChunk(int chunkX, int chunkY) {
        return WorldSeed.seededRandom(seed, chunkX, chunkY);
    }

    // Generation hook for one chunk. Wires World Seed + chunk coordinates so the
    // foundation is in place and testable. The body deliberately only marks the
    // chunk generated: real terrain/biome/points-of-interest generation is added
    // in later prompts by filling OpenWorldChunk's containers using hash() and
    // randomForChunk() above.
    public void generate(OpenWorldChunk chunk) {
        chunk.generated = true;
    }
}
