package com.benjoo.bsnake;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;

// Handles all SharedPreferences read/write for the leaderboard and color customization.
public class PersistenceManager {

    private final Context context;

    public PersistenceManager(Context context) {
        this.context = context;
    }

    // Deserialize the CSV-stored leaderboard into ScoreEntry objects.
    // Legacy entries (2-field) receive difficulty "UNKNOWN".
    ArrayList<GameState.ScoreEntry> loadScores() {
        return loadScores(0); // Default to Arcade for backward compatibility
    }

    ArrayList<GameState.ScoreEntry> loadScores(int gameModeOrdinal) {
        String key = gameModeOrdinal == 1 ? "leaderboard_classic" : "leaderboard_arcade";
        ArrayList<GameState.ScoreEntry> list = loadScoresFromKey(key);
        // Migration: if Arcade and no scores yet, check legacy key
        if (gameModeOrdinal == 0 && list.isEmpty()) {
            list = loadScoresFromKey("leaderboard");
            if (!list.isEmpty()) {
                saveScoresList(list, key);
            }
        }
        return list;
    }

    private ArrayList<GameState.ScoreEntry> loadScoresFromKey(String key) {
        ArrayList<GameState.ScoreEntry> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        String data = prefs.getString(key, "");
        if (!data.isEmpty()) {
            String[] parts = data.split(",");
            for (String part : parts) {
                String[] kv = part.split(":", -1);
                if (kv.length == 2 || kv.length == 3) {
                    try {
                        int score = Integer.parseInt(kv[0]);
                        long time = Long.parseLong(kv[1]);
                        String difficulty = kv.length == 3 && !kv[2].isEmpty() ? kv[2] : "UNKNOWN";
                        list.add(new GameState.ScoreEntry(score, time, difficulty));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return list;
    }

    private void saveScoresList(ArrayList<GameState.ScoreEntry> list, String key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).score).append(":").append(list.get(i).timestamp)
                    .append(":").append(list.get(i).difficulty);
        }
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        prefs.edit().putString(key, sb.toString()).apply();
    }

    // Add a new score, sort descending, cap at 20 entries, and serialize back to CSV.
    void saveScore(int score, String difficulty) {
        saveScore(score, difficulty, 0); // Default to Arcade
    }

    void saveScore(int score, String difficulty, int gameModeOrdinal) {
        String key = gameModeOrdinal == 1 ? "leaderboard_classic" : "leaderboard_arcade";
        ArrayList<GameState.ScoreEntry> list = loadScoresFromKey(key);
        // Migration: if Arcade and no new-key scores yet, merge legacy scores
        if (gameModeOrdinal == 0 && list.isEmpty()) {
            list = loadScoresFromKey("leaderboard");
        }
        list.add(new GameState.ScoreEntry(score, System.currentTimeMillis(), difficulty));
        Collections.sort(list, (a, b) -> Integer.compare(b.score, a.score));
        if (list.size() > 20) {
            list = new ArrayList<>(list.subList(0, 20));
        }
        saveScoresList(list, key);
    }

    // Restore previously saved snake head/body colors (defaults to green).
    void loadColors(GameState state) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        state.headColor = prefs.getInt("headColor", Color.GREEN);
        state.bodyColor = prefs.getInt("bodyColor", Color.GREEN);
    }

    // Persist the current head/body color integers.
    void saveColors(int headColor, int bodyColor) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("headColor", headColor).putInt("bodyColor", bodyColor).apply();
    }

    // Restore previously saved camera mode (defaults to CLASSIC_ZOOM).
    void loadCameraMode(GameState state) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        int ord = prefs.getInt("cameraMode", 0);
        GameState.CameraMode[] modes = GameState.CameraMode.values();
        if (ord >= 0 && ord < modes.length) state.cameraMode = modes[ord];
    }

    // Persist the current camera mode ordinal.
    void saveCameraMode(GameState.CameraMode mode) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("cameraMode", mode.ordinal()).apply();
    }

    // Restore previously saved volume levels (defaults to 1.0 = full).
    void loadVolumes(GameState state) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        state.musicVolume = prefs.getFloat("musicVolume", 0.25f);
        state.sfxVolume = prefs.getFloat("sfxVolume", 0.5f);
    }

    // Persist current volume levels.
    void saveVolumes(float musicVolume, float sfxVolume) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        prefs.edit().putFloat("musicVolume", musicVolume).putFloat("sfxVolume", sfxVolume).apply();
    }

    // Save the last played game mode ordinal.
    void saveGameMode(int gameModeOrdinal) {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("lastPlayedMode", gameModeOrdinal).apply();
    }

    // Load the last played game mode ordinal into state. Returns -1 if none saved.
    int loadGameMode() {
        SharedPreferences prefs = context.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        return prefs.getInt("lastPlayedMode", -1);
    }

    // Parse a #RRGGBB (or RRGGBB) hex string to an integer color; returns null on invalid input.
    Integer parseHexColor(String value) {
        String hex = value == null ? "" : value.trim();
        if (!hex.matches("#?[0-9A-Fa-f]{6}")) return null;
        if (!hex.startsWith("#")) hex = "#" + hex;
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
