package com.simpleblood.compat;

import com.simpleblood.SimpleBlood;
import com.simpleblood.geom.OrientedBox;
import com.simpleblood.mob.RagdollBodies;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class RagdollDeaths {

    private RagdollDeaths() {}

    public static final String MOD = "ragdolldeaths";

    private static final String CLIENT = "com.frostfleee.ragdolldeaths.client.";

    private static boolean looked;
    private static Object host;
    private static Field ragdolls;
    private static Field entity, failed, bodies;
    private static Field x, y, z, qx, qy, qz, qw, hx, hy, hz;
    private static Method applyPose;

    public static boolean available() {
        if (!looked) {
            looked = true;
            if (com.simpleblood.Platform.modIds().contains(MOD)) lookUp();
        }
        return ragdolls != null;
    }

    private static void lookUp() {
        try {
            ClassLoader cl = RagdollDeaths.class.getClassLoader();
            Class<?> hostClass = Class.forName(CLIENT + "PhysicsHost", false, cl);
            Class<?> ragdoll = Class.forName(CLIENT + "PhysicsRagdoll", false, cl);
            Class<?> body = Class.forName("com.frostfleee.ragdolldeaths.physics.Body", false, cl);
            Field instance = open(hostClass.getDeclaredField("INSTANCE"));
            Field list = open(hostClass.getDeclaredField("ragdolls"));
            entity = open(ragdoll.getDeclaredField("entity"));
            failed = open(ragdoll.getDeclaredField("failed"));
            bodies = open(ragdoll.getDeclaredField("bodies"));
            if (!LivingEntity.class.isAssignableFrom(entity.getType()) || !bodies.getType().isArray()
                    || bodies.getType().getComponentType() != body || failed.getType() != boolean.class) {
                throw new NoSuchFieldException("PhysicsRagdoll's fields have other types");
            }
            x = body.getField("x");
            y = body.getField("y");
            z = body.getField("z");
            qx = body.getField("qx");
            qy = body.getField("qy");
            qz = body.getField("qz");
            qw = body.getField("qw");
            hx = body.getField("hx");
            hy = body.getField("hy");
            hz = body.getField("hz");
            host = instance.get(null);
            if (host == null || !(list.get(host) instanceof List<?>)) throw new IllegalStateException("no physics host");
            ragdolls = list;
            try {
                applyPose = Class.forName(CLIENT + "PhysicsPoseApplier", false, cl).getMethod("apply",
                        net.minecraft.client.model.Model.class, Object.class, org.joml.Matrix4f.class);
            } catch (Throwable t) {
                applyPose = null;
            }
            SimpleBlood.LOGGER.info("Ragdoll Deaths found: its bodies bleed and keep the blood on mobs");
        } catch (Throwable t) {
            ragdolls = null;
            SimpleBlood.LOGGER.warn("Ragdoll Deaths found, but not a version Simple Blood knows; its bodies will not bleed ({})", t.toString());
        }
    }

    private static Field open(Field f) {
        f.setAccessible(true);
        return f;
    }

    private static void disable(Throwable t) {
        if (ragdolls == null) return;
        ragdolls = null;
        applyPose = null;
        SimpleBlood.LOGGER.warn("Ragdoll Deaths bodies not readable any more; they bleed no more ({})", t.toString());
    }

    private interface Visitor {
        void body(LivingEntity mob, Object[] parts) throws ReflectiveOperationException;
    }

    private static void forEachBody(ClientLevel level, Visitor visit) {
        if (level == null || !available()) return;
        try {
            for (Object r : (List<?>) ragdolls.get(host)) {
                if (failed.getBoolean(r) || !(bodies.get(r) instanceof Object[] parts) || parts.length == 0) continue;
                if (!(entity.get(r) instanceof LivingEntity mob) || mob.level() != level) continue;
                visit.body(mob, parts);
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    static void bodies(ClientLevel level, List<RagdollBodies.Body> out) {
        forEachBody(level, (mob, parts) -> {
            OrientedBox[] boxes = new OrientedBox[parts.length];
            for (int i = 0; i < parts.length; i++) boxes[i] = boxOf(parts[i]);
            RagdollBodies.Body b = RagdollBodies.body(MOD, mob.getId(), null, mob.getId(), boxes);
            if (b != null) out.add(b);
        });
    }

    static void collect(ClientLevel level, Map<OtherRagdolls.Key, OtherRagdolls.Body> out) {
        forEachBody(level, (mob, parts) -> {
            Vec3[] at = new Vec3[parts.length];
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null) at[i] = new Vec3(x.getDouble(parts[i]), y.getDouble(parts[i]), z.getDouble(parts[i]));
            }
            out.put(new OtherRagdolls.Key(MOD, mob.getId()), new OtherRagdolls.Body(at, mob));
        });
    }

    private static OrientedBox boxOf(Object part) throws IllegalAccessException {
        if (part == null) return null;
        try {
            return new OrientedBox(x.getDouble(part), y.getDouble(part), z.getDouble(part),
                    qx.getDouble(part), qy.getDouble(part), qz.getDouble(part), qw.getDouble(part),
                    Math.abs(hx.getDouble(part)), Math.abs(hy.getDouble(part)), Math.abs(hz.getDouble(part)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isBody(int entityId) {
        return ragdolls != null && RagdollBodies.find(MOD, entityId) != null;
    }

    public static void poseLayer(Object model, Object state, org.joml.Matrix4f pose) {
        if (applyPose == null) return;
        try {
            applyPose.invoke(null, model, state, pose);
        } catch (Throwable t) {
            applyPose = null;
            SimpleBlood.LOGGER.warn("Ragdoll Deaths: layers over its bodies not readable; they take no blood ({})", t.toString());
        }
    }
}
