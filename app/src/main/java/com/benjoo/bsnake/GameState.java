package com.benjoo.bsnake;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.RectF;

import java.util.ArrayList;

public class GameState {

    enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEADERBOARD, SETTINGS }
    State currentState = State.MENU;

    enum SortMode { HIGH_SCORE, RECENT }
    SortMode sortMode = SortMode.HIGH_SCORE;

    static class ScoreEntry {
        int score;
        long timestamp;
        String difficulty;

        ScoreEntry(int score, long timestamp, String difficulty) {
            this.score = score;
            this.timestamp = timestamp;
            this.difficulty = difficulty;
        }
    }

    ArrayList<Point> snake = new ArrayList<>();
    ArrayList<Point> prevSnake = new ArrayList<>();
    ArrayList<Point> foods = new ArrayList<>();
    int dirX = 1, dirY = 0;
    ArrayList<Point> inputQueue = new ArrayList<>();
    int cellSize = 40;
    int uiCellSize = 40;
    int cols = 32, rows = 32;
    float viewportWidthCells, viewportHeightCells;
    float boardLeft, boardTop;
    float cameraX, cameraY;
    boolean cameraInitialized;

    final long[] speedDelays = {220, 150, 90};
    final String[] speedLabels = {"EASY", "NORMAL", "HARD"};
    int speedIndex = 1;
    long tickDelay;

    int lastScore = 0;
    int screenW, screenH;

    RectF startBtn, speedBtn, settingsBtn, leaderboardBtn, exitBtn;
    RectF headInputBtn, bodyInputBtn, settingsApplyBtn, settingsBackBtn;
    RectF resumeBtn, pauseMenuBtn;
    RectF restartBtn, overMenuBtn;
    RectF lbSortBtn, lbBackBtn;
    RectF pauseIcon;

    float downX, downY;
    int headColor = Color.GREEN;
    int bodyColor = Color.GREEN;
    String headHex = "#00FF00";
    String bodyHex = "#00FF00";
    int editingColor = -1;

    GameState() {
        tickDelay = speedDelays[speedIndex];
    }

    void configureBoard() {
        uiCellSize = Math.max(16, screenW / 20);
        cellSize = Math.max(8, screenW / 10);
        viewportWidthCells = screenW / (float) cellSize;
        viewportHeightCells = screenH / (float) cellSize;
        boardLeft = 0;
        boardTop = 0;
    }

    void layoutButtons() {
        float bw = Math.min(screenW * 0.7f, 420);
        float bh = uiCellSize * 1.5f;
        float gap = uiCellSize * 0.4f;
        float cx = screenW / 2f;
        float startY = screenH * 0.40f;

        startBtn = makeBtn(cx, startY, bw, bh);
        speedBtn = makeBtn(cx, startY + bh + gap, bw, bh);
        settingsBtn = makeBtn(cx, startY + (bh + gap) * 2, bw, bh);
        leaderboardBtn = makeBtn(cx, startY + (bh + gap) * 3, bw, bh);
        exitBtn = makeBtn(cx, startY + (bh + gap) * 4, bw, bh);

        headInputBtn = makeBtn(cx, screenH * 0.36f, bw, bh);
        bodyInputBtn = makeBtn(cx, screenH * 0.48f, bw, bh);
        settingsApplyBtn = makeBtn(cx, screenH * 0.67f, bw, bh);
        settingsBackBtn = makeBtn(cx, screenH * 0.80f, bw, bh);

        resumeBtn = makeBtn(cx, screenH * 0.5f, bw, bh);
        pauseMenuBtn = makeBtn(cx, screenH * 0.5f + bh + gap, bw, bh);

        restartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        overMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        lbSortBtn = makeBtn(cx, screenH * 0.20f, bw, bh * 0.8f);
        lbBackBtn = makeBtn(cx, screenH * 0.88f, bw, bh);

        float iconSize = uiCellSize * 1.1f;
        pauseIcon = new RectF(screenW - iconSize - 16, 16, screenW - 16, 16 + iconSize);
    }

    static RectF makeBtn(float cx, float cy, float w, float h) {
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }
}
