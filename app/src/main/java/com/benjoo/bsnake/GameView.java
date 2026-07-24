package com.benjoo.bsnake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
    Thread thread;
    SurfaceHolder holder;
    volatile boolean running = false;
    Paint paint = new Paint();
    ArrayList<Point> snake = new ArrayList<>();
    ArrayList<Point> prevSnake = new ArrayList<>();
    Point food = new Point();
    int dirX = 1, dirY = 0;
    int cellSize = 40;
    int cols = 10, rows = 10;
    Random rand = new Random();
    long tickDelay = 150; // ms

    float downX, downY;

    public GameView(Context context) { super(context); init(); }
    public GameView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        paint.setAntiAlias(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        int w = getWidth();
        int h = getHeight();
        cellSize = Math.max(16, w / 20);
        cols = Math.max(10, w / cellSize);
        rows = Math.max(10, h / cellSize);
        resetGame();
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    private void resetGame() {
        snake.clear();
        int startX = cols / 2;
        int startY = rows / 2;
        for (int i = 0; i < 3; i++) {
            snake.add(new Point(startX - i, startY));
        }
        dirX = 1; dirY = 0;
        placeFood();
        prevSnake.clear();
        for (Point p : snake) prevSnake.add(new Point(p));
    }

    private void placeFood() {
        int fx, fy;
        boolean coll;
        do {
            fx = rand.nextInt(cols);
            fy = rand.nextInt(rows);
            coll = false;
            for (Point p : snake) {
                if (p.x == fx && p.y == fy) { coll = true; break; }
            }
        } while (coll);
        food.x = fx; food.y = fy;
    }

    @Override
    public void run() {
        long lastTick = System.currentTimeMillis();
        while (running) {
            long now = System.currentTimeMillis();
            if (now - lastTick >= tickDelay) {
                update();
                lastTick = now;
                now = System.currentTimeMillis();
            }
            float t = Math.min(1f, (now - lastTick) / (float) tickDelay);
            draw(t);
            try { Thread.sleep(8); } catch (InterruptedException e) { }
        }
    }

    private void update() {
        prevSnake.clear();
        for (Point p : snake) prevSnake.add(new Point(p));

        Point head = snake.get(0);
        int nx = head.x + dirX;
        int ny = head.y + dirY;
        if (nx < 0) nx = cols - 1;
        if (nx >= cols) nx = 0;
        if (ny < 0) ny = rows - 1;
        if (ny >= rows) ny = 0;
        for (Point p : snake) {
            if (p.x == nx && p.y == ny) {
                resetGame();
                return;
            }
        }
        snake.add(0, new Point(nx, ny));
        if (nx == food.x && ny == food.y) {
            placeFood();
            // tail didn't move this tick — duplicate its old spot so lengths line up
            prevSnake.add(new Point(prevSnake.get(prevSnake.size() - 1)));
        } else {
            snake.remove(snake.size() - 1);
        }
    }

    private void draw(float t) {
        if (!holder.getSurface().isValid()) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;
        canvas.drawColor(Color.BLACK);
        paint.setColor(Color.GREEN);
        int n = Math.min(snake.size(), prevSnake.size());
        for (int i = 0; i < n; i++) {
            Point cur = snake.get(i);
            Point prev = prevSnake.get(i);

            float dx = cur.x - prev.x;
            float dy = cur.y - prev.y;
            boolean wrapped = Math.abs(dx) > 1 || Math.abs(dy) > 1;

            float px, py;
            if (wrapped) {
                px = cur.x * cellSize;
                py = cur.y * cellSize;
            } else {
                px = (prev.x + dx * t) * cellSize;
                py = (prev.y + dy * t) * cellSize;
            }
            canvas.drawRect(px, py, px + cellSize - 1, py + cellSize - 1, paint);
        }
        paint.setColor(Color.RED);
        float cx = food.x * cellSize + cellSize / 2f;
        float cy = food.y * cellSize + cellSize / 2f;
        float r = cellSize / 2f - 4;
        canvas.drawCircle(cx, cy, Math.max(4, r), paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        canvas.drawText("Score: " + (snake.size() - 3), 10, 40, paint);
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                float upX = event.getX();
                float upY = event.getY();
                float dx = upX - downX;
                float dy = upY - downY;
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0 && dirX != -1) { dirX = 1; dirY = 0; }
                    else if (dx < 0 && dirX != 1) { dirX = -1; dirY = 0; }
                } else {
                    if (dy > 0 && dirY != -1) { dirX = 0; dirY = 1; }
                    else if (dy < 0 && dirY != 1) { dirX = 0; dirY = -1; }
                }
                break;
        }
        return true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException e) { }
    }
}
