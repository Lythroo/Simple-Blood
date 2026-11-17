package com.simpleblood;

final class CoughTiming {
    private CoughTiming() {}

    static final int SLOWEST_GAP_TICKS = 400, FASTEST_GAP_TICKS = 120;
    static final int MIN_GAP_TICKS = 20;
    static final int COUGH_GAP_TICKS = 7;
    static final float FEWEST_DROPS = 3f, MOST_DROPS = 7f;

    static float severity(float health, float maxHealth, float threshold) {
        if (maxHealth <= 0f || threshold <= 0f) return -1f;
        float fraction = health / maxHealth;
        if (fraction > threshold) return -1f;
        return Math.min(1f, Math.max(0f, 1f - fraction / threshold));
    }

    static int gapTicks(float severity, float frequency, float random) {
        float base = SLOWEST_GAP_TICKS - (SLOWEST_GAP_TICKS - FASTEST_GAP_TICKS) * severity;
        float jitter = 0.7f + 0.6f * random;
        return Math.max(MIN_GAP_TICKS, Math.round(base * jitter / Math.max(0.01f, frequency)));
    }

    static int drops(float severity, float amount, float random) {
        if (amount <= 0f) return 0;
        float base = FEWEST_DROPS + (MOST_DROPS - FEWEST_DROPS) * severity;
        return Math.max(1, Math.round(base * amount * (0.75f + 0.5f * random)));
    }
}
