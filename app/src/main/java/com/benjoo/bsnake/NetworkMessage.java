package com.benjoo.bsnake;

import android.graphics.Point;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

class NetworkMessage {

    static String hello(int color) {
        try {
            return new JSONObject()
                    .put("type", "hello")
                    .put("color", color)
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

    static String startGame() {
        try {
            return new JSONObject()
                    .put("type", "start")
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String input(int dx, int dy, int tick) {
        try {
            return new JSONObject()
                    .put("type", "input")
                    .put("dx", dx)
                    .put("dy", dy)
                    .put("tick", tick)
                    .toString() + "\n";
        } catch (Exception e) { return null; }
    }

    static String state(ArrayList<Point> snakes0, ArrayList<Point> snakes1,
                        int score0, int score1, int dirX0, int dirY0, int dirX1, int dirY1,
                        ArrayList<Point> foods, GameState.BossFruit boss,
                        ArrayList<GameState.BossTrailCell> trail, int tick) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "state");
            msg.put("tick", tick);
            JSONArray snArr = new JSONArray();
            snArr.put(bodyToJson(snakes0));
            snArr.put(bodyToJson(snakes1));
            msg.put("snakes", snArr);
            JSONArray scArr = new JSONArray();
            scArr.put(score0);
            scArr.put(score1);
            msg.put("scores", scArr);
            JSONArray drArr = new JSONArray();
            drArr.put(new JSONArray().put(dirX0).put(dirY0));
            drArr.put(new JSONArray().put(dirX1).put(dirY1));
            msg.put("dirs", drArr);
            JSONArray fdArr = new JSONArray();
            for (Point f : foods) fdArr.put(new JSONArray().put(f.x).put(f.y));
            msg.put("foods", fdArr);
            if (boss != null && boss.alive) {
                JSONObject bj = new JSONObject();
                bj.put("x", boss.x);
                bj.put("y", boss.y);
                bj.put("hp", boss.hp);
                bj.put("lastMoveTick", boss.lastMoveTick);
                msg.put("boss", bj);
            }
            JSONArray trArr = new JSONArray();
            for (GameState.BossTrailCell tc : trail) {
                trArr.put(new JSONArray().put(tc.x).put(tc.y).put(tc.createdAtTick));
            }
            msg.put("trail", trArr);
            return msg.toString() + "\n";
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
