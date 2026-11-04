package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import com.simpleblood.particle.PuffParticle;
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
    private static final float PUFFS_PER_RADIUS = 5f;
    private static final int MAX_PUFFS = 40;
    private static final double CLOUD_REACH = 0.45;
    private static final int PAINT_DELAY = 3;
    private static final int SPLATTER_RAYS = 48;
    private static final double SPLATTER_REACH = 1.3;
    private static final int SPLATTER_PIXELS = 70, CORE_PIXELS = 160;

    private record Splatter(WeakReference<ClientLevel> level, Vec3 centre, float radius, int colour, long due) {}

    private static final List<Splatter> PENDING = new ArrayList<>();

    public static void heard(ClientLevel level, Vec3 centre, float radius) {
        Creeper creeper = exploding(level, centre);
        if (creeper == null) return;
        Tally.count("creeper.found");
        burst(level, creeper, centre, radius);
    }

    private static Creeper exploding(ClientLevel level, Vec3 centre) {
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

    private static void burst(ClientLevel level, Creeper creeper, Vec3 centre, float radius) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (!cfg.globalEnabled() || !SimpleBlood.shouldEntityBleed(creeper)) return;
        Tally.count("creeper.blast");
        com.simpleblood.compat.PhysicsModRagdolls.entityDied(creeper);
        BloodColor.Color colour = BloodColor.getBloodColor(creeper);
        if (cfg.deathBurstEnabled()) {
            HitContext ctx = HitContext.selfBlast(creeper);
            float velocityAdjust = creeper.isInWater() ? 0.3f : 1.0f;
            BloodParticle.setCurrentBloodColor(colour);
            BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(creeper));
            BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(creeper));
            BloodParticle.setGlowing(SimpleBlood.bloodGlows(creeper));
            BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(creeper));
            BloodParticle.setShouldTransformToFog(SimpleBlood.createsFogUnderwater(creeper));
            BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(creeper));
            BloodSounds.splat(level, centre, 1.0f);
            cloud(level, centre, radius, cfg);
            BloodSpray.explosion(level, ctx, velocityAdjust);
            com.simpleblood.mob.MobBlood.death(creeper, ctx);
            Giblets.death(level, creeper, ctx);
            BloodParticle.resetSpawnState();
        }
        if (cfg.surfaces.enabled && SimpleBlood.bloodKindOf(creeper) == BloodKind.LIQUID
                && SimpleBlood.shouldEntityTransformToStains(creeper)) {
            PENDING.add(new Splatter(new WeakReference<>(level), centre, radius, colour.toRgb(), level.getGameTime() + PAINT_DELAY));
        }
    }

    private static void cloud(ClientLevel level, Vec3 centre, float radius, SimpleBloodConfig cfg) {
        RandomSource rng = level.getRandom();
        int puffs = Math.min(MAX_PUFFS, Math.round(PUFFS_PER_RADIUS * radius * Math.min(1.5f, cfg.masterAmount())));
        Vec3 middle = centre.add(0, 0.6, 0);
        PuffParticle.setCloud(radius * 0.9f);
        BloodParticle.setBallistic(true, 1.0f);
        try {
            for (int i = 0; i < puffs; i++) {
                double z = rng.nextDouble() * 1.6 - 0.6, a = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
                Vec3 dir = new Vec3(r * Math.cos(a), z, r * Math.sin(a));
                Vec3 p = middle.add(dir.scale(radius * CLOUD_REACH * Math.cbrt(rng.nextDouble())));
                Vec3 v = dir.scale(0.3 + rng.nextDouble() * 0.4);
                ClientBloodParticleSpawner.emitPlain(level, BloodParticles.BONE_DUST, p.x, p.y, p.z, v.x, v.y, v.z);
                Tally.count("creeper.cloud");
            }
        } finally {
            PuffParticle.setCloud(0f);
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    public static void tick(ClientLevel level) {
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
