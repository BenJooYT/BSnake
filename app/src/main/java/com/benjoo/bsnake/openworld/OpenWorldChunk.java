package com.benjoo.bsnake.openworld;

import java.util.ArrayList;

// ---------------------------------------------------------------------------
// OPEN WORLD CHUNK
// ---------------------------------------------------------------------------
// A lightweight representation of a single Open World chunk. Each chunk owns a
// region of CHUNK_SIZE x CHUNK_SIZE world cells and a world-space coordinate.
//
// This is a Phase 1 structure. It establishes the containers that later prompts
// will fill with generated terrain, obstacles, food, points of interest,
// enemies, and persistent modifications — without allocating heavyweight Java
// objects for every world cell. The base terrain layer is a flat primitive
// byte[] grid (CHUNK_SIZE x CHUNK_SIZE), and every other content type is stored
// sparsely: records exist only where entities actually are, never one per cell.
//
// Containers are lazily allocated on first use so an empty chunk stays tiny.
// ---------------------------------------------------------------------------
public class OpenWorldChunk {

    // Open space (no terrain).
    public static final byte TERRAIN_EMPTY = 0;

    // World-space chunk coordinates (see OpenWorldCoords).
    public final int chunkX;
    public final int chunkY;

    // World coordinate of this chunk's minimum corner cell.
    public final int originX;
    public final int originY;

    // True once this chunk's content has been generated. The generation pass
    // itself is deferred to a later phase; the flag establishes the lifecycle.
    public boolean generated = false;

    // Compact base-terrain grid, CHUNK_SIZE x CHUNK_SIZE (row-major), allocated
    // lazily. One byte per cell keeps even a fully shaped chunk tiny (~1 KiB).
    // 0 = TERRAIN_EMPTY; other values are terrain/block types defined by the
    // future generation system. Never an object per cell.
    private byte[] terrain;

    // Sparse content (records exist only where content exists, never per cell).
    // The concrete types/kinds these represent are owned by later prompts; the
    // small record types below carry the shared spatial fields they all need.
    private ArrayList<CellRef> obstacles;
    private ArrayList<CellRef> foodRefs;
    private ArrayList<PointOfInterest> pointsOfInterest;
    private ArrayList<EntityRef> enemies;
    private ArrayList<CellRef> modifications;

    public OpenWorldChunk(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.originX = OpenWorldCoords.chunkToWorld(chunkX);
        this.originY = OpenWorldCoords.chunkToWorld(chunkY);
    }

    // Returns true if the given world cell lies within this chunk.
    public boolean contains(int worldX, int worldY) {
        return OpenWorldCoords.worldToChunk(worldX) == chunkX
                && OpenWorldCoords.worldToChunk(worldY) == chunkY;
    }

    // -----------------------------------------------------------------------
    // Terrain (compact primitive grid)
    // -----------------------------------------------------------------------

    private void ensureTerrain() {
        if (terrain == null) {
            terrain = new byte[OpenWorldCoords.CHUNK_SIZE * OpenWorldCoords.CHUNK_SIZE];
        }
    }

    // Local coordinate -> linear index (local coords are guaranteed 0..SIZE-1).
    private static int indexOf(int localX, int localY) {
        return localY * OpenWorldCoords.CHUNK_SIZE + localX;
    }

    public byte terrainAtLocal(int localX, int localY) {
        if (terrain == null) return TERRAIN_EMPTY;
        return terrain[indexOf(localX, localY)];
    }

    public void setTerrainAtLocal(int localX, int localY, byte type) {
        ensureTerrain();
        terrain[indexOf(localX, localY)] = type;
    }

    public byte terrainAtWorld(int worldX, int worldY) {
        if (!contains(worldX, worldY)) return TERRAIN_EMPTY;
        return terrainAtLocal(
                OpenWorldCoords.worldToLocal(worldX),
                OpenWorldCoords.worldToLocal(worldY));
    }

    public void setTerrainAtWorld(int worldX, int worldY, byte type) {
        if (!contains(worldX, worldY)) return;
        setTerrainAtLocal(
                OpenWorldCoords.worldToLocal(worldX),
                OpenWorldCoords.worldToLocal(worldY),
                type);
    }

    // -----------------------------------------------------------------------
    // Sparse content containers (lazily allocated)
    // -----------------------------------------------------------------------

    public ArrayList<CellRef> obstacles() {
        if (obstacles == null) obstacles = new ArrayList<>();
        return obstacles;
    }

    public ArrayList<CellRef> foodRefs() {
        if (foodRefs == null) foodRefs = new ArrayList<>();
        return foodRefs;
    }

    public ArrayList<PointOfInterest> pointsOfInterest() {
        if (pointsOfInterest == null) pointsOfInterest = new ArrayList<>();
        return pointsOfInterest;
    }

    public ArrayList<EntityRef> enemies() {
        if (enemies == null) enemies = new ArrayList<>();
        return enemies;
    }

    public ArrayList<CellRef> modifications() {
        if (modifications == null) modifications = new ArrayList<>();
        return modifications;
    }

    // -----------------------------------------------------------------------
    // Record types for future content
    // -----------------------------------------------------------------------

    // A single-cell sparse record with a kind discriminator. Local coordinates
    // keep it small; world coords can be derived via chunkOrigin + local.
    public static class CellRef {
        public final int localX;
        public final int localY;
        public final int kind;
        public CellRef(int localX, int localY, int kind) {
            this.localX = localX;
            this.localY = localY;
            this.kind = kind;
        }
    }

    // A point of interest — a larger anchored feature (camp, landmark, ...).
    public static class PointOfInterest {
        public final int localX;
        public final int localY;
        public final int kind;
        public final int radius;
        public PointOfInterest(int localX, int localY, int kind, int radius) {
            this.localX = localX;
            this.localY = localY;
            this.kind = kind;
            this.radius = radius;
        }
    }

    // A housed entity (enemy / NPC / boss) within the chunk.
    public static class EntityRef {
        public final int localX;
        public final int localY;
        public final int kind;
        public final int id;
        public EntityRef(int localX, int localY, int kind, int id) {
            this.localX = localX;
            this.localY = localY;
            this.kind = kind;
            this.id = id;
        }
    }
}
