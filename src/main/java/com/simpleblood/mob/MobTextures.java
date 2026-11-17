package com.simpleblood.mob;

import com.mojang.blaze3d.platform.NativeImage;
import com.simpleblood.surface.Pixels;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MobTextures {
    private MobTextures() {}

    record Alpha(boolean[] opaque, int w, int h) {
        boolean at(double u, double v) {
            int x = (int) Math.floor(u * w), y = (int) Math.floor(v * h);
            if (x < 0 || y < 0 || x >= w || y >= h) return true;
            return opaque[y * w + x];
        }
    }

    private static final Alpha WHOLE = new Alpha(new boolean[0], 0, 0);
    private static final Map<Object, Alpha> CACHE = new ConcurrentHashMap<>();

    static Alpha of(Object texture) {
        if (texture == null) return null;
        Alpha a = CACHE.computeIfAbsent(texture, MobTextures::read);
        return a == WHOLE ? null : a;
    }

    private static Alpha read(Object texture) {
        try {
            //? if >=1.21.11 {
            var id = (net.minecraft.resources.Identifier) texture;
            //?} else {
            /*var id = (net.minecraft.resources.ResourceLocation) texture;
            *///?}
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return WHOLE;
            try (InputStream in = resource.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                if (w <= 0 || h <= 0 || w * h > 1 << 22) return WHOLE;
                boolean[] opaque = new boolean[w * h];
                boolean holes = false;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        boolean drawn = Pixels.alpha(img, x, y) >= 16;
                        opaque[y * w + x] = drawn;
                        holes |= !drawn;
                    }
                }
                return holes ? new Alpha(opaque, w, h) : WHOLE;
            }
        } catch (Throwable t) {
            return WHOLE;
        }
    }

    private static final Map<Object, Alpha> BY_POLYGON = new ConcurrentHashMap<>();

    static void drawnWith(Object polygon, Alpha alpha) {
        if (polygon == null) return;
        if (alpha == null) BY_POLYGON.remove(polygon);
        else BY_POLYGON.put(polygon, alpha);
    }

    static Alpha ofPolygon(Object polygon) {
        return polygon == null ? null : BY_POLYGON.get(polygon);
    }

    static void clear() {
        CACHE.clear();
        BY_POLYGON.clear();
    }

    public static void resourcesChanged() {
        clear();
    }
}
