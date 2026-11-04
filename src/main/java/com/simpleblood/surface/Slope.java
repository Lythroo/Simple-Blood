package com.simpleblood.surface;

final class Slope {
    static final double DRIPS = 0.5;
    static final double SLIDES = 0.34;
    static final double STILL = 0.1;

    static final int PLUS_U = 0, MINUS_U = 1, PLUS_V = 2, MINUS_V = 3;

    private double along, down, out;
    private boolean turned;

    void set(double uy, double vy, double ny, double restUy, double restVy, double restNy) {
        along = -uy;
        down = -vy;
        out = -ny;
        turned = Math.abs(uy - restUy) + Math.abs(vy - restVy) + Math.abs(ny - restNy) > STILL;
    }

    boolean turned() {
        return turned;
    }

    boolean drips() {
        return out > DRIPS;
    }

    double pull() {
        return Math.sqrt(along * along + down * down);
    }

    boolean slides() {
        return !drips() && pull() > SLIDES;
    }

    double stepU() {
        double m = Math.max(Math.abs(along), Math.abs(down));
        return m < 1.0e-9 ? 0 : along / m;
    }

    double stepV() {
        double m = Math.max(Math.abs(along), Math.abs(down));
        return m < 1.0e-9 ? 0 : down / m;
    }

    int neighbour(double r) {
        double a = Math.abs(along), b = Math.abs(down);
        if (r * (a + b) < a) return along > 0 ? PLUS_U : MINUS_U;
        return down > 0 ? PLUS_V : MINUS_V;
    }

    static int dx(int neighbour) {
        return neighbour == PLUS_U ? 1 : neighbour == MINUS_U ? -1 : 0;
    }

    static int dy(int neighbour) {
        return neighbour == PLUS_V ? 1 : neighbour == MINUS_V ? -1 : 0;
    }
}
