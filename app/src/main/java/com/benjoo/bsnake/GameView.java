package com.benjoo.bsnake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {

    // ---- game state machine ----
    private enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEADERBOARD }
    private State state = State.MENU;

    private enum SortMode { HIGH_SCORE, RECENT }
    private SortMode sortMode = SortMode.HIGH_SCORE;

    private static class ScoreEntry {
        int score;
        long timestamp;
        String difficulty;

        ScoreEntry(int score, long timestamp, String difficulty) {
            this.score = score;
            this.timestamp = timestamp;
            this.difficulty = difficulty;
        }
    }

    Thread thread;
    SurfaceHolder holder;
    volatile boolean running = false;
    Paint paint = new Paint();
    ArrayList<Point> snake = new ArrayList<>();
    ArrayList<Point> prevSnake = new ArrayList<>();
    ArrayList<Point> foods = new ArrayList<>();
    int dirX = 1, dirY = 0;
    ArrayList<Point> inputQueue = new ArrayList<>();
    int cellSize = 40;
    int cols = 10, rows = 10;
    Random rand = new Random();

    // speed options (index into arrays below)
    private final long[] speedDelays = {220, 150, 90};
    private final String[] speedLabels = {"EASY", "NORMAL", "HARD"};
    private int speedIndex = 1;
    long tickDelay = speedDelays[speedIndex];

    int lastScore = 0;
    int screenW, screenH;

    // menu buttons
    RectF startBtn, speedBtn, settingsBtn, leaderboardBtn, exitBtn;
    // pause overlay buttons
    RectF resumeBtn, pauseMenuBtn;
    // game over overlay buttons
    RectF restartBtn, overMenuBtn;
    // leaderboard overlay buttons
    RectF lbSortBtn, lbBackBtn;
    // in-game pause icon
    RectF pauseIcon;

    float downX, downY;
    int headColor = Color.GREEN;
    int bodyColor = Color.GREEN;

    public GameView(Context context) { super(context); init(); loadColors(); }
    public GameView(Context context, AttributeSet attrs) { super(context, attrs); init(); loadColors(); }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        paint.setAntiAlias(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenW = getWidth();
        screenH = getHeight();
        cellSize = Math.max(16, screenW / 20);
        cols = Math.max(10, screenW / cellSize);
        rows = Math.max(10, screenH / cellSize);
        layoutButtons();
        state = State.MENU;
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    // -------------------- layout --------------------

    private void layoutButtons() {
        float bw = Math.min(screenW * 0.7f, 420);
        float bh = cellSize * 1.5f;
        float gap = cellSize * 0.4f;
        float cx = screenW / 2f;
        float startY = screenH * 0.40f;

        startBtn = makeBtn(cx, startY, bw, bh);
        speedBtn = makeBtn(cx, startY + bh + gap, bw, bh);
        settingsBtn = makeBtn(cx, startY + (bh + gap) * 2, bw, bh);
        leaderboardBtn = makeBtn(cx, startY + (bh + gap) * 3, bw, bh);
        exitBtn = makeBtn(cx, startY + (bh + gap) * 4, bw, bh);

        resumeBtn = makeBtn(cx, screenH * 0.5f, bw, bh);
        pauseMenuBtn = makeBtn(cx, screenH * 0.5f + bh + gap, bw, bh);

        restartBtn = makeBtn(cx, screenH * 0.56f, bw, bh);
        overMenuBtn = makeBtn(cx, screenH * 0.56f + bh + gap, bw, bh);

        lbSortBtn = makeBtn(cx, screenH * 0.20f, bw, bh * 0.8f);
        lbBackBtn = makeBtn(cx, screenH * 0.88f, bw, bh);

        float iconSize = cellSize * 1.1f;
        pauseIcon = new RectF(screenW - iconSize - 16, 16, screenW - 16, 16 + iconSize);
    }

    private RectF makeBtn(float cx, float cy, float w, float h) {
        return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    // -------------------- game logic --------------------

    private void resetGame() {
        snake.clear();
        int startX = cols / 2;
        int startY = rows / 2;
        for (int i = 0; i < 3; i++) {
            snake.add(new Point(startX - i, startY));
        }
        dirX = 1; dirY = 0;
        inputQueue.clear();
        placeFood();
        prevSnake.clear();
        for (Point p : snake) prevSnake.add(new Point(p));
    }

    private int getTargetFoodCount(int score) {
        int count = 1;
        for (int fc = 6; fc >= 2; fc--) {
            double threshold = 50 * Math.pow(2, fc - 2);
            if (score >= threshold) {
                count = fc;
                break;
            }
        }
        return Math.min(6, count);
    }

    private void placeFood() {
        foods.clear();
        int target = getTargetFoodCount(snake.size() - 3);
        while (foods.size() < target) {
            spawnFood();
        }
    }

    private void spawnFood() {
        int fx, fy;
        boolean coll;
        do {
            fx = rand.nextInt(cols);
            fy = rand.nextInt(rows);
            coll = false;
            for (Point p : snake) {
                if (p.x == fx && p.y == fy) { coll = true; break; }
            }
            if (!coll) {
                for (Point f : foods) {
                    if (f.x == fx && f.y == fy) { coll = true; break; }
                }
            }
        } while (coll);
        foods.add(new Point(fx, fy));
    }

    @Override
    public void run() {
        long lastTick = System.currentTimeMillis();
        while (running) {
            long now = System.currentTimeMillis();
            if (state == State.PLAYING && now - lastTick >= tickDelay) {
                update();
                lastTick = now;
                now = System.currentTimeMillis();
            } else if (state != State.PLAYING) {
                lastTick = now;
            }
            float t = Math.min(1f, (now - lastTick) / (float) tickDelay);
            draw(t);
            try { Thread.sleep(8); } catch (InterruptedException e) { }
        }
    }

    private void update() {
        if (!inputQueue.isEmpty()) {
            Point nextDir = inputQueue.remove(0);
            dirX = nextDir.x;
            dirY = nextDir.y;
        }

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
                lastScore = snake.size() - 3;
                if (lastScore >= 0) {
                    saveScore(lastScore);
                }
                state = State.GAME_OVER;
                return;
            }
        }
        snake.add(0, new Point(nx, ny));

        Point eatenFood = null;
        for (Point f : foods) {
            if (nx == f.x && ny == f.y) {
                eatenFood = f;
                break;
            }
        }

        if (eatenFood != null) {
            foods.remove(eatenFood);
            // tail didn't move this tick -- duplicate its old spot so lengths line up
            prevSnake.add(new Point(prevSnake.get(prevSnake.size() - 1)));
        } else {
            snake.remove(snake.size() - 1);
        }

        int targetFoodCount = getTargetFoodCount(snake.size() - 3);
        while (foods.size() < targetFoodCount) {
            spawnFood();
        }
    }

    // -------------------- drawing --------------------

    private void draw(float t) {
        if (!holder.getSurface().isValid()) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;
        canvas.drawColor(Color.BLACK);

        switch (state) {
            case MENU:
                drawMenu(canvas);
                break;
            case LEADERBOARD:
                drawLeaderboard(canvas);
                break;
            case PLAYING:
                drawGameField(canvas, t);
                drawPauseIcon(canvas);
                break;
            case PAUSED:
                drawGameField(canvas, 1f);
                drawDim(canvas);
                drawPausedOverlay(canvas);
                break;
            case GAME_OVER:
                drawGameField(canvas, 1f);
                drawDim(canvas);
                drawGameOverOverlay(canvas);
                break;
        }

        holder.unlockCanvasAndPost(canvas);
    }

    private void drawGameField(Canvas canvas, float t) {
        int n = Math.min(snake.size(), prevSnake.size());
        for (int i = 0; i < n; i++) {
            paint.setColor(i == 0 ? headColor : bodyColor);
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
        for (Point f : foods) {
            float cx = f.x * cellSize + cellSize / 2f;
            float cy = f.y * cellSize + cellSize / 2f;
            float r = cellSize / 2f - 4;
            canvas.drawCircle(cx, cy, Math.max(4, r), paint);
        }
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("Score: " + (snake.size() - 3), 10, 40, paint);
    }

    private void drawDim(Canvas canvas) {
        paint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRect(0, 0, screenW, screenH, paint);
    }

    private void drawMenu(Canvas canvas) {
        drawTitle(canvas, screenH * 0.24f);
        drawButton(canvas, startBtn, "START");
        drawButton(canvas, speedBtn, "SPEED: " + speedLabels[speedIndex]);
        drawButton(canvas, settingsBtn, "SETTINGS");
        drawButton(canvas, leaderboardBtn, "LEADERBOARD");
        drawButton(canvas, exitBtn, "EXIT");
    }

    private void openSettings() {
        Context context = getContext();
        if (!(context instanceof Activity)) return;

        Activity activity = (Activity) context;
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        EditText headInput = makeColorInput(activity, "Head color (#RRGGBB)", headColor);
        EditText bodyInput = makeColorInput(activity, "Body color (#RRGGBB)", bodyColor);
        fields.addView(headInput);
        fields.addView(bodyInput);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("SNAKE COLORS")
                .setView(fields)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("APPLY", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer newHeadColor = parseHexColor(headInput.getText().toString());
            Integer newBodyColor = parseHexColor(bodyInput.getText().toString());
            if (newHeadColor == null || newBodyColor == null) {
                if (newHeadColor == null) headInput.setError("Use a hex color such as #00FF00");
                if (newBodyColor == null) bodyInput.setError("Use a hex color such as #00FF00");
                return;
            }
            headColor = newHeadColor;
            bodyColor = newBodyColor;
            saveColors();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private EditText makeColorInput(Activity activity, String hint, int color) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
        return input;
    }

    private Integer parseHexColor(String value) {
        String hex = value == null ? "" : value.trim();
        if (!hex.matches("#?[0-9A-Fa-f]{6}")) return null;
        if (!hex.startsWith("#")) hex = "#" + hex;
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void loadColors() {
        SharedPreferences prefs = getContext().getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        headColor = prefs.getInt("headColor", Color.GREEN);
        bodyColor = prefs.getInt("bodyColor", Color.GREEN);
    }

    private void saveColors() {
        SharedPreferences prefs = getContext().getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("headColor", headColor).putInt("bodyColor", bodyColor).apply();
    }

    private void drawLeaderboard(Canvas canvas) {
        drawCenteredText(canvas, "LEADERBOARD", screenW / 2f, screenH * 0.10f, 60, Color.GREEN, true);
        drawButton(canvas, lbSortBtn, "SORT: " + (sortMode == SortMode.HIGH_SCORE ? "HIGH SCORE" : "RECENT"));

        ArrayList<ScoreEntry> list = loadScores();
        Collections.sort(list, (a, b) -> {
            if (sortMode == SortMode.HIGH_SCORE) {
                return Integer.compare(b.score, a.score);
            } else {
                return Long.compare(b.timestamp, a.timestamp);
            }
        });

        if (list.isEmpty()) {
            drawCenteredText(canvas, "No scores yet!", screenW / 2f, screenH * 0.5f, 40, Color.WHITE, false);
        } else {
            float startY = screenH * 0.30f;
            float rowH = cellSize * 1.2f;
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            int maxShow = Math.min(list.size(), 8);
            for (int i = 0; i < maxShow; i++) {
                ScoreEntry entry = list.get(i);
                String dateStr = sdf.format(new Date(entry.timestamp));
                String text = (i + 1) + ".  Score: " + entry.score + "   "
                        + entry.difficulty + "   " + dateStr;
                drawCenteredText(canvas, text, screenW / 2f, startY + i * rowH, 32, Color.WHITE, false);
            }
        }

        drawButton(canvas, lbBackBtn, "BACK");
    }

    private ArrayList<ScoreEntry> loadScores() {
        ArrayList<ScoreEntry> list = new ArrayList<>();
        Context ctx = getContext();
        if (ctx == null) return list;
        SharedPreferences prefs = ctx.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
        String data = prefs.getString("leaderboard", "");
        if (!data.isEmpty()) {
            String[] parts = data.split(",");
            for (String part : parts) {
                String[] kv = part.split(":", -1);
                if (kv.length == 2 || kv.length == 3) {
                    try {
                        int score = Integer.parseInt(kv[0]);
                        long time = Long.parseLong(kv[1]);
                        // Older entries did not store difficulty.
                        String difficulty = kv.length == 3 && !kv[2].isEmpty()
                                ? kv[2] : "UNKNOWN";
                        list.add(new ScoreEntry(score, time, difficulty));
                    } catch (NumberFormatException e) {
                        // ignore malformed
                    }
                }
            }
        }
        return list;
    }

    private void saveScore(int score) {
        ArrayList<ScoreEntry> list = loadScores();
        list.add(new ScoreEntry(score, System.currentTimeMillis(), speedLabels[speedIndex]));
        Collections.sort(list, (a, b) -> Integer.compare(b.score, a.score));
        if (list.size() > 20) {
            list = new ArrayList<>(list.subList(0, 20));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i).score).append(":").append(list.get(i).timestamp)
                    .append(":").append(list.get(i).difficulty);
        }
        Context ctx = getContext();
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences("BSnakePrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("leaderboard", sb.toString()).apply();
        }
    }

    private void drawPausedOverlay(Canvas canvas) {
        drawCenteredText(canvas, "PAUSED", screenW / 2f, screenH * 0.36f, 64, Color.GREEN, true);
        drawButton(canvas, resumeBtn, "RESUME");
        drawButton(canvas, pauseMenuBtn, "MENU");
    }

    private void drawGameOverOverlay(Canvas canvas) {
        drawCenteredText(canvas, "GAME OVER", screenW / 2f, screenH * 0.36f, 60, Color.RED, true);
        drawCenteredText(canvas, "SCORE: " + lastScore, screenW / 2f, screenH * 0.36f + 56, 40, Color.WHITE, false);
        drawButton(canvas, restartBtn, "RESTART");
        drawButton(canvas, overMenuBtn, "MENU");
    }

    private void drawPauseIcon(Canvas canvas) {
        // two small green bars, blocky like the snake body
        paint.setColor(Color.GREEN);
        float w = pauseIcon.width();
        float h = pauseIcon.height();
        float barW = w * 0.28f;
        canvas.drawRect(pauseIcon.left, pauseIcon.top, pauseIcon.left + barW, pauseIcon.top + h, paint);
        canvas.drawRect(pauseIcon.right - barW, pauseIcon.top, pauseIcon.right, pauseIcon.top + h, paint);
    }

    private void drawTitle(Canvas canvas, float y) {
        drawCenteredText(canvas, "SNAKE", screenW / 2f, y, 96, Color.GREEN, true);
    }

    /** Draws a blocky button matching the snake's cell style: a black gap border
     *  around a solid green rect (same look as the 1px gaps between snake segments),
     *  with centered white label text. */
    private void drawButton(Canvas canvas, RectF r, String label) {
        paint.setColor(Color.GREEN);
        canvas.drawRect(r.left, r.top, r.right - 2, r.bottom - 2, paint);
        drawCenteredText(canvas, label, r.centerX(), r.centerY(), 36, Color.BLACK, true);
    }

    private void drawCenteredText(Canvas canvas, String text, float cx, float cy, float size, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, cx, textY, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // -------------------- input --------------------

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
                handleTouchUp(upX, upY);
                break;
        }
        return true;
    }

    private void handleTouchUp(float upX, float upY) {
        switch (state) {
            case MENU:
                if (contains(startBtn, upX, upY)) {
                    resetGame();
                    state = State.PLAYING;
                } else if (contains(speedBtn, upX, upY)) {
                    speedIndex = (speedIndex + 1) % speedLabels.length;
                    tickDelay = speedDelays[speedIndex];
                } else if (contains(settingsBtn, upX, upY)) {
                    openSettings();
                } else if (contains(leaderboardBtn, upX, upY)) {
                    state = State.LEADERBOARD;
                } else if (contains(exitBtn, upX, upY)) {
                    exitApp();
                }
                break;

            case LEADERBOARD:
                if (contains(lbSortBtn, upX, upY)) {
                    sortMode = (sortMode == SortMode.HIGH_SCORE) ? SortMode.RECENT : SortMode.HIGH_SCORE;
                } else if (contains(lbBackBtn, upX, upY)) {
                    state = State.MENU;
                }
                break;

            case PAUSED:
                if (contains(resumeBtn, upX, upY)) {
                    state = State.PLAYING;
                } else if (contains(pauseMenuBtn, upX, upY)) {
                    state = State.MENU;
                }
                break;

            case GAME_OVER:
                if (contains(restartBtn, upX, upY)) {
                    resetGame();
                    state = State.PLAYING;
                } else if (contains(overMenuBtn, upX, upY)) {
                    state = State.MENU;
                }
                break;

            case PLAYING:
                float dx = upX - downX;
                float dy = upY - downY;
                boolean smallMove = Math.abs(dx) < 20 && Math.abs(dy) < 20;
                if (smallMove && contains(pauseIcon, upX, upY)) {
                    state = State.PAUSED;
                    return;
                }

                int ndx = 0, ndy = 0;
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) { ndx = 1; ndy = 0; }
                    else if (dx < 0) { ndx = -1; ndy = 0; }
                } else {
                    if (dy > 0) { ndx = 0; ndy = 1; }
                    else if (dy < 0) { ndx = 0; ndy = -1; }
                }

                if (ndx != 0 || ndy != 0) {
                    Point lastDir = inputQueue.isEmpty() ? new Point(dirX, dirY) : inputQueue.get(inputQueue.size() - 1);
                    if (!(ndx == -lastDir.x && ndy == -lastDir.y) && inputQueue.size() < 2) {
                        if (!(ndx == lastDir.x && ndy == lastDir.y)) {
                            inputQueue.add(new Point(ndx, ndy));
                        }
                    }
                }
                break;
        }
    }

    private boolean contains(RectF r, float x, float y) {
        return r != null && r.contains(x, y);
    }

    private void exitApp() {
        Context ctx = getContext();
        if (ctx instanceof Activity) {
            ((Activity) ctx).finish();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        screenW = width;
        screenH = height;
        layoutButtons();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException e) { }
    }
}
