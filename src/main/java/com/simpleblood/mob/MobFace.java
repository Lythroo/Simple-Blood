package com.simpleblood.mob;

import com.mojang.blaze3d.platform.NativeImage;
import com.simpleblood.BloodColor;
import com.simpleblood.surface.BloodCanvasAtlas;
import com.simpleblood.surface.Pixels;
import net.minecraft.util.RandomSource;

final class MobFace {
    static final int MAX_DEPTH = 6;
    private static final float[] TONE = {0.86f, 1.0f, 1.0f, 1.18f};

    final int pw, ph;
    private final byte[] depth;
    private final byte[] tone;
    private final int[] rgb;
    private final int[] born;
    private final int[] life;
    int count;
    int slot = -1;
    boolean dirty;
    int glow;
    int lastSeen;
    private NativeImage image, spec;

    MobFace(int pw, int ph) {
        this.pw = pw;
        this.ph = ph;
        int n = pw * ph;
        depth = new byte[n];
        tone = new byte[n];
        rgb = new int[n];
        born = new int[n];
        life = new int[n];
    }

    boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < pw && y < ph;
    }

    int depthAt(int x, int y) {
        return depth[y * pw + x];
    }

    int colourAt(int x, int y) {
        return rgb[y * pw + x];
    }

    void add(int x, int y, int units, int colour, int now, int lifetime, RandomSource rng) {
        int i = y * pw + x;
        if (depth[i] == 0) {
            count++;
            tone[i] = (byte) rng.nextInt(TONE.length);
            life[i] = Math.max(20, (int) (lifetime * (0.85f + rng.nextFloat() * 0.3f)));
        }
        born[i] = now;
        rgb[i] = colour;
        depth[i] = (byte) Math.min(MAX_DEPTH, depth[i] + units);
        dirty = true;
    }

    int take(int x, int y) {
        int i = y * pw + x;
        if (depth[i] == 0) return -1;
        int c = rgb[i];
        if (--depth[i] == 0) count--;
        dirty = true;
        return c;
    }

    boolean age(int now, int wash) {
        for (int i = 0; i < depth.length; i++) {
            if (depth[i] == 0) continue;
            if (wash > 0) born[i] -= wash;
            if (now - born[i] >= life[i]) {
                depth[i] = 0;
                count--;
                dirty = true;
            }
        }
        if (wash > 0 && count > 0) dirty = true;
        return count == 0;
    }

    void refresh(BloodCanvasAtlas atlas, int now, float opacity) {
        if (slot < 0) {
            slot = atlas.acquireSlot();
            if (slot < 0) return;
        }
        if (image == null) image = new NativeImage(pw, ph, true);
        if (spec == null && atlas.wantsSpecular()) spec = new NativeImage(pw, ph, true);
        float glowSum = 0f;
        for (int i = 0; i < depth.length; i++) {
            int x = i % pw, y = i / pw;
            if (spec != null) Pixels.setAbgr(spec, x, y, 0);
            if (depth[i] == 0) {
                Pixels.setAbgr(image, x, y, 0);
                continue;
            }
            float agePct = (now - born[i]) / (float) Math.max(1, life[i]);
            float dry = Math.max(0f, Math.min(1f, (agePct - 0.35f) / 0.35f));
            if ((rgb[i] & BloodColor.GLOW_BIT) != 0) glowSum += 1f - dry;
            float deep = Math.min(1f, (depth[i] - 1) / 3f);
            float f = TONE[tone[i]] * (1f - 0.16f * dry) * (1f - 0.08f * deep);
            int c = rgb[i];
            int r = clamp((int) (((c >> 16) & 0xFF) * f));
            int g = clamp((int) (((c >> 8) & 0xFF) * f));
            int b = clamp((int) ((c & 0xFF) * f));
            float alpha = opacity * (0.55f + 0.45f * Math.min(1f, (depth[i] - 1) / 2f));
            if (agePct > 0.75f) alpha *= 1f - Math.min(1f, (agePct - 0.75f) / 0.25f);
            Pixels.setAbgr(image, x, y, Pixels.packAbgr(r, g, b, clamp((int) (alpha * 255))));
            if (spec != null) {
                float smooth = (0.72f + 0.2f * deep) * (1f - dry);
                Pixels.setAbgr(spec, x, y, Pixels.packAbgr(clamp((int) (smooth * 255)), 6, 0, 255));
            }
        }
        glow = count == 0 ? 0 : Math.round(15f * Math.min(1f, glowSum / count));
        atlas.upload(slot, image, spec);
        dirty = false;
    }

    void dispose(BloodCanvasAtlas atlas) {
        if (atlas != null && slot >= 0) atlas.releaseSlot(slot);
        slot = -1;
        if (image != null) {
            image.close();
            image = null;
        }
        if (spec != null) {
            spec.close();
            spec = null;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
