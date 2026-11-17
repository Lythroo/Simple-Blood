package com.simpleblood.geom;

public final class QuadSurface {

    @FunctionalInterface
    public interface Texels {
        boolean drawn(int face, double u, double v);

        Texels SOLID = (face, u, v) -> true;
    }

    private static final double EDGE_ON = 1.0e-4;

    private final double[] corners;
    private final double[] normals;
    private final double[] uvs;
    private final Texels texels;
    private final int count;

    public QuadSurface(double[] corners, double[] normals, double[] uvs, Texels texels) {
        if (corners.length % 9 != 0 || normals.length * 3 != corners.length || uvs.length * 3 != corners.length * 2) {
            throw new IllegalArgumentException("face arrays disagree: " + corners.length + " / " + normals.length + " / " + uvs.length);
        }
        this.corners = corners;
        this.normals = normals;
        this.uvs = uvs;
        this.texels = texels == null ? Texels.SOLID : texels;
        this.count = corners.length / 9;
    }

    public int faces() {
        return count;
    }

    public double firstHit(double ax, double ay, double az, double bx, double by, double bz, double[] out) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double best = Double.POSITIVE_INFINITY;
        int bestFace = -1;
        double bestA = 0, bestB = 0;
        for (int f = 0; f < count; f++) {
            int n = f * 3, c = f * 9;
            double nx = normals[n], ny = normals[n + 1], nz = normals[n + 2];
            double facing = dx * nx + dy * ny + dz * nz;
            if (facing > -EDGE_ON) continue;
            double t = ((corners[c] - ax) * nx + (corners[c + 1] - ay) * ny + (corners[c + 2] - az) * nz) / facing;
            if (t < 0 || t > 1 || t >= best) continue;
            double qx = ax + dx * t - corners[c], qy = ay + dy * t - corners[c + 1], qz = az + dz * t - corners[c + 2];
            double e1x = corners[c + 3] - corners[c], e1y = corners[c + 4] - corners[c + 1], e1z = corners[c + 5] - corners[c + 2];
            double e3x = corners[c + 6] - corners[c], e3y = corners[c + 7] - corners[c + 1], e3z = corners[c + 8] - corners[c + 2];
            double a11 = e1x * e1x + e1y * e1y + e1z * e1z, a13 = e1x * e3x + e1y * e3y + e1z * e3z, a33 = e3x * e3x + e3y * e3y + e3z * e3z;
            double det = a11 * a33 - a13 * a13;
            if (det < 1.0e-14) continue;
            double b1 = qx * e1x + qy * e1y + qz * e1z, b3 = qx * e3x + qy * e3y + qz * e3z;
            double a = (b1 * a33 - b3 * a13) / det, b = (a11 * b3 - a13 * b1) / det;
            if (a < 0 || a > 1 || b < 0 || b > 1) continue;
            if (!drawnAt(f, a, b)) continue;
            best = t;
            bestFace = f;
            bestA = a;
            bestB = b;
        }
        if (bestFace < 0) return -1;
        int c = bestFace * 9, n = bestFace * 3;
        out[0] = corners[c] + (corners[c + 3] - corners[c]) * bestA + (corners[c + 6] - corners[c]) * bestB;
        out[1] = corners[c + 1] + (corners[c + 4] - corners[c + 1]) * bestA + (corners[c + 7] - corners[c + 1]) * bestB;
        out[2] = corners[c + 2] + (corners[c + 5] - corners[c + 2]) * bestA + (corners[c + 8] - corners[c + 2]) * bestB;
        out[3] = normals[n];
        out[4] = normals[n + 1];
        out[5] = normals[n + 2];
        out[6] = bestFace;
        return best;
    }

    public double distanceToDrawn(double x, double y, double z, int samples) {
        double best = Double.POSITIVE_INFINITY;
        for (int f = 0; f < count; f++) {
            int c = f * 9;
            for (int i = 0; i < samples; i++) {
                double a = (i + 0.5) / samples;
                for (int j = 0; j < samples; j++) {
                    double b = (j + 0.5) / samples;
                    if (!drawnAt(f, a, b)) continue;
                    double px = corners[c] + (corners[c + 3] - corners[c]) * a + (corners[c + 6] - corners[c]) * b;
                    double py = corners[c + 1] + (corners[c + 4] - corners[c + 1]) * a + (corners[c + 7] - corners[c + 1]) * b;
                    double pz = corners[c + 2] + (corners[c + 5] - corners[c + 2]) * a + (corners[c + 8] - corners[c + 2]) * b;
                    double d = (px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z);
                    if (d < best) best = d;
                }
            }
        }
        return Math.sqrt(best);
    }

    public boolean drawnAt(int f, double a, double b) {
        int u = f * 6;
        double tu = uvs[u] + (uvs[u + 2] - uvs[u]) * a + (uvs[u + 4] - uvs[u]) * b;
        double tv = uvs[u + 1] + (uvs[u + 3] - uvs[u + 1]) * a + (uvs[u + 5] - uvs[u + 1]) * b;
        return texels.drawn(f, tu, tv);
    }
}
