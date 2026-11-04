package com.simpleblood.surface;

final class PoolShading {
    static final int STEP = 5;
    private static final int DIAGONAL = 7;
    static final int UNSET = Integer.MAX_VALUE / 2;

    private static final float BODY_RAMP = 4.0f;
    private static final float BODY_DARKEN = 0.14f;
    private static final float BODY_RICHER = 0.08f;
    private static final float EDGE_LIGHTEN = 0.05f;
    private static final float EDGE_CLEAR = 0.12f;

    private static final float RING_WIDTH = 2.0f;
    private static final float RING_DARKEN = 0.30f;
    private static final float MIDDLE_DARKEN = 0.07f;
    private static final float MIDDLE_FADE = 0.20f;

    private PoolShading() {}

    static void edgeDistances(int[] dist, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int d = dist[i];
                if (d <= STEP) continue;
                if (x > 0) d = relax(d, dist[i - 1], STEP);
                if (y > 0) {
                    d = relax(d, dist[i - w], STEP);
                    if (x > 0) d = relax(d, dist[i - w - 1], DIAGONAL);
                    if (x < w - 1) d = relax(d, dist[i - w + 1], DIAGONAL);
                }
                dist[i] = d;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                int d = dist[i];
                if (d <= STEP) continue;
                if (x < w - 1) d = relax(d, dist[i + 1], STEP);
                if (y < h - 1) {
                    d = relax(d, dist[i + w], STEP);
                    if (x < w - 1) d = relax(d, dist[i + w + 1], DIAGONAL);
                    if (x > 0) d = relax(d, dist[i + w - 1], DIAGONAL);
                }
                dist[i] = d;
            }
        }
    }

    private static int relax(int d, int neighbour, int step) {
        return neighbour == 0 ? d : Math.min(d, neighbour + step);
    }

    static float pixels(int distance) {
        return distance / (float) STEP;
    }

    private static float inward(float edge, float ramp) {
        float t = Math.max(0f, Math.min(1f, (edge - 1f) / ramp));
        return t * t * (3f - 2f * t);
    }

    private static float depthWeight(float deep, float dry) {
        return deep * (1f - dry);
    }

    static float depthTone(float edge, float deep, float dry) {
        float w = depthWeight(deep, dry);
        float body = inward(edge, BODY_RAMP);
        return 1f + w * (EDGE_LIGHTEN * (1f - body) - BODY_DARKEN * body);
    }

    static float depthRichness(float edge, float deep, float dry) {
        return 1f - BODY_RICHER * depthWeight(deep, dry) * inward(edge, BODY_RAMP);
    }

    static float depthAlpha(float edge, float deep, float dry) {
        return 1f - EDGE_CLEAR * depthWeight(deep, dry) * (1f - inward(edge, BODY_RAMP));
    }

    static float ringTone(float edge, float dry) {
        float rim = 1f - inward(edge, RING_WIDTH);
        float ring = (float) Math.sqrt(dry);
        return 1f - dry * MIDDLE_DARKEN - ring * (RING_DARKEN - MIDDLE_DARKEN) * rim;
    }

    static float ringAlpha(float edge, float dry) {
        return 1f - MIDDLE_FADE * dry * inward(edge, RING_WIDTH);
    }
}
