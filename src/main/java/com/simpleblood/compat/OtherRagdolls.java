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

    record Body(Vec3[] parts, LivingEntity owner, int source, Vec3 cutAt, Vec3 cutDir) {
        Body(Vec3[] parts, LivingEntity owner) {
            this(parts, owner, -1, null, null);
        }
    }

    private static Method severedParts;
    private static boolean severedLooked;

    static int severedMask(int ragdollId) {
        if (!severedLooked) {
            severedLooked = true;
            try {
                severedParts = Class.forName("com.raiiiden.ragdollified.api.RagdollAmputationApi", false, OtherRagdolls.class.getClassLoader())
                        .getMethod("getSeveredParts", int.class);
            } catch (Throwable t) {
                severedParts = null;
            }
        }
        if (severedParts == null) return 0;
        try {
            int mask = 0;
            if (severedParts.invoke(null, ragdollId) instanceof Set<?> parts) {
                for (Object p : parts) if (p instanceof Enum<?> e && e.ordinal() < 31) mask |= 1 << e.ordinal();
            }
            return mask;
        } catch (Throwable t) {
            severedParts = null;
            return 0;
        }
    }

    private record Limb(Vec3 at, int source, long seen, org.joml.Quaternionf turn, Vec3 half) {}

    private static final Map<Integer, Limb> LIMBS = new HashMap<>();
    private static final Map<Integer, Vec3> CUT_ENDS = new HashMap<>();
    private static Method limbId, limbSource, limbHalf;
    private static Field limbPosition, px, py, pz, limbRotation, qx, qy, qz, qw;
    private static boolean limbLooked, limbReadable, turnReadable;

    public static void limbDrawn(Object limb, Object transform) {
        if (limb == null || transform == null) return;
        try {
            if (!limbLooked) {
                limbLooked = true;
                limbId = limb.getClass().getMethod("getLimbId");
                limbSource = limb.getClass().getMethod("getSourceEntityId");
                limbPosition = transform.getClass().getField("position");
                Class<?> v = limbPosition.getType();
                px = v.getField("x");
                py = v.getField("y");
                pz = v.getField("z");
                try {
                    limbRotation = transform.getClass().getField("rotation");
                    Class<?> q = limbRotation.getType();
                    qx = q.getField("x");
                    qy = q.getField("y");
                    qz = q.getField("z");
                    qw = q.getField("w");
                    limbHalf = limb.getClass().getMethod("getHalfExtents");
                    turnReadable = true;
                } catch (Throwable t) {
                    turnReadable = false;
                }
                limbReadable = true;
            }
            if (!limbReadable) return;
            com.simpleblood.Tally.count("ragdoll.limb");
            Object p = limbPosition.get(transform);
            Vec3 at = new Vec3(px.getFloat(p), py.getFloat(p), pz.getFloat(p));
            org.joml.Quaternionf turn = null;
            Vec3 half = null;
            if (turnReadable) {
                try {
                    Object r = limbRotation.get(transform), h = limbHalf.invoke(limb);
                    turn = new org.joml.Quaternionf(qx.getFloat(r), qy.getFloat(r), qz.getFloat(r), qw.getFloat(r));
                    half = new Vec3(px.getFloat(h), py.getFloat(h), pz.getFloat(h));
                } catch (Throwable t) {
                    turnReadable = false;
                    SimpleBlood.LOGGER.warn("Ragdollified cut-off limbs: cut ends not readable, they will not spurt ({})", t.toString());
                }
            }
            net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            LIMBS.put((Integer) limbId.invoke(limb), new Limb(at, (Integer) limbSource.invoke(limb),
                    level == null ? 0 : level.getGameTime(), turn, half));
        } catch (Throwable t) {
            if (limbReadable) SimpleBlood.LOGGER.warn("Ragdollified cut-off limbs: not readable, they will not bleed ({})", t.toString());
            limbReadable = false;
        }
    }

    private static Vec3 cutEnd(Limb l, Vec3 body) {
        Vec3 h = l.half();
        Vec3 end = h.x >= h.y && h.x >= h.z ? new Vec3(h.x, 0, 0) : h.y >= h.z ? new Vec3(0, h.y, 0) : new Vec3(0, 0, h.z);
        if (body == null) return end;
        Vec3 w = turned(end, l.turn());
        return l.at().add(w).distanceToSqr(body) <= l.at().subtract(w).distanceToSqr(body) ? end : end.scale(-1);
    }

    private static Vec3 torsoOf(Map<Key, Body> bodies, int source, Entity from) {
        Body ragdoll = bodies.get(new Key("ragdollified", source));
        if (ragdoll != null && ragdoll.parts().length > 0 && ragdoll.parts()[0] != null) return ragdoll.parts()[0];
        return from == null ? null : from.position().add(0, from.getBbHeight() * 0.5, 0);
    }

    private static Vec3 turned(Vec3 v, org.joml.Quaternionf turn) {
        org.joml.Vector3f w = new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z).rotate(turn);
        return new Vec3(w.x, w.y, w.z);
    }

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

    public static boolean sarosThere() {
        return any() && saroEntity != null;
    }

    public static int saroPlayerOf(Entity e) {
        if (e == null || !any() || saroEntity == null || !saroEntity.isInstance(e)) return -1;
        try {
            return (Integer) saroPlayerId.invoke(e);
        } catch (Throwable t) {
            return -1;
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
        if (!LIMBS.isEmpty()) {
            long now = level.getGameTime();
            LIMBS.values().removeIf(l -> now - l.seen() > 10 || now < l.seen());
            CUT_ENDS.keySet().retainAll(LIMBS.keySet());
            LIMBS.forEach((id, l) -> {
                Entity from = level.getEntity(l.source());
                Vec3 cutAt = null, cutDir = null;
                if (l.turn() != null && l.half() != null) {
                    Vec3 end = CUT_ENDS.computeIfAbsent(id, k -> cutEnd(l, torsoOf(out, l.source(), from)));
                    Vec3 w = turned(end, l.turn());
                    cutAt = l.at().add(w);
                    cutDir = w.normalize();
                }
                out.put(new Key("ragdollified-limb", id), new Body(new Vec3[]{l.at()},
                        from instanceof LivingEntity living && living.isAlive() ? living : null, l.source(), cutAt, cutDir));
            });
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
