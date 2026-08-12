package com.benjoo.bsnake.openworld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ---------------------------------------------------------------------------
// OPEN WORLD CHUNK MANAGER
// ---------------------------------------------------------------------------
// Responsible for managing the set of loaded Open World chunks. Phase 1
// establishes the architecture and basic lifecycle:
//   - loading chunks
//   - generating chunks (foundation hook only)
//   - tracking loaded chunks
//   - unloading distant chunks
//   - looking up chunks from world coordinates
//   - determining which chunks surround the player
//
// Only a configurable local area around the player is ever maintained, so the
// world is never generated wholesale. The exact load radius is a configurable
// constant so later prompts can tune it.
// ---------------------------------------------------------------------------
public class OpenWorldChunkManager {

    // Configurable constants controlling how much of the world is loaded around
    // the player (in chunks). Easy to change later. The loaded world is the set
    // of chunks within LOAD_RADIUS chunks of the player's chunk.
    public static final int LOAD_RADIUS = 3;

    // Distinguishes the three lifecycle states:
    //   ACTIVE  - the chunk the player currently occupies
    //   NEARBY  - loaded and within the surrounding radius
    //   DISTANT - not loaded (outside the radius)
    public enum ChunkState { DISTANT, NEARBY, ACTIVE }

    private final Map<Long, OpenWorldChunk> chunks = new HashMap<>();

    // Deterministic generator for the world's seed. Generation is delegated to
    // it so World Seed + chunk coordinates -> content is centralized here.
    private OpenWorldGenerator generator;

    public OpenWorldChunkManager() { }

    // Binds the seed-bearing generator this world uses for all chunk generation.
    // Call after the world seed is established (create or restore).
    public void setGenerator(OpenWorldGenerator generator) {
        this.generator = generator;
    }

    // Packs a (chunkX, chunkY) pair into a single hash key.
    static long pack(int chunkX, int chunkY) {
        return (((long) chunkX) << 32) ^ (chunkY & 0xFFFFFFFFL);
    }

    // Returns the chunk containing the given world cell, creating it on demand
    // (loading + foundation generation) if it is not already present.
    public OpenWorldChunk getChunk(int worldX, int worldY) {
        int cx = OpenWorldCoords.worldToChunk(worldX);
        int cy = OpenWorldCoords.worldToChunk(worldY);
        long key = pack(cx, cy);
        OpenWorldChunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new OpenWorldChunk(cx, cy);
            generateChunk(chunk);
            chunks.put(key, chunk);
        }
        return chunk;
    }

    // Refreshes the loaded set around the given player world cell: loads any
    // chunks within LOAD_RADIUS that are missing and unloads any that are now
    // too far away. Safe to call every tick (lookups only when unchanged).
    public void update(int playerWorldX, int playerWorldY) {
        int pcx = OpenWorldCoords.worldToChunk(playerWorldX);
        int pcy = OpenWorldCoords.worldToChunk(playerWorldY);

        // Load the surrounding area.
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dy = -LOAD_RADIUS; dy <= LOAD_RADIUS; dy++) {
                int cx = pcx + dx;
                int cy = pcy + dy;
                long key = pack(cx, cy);
                if (!chunks.containsKey(key)) {
                    OpenWorldChunk chunk = new OpenWorldChunk(cx, cy);
                    generateChunk(chunk);
                    chunks.put(key, chunk);
                }
            }
        }

        // Unload chunks that have moved out of range.
        chunks.entrySet().removeIf(entry -> {
            OpenWorldChunk c = entry.getValue();
            int dx = Math.abs(c.chunkX - pcx);
            int dy = Math.abs(c.chunkY - pcy);
            return dx > LOAD_RADIUS || dy > LOAD_RADIUS;
        });
    }

    // Foundation hook for deterministic content generation. Content generation
    // itself is deferred to a later prompt; the chunk is delegated to the bound
    // generator so it is marked generated and the lifecycle is established and
    // testable.
    private void generateChunk(OpenWorldChunk chunk) {
        if (generator != null) {
            generator.generate(chunk);
        } else {
            chunk.generated = true;
        }
    }

    // Returns the lifecycle state of the chunk containing the given world cell,
    // relative to the player's world position:
    //   ACTIVE  - the chunk the player currently occupies
    //   NEARBY  - loaded and within the surrounding load radius
    //   DISTANT - not loaded (outside the radius)
    public ChunkState stateOf(int worldX, int worldY, int playerWorldX, int playerWorldY) {
        int cx = OpenWorldCoords.worldToChunk(worldX);
        int cy = OpenWorldCoords.worldToChunk(worldY);
        if (!chunks.containsKey(pack(cx, cy))) return ChunkState.DISTANT;
        int pcx = OpenWorldCoords.worldToChunk(playerWorldX);
        int pcy = OpenWorldCoords.worldToChunk(playerWorldY);
        if (cx == pcx && cy == pcy) return ChunkState.ACTIVE;
        return ChunkState.NEARBY;
    }

    // Snapshot of all currently loaded chunks, for rendering / iteration.
    public List<OpenWorldChunk> loadedChunks() {
        return new ArrayList<>(chunks.values());
    }

    // Number of loaded chunks (useful for diagnostics / tests).
    public int loadedChunkCount() {
        return chunks.size();
    }

    // Clears all loaded chunks (used on reset / save reload).
    public void clear() {
        chunks.clear();
    }
}

