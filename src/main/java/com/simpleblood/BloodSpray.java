package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Directional blood: given a {@link HitContext} with a known direction, the main spray
 * bursts out of the wound back toward whatever struck it (the way a hit surface spatters),
 * and a little carries on out of the far side. Weapon families shape the spray: a blade
 * sweeps a wide flat arc along the swing, an axe throws fewer, heavier drops, an arrow
 * punches a narrow fast stream through, a mace slams blood flat out at the feet, and an
 * explosion throws it everywhere (up included).
 */
public final class BloodSpray {

    private BloodSpray() {}

    /** Whether a context calls for directional handling rather than the classic radial burst. */
    public static boolean isDirectional(HitContext ctx) {
        return ctx != null && ctx.direction != null
                && ctx.weapon != HitContext.Weapon.EXPLOSION && ctx.weapon != HitContext.Weapon.FALL
                && SimpleBloodClient.getConfig().directionalEnabled();
    }

    /**
     * Spawns {@code count} particles leaving the victim along the blow.
     *
     * @param velocityAdjust 1.0 in air, smaller underwater
     */
    public static void exitSpray(ClientLevel world, HitContext ctx, int count, float velocityAdjust) {
        exitSpray(world, ctx, count, velocityAdjust, false);
    }

    /**
     * @param firstTick true for the opening tick of a burst (or a one-shot death burst): the
     *                  weapon signature moves (a sword sweep, a mace slam) happen once, here
     */
    public static void exitSpray(ClientLevel world, HitContext ctx, int count, float velocityAdjust, boolean firstTick) {
        if (ctx.direction == null) return;
        RandomSource rng = world.getRandom();
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        boolean flavour = cfg.weaponFlavourEnabled();
        HitContext.Weapon weapon = flavour ? ctx.weapon : HitContext.Weapon.OTHER;

        boolean liquid = SimpleBlood.bloodKindOf(ctx.entity) == BloodKind.LIQUID;
        if (firstTick && liquid) {
            if (flavour) {
                switch (weapon) {
                    case BLADE -> sweep(world, ctx, velocityAdjust);
                    case ARROW -> pierce(world, ctx, velocityAdjust);
                    case MACE -> {
                        BloodSounds.splat(world, ctx.entity.position(), ctx.smash ? 1.0f : 0.5f);
                        slamStain(world, ctx);
                        if (ctx.smash) explosion(world, ctx, velocityAdjust);
                        else shockwave(world, ctx, velocityAdjust);
                    }
                    case AXE -> { if (ctx.severity > 0.5f) slamStain(world, ctx); }
                    default -> { }
                }
            }
            // Any hard hit throws a fountain up out of the wound: blunt weapons need less of a
            // hit for it, a sword or a fist has to really connect. A smash has its explosion.
            boolean blunt = weapon == HitContext.Weapon.MACE || weapon == HitContext.Weapon.AXE;
            if (!ctx.smash && ctx.severity > (blunt ? 0.35f : 0.5f)) geyser(world, ctx, velocityAdjust);
        }
        if (count <= 0) return;

        float speed = 0.28f + 0.45f * ctx.severity;
        float lateral = 0.35f, vertical = 0.30f, up = 0.06f, sizeMult = 1.0f;
        switch (weapon) {
            case BLADE -> { lateral = 0.75f; vertical = 0.12f; up = 0.05f; }              // flat sweeping arc
            case AXE   -> { speed *= 0.9f; lateral = 0.4f; vertical = 0.35f; up = -0.02f; sizeMult = 1.35f; count = Math.max(1, count * 3 / 4); }
            case ARROW -> { speed *= 1.4f; lateral = 0.18f; vertical = 0.15f; up = 0.02f; } // narrow fast stream
            case MACE  -> { speed *= 1.15f; lateral = 1.0f; vertical = 0.1f; up = -0.15f; } // slammed out and down
            default -> { }
        }
        if (ctx.crit) { lateral *= 0.7f; vertical *= 0.7f; speed *= 1.15f; }
        if (ctx.overkill) { speed *= 1.25f; count += count / 2; }

        // Blood bursts out of the wound back toward the attacker.
        Vec3 d = ctx.direction.scale(-1);
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 exit = weapon == HitContext.Weapon.MACE
                ? ctx.entity.position().add(0, 0.15, 0)   // at the feet
                : (ctx.wound != null ? ctx.wound : ctx.entryPoint());
        double hw = ctx.entity.getBbWidth() * 0.5, hh = ctx.entity.getBbHeight() * 0.5;
        // From a known wound the spray is tight; without one it spreads over the near face.
        double spreadU = ctx.wound != null ? 0.15 : 0.8, spreadV = ctx.wound != null ? 0.15 : 0.9;

        float prevSize = BloodParticle.currentSizeMultiplier();
        if (sizeMult != 1.0f) BloodParticle.setEntitySizeMultiplier(prevSize * sizeMult);
        try {
            for (int i = 0; i < count; i++) {
                // A little spread so it does not all come from one pixel.
                double ox = (rng.nextDouble() - 0.5) * hw * spreadU;
                double oy = (rng.nextDouble() - 0.5) * hh * spreadV;
                Vec3 p = exit.add(side.scale(ox)).add(0, oy, 0);
                if (weapon == HitContext.Weapon.MACE) p = exit.add((rng.nextDouble() - 0.5) * hw, 0, (rng.nextDouble() - 0.5) * hw);

                double s = speed * (0.6 + rng.nextDouble() * 0.8);
                Vec3 v = weapon == HitContext.Weapon.MACE
                        ? new Vec3(Math.cos(rng.nextDouble() * Math.PI * 2), 0, Math.sin(rng.nextDouble() * Math.PI * 2)).scale(s)
                        : d.scale(s);
                v = v.add(side.scale((rng.nextDouble() - 0.5) * 2 * lateral * s))
                        .add(0, (rng.nextDouble() - 0.5) * 2 * vertical * s + up, 0);
                boolean splash = i % 3 != 0;
                ClientBloodParticleSpawner.emit(world, ctx.entity, splash, p.x, p.y, p.z,
                        v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
            }
        } finally {
            if (sizeMult != 1.0f) BloodParticle.setEntitySizeMultiplier(prevSize);
        }
    }

    /**
     * A sword sweep: a wide, flat fan of long streaks flung off the blade back toward the
     * attacker at chest height, fast enough to land a few blocks away in a line.
     */
    private static void sweep(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        Vec3 d = ctx.direction.scale(-1);
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 exit = (ctx.wound != null ? ctx.wound : ctx.entryPoint()).add(0, ctx.entity.getBbHeight() * 0.05, 0);
        // A half-charged swing throws a weaker, shorter fan.
        float charge = 0.25f + 0.75f * ctx.charge;
        int count = Math.round((3 + ctx.severity * 6 + (ctx.crit ? 2 : 0)) * charge * SimpleBloodClient.getConfig().masterAmount());
        double halfArc = Math.toRadians(ctx.crit ? 40 : 60);
        float speed = (0.20f + 0.16f * ctx.severity) * (0.6f + 0.4f * ctx.charge);
        for (int i = 0; i < count; i++) {
            double a = (rng.nextDouble() * 2 - 1) * halfArc;
            Vec3 dir = d.scale(Math.cos(a)).add(side.scale(Math.sin(a))).normalize();
            double s = speed * (0.7 + rng.nextDouble() * 0.6);
            Vec3 v = dir.scale(s).add(0, 0.01 + rng.nextDouble() * 0.03, 0);
            Vec3 p = exit.add(side.scale((rng.nextDouble() - 0.5) * ctx.entity.getBbWidth() * 0.6))
                    .add(0, (rng.nextDouble() - 0.5) * 0.25, 0);
            ClientBloodParticleSpawner.emit(world, BloodParticles.BLOOD_STREAK, p.x, p.y, p.z,
                    v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
        }
    }

    /**
     * A big blunt hit also throws a fountain straight up out of the wound: a handful of drops
     * that go a couple of blocks high and rain back down around the victim.
     */
    private static void geyser(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        int count = Math.round((4 + ctx.severity * 8) * (0.4f + 0.6f * ctx.charge) * SimpleBloodClient.getConfig().masterAmount());
        Vec3 from = ctx.wound != null ? ctx.wound : ctx.entity.position().add(0, ctx.entity.getBbHeight() * 0.7, 0);
        float speed = 0.30f + 0.35f * ctx.severity;
        BloodParticle.setBallistic(true, 2.0f);
        try {
            for (int i = 0; i < count; i++) {
                double s = speed * (0.6 + rng.nextDouble() * 0.6);
                double a = rng.nextDouble() * Math.PI * 2, tilt = rng.nextDouble() * 0.25; // nearly straight up
                Vec3 v = new Vec3(Math.cos(a) * tilt * s, s, Math.sin(a) * tilt * s);
                Vec3 p = from.add((rng.nextDouble() - 0.5) * 0.2, (rng.nextDouble() - 0.5) * 0.15, (rng.nextDouble() - 0.5) * 0.2);
                ClientBloodParticleSpawner.emit(world, ctx.entity, i % 2 == 0, p.x, p.y, p.z,
                        v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    /**
     * A mace smash: the blood goes the way the block debris does, only up. As many drops as
     * vanilla's 750 debris particles (scaled by the master amount), thrown in every direction
     * with most of them heading skyward, fast enough to rain back down over several blocks.
     */
    private static void explosion(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        int count = Math.round(750 * SimpleBloodClient.getConfig().masterAmount());
        Vec3 c = ctx.entity.position().add(0, ctx.entity.getBbHeight() * 0.5, 0);
        double hw = ctx.entity.getBbWidth() * 0.5, hh = ctx.entity.getBbHeight() * 0.5;
        BloodParticle.setBallistic(true, 2.5f);
        try {
            for (int i = 0; i < count; i++) {
                // Any direction at all, seven in ten of them upward.
                double z = rng.nextDouble() * 2 - 1, a = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
                if (z < 0 && rng.nextInt(10) < 7) z = -z;
                double s = 0.35 + rng.nextDouble() * 0.75;
                Vec3 v = new Vec3(r * Math.cos(a) * s, z * s, r * Math.sin(a) * s);
                Vec3 p = c.add((rng.nextDouble() - 0.5) * 2 * hw, (rng.nextDouble() - 0.5) * 2 * hh, (rng.nextDouble() - 0.5) * 2 * hw);
                ClientBloodParticleSpawner.emit(world, ctx.entity, i % 5 != 0, p.x, p.y, p.z,
                        v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    /**
     * An arrow punching through: a tight stream of long drops carries on out of the far side
     * along the shot, the blade sweep's narrow cousin.
     */
    private static void pierce(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        int count = Math.round((2 + ctx.severity * 5) * SimpleBloodClient.getConfig().masterAmount());
        Vec3 d = ctx.direction;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 exit = ctx.exitPoint();
        if (ctx.wound != null) exit = new Vec3(exit.x, ctx.wound.y, exit.z);
        float speed = 0.22f + 0.22f * ctx.severity;
        for (int i = 0; i < count; i++) {
            double a = (rng.nextDouble() * 2 - 1) * Math.toRadians(12);
            Vec3 dir = d.scale(Math.cos(a)).add(side.scale(Math.sin(a))).normalize();
            double s = speed * (0.7 + rng.nextDouble() * 0.6);
            Vec3 v = dir.scale(s).add(0, 0.01 + rng.nextDouble() * 0.03, 0);
            Vec3 p = exit.add(side.scale((rng.nextDouble() - 0.5) * 0.15)).add(0, (rng.nextDouble() - 0.5) * 0.15, 0);
            ClientBloodParticleSpawner.emit(world, BloodParticles.BLOOD_STREAK, p.x, p.y, p.z,
                    v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
        }
    }

    /**
     * The mace's shockwave in blood: long drops thrown out low and fast in every direction,
     * landing a few blocks away as stretched spatter.
     */
    private static void shockwave(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        Vec3 c = ctx.entity.position().add(0, 0.25, 0);
        float charge = 0.25f + 0.75f * ctx.charge;
        int count = Math.round((8 + ctx.severity * 10) * charge * SimpleBloodClient.getConfig().masterAmount());
        float speed = (0.5f + 0.6f * ctx.severity) * (0.6f + 0.4f * ctx.charge);
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = speed * (0.6 + rng.nextDouble() * 0.7);
            Vec3 v = new Vec3(Math.cos(a) * s, 0.04 + rng.nextDouble() * 0.10, Math.sin(a) * s);
            Vec3 p = c.add(Math.cos(a) * 0.3, rng.nextDouble() * 0.3, Math.sin(a) * 0.3);
            ClientBloodParticleSpawner.emit(world, BloodParticles.BLOOD_STREAK, p.x, p.y, p.z,
                    v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
        }
    }

    /**
     * A blunt slam: a crater of blood painted straight onto the ground under the victim, with
     * spokes flung outward, on top of the flat ring of drops the mace branch throws.
     */
    private static void slamStain(ClientLevel world, HitContext ctx) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (!cfg.surfaces.enabled || SimpleBlood.bloodKindOf(ctx.entity) != BloodKind.LIQUID) return;
        RandomSource rng = world.getRandom();
        Vec3 feet = ctx.entity.position();
        BloodColor.Color c = BloodParticle.currentBloodColor();
        int colour = ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        // Splat strength follows the swing: a spam-click leaves a smudge, a full charge a crater.
        float charge = 0.2f + 0.8f * ctx.charge * ctx.charge;
        float scale = (cfg.surfaces.resolution * cfg.surfaces.resolution / 256.0f) * cfg.surfaceAmount() * charge;
        double reach = (0.8 + ctx.entity.getBbWidth() * 0.8 + ctx.severity * 1.4) * (0.4 + 0.6 * ctx.charge);

        // The core under the victim: big enough to spill onto the neighbouring blocks...
        for (int i = 0; i < 3; i++) {
            Vec3 at = i == 0 ? feet : feet.add((rng.nextDouble() - 0.5) * 0.6, 0, (rng.nextDouble() - 0.5) * 0.6);
            com.simpleblood.surface.BloodSurfaces.Hit hit = com.simpleblood.surface.BloodSurfaces.trace(world,
                    at.add(0, 0.3, 0), at.add(0, -1.2, 0));
            if (hit == null) continue;
            int pixels = Math.round((i == 0 ? 90 + ctx.severity * 110 : 30 + ctx.severity * 40) * scale);
            com.simpleblood.surface.BloodSurfaces.deposit(world, hit, colour, Math.max(8, pixels));
        }
        // ...and long spokes flung outward, each stretched away from the impact, with drops
        // at their tips.
        int spokes = Math.round((8 + ctx.severity * 8) * (0.4f + 0.6f * ctx.charge));
        for (int i = 0; i < spokes; i++) {
            double a = (i + rng.nextDouble()) * Math.PI * 2 / spokes;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            double len = reach * (0.4 + rng.nextDouble() * 0.6);
            for (double r = 0.35; r <= len; r += 0.45) {
                Vec3 at = feet.add(out.scale(r));
                com.simpleblood.surface.BloodSurfaces.Hit h = com.simpleblood.surface.BloodSurfaces.trace(world,
                        at.add(0, 0.3, 0), at.add(0, -1.2, 0));
                if (h == null) break;
                float falloff = (float) (1.0 - r / (len + 0.3));
                int pixels = Math.round((10 + rng.nextInt(10) + ctx.severity * 26) * falloff * scale);
                com.simpleblood.surface.BloodSurfaces.deposit(world, h, colour, Math.max(3, pixels), out);
            }
        }
    }

    /** A few drops carrying on out of the far side of the body, along the blow. */
    public static void entrySpatter(ClientLevel world, HitContext ctx, int count, float velocityAdjust) {
        if (count <= 0 || ctx.direction == null) return;
        RandomSource rng = world.getRandom();
        Vec3 d = ctx.direction;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        // Straight through from the wound to the opposite face.
        Vec3 exit = ctx.exitPoint();
        if (ctx.wound != null) exit = new Vec3(exit.x, ctx.wound.y, exit.z);
        double hw = ctx.entity.getBbWidth() * 0.5;
        float speed = 0.10f + 0.16f * ctx.severity;
        if (ctx.weapon == HitContext.Weapon.ARROW) { speed *= 1.5f; count += count / 2; } // arrows punch through
        for (int i = 0; i < count; i++) {
            Vec3 p = exit.add(side.scale((rng.nextDouble() - 0.5) * hw * 0.4)).add(0, (rng.nextDouble() - 0.5) * 0.2, 0);
            Vec3 v = d.scale(speed * (0.5 + rng.nextDouble()))
                    .add(side.scale((rng.nextDouble() - 0.5) * speed))
                    .add(0, rng.nextDouble() * 0.05, 0);
            ClientBloodParticleSpawner.emit(world, ctx.entity, false, p.x, p.y, p.z,
                    v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
        }
    }

    /** Explosion / fall: everything at once, thrown outward (and upward for explosions). */
    public static void radialBurst(ClientLevel world, HitContext ctx, int count, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        boolean explosion = ctx.weapon == HitContext.Weapon.EXPLOSION;
        Vec3 c = ctx.entity.position().add(0, explosion ? ctx.entity.getBbHeight() * 0.5 : 0.1, 0);
        double hw = ctx.entity.getBbWidth() * 0.5;
        float speed = (explosion ? 0.35f : 0.18f) + 0.3f * ctx.severity;
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = speed * (0.5 + rng.nextDouble());
            double vy = explosion ? rng.nextDouble() * s * 1.2 : rng.nextDouble() * 0.04;
            Vec3 p = c.add((rng.nextDouble() - 0.5) * hw, (rng.nextDouble() - 0.5) * (explosion ? ctx.entity.getBbHeight() * 0.6 : 0.1), (rng.nextDouble() - 0.5) * hw);
            ClientBloodParticleSpawner.emit(world, ctx.entity, i % 2 == 0, p.x, p.y, p.z,
                    Math.cos(a) * s * velocityAdjust, vy * velocityAdjust, Math.sin(a) * s * velocityAdjust);
        }
    }
}
