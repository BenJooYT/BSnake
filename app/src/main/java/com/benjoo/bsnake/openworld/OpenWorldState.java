package com.benjoo.bsnake.openworld;

import java.util.ArrayList;
import java.util.HashSet;

// ---------------------------------------------------------------------------
// OPEN WORLD STATE
// ---------------------------------------------------------------------------
// The dedicated, persistent model for the Open World game mode. It is kept
// separate from Arcade and Classic state and is designed so later prompts can
// add progression systems (currency, XP, exploration, discovered locations,
// world modifications, NPC/quest state, defeated encounters, ...) without
// redesigning it.
//
// Phase 1 establishes the structure: the fields the foundation needs now
// (seed, player world position, score, lifetime score, snake length) plus
// lightweight containers that hold the *shape* of future progression data.
// The actual gameplay systems that fill these containers come in later prompts.
// ---------------------------------------------------------------------------
public class OpenWorldState {

    // -----------------------------------------------------------------------
    // Core persistent fields (used by the foundation now)
    // -----------------------------------------------------------------------

    // World seed; independent of Java's global random state. Generated at world
    // creation and used by all future procedural-generation systems.
    public long seed;

    // The snake/player's position in world coordinates.
    public int playerX;
    public int playerY;

    // Current run score and the cumulative lifetime score across all runs.
    public int score;
    public int lifetimeScore;

    // True once this Open World has been created (a seed assigned and a valid
    // player position established). Drives whether we start fresh or restore.
    public boolean initialized;

    // The snake's length/state is mirrored from the shared snake model during a
    // run; this records the last length for persistence.
    public int length;

    // -----------------------------------------------------------------------
    // Future persistent progression (structure established now, filled later)
    // -----------------------------------------------------------------------

    // RPG currency. Simple integer accumulator for now.
    public int currency;

    // Experience and level. Level is derived/assigned by the future XP system.
    public int xp;
    public int level;

    // Explored areas. World is chunk-based (see OpenWorldCoords), so we record
    // exploration per chunk to stay compact for very large worlds. Each chunk is
    // stored as a single packed long key (see packChunkKey / unpackChunkKey).
    public final HashSet<Long> exploredChunks = new HashSet<>();

    // Discovered locations (points of interest the player has found).
    public final ArrayList<LocationRecord> discoveredLocations = new ArrayList<>();

    // Persistent world modifications (e.g. terrain the player altered, built,
    // or destroyed). Each modification references a world position.
    public final ArrayList<WorldModification> worldModifications = new ArrayList<>();

    // Persistent NPC / world-state records keyed by a stable actor/object id.
    public final ArrayList<NpcState> npcStates = new ArrayList<>();

    // World encounters the player has defeated, keyed by encounter id.
    public final HashSet<String> defeatedEncounters = new HashSet<>();

    public OpenWorldState() { }

    // Clears run-scoped state, keeping all persistent progression (seed,
    // lifetime score, currency, XP, exploration, locations, modifications,
    // NPC state, defeated encounters).
    public void resetRun() {
        score = 0;
        length = 0;
    }

    // -----------------------------------------------------------------------
    // Coordinate helpers
    // -----------------------------------------------------------------------

    // Packs a chunk (cx, cy) into a single long key for exploredChunks and other
    // chunk-keyed structures. Works for negative chunk coordinates too.
    public static long packChunkKey(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xFFFFFFFFL);
    }

    public static int unpackChunkKeyX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackChunkKeyY(long key) {
        return (int) key;
    }

    // Marks the chunk containing a world cell as explored. Safe to call even if
    // already explored (no-op).
    public void markExplored(int worldX, int worldY) {
        exploredChunks.add(packChunkKey(
                OpenWorldCoords.worldToChunk(worldX),
                OpenWorldCoords.worldToChunk(worldY)));
    }

    public boolean isExplored(int worldX, int worldY) {
        return exploredChunks.contains(packChunkKey(
                OpenWorldCoords.worldToChunk(worldX),
                OpenWorldCoords.worldToChunk(worldY)));
    }

    // -----------------------------------------------------------------------
    // Record types for future progression data
    // -----------------------------------------------------------------------

    // A discovered point of interest. Later prompts define what kinds of
    // locations exist (camps, dungeons, landmarks, ...); this holds the shared
    // fields so those additions don't require a different container.
    public static class LocationRecord {
        public int x;
        public int y;
        public String id;      // stable identifier / kind of location
        public LocationRecord(int x, int y, String id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }
    }

    // A persistent change to the world (terrain edit, structure, etc.). The
    // concrete meaning of `meta` is owned by whichever system creates it.
    public static class WorldModification {
        public int x;
        public int y;
        public int kind;       // discriminator for the modification type
        public int meta;       // per-kind data
        public WorldModification(int x, int y, int kind, int meta) {
            this.x = x;
            this.y = y;
            this.kind = kind;
            this.meta = meta;
        }
    }

    // Persistent state for an NPC or world object, keyed by id.
    public static class NpcState {
        public String id;
        public int x;
        public int y;
        public int state;      // per-NPC meaning established by later systems
        public NpcState(String id, int x, int y, int state) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.state = state;
        }
    }
}
