package com.simpleblood.compat;

import com.simpleblood.SimpleBlood;
import com.simpleblood.mob.RagdollBodies;
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

    private static Method livingMask;
    private static Method livingNone;
    private static Method worldAnchor;
    private static Object[] ragdollParts;
    private static boolean livingLooked;

    static boolean livingCuts() {
        if (!livingLooked) {
            livingLooked = true;
            ClassLoader cl = OtherRagdolls.class.getClassLoader();
            try {
                Class<?> state = find("com.raiiiden.ragdollifiedamp.client.ClientAmputationState", cl);
                Class<?> anchors = find("com.raiiiden.ragdollified.LimbAnchors", cl);
                Class<?> part = find("com.raiiiden.ragdollified.RagdollPart", cl);
                if (state != null && anchors != null && part != null) {
                    livingMask = state.getMethod("severedMask", Entity.class);
                    livingNone = state.getMethod("isEmpty");
                    worldAnchor = anchors.getMethod("worldAnchor", LivingEntity.class, part);
                    ragdollParts = part.getEnumConstants();
                    SimpleBlood.LOGGER.info("Ragdollified Amputation found: stumps on living mobs bleed");
                }
            } catch (Throwable t) {
                livingMask = null;
                SimpleBlood.LOGGER.warn("Ragdollified Amputation looks different than expected: stumps on living mobs will not bleed ({})", t.toString());
            }
        }
        if (livingMask == null) return false;
        try {
            return !(Boolean) livingNone.invoke(null);
        } catch (Throwable t) {
            livingMask = null;
            return false;
        }
    }

    static int livingSeveredMask(Entity e) {
        if (livingMask == null) return 0;
        try {
            return (Integer) livingMask.invoke(null, e);
        } catch (Throwable t) {
            livingMask = null;
            SimpleBlood.LOGGER.warn("Ragdollified Amputation: cut parts not readable, stumps on living mobs stop bleeding ({})", t.toString());
            return 0;
        }
    }

    static Vec3 anchor(LivingEntity e, int index) {
        if (worldAnchor == null || index < 0 || index >= ragdollParts.length) return null;
        try {
            return (Vec3) worldAnchor.invoke(null, e, ragdollParts[index]);
        } catch (Throwable t) {
            worldAnchor = null;
            SimpleBlood.LOGGER.warn("Ragdollified: limb positions not readable, stumps on living mobs stop bleeding ({})", t.toString());
            return null;
        }
    }

    static int partCount() {
        return ragdollParts == null ? 0 : ragdollParts.length;
    }

    private static Method allRagdolls;
    private static Method ragdollIdOf, originalIdOf, ragdollGone, snapshotOf;
    private static Field snapPositions, snapRotations, snapHalves, snapGone;
    private static Field tupleX, tupleY, tupleZ, quatX, quatY, quatZ, quatW;
    private static boolean boxesLooked;
    private static Method saroRotation, saroBoxSize, saroQx, saroQy, saroQz, saroQw;
    private static boolean saroBoxesLooked;
    private static final int LIMB_FRESH = 10;

    static void bodies(ClientLevel level, java.util.List<RagdollBodies.Body> out) {
        if (!any()) return;
        if (ragdollIds != null) ragdollifiedBoxes(out);
        if (!LIMBS.isEmpty()) {
            long now = level.getGameTime();
            LIMBS.forEach((id, l) -> {
                if (now - l.seen() > LIMB_FRESH || now < l.seen() || l.turn() == null || l.half() == null) return;
                com.simpleblood.geom.OrientedBox box = box(l.at().x, l.at().y, l.at().z, l.turn().x, l.turn().y, l.turn().z, l.turn().w,
                        l.half().x, l.half().y, l.half().z);
                RagdollBodies.Body b = RagdollBodies.body("ragdollified-limb", id, null, l.source(), new com.simpleblood.geom.OrientedBox[]{box});
                if (b != null) out.add(b);
            });
        }
        if (saroEntity != null) saroBoxes(level, out);
    }

    private static void ragdollifiedBoxes(java.util.List<RagdollBodies.Body> out) {
        if (!boxesLooked) {
            boxesLooked = true;
            try {
                ClassLoader cl = OtherRagdolls.class.getClassLoader();
                allRagdolls = Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager", false, cl).getMethod("getAll");
                Class<?> ragdoll = Class.forName("com.raiiiden.ragdollified.client.ClientRagdoll", false, cl);
                ragdollIdOf = ragdoll.getMethod("getId");
                originalIdOf = ragdoll.getMethod("getOriginalEntityId");
                ragdollGone = ragdoll.getMethod("isDestroyed");
                snapshotOf = ragdoll.getMethod("getSnapshot");
                Class<?> snap = snapshotOf.getReturnType();
                snapPositions = snap.getField("positions");
                snapRotations = snap.getField("rotations");
                snapHalves = snap.getField("halfExtents");
                snapGone = snap.getField("destroyed");
                Class<?> vector = snapPositions.getType().getComponentType(), quat = snapRotations.getType().getComponentType();
                tupleX = vector.getField("x");
                tupleY = vector.getField("y");
                tupleZ = vector.getField("z");
                quatX = quat.getField("x");
                quatY = quat.getField("y");
                quatZ = quat.getField("z");
                quatW = quat.getField("w");
            } catch (Throwable t) {
                allRagdolls = null;
                SimpleBlood.LOGGER.warn("Ragdollified: ragdoll parts not readable; blood passes through its ragdolls and shots do not hit them ({})", t.toString());
            }
        }
        if (allRagdolls == null) return;
        try {
            if (!(allRagdolls.invoke(null) instanceof java.util.Collection<?> all)) return;
            for (Object r : all) {
                if (Boolean.TRUE.equals(ragdollGone.invoke(r))) continue;
                Object s = snapshotOf.invoke(r);
                if (s == null || snapGone.getBoolean(s)) continue;
                Object[] at = (Object[]) snapPositions.get(s), turn = (Object[]) snapRotations.get(s), half = (Object[]) snapHalves.get(s);
                if (at == null || turn == null || half == null) continue;
                com.simpleblood.geom.OrientedBox[] parts = new com.simpleblood.geom.OrientedBox[at.length];
                for (int i = 0; i < at.length; i++) {
                    if (at[i] == null || i >= turn.length || turn[i] == null || i >= half.length || half[i] == null) continue;
                    parts[i] = box(tupleX.getFloat(at[i]), tupleY.getFloat(at[i]), tupleZ.getFloat(at[i]),
                            quatX.getFloat(turn[i]), quatY.getFloat(turn[i]), quatZ.getFloat(turn[i]), quatW.getFloat(turn[i]),
                            tupleX.getFloat(half[i]), tupleY.getFloat(half[i]), tupleZ.getFloat(half[i]));
                }
                RagdollBodies.Body b = RagdollBodies.body("ragdollified", (Integer) ragdollIdOf.invoke(r), null, (Integer) originalIdOf.invoke(r), parts);
                if (b != null) out.add(b);
            }
        } catch (Throwable t) {
            allRagdolls = null;
            SimpleBlood.LOGGER.warn("Ragdollified: ragdoll parts not readable any more; blood passes through its ragdolls and shots do not hit them ({})", t.toString());
        }
    }

    private static void saroBoxes(ClientLevel level, java.util.List<RagdollBodies.Body> out) {
        if (!saroBoxesLooked) {
            saroBoxesLooked = true;
            try {
                ClassLoader cl = OtherRagdolls.class.getClassLoader();
                saroRotation = saroTransform.getReturnType().getMethod("getRotation");
                Class<?> quat = saroRotation.getReturnType();
                saroQx = quat.getMethod("getX");
                saroQy = quat.getMethod("getY");
                saroQz = quat.getMethod("getZ");
                saroQw = quat.getMethod("getW");
                saroBoxSize = Class.forName("de.sarocesch.sarosragdoll.ragdoll.physics.EnumRagdollBodyPart", false, cl).getMethod("getBoxSize");
            } catch (Throwable t) {
                saroRotation = null;
                SimpleBlood.LOGGER.warn("Saro's Player Ragdoll: part boxes not readable; blood passes through its ragdolls ({})", t.toString());
            }
        }
        if (saroRotation == null) return;
        try {
            for (Entity e : level.entitiesForRendering()) {
                if (!saroEntity.isInstance(e)) continue;
                com.simpleblood.geom.OrientedBox[] parts = new com.simpleblood.geom.OrientedBox[saroParts.length];
                for (int i = 0; i < parts.length; i++) {
                    Object sync = saroPart.invoke(e, saroParts[i]);
                    if (sync == null) continue;
                    Object transform = saroTransform.invoke(sync);
                    Object p = saroPosition.invoke(transform), q = saroRotation.invoke(transform), half = saroBoxSize.invoke(saroParts[i]);
                    if (p == null || q == null || half == null) continue;
                    parts[i] = box(vx.getFloat(p), vy.getFloat(p), vz.getFloat(p),
                            (Float) saroQx.invoke(q), (Float) saroQy.invoke(q), (Float) saroQz.invoke(q), (Float) saroQw.invoke(q),
                            vx.getFloat(half), vy.getFloat(half), vz.getFloat(half));
                }
                RagdollBodies.Body b = RagdollBodies.body("saros", e.getId(), null, (Integer) saroPlayerId.invoke(e), parts);
                if (b != null) out.add(b);
            }
        } catch (Throwable t) {
            saroRotation = null;
            SimpleBlood.LOGGER.warn("Saro's Player Ragdoll: part boxes not readable any more; blood passes through its ragdolls ({})", t.toString());
        }
    }

    private static com.simpleblood.geom.OrientedBox box(double x, double y, double z, double qx, double qy, double qz, double qw,
                                                        double hx, double hy, double hz) {
        try {
            return new com.simpleblood.geom.OrientedBox(x, y, z, qx, qy, qz, qw, Math.abs(hx), Math.abs(hy), Math.abs(hz));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
