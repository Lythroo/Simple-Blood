package com.simpleblood.compat;

import com.simpleblood.Platform;

import java.lang.reflect.Method;

public final class ShaderPacks {
    private ShaderPacks() {}

    private static boolean looked;
    private static Object api;
    private static Method inUse;
    private static boolean cached;
    private static long cachedAt;

    public static boolean inUse() {
        long now = System.nanoTime();
        if (now - cachedAt < 500_000_000L && cachedAt != 0) return cached;
        cachedAt = now;
        cached = ask();
        return cached;
    }

    public static boolean drawAsTranslucentEntity(Object pipeline) {
        inUse();
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

    private static boolean ask() {
        if (!looked) {
            looked = true;
            java.util.List<String> mods = Platform.modIds();
            if (!mods.contains("iris") && !mods.contains("oculus")) return false;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ShaderPacks.class.getClassLoader());
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                api = null;
                inUse = null;
            }
        }
        if (api == null || inUse == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            inUse = null;
            return false;
        }
    }
}
