package com.benjoo.bsnake.openworld;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// JVM tests for the Open World world-space camera: camera-relative world-to-
// screen conversion, precision stability at large/negative world coordinates,
// and follow/snap behaviour. These exercise the cases the Phase 1 prompt calls
// out (camera follows the snake, conversion stays stable at large distances).
public class OpenWorldCameraTest {

    @Test public void snapTo_anchorsCameraAtWorldPosition() {
        OpenWorldCamera cam = new OpenWorldCamera();
        cam.snapTo(-12345f, 9876f);
        assertEquals(-12345f, cam.x, 0f);
        assertEquals(9876f, cam.y, 0f);
    }

    @Test public void follow_movesCameraTowardsHead() {
        OpenWorldCamera cam = new OpenWorldCamera();
        cam.snapTo(0, 0);
        cam.follow(100f, 0f, 0f);
        float blend = Math.min(1f, OpenWorldCamera.FOLLOW_LAG + 0f);
        assertEquals(100f * blend, cam.x, 1e-3f);
        assertEquals(0f, cam.y, 0f);
    }

    @Test public void cameraRelative_conversionIsPrecisePastOrigin() {
        OpenWorldCamera cam = new OpenWorldCamera();
        cam.snapTo(0, 0);
        // At the origin the camera-relative offset equals the world offset.
        assertEquals(7f, cam.cameraXOf(7f), 1e-4f);
        assertEquals(-7f, cam.cameraYOf(-7f), 1e-4f);
        // The camera-relative value is small even for huge world coordinates.
        cam.snapTo(1_000_000, -1_000_000);
        assertEquals(-32f, cam.cameraXOf(999_968f), 1e-3f);
        assertEquals(32f, cam.cameraYOf(-999_968f), 1e-3f);
    }

    @Test public void screenX_screenY_stableAcrossLargeDistances() {
        // A cell at a fixed offset from the camera must map to identical
        // screen pixels whether the camera is at the origin or deep in the
        // negative/positive world, because all screen math is camera-relative.
        float boardLeft = 100f;
        float boardTop = 200f;
        float centerCellsX = 10f;
        float centerCellsY = 8f;
        float cell = 24f;

        OpenWorldCamera camA = new OpenWorldCamera();
        camA.snapTo(0, 0);
        OpenWorldCamera camB = new OpenWorldCamera();
        camB.snapTo(-5_000_000, 5_000_000);

        for (int[] offset : new int[][]{{0, 0}, {-1, -1}, {31, 31}, {-32, 32}}) {
            int dx = offset[0];
            int dy = offset[1];
            assertEquals("x offset " + dx,
                    camA.screenX(dx, boardLeft, centerCellsX, cell),
                    camB.screenX(camB.x + dx, boardLeft, centerCellsX, cell), 0.1f);
            assertEquals("y offset " + dy,
                    camA.screenY(dy, boardTop, centerCellsY, cell),
                    camB.screenY(camB.y + dy, boardTop, centerCellsY, cell), 0.1f);
        }
    }

    @Test public void worldToScreen_mapsWorldIntoCursorCoords() {
        OpenWorldCamera cam = new OpenWorldCamera();
        cam.snapTo(0, 0);
        for (int w : new int[]{-1_000_000, -32, -1, 0, 1, 32, 1_000_000}) {
            float sx = cam.screenX(w, 100f, 10f, 24f);
            assertEquals(100f + 24f * (10f + w), sx, 1e-2f);
        }
    }

    @Test public void screenX_screenY_areMonotonic() {
        OpenWorldCamera cam = new OpenWorldCamera();
        cam.snapTo(50, 50);
        float prev = cam.screenX(20, 0, 0, 16);
        for (int w = 21; w <= 80; w++) {
            float cur = cam.screenX(w, 0, 0, 16);
            assertTrue("x must increase with world x", cur > prev);
            prev = cur;
        }
    }
}