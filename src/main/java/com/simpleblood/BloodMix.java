package com.simpleblood;

public final class BloodMix {
    private BloodMix() {}

    private static final float PIGMENT = 0.55f;
    private static final float FLOOR = 0.004f;
    private static final float DRY_SHARE = 0.3f;

    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            float c = i / 255f;
            TO_LINEAR[i] = c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
        }
    }

    public static boolean on() {
        SimpleBloodConfig c = SimpleBloodClient.getConfig();
        return c == null || c.surfaces.mixing;
    }

    public static int join(int old, float oldUnits, int added, float addUnits, float wet, float spot, float dither) {
        if (old == added || addUnits <= 0) return added;
        if (!on()) return added;
        Tally.count("blood.mix");
        SimpleBloodConfig c = SimpleBloodClient.getConfig();
        float marbling = c == null ? 0.6f : Math.max(0, Math.min(100, c.surfaces.marbling)) / 100f;
        float oldShare = Math.max(0f, oldUnits) * (DRY_SHARE + (1f - DRY_SHARE) * Math.max(0f, Math.min(1f, wet)));
        float t = addUnits / (addUnits + oldShare);
        t *= (1f - marbling) + marbling * 2f * spot;
        return mix(old, added, Math.max(0.02f, Math.min(1f, t)), dither);
    }

    public static int mix(int a, int b, float t, float dither) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float la = TO_LINEAR[(a >> shift) & 0xFF], lb = TO_LINEAR[(b >> shift) & 0xFF];
            float light = la + (lb - la) * t;
            float pigment = (float) Math.exp((1f - t) * Math.log(la + FLOOR) + t * Math.log(lb + FLOOR)) - FLOOR;
            rgb |= toSrgb(light + (pigment - light) * PIGMENT) << shift;
        }
        float glowing = ((a & BloodColor.GLOW_BIT) != 0 ? 1f - t : 0f) + ((b & BloodColor.GLOW_BIT) != 0 ? t : 0f);
        if (dither < glowing) rgb |= BloodColor.GLOW_BIT;
        return rgb;
    }

    public static float spot(int x, int y, int salt) {
        int h = x * 73856093 ^ y * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536f;
    }

    public static float wetness(int now, int born, int life) {
        float age = (now - born) / (float) Math.max(1, life);
        return Math.max(0f, Math.min(1f, 1f - (age - 0.3f) / 0.4f));
    }

    private static int toSrgb(float linear) {
        float c = Math.max(0f, Math.min(1f, linear));
        float s = c <= 0.0031308f ? c * 12.92f : 1.055f * (float) Math.pow(c, 1 / 2.4f) - 0.055f;
        return Math.max(0, Math.min(255, Math.round(s * 255f)));
    }
}
