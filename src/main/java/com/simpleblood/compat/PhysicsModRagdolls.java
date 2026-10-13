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

/**
 * Blood for Physics Mod mob physics (GitHub issue #6). A mob that dies into a ragdoll, or
 * falls apart into blocky or fractured pieces, keeps bleeding: its limbs or pieces drip for a
 * while, slam down with a splash and a stain right where they hit, and smear blood along the
 * ground where they slide. Ragdolls that break apart and loose pieces are gore and stain at
 * every hard landing; a whole ragdoll only now and then, and less. Mobs whose blood is not
 * liquid (bone, metal, embers...) knock off their own pieces instead and leave no stains.
 *
 * <p>Physics Mod is closed source and has no API, so this touches it only by reflection, on a
 * handful of members looked up once ({@code PhysicsMod.getInstanceNullable},
 * {@code physicsWorld}, the world's ragdoll and body lists and offset, each ragdoll's bodies,
 * each body's renderable with its position and type). If any of them is missing (another
 * Physics Mod version), it says so in the log once and stays off; nothing is mixed into
 * Physics Mod's classes.
 *
 * <p>Neither ragdolls nor pieces know which mob they were. Physics Mod removes the mob as it
 * builds them, so each death is noted here when it happens (where, and the mob's blood), and
 * a new ragdoll, or a batch of new mob pieces, is matched to a death that just happened where
 * it appeared. Ragdolls and pieces of anything else (doors, vines, blocks, items) never match.
 */
public final class PhysicsModRagdolls {

    private PhysicsModRagdolls() {}

    private static final String MOD_ID = "physicsmod";
    /** How long after a death its ragdoll or pieces may still turn up, in ticks. */
    private static final int MATCH_WINDOW = 60;
    /** How far from where the mob died its ragdoll or pieces may first be seen, in blocks. */
    private static final double MATCH_RANGE = 3.0;
    /** Slower than this (blocks/tick) a limb or piece that stops has not hit hard: whole ragdolls / gore. */
    private static final double IMPACT_SPEED = 0.22, IMPACT_SPEED_GORE = 0.15;
    /** Most stains and smears one body paints per tick (a fractured mob is dozens of pieces). */
    private static final int PAINT_PER_TICK = 6;

    private static final RandomSource RNG = RandomSource.create();

    // ------------------------------------------------------------------ reflection
    private static boolean looked, usable;
    private static Method getInstanceNullable; // PhysicsMod.getInstanceNullable(ClientLevel)
    private static Field physicsWorldField;    // PhysicsMod.physicsWorld
    private static Field worldRagdolls;        // PhysicsWorld.ragdolls (Iterable<Ragdoll>)
    private static Field worldBodies;          // PhysicsWorld.bodies (Iterable<IRigidBody>)
    private static Method getOffset;           // PhysicsWorld.getOffset() -> org.joml.Vector3d
    private static Field btBodies;             // Ragdoll.btBodies (List<Ragdoll.LinkedBody>)
    private static Class<?> breakableRagdoll;  // BreakableRagdoll: falls apart into its limbs
    private static Method linkedRigid;         // Ragdoll.LinkedBody.rigid() -> IRigidBody
    private static Method rigidEntity;         // IRigidBody.getEntity() -> PhysicsRenderable
    private static Method rigidDestroyed;      // IRigidBody.isDestroyed()
    private static Field renderablePosition;   // PhysicsRenderable.position (org.joml.Vector3f)
    private static Field renderableType;       // PhysicsRenderable.type (PhysicsEntity.Type: MOB, BLOCK...)

    /** Whether Physics Mod is there and looks the way this was written for. */
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
                breakableRagdoll = null; // every ragdoll counts as whole
            }
            SimpleBlood.LOGGER.info("Physics Mod found: ragdolls and mob pieces bleed");
            return true;
        } catch (Throwable t) {
            SimpleBlood.LOGGER.warn("Physics Mod found, but not a version Simple Blood knows; its ragdolls will not bleed ({})", t.toString());
            return false;
        }
    }

    /** Gives up for good after an error: the rest of the mod carries on. */
    private static void disable(Throwable t) {
        usable = false;
        SimpleBlood.LOGGER.warn("Physics Mod ragdoll blood switched off after an error", t);
    }

    // ------------------------------------------------------------------ deaths and bodies

    /** Everything about a dead mob's blood its body will need (the mob itself is gone by then). */
    private record Death(Vec3 pos, long tick, BloodColor.Color colour, BloodKind kind, boolean glows,
                         boolean stains, float size) {}

    /**
     * A body that is bleeding: a ragdoll (whose bodies are read from it every tick) or a batch
     * of loose pieces, with its mob's blood and what each part did last tick.
     */
    private static final class Bleeding {
        final Death death;
        final long born;
        final boolean gore;          // broken ragdoll or loose pieces: stains every hard landing
        final Object ragdoll;        // null for pieces
        final List<Object> pieces;   // null for a ragdoll
        Vec3[] last;
        Vec3[] velocity;

        Bleeding(Death death, long born, boolean gore, Object ragdoll, List<Object> pieces) {
            this.death = death;
            this.born = born;
            this.gore = gore;
            this.ragdoll = ragdoll;
            this.pieces = pieces;
        }
    }

    private static final List<Death> DEATHS = new ArrayList<>();
    /** Every ragdoll and body already looked at (matched or not), so each is matched once. */
    private static final Set<Object> SEEN = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<Bleeding> BLEEDING = new ArrayList<>();
    private static ClientLevel lastLevel;

    /** A mob died (from the death burst): remember its blood in case it turns into a ragdoll or pieces. */
    public static void entityDied(LivingEntity entity) {
        if (!enabled() || !(entity.level() instanceof ClientLevel level)) return;
        DEATHS.add(new Death(entity.position().add(0, entity.getBbHeight() * 0.5, 0), level.getGameTime(),
                BloodColor.getBloodColor(entity), SimpleBlood.bloodKindOf(entity),
                SimpleBlood.bloodGlows(entity), SimpleBlood.shouldEntityTransformToStains(entity),
                SimpleBlood.getParticleSizeMultiplier(entity)));
        if (DEATHS.size() > 64) DEATHS.remove(0);
    }

    private static boolean enabled() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg != null && cfg.globalEnabled() && cfg.general.physicsModRagdolls && available();
    }

    private static SimpleBloodConfig.PhysicsModSettings settings() {
        return SimpleBloodClient.getConfig().physicsMod;
    }

    /** How long a body keeps bleeding, in ticks; it weakens over this time. */
    private static int bleedTicks() {
        return 20 * Math.max(1, settings().bleedSeconds);
    }

    /** {@code base} scaled by a percentage, rounded up or down at random so small amounts still add up. */
    private static int scaled(int base, int percent) {
        float f = base * Math.max(0, percent) / 100f;
        int n = (int) f;
        return RNG.nextFloat() < f - n ? n + 1 : n;
    }

    /** Every client tick, in a world and not paused. */
    public static void tick(ClientLevel level) {
        if (level != lastLevel) {
            lastLevel = level;
            DEATHS.clear();
            SEEN.clear();
            BLEEDING.clear();
        }
        if (!enabled()) {
            BLEEDING.clear();
            return;
        }
        try {
            tickBodies(level);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void tickBodies(ClientLevel level) throws Exception {
        long now = level.getGameTime();
        DEATHS.removeIf(d -> now - d.tick > MATCH_WINDOW);
        Object mod = getInstanceNullable.invoke(null, level);
        if (mod == null) return;
        Object world = physicsWorldField.get(mod);
        if (world == null) return;
        org.joml.Vector3d offset = (org.joml.Vector3d) getOffset.invoke(world);

        // Ragdolls: match new ones to deaths; note every ragdoll's bodies, which are no loose pieces.
        Set<Object> aliveRagdolls = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> ragdollBodies = Collections.newSetFromMap(new IdentityHashMap<>());
        if (worldRagdolls.get(world) instanceof Iterable<?> ragdolls) {
            for (Object ragdoll : ragdolls) {
                aliveRagdolls.add(ragdoll);
                List<Object> rigids = rigidsOf(ragdoll);
                ragdollBodies.addAll(rigids);
                if (SEEN.contains(ragdoll) || DEATHS.isEmpty() || rigids.isEmpty()) continue;
                SEEN.add(ragdoll);
                Death death = closest(centre(positions(rigids, offset)));
                if (death != null) {
                    DEATHS.remove(death);
                    boolean breaks = breakableRagdoll != null && breakableRagdoll.isInstance(ragdoll);
                    BLEEDING.add(new Bleeding(death, now, breaks, ragdoll, null));
                }
            }
        }

        // Blocky and fractured mobs: new loose mob pieces near a fresh death become its body.
        if (!DEATHS.isEmpty() && settings().pieces && worldBodies.get(world) instanceof Iterable<?> bodies) {
            Map<Death, List<Object>> batches = new IdentityHashMap<>();
            for (Object rigid : bodies) {
                if (SEEN.contains(rigid) || ragdollBodies.contains(rigid)) continue;
                SEEN.add(rigid);
                Object renderable = rigidEntity.invoke(rigid);
                if (renderable == null || !(renderableType.get(renderable) instanceof Enum<?> type)
                        || !"MOB".equals(type.name())) continue;
                Vec3 p = position(rigid, offset);
                Death death = p == null ? null : closest(p);
                if (death != null) batches.computeIfAbsent(death, d -> new ArrayList<>()).add(rigid);
            }
            batches.forEach((death, pieces) -> {
                DEATHS.remove(death);
                BLEEDING.add(new Bleeding(death, now, true, null, pieces));
            });
        }

        Iterator<Bleeding> it = BLEEDING.iterator();
        while (it.hasNext()) {
            Bleeding b = it.next();
            if (now - b.born > bleedTicks() || (b.ragdoll != null && !aliveRagdolls.contains(b.ragdoll))) {
                it.remove(); // bled out, or the ragdoll is gone (despawned, burnt)
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
                it.remove(); // every piece has despawned
                continue;
            }
            bleed(level, b, parts, now);
        }
    }

    /** A ragdoll's rigid bodies, in order. */
    private static List<Object> rigidsOf(Object ragdoll) throws Exception {
        if (!(btBodies.get(ragdoll) instanceof List<?> list) || list.isEmpty()) return List.of();
        List<Object> out = new ArrayList<>(list.size());
        for (Object body : list) {
            Object rigid = linkedRigid.invoke(body);
            if (rigid != null) out.add(rigid);
        }
        return out;
    }

    /** World position of a rigid body (physics coordinates plus the world's offset), or null. */
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

    private static Death closest(Vec3 c) {
        Death best = null;
        double bestD = MATCH_RANGE * MATCH_RANGE;
        for (Death d : DEATHS) {
            double dd = d.pos.distanceToSqr(c);
            if (dd < bestD) { bestD = dd; best = d; }
        }
        return best;
    }

    // ------------------------------------------------------------------ bleeding

    private static void bleed(ClientLevel level, Bleeding b, Vec3[] parts, long now) {
        int n = parts.length;
        if (b.last == null || b.last.length != n) {
            b.last = parts.clone();
            b.velocity = new Vec3[n];
            return;
        }
        Death d = b.death;
        float fresh = Math.max(0f, 1f - (float) (now - b.born) / bleedTicks()); // 1 just dead .. 0 bled out
        boolean liquid = d.kind == BloodKind.LIQUID;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        SimpleBloodConfig.PhysicsModSettings s = cfg.physicsMod;
        boolean paints = liquid && d.stains && cfg.surfaces.enabled;
        float wholeStains = Math.max(0, s.wholeRagdollStains) / 100f;
        float drips = Math.max(0, s.drips) / 100f;
        float smearChance = 0.5f * Math.max(0, s.smearAmount) / 100f;
        // Many small pieces drip less each, so a fractured mob bleeds about as much as a ragdoll.
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

                // A part that was flying and suddenly stopped hit something: a splash (bone and
                // metal knock off pieces instead) and, for blood, a stain right where it hit.
                if (beforeSpeed > impactSpeed && speed < beforeSpeed * 0.35) {
                    splash(level, p, beforeSpeed, s.splashes);
                    if (paints && s.stains && painted < PAINT_PER_TICK && (b.gore || RNG.nextFloat() < wholeStains)) {
                        stain(level, d, p, before, beforeSpeed, b.gore, s.stainSize);
                        painted++;
                    }
                }

                // Wounds keep dripping, more while the body is thrown about, less as it bleeds out.
                if (liquid && RNG.nextFloat() < 0.035f * drips * dripShare * fresh * (1f + (float) speed * 5f)) {
                    ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_DRIP, p.x, p.y, p.z,
                            v.x * 0.5, -0.05, v.z * 0.5);
                }

                // A part sliding along the ground leaves a smear.
                double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
                if (paints && s.smears && painted < PAINT_PER_TICK && fresh > 0.15f && horizontal > 0.04
                        && RNG.nextFloat() < smearChance && nearGround(level, p)) {
                    paint(level, p.add(0, 0.1, 0), p.add(0, -0.6, 0), colourOf(d), 1 + RNG.nextInt(2));
                    painted++;
                }

                b.velocity[i] = v;
                b.last[i] = p;
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    /** A few drops (or pieces) thrown up off the spot where a part landed hard. */
    private static void splash(ClientLevel level, Vec3 p, double speed, int percent) {
        int count = scaled(2 + RNG.nextInt(3), percent);
        for (int k = 0; k < count; k++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double s = 0.08 + speed * 0.4;
            ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_SPLASH, p.x, p.y + 0.1, p.z,
                    Math.cos(a) * s, 0.12 + RNG.nextDouble() * 0.1, Math.sin(a) * s);
        }
    }

    /**
     * Blood painted right where a part hit: traced along the way it was going (into a wall, a
     * floor, a ceiling), straight down if that finds nothing. Gore leaves a big stain, a whole
     * ragdoll a small one; both grow with the speed of the hit.
     */
    private static void stain(ClientLevel level, Death d, Vec3 p, Vec3 velocity, double speed, boolean gore, int sizePercent) {
        int pixels = gore ? 6 + (int) Math.min(10, speed * 30) : 2 + (int) Math.min(4, speed * 12);
        pixels = Math.max(1, scaled(pixels, sizePercent));
        Vec3 dir = velocity.normalize();
        boolean hit = paint(level, p.subtract(dir.scale(0.3)), p.add(dir.scale(0.8)), colourOf(d), pixels);
        if (!hit) hit = paint(level, p.add(0, 0.1, 0), p.add(0, -0.8, 0), colourOf(d), pixels);
        if (hit) BloodSounds.landed(level, p, false, true, BloodKind.LIQUID);
    }

    /**
     * Blood painted on the first block face along a line, as {@code SimpleBloodAPI.paint} does,
     * but keeping the glow mark in the colour.
     */
    private static boolean paint(ClientLevel level, Vec3 from, Vec3 to, int colour, int pixels) {
        BloodSurfaces.Hit hit = BloodSurfaces.trace(level, from, to);
        return hit != null && BloodSurfaces.deposit(level, hit, colour, pixels, to.subtract(from)) != BloodSurfaces.NOTHING;
    }

    /** The per-batch particle settings, as the death burst would set them for this mob. */
    private static void prepare(Death d) {
        BloodParticle.setCurrentBloodColor(d.colour);
        BloodParticle.setCurrentKind(d.kind);
        BloodParticle.setGlowing(d.glows);
        BloodParticle.setPaintsSurfaces(d.stains);
        BloodParticle.setShouldTransformToFog(d.kind == BloodKind.LIQUID && d.stains);
        BloodParticle.setShouldDespawnInWater(d.kind == BloodKind.POWDER);
        BloodParticle.setEntitySizeMultiplier(d.size);
        BloodParticle.setBallistic(true, 1.0f); // splashes fly where they are thrown
    }

    /** Whether there is something solid just under this point (the part is on the ground). */
    private static boolean nearGround(ClientLevel level, Vec3 p) {
        BlockPos below = BlockPos.containing(p.x, p.y - 0.35, p.z);
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /** The mob's blood as a stored colour, marked if it glows. */
    private static int colourOf(Death d) {
        BloodColor.Color c = d.colour;
        int rgb = ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        return d.glows ? rgb | BloodColor.GLOW_BIT : rgb;
    }
}
