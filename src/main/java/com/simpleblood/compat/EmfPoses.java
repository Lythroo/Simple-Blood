package com.simpleblood.compat;

import com.simpleblood.SimpleBlood;

import java.lang.reflect.Method;

public final class EmfPoses {

    private EmfPoses() {}

    private static boolean looked;
    private static Method animate;
    private static Class<?> emfRoot;
    private static Method etfState, etfMount;
    private static java.lang.reflect.Field etfDepth;

    private static void lookUp() {
        looked = true;
        try {
            ClassLoader cl = EmfPoses.class.getClassLoader();
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi", false, cl);
            Class<?> root = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot", false, cl);
            for (Method m : api.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("animateModelForState") && p.length == 3 && p[1] == root && p[2] == boolean.class
                        && !p[0].getName().startsWith("traben.")) {
                    animate = m;
                    emfRoot = root;
                }
            }
            if (animate != null) SimpleBlood.LOGGER.info("Entity Model Features found: blood on mobs follows its animations");
            try {
                Class<?> etf = Class.forName("traben.entity_texture_features.features.state.ETFState", false, cl);
                Class<?> etfEntityState = Class.forName("traben.entity_texture_features.features.state.ETFEntityRenderState", false, cl);
                etfState = etf.getMethod("state");
                etfMount = etf.getMethod("mount", etfEntityState);
                etfDepth = etf.getField("currentModelPartDepth");
            } catch (Throwable t) {
                etfState = etfMount = null;
                etfDepth = null;
            }
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            SimpleBlood.LOGGER.warn("Entity Model Features found, but not a version Simple Blood knows; blood on mobs may sit off its animations ({})", t.toString());
        }
    }

    public static net.minecraft.client.model.geom.ModelPart vanilla(net.minecraft.client.model.geom.ModelPart part) {
        try {
            if (part.getClass().getField("vanillaRoot").get(part) instanceof net.minecraft.client.model.geom.ModelPart v) return v;
        } catch (Throwable ignored) {
        }
        return part;
    }

    public static void pose(Object state, Object root) {
        if (!looked) lookUp();
        if (animate == null || state == null || !emfRoot.isInstance(root)) return;
        if (!animate.getParameterTypes()[0].isInstance(state)) return;
        try {
            Object etfBefore = etfState != null ? etfState.invoke(null) : null;
            int depthBefore = etfDepth != null ? etfDepth.getInt(null) : 0;
            animate.invoke(null, state, root, false);
            if (etfState != null) {
                if (etfBefore != null && etfState.invoke(null) != etfBefore) etfMount.invoke(null, etfBefore);
                etfDepth.setInt(null, depthBefore);
            }
            com.simpleblood.Tally.count("emf.posed");
        } catch (Throwable t) {
            animate = null;
            SimpleBlood.LOGGER.warn("Entity Model Features posing for blood on mobs switched off after an error", t);
        }
    }
}
