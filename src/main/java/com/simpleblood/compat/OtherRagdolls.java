package com.simpleblood.compat;

import com.simpleblood.SimpleBlood;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OtherRagdolls {

    private OtherRagdolls() {}

    record Key(String mod, int id) {}

    record Body(Vec3[] parts, LivingEntity owner) {}

    private static boolean looked;

    private static Method ragdollIds;
    private static Method ragdollState;
    private static Method partPositions;

    private static Class<?> saroEntity;
    private static Object[] saroParts;
    private static Method saroPart;
    private static Method saroTransform;
    private static Method saroPosition;
    private static Method saroPlayerId;
    private static Field vx, vy, vz;

    static boolean any() {
        if (!looked) {
            looked = true;
            lookUp();
        }
        return ragdollIds != null || saroEntity != null || !com.simpleblood.SimpleBloodAPI.ragdollSources().isEmpty();
    }

    private static void lookUp() {
        ClassLoader cl = OtherRagdolls.class.getClassLoader();
        Class<?> api = find("com.raiiiden.ragdollified.api.RagdollifiedApi", cl);
        if (api != null) {
            try {
                ragdollIds = api.getMethod("getRagdollIds");
                ragdollState = api.getMethod("getState", int.class);
                partPositions = Class.forName("com.raiiiden.ragdollified.api.RagdollState", false, cl).getMethod("partPositions");
                SimpleBlood.LOGGER.info("Ragdollified found: its ragdolls bleed");
            } catch (Throwable t) {
                ragdollIds = null;
                SimpleBlood.LOGGER.warn("Ragdollified found, but not a version Simple Blood knows; its ragdolls will not bleed ({})", t.toString());
            }
        }
        Class<?> entity = find("de.sarocesch.sarosragdoll.ragdoll.RagdollEntity", cl);
        if (entity != null) {
            try {
                Class<?> part = Class.forName("de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart", false, cl);
                Class<?> sync = Class.forName("de.sarocesch.sarosragdoll.ragdoll.physics.SynchronizedRigidBodyTransform", false, cl);
                Class<?> transform = Class.forName("de.sarocesch.sarosragdoll.ragdoll.physics.RigidBodyTransform", false, cl);
                Class<?> vector = Class.forName("com.jme3.math.Vector3f", false, cl);
                saroParts = part.getEnumConstants();
                saroPart = entity.getMethod("getPartTransform", part);
                saroTransform = sync.getMethod("getTransform");
                saroPosition = transform.getMethod("getPosition");
                saroPlayerId = entity.getMethod("getHandledPlayerId");
                vx = vector.getField("x");
                vy = vector.getField("y");
                vz = vector.getField("z");
                saroEntity = entity;
                SimpleBlood.LOGGER.info("Saro's Player Ragdoll found: its ragdolls bleed");
            } catch (Throwable t) {
                saroEntity = null;
                SimpleBlood.LOGGER.warn("Saro's Player Ragdoll found, but not a version Simple Blood knows; its ragdolls will not bleed ({})", t.toString());
            }
        }
    }

    private static Class<?> find(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    static Map<Key, Body> collect(ClientLevel level) {
        if (!any()) return Map.of();
        Map<Key, Body> out = new HashMap<>();
        if (ragdollIds != null) {
            try {
                ragdollified(out);
            } catch (Throwable t) {
                ragdollIds = null;
                SimpleBlood.LOGGER.warn("Ragdollified ragdoll blood switched off after an error", t);
            }
        }
        if (saroEntity != null) {
            try {
                saros(level, out);
            } catch (Throwable t) {
                saroEntity = null;
                SimpleBlood.LOGGER.warn("Saro's Player Ragdoll blood switched off after an error", t);
            }
        }
        var sources = com.simpleblood.SimpleBloodAPI.ragdollSources();
        for (var entry : sources.entrySet()) {
            try {
                entry.getValue().collect(level, (id, parts) -> {
                    if (id == null || parts == null) return;
                    Entity e = level.getEntity(id);
                    out.put(new Key("api:" + entry.getKey(), id), new Body(parts.clone(), e instanceof LivingEntity living ? living : null));
                });
            } catch (Throwable t) {
                sources.remove(entry.getKey());
                SimpleBlood.LOGGER.warn("Ragdoll source '{}' switched off after an error", entry.getKey(), t);
            }
        }
        return out;
    }

    private static void ragdollified(Map<Key, Body> out) throws Exception {
        if (!(ragdollIds.invoke(null) instanceof Set<?> ids)) return;
        for (Object id : ids) {
            if (!(id instanceof Integer n)) continue;
            if (!(ragdollState.invoke(null, n) instanceof Optional<?> state) || state.isEmpty()) continue;
            if (!(partPositions.invoke(state.get()) instanceof Map<?, ?> positions) || positions.isEmpty()) continue;
            Vec3[] parts = new Vec3[8];
            positions.forEach((part, at) -> {
                if (part instanceof Enum<?> e && e.ordinal() < parts.length && at instanceof Vec3 v) parts[e.ordinal()] = v;
            });
            out.put(new Key("ragdollified", n), new Body(parts, null));
        }
    }

    private static void saros(ClientLevel level, Map<Key, Body> out) throws Exception {
        for (Entity e : level.entitiesForRendering()) {
            if (!saroEntity.isInstance(e)) continue;
            Vec3[] parts = new Vec3[saroParts.length];
            for (int i = 0; i < parts.length; i++) {
                Object sync = saroPart.invoke(e, saroParts[i]);
                if (sync == null) continue;
                Object p = saroPosition.invoke(saroTransform.invoke(sync));
                if (p != null) parts[i] = new Vec3(vx.getFloat(p), vy.getFloat(p), vz.getFloat(p));
            }
            Entity player = level.getEntity((Integer) saroPlayerId.invoke(e));
            if (!(player instanceof LivingEntity)) player = net.minecraft.client.Minecraft.getInstance().player;
            out.put(new Key("saros", e.getId()), new Body(parts, player instanceof LivingEntity living ? living : null));
        }
    }
}
