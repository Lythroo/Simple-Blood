package com.simpleblood.geom;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientedBoxTest {

    private static final double EPS = 1.0e-9;
    private static final double S45 = Math.sin(Math.PI / 4), C45 = Math.cos(Math.PI / 4);

    private static OrientedBox straight(double cx, double cy, double cz, double hx, double hy, double hz) {
        return new OrientedBox(cx, cy, cz, 0, 0, 0, 1, hx, hy, hz);
    }

    @Test
    void segmentGoesInThroughTheFaceItMeetsFirst() {
        OrientedBox box = straight(10, 20, 30, 1, 0.5, 0.25);
        double[] n = new double[3];
        assertEquals(0.4, box.entry(5, 20, 30, 15, 20, 30, n), EPS);
        assertArrayEquals(new double[]{-1, 0, 0}, n, EPS);
        assertEquals(0.5, box.entry(10, 22.5, 30, 10, 18.5, 30, n), EPS);
        assertArrayEquals(new double[]{0, 1, 0}, n, EPS);
    }

    @Test
    void noEntryWhenItMissesStopsShortOrStartsInside() {
        OrientedBox box = straight(0, 0, 0, 1, 1, 1);
        assertEquals(-1, box.entry(-5, 2, 0, 5, 2, 0, null), EPS);
        assertEquals(-1, box.entry(-5, 0, 0, -2, 0, 0, null), EPS);
        assertEquals(-1, box.entry(0, 0, 0, 5, 0, 0, null), EPS);
        assertEquals(-1, box.entry(-5, 1.5, 0, 5, 1.5, 0, null), EPS);
        assertEquals(-1, box.entry(-5, 0, 0, -5, 0, 0, null), EPS);
    }

    @Test
    void lineComesOutOnTheFarSide() {
        OrientedBox box = straight(10, 20, 30, 1, 0.5, 0.25);
        assertEquals(0.6, box.exit(5, 20, 30, 15, 20, 30), EPS);
        assertEquals(1.2, box.exit(5, 20, 30, 10, 20, 30), EPS);
        assertEquals(-1, box.exit(5, 25, 30, 15, 25, 30), EPS);
        assertEquals(-1, box.exit(15, 20, 30, 20, 20, 30), EPS);
    }

    @Test
    void turnedBoxIsHitOnItsTurnedFaces() {
        OrientedBox box = new OrientedBox(0, 0, 0, 0, S45, 0, C45, 2, 0.5, 0.25);
        double[] d = new double[3];
        box.toWorldDir(1, 0, 0, d);
        assertArrayEquals(new double[]{0, 0, -1}, d, EPS);
        box.toWorldDir(0, 0, 1, d);
        assertArrayEquals(new double[]{1, 0, 0}, d, EPS);

        double[] n = new double[3];
        assertEquals(0.475, box.entry(-5, 0, 0, 5, 0, 0, n), EPS);
        assertArrayEquals(new double[]{-1, 0, 0}, n, EPS);
        assertEquals(0.3, box.entry(0, 0, -5, 0, 0, 5, n), EPS);
        assertArrayEquals(new double[]{0, 0, -1}, n, EPS);
        assertTrue(box.contains(0, 0, 1.9));
        assertFalse(box.contains(1.9, 0, 0));
    }

    @Test
    void pointsAndDirectionsTurnLikeTheQuaternion() {
        double qx = 1, qy = 2, qz = 3, qw = 4;
        OrientedBox box = new OrientedBox(1, -2, 3, qx, qy, qz, qw, 0.3, 0.4, 0.5);
        double len = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        Random rng = new Random(7);
        double[] w = new double[3], back = new double[3];
        for (int k = 0; k < 200; k++) {
            double x = rng.nextGaussian(), y = rng.nextGaussian(), z = rng.nextGaussian();
            double[] expected = rotate(qx / len, qy / len, qz / len, qw / len, x, y, z);
            box.toWorldDir(x, y, z, w);
            assertArrayEquals(expected, w, EPS);
            box.toLocalDir(w[0], w[1], w[2], back);
            assertArrayEquals(new double[]{x, y, z}, back, EPS);
            box.toWorldPoint(x, y, z, w);
            assertArrayEquals(new double[]{expected[0] + 1, expected[1] - 2, expected[2] + 3}, w, EPS);
            box.toLocalPoint(w[0], w[1], w[2], back);
            assertArrayEquals(new double[]{x, y, z}, back, EPS);
        }
    }

    @Test
    void nearestOnSurfaceFromInsideAndOutside() {
        OrientedBox box = straight(0, 0, 0, 1, 0.5, 0.25);
        double[] p = new double[3], n = new double[3];
        box.nearestOnSurface(0.2, 0.45, 0, p, n);
        assertArrayEquals(new double[]{0.2, 0.5, 0}, p, EPS);
        assertArrayEquals(new double[]{0, 1, 0}, n, EPS);
        box.nearestOnSurface(3, 0.1, -0.1, p, n);
        assertArrayEquals(new double[]{1, 0.1, -0.1}, p, EPS);
        assertArrayEquals(new double[]{1, 0, 0}, n, EPS);

        OrientedBox turned = new OrientedBox(0, 0, 0, 0, S45, 0, C45, 2, 0.5, 0.25);
        turned.nearestOnSurface(0.2, 0, 0.5, p, n);
        assertArrayEquals(new double[]{0.25, 0, 0.5}, p, EPS);
        assertArrayEquals(new double[]{1, 0, 0}, n, EPS);
    }

    @Test
    void entryPointLiesOnTheSurfaceWithOutsideBeforeAndInsideAfter() {
        Random rng = new Random(11);
        int hits = 0;
        double[] n = new double[3];
        for (int k = 0; k < 2000; k++) {
            OrientedBox box = new OrientedBox(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian(),
                    rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian(),
                    0.05 + rng.nextDouble(), 0.05 + rng.nextDouble(), 0.05 + rng.nextDouble());
            double ax = rng.nextGaussian() * 3, ay = rng.nextGaussian() * 3, az = rng.nextGaussian() * 3;
            double bx = rng.nextGaussian() * 3, by = rng.nextGaussian() * 3, bz = rng.nextGaussian() * 3;
            double t = box.entry(ax, ay, az, bx, by, bz, n);
            if (t < 0) continue;
            hits++;
            double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double step = 1.0e-6 / len;
            assertFalse(box.contains(ax + dx * (t - step), ay + dy * (t - step), az + dz * (t - step)), "just before the entry is outside");
            assertTrue(box.contains(ax + dx * (t + step), ay + dy * (t + step), az + dz * (t + step)), "just after the entry is inside");
            assertTrue(n[0] * dx + n[1] * dy + n[2] * dz < 0);
            assertEquals(1, Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]), EPS);
        }
        assertTrue(hits > 50, "enough random segments hit a box to mean something: " + hits);
    }

    @Test
    void stepOutLeavesEveryOverlappingBoxAlongTheFacing() {
        OrientedBox leg = straight(0, 0.125, 0, 0.5, 0.125, 0.125);
        OrientedBox other = new OrientedBox(0, 0.2, 0, 0, S45, 0, C45, 0.5, 0.15, 0.125);
        OrientedBox[] parts = {leg, null, other};
        double[] p = {0, 0.28, 0};
        assertEquals(2, OrientedBox.stepOut(parts, p, 0, 1, 0, 0.04, 8));
        assertArrayEquals(new double[]{0, 0.36, 0}, p, EPS);
        assertFalse(leg.contains(p[0], p[1], p[2]) || other.contains(p[0], p[1], p[2]));

        double[] free = {2, 2, 2};
        assertEquals(0, OrientedBox.stepOut(parts, free, 0, 1, 0, 0.04, 8));
        assertArrayEquals(new double[]{2, 2, 2}, free, EPS);

        double[] stuck = {0, 0.1, 0};
        assertEquals(-1, OrientedBox.stepOut(parts, stuck, 0, 1, 0, 0.04, 2));
    }

    @Test
    void marginGrowsTheBoxOnEverySide() {
        OrientedBox box = new OrientedBox(0, 0, 0, 0, S45, 0, C45, 1, 0.5, 0.25);
        assertFalse(box.contains(0.3, 0, 0));
        assertTrue(box.contains(0.3, 0, 0, 0.1));
        assertFalse(box.contains(0.4, 0, 0, 0.1));
        assertTrue(box.contains(0.34, 0.59, 1.09, 0.1));
    }

    @Test
    void rejectsBoxesThatAreNoBoxes() {
        assertThrows(IllegalArgumentException.class, () -> new OrientedBox(0, 0, 0, 0, 0, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new OrientedBox(0, 0, 0, 0, 0, 0, 1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new OrientedBox(Double.NaN, 0, 0, 0, 0, 0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new OrientedBox(0, 0, 0, 0, 0, 0, 1, Double.POSITIVE_INFINITY, 1, 1));
    }

    private static double[] rotate(double x, double y, double z, double w, double vx, double vy, double vz) {
        double rw = -x * vx - y * vy - z * vz;
        double rx = w * vx + y * vz - z * vy;
        double ry = w * vy + z * vx - x * vz;
        double rz = w * vz + x * vy - y * vx;
        return new double[]{
                rx * w - rw * x - ry * z + rz * y,
                ry * w - rw * y - rz * x + rx * z,
                rz * w - rw * z - rx * y + ry * x};
    }
}
