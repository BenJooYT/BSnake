package com.benjoo.bsnake;

import android.graphics.Point;

import java.util.Random;

public class SnakeEngine {

    private final GameState state;
    private final PersistenceManager persistence;
    private final Random rand = new Random();

    public SnakeEngine(GameState state, PersistenceManager persistence) {
        this.state = state;
        this.persistence = persistence;
    }

    void resetGame() {
        state.snake.clear();
        int startX = state.cols / 2;
        int startY = state.rows / 2;
        for (int i = 0; i < 3; i++) {
            state.snake.add(new Point(startX - i, startY));
        }
        state.cameraX = startX;
        state.cameraY = startY;
        state.cameraInitialized = true;
        state.dirX = 1;
        state.dirY = 0;
        state.inputQueue.clear();
        placeFood();
        state.prevSnake.clear();
        for (Point p : state.snake) state.prevSnake.add(new Point(p));
    }

    void update() {
        if (!state.inputQueue.isEmpty()) {
            Point nextDir = state.inputQueue.remove(0);
            state.dirX = nextDir.x;
            state.dirY = nextDir.y;
        }

        state.prevSnake.clear();
        for (Point p : state.snake) state.prevSnake.add(new Point(p));

        Point head = state.snake.get(0);
        int nx = head.x + state.dirX;
        int ny = head.y + state.dirY;
        boolean teleported = nx < 0 || nx >= state.cols || ny < 0 || ny >= state.rows;
        if (nx < 0) nx = state.cols - 1;
        if (nx >= state.cols) nx = 0;
        if (ny < 0) ny = state.rows - 1;
        if (ny >= state.rows) ny = 0;
        if (teleported) {
            state.cameraX = nx;
            state.cameraY = ny;
        }
        for (Point p : state.snake) {
            if (p.x == nx && p.y == ny) {
                state.lastScore = state.snake.size() - 3;
                if (state.lastScore >= 0) {
                    persistence.saveScore(state.lastScore, state.speedLabels[state.speedIndex]);
                }
                state.currentState = GameState.State.GAME_OVER;
                return;
            }
        }
        state.snake.add(0, new Point(nx, ny));

        Point eatenFood = null;
        for (Point f : state.foods) {
            if (nx == f.x && ny == f.y) {
                eatenFood = f;
                break;
            }
        }

        if (eatenFood != null) {
            state.foods.remove(eatenFood);
            state.prevSnake.add(new Point(state.prevSnake.get(state.prevSnake.size() - 1)));
        } else {
            state.snake.remove(state.snake.size() - 1);
        }

        int targetFoodCount = getTargetFoodCount(state.snake.size() - 3);
        while (state.foods.size() < targetFoodCount) {
            spawnFood();
        }
    }

    private int getTargetFoodCount(int score) {
        for (int fc = 6; fc >= 2; fc--) {
            double threshold = 50 * Math.pow(2, fc - 2);
            if (score >= threshold) {
                return fc;
            }
        }
        return 1;
    }

    private void placeFood() {
        state.foods.clear();
        int target = getTargetFoodCount(state.snake.size() - 3);
        while (state.foods.size() < target) {
            spawnFood();
        }
    }

    private void spawnFood() {
        int fx, fy;
        boolean coll;
        do {
            fx = rand.nextInt(state.cols);
            fy = rand.nextInt(state.rows);
            coll = false;
            for (Point p : state.snake) {
                if (p.x == fx && p.y == fy) {
                    coll = true;
                    break;
                }
            }
            if (!coll) {
                for (Point f : state.foods) {
                    if (f.x == fx && f.y == fy) {
                        coll = true;
                        break;
                    }
                }
            }
        } while (coll);
        state.foods.add(new Point(fx, fy));
    }

}
