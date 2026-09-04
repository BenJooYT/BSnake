package com.benjoo.bsnake;

import android.graphics.Point;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

class NetworkMessage {

    static String hello(int headColor, int bodyColor, int screenW, int screenH) {
        try {
            return new JSONObject()
                    .put("type", "hello")
                    .put("color", headColor)
                    .put("bodyColor", bodyColor)
                    .put("screenW", screenW)
                    .put("screenH", screenH)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String modeSel(int mode) {
        try {
            return new JSONObject()
                    .put("type", "mode")
                    .put("mode", mode)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String ready(boolean isReady) {
        try {
            return new JSONObject()
                    .put("type", "ready")
                    .put("ready", isReady)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String startGame(int mode, int cols, int rows) {
        try {
            return new JSONObject()
                    .put("type", "start")
                    .put("mode", mode)
                    .put("cols", cols)
                    .put("rows", rows)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String clientState(ArrayList<Point> body, int dirX, int dirY, int score, boolean alive) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "clientState");
            msg.put("body", bodyToJson(body));
            msg.put("dirX", dirX);
            msg.put("dirY", dirY);
            msg.put("score", score);
            msg.put("alive", alive);
            return msg.toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String state(ArrayList<Point> snakes0,
                        int score0, int score1, int dirX0, int dirY0,
                        boolean alive0, boolean alive1,
                        ArrayList<GameState.Fruit> foods, GameState.BossSnake boss,
                        ArrayList<GameState.BossTrailCell> trail, int tick,
                        ArrayList<GameState.WallCell> walls,
                        ArrayList<Point> wallPreviewPositions, int wallPreviewStartTick,
                        boolean wallPreviewActive, int nextWallTick,
                        int headColor0, int headColor1, int bodyColor0, int bodyColor1,
                        ArrayList<GameState.MinionSnake> minions) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "state");
            msg.put("tick", tick);
            msg.put("snake", bodyToJson(snakes0));
            JSONArray scArr = new JSONArray();
            scArr.put(score0);
            scArr.put(score1);
            msg.put("scores", scArr);
            JSONArray alArr = new JSONArray();
            alArr.put(alive0);
            alArr.put(alive1);
            msg.put("alive", alArr);
            JSONArray drArr = new JSONArray();
            drArr.put(new JSONArray().put(dirX0).put(dirY0));
            msg.put("dirs", drArr);
            JSONArray hcArr = new JSONArray();
            hcArr.put(headColor0);
            hcArr.put(headColor1);
            msg.put("headColors", hcArr);
            JSONArray bcArr = new JSONArray();
            bcArr.put(bodyColor0);
            bcArr.put(bodyColor1);
            msg.put("bodyColors", bcArr);
            JSONArray fdArr = new JSONArray();
            for (GameState.Fruit f : foods) {
                fdArr.put(new JSONArray().put(f.x).put(f.y).put(f.type.ordinal()));
            }
            msg.put("foods", fdArr);
            if (boss != null && boss.alive) {
                JSONObject bj = new JSONObject();
                bj.put("body", bodyToJson(boss.body));
                bj.put("dirX", boss.dirX);
                bj.put("dirY", boss.dirY);
                bj.put("lastMoveTick", boss.lastMoveTick);
                bj.put("growthPending", boss.growthPending);
                bj.put("type", boss.type.ordinal());
                bj.put("storedFruits", boss.storedFruits);
                bj.put("phantomTangible", boss.phantomIsTangible ? 1 : 0);
                bj.put("phantomPhaseTick", boss.phantomPhaseTick);
                msg.put("boss", bj);
            }
            // Minions (SUMMONER)
            if (minions != null && !minions.isEmpty()) {
                JSONArray mj = new JSONArray();
                for (GameState.MinionSnake minion : minions) {
                    JSONObject mjo = new JSONObject();
                    mjo.put("body", bodyToJson(minion.body));
                    mjo.put("dirX", minion.dirX);
                    mjo.put("dirY", minion.dirY);
                    mjo.put("lastMoveTick", minion.lastMoveTick);
                    mj.put(mjo);
                }
                msg.put("minions", mj);
            }
            JSONArray trArr = new JSONArray();
            for (GameState.BossTrailCell tc : trail) {
                trArr.put(new JSONArray().put(tc.x).put(tc.y).put(tc.createdAtTick));
            }
            msg.put("trail", trArr);
            // Wall data
            if (walls != null && !walls.isEmpty()) {
                JSONArray wlArr = new JSONArray();
                for (GameState.WallCell w : walls) {
                    wlArr.put(new JSONArray().put(w.x).put(w.y).put(w.createdAtTick)
                            .put(w.dying ? 1 : 0).put(w.deathStartTick));
                }
                msg.put("walls", wlArr);
            }
            if (wallPreviewPositions != null) {
                JSONArray wpArr = new JSONArray();
                for (Point p : wallPreviewPositions) {
                    wpArr.put(new JSONArray().put(p.x).put(p.y));
                }
                msg.put("wallPP", wpArr);
            }
            msg.put("wallPST", wallPreviewStartTick);
            msg.put("wallPA", wallPreviewActive);
            msg.put("nextWT", nextWallTick);
            return msg.toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String bossHit() {
        try {
            return new JSONObject()
                    .put("type", "bossHit")
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String gameOver(int winner, int[] scores) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "gameOver");
            msg.put("winner", winner);
            JSONArray scArr = new JSONArray();
            for (int s : scores) scArr.put(s);
            msg.put("scores", scArr);
            return msg.toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String ping() {
        try {
            return new JSONObject()
                    .put("type", "ping")
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    // Host -> client: boss death cinematic started. Carries the rendered
    // snapshot so the remote and the host render the same scene.
    static String bossCinematic(float focusX, float focusY,
                                float cameraStartX, float cameraStartY,
                                int color, ArrayList<Point> bossBody) {
        try {
            return new JSONObject()
                    .put("type", "bossCinematic")
                    .put("focusX", focusX)
                    .put("focusY", focusY)
                    .put("camX", cameraStartX)
                    .put("camY", cameraStartY)
                    .put("color", color)
                    .put("bossBody", bodyToJson(bossBody))
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    // Host -> client: the post-boss card offer is ready (ids in offer order).
    // An empty id list means "skip, resume play".
    static String bossUpgrade(ArrayList<String> cardIds) {
        try {
            JSONArray arr = new JSONArray();
            if (cardIds != null) for (String id : cardIds) arr.put(id);
            return new JSONObject()
                    .put("type", "bossUpgrade")
                    .put("ids", arr)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    // Either player -> the other: the pick (index into the shared offer, -1 = skip).
    static String upgradePick(int index) {
        try {
            return new JSONObject()
                    .put("type", "upgradePick")
                    .put("index", index)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static JSONArray bodyToJson(ArrayList<Point> body) {
        JSONArray arr = new JSONArray();
        for (Point p : body) arr.put(new JSONArray().put(p.x).put(p.y));
        return arr;
    }

    static ArrayList<Point> jsonToBody(JSONArray arr) {
        ArrayList<Point> body = new ArrayList<>();
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pt = arr.getJSONArray(i);
                body.add(new Point(pt.getInt(0), pt.getInt(1)));
            }
        } catch (Exception e) { }
        return body;
    }
}
