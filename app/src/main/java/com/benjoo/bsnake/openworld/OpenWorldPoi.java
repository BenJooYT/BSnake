package com.benjoo.bsnake.openworld;

// ---------------------------------------------------------------------------
// OPEN WORLD POINT OF INTEREST DEFINITIONS
// ---------------------------------------------------------------------------
// The hand-designed POI templates. Placement is procedural and deterministic
// (see OpenWorldGenerator) but the *look and feel* of each location comes from
// one of these templates, so discovered places are recognizable landmarks rather
// than random noise.
//
// Each template also carries a hand-designed STRUCTURE BLUEPRINT: a small grid
// of unit cells that describes how the location is "built". A '.' is empty
// ground; any other char is a structure unit of a given kind (wall, floor,
// feature, decor). This gives POIs real, legible structures made of units,
// instead of a bare marker, while placement of the whole POI stays procedural.
//
// Templates are tiered so rarity maps to reward magnitude:
//   COMMON   - frequent, small discoveries (camps, small ruins, shrines, ...)
//   UNCOMMON - need more exploration (settlements, caves, structures, ...)
//   RARE     - memorable, high-value finds (temples, landmarks, boss arenas)
//
// Rare POIs grant one-time rewards keyed to the location itself, which cannot be
// simply farmed because each unique POI pays out only once per save.
// ---------------------------------------------------------------------------
public class OpenWorldPoi {

    // Tiers.
    public static final int TIER_COMMON = 0;
    public static final int TIER_UNCOMMON = 1;
    public static final int TIER_RARE = 2;

    // Kind identifiers (index into TEMPLATES). Grouped by tier for lookup.
    public static final int CAMP = 0;
    public static final int RUIN_SMALL = 1;
    public static final int RESOURCE_PATCH = 2;
    public static final int SHRINE = 3;
    public static final int CAVE_ENTRANCE = 4;

    public static final int SETTLEMENT = 5;
    public static final int CAVE_LARGE = 6;
    public static final int ANCIENT_STRUCTURE = 7;
    public static final int TRADING_POST = 8;
    public static final int HIDDEN_GARDEN = 9;

    public static final int TEMPLE = 10;
    public static final int RUINS_MASSIVE = 11;
    public static final int BIOME_LANDMARK = 12;
    public static final int LEGENDARY_RESOURCE = 13;
    public static final int BOSS_ARENA = 14;

    public static final int KIND_COUNT = 15;

    // -----------------------------------------------------------------------
    // Structure unit kinds (blueprint characters)
    // -----------------------------------------------------------------------
    public static final char UNIT_EMPTY = '.';
    public static final char UNIT_WALL = 'W';
    public static final char UNIT_FLOOR = 'F';
    public static final char UNIT_FEATURE = 'O';
    public static final char UNIT_DECOR = 'D';
    // Enclosure perimeter char (draws as a wall ring on the outer boundary).
    public static final char UNIT_BOUNDARY = 'B';

    // -----------------------------------------------------------------------
    // Template record
    // -----------------------------------------------------------------------
    public static class Template {
        public final int kind;
        public final String id;      // stable identifier used for Locations
        public final String name;    // player-facing landmark name
        public final int tier;
        public final int radius;     // discovery/visual footprint in cells
        public final int rewardCurrency;
        public final int rewardXp;
        public final int color;      // ARGB marker color
        public final char[][] plan;  // structure blueprint (rows, bottom-up)

        Template(int kind, String id, String name, int tier, int radius,
                 int rewardCurrency, int rewardXp, int color, char[][] plan) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.tier = tier;
            this.radius = radius;
            this.rewardCurrency = rewardCurrency;
            this.rewardXp = rewardXp;
            this.color = color;
            this.plan = plan;
        }
    }

    // Sparse lookup by kind; null when kind is out of range.
    private static final Template[] TEMPLATES = buildTemplates();

    // Helper: rows of a blueprint, top of the grid first (index 0 = highest Y).
    private static char[][] plan(String... rows) {
        int h = rows.length;
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        char[][] p = new char[h][w];
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < w; x++) {
                p[y][x] = x < r.length() ? r.charAt(x) : UNIT_EMPTY;
            }
        }
        return p;
    }

    private static Template[] buildTemplates() {
        Template[] t = new Template[KIND_COUNT];
        // ---- Common (small, frequent) ----
        t[CAMP] = new Template(CAMP, "poi_camp", "Abandoned Campsite", TIER_COMMON, 3,
                25, 20, 0xFFC9A227,
                plan(
                        "..OO..",
                        ".WFFW.",
                        "WFFFFW",
                        "WFFOFW",
                        ".WFFW.",
                        "..OO.."));
        t[RUIN_SMALL] = new Template(RUIN_SMALL, "poi_ruin_small", "Small Ruin", TIER_COMMON, 4,
                30, 25, 0xFF9A8A78,
                plan(
                        "..W....",
                        ".W.F.W.",
                        "W.DF.W.",
                        ".WDF.W.",
                        "..W..W.",
                        ".....W."));
        t[RESOURCE_PATCH] = new Template(RESOURCE_PATCH, "poi_resource", "Resource Patch", TIER_COMMON, 3,
                20, 25, 0xFF7CC65A,
                plan(
                        "..O..",
                        ".ODO.",
                        ".ODO.",
                        ".ODO.",
                        "..O.."));
        // A stepped shrine with a raised central altar (feature), flanked by two
        // pillar walls, open approach at the front. Infinitely symmetrical.
        t[SHRINE] = new Template(SHRINE, "poi_shrine", "Forgotten Shrine", TIER_COMMON, 4,
                35, 40, 0xFFB18BD6,
                plan(
                        "...W...",
                        "..WFW..",
                        ".WFFFW.",
                        "WFOOOFW",
                        ".WFFFW.",
                        "..WWW..",
                        "...O...",
                        "......."));
        t[CAVE_ENTRANCE] = new Template(CAVE_ENTRANCE, "poi_cave_small", "Cave Entrance", TIER_COMMON, 3,
                25, 20, 0xFF6E5A4A,
                plan(
                        "WWWWW",
                        "WDDDW",
                        "WDFDW",
                        "WDDDW",
                        "WWWWW"));
        // ---- Uncommon ----
        // Four small houses around a central open plaza with a well. Each house
        // is a wall frame with a floor interior and a door gap in its bottom wall.
        t[SETTLEMENT] = new Template(SETTLEMENT, "poi_settlement", "Abandoned Settlement", TIER_UNCOMMON, 6,
                80, 70, 0xFFE08A3C,
                plan(
                        ".WWWW.....WWWW.",
                        ".WFFW.....WFFW.",
                        ".WFFW.....WFFW.",
                        ".W.WW.....W.WW.",
                        ".......O.......",
                        ".WWWW.....WWWW.",
                        ".WFFW.....WFFW.",
                        ".WFFW.....WFFW.",
                        ".W.WW.....W.WW."));
        t[CAVE_LARGE] = new Template(CAVE_LARGE, "poi_cave_large", "Large Cave", TIER_UNCOMMON, 5,
                70, 65, 0xFF55473B,
                plan(
                        "..WWW..",
                        ".WDDDW.",
                        "WDDFDDW",
                        "WDFFFDW",
                        "WDDFDDW",
                        ".WDDDW.",
                        "..WWW.."));
        // A domed ancient structure: round stepped walls with a central chamber
        // (feature blocks) and a capstone. Fully symmetric.
        t[ANCIENT_STRUCTURE] = new Template(ANCIENT_STRUCTURE, "poi_ancient", "Ancient Structure", TIER_UNCOMMON, 6,
                75, 80, 0xFF5AA0C9,
                plan(
                        "....O....",
                        "...WWW...",
                        "..WFFFW..",
                        ".WFFFFFW.",
                        "WFOOOOFW.",
                        ".WFFFFFW.",
                        "..WFFFW..",
                        "...WWW...",
                        "........."));
        t[TRADING_POST] = new Template(TRADING_POST, "poi_trading", "Trading Post", TIER_UNCOMMON, 5,
                90, 60, 0xFF3CC9C0,
                plan(
                        "..WOW..",
                        ".WFFFW.",
                        "WFFOFFW",
                        "WFFOOFW",
                        "WFFFFFW",
                        ".WFFFW.",
                        "..WWW.."));
        t[HIDDEN_GARDEN] = new Template(HIDDEN_GARDEN, "poi_garden", "Hidden Garden", TIER_UNCOMMON, 5,
                65, 90, 0xFF57C96B,
                plan(
                        "..OOO..",
                        ".OFOFO.",
                        "OFFFFFO",
                        "OFFOFFO",
                        "OFFFFFO",
                        ".OFOFO.",
                        "..OOO.."));
        // ---- Rare ----
        // A grand symmetrical temple: stepped roof capstones, a wide hall with twin
        // inner sanctuaries (feature blocks) and a central altar, flanked by two
        // towers. Symmetric about the vertical axis.
        t[TEMPLE] = new Template(TEMPLE, "poi_temple", "Ancient Temple", TIER_RARE, 8,
                200, 180, 0xFFFFD54F,
                plan(
                        "...W.W.W...",
                        "..WWWWWWW..",
                        ".WFFFFFFFW.",
                        "WFFFOOOFFFW",
                        "WFFFOOOFFFW",
                        "WFFFOOOFFFW",
                        ".WFFFFFFFW.",
                        "..WWWWWWW..",
                        "...W.W.W..."));
        t[RUINS_MASSIVE] = new Template(RUINS_MASSIVE, "poi_ruins_massive", "Massive Ruins", TIER_RARE, 9,
                180, 200, 0xFFC0B39A,
                plan(
                        "WW..W..WW.",
                        "WFFWWFFFW.",
                        "WFFFWFFFW.",
                        ".WWFFWW.WW",
                        ".WFFFW.FFW",
                        "WFFFFWWWW.",
                        "WWFW..W...",
                        ".WWW......"));
        t[BIOME_LANDMARK] = new Template(BIOME_LANDMARK, "poi_landmark", "Biome Landmark", TIER_RARE, 7,
                160, 200, 0xFF8FD0FF,
                plan(
                        "....O....",
                        "...OFW...",
                        "..OFFFW..",
                        ".OFFOFFW.",
                        "OFFFWFFW.",
                        ".OFFOFFW.",
                        "..OFFFW..",
                        "...OFW...",
                        "....O...."));
        t[LEGENDARY_RESOURCE] = new Template(LEGENDARY_RESOURCE, "poi_legendary", "Legendary Resource Site", TIER_RARE, 6,
                250, 160, 0xFFFFB300,
                plan(
                        "..OODO..",
                        ".ODOODO.",
                        "ODODOODO",
                        "ODFDFODO",
                        "ODODOODO",
                        ".ODOODO.",
                        "..OODO.."));
        t[BOSS_ARENA] = new Template(BOSS_ARENA, "poi_boss_arena", "World Boss Arena", TIER_RARE, 10,
                0, 250, 0xFFE53935,
                plan(
                        "BBBBBBBBBB",
                        "BWWWWWWWWB",
                        "BWWWWWWWWB",
                        "BWWWFFWWWB",
                        "BWWFFOFFWB",
                        "BWWFFOFFWB",
                        "BWWWFFWWWB",
                        "BWWWWWWWWB",
                        "BWWWWWWWWB",
                        "BBBBBBBBBB"));
        return t;
    }

    public static Template templateFor(int kind) {
        if (kind < 0 || kind >= KIND_COUNT) return null;
        return TEMPLATES[kind];
    }
}