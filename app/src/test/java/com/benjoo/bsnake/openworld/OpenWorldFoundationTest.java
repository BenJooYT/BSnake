package com.benjoo.bsnake.openworld;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// JVM unit tests for the pure Open World foundation (no Android dependencies):
// coordinate conversions, chunk lifecycle, save round-trip, and determinism.
// These exercise the negative-coordinate, chunk-boundary, origin, and large-
// distance cases the Phase 1 prompt calls out for early detection.
public class OpenWorldFoundationTest {

    // ------------------------- Coordinate conversion ------------------------

    @Test public void worldToChunk_negativeUsesFloorDivision() {
        assertEquals(0, OpenWorldCoords.worldToChunk(0));
        assertEquals(0, OpenWorldCoords.worldToChunk(31));
        assertEquals(1, OpenWorldCoords.worldToChunk(32));
        assertEquals(-1, OpenWorldCoords.worldToChunk(-1));   // floorDiv(-1,32) = -1
        assertEquals(-1, OpenWorldCoords.worldToChunk(-32));
        assertEquals(-2, OpenWorldCoords.worldToChunk(-33));
    }

    @Test public void worldToLocal_negativeUsesFloorModulo() {
        assertEquals(0, OpenWorldCoords.worldToLocal(0));
        assertEquals(31, OpenWorldCoords.worldToLocal(31));
        assertEquals(0, OpenWorldCoords.worldToLocal(32));
        assertEquals(31, OpenWorldCoords.worldToLocal(-1));   // floorMod(-1,32) = 31
        assertEquals(0, OpenWorldCoords.worldToLocal(-32));
        assertEquals(31, OpenWorldCoords.worldToLocal(-33));
    }

    @Test public void chunkToWorld_isExactForNegatives() {
        assertEquals(0, OpenWorldCoords.chunkToWorld(0));
        assertEquals(32, OpenWorldCoords.chunkToWorld(1));
        assertEquals(-32, OpenWorldCoords.chunkToWorld(-1));
    }

    @Test public void cellDecompose_reconstructsAcrossOrigin() {
        OpenWorldCoords.Cell c = OpenWorldCoords.decompose(-1, -1);
        assertEquals(-1, c.chunkX);
        assertEquals(-1, c.chunkY);
        assertEquals(31, c.localX);
        assertEquals(31, c.localY);
        // Reconstruct the world cell from chunk + local.
        assertEquals(-1, OpenWorldCoords.chunkLocalToWorld(c.chunkX, c.localX));
        assertEquals(-1, OpenWorldCoords.chunkLocalToWorld(c.chunkY, c.localY));
    }

    @Test public void chunkLocalToWorld_isInverseOfWorldToLocal() {
        for (int w : new int[]{-130, -64, -33, -32, -1, 0, 1, 31, 32, 33, 100, 1_000_000, -1_000_000}) {
            int chunk = OpenWorldCoords.worldToChunk(w);
            int local = OpenWorldCoords.worldToLocal(w);
            assertEquals(w, OpenWorldCoords.chunkLocalToWorld(chunk, local));
            assertEquals(chunk, OpenWorldCoords.worldToChunk(OpenWorldCoords.chunkLocalToWorld(chunk, local)));
        }
    }

    @Test public void packUnpack_worksForNegativesAndLargeValues() {
        int[][] pairs = {{0,0},{31,32},{-1,-1},{-32,32},{1_000_000,-1_000_000},{-1_000_000_000,1_000_000_000}};
        for (int[] p : pairs) {
            long key = OpenWorldState.packChunkKey(p[0], p[1]);
            assertEquals(p[0], OpenWorldState.unpackChunkKeyX(key));
            assertEquals(p[1], OpenWorldState.unpackChunkKeyY(key));
        }
    }

    // ------------------------- Chunk manager lifecycle ----------------------

    @Test public void chunkManager_maintainsBoundedAreaAroundNegativeCoords() {
        OpenWorldChunkManager mgr = new OpenWorldChunkManager();
        mgr.setGenerator(new OpenWorldGenerator(12345L));

        mgr.update(0, 0);
        int r = OpenWorldChunkManager.LOAD_RADIUS;
        int side = 2 * r + 1;
        assertEquals(side * side, mgr.loadedChunkCount());

        // Player's own chunk is ACTIVE, neighbours are NEARBY.
        assertEquals(OpenWorldChunkManager.ChunkState.ACTIVE, mgr.stateOf(0, 0, 0, 0));
        assertEquals(OpenWorldChunkManager.ChunkState.NEARBY, mgr.stateOf(32, 0, 0, 0));

        // Far chunk not loaded -> DISTANT.
        assertEquals(OpenWorldChunkManager.ChunkState.DISTANT, mgr.stateOf(9000, 0, 0, 0));

        // Move deep into the negative world (crossing many chunk boundaries).
        mgr.update(-64, -64);
        assertEquals(side * side, mgr.loadedChunkCount());
        assertEquals(OpenWorldChunkManager.ChunkState.ACTIVE,
                mgr.stateOf(-64, -64, -64, -64));
    }

    @Test public void chunkManager_unloadsDistantChunks() {
        OpenWorldChunkManager mgr = new OpenWorldChunkManager();
        mgr.setGenerator(new OpenWorldGenerator(7L));
        mgr.update(0, 0);
        int side = 2 * OpenWorldChunkManager.LOAD_RADIUS + 1;
        assertTrue(mgr.loadedChunkCount() <= side * side);
        // Move far away: the old loaded set must be dropped (bounded).
        mgr.update(500, 500);
        assertTrue(mgr.loadedChunkCount() <= side * side);
        // A chunk at the old origin is now distant.
        assertEquals(OpenWorldChunkManager.ChunkState.DISTANT, mgr.stateOf(0, 0, 500, 500));
    }

    @Test public void chunk_landmarkOperations() {
        OpenWorldChunk chunk = new OpenWorldChunk(-1, -1); // spans world -32..-1
        assertEquals(-32, chunk.originX);
        assertTrue(chunk.contains(-1, -1));
        assertFalse(chunk.contains(0, 0));
        chunk.setTerrainAtWorld(-1, -1, (byte) 3);
        assertEquals((byte) 3, chunk.terrainAtWorld(-1, -1));
        assertEquals(OpenWorldChunk.TERRAIN_EMPTY, chunk.terrainAtWorld(-2, -2));
    }

    // ------------------------- Save data round-trip -------------------------

    @Test public void saveData_roundTripsAllFields() {
        OpenWorldState s = new OpenWorldState();
        s.initialized = true;
        s.seed = Long.MAX_VALUE;
        s.playerX = -5;
        s.playerY = 400;
        s.length = 12;
        s.lifetimeScore = 999;
        s.currency = 50;
        s.xp = 3;
        s.level = 2;
        s.markExplored(-1, -1);
        s.markExplored(0, 0);
        s.discoveredLocations.add(new OpenWorldState.LocationRecord(10, 20, "camp"));
        s.worldModifications.add(new OpenWorldState.WorldModification(-5, -5, 1, 7));
        s.npcStates.add(new OpenWorldState.NpcState("villager", 4, 4, 1));
        s.defeatedEncounters.add("boss_alpha");

        String csv = OpenWorldSaveData.fromState(s).toCsv();
        OpenWorldSaveData d = OpenWorldSaveData.fromCsv(csv);

        assertEquals(true, d.initialized);
        assertEquals(Long.MAX_VALUE, d.seed);
        assertEquals(-5, d.playerX);
        assertEquals(400, d.playerY);
        assertEquals(12, d.length);
        assertEquals(999, d.lifetimeScore);
        assertEquals(50, d.currency);
        assertEquals(3, d.xp);
        assertEquals(2, d.level);

        OpenWorldState restored = new OpenWorldState();
        d.applyTo(restored);
        assertEquals(2, restored.exploredChunks.size());
        assertTrue(restored.isExplored(-1, -1));
        assertTrue(restored.isExplored(0, 0));
        assertEquals(1, restored.discoveredLocations.size());
        assertEquals("camp", restored.discoveredLocations.get(0).id);
        assertEquals(1, restored.worldModifications.size());
        assertEquals(1, restored.npcStates.size());
        assertEquals(1, restored.defeatedEncounters.size());
    }

    @Test public void saveData_emptyCsvDecodesAsUninitialized() {
        OpenWorldSaveData d = OpenWorldSaveData.fromCsv("");
        assertFalse(d.initialized);
    }

    // ------------------------- Determinism -------------------------

    @Test public void worldSeed_isDeterministicPerCoordinates() {
        assertEquals(WorldSeed.deterministic(42, 3, 4), WorldSeed.deterministic(42, 3, 4));
        assertTrue(WorldSeed.deterministic(42, 3, 4) != WorldSeed.deterministic(42, 4, 4)
                || WorldSeed.deterministic(42, 3, 4) != WorldSeed.deterministic(42, 3, 5));
        // seededRandom is stable for a fixed seed + chunk.
        Random r1 = WorldSeed.seededRandom(99, -7, -7);
        Random r2 = WorldSeed.seededRandom(99, -7, -7);
        assertEquals(r1.nextInt(), r2.nextInt());
    }
}