package com.benjoo.bsnake;

import android.graphics.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * TEMPORARY debug autoplayer. Feeds directions through the same
 * GameView.sendSwipe() path a human touch uses, so the real engine,
 * collision, scoring and rendering all run unmodified.
 */
class GameBot {

    private final GameState state;
    private final GameView view;

    private int cols, rows;
    private int[] cycle;          // Hamiltonian cycle index per cell (fallback route)
    private int lastLoggedScore = -1;

    // ----- challenge completion task state -----
    private final HashMap<GameState.Fruit, Long> foodSpawnMs = new HashMap<>();
    private boolean perfectTimingActive = false;
    private int perfectTimingMaxAgeMs = 0;
    private boolean edgeWalkerActive = false;
    private int forbiddenDir = -1;
    private int lastChallengesDone = -1;

    GameBot(GameState state, GameView view) {
        this.state = state;
        this.view = view;
    }

    // ----- cycle (safety fallback) -----

    private void buildCycle() {
        cols = state.cols;
        rows = state.rows;
        cycle = new int[cols * rows];
        int idx = 0;
        // Columns 1..cols-1 are covered row by row in a boustrophedon,
        // column 0 is the vertical return leg. Requires an even row count.
        for (int y = 0; y < rows; y++) {
            if (y % 2 == 0) for (int x = 1; x < cols; x++) cycle[y * cols + x] = idx++;
            else for (int x = cols - 1; x >= 1; x--) cycle[y * cols + x] = idx++;
        }
        for (int y = rows - 1; y >= 0; y--) cycle[y * cols] = idx++;
    }

    // ----- helpers -----

    private int wrapX(int x) { return x < 0 ? cols - 1 : (x >= cols ? 0 : x); }
    private int wrapY(int y) { return y < 0 ? rows - 1 : (y >= rows ? 0 : y); }
    private int id(int x, int y) { return y * cols + x; }

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    /** Cells occupied by the body, optionally ignoring the tail (it moves away). */
    private boolean[] occupancy(ArrayList<Point> body, boolean ignoreTail) {
        boolean[] occ = new boolean[cols * rows];
        int n = ignoreTail ? body.size() - 1 : body.size();
        for (int i = 0; i < n; i++) occ[id(body.get(i).x, body.get(i).y)] = true;
        markHazards(occ);
        return occ;
    }

    /** Marks boss body and live walls as blocked — both are lethal to the player. */
    private void markHazards(boolean[] blocked) {
        if (state.boss.alive) {
            // The boss HEAD is a valid target (hitting it damages the boss), so
            // only its body segments are lethal and block pathing.
            for (int i = 1; i < state.boss.body.size(); i++) {
                blocked[id(state.boss.body.get(i).x, state.boss.body.get(i).y)] = true;
            }
        }
        for (GameState.WallCell w : state.walls) {
            if (!w.dying) blocked[id(w.x, w.y)] = true;
        }
    }

    /** BFS distance from a start cell to every reachable free cell (-1 if unreachable). */
    private int[] bfs(int start, boolean[] blocked) {
        int[] dist = new int[cols * rows];
        for (int i = 0; i < dist.length; i++) dist[i] = -1;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[start] = 0;
        q.add(start);
        while (!q.isEmpty()) {
            int c = q.poll();
            int cx = c % cols, cy = c / cols;
            for (int d = 0; d < 4; d++) {
                int nx = wrapX(cx + DX[d]), ny = wrapY(cy + DY[d]);
                int ni = id(nx, ny);
                if (blocked[ni] || dist[ni] != -1) continue;
                dist[ni] = dist[c] + 1;
                q.add(ni);
            }
        }
        return dist;
    }

    private boolean growsInto(int nx, int ny, GameState.SnakeData sd) {
        if (sd.growthPending > 0 || state.bossGrowthPending > 0) return true;
        for (GameState.Fruit f : state.foods) if (f.x == nx && f.y == ny) return true;
        return false;
    }

    /**
     * A move is safe when, after taking it, the head can still reach its own tail
     * through free cells — the standard guarantee that the snake is not sealing
     * itself into a pocket.
     */
    private boolean isSafe(ArrayList<Point> body, int nx, int ny, GameState.SnakeData sd) {
        boolean grows = growsInto(nx, ny, sd);
        ArrayList<Point> next = new ArrayList<>();
        next.add(new Point(nx, ny));
        int keep = grows ? body.size() : body.size() - 1;
        for (int i = 0; i < keep; i++) next.add(body.get(i));
        int head = id(nx, ny);
        for (int i = 1; i < next.size(); i++) {
            if (id(next.get(i).x, next.get(i).y) == head) return false;  // hits itself
        }
        if (next.size() < 3) return true;
        Point tail = next.get(next.size() - 1);
        boolean[] blocked = new boolean[cols * rows];
        for (int i = 0; i < next.size() - 1; i++) blocked[id(next.get(i).x, next.get(i).y)] = true;
        markHazards(blocked);
        blocked[head] = false;
        return bfs(head, blocked)[id(tail.x, tail.y)] != -1;
    }

    /**
     * One-step lookahead on top of {@link #isSafe}: a move is an escape-route
     * move when, after taking it, the snake still has at least one further move
     * that keeps its head connected to its own tail. This stops the bot from
     * picking a move that is survivable this tick but corners it the next,
     * i.e. it always leaves itself a way to keep looping around.
     */
    private boolean keepsEscapeRoute(ArrayList<Point> body, int nx, int ny, GameState.SnakeData sd) {
        boolean grows = growsInto(nx, ny, sd);
        ArrayList<Point> next = new ArrayList<>();
        next.add(new Point(nx, ny));
        int keep = grows ? body.size() : body.size() - 1;
        for (int i = 0; i < keep; i++) next.add(body.get(i));
        int head = id(nx, ny);
        for (int i = 1; i < next.size(); i++) {
            if (id(next.get(i).x, next.get(i).y) == head) return false;
        }
        if (next.size() < 3) return true;
        Point hd = next.get(0), tail = next.get(next.size() - 1);

        // The snake must still be able to reach its own tail from this new head.
        boolean[] baseBlocked = new boolean[cols * rows];
        for (int i = 0; i < next.size() - 1; i++) baseBlocked[id(next.get(i).x, next.get(i).y)] = true;
        baseBlocked[head] = false;
        if (bfs(head, baseBlocked)[id(tail.x, tail.y)] == -1) return false;

        // And there must be at least one next move from here that keeps the tail
        // reachable, so the snake is never immediately cornered.
        for (int d = 0; d < 4; d++) {
            int nx2 = wrapX(hd.x + DX[d]), ny2 = wrapY(hd.y + DY[d]);
            int head2 = id(nx2, ny2);
            if (baseBlocked[head2]) continue;
            ArrayList<Point> next2 = new ArrayList<>();
            next2.add(new Point(nx2, ny2));
            int keep2 = next.size() - 1;                    // assume tail keeps moving
            for (int i = 0; i < keep2; i++) next2.add(next.get(i));
            boolean collide = false;
            for (int i = 1; i < next2.size(); i++) {
                if (id(next2.get(i).x, next2.get(i).y) == head2) { collide = true; break; }
            }
            if (collide) continue;
            if (next2.size() < 3) return true;
            Point tail2 = next2.get(next2.size() - 1);
            boolean[] blocked2 = new boolean[cols * rows];
            for (int i = 0; i < next2.size() - 1; i++) blocked2[id(next2.get(i).x, next2.get(i).y)] = true;
            blocked2[head2] = false;
            if (bfs(head2, blocked2)[id(tail2.x, tail2.y)] != -1) return true;
        }
        return false;
    }

    private GameState.Fruit pickFood() {
        GameState.Fruit best = null;
        if (perfectTimingActive) {
            // PERFECT_TIMING: hunt only fruit we can still reach inside its
            // freshness window (spawn + max age). Among those, take the one
            // that can be caught soonest, breaking ties toward the freshest.
            GameState.SnakeData sd = state.snakes[0];
            if (!sd.body.isEmpty()) {
                Point head = sd.body.get(0);
                long now = System.currentTimeMillis();
                long bestTravel = Long.MAX_VALUE, bestAge = Long.MAX_VALUE;
                for (GameState.Fruit f : state.foods) {
                    if (f.type == GameState.FruitType.HEAL) continue;
                    Long spawn = foodSpawnMs.get(f);
                    long age = spawn == null ? 0 : now - spawn;
                    long remaining = perfectTimingMaxAgeMs - age;
                    if (remaining <= 0) continue;                 // already too old
                    int dist = distTo(head, new Point(f.x, f.y));
                    if (dist < 0) continue;                       // not reachable
                    long travel = dist * state.tickDelay;
                    if (travel >= remaining) continue;            // can't make it in time
                    if (travel < bestTravel || (travel == bestTravel && age < bestAge)) {
                        bestTravel = travel;
                        bestAge = age;
                        best = f;
                    }
                }
            }
            if (best == null) {
                // Nothing catchable in time — fall back to any normal fruit.
                for (GameState.Fruit f : state.foods) {
                    if (f.type == GameState.FruitType.HEAL) continue;
                    if (best == null) best = f;
                }
            }
        } else {
            for (GameState.Fruit f : state.foods) {
                if (f.type == GameState.FruitType.HEAL) continue;   // no score, skip
                if (best == null) best = f;
            }
        }
        if (best == null && !state.foods.isEmpty()) best = state.foods.get(0);
        return best;
    }

    /**
     * Shortest path head -> food. Walks a virtual snake down the whole path and
     * only returns the first step if, once the food is eaten, that virtual snake
     * can still reach its own tail. Returns null when the food is not worth it.
     */
    private int[] pathFirstStepIfSurvivable(ArrayList<Point> body, Point head,
                                            GameState.Fruit food, GameState.SnakeData sd) {
        return pathFirstStepTo(body, head, new Point(food.x, food.y), sd, true);
    }

    /**
     * Generalized version of {@link #pathFirstStepIfSurvivable}: returns the first
     * step of a path to an arbitrary target cell, provided that walking the whole
     * path still leaves the virtual snake able to reach its own tail afterwards.
     * {@code growsAtEnd} models whether reaching the target lengthens the snake
     * (food grows; boss head and trail do not).
     */
    private int[] pathFirstStepTo(ArrayList<Point> body, Point head, Point target,
                                  GameState.SnakeData sd, boolean growsAtEnd) {
        boolean[] blocked = occupancy(body, true);
        blocked[id(head.x, head.y)] = false;
        int[] dist = bfs(id(head.x, head.y), blocked);
        int targetId = id(target.x, target.y);
        if (dist[targetId] < 0) return null;

        // Walk the path backwards from the target to recover it in order.
        ArrayList<Integer> path = new ArrayList<>();
        int cur = targetId;
        while (cur != id(head.x, head.y)) {
            path.add(0, cur);
            int cx = cur % cols, cy = cur / cols;
            int prev = -1;
            for (int d = 0; d < 4; d++) {
                int px = wrapX(cx + DX[d]), py = wrapY(cy + DY[d]);
                int pi = id(px, py);
                if (dist[pi] == dist[cur] - 1) { prev = pi; break; }
            }
            if (prev < 0) return null;
            cur = prev;
        }
        if (path.isEmpty()) return null;

        // Virtual walk: grow only on the final cell when the target grows the snake.
        ArrayList<Point> virt = new ArrayList<>(body);
        int pending = sd.growthPending + state.bossGrowthPending;
        for (int i = 0; i < path.size(); i++) {
            int c = path.get(i);
            virt.add(0, new Point(c % cols, c / cols));
            boolean grows = (growsAtEnd && i == path.size() - 1) || pending > 0;
            if (pending > 0) pending--;
            if (!grows) virt.remove(virt.size() - 1);
        }
        if (virt.size() < 3) return getStep(head, path.get(0));

        Point vHead = virt.get(0), vTail = virt.get(virt.size() - 1);
        boolean[] vBlocked = new boolean[cols * rows];
        for (int i = 0; i < virt.size() - 1; i++) vBlocked[id(virt.get(i).x, virt.get(i).y)] = true;
        markHazards(vBlocked);
        vBlocked[id(vHead.x, vHead.y)] = false;
        int[] vDist = bfs(id(vHead.x, vHead.y), vBlocked);
        if (vDist[id(vTail.x, vTail.y)] < 0) return null;

        // Also insist on enough open room to unwind, not just a tail touch.
        int room = 0;
        for (int v : vDist) if (v >= 0) room++;
        if (room < virt.size() / 2) return null;

        return getStep(head, path.get(0));
    }

    /** Direction from head to an adjacent cell, honouring wrap. */
    private int[] getStep(Point head, int cell) {
        int nx = cell % cols, ny = cell / cols;
        for (int d = 0; d < 4; d++) {
            if (wrapX(head.x + DX[d]) == nx && wrapY(head.y + DY[d]) == ny) {
                return new int[]{DX[d], DY[d]};
            }
        }
        return null;
    }

    /**
     * Picks the bot's next move toward the highest-value goal that is reachable
     * and still survivable: first the boss head (to kill the boss), then the
     * nearest trail fruit, then a scored food. Returns null if no goal is worth
     * chasing, so the caller falls back to a pure survival move.
     */
    private int[] chooseGoal(ArrayList<Point> body, Point head, GameState.SnakeData sd) {
        // 1) Boss head — highest value: scores per hit and works toward defeating
        //    the boss. Only pursued when the whole approach is survivable.
        if (state.boss.alive && !state.boss.body.isEmpty()) {
            Point bh = state.boss.body.get(0);
            int[] step = pathFirstStepTo(body, head, bh, sd, false);
            if (step != null) return step;
        }

        // 2) Trail fruit — nearest reachable one that is safe to collect.
        if (!state.bossTrail.isEmpty()) {
            int[] bestStep = null;
            int bestDist = Integer.MAX_VALUE;
            for (GameState.BossTrailCell tc : state.bossTrail) {
                Point t = new Point(tc.x, tc.y);
                int d = distTo(head, t);
                if (d < 0 || d >= bestDist) continue;
                int[] step = pathFirstStepTo(body, head, t, sd, false);
                if (step != null) { bestDist = d; bestStep = step; }
            }
            if (bestStep != null) return bestStep;
        }

        // 3) Scored food.
        GameState.Fruit food = pickFood();
        if (food != null) {
            return pathFirstStepIfSurvivable(body, head, food, sd);
        }
        return null;
    }

    // ----- challenge completion task -----
    //
    // Steers the autoplayer toward finishing the Arcade run's active
    // objectives: avoids the DIRECTION_LOCK direction, hugs the border for
    // EDGE_WALKER, and times fruit chases for PERFECT_TIMING.

    private void challengeTask() {
        perfectTimingActive = false;
        perfectTimingMaxAgeMs = 0;
        edgeWalkerActive = false;
        forbiddenDir = -1;
        long now = System.currentTimeMillis();
        int done = 0;
        for (ActiveChallenge ac : state.activeChallenges) {
            if (ac.completed) done++;
            if (ac.completed || ac.failed) continue;
            switch (ac.def.type) {
                case PERFECT_TIMING:
                    perfectTimingActive = true;
                    perfectTimingMaxAgeMs = ac.def.params[0];
                    break;
                case EDGE_WALKER:
                    edgeWalkerActive = true;
                    break;
                case DIRECTION_LOCK:
                    forbiddenDir = ac.forbiddenDir;
                    break;
                default:
                    break;
            }
        }
        if (done != lastChallengesDone) {
            lastChallengesDone = done;
            android.util.Log.i("BOT", "challenges done=" + done + " of " + state.activeChallenges.size());
        }
        // Remember when each fruit first appeared so PERFECT_TIMING can judge
        // freshness; drop anything that has been eaten.
        for (GameState.Fruit f : state.foods) {
            if (!foodSpawnMs.containsKey(f)) foodSpawnMs.put(f, now);
        }
        if (!foodSpawnMs.isEmpty()) {
            ArrayList<GameState.Fruit> gone = new ArrayList<>();
            for (GameState.Fruit f : foodSpawnMs.keySet()) {
                if (!state.foods.contains(f)) gone.add(f);
            }
            for (GameState.Fruit f : gone) foodSpawnMs.remove(f);
        }
    }

    /** Cells from a point to another through free cells; -1 when unreachable. */
    private int distTo(Point from, Point to) {
        boolean[] blocked = occupancy(state.snakes[0].body, true);
        blocked[id(from.x, from.y)] = false;
        return bfs(id(from.x, from.y), blocked)[id(to.x, to.y)];
    }

    /** How many steps from the nearest board edge. */
    private int distToBorder(int x, int y) {
        return Math.min(Math.min(x, cols - 1 - x), Math.min(y, rows - 1 - y));
    }

    /** 0=up 1=right 2=down 3=left, matching DIRECTION_LOCK. */
    private int dirToIndex(int dx, int dy) {
        if (dx == 0 && dy == -1) return 0;
        if (dx == 1 && dy == 0) return 1;
        if (dx == 0 && dy == 1) return 2;
        if (dx == -1 && dy == 0) return 3;
        return -1;
    }

    // ----- called once per engine tick -----

    void step() {
        GameState.SnakeData sd = state.snakes[0];
        if (!sd.alive || sd.body.isEmpty()) return;
        if (cycle == null || cols != state.cols || rows != state.rows) buildCycle();
        challengeTask();

        if (state.score != lastLoggedScore) {
            lastLoggedScore = state.score;
            android.util.Log.i("BOT", "score=" + state.score + " len=" + sd.body.size());
        }

        ArrayList<Point> body = new ArrayList<>(sd.body);
        Point head = body.get(0);

        // Candidate moves, minus the illegal reversal.
        ArrayList<int[]> safe = new ArrayList<>();     // {dx, dy, nx, ny}
        ArrayList<int[]> legal = new ArrayList<>();
        for (int d = 0; d < 4; d++) {
            int dx = DX[d], dy = DY[d];
            if (body.size() > 1 && dx == -sd.dirX && dy == -sd.dirY) continue;
            if (forbiddenDir >= 0 && dirToIndex(dx, dy) == forbiddenDir) continue;
            int nx = wrapX(head.x + dx), ny = wrapY(head.y + dy);
            boolean grows = growsInto(nx, ny, sd);
            boolean[] occ = occupancy(body, !grows);
            if (occ[id(nx, ny)]) continue;
            legal.add(new int[]{dx, dy, nx, ny});
            if (isSafe(body, nx, ny, sd)) safe.add(new int[]{dx, dy, nx, ny});
        }
        if (legal.isEmpty()) return;

        int[] chosen = null;

        // Chase the highest-value reachable goal only if walking the WHOLE path
        // there still leaves the snake able to reach its own tail afterwards.
        // Priority: boss head (kills the boss) -> trail fruit -> food.
        if (!safe.isEmpty()) {
            chosen = chooseGoal(body, head, sd);
        }

        // Not worth chasing: stay alive and keep the board open by taking the
        // safe move that best preserves an escape route, then the most reachable
        // space, breaking ties toward the tail (and toward the border when
        // EDGE_WALKER is active).
        if (chosen == null && !safe.isEmpty()) {
            Point tail = body.get(body.size() - 1);
            int bestRoom = -1, bestBorder = Integer.MAX_VALUE, bestTailDist = Integer.MAX_VALUE;
            boolean bestEscape = false;
            for (int[] c : safe) {
                boolean[] blocked = occupancy(body, true);
                int[] dist = bfs(id(c[2], c[3]), blocked);
                int room = 0;
                for (int v : dist) if (v >= 0) room++;
                int td = dist[id(tail.x, tail.y)];
                if (td < 0) td = Integer.MAX_VALUE;
                int border = edgeWalkerActive ? distToBorder(c[2], c[3]) : 0;
                boolean escape = keepsEscapeRoute(body, c[2], c[3], sd);
                boolean better;
                if (escape != bestEscape) better = escape;
                else if (room != bestRoom) better = room > bestRoom;
                else if (edgeWalkerActive) better = border < bestBorder
                        || (border == bestBorder && td < bestTailDist);
                else better = td < bestTailDist;
                if (better) {
                    bestEscape = escape; bestRoom = room; bestBorder = border;
                    bestTailDist = td; chosen = c;
                }
            }
        }

        // Nothing provably safe — take the move with the most open space.
        if (chosen == null) {
            int best = -1;
            for (int[] c : legal) {
                boolean[] blocked = occupancy(body, true);
                int[] dist = bfs(id(c[2], c[3]), blocked);
                int room = 0;
                for (int v : dist) if (v >= 0) room++;
                if (room > best) { best = room; chosen = c; }
            }
        }

        if (chosen != null && !(chosen[0] == sd.dirX && chosen[1] == sd.dirY)) {
            // The bot plans in world coordinates using sd.dirX/dirY, but while
            // a MIRROR fruit is active the engine inverts the input direction.
            // Send the negated swipe so the actual movement matches the plan.
            boolean mirrored = sd.mirrorUntilMs > System.currentTimeMillis();
            view.sendSwipe(mirrored ? -chosen[0] : chosen[0],
                           mirrored ? -chosen[1] : chosen[1]);
        }
    }
}
