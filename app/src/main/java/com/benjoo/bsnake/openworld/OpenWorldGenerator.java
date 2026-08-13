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
        fillTerrain(chunk);
        // Place a deterministic handful of normal food within this chunk. Food
        // is stored sparsely in the chunk's foodRefs and is loaded/unloaded
        // with the chunk, so a fresh world always yields food wherever the
        // player travels and it disappears once the chunk is far away.
        Random r = randomForChunk(chunk.chunkX, chunk.chunkY);
        int count = 2 + r.nextInt(3);
        int size = OpenWorldCoords.CHUNK_SIZE;
        for (int i = 0; i < count; i++) {
            int lx = r.nextInt(size);
            int ly = r.nextInt(size);
            chunk.foodRefs().add(new OpenWorldChunk.CellRef(lx, ly, OpenWorldChunk.FOOD_NORMAL));
        }
        placePois(chunk);
    }

    // -----------------------------------------------------------------------
    // Points of interest
    // -----------------------------------------------------------------------
    // POIs are placed on a coarse grid so they are sparse landmarks, each slot
    // sized to fit comfortably inside one chunk. Placement, kind, jitter and
    // tier are all driven by a per-slot Random (derived from the world seed),
    // so a POI's position and identity are stable for the life of the save and
    // never collide with fresh per-chunk state.

    // Spacing (in cells) between adjacent POI resource slots. Kept larger than
    // a chunk so each slot anchors exactly one POI away from neighbours.
    public static final int POI_SLOT_CELLS = 64;

    // Number of POI slots across the world before the tier table resamples, so
    // rarer tiers actually stay rare instead of clustering.
    private static final int TIER_RESAMPLE = 3;

    // Probability (per 100) that a rare-tier slot becomes a true rare POI rather
    // than lowering to uncommon. Keeps endgame finds special.
    private static final int RARE_KEEP_PROB = 60;

    private void placePois(OpenWorldChunk chunk) {
        int slotX = Math.floorDiv(chunk.originX, POI_SLOT_CELLS);
        int slotY = Math.floorDiv(chunk.originY, POI_SLOT_CELLS);
        // Only one slot influences this chunk (slot spans POI_SLOT_CELLS and a
        // chunk is smaller), but compute over the covering slot regardless.
        Random sr = randomForChunk(slotX, slotY);
        // Long-lived contribution of the whole slot region to entity variety.
        int slotHash = hash(slotX, slotY);

        int tierRoll = Math.floorMod(slotHash, 100);
        int resample = Math.floorMod(sr.nextInt(), TIER_RESAMPLE) == 0 ? 1 : 0;
        // Determine tier: ~40% common, ~40% uncommon, ~20% rare (with resample
        // wobble so no single seed guarantees a rare). Rare can "fall back".
        int tier;
        if (tierRoll < 40) tier = OpenWorldPoi.TIER_COMMON;
        else if (tierRoll < 80) tier = OpenWorldPoi.TIER_UNCOMMON;
        else tier = (resample == 1 && sr.nextInt(100) < RARE_KEEP_PROB)
                ? OpenWorldPoi.TIER_RARE : OpenWorldPoi.TIER_UNCOMMON;

        int kind = pickKindForTier(tier, sr);

        // Anchor the POI at a deterministic jittered cell inside the slot.
        int lx = Math.floorMod(sr.nextInt(), POI_SLOT_CELLS);
        int ly = Math.floorMod(sr.nextInt(), POI_SLOT_CELLS);
        int worldX = slotX * POI_SLOT_CELLS + lx;
        int worldY = slotY * POI_SLOT_CELLS + ly;

        // Only this chunk owns the POI record (its origin maps to the same slot
        // only for the owning chunk). Guard against double-adding on the shared
        // slot when neighbouring chunks regenerate.
        if (!chunk.contains(worldX, worldY)) return;

        // A POI never sits on water; nudge it onto walkable terrain.
        int nudge = 0;
        while (nudge < POI_SLOT_CELLS && isWater(worldX, worldY)) {
            worldX = slotX * POI_SLOT_CELLS + Math.floorMod(lx + ++nudge, POI_SLOT_CELLS);
            if (!chunk.contains(worldX, worldY)) return;
        }
        if (isWater(worldX, worldY)) return;

        OpenWorldPoi.Template t = OpenWorldPoi.templateFor(kind);
        if (t == null) return;
        int radius = t.radius;
        // Store the POI anchor record.
        chunk.pointsOfInterest().add(new OpenWorldChunk.PointOfInterest(
                worldX - chunk.originX, worldY - chunk.originY, kind, radius));
        // Expand the template's blueprint into structure unit cells. Each POI is
        // a structure "built" from units; unit cells are stored in their owning
        // chunk's obstacle container (kept light: one CellRef per built cell).
        placeStructureUnits(chunk, t, worldX, worldY);
    }

    // Writes the template's blueprint into structure unit records. The plan grid
    // is centred on the POI anchor; each non-empty cell becomes a CellRef in the
    // chunk that contains it. Only used for the loaded/visible world, so cells
    // landing off-chunk are simply skipped here (neighbouring chunk regenerates
    // its own copy); out-of-bounds blueprint cells are clamped/skipped.
    private void placeStructureUnits(OpenWorldChunk chunk, OpenWorldPoi.Template t,
                                     int anchorX, int anchorY) {
        if (t.plan == null || t.plan.length == 0) return;
        int h = t.plan.length;
        int w = t.plan[0].length;
        // Anchor sits at the centre of the blueprint grid.
        int halfW = w / 2;
        int halfH = h / 2;
        OpenWorldChunk.CellRef anchor = null;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                char c = t.plan[py][px];
                if (c == OpenWorldPoi.UNIT_EMPTY) continue;
                int wx = anchorX + (px - halfW);
                int wy = anchorY - (py - halfH); // py 0 = top (higher Y) reversed
                // Anchor Y grows downward in world coords; blueprint row 0 is
                // top so offset by - (py - halfH).
                if (!chunk.contains(wx, wy)) continue;
                if (isWater(wx, wy)) continue;
                int kind = unitKind(c);
                chunk.obstacles().add(new OpenWorldChunk.CellRef(
                        wx - chunk.originX, wy - chunk.originY, kind));
                if (kind == OpenWorldPoi.UNIT_FLOOR || kind == OpenWorldPoi.UNIT_FEATURE) {
                    // nothing extra; keep anchor tracking simple below
                }
            }
        }
    }

    private int unitKind(char c) {
        switch (c) {
            case OpenWorldPoi.UNIT_WALL: return OpenWorldPoi.UNIT_WALL;
            case OpenWorldPoi.UNIT_FLOOR: return OpenWorldPoi.UNIT_FLOOR;
            case OpenWorldPoi.UNIT_FEATURE: return OpenWorldPoi.UNIT_FEATURE;
            case OpenWorldPoi.UNIT_DECOR: return OpenWorldPoi.UNIT_DECOR;
            case OpenWorldPoi.UNIT_BOUNDARY: return OpenWorldPoi.UNIT_BOUNDARY;
            default: return OpenWorldPoi.UNIT_FLOOR;
        }
    }

    private boolean isWater(int worldX, int worldY) {
        return biomeAt(worldX, worldY) == OpenWorldChunk.TERRAIN_WATER
                || biomeAt(worldX, worldY) == OpenWorldChunk.TERRAIN_EMPTY;
    }

    // Deterministically picks a POI kind within the requested tier.
    private int pickKindForTier(int tier, Random sr) {
        int[] pool;
        if (tier == OpenWorldPoi.TIER_COMMON) {
            pool = new int[]{OpenWorldPoi.CAMP, OpenWorldPoi.RUIN_SMALL,
                    OpenWorldPoi.RESOURCE_PATCH, OpenWorldPoi.SHRINE,
                    OpenWorldPoi.CAVE_ENTRANCE};
        } else if (tier == OpenWorldPoi.TIER_UNCOMMON) {
            pool = new int[]{OpenWorldPoi.SETTLEMENT, OpenWorldPoi.CAVE_LARGE,
                    OpenWorldPoi.ANCIENT_STRUCTURE, OpenWorldPoi.TRADING_POST,
                    OpenWorldPoi.HIDDEN_GARDEN};
        } else {
            pool = new int[]{OpenWorldPoi.TEMPLE, OpenWorldPoi.RUINS_MASSIVE,
                    OpenWorldPoi.BIOME_LANDMARK, OpenWorldPoi.LEGENDARY_RESOURCE,
                    OpenWorldPoi.BOSS_ARENA};
        }
        return pool[Math.floorMod(sr.nextInt(), pool.length)];
    }

    // Returns the POI kind at a world cell by recomputing its slot, for the map
    // renderer / discovery that must look up a location even when its chunk is
    // not loaded. -1 when the cell is not the anchor of a POI.
    public int poiKindAt(int worldX, int worldY) {
        int slotX = Math.floorDiv(worldX, POI_SLOT_CELLS);
        int slotY = Math.floorDiv(worldY, POI_SLOT_CELLS);
        Random sr = randomForChunk(slotX, slotY);
        int slotHash = hash(slotX, slotY);
        int tierRoll = Math.floorMod(slotHash, 100);
        int resample = Math.floorMod(sr.nextInt(), TIER_RESAMPLE) == 0 ? 1 : 0;
        int tier;
        if (tierRoll < 40) tier = OpenWorldPoi.TIER_COMMON;
        else if (tierRoll < 80) tier = OpenWorldPoi.TIER_UNCOMMON;
        else tier = (resample == 1 && sr.nextInt(100) < RARE_KEEP_PROB)
                ? OpenWorldPoi.TIER_RARE : OpenWorldPoi.TIER_UNCOMMON;
        int kind = pickKindForTier(tier, sr);
        int lx = Math.floorMod(sr.nextInt(), POI_SLOT_CELLS);
        int ly = Math.floorMod(sr.nextInt(), POI_SLOT_CELLS);
        // Anchor Y is fixed by the slot; X gets an in-slot water nudge. Mirror
        // the placement loop exactly so this lookup matches the stored POI.
        int wx = slotX * POI_SLOT_CELLS + lx;
        for (int n = 0; n < POI_SLOT_CELLS; n++) {
            if (wx == worldX && ly == worldY - slotY * POI_SLOT_CELLS) return kind;
            wx = slotX * POI_SLOT_CELLS + Math.floorMod(lx + n + 1, POI_SLOT_CELLS);
        }
        return -1;
    }

    // Fills a chunk's terrain grid with a simplified, deterministic biome map.
    // Uses a coarse per-chunk noise so biomes form broad regions (with a little
    // fine detail) rather than per-cell static. Same seed + chunk => same map,
    // and regenerating an unloaded chunk later reproduces identical terrain.
    private void fillTerrain(OpenWorldChunk chunk) {
        int size = OpenWorldCoords.CHUNK_SIZE;
        for (int localY = 0; localY < size; localY++) {
            for (int localX = 0; localX < size; localX++) {
                int worldX = chunk.originX + localX;
                int worldY = chunk.originY + localY;
                chunk.setTerrainAtLocal(localX, localY, biomeAt(worldX, worldY));
            }
        }
    }

    // Deterministic biome for any world cell. Shared by chunk generation and the
    // map renderer (which needs a color for explored cells even when their chunk
    // is not loaded). Region + fine noise are derived purely from the seed and
    // chunk coordinates, so this matches fillTerrain exactly. Pure hash (no
    // Random allocation) so the full map can shade thousands of cells per frame.
    public byte biomeAt(int worldX, int worldY) {
        int chunkX = OpenWorldCoords.worldToChunk(worldX);
        int chunkY = OpenWorldCoords.worldToChunk(worldY);
        int region = Math.floorMod(hash(chunkX, chunkY + 137), 3);
        int offset = hash(chunkX + 7, chunkY - 3);
        int coarse = Math.floorMod(hash(worldX / 4, worldY / 4), 1000);
        int fine = Math.floorMod(hash(worldX + offset, worldY + offset), 1000);
        byte t;
        if (region == 0) {
            if (coarse < 150) t = OpenWorldChunk.TERRAIN_MOUNTAIN;
            else if (coarse < 300) t = OpenWorldChunk.TERRAIN_SNOW;
            else t = OpenWorldChunk.TERRAIN_GRASS;
        } else if (region == 1) {
            if (coarse < 200) t = OpenWorldChunk.TERRAIN_FOREST;
            else if (coarse < 250) t = OpenWorldChunk.TERRAIN_WATER;
            else t = OpenWorldChunk.TERRAIN_GRASS;
        } else {
            if (coarse < 280) t = OpenWorldChunk.TERRAIN_WATER;
            else if (coarse < 330) t = OpenWorldChunk.TERRAIN_SAND;
            else t = OpenWorldChunk.TERRAIN_GRASS;
        }
        if (fine % 17 == 0) t = OpenWorldChunk.TERRAIN_FOREST;
        if (fine % 91 == 0) t = OpenWorldChunk.TERRAIN_WATER;
        return t;
    }
}
