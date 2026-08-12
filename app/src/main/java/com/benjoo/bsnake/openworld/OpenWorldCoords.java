package com.benjoo.bsnake.openworld;

// ---------------------------------------------------------------------------
// OPEN WORLD COORDINATES
// ---------------------------------------------------------------------------
// Pure, reusable coordinate utilities for the Open World mode. All conversions
// between world-space cell coordinates and chunk coordinates live here so they
// are correct once and reused everywhere.
//
// The Open World uses an unbounded integer grid in world coordinates. The world
// is divided into fixed-size square chunks (reusing the existing 32x32 cell
// concept). Every world cell belongs to exactly one chunk, even across zero and
// for arbitrarily large positive/negative coordinates. This relies on proper
// floor division / floor modulo so cells near chunk boundaries are assigned
// consistently regardless of sign.
//
// World coordinate system is fully independent from screen coordinates.
// ---------------------------------------------------------------------------
public final class OpenWorldCoords {

    // Reuses the existing 32x32 arena concept as the chunk size.
    public static final int CHUNK_SIZE = 32;

    private OpenWorldCoords() { }

    // World-space cell coordinate -> owning chunk coordinate (floor division,
    // so negative cells map to the correct chunk).
    public static int worldToChunk(int world) {
        return Math.floorDiv(world, CHUNK_SIZE);
    }

    // World-space cell coordinate -> local coordinate (0..CHUNK_SIZE-1) within
    // its owning chunk. Floor modulo keeps local coords non-negative even for
    // negative world cells.
    public static int worldToLocal(int world) {
        return Math.floorMod(world, CHUNK_SIZE);
    }

    // Chunk coordinate -> world coordinate of the chunk's minimum corner cell.
    // CHUNK_SIZE is a positive constant, so plain multiplication is exact.
    public static int chunkToWorld(int chunk) {
        return chunk * CHUNK_SIZE;
    }

    // World coordinate of the center cell of a chunk (used as an anchor for
    // procedural generation that wants a stable reference point per chunk).
    public static int chunkCenterWorld(int chunk) {
        return chunkToWorld(chunk) + CHUNK_SIZE / 2;
    }

    // World cell coordinate from a chunk coordinate and an in-chunk local
    // coordinate. The inverse of worldToChunk + worldToLocal. Works for negative
    // chunk coordinates because chunkToWorld is exact (CHUNK_SIZE is positive,
    // so multiplying a negative chunk by it stays exact), and local is always
    // in [0, CHUNK_SIZE).
    public static int chunkLocalToWorld(int chunk, int local) {
        return chunkToWorld(chunk) + local;
    }

    // Wraps a single world-cell decomposition into its owning chunk coordinate
    // and in-chunk local coordinate. Reliability around negative coordinates is
    // guaranteed by floorDiv (worldToChunk) and floorMod (worldToLocal), so a
    // cell is always assigned to exactly one chunk and gets a local coord in the
    // correct range even at chunk boundaries and across zero.
    public static Cell decompose(int worldX, int worldY) {
        int cx = worldToChunk(worldX);
        int cy = worldToChunk(worldY);
        return new Cell(cx, cy,
                worldToLocal(worldX), worldToLocal(worldY),
                worldX, worldY);
    }

    // A value holder connecting the world/chunk/local coordinate systems for a
    // single world cell. Pure data; the conversions live in OpenWorldCoords.
    public static class Cell {
        public final int chunkX;
        public final int chunkY;
        public final int localX;
        public final int localY;
        public final int worldX;
        public final int worldY;

        Cell(int chunkX, int chunkY, int localX, int localY, int worldX, int worldY) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.localX = localX;
            this.localY = localY;
            this.worldX = worldX;
            this.worldY = worldY;
        }

        public int chunkOriginWorldX() {
            return chunkToWorld(chunkX);
        }

        public int chunkOriginWorldY() {
            return chunkToWorld(chunkY);
        }
    }
}

