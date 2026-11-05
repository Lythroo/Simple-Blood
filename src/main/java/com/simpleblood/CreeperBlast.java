package com.simpleblood;

import com.simpleblood.particle.BloodMistParticle;
import com.simpleblood.particle.BloodParticle;
import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class CreeperBlast {

    private CreeperBlast() {}

    private static final double FOUND_WITHIN = 1.5;
    private static final float ABOUT_TO_BLOW = 0.5f;
    private static final float MIN_CREEPER_RADIUS = 1.5f;
    private static final int CONFIRM_TICKS = 3;
    private static final int MAX_CANDIDATES = 8;
    private static final float WISPS_PER_RADIUS = 4f;
    private static final int MAX_WISPS = 24;
    private static final double CLOUD_REACH = 0.12;
    private static final double WISP_SPEED = 0.07, WISP_SPEED_SPREAD = 0.04;
    private static final float WISP_SIZE = 0.6f;
    private static final int BURST_DROPS = 300;
    private static final float BURST_SPEED = 0.5f;
    private static final int MAX_PENDING = 16;
    private static final int PAINT_DELAY = 3;
    private static final int SPLATTER_RAYS = 36;
    private static final double SPLATTER_REACH = 0.9;
    private static final int SPLATTER_PIXELS = 50, CORE_PIXELS = 120;

    private record Splatter(WeakReference<ClientLevel> level, Vec3 centre, float radius, int colour, long due) {}

    private static final List<Splatter> PENDING = new ArrayList<>();

    private record Candidate(WeakReference<Creeper> creeper, int id, Vec3 centre, float radius, long heard) {}

    private static final List<Candidate> CANDIDATES = new ArrayList<>(2);

    public static void heard(ClientLevel level, Vec3 centre, float radius) {
        if (radius < MIN_CREEPER_RADIUS || CANDIDATES.size() >= MAX_CANDIDATES) return;
        Creeper creeper = aboutToBlow(level, centre);
        if (creeper == null) return;
        for (Candidate c : CANDIDATES) if (c.id() == creeper.getId()) return;
        CANDIDATES.add(new Candidate(new WeakReference<>(creeper), creeper.getId(), centre, radius, level.getGameTime()));
        Tally.count("creeper.candidate");
    }

    private static Creeper aboutToBlow(ClientLevel level, Vec3 centre) {
        AABB around = new AABB(centre.x - FOUND_WITHIN, centre.y - FOUND_WITHIN, centre.z - FOUND_WITHIN,
                centre.x + FOUND_WITHIN, centre.y + FOUND_WITHIN, centre.z + FOUND_WITHIN);
        Creeper best = null;
        double bestD = Double.MAX_VALUE;
        for (Creeper c : level.getEntitiesOfClass(Creeper.class, around)) {
            if (!c.isAlive() || !(c.isIgnited() || c.getSwelling(1f) >= ABOUT_TO_BLOW)) continue;
            double d = c.position().distanceToSqr(centre);
            if (d < bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    private static void confirm(ClientLevel level) {
        long now = level.getGameTime();
        Iterator<Candidate> it = CANDIDATES.iterator();
        while (it.hasNext()) {
            Candidate c = it.next();
            Creeper creeper = c.creeper().get();
            if (creeper == null || creeper.level() != level) {
                it.remove();
            } else if (creeper.isRemoved()) {
                it.remove();
                Tally.count("creeper.found");
                burst(level, creeper, c.centre(), c.radius());
            } else if (now - c.heard() > CONFIRM_TICKS || now < c.heard()) {
                it.remove();
                Tally.count("creeper.spared");
            }
        }
    }

    private static void burst(ClientLevel level, Creeper creeper, Vec3 centre, float radius) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (!cfg.globalEnabled() || !SimpleBlood.shouldEntityBleed(creeper)) return;
        Tally.count("creeper.blast");
        com.simpleblood.compat.PhysicsModRagdolls.entityDied(creeper);
        BloodColor.Color colour = BloodColor.getBloodColor(creeper);
        if (cfg.deathBurstEnabled()) {
            HitContext ctx = HitContext.selfBlast(creeper);
            float velocityAdjust = creeper.isInWater() ? 0.3f : 1.0f;
            try {
                BloodParticle.setCurrentBloodColor(colour);
                BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(creeper));
                BloodParticle.setThrowOfMob(SimpleBlood.getThrowSpeedMultiplier(creeper), SimpleBlood.getThrowUpMultiplier(creeper));
                BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(creeper));
                BloodParticle.setGlowing(SimpleBlood.bloodGlows(creeper));
                BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(creeper));
                BloodParticle.setShouldTransformToFog(SimpleBlood.createsFogUnderwater(creeper));
                BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(creeper));
                BloodParticle.setBlownByBlasts(false);
                BloodSounds.splat(level, centre, 1.0f);
                cloud(level, centre, radius, cfg);
                BloodSpray.explosion(level, ctx, velocityAdjust, BURST_DROPS, BURST_SPEED);
                com.simpleblood.mob.MobBlood.death(creeper, ctx);
                Giblets.death(level, creeper, ctx);
            } finally {
                BloodParticle.resetSpawnState();
            }
        }
        if (cfg.surfaces.enabled && SimpleBlood.bloodKindOf(creeper) == BloodKind.LIQUID
                && SimpleBlood.shouldEntityTransformToStains(creeper) && PENDING.size() < MAX_PENDING) {
            PENDING.add(new Splatter(new WeakReference<>(level), centre, radius, colour.toRgb(), level.getGameTime() + PAINT_DELAY));
        }
    }

    private static void cloud(ClientLevel level, Vec3 centre, float radius, SimpleBloodConfig cfg) {
        RandomSource rng = level.getRandom();
        int wisps = Math.min(MAX_WISPS, Math.round(WISPS_PER_RADIUS * radius * Math.min(1.5f, cfg.masterAmount())));
        Vec3 middle = centre.add(0, 0.6, 0);
        BloodMistParticle.setSize(radius * WISP_SIZE);
        try {
            for (int i = 0; i < wisps; i++) {
                double z = rng.nextDouble() * 2 - 1, a = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
                Vec3 dir = new Vec3(r * Math.cos(a), z, r * Math.sin(a));
                Vec3 p = middle.add(dir.scale(radius * CLOUD_REACH * Math.cbrt(rng.nextDouble())));
                Vec3 v = dir.scale(radius * (WISP_SPEED + rng.nextDouble() * WISP_SPEED_SPREAD));
                ClientBloodParticleSpawner.emitPlain(level, BloodParticles.BLOOD_MIST, p.x, p.y, p.z, v.x, v.y, v.z);
                Tally.count("creeper.cloud");
            }
        } finally {
            BloodMistParticle.setSize(1.0f);
        }
    }

    public static void tick(ClientLevel level) {
        if (!CANDIDATES.isEmpty()) confirm(level);
        if (PENDING.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Splatter> it = PENDING.iterator();
        while (it.hasNext()) {
            Splatter s = it.next();
            ClientLevel in = s.level().get();
            if (in != level) {
                it.remove();
                continue;
            }
            if (now < s.due()) continue;
            it.remove();
            splatter(level, s);
        }
    }

    private static void splatter(ClientLevel level, Splatter s) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (!cfg.surfaces.enabled) return;
        RandomSource rng = level.getRandom();
        float scale = (cfg.surfaces.resolution * cfg.surfaces.resolution / 256.0f) * cfg.surfaceAmount();
        Vec3 middle = s.centre().add(0, 0.5, 0);
        double reach = s.radius() * SPLATTER_REACH;
        BloodSurfaces.Hit core = BloodSurfaces.trace(level, middle, middle.add(0, -reach, 0));
        if (core != null) paint(level, core, s.colour(), CORE_PIXELS * scale, null);
        for (int i = 0; i < SPLATTER_RAYS; i++) {
            double z = rng.nextDouble() * 1.4 - 1.0, a = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
            Vec3 dir = new Vec3(r * Math.cos(a), z, r * Math.sin(a));
            BloodSurfaces.Hit hit = BloodSurfaces.trace(level, middle, middle.add(dir.scale(reach)));
            if (hit == null) continue;
            float near = (float) (1.0 - hit.point().distanceTo(middle) / (reach + 0.5));
            paint(level, hit, s.colour(), SPLATTER_PIXELS * near * near * scale, dir);
        }
    }

    private static void paint(ClientLevel level, BloodSurfaces.Hit hit, int colour, float pixels, Vec3 travel) {
        int n = Math.round(pixels);
        if (n < 2) return;
        if (BloodSurfaces.deposit(level, hit, colour, n, travel) != BloodSurfaces.NOTHING) Tally.count("creeper.splatter");
    }
}
