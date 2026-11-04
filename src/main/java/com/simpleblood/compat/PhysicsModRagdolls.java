package com.simpleblood.compat;

import com.simpleblood.BloodColor;
import com.simpleblood.BloodKind;
import com.simpleblood.BloodParticles;
import com.simpleblood.BloodSounds;
import com.simpleblood.ClientBloodParticleSpawner;
import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.particle.BloodParticle;
import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class PhysicsModRagdolls {

    private PhysicsModRagdolls() {}

    private static final String MOD_ID = "physicsmod";
    private static final int MATCH_WINDOW = 60;
    private static final double MATCH_RANGE = 3.0;
    private static final double IMPACT_SPEED = 0.22, IMPACT_SPEED_GORE = 0.15;
    private static final int PAINT_PER_TICK = 6;

    private static final RandomSource RNG = RandomSource.create();

    private static boolean looked, usable;
    private static Method getInstanceNullable;
    private static Field physicsWorldField;
    private static Field worldRagdolls;
    private static Field worldBodies;
    private static Method getOffset;
    private static Field btBodies;
    private static Class<?> breakableRagdoll;
    private static Method linkedRigid;
    private static Method rigidEntity;
    private static Method rigidDestroyed;
    private static Field renderablePosition;
    private static Field renderableType;

    public static boolean anyRagdollMod() {
        return available() || OtherRagdolls.any();
    }

    public static boolean available() {
        if (!looked) {
            looked = true;
            usable = Platform.modIds().contains(MOD_ID) && lookUp();
        }
        return usable;
    }

    private static boolean lookUp() {
        try {
            ClassLoader cl = PhysicsModRagdolls.class.getClassLoader();
            Class<?> mod = Class.forName("net.diebuddies.physics.PhysicsMod", false, cl);
            Class<?> world = Class.forName("net.diebuddies.physics.PhysicsWorld", false, cl);
            Class<?> ragdoll = Class.forName("net.diebuddies.physics.ragdoll.Ragdoll", false, cl);
            Class<?> linked = Class.forName("net.diebuddies.physics.ragdoll.Ragdoll$LinkedBody", false, cl);
            Class<?> rigid = Class.forName("net.diebuddies.physics.IRigidBody", false, cl);
            Class<?> renderable = Class.forName("net.diebuddies.physics.PhysicsRenderable", false, cl);
            getInstanceNullable = mod.getMethod("getInstanceNullable", ClientLevel.class);
            physicsWorldField = mod.getField("physicsWorld");
            worldRagdolls = world.getDeclaredField("ragdolls");
            worldRagdolls.setAccessible(true);
            worldBodies = world.getDeclaredField("bodies");
            worldBodies.setAccessible(true);
            getOffset = world.getMethod("getOffset");
            btBodies = ragdoll.getField("btBodies");
            linkedRigid = linked.getMethod("rigid");
            rigidEntity = rigid.getMethod("getEntity");
            rigidDestroyed = rigid.getMethod("isDestroyed");
            renderablePosition = renderable.getField("position");
            renderableType = renderable.getField("type");
            try {
                breakableRagdoll = Class.forName("net.diebuddies.physics.ragdoll.BreakableRagdoll", false, cl);
            } catch (ClassNotFoundException e) {
                breakableRagdoll = null;
            }
            if (breakableRagdoll != null) {
                try {
                    Class<?> joint = Class.forName("physx.extensions.PxJoint", false, cl);
                    Class<?> tuple = Class.forName("net.diebuddies.physics.PhysicsWorld$Tuple", false, cl);
                    pxJoints = breakableRagdoll.getField("pxJoints");
                    jointParents = world.getMethod("getJointParents", joint);
                    tupleA = tuple.getMethod("getA");
                    tupleB = tuple.getMethod("getB");
                } catch (Throwable t) {
                    pxJoints = null;
                    SimpleBlood.LOGGER.warn("Physics Mod: ragdoll joints not readable, parts knocked off will not spurt ({})", t.toString());
                }
                try {
                    Class<?> planned = Class.forName("net.diebuddies.physics.ragdoll.RagdollJoint", false, cl);
                    plannedJoints = ragdoll.getField("joints");
                    plannedBodies = ragdoll.getField("bodies");
                    linkedEntity = linked.getMethod("entity");
                    plannedIndex1 = planned.getField("index1");
                    plannedIndex2 = planned.getField("index2");
                    plannedPoint1 = planned.getField("point1");
                    plannedPoint2 = planned.getField("point2");
                } catch (Throwable t) {
                    plannedJoints = null;
                }
            }
            SimpleBlood.LOGGER.info("Physics Mod found: ragdolls and mob pieces bleed");
            return true;
        } catch (Throwable t) {
            SimpleBlood.LOGGER.warn("Physics Mod found, but not a version Simple Blood knows; its ragdolls will not bleed ({})", t.toString());
            return false;
        }
    }

    private static void disable(Throwable t) {
        usable = false;
        SimpleBlood.LOGGER.warn("Physics Mod ragdoll blood switched off after an error", t);
    }

    private record Death(Vec3 pos, long tick, BloodColor.Color colour, BloodKind kind, boolean glows,
                         boolean stains, float size, int entityId, String type) {}

    private static final class Bleeding {
        final Death death;
        final long born;
        final boolean gore;
        final Object ragdoll;
        final List<Object> pieces;
        Vec3[] last;
        Vec3[] velocity;
        int pooled;
        long stillSince = -1;

        int severed;
        final Vec3[] stumpAt = new Vec3[8], lastSeen = new Vec3[8];
        final Spurt[] stumps = new Spurt[8];
        Spurt cut;

        float blood = 1f;
        private long drained = Long.MIN_VALUE;

        void drain(long now) {
            if (drained == now) return;
            drained = now;
            float ticks = spurtTicks();
            int wounds = 0;
            for (Spurt sp : stumps) if (sp != null) wounds++;
            wounds += tears.size();
            if (cut != null) wounds++;
            if (pieceWounds != null) wounds += pieceWounds.size();
            blood = Math.max(0f, blood - (STILL_DRAIN + WOUND_DRAIN * wounds * blood) / ticks);
            for (Tear t : tears) t.atPiece().own = Math.max(0f, t.atPiece().own - (STILL_DRAIN + WOUND_DRAIN * t.atPiece().own) / ticks);
        }

        float fresh(long now) {
            return Math.min(blood, Math.max(0f, 1f - (float) (now - born) / bleedTicks()));
        }

        float pressure(Spurt sp) {
            return sp.own >= 0f ? sp.own : blood;
        }

        boolean pumping() {
            if (blood > DRY && (cut != null || !tears.isEmpty())) return true;
            for (Spurt sp : stumps) if (sp != null && blood > DRY) return true;
            if (pieceWounds != null && !pieceWounds.isEmpty() && blood > DRY) return true;
            for (Tear t : tears) if (t.atPiece().own > DRY) return true;
            return false;
        }

        int[][] links;
        Object[] linkJoints;
        boolean[] linkTorn;
        Vec3[][] linkAnchors;
        String[] linkSource;
        Vec3[][] linkPlan;
        double[] linkRest;
        int[] linkStretch;
        Vec3[] seen;
        final List<Tear> tears = new ArrayList<>(2);
        List<Object> firstRigids;
        List<PieceWound> pieceWounds;

        Bleeding(Death death, long born, boolean gore, Object ragdoll, List<Object> pieces) {
            this.death = death;
            this.born = born;
            this.gore = gore;
            this.ragdoll = ragdoll;
            this.pieces = pieces;
        }
    }

    private static final List<Death> DEATHS = new ArrayList<>();
    private static final Set<Object> SEEN = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<Bleeding> BLEEDING = new ArrayList<>();
    private static ClientLevel lastLevel;

    public static void entityDied(LivingEntity entity) {
        if (!enabled() || !(entity.level() instanceof ClientLevel level)) return;
        for (Death d : DEATHS) if (d.entityId() == entity.getId()) return;
        DEATHS.add(deathOf(entity, level));
        SimpleBlood.LOGGER.info("[decap] death told: {}", entity.getType());
        if (DEATHS.size() > 64) DEATHS.remove(0);
    }

    private static Death deathOf(LivingEntity entity, ClientLevel level) {
        return new Death(entity.position().add(0, entity.getBbHeight() * 0.5, 0), level.getGameTime(),
                BloodColor.getBloodColor(entity), SimpleBlood.bloodKindOf(entity),
                SimpleBlood.bloodGlows(entity), SimpleBlood.shouldEntityTransformToStains(entity),
                SimpleBlood.getParticleSizeMultiplier(entity), entity.getId(),
                String.valueOf(net.minecraft.world.entity.EntityType.getKey(entity.getType())));
    }

    private static boolean enabled() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg != null && cfg.globalEnabled() && cfg.general.physicsModRagdolls && (available() || OtherRagdolls.any());
    }

    private static SimpleBloodConfig.PhysicsModSettings settings() {
        return SimpleBloodClient.getConfig().physicsMod;
    }

    private static int bleedTicks() {
        return 20 * Math.max(1, settings().bleedSeconds);
    }

    private static final int NORMAL_SUPPLY_TICKS = 300;

    private static int spurtTicks() {
        return Math.max(20, Math.round(NORMAL_SUPPLY_TICKS * Math.max(10, settings().bloodSupply) / 100f));
    }

    private static int scaled(int base, int percent) {
        float f = base * Math.max(0, percent) / 100f;
        int n = (int) f;
        return RNG.nextFloat() < f - n ? n + 1 : n;
    }

    public static void tick(ClientLevel level) {
        if (level != lastLevel) {
            lastLevel = level;
            LIVE_RIGIDS = null;
            DEATHS.clear();
            SEEN.clear();
            SEEN_OTHER.clear();
            BLEEDING.clear();
        }
        if (!enabled()) {
            BLEEDING.clear();
            return;
        }
        long now = level.getGameTime();
        DEATHS.removeIf(d -> now - d.tick > MATCH_WINDOW);
        if (available()) {
            try {
                tickBodies(level);
            } catch (Throwable t) {
                disable(t);
            }
        }
        tickOtherRagdolls(level, now);
    }

    private static final Set<Object> SEEN_OTHER = new java.util.HashSet<>();

    private static Death bleedingOf(OtherRagdolls.Key key) {
        Bleeding b = bleedingFor(key);
        return b == null ? null : b.death;
    }

    private static Bleeding bleedingFor(OtherRagdolls.Key key) {
        for (Bleeding b : BLEEDING) if (key.equals(b.ragdoll)) return b;
        return null;
    }

    private static void tickOtherRagdolls(ClientLevel level, long now) {
        if (!OtherRagdolls.any()) return;
        Map<OtherRagdolls.Key, OtherRagdolls.Body> bodies = OtherRagdolls.collect(level);
        SEEN_OTHER.retainAll(bodies.keySet());
        bodies.forEach((key, body) -> {
            if (SEEN_OTHER.contains(key)) return;
            List<Vec3> present = new ArrayList<>();
            for (Vec3 p : body.parts()) if (p != null && p.lengthSqr() > 1.0e-6) present.add(p);
            if (present.isEmpty()) return;
            SEEN_OTHER.add(key);
            Death death = null;
            if (body.source() >= 0) {
                if (body.owner() != null) death = deathOf(body.owner(), level);
                else death = bleedingOf(new OtherRagdolls.Key("ragdollified", body.source()));
            } else {
                death = DEATHS.isEmpty() ? null : closest(centre(present));
                if (death != null) DEATHS.remove(death);
                else if (body.owner() != null) death = deathOf(body.owner(), level);
            }
            if (death != null) {
                Bleeding b = new Bleeding(death, now, body.source() >= 0, key, null);
                if (body.source() >= 0) {
                    Bleeding from = bleedingFor(new OtherRagdolls.Key("ragdollified", body.source()));
                    b.blood = Math.min(PIECE_SHARE, (from != null ? from.blood : 1f) * 0.5f);
                }
                BLEEDING.add(b);
            }
        });
        Iterator<Bleeding> it = BLEEDING.iterator();
        while (it.hasNext()) {
            Bleeding b = it.next();
            if (!(b.ragdoll instanceof OtherRagdolls.Key key)) continue;
            OtherRagdolls.Body body = bodies.get(key);
            b.drain(now);
            boolean bleeding = b.fresh(now) > 0f;
            if (body == null || !bleeding && !b.pumping()) {
                it.remove();
                continue;
            }
            if ("ragdollified".equals(key.mod())) stumps(level, b, key.id(), body.parts(), now);
            if (body.cutAt() != null) cutEnd(level, b, body.cutAt(), body.cutDir(), now);
            if (bleeding) bleed(level, b, body.parts(), now);
        }
    }

    record Tear(int body, int piece, Vec3 bodyLocal, Vec3 pieceLocal, Vec3 bodyAim, Vec3 pieceAim, Spurt atBody, Spurt atPiece) {}

    private static void findTears(ClientLevel level, Bleeding b, Object world, Vec3[] parts,
                                  java.util.function.IntFunction<org.joml.Quaternionf> turn, long now) throws Exception {
        if (b.links == null) {
            b.links = new int[0][];
            b.linkJoints = new Object[0];
            b.linkTorn = new boolean[0];
            b.seen = parts.clone();
            if (pxJoints == null || breakableRagdoll == null || !breakableRagdoll.isInstance(b.ragdoll)
                    || !(pxJoints.get(b.ragdoll) instanceof List<?> joints)) return;
            List<int[]> links = new ArrayList<>();
            List<Object> held = new ArrayList<>();
            List<Vec3[]> anchors = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            List<Vec3[]> plans = new ArrayList<>();
            List<Double> rests = new ArrayList<>();
            int[] planIndex = planIndices(b);
            for (Object joint : joints) {
                Object tuple = jointParents.invoke(world, joint);
                if (tuple == null) continue;
                int ia = indexOf(b.firstRigids, tupleA.invoke(tuple)), ib = indexOf(b.firstRigids, tupleB.invoke(tuple));
                if (ia < 0 || ib < 0) continue;
                links.add(new int[]{ia, ib});
                held.add(joint);
                Vec3[] plan = plannedAnchors(b, planIndex, ia, ib, parts, turn, false);
                Vec3[] anchor = plan != null && meets(plan, ia, ib, parts, turn) ? plan : null;
                anchors.add(anchor);
                plans.add(plan);
                rests.add(parts[ia] != null && parts[ib] != null ? parts[ia].distanceTo(parts[ib]) : -1);
                sources.add(anchor != null ? "plan" : "between");
            }
            b.links = links.toArray(new int[0][]);
            b.linkJoints = held.toArray();
            b.linkTorn = new boolean[b.links.length];
            b.linkAnchors = anchors.toArray(new Vec3[0][]);
            b.linkPlan = plans.toArray(new Vec3[0][]);
            b.linkRest = rests.stream().mapToDouble(Double::doubleValue).toArray();
            b.linkStretch = new int[b.links.length];
            b.linkSource = sources.toArray(new String[0]);
            SimpleBlood.LOGGER.info("[decap] {} ragdoll watched: {} parts, {} joints, anchors {}",
                    b.death.type(), b.firstRigids.size(), joints.size(), sources);
            return;
        }
        if (b.links.length > 0 && pxJoints.get(b.ragdoll) instanceof List<?> current) {
            for (int k = 0; k < b.links.length; k++) {
                if (b.linkTorn[k]) continue;
                Object joint = b.linkJoints[k];
                boolean gone = true;
                for (Object j : current) if (j == joint) { gone = false; break; }
                if (!gone && !pulledApart(b, k, parts, turn)) continue;
                b.linkTorn[k] = true;
                int a = b.links[k][0], c = b.links[k][1];
                int body = joinedCount(b, a) >= joinedCount(b, c) ? a : c, piece = body == a ? c : a;
                Vec3 bodyWas = b.seen[body] != null ? b.seen[body] : parts[body];
                Vec3 pieceWas = b.seen[piece] != null ? b.seen[piece] : parts[piece];
                if (bodyWas == null || pieceWas == null) continue;
                Vec3[] anchor = b.linkAnchors[k];
                Vec3 bodyLocal, pieceLocal;
                if (anchor != null) {
                    bodyLocal = anchor[body == a ? 0 : 1];
                    pieceLocal = anchor[body == a ? 1 : 0];
                } else {
                    Vec3 mid = bodyWas.add(pieceWas).scale(0.5);
                    bodyLocal = local(mid.subtract(bodyWas), turn.apply(body));
                    pieceLocal = local(mid.subtract(pieceWas), turn.apply(piece));
                }
                SimpleBlood.LOGGER.info("[decap] {} joint {}-{} tore ({}, {} still joined to the body), wound {} blocks off the body's middle, {} off the piece's",
                        b.death.type(), body, piece, gone ? "taken away" : "broken", joinedCount(b, body),
                        String.format(java.util.Locale.ROOT, "%.2f", bodyLocal.length()),
                        String.format(java.util.Locale.ROOT, "%.2f", pieceLocal.length()));
                Vec3 apart = pieceWas.subtract(bodyWas);
                Vec3 bodyAim = aim(local(apart, turn.apply(body)), bodyLocal);
                Vec3 pieceAim = aim(local(apart.reverse(), turn.apply(piece)), pieceLocal);
                b.tears.add(new Tear(body, piece, bodyLocal, pieceLocal, bodyAim, pieceAim, new Spurt(now),
                        new Spurt(now, Math.min(PIECE_SHARE, b.blood * 0.5f))));
                com.simpleblood.Tally.count("ragdoll.tear");
                Vec3 at = bodyWas.add(world(bodyLocal, turn.apply(body) == null ? new org.joml.Quaternionf() : turn.apply(body)));
                if (b.death.kind == BloodKind.LIQUID) BloodSounds.stump(level, at);
            }
        }
        Vec3[] moved = new Vec3[parts.length];
        for (int i = 0; i < parts.length && i < b.seen.length; i++) {
            if (parts[i] != null && b.seen[i] != null) moved[i] = parts[i].subtract(b.seen[i]);
            if (parts[i] != null) b.seen[i] = parts[i];
        }
        if (b.tears.isEmpty() || b.death.kind != BloodKind.LIQUID) return;
        prepare(b.death);
        try {
            for (Tear t : b.tears) {
                pumpAt(level, t.atBody(), b.pressure(t.atBody()), parts[t.body()], moved[t.body()], t.bodyLocal(), t.bodyAim(), turn.apply(t.body()), now);
                pumpAt(level, t.atPiece(), b.pressure(t.atPiece()), parts[t.piece()], moved[t.piece()], t.pieceLocal(), t.pieceAim(), turn.apply(t.piece()), now);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static Vec3 aim(Vec3 away, Vec3 local) {
        boolean hasAway = away.lengthSqr() > 1.0e-6, hasOut = local.lengthSqr() > 0.05 * 0.05;
        if (hasAway && hasOut) {
            Vec3 both = away.normalize().add(local.normalize());
            if (both.lengthSqr() > 0.1) return both.normalize();
        }
        if (hasAway) return away.normalize();
        return hasOut ? local.normalize() : new Vec3(0, 1, 0);
    }

    private static final double TEAR_GAP = 0.3;

    private static boolean pulledApart(Bleeding b, int k, Vec3[] parts, java.util.function.IntFunction<org.joml.Quaternionf> turn) {
        int a = b.links[k][0], c = b.links[k][1];
        if (a >= parts.length || c >= parts.length || parts[a] == null || parts[c] == null) return false;
        double gap;
        Vec3[] plan = b.linkPlan[k];
        org.joml.Quaternionf ra = turn.apply(a), rc = turn.apply(c);
        if (plan != null && ra != null && rc != null) {
            gap = parts[a].add(world(plan[0], ra)).distanceTo(parts[c].add(world(plan[1], rc)));
        } else if (b.linkRest[k] >= 0) {
            gap = parts[a].distanceTo(parts[c]) - b.linkRest[k];
        } else {
            return false;
        }
        if (gap <= TEAR_GAP) {
            b.linkStretch[k] = 0;
            return false;
        }
        return ++b.linkStretch[k] >= 2;
    }

    private static boolean meets(Vec3[] plan, int ia, int ib, Vec3[] parts, java.util.function.IntFunction<org.joml.Quaternionf> turn) {
        org.joml.Quaternionf ra = turn.apply(ia), rb = turn.apply(ib);
        if (parts[ia] == null || parts[ib] == null || ra == null || rb == null) return false;
        return parts[ia].add(world(plan[0], ra)).distanceTo(parts[ib].add(world(plan[1], rb))) <= 0.35;
    }

    private static int[] planIndices(Bleeding b) {
        if (plannedJoints == null || b.firstRigids == null) return null;
        try {
            if (!(btBodies.get(b.ragdoll) instanceof List<?> linkedBodies)
                    || !(plannedBodies.get(b.ragdoll) instanceof List<?> bodies)) return null;
            int[] out = new int[b.firstRigids.size()];
            java.util.Arrays.fill(out, -1);
            for (Object linkedBody : linkedBodies) {
                int i = indexOf(b.firstRigids, linkedRigid.invoke(linkedBody));
                Object entity = linkedEntity.invoke(linkedBody);
                if (i < 0 || entity == null) continue;
                for (int k = 0; k < bodies.size(); k++) if (bodies.get(k) == entity) { out[i] = k; break; }
            }
            return out;
        } catch (Throwable t) {
            plannedJoints = null;
            return null;
        }
    }

    private static Vec3[] plannedAnchors(Bleeding b, int[] planIndex, int ia, int ib, Vec3[] parts,
                                         java.util.function.IntFunction<org.joml.Quaternionf> turn, boolean check) {
        if (planIndex == null || ia >= planIndex.length || ib >= planIndex.length) return null;
        int ea = planIndex[ia], eb = planIndex[ib];
        org.joml.Quaternionf ra = turn.apply(ia), rb = turn.apply(ib);
        if (ea < 0 || eb < 0 || parts[ia] == null || parts[ib] == null || ra == null || rb == null) return null;
        try {
            if (!(plannedJoints.get(b.ragdoll) instanceof List<?> plan)) return null;
            for (Object j : plan) {
                int i1 = plannedIndex1.getInt(j), i2 = plannedIndex2.getInt(j);
                if (!(i1 == ea && i2 == eb) && !(i1 == eb && i2 == ea)) continue;
                if (!(plannedPoint1.get(j) instanceof org.joml.Vector3d p1) || !(plannedPoint2.get(j) instanceof org.joml.Vector3d p2)) return null;
                Vec3 onA = i1 == ea ? new Vec3(p1.x, p1.y, p1.z) : new Vec3(p2.x, p2.y, p2.z);
                Vec3 onB = i1 == ea ? new Vec3(p2.x, p2.y, p2.z) : new Vec3(p1.x, p1.y, p1.z);
                if (check && parts[ia].add(world(onA, ra)).distanceTo(parts[ib].add(world(onB, rb))) > 0.35) return null;
                return new Vec3[]{onA, onB};
            }
        } catch (Throwable t) {
            plannedJoints = null;
        }
        return null;
    }

    private static int indexOf(List<Object> list, Object o) {
        if (list == null || o == null) return -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i) == o) return i;
        return -1;
    }

    private static void pumpAt(ClientLevel level, Spurt s, float pressure, Vec3 centre, Vec3 moved, Vec3 local, Vec3 aim,
                               org.joml.Quaternionf turn, long now) {
        if (centre == null) return;
        Vec3 off = turn == null ? local : world(local, turn);
        Vec3 dir = turn == null ? aim : world(aim, turn);
        Vec3 at = centre.add(off);
        Vec3 swept = s.lastAt != null && s.lastAt.distanceToSqr(at) < 4 ? at.subtract(s.lastAt) : moved;
        s.lastAt = at;
        pump(level, s, at, dir, swept == null ? Vec3.ZERO : swept, pressure, now);
    }

    private static int joinedCount(Bleeding b, int i) {
        int n = b.firstRigids == null ? 0 : b.firstRigids.size();
        if (i < 0 || i >= n) return 0;
        boolean[] in = new boolean[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        in[i] = true;
        queue.add(i);
        int count = 0;
        while (!queue.isEmpty()) {
            int k = queue.poll();
            count++;
            for (int l = 0; l < b.links.length; l++) {
                if (b.linkTorn[l]) continue;
                int other = b.links[l][0] == k ? b.links[l][1] : b.links[l][1] == k ? b.links[l][0] : -1;
                if (other >= 0 && other < n && !in[other]) {
                    in[other] = true;
                    queue.add(other);
                }
            }
        }
        return count;
    }

    private static Vec3 local(Vec3 v, org.joml.Quaternionf turn) {
        if (turn == null) return v;
        org.joml.Vector3f w = new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z).rotate(new org.joml.Quaternionf(turn).conjugate());
        return new Vec3(w.x, w.y, w.z);
    }

    private static Vec3 world(Vec3 v, org.joml.Quaternionf turn) {
        org.joml.Vector3f w = new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z).rotate(turn);
        return new Vec3(w.x, w.y, w.z);
    }

    private static final int FIRST_GUSH = 30;
    private static final int BEAT_TICKS = 4;

    private static final float DRY = 0.02f;
    private static final float STILL_DRAIN = 0.4f;
    private static final float WOUND_DRAIN = 2f;
    private static final float PIECE_SHARE = 0.35f;

    static final class Spurt {
        final long since;
        long nextBeat;
        Vec3 lastAt;
        float own = -1f;

        Spurt(long since) {
            this.since = since;
            this.nextBeat = since + FIRST_GUSH;
        }

        Spurt(long since, float own) {
            this(since);
            this.own = own;
        }
    }

    private static void stumps(ClientLevel level, Bleeding b, int id, Vec3[] parts, long now) {
        Vec3 torso = parts.length > 0 ? parts[0] : null;
        if (torso == null) return;
        int mask = OtherRagdolls.severedMask(id);
        if (mask != 0) com.simpleblood.Tally.count("ragdoll.severed");
        if (mask != b.severed) {
            for (int i = 1; i < Math.min(parts.length, b.stumpAt.length); i++) {
                if ((mask & (1 << i)) == 0 || (b.severed & (1 << i)) != 0) continue;
                Vec3 was = b.lastSeen[i];
                if (was == null) continue;
                b.stumps[i] = new Spurt(now);
                b.stumpAt[i] = was.subtract(torso).scale(0.6);
                if (b.death.kind == BloodKind.LIQUID) BloodSounds.stump(level, torso.add(b.stumpAt[i]));
            }
            b.severed = mask;
        }
        for (int i = 0; i < Math.min(parts.length, b.lastSeen.length); i++) if (parts[i] != null) b.lastSeen[i] = parts[i];
        if (b.death.kind != BloodKind.LIQUID) return;
        prepare(b.death);
        try {
            for (int i = 1; i < b.stumpAt.length; i++) {
                Vec3 off = b.stumpAt[i];
                Spurt s = b.stumps[i];
                if (off == null || s == null) continue;
                pump(level, s, torso.add(off), off.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : off.normalize(), Vec3.ZERO, b.blood, now);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static void cutEnd(ClientLevel level, Bleeding b, Vec3 at, Vec3 dir, long now) {
        if (b.death.kind != BloodKind.LIQUID) return;
        if (b.cut == null) b.cut = new Spurt(b.born);
        if (b.blood <= DRY) return;
        prepare(b.death);
        try {
            pump(level, b.cut, at, dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : dir.normalize(), Vec3.ZERO, b.blood, now);
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static void pump(ClientLevel level, Spurt s, Vec3 at, Vec3 dir, Vec3 carried, float pressure, long now) {
        if (pressure <= DRY) return;
        int percent = settings().spurts;
        if (percent <= 0) return;
        long age = now - s.since;
        float life = (float) Math.sqrt(Math.min(1f, pressure));
        com.simpleblood.Tally.count("ragdoll.stump");
        long intoBeat = now - s.nextBeat;
        if (intoBeat >= BEAT_TICKS) {
            s.nextBeat = now + 8 + Math.round((1f - life) * 10);
            intoBeat = now - s.nextBeat;
        }
        int drops;
        double speed;
        if (age < FIRST_GUSH) {
            float gush = 1f - age / (float) FIRST_GUSH;
            drops = scaled(RNG.nextFloat() < 0.4f + 0.6f * gush ? 3 : 2, percent);
            speed = (0.10 + 0.14 * gush) * (0.35 + 0.65 * life);
        } else if (intoBeat >= 0) {
            drops = scaled(life > 0.5f ? 3 : 2, Math.round(percent * life));
            speed = 0.07 + 0.15 * life;
        } else {
            drops = RNG.nextFloat() < 0.55f * life ? scaled(1, percent) : 0;
            speed = 0.025 + 0.02 * life;
        }
        if (drops <= 0) return;
        Vec3 side = Math.abs(dir.y) < 0.9 ? dir.cross(new Vec3(0, 1, 0)).normalize() : dir.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 over = dir.cross(side);
        for (int k = 0; k < drops; k++) {
            double tilt = SPRAY_CONE * RNG.nextDouble(), round = RNG.nextDouble() * Math.PI * 2;
            double along = Math.cos(tilt), across = Math.sin(tilt);
            double v = speed * (0.85 + 0.3 * RNG.nextDouble());
            double cx = Math.cos(round) * across, cy = Math.sin(round) * across;
            ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_SPRAY, at.x, at.y, at.z,
                    (dir.x * along + side.x * cx + over.x * cy) * v + carried.x * CARRY,
                    (dir.y * along + side.y * cx + over.y * cy) * v + carried.y * CARRY,
                    (dir.z * along + side.z * cx + over.z * cy) * v + carried.z * CARRY);
        }
    }

    private static final double SPRAY_CONE = Math.toRadians(50);
    private static final double CARRY = 1.0;

    private static void tickBodies(ClientLevel level) throws Exception {
        long now = level.getGameTime();
        Object mod = getInstanceNullable.invoke(null, level);
        if (mod == null) return;
        Object world = physicsWorldField.get(mod);
        if (world == null) return;
        org.joml.Vector3d offset = (org.joml.Vector3d) getOffset.invoke(world);

        Set<Object> aliveRagdolls = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> ragdollBodies = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> live = Collections.newSetFromMap(new IdentityHashMap<>());
        if (worldRagdolls.get(world) instanceof Iterable<?> all) for (Object ragdoll : all) live.addAll(rigidsOf(ragdoll));
        if (worldBodies.get(world) instanceof Iterable<?> all) for (Object rigid : all) live.add(rigid);
        LIVE_RIGIDS = live;
        if (worldRagdolls.get(world) instanceof Iterable<?> ragdolls) {
            for (Object ragdoll : ragdolls) {
                aliveRagdolls.add(ragdoll);
                List<Object> rigids = rigidsOf(ragdoll);
                ragdollBodies.addAll(rigids);
                if (SEEN.contains(ragdoll) || rigids.isEmpty()) continue;
                if (!isMob(rigids.get(0))) {
                    SEEN.add(ragdoll);
                    continue;
                }
                Vec3 middle = centre(positions(rigids, offset));
                Death death = DEATHS.isEmpty() ? null : closest(middle);
                if (death == null) death = takenAsPieces(rigids);
                if (death == null) death = dyingNear(level, middle);
                if (death == null) {
                    giveUpLater(ragdoll, now);
                    if (SEEN.contains(ragdoll)) {
                        com.simpleblood.Tally.count("ragdoll.unmatched");
                        SimpleBlood.LOGGER.info("[decap] a ragdoll matched no death near it and is left alone ({} deaths waiting)", DEATHS.size());
                    }
                    continue;
                }
                SEEN.add(ragdoll);
                DEATHS.remove(death);
                boolean breaks = breakableRagdoll != null && breakableRagdoll.isInstance(ragdoll);
                BLEEDING.add(new Bleeding(death, now, breaks, ragdoll, null));
                com.simpleblood.Tally.count("ragdoll.matched");
                SimpleBlood.LOGGER.info("[decap] {} ragdoll matched to its death ({}, {} parts)", death.type(), breaks ? "breakable" : "whole", rigids.size());
                List<com.simpleblood.mob.RigidCoats.Limb> limbs = new ArrayList<>(rigids.size());
                for (Object rigid : rigids) limbs.add(new PhysicsLimb(rigid, world));
                com.simpleblood.mob.RigidCoats.attach(death.entityId(), limbs);
            }
        }

        if (!DEATHS.isEmpty() && settings().pieces && worldBodies.get(world) instanceof Iterable<?> bodies) {
            Map<Death, List<Object>> batches = new IdentityHashMap<>();
            for (Object rigid : bodies) {
                if (SEEN.contains(rigid) || ragdollBodies.contains(rigid)) continue;
                Object renderable = rigidEntity.invoke(rigid);
                if (renderable == null || !(renderableType.get(renderable) instanceof Enum<?> type)
                        || !"MOB".equals(type.name())) {
                    SEEN.add(rigid);
                    continue;
                }
                Vec3 p = position(rigid, offset);
                Death death = p == null ? null : closest(p);
                if (death == null) {
                    giveUpLater(rigid, now);
                    continue;
                }
                SEEN.add(rigid);
                batches.computeIfAbsent(death, d -> new ArrayList<>()).add(rigid);
            }
            batches.forEach((death, pieces) -> {
                DEATHS.remove(death);
                BLEEDING.add(new Bleeding(death, now, true, null, pieces));
                SimpleBlood.LOGGER.info("[decap] {} matched to {} loose pieces", death.type(), pieces.size());
                List<com.simpleblood.mob.RigidCoats.Limb> limbs = new ArrayList<>(pieces.size());
                for (Object rigid : pieces) limbs.add(new PhysicsLimb(rigid, world));
                com.simpleblood.mob.RigidCoats.attach(death.entityId(), limbs);
            });
        }

        Iterator<Bleeding> it = BLEEDING.iterator();
        while (it.hasNext()) {
            Bleeding b = it.next();
            if (b.ragdoll instanceof OtherRagdolls.Key) continue;
            b.drain(now);
            boolean bleeding = b.fresh(now) > 0f;
            if (!bleeding && !b.pumping() && !mayTear(b) || (b.ragdoll != null && !aliveRagdolls.contains(b.ragdoll))) {
                it.remove();
                continue;
            }
            List<Object> rigids = b.ragdoll != null ? rigidsOf(b.ragdoll) : b.pieces;
            Vec3[] parts = new Vec3[rigids.size()];
            boolean any = false;
            for (int i = 0; i < parts.length; i++) {
                Object rigid = rigids.get(i);
                if (Boolean.TRUE.equals(rigidDestroyed.invoke(rigid))) continue;
                parts[i] = position(rigid, offset);
                any |= parts[i] != null;
            }
            if (!any) {
                it.remove();
                continue;
            }
            if (b.ragdoll != null && !rigids.isEmpty() && settings().spurts > 0) {
                if (b.firstRigids == null) b.firstRigids = new ArrayList<>(rigids);
                List<Object> limbs = b.firstRigids;
                Vec3[] whole = new Vec3[limbs.size()];
                for (int i = 0; i < whole.length; i++) {
                    Object rigid = limbs.get(i);
                    if (!Boolean.TRUE.equals(rigidDestroyed.invoke(rigid))) whole[i] = position(rigid, offset);
                }
                try {
                    findTears(level, b, world, whole, i -> new PhysicsLimb(limbs.get(i), world).rotation(1f), now);
                } catch (Throwable t) {
                    pxJoints = null;
                    SimpleBlood.LOGGER.warn("Physics Mod: ragdoll joints not readable any more, parts knocked off will not spurt ({})", t.toString());
                }
            }
            if (b.pieces != null && b.pieceWounds == null && now - b.born >= 2) b.pieceWounds = pieceWounds(level, b.death, b.pieces, world, offset, now);
            if (b.pieceWounds != null && !b.pieceWounds.isEmpty() && settings().spurts > 0) pumpPieces(level, b, parts, world, now);
            if (bleeding) bleed(level, b, parts, now);
        }
    }

    private static boolean mayTear(Bleeding b) {
        if (!b.gore || b.ragdoll == null || pxJoints == null || settings().spurts <= 0) return false;
        if (b.links == null) return true;
        for (boolean torn : b.linkTorn) if (!torn) return true;
        return false;
    }

    private static final int MAX_PIECE_WOUNDS = 4;

    record PieceWound(int index, Vec3 local, Vec3 aim, Spurt spurt) {}

    private static List<PieceWound> pieceWounds(ClientLevel level, Death death, List<Object> pieces, Object world,
                                                org.joml.Vector3d offset, long now) {
        if (death.kind != BloodKind.LIQUID || pieces.isEmpty()) return List.of();
        List<PieceWound> out = new ArrayList<>();
        int n = pieces.size(), wounds = Math.min(MAX_PIECE_WOUNDS, n);
        for (int k = 0; k < wounds; k++) {
            int i = (int) ((k + 0.5) * n / wounds);
            Object rigid = pieces.get(i);
            Vec3 at;
            try {
                at = position(rigid, offset);
            } catch (Exception e) {
                continue;
            }
            org.joml.Quaternionf turn = new PhysicsLimb(rigid, world).rotation(1f);
            if (at == null) continue;
            Vec3 toMiddle = death.pos().subtract(at);
            Vec3 aim = toMiddle.lengthSqr() < 0.01 ? new Vec3(0, 1, 0) : toMiddle.normalize();
            Vec3 local = local(aim.scale(0.12), turn);
            out.add(new PieceWound(i, local, local(aim, turn), new Spurt(now)));
        }
        if (!out.isEmpty()) BloodSounds.stump(level, death.pos());
        return out;
    }

    private static void pumpPieces(ClientLevel level, Bleeding b, Vec3[] parts, Object world, long now) {
        prepare(b.death);
        try {
            for (PieceWound w : b.pieceWounds) {
                if (w.index() >= parts.length || parts[w.index()] == null) continue;
                org.joml.Quaternionf turn = new PhysicsLimb(b.pieces.get(w.index()), world).rotation(1f);
                pumpAt(level, w.spurt(), b.blood, parts[w.index()], null, w.local(), w.aim(), turn, now);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static boolean isMob(Object rigid) throws Exception {
        Object renderable = rigidEntity.invoke(rigid);
        return renderable != null && renderableType.get(renderable) instanceof Enum<?> type && "MOB".equals(type.name());
    }

    private static Set<Object> LIVE_RIGIDS;

    private static final Map<Object, Long> FIRST_SEEN = new WeakHashMap<>();

    private static void giveUpLater(Object body, long now) {
        long first = FIRST_SEEN.computeIfAbsent(body, b -> now);
        if (now - first > 20) {
            SEEN.add(body);
            FIRST_SEEN.remove(body);
        }
    }

    private static List<Object> rigidsOf(Object ragdoll) throws Exception {
        if (!(btBodies.get(ragdoll) instanceof List<?> list) || list.isEmpty()) return List.of();
        List<Object> out = new ArrayList<>(list.size());
        for (Object body : list) {
            Object rigid = linkedRigid.invoke(body);
            if (rigid != null) out.add(rigid);
        }
        return out;
    }

    private static java.lang.reflect.Field pxJoints;
    private static java.lang.reflect.Method jointParents, tupleA, tupleB;
    private static java.lang.reflect.Field plannedJoints, plannedBodies, plannedIndex1, plannedIndex2, plannedPoint1, plannedPoint2;
    private static java.lang.reflect.Method linkedEntity;

    private static java.lang.reflect.Field oldPosition, rotation, oldRotation, scaleField, models, modelMesh,
            meshPositions, meshUvs, meshQuads;
    private static boolean limbLooked, meshesReadable;
    private static Method renderPercent, despawnSpeed, despawnScale;
    private static java.lang.reflect.Field timeLeft;
    private static final float FADE_SECONDS = 2.5f;
    private static java.lang.reflect.Field meshTriangles, meshDiscards;

    private record MeshCopy(float[] positions, float[] uvs, int[] quads, int[] triangles) {}

    private static final Map<Object, MeshCopy> MESH_COPIES = Collections.synchronizedMap(new WeakHashMap<>());

    public static void meshCleared(Object mesh) {
        try {
            if (!PhysicsLimb.readable() || !meshesReadable) return;
            if (meshDiscards != null && !Boolean.TRUE.equals(meshDiscards.get(mesh))) return;
            MeshCopy copy = copyOf(mesh);
            if (copy != null) MESH_COPIES.put(mesh, copy);
        } catch (Throwable t) {
        }
    }

    private static MeshCopy copyOf(Object mesh) throws Exception {
        if (!(meshPositions.get(mesh) instanceof List<?> positions) || !(meshUvs.get(mesh) instanceof List<?> uvs)
                || positions.size() != uvs.size() || positions.isEmpty() || positions.size() > 4096) return null;
        float[] pos = new float[positions.size() * 3], uv = new float[uvs.size() * 2];
        for (int i = 0; i < positions.size(); i++) {
            if (!(positions.get(i) instanceof org.joml.Vector3f v) || !(uvs.get(i) instanceof org.joml.Vector2f t)) return null;
            pos[i * 3] = v.x;
            pos[i * 3 + 1] = v.y;
            pos[i * 3 + 2] = v.z;
            uv[i * 2] = t.x;
            uv[i * 2 + 1] = t.y;
        }
        int[] quads = meshQuads.get(mesh) instanceof it.unimi.dsi.fastutil.ints.IntList q ? q.toIntArray() : new int[0];
        int[] tris = meshTriangles != null && meshTriangles.get(mesh) instanceof it.unimi.dsi.fastutil.ints.IntList t ? t.toIntArray() : new int[0];
        return new MeshCopy(pos, uv, quads, tris);
    }

    private static MeshCopy verticesOf(Object mesh) throws Exception {
        MeshCopy live = copyOf(mesh);
        return live != null ? live : MESH_COPIES.get(mesh);
    }

    private record PhysicsLimb(Object rigid, Object world) implements com.simpleblood.mob.RigidCoats.Limb {
        @Override
        public Vec3 position(float partialTick) {
            try {
                if (!readable() || Boolean.TRUE.equals(rigidDestroyed.invoke(rigid))) return null;
                Set<Object> live = LIVE_RIGIDS;
                if (live != null && !live.contains(rigid)) return null;
                Object r = rigidEntity.invoke(rigid);
                if (r == null || !(renderablePosition.get(r) instanceof org.joml.Vector3f now)) return null;
                org.joml.Vector3f was = oldPosition.get(r) instanceof org.joml.Vector3f o ? o : now;
                org.joml.Vector3d offset = (org.joml.Vector3d) getOffset.invoke(world);
                float t = between(partialTick);
                return new Vec3(was.x + (now.x - was.x) * t + offset.x,
                        was.y + (now.y - was.y) * t + offset.y,
                        was.z + (now.z - was.z) * t + offset.z);
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public org.joml.Quaternionf rotation(float partialTick) {
            try {
                if (!readable()) return null;
                Object r = rigidEntity.invoke(rigid);
                if (r == null || !(rotation.get(r) instanceof org.joml.Quaternionf now)) return null;
                org.joml.Quaternionf was = oldRotation.get(r) instanceof org.joml.Quaternionf o ? o : now;
                float t = between(partialTick);
                float dot = was.x * now.x + was.y * now.y + was.z * now.z + was.w * now.w;
                float a = 1f - t, b = dot >= 0 ? t : -t;
                return new org.joml.Quaternionf(was.x * a + now.x * b, was.y * a + now.y * b, was.z * a + now.z * b, was.w * a + now.w * b).normalize();
            } catch (Throwable t) {
                return null;
            }
        }

        private float between(float partialTick) {
            if (partialTick >= 1f) return 1f;
            try {
                if (renderPercent == null) renderPercent = world.getClass().getMethod("getRenderPercent");
                return (float) Math.max(0, Math.min(1, ((Number) renderPercent.invoke(world)).doubleValue()));
            } catch (Throwable t) {
                return partialTick;
            }
        }

        @Override
        public org.joml.Vector3f scale() {
            try {
                if (!readable() || scaleField == null) return null;
                Object r = rigidEntity.invoke(rigid);
                return r != null && scaleField.get(r) instanceof org.joml.Vector3f v ? new org.joml.Vector3f(v) : null;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public float fade(float partialTick) {
            try {
                if (!readable() || timeLeft == null || despawnSpeed == null) return 1f;
                Object r = rigidEntity.invoke(rigid);
                if (r == null) return 0f;
                double untilDespawn = timeLeft.getFloat(r) - ((Number) despawnSpeed.invoke(r)).doubleValue();
                float fade = (float) Math.max(0, Math.min(1, untilDespawn / FADE_SECONDS));
                if (despawnScale != null && fade > 0f) {
                    Object level = net.minecraft.client.Minecraft.getInstance().level;
                    if (level != null) fade = Math.min(fade, ((Number) despawnScale.invoke(r, level)).floatValue());
                }
                return fade;
            } catch (Throwable t) {
                return 1f;
            }
        }

        @Override
        public List<float[]> quads() {
            if (!readable() || !meshesReadable) return List.of();
            try {
                Object r = rigidEntity.invoke(rigid);
                if (r == null || !(models.get(r) instanceof List<?> list)) return List.of();
                List<float[]> out = new ArrayList<>();
                for (Object model : list) {
                    Object mesh = model == null ? null : modelMesh.get(model);
                    MeshCopy m = mesh == null ? null : verticesOf(mesh);
                    if (m == null) continue;
                    int vertices = m.positions.length / 3;
                    if (m.quads.length >= 4) {
                        for (int q = 0; q + 3 < m.quads.length; q += 4) add(out, m, m.quads[q], m.quads[q + 1], m.quads[q + 2], m.quads[q + 3]);
                    } else if (m.triangles.length >= 6) {
                        for (int t = 0; t + 5 < m.triangles.length; t += 6) {
                            int[] c = new int[4];
                            int n = 0;
                            for (int k = 0; k < 6 && n <= 4; k++) {
                                int i = m.triangles[t + k];
                                boolean seen = false;
                                for (int j = 0; j < n; j++) if (c[j] == i) seen = true;
                                if (!seen && n < 4) c[n++] = i;
                                else if (!seen) n = 5;
                            }
                            if (n == 4) add(out, m, c[0], c[1], c[2], c[3]);
                        }
                    } else {
                        for (int q = 0; q + 3 < vertices; q += 4) add(out, m, q, q + 1, q + 2, q + 3);
                    }
                }
                return out;
            } catch (Throwable t) {
                return List.of();
            }
        }

        private static void add(List<float[]> out, MeshCopy m, int... corners) {
            int vertices = m.positions.length / 3;
            float[] quad = new float[20];
            for (int k = 0; k < 4; k++) {
                int i = corners[k];
                if (i < 0 || i >= vertices) return;
                quad[k * 5] = m.positions[i * 3];
                quad[k * 5 + 1] = m.positions[i * 3 + 1];
                quad[k * 5 + 2] = m.positions[i * 3 + 2];
                quad[k * 5 + 3] = m.uvs[i * 2];
                quad[k * 5 + 4] = m.uvs[i * 2 + 1];
            }
            out.add(quad);
        }

        static boolean readable() {
            if (!limbLooked) {
                limbLooked = true;
                ClassLoader cl = PhysicsModRagdolls.class.getClassLoader();
                try {
                    Class<?> renderable = Class.forName("net.diebuddies.physics.PhysicsRenderable", false, cl);
                    oldPosition = renderable.getField("oldPosition");
                    rotation = renderable.getField("rotation");
                    oldRotation = renderable.getField("oldRotation");
                } catch (Throwable t) {
                    rotation = null;
                    SimpleBlood.LOGGER.warn("Physics Mod limbs: rotation not readable, blood on mobs stays off its ragdolls ({})", t.toString());
                }
                try {
                    Class<?> renderable = Class.forName("net.diebuddies.physics.PhysicsRenderable", false, cl);
                    scaleField = renderable.getField("scale");
                    models = renderable.getField("models");
                    Class<?> model = Class.forName("net.diebuddies.physics.Model", false, cl);
                    modelMesh = model.getField("mesh");
                    Class<?> mesh = Class.forName("net.diebuddies.physics.Mesh", false, cl);
                    meshPositions = mesh.getField("positions");
                    meshUvs = mesh.getField("uvs");
                    meshQuads = mesh.getField("indicesQuads");
                    meshTriangles = mesh.getField("indices");
                    try {
                        meshDiscards = mesh.getField("canDiscard");
                    } catch (Throwable ignored) {
                        meshDiscards = null;
                    }
                    meshesReadable = true;
                } catch (Throwable t) {
                    SimpleBlood.LOGGER.warn("Physics Mod limbs: meshes not readable, blood on mobs is pinned to its ragdolls more roughly ({})", t.toString());
                }
                try {
                    Class<?> renderable = Class.forName("net.diebuddies.physics.PhysicsRenderable", false, cl);
                    timeLeft = renderable.getField("time");
                    despawnSpeed = renderable.getMethod("getDespawnSpeed");
                    try {
                        despawnScale = renderable.getMethod("getDespawnScale", net.minecraft.world.level.Level.class);
                    } catch (Throwable ignored) {
                        despawnScale = null;
                    }
                } catch (Throwable t) {
                    timeLeft = null;
                    despawnSpeed = null;
                    SimpleBlood.LOGGER.warn("Physics Mod limbs: lifetime not readable, blood on ragdolls does not fade before they despawn ({})", t.toString());
                }
            }
            return rotation != null;
        }
    }

    private static Vec3 position(Object rigid, org.joml.Vector3d offset) throws Exception {
        Object renderable = rigidEntity.invoke(rigid);
        if (renderable == null) return null;
        Object p = renderablePosition.get(renderable);
        return p instanceof org.joml.Vector3f v ? new Vec3(v.x + offset.x, v.y + offset.y, v.z + offset.z) : null;
    }

    private static List<Vec3> positions(List<Object> rigids, org.joml.Vector3d offset) throws Exception {
        List<Vec3> out = new ArrayList<>(rigids.size());
        for (Object rigid : rigids) {
            Vec3 p = position(rigid, offset);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static Vec3 centre(List<Vec3> parts) {
        if (parts.isEmpty()) return Vec3.ZERO;
        double x = 0, y = 0, z = 0;
        for (Vec3 p : parts) { x += p.x; y += p.y; z += p.z; }
        return new Vec3(x / parts.size(), y / parts.size(), z / parts.size());
    }

    private static Death takenAsPieces(List<Object> rigids) {
        for (Iterator<Bleeding> it = BLEEDING.iterator(); it.hasNext(); ) {
            Bleeding b = it.next();
            if (b.pieces == null) continue;
            for (Object piece : b.pieces) {
                if (!rigids.contains(piece)) continue;
                it.remove();
                SimpleBlood.LOGGER.info("[decap] {} ragdoll took back its death from its own bodies, taken as loose pieces a frame early", b.death.type());
                return b.death;
            }
        }
        return null;
    }

    private static Death dyingNear(ClientLevel level, Vec3 c) {
        LivingEntity best = null;
        double bestD = MATCH_RANGE * MATCH_RANGE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(c, MATCH_RANGE * 2, MATCH_RANGE * 2, MATCH_RANGE * 2),
                e -> e.isDeadOrDying() && SimpleBlood.shouldEntityBleed(e))) {
            double dd = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(c);
            if (dd < bestD) { bestD = dd; best = e; }
        }
        if (best == null) return null;
        SimpleBlood.LOGGER.info("[decap] a ragdoll had no death told for it: matched to the dying {} lying there", best.getType());
        return deathOf(best, level);
    }

    private static Death closest(Vec3 c) {
        Death best = null;
        double bestD = MATCH_RANGE * MATCH_RANGE;
        for (Death d : DEATHS) {
            double dd = d.pos.distanceToSqr(c);
            if (dd < bestD) { bestD = dd; best = d; }
        }
        return best;
    }

    private static void bleed(ClientLevel level, Bleeding b, Vec3[] parts, long now) {
        int n = parts.length;
        if (b.last == null || b.last.length != n) {
            b.last = parts.clone();
            b.velocity = new Vec3[n];
            return;
        }
        Death d = b.death;
        float fresh = b.fresh(now);
        boolean liquid = d.kind == BloodKind.LIQUID;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        SimpleBloodConfig.PhysicsModSettings s = cfg.physicsMod;
        boolean paints = liquid && d.stains && cfg.surfaces.enabled;
        float wholeStains = Math.max(0, s.wholeRagdollStains) / 100f;
        float drips = Math.max(0, s.drips) / 100f;
        float smearChance = 0.5f * Math.max(0, s.smearAmount) / 100f;
        float dripShare = Math.min(1f, 10f / n);
        double impactSpeed = b.gore ? IMPACT_SPEED_GORE : IMPACT_SPEED;
        int painted = 0;
        prepare(d);
        try {
            for (int i = 0; i < n; i++) {
                Vec3 p = parts[i];
                if (p == null || b.last[i] == null) {
                    b.last[i] = p;
                    continue;
                }
                Vec3 v = p.subtract(b.last[i]);
                double speed = v.length();
                Vec3 before = b.velocity[i];
                double beforeSpeed = before == null ? 0 : before.length();

                if (beforeSpeed > impactSpeed && speed < beforeSpeed * 0.35) {
                    splash(level, p, beforeSpeed, s.splashes);
                    if (paints && s.stains && painted < PAINT_PER_TICK && (b.gore || RNG.nextFloat() < wholeStains)) {
                        stain(level, d, p, before, beforeSpeed, b.gore, s.stainSize);
                        painted++;
                    }
                }

                if (liquid && RNG.nextFloat() < 0.035f * drips * dripShare * fresh * (1f + (float) speed * 5f)) {
                    ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_DRIP, p.x, p.y, p.z,
                            v.x * 0.5, -0.05, v.z * 0.5);
                }

                double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
                if (paints && s.smears && painted < PAINT_PER_TICK && fresh > 0.15f && horizontal > 0.04
                        && RNG.nextFloat() < smearChance && nearGround(level, p)) {
                    paint(level, p.add(0, 0.1, 0), p.add(0, -0.6, 0), colourOf(d), 1 + RNG.nextInt(2));
                    painted++;
                }

                b.velocity[i] = v;
                b.last[i] = p;
            }
            pool(level, b, parts, now, fresh, paints, drips);
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static final int POOL_PIXELS = 160;

    private static void pool(ClientLevel level, Bleeding b, Vec3[] parts, long now, float fresh, boolean paints, float drips) {
        if (b.death.kind != BloodKind.LIQUID || fresh <= 0f || drips <= 0f) return;
        double x = 0, y = 0, z = 0, speed = 0;
        int n = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) continue;
            x += parts[i].x;
            y = n == 0 ? parts[i].y : Math.min(y, parts[i].y);
            z += parts[i].z;
            if (b.velocity[i] != null) speed = Math.max(speed, b.velocity[i].length());
            n++;
        }
        if (n == 0) return;
        Vec3 middle = new Vec3(x / n, y, z / n);
        if (speed > 0.03) {
            b.stillSince = -1;
            return;
        }
        if (b.stillSince < 0) b.stillSince = now;
        if (RNG.nextFloat() < 0.3f * drips * fresh) {
            Vec3 from = parts[RNG.nextInt(parts.length)];
            if (from != null) {
                ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_DRIP, from.x + (RNG.nextDouble() - 0.5) * 0.3, from.y,
                        from.z + (RNG.nextDouble() - 0.5) * 0.3, 0, -0.05, 0);
            }
        }
        int cap = Math.round(POOL_PIXELS * Math.min(2f, drips));
        if (!paints || b.pooled >= cap || (now - b.stillSince) % 4 != 0) return;
        float share = 8f * cap * fresh / bleedTicks();
        int pixels = (int) share + (RNG.nextFloat() < share - (int) share ? 1 : 0);
        if (pixels <= 0) return;
        double r = Math.min(0.6, 0.15 + b.pooled / (double) cap * 0.5);
        double a = RNG.nextDouble() * Math.PI * 2, d = Math.sqrt(RNG.nextDouble()) * r;
        Vec3 at = middle.add(Math.cos(a) * d, 0, Math.sin(a) * d);
        if (paint(level, at.add(0, 0.4, 0), at.add(0, -1.2, 0), colourOf(b.death), pixels)) {
            b.pooled += pixels;
            com.simpleblood.Tally.count("ragdoll.pool");
        }
    }

    private static void splash(ClientLevel level, Vec3 p, double speed, int percent) {
        int count = scaled(2 + RNG.nextInt(3), percent);
        for (int k = 0; k < count; k++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double s = 0.08 + speed * 0.4;
            ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_SPLASH, p.x, p.y + 0.1, p.z,
                    Math.cos(a) * s, 0.12 + RNG.nextDouble() * 0.1, Math.sin(a) * s);
        }
    }

    private static void stain(ClientLevel level, Death d, Vec3 p, Vec3 velocity, double speed, boolean gore, int sizePercent) {
        int pixels = gore ? 6 + (int) Math.min(10, speed * 30) : 2 + (int) Math.min(4, speed * 12);
        pixels = Math.max(1, scaled(pixels, sizePercent));
        Vec3 dir = velocity.normalize();
        boolean hit = paint(level, p.subtract(dir.scale(0.3)), p.add(dir.scale(0.8)), colourOf(d), pixels);
        if (!hit) hit = paint(level, p.add(0, 0.1, 0), p.add(0, -0.8, 0), colourOf(d), pixels);
        if (hit) BloodSounds.landed(level, p, false, true, BloodKind.LIQUID);
    }

    private static boolean paint(ClientLevel level, Vec3 from, Vec3 to, int colour, int pixels) {
        com.simpleblood.Tally.count("ragdoll.paint");
        BloodSurfaces.Hit hit = BloodSurfaces.trace(level, from, to);
        return hit != null && BloodSurfaces.deposit(level, hit, colour, pixels, to.subtract(from)) != BloodSurfaces.NOTHING;
    }

    private static void prepare(Death d) {
        BloodParticle.setCurrentBloodColor(d.colour);
        BloodParticle.setCurrentKind(d.kind);
        BloodParticle.setGlowing(d.glows);
        BloodParticle.setPaintsSurfaces(d.stains);
        BloodParticle.setShouldTransformToFog(d.kind == BloodKind.LIQUID && d.stains);
        BloodParticle.setShouldDespawnInWater(d.kind == BloodKind.POWDER);
        BloodParticle.setEntitySizeMultiplier(d.size);
        BloodParticle.setBallistic(true, 1.0f);
    }

    private static boolean nearGround(ClientLevel level, Vec3 p) {
        BlockPos below = BlockPos.containing(p.x, p.y - 0.35, p.z);
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static int colourOf(Death d) {
        BloodColor.Color c = d.colour;
        int rgb = ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        return d.glows ? rgb | BloodColor.GLOW_BIT : rgb;
    }
}
