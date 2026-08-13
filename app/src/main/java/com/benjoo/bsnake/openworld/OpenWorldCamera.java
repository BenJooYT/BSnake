package com.benjoo.bsnake.openworld;

// ---------------------------------------------------------------------------
// OPEN WORLD CAMERA
// ---------------------------------------------------------------------------
// World-space camera for Open World. The camera is anchored in world
// coordinates and follows the snake. All world-to-screen conversion is
// camera-relative (world -> camera -> screen) so precision is preserved at
// large distances, and Open World coordinates are never mixed directly with
// Android screen coordinates throughout the rendering code.
//
// This is kept isolated from the Arcade/Classic camera behavior, which is
// unchanged.
// ---------------------------------------------------------------------------
public class OpenWorldCamera {

    // Camera anchor in world coordinates (can be any sign / magnitude).
    public float x;
    public float y;

    // Configurable: how much the camera trails behind the snake head. Kept
    // strictly below 1 so the camera always eases toward the head instead of
    // snapping to it mid-glide (which caused a visible stutter when the blend
    // previously reached exactly 1.0 as the interpolation factor climbed).
    public static final float FOLLOW_LAG = 0.22f;

    public OpenWorldCamera() {
        x = 0f;
        y = 0f;
    }

    // Targets the camera at the snake head (world float position). Uses a
    // constant smoothing factor rather than one tied to the head's per-frame
    // interpolation so the camera never snaps discontinuously and the motion
    // stays fluid frame to frame.
    public void follow(float headWorldX, float headWorldY, float t) {
        float blend = FOLLOW_LAG;
        x += (headWorldX - x) * blend;
        y += (headWorldY - y) * blend;
    }

    // Centers the camera immediately (used on reset / load).
    public void snapTo(float headWorldX, float headWorldY) {
        x = headWorldX;
        y = headWorldY;
    }

    // --- World -> screen conversions (camera-relative) ---
    // Each takes the current cell size and viewport so this class stays free of
    // the shared game state and can be reused anywhere.
    //
    // The chain is deliberately: World -> Camera -> Screen.
    //   World   coordinates:    unbounded integer world space
    //   Camera  coordinates:    world minus the camera anchor (small numbers,
    //                            so screen math never sees huge world values)
    //   Screen  coordinates:    Android pixels

    // World x -> camera-relative x (world minus the camera anchor).
    public float cameraXOf(float worldX) {
        return worldX - x;
    }

    // World y -> camera-relative y (world minus the camera anchor).
    public float cameraYOf(float worldY) {
        return worldY - y;
    }

    // Screen x for a world x, given the screen origin and cell size.
    public float screenX(float worldX, float boardLeft, float centerCellsX, float cellSize) {
        return boardLeft + cellSize * (centerCellsX + cameraXOf(worldX));
    }

    // Screen y for a world y, given the screen origin and cell size.
    public float screenY(float worldY, float boardTop, float centerCellsY, float cellSize) {
        return boardTop + cellSize * (centerCellsY + cameraYOf(worldY));
    }
}

