package com.simpleblood;

import com.mojang.blaze3d.platform.NativeImage;
import com.simpleblood.surface.Pixels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GibletColours {
    private GibletColours() {}

    public static final int FLESH = 0xD8706A;
    public static final String AUTO = "auto", OFF = "off";

    private static final float RED_HUE = 25f, RED_SATURATION = 0.3f;
    private static final int MIN_ALPHA = 16;
    private static final Map<Object, Integer> BODY = new ConcurrentHashMap<>();
    private static final int UNREADABLE = -1;

    public static int of(LivingEntity entity, int blood) {
        Integer own = parse(SimpleBlood.gibletsOf(entity));
        if (own != null) return own;
        Integer api = SimpleBloodAPI.gibletColour(entity);
        if (api != null) return api & 0xFFFFFF;
        if (isRed(blood)) return meat(blood);
        int body = bodyColour(entity);
        return body != UNREADABLE ? body : blood;
    }

    public static int forBlood(int blood) {
        return isRed(blood) ? meat(blood) : blood;
    }

    public static Integer parse(String setting) {
        if (setting == null || setting.length() != 7 || setting.charAt(0) != '#') return null;
        try {
            return Integer.parseInt(setting.substring(1), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String format(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static void clear() {
        BODY.clear();
    }

    private static int meat(int blood) {
        return mix(blood, FLESH);
    }

    private static boolean isRed(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max <= 0 || (max - min) / max < RED_SATURATION || max != r) return false;
        float hue = 60f * Math.abs(g - b) / (max - min);
        return hue <= RED_HUE;
    }

    private static int mix(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return (r << 16) | (g << 8) | bl;
    }

    private static int bodyColour(LivingEntity entity) {
        Object texture = textureOf(entity);
        return texture == null ? UNREADABLE : BODY.computeIfAbsent(texture, GibletColours::read);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object textureOf(LivingEntity entity) {
        try {
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living)) return null;
            //? if >=1.21.11 {
            return living.getTextureLocation((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) living.createRenderState(entity, 1.0f));
            //?} else {
            /*return living.getTextureLocation(entity);
            *///?}
        } catch (Throwable t) {
            return null;
        }
    }

    private static int read(Object texture) {
        try {
            //? if >=1.21.11 {
            var id = (net.minecraft.resources.Identifier) texture;
            //?} else {
            /*var id = (net.minecraft.resources.ResourceLocation) texture;
            *///?}
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return UNREADABLE;
            try (InputStream in = resource.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                if (w <= 0 || h <= 0 || w * h > 1 << 22) return UNREADABLE;
                long r = 0, g = 0, b = 0, weight = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int a = Pixels.alpha(img, x, y);
                        if (a < MIN_ALPHA) continue;
                        int rgb = Pixels.rgb(img, x, y);
                        r += (long) ((rgb >> 16) & 0xFF) * a;
                        g += (long) ((rgb >> 8) & 0xFF) * a;
                        b += (long) (rgb & 0xFF) * a;
                        weight += a;
                    }
                }
                if (weight == 0) return UNREADABLE;
                return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
            }
        } catch (Throwable t) {
            return UNREADABLE;
        }
    }
}
