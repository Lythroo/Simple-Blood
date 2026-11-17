package com.simpleblood.geom;

public final class PolygonForms {

    private PolygonForms() {}

    public static long form(float[] v) {
        double mx = (v[0] + v[5] + v[10] + v[15]) / 4, my = (v[1] + v[6] + v[11] + v[16]) / 4, mz = (v[2] + v[7] + v[12] + v[17]) / 4;
        return unordered(corner(v, 0, mx, my, mz, true), corner(v, 1, mx, my, mz, true), corner(v, 2, mx, my, mz, true),
                corner(v, 3, mx, my, mz, true), 2305843009213693951L);
    }

    public static long textureForm(float[] v) {
        return unordered(corner(v, 0, 0, 0, 0, false), corner(v, 1, 0, 0, 0, false), corner(v, 2, 0, 0, 0, false),
                corner(v, 3, 0, 0, 0, false), 4611686018427387847L);
    }

    private static long corner(float[] v, int k, double mx, double my, double mz, boolean where) {
        long h = 17;
        if (where) {
            h = 31 * h + Math.round((v[k * 5] - mx) * 1000.0);
            h = 31 * h + Math.round((v[k * 5 + 1] - my) * 1000.0);
            h = 31 * h + Math.round((v[k * 5 + 2] - mz) * 1000.0);
        }
        h = 31 * h + Math.round(v[k * 5 + 3] * 100000.0);
        h = 31 * h + Math.round(v[k * 5 + 4] * 100000.0);
        return h;
    }

    private static long unordered(long a, long b, long c, long d, long seed) {
        long t;
        if (a > b) { t = a; a = b; b = t; }
        if (c > d) { t = c; c = d; d = t; }
        if (a > c) { t = a; a = c; c = t; }
        if (b > d) { t = b; b = d; d = t; }
        if (b > c) { t = b; b = c; c = t; }
        long h = seed;
        h = 31 * h + a;
        h = 31 * h + b;
        h = 31 * h + c;
        h = 31 * h + d;
        return h == 0 ? 1 : h;
    }
}
