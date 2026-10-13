package com.simpleblood.compat;

import com.simpleblood.Platform;

import java.lang.reflect.Method;

/**
 * Whether an Iris shader pack is drawing the world right now, read through the public Iris API
 * by reflection (no dependency on Iris). Shader packs light and tint see-through particles
 * in their own way, and some things look better adjusted for that.
 */
public final class ShaderPacks {
    private ShaderPacks() {}

    private static boolean looked;
    private static Object api;
    private static Method inUse;
    private static Method shadowPass;
    private static boolean cached;
    private static long cachedAt;

    /** True while a shader pack is in use; checked at most every half second. */
    public static boolean inUse() {
        long now = System.nanoTime();
        if (now - cachedAt < 500_000_000L && cachedAt != 0) return cached;
        cachedAt = now;
        cached = ask();
        return cached;
    }

    /**
     * Has Iris draw {@code pipeline} (a render pipeline of this mod) with the shader pack program
     * for see-through entities. Iris only knows the pipelines of the game itself; without this a
     * pipeline of ours would be drawn without the pack. Iris 1.10 and later; false otherwise.
     */
    public static boolean drawAsTranslucentEntity(Object pipeline) {
        inUse(); // finds the API
        if (api == null) return false;
        try {
            Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram", false, ShaderPacks.class.getClassLoader());
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object entities = Enum.valueOf((Class) program, "ENTITIES_TRANSLUCENT");
            for (Method m : api.getClass().getMethods()) {
                if (m.getName().equals("assignPipeline") && m.getParameterCount() == 2) {
                    m.invoke(api, pipeline, entities);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            com.simpleblood.SimpleBlood.LOGGER.warn("Iris could not take the blood cloud pipeline", e);
        }
        return false;
    }

    /**
     * True while the shader pack draws the world from the sun for its shadows: everything is
     * drawn once more then, in the sun's view, not the camera's.
     */
    public static boolean inShadowPass() {
        if (!inUse() || shadowPass == null) return false;
        try {
            return (Boolean) shadowPass.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            shadowPass = null; // stop asking
            return false;
        }
    }

    private static boolean ask() {
        if (!looked) {
            looked = true;
            java.util.List<String> mods = Platform.modIds();
            if (!mods.contains("iris") && !mods.contains("oculus")) return false; // Oculus keeps the Iris API
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ShaderPacks.class.getClassLoader());
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
                try {
                    shadowPass = c.getMethod("isRenderingShadowPass");
                } catch (NoSuchMethodException e) {
                    shadowPass = null;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                api = null;
                inUse = null;
            }
        }
        if (api == null || inUse == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            inUse = null; // stop asking
            return false;
        }
    }
}
