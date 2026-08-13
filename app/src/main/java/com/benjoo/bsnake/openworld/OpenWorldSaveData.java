package com.benjoo.bsnake.openworld;

import java.util.ArrayList;
import java.util.HashSet;

// ---------------------------------------------------------------------------
// OPEN WORLD SAVE DATA
// ---------------------------------------------------------------------------
// The dedicated, persistent structure for an Open World save. It mirrors the
// persistent (non run-local) fields of OpenWorldState and knows how to encode
// its progression containers for storage and decode them back. It deliberately
// does NOT carry generated terrain: terrain is always rebuilt from the world
// seed, so the save stays small and portable.
//
// The format is a flat CSV document (one pref key) split into lines, each line a
// comma-separated record. Absent sections simply decode as empty, which keeps
// old saves (or saves missing open-world data) loading normally.
// ---------------------------------------------------------------------------
public class OpenWorldSaveData {

    public boolean initialized;
    public long seed;
    public int playerX;
    public int playerY;
    public int length;
    public int lifetimeScore;
    public int currency;
    public int xp;
    public int level;

    public final ArrayList<long[]> exploredChunks = new ArrayList<>(); // {cx, cy}
    public final ArrayList<String[]> discoveredLocations = new ArrayList<>(); // {x, y, id}
    public final ArrayList<int[]> worldModifications = new ArrayList<>();       // {x, y, kind, meta}
    public final ArrayList<String[]> npcStates = new ArrayList<>();             // {x, y, state, id}
    public final ArrayList<String[]> customMarkers = new ArrayList<>();         // {x, y, kind}
    public final ArrayList<String> defeatedEncounters = new ArrayList<>();

    public OpenWorldSaveData() { }

    // Captures the persistent fields of an Open World state into save data.
    public static OpenWorldSaveData fromState(OpenWorldState s) {
        OpenWorldSaveData d = new OpenWorldSaveData();
        d.initialized = s.initialized;
        d.seed = s.seed;
        d.playerX = s.playerX;
        d.playerY = s.playerY;
        d.length = s.length;
        d.lifetimeScore = s.lifetimeScore;
        d.currency = s.currency;
        d.xp = s.xp;
        d.level = s.level;
        for (Long key : s.exploredChunks) {
            d.exploredChunks.add(new long[]{
                    OpenWorldState.unpackChunkKeyX(key),
                    OpenWorldState.unpackChunkKeyY(key)});
        }
        for (OpenWorldState.LocationRecord r : s.discoveredLocations) {
            d.discoveredLocations.add(new String[]{String.valueOf(r.x), String.valueOf(r.y), r.id});
        }
        for (OpenWorldState.WorldModification m : s.worldModifications) {
            d.worldModifications.add(new int[]{m.x, m.y, m.kind, m.meta});
        }
        for (OpenWorldState.NpcState n : s.npcStates) {
            d.npcStates.add(new String[]{String.valueOf(n.x), String.valueOf(n.y),
                    String.valueOf(n.state), n.id});
        }
        for (OpenWorldState.CustomMarker m : s.customMarkers) {
            d.customMarkers.add(new String[]{String.valueOf(m.x), String.valueOf(m.y),
                    String.valueOf(m.kind)});
        }
        d.defeatedEncounters.addAll(s.defeatedEncounters);
        return d;
    }

    // Restores the persistent fields of this save data into an Open World state.
    public void applyTo(OpenWorldState s) {
        s.initialized = initialized;
        s.seed = seed;
        s.playerX = playerX;
        s.playerY = playerY;
        s.length = length;
        s.lifetimeScore = lifetimeScore;
        s.currency = currency;
        s.xp = xp;
        s.level = level;
        s.exploredChunks.clear();
        for (long[] c : exploredChunks) {
            s.exploredChunks.add(OpenWorldState.packChunkKey((int) c[0], (int) c[1]));
        }
        s.discoveredLocations.clear();
        for (String[] r : discoveredLocations) {
            s.discoveredLocations.add(new OpenWorldState.LocationRecord(
                    parseInt(r[0]), parseInt(r[1]), r[2]));
        }
        s.worldModifications.clear();
        for (int[] m : worldModifications) {
            s.worldModifications.add(new OpenWorldState.WorldModification(m[0], m[1], m[2], m[3]));
        }
        s.npcStates.clear();
        for (String[] n : npcStates) {
            s.npcStates.add(new OpenWorldState.NpcState(n[3], parseInt(n[0]), parseInt(n[1]), parseInt(n[2])));
        }
        s.customMarkers.clear();
        for (String[] m : customMarkers) {
            if (m.length >= 3) {
                s.customMarkers.add(new OpenWorldState.CustomMarker(parseInt(m[0]), parseInt(m[1]), parseInt(m[2])));
            }
        }
        s.defeatedEncounters.clear();
        s.defeatedEncounters.addAll(defeatedEncounters);
    }

    // -----------------------------------------------------------------------
    // Serialization (line-oriented CSV)
    // -----------------------------------------------------------------------

    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("v1\n");
        sb.append(initialized ? 1 : 0).append(',').append(seed).append(',')
          .append(playerX).append(',').append(playerY).append(',')
          .append(length).append(',').append(lifetimeScore).append(',')
          .append(currency).append(',').append(xp).append(',').append(level)
          .append('\n');
        sb.append("explored");
        for (long[] c : exploredChunks) sb.append(',').append(c[0]).append(':').append(c[1]);
        sb.append('\n');
        sb.append("locations");
        for (String[] r : discoveredLocations) sb.append(',').append(r[0]).append(':').append(r[1]).append(':').append(r[2]);
        sb.append('\n');
        sb.append("mods");
        for (int[] m : worldModifications) sb.append(',').append(m[0]).append(':').append(m[1]).append(':').append(m[2]).append(':').append(m[3]);
        sb.append('\n');
        sb.append("npcs");
        for (String[] n : npcStates) sb.append(',').append(n[0]).append(':').append(n[1]).append(':').append(n[2]).append(':').append(n[3]);
        sb.append('\n');
        sb.append("markers");
        for (String[] m : customMarkers) sb.append(',').append(m[0]).append(':').append(m[1]).append(':').append(m[2]);
        sb.append('\n');
        sb.append("defeated");
        for (String id : defeatedEncounters) sb.append(',').append(id);
        return sb.toString();
    }

    public static OpenWorldSaveData fromCsv(String data) {
        OpenWorldSaveData d = new OpenWorldSaveData();
        if (data == null || data.isEmpty()) return d;
        String[] lines = data.split("\n");
        if (lines.length < 2) return d;
        // Header line + scalar line.
        String[] sc = lines[1].split(",");
        if (sc.length >= 5) {
            d.initialized = sc[0].equals("1");
            d.seed = parseLong(sc[1]);
            d.playerX = parseInt(sc[2]);
            d.playerY = parseInt(sc[3]);
            d.length = parseInt(sc[4]);
            if (sc.length >= 9) {
                d.lifetimeScore = parseInt(sc[5]);
                d.currency = parseInt(sc[6]);
                d.xp = parseInt(sc[7]);
                d.level = parseInt(sc[8]);
            }
        }
        for (int i = 2; i < lines.length; i++) {
            String[] parts = lines[i].split(",", -1);
            if (parts.length < 1) continue;
            String tag = parts[0];
            if (tag.equals("explored")) {
                for (int j = 1; j < parts.length; j++) {
                    String[] c = parts[j].split(":");
                    if (c.length == 2) d.exploredChunks.add(new long[]{parseLong(c[0]), parseLong(c[1])});
                }
            } else if (tag.equals("locations")) {
                for (int j = 1; j < parts.length; j++) {
                    String[] c = parts[j].split(":", -1);
                    if (c.length == 3) d.discoveredLocations.add(new String[]{c[0], c[1], c[2]});
                }
            } else if (tag.equals("mods")) {
                for (int j = 1; j < parts.length; j++) {
                    String[] c = parts[j].split(":", -1);
                    if (c.length == 4) d.worldModifications.add(new int[]{
                            parseInt(c[0]), parseInt(c[1]), parseInt(c[2]), parseInt(c[3])});
                }
            } else if (tag.equals("npcs")) {
                for (int j = 1; j < parts.length; j++) {
                    String[] c = parts[j].split(":", -1);
                    if (c.length == 4) d.npcStates.add(new String[]{c[0], c[1], c[2], c[3]});
                }
            } else if (tag.equals("markers")) {
                for (int j = 1; j < parts.length; j++) {
                    String[] c = parts[j].split(":", -1);
                    if (c.length == 3) d.customMarkers.add(new String[]{c[0], c[1], c[2]});
                }
            } else if (tag.equals("defeated")) {
                for (int j = 1; j < parts.length; j++) {
                    if (!parts[j].isEmpty()) d.defeatedEncounters.add(parts[j]);
                }
            }
        }
        return d;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }
}