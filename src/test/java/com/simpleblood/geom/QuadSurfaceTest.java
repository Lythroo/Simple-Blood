package com.simpleblood.geom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadSurfaceTest {

    private static final double EPS = 1.0e-9;

    private static double[] floorCorners(double y) {
        return new double[]{0, y, 0, 1, y, 0, 0, y, 1};
    }

    private static QuadSurface floor(double y, QuadSurface.Texels texels) {
        return new QuadSurface(floorCorners(y), new double[]{0, 1, 0}, new double[]{0, 0, 1, 0, 0, 1}, texels);
    }

    @Test
    void dropFallingOnADrawnTexelStopsOnTheFace() {
        QuadSurface s = floor(0.5, QuadSurface.Texels.SOLID);
        double[] out = new double[7];
        double t = s.firstHit(0.3, 1.5, 0.7, 0.3, -0.5, 0.7, out);
        assertEquals(0.5, t, EPS);
        assertEquals(0.3, out[0], EPS);
        assertEquals(0.5, out[1], EPS);
        assertEquals(0.7, out[2], EPS);
        assertEquals(1.0, out[4], EPS);
        assertEquals(0, out[6], EPS);
    }

    @Test
    void seeThroughTexelLetsTheDropGoOn() {
        QuadSurface s = floor(0.5, (face, u, v) -> u < 0.5);
        double[] out = new double[7];
        assertEquals(-1, s.firstHit(0.8, 1.5, 0.5, 0.8, -0.5, 0.5, out), EPS);
        assertEquals(0.5, s.firstHit(0.2, 1.5, 0.5, 0.2, -0.5, 0.5, out), EPS);
    }

    @Test
    void missingTheFaceOrComingFromBehindIsNoHit() {
        QuadSurface s = floor(0.5, QuadSurface.Texels.SOLID);
        double[] out = new double[7];
        assertEquals(-1, s.firstHit(1.5, 1.5, 0.5, 1.5, -0.5, 0.5, out), EPS);
        assertEquals(-1, s.firstHit(0.5, -0.5, 0.5, 0.5, 1.5, 0.5, out), EPS);
        assertEquals(-1, s.firstHit(0.5, 1.5, 0.5, 0.5, 0.6, 0.5, out), EPS);
        assertEquals(-1, s.firstHit(0.2, 0.5, 0.5, 0.8, 0.5, 0.5, out), EPS);
    }

    @Test
    void theNearestDrawnFaceAlongTheWayWins() {
        double[] corners = new double[18];
        System.arraycopy(floorCorners(0.8), 0, corners, 0, 9);
        System.arraycopy(floorCorners(0.2), 0, corners, 9, 9);
        QuadSurface s = new QuadSurface(corners, new double[]{0, 1, 0, 0, 1, 0},
                new double[]{0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1}, (face, u, v) -> face == 1);
        double[] out = new double[7];
        double t = s.firstHit(0.5, 1.0, 0.5, 0.5, 0.0, 0.5, out);
        assertEquals(0.8, t, EPS);
        assertEquals(1, out[6], EPS);
        QuadSurface both = new QuadSurface(corners, new double[]{0, 1, 0, 0, 1, 0},
                new double[]{0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1}, QuadSurface.Texels.SOLID);
        assertEquals(0.2, both.firstHit(0.5, 1.0, 0.5, 0.5, 0.0, 0.5, out), EPS);
        assertEquals(0, out[6], EPS);
    }

    @Test
    void turnedFaceMapsItsTextureByEdgeWeights() {
        QuadSurface s = new QuadSurface(new double[]{0, 0, 0, 0, 0, 1, 0, 1, 0}, new double[]{-1, 0, 0},
                new double[]{0, 0, 1, 0, 0, 1}, (face, u, v) -> v > 0.5);
        double[] out = new double[7];
        assertEquals(-1, s.firstHit(-1, 0.25, 0.5, 1, 0.25, 0.5, out), EPS);
        assertEquals(0.5, s.firstHit(-1, 0.75, 0.5, 1, 0.75, 0.5, out), EPS);
        assertEquals(-1, out[3], EPS);
    }

    @Test
    void distanceToTheNearestDrawnTexel() {
        QuadSurface s = floor(0.0, (face, u, v) -> u < 0.25);
        assertEquals(0.3, s.distanceToDrawn(0.1, 0.3, 0.5, 16), 0.04);
        assertTrue(s.distanceToDrawn(0.75, 0, 0.5, 16) > 0.45);
    }

    @Test
    void mismatchedArraysAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuadSurface(new double[9], new double[6], new double[6], null));
    }
}
