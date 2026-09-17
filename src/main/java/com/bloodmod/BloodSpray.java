package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class BloodSpray {

    private BloodSpray() {}

    public static boolean isDirectional(HitContext ctx) {
        return ctx != null && ctx.direction != null
                && ctx.weapon != HitContext.Weapon.EXPLOSION && ctx.weapon != HitContext.Weapon.FALL
                && BloodModClient.getConfig().directionalEnabled();
    }

    public static void exitSpray(ClientLevel world, HitContext ctx, int count, float velocityAdjust) {
        exitSpray(world, ctx, count, velocityAdjust, false);
    }

    public static void exitSpray(ClientLevel world, HitContext ctx, int count, float velocityAdjust, boolean firstTick) {
        if (ctx.direction == null) return;
        RandomSource rng = world.getRandom();
        BloodModConfig cfg = BloodModClient.getConfig();
        boolean flavour = cfg.weaponFlavourEnabled();
        HitContext.Weapon weapon = flavour ? ctx.weapon : HitContext.Weapon.OTHER;

        boolean liquid = BloodMod.bloodKindOf(ctx.entity) == BloodKind.LIQUID;
        if (firstTick && liquid) {
            if (flavour) {
                switch (weapon) {
                    case BLADE -> sweep(world, ctx, velocityAdjust);
                    case ARROW -> pierce(world, ctx, velocityAdjust);
                    case MACE -> {
                        slamStain(world, ctx);
                        if (ctx.smash) explosion(world, ctx, velocityAdjust);
                        else shockwave(world, ctx, velocityAdjust);
                    }
                    case AXE -> { if (ctx.severity > 0.5f) slamStain(world, ctx); }
                    default -> { }
                }
            }
            boolean blunt = weapon == HitContext.Weapon.MACE || weapon == HitContext.Weapon.AXE;
            if (!ctx.smash && ctx.severity > (blunt ? 0.35f : 0.5f)) geyser(world, ctx, velocityAdjust);
        }
        if (count <= 0) return;

        float speed = 0.28f + 0.45f * ctx.severity;
        float lateral = 0.35f, vertical = 0.30f, up = 0.06f, sizeMult = 1.0f;
        switch (weapon) {
            case BLADE -> { lateral = 0.75f; vertical = 0.12f; up = 0.05f; }
            case AXE   -> { speed *= 0.9f; lateral = 0.4f; vertical = 0.35f; up = -0.02f; sizeMult = 1.35f; count = Math.max(1, count * 3 / 4); }
            case ARROW -> { speed *= 1.4f; lateral = 0.18f; vertical = 0.15f; up = 0.02f; }
            case MACE  -> { speed *= 1.15f; lateral = 1.0f; vertical = 0.1f; up = -0.15f; }
            default -> { }
        }
        if (ctx.crit) { lateral *= 0.7f; vertical *= 0.7f; speed *= 1.15f; }
        if (ctx.overkill) { speed *= 1.25f; count += count / 2; }

        Vec3 d = ctx.direction.scale(-1);
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 exit = weapon == HitContext.Weapon.MACE
                ? ctx.entity.position().add(0, 0.15, 0)
                : (ctx.wound != null ? ctx.wound : ctx.entryPoint());
        double hw = ctx.entity.getBbWidth() * 0.5, hh = ctx.entity.getBbHeight() * 0.5;
        double spreadU = ctx.wound != null ? 0.15 : 0.8, spreadV = ctx.wound != null ? 0.15 : 0.9;

        float prevSize = BloodParticle.currentSizeMultiplier();
        if (sizeMult != 1.0f) BloodParticle.setEntitySizeMultiplier(prevSize * sizeMult);
        try {
            for (int i = 0; i < count; i++) {
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

    private static void sweep(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        Vec3 d = ctx.direction.scale(-1);
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 exit = (ctx.wound != null ? ctx.wound : ctx.entryPoint()).add(0, ctx.entity.getBbHeight() * 0.05, 0);
        float charge = 0.25f + 0.75f * ctx.charge;
        int count = Math.round((3 + ctx.severity * 6 + (ctx.crit ? 2 : 0)) * charge * BloodModClient.getConfig().masterAmount());
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

    private static void geyser(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        int count = Math.round((4 + ctx.severity * 8) * (0.4f + 0.6f * ctx.charge) * BloodModClient.getConfig().masterAmount());
        Vec3 from = ctx.wound != null ? ctx.wound : ctx.entity.position().add(0, ctx.entity.getBbHeight() * 0.7, 0);
        float speed = 0.30f + 0.35f * ctx.severity;
        BloodParticle.setBallistic(true, 2.0f);
        try {
            for (int i = 0; i < count; i++) {
                double s = speed * (0.6 + rng.nextDouble() * 0.6);
                double a = rng.nextDouble() * Math.PI * 2, tilt = rng.nextDouble() * 0.25;
                Vec3 v = new Vec3(Math.cos(a) * tilt * s, s, Math.sin(a) * tilt * s);
                Vec3 p = from.add((rng.nextDouble() - 0.5) * 0.2, (rng.nextDouble() - 0.5) * 0.15, (rng.nextDouble() - 0.5) * 0.2);
                ClientBloodParticleSpawner.emit(world, ctx.entity, i % 2 == 0, p.x, p.y, p.z,
                        v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static void explosion(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        int count = Math.round(750 * BloodModClient.getConfig().masterAmount());
        Vec3 c = ctx.entity.position().add(0, ctx.entity.getBbHeight() * 0.5, 0);
        double hw = ctx.entity.getBbWidth() * 0.5, hh = ctx.entity.getBbHeight() * 0.5;
        BloodParticle.setBallistic(true, 2.5f);
        try {
            for (int i = 0; i < count; i++) {
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

    private static void pierce(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        int count = Math.round((2 + ctx.severity * 5) * BloodModClient.getConfig().masterAmount());
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

    private static void shockwave(ClientLevel world, HitContext ctx, float velocityAdjust) {
        RandomSource rng = world.getRandom();
        Vec3 c = ctx.entity.position().add(0, 0.25, 0);
        float charge = 0.25f + 0.75f * ctx.charge;
        int count = Math.round((8 + ctx.severity * 10) * charge * BloodModClient.getConfig().masterAmount());
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

    private static void slamStain(ClientLevel world, HitContext ctx) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (!cfg.surfaces.enabled || BloodMod.bloodKindOf(ctx.entity) != BloodKind.LIQUID) return;
        RandomSource rng = world.getRandom();
        Vec3 feet = ctx.entity.position();
        BloodColor.Color c = BloodParticle.currentBloodColor();
        int colour = ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        float charge = 0.2f + 0.8f * ctx.charge * ctx.charge;
        float scale = (cfg.surfaces.resolution * cfg.surfaces.resolution / 256.0f) * cfg.surfaceAmount() * charge;
        double reach = (0.8 + ctx.entity.getBbWidth() * 0.8 + ctx.severity * 1.4) * (0.4 + 0.6 * ctx.charge);

        for (int i = 0; i < 3; i++) {
            Vec3 at = i == 0 ? feet : feet.add((rng.nextDouble() - 0.5) * 0.6, 0, (rng.nextDouble() - 0.5) * 0.6);
            com.bloodmod.surface.BloodSurfaces.Hit hit = com.bloodmod.surface.BloodSurfaces.trace(world,
                    at.add(0, 0.3, 0), at.add(0, -1.2, 0));
            if (hit == null) continue;
            int pixels = Math.round((i == 0 ? 90 + ctx.severity * 110 : 30 + ctx.severity * 40) * scale);
            com.bloodmod.surface.BloodSurfaces.deposit(world, hit, colour, Math.max(8, pixels));
        }
        int spokes = Math.round((8 + ctx.severity * 8) * (0.4f + 0.6f * ctx.charge));
        for (int i = 0; i < spokes; i++) {
            double a = (i + rng.nextDouble()) * Math.PI * 2 / spokes;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            double len = reach * (0.4 + rng.nextDouble() * 0.6);
            for (double r = 0.35; r <= len; r += 0.45) {
                Vec3 at = feet.add(out.scale(r));
                com.bloodmod.surface.BloodSurfaces.Hit h = com.bloodmod.surface.BloodSurfaces.trace(world,
                        at.add(0, 0.3, 0), at.add(0, -1.2, 0));
                if (h == null) break;
                float falloff = (float) (1.0 - r / (len + 0.3));
                int pixels = Math.round((10 + rng.nextInt(10) + ctx.severity * 26) * falloff * scale);
                com.bloodmod.surface.BloodSurfaces.deposit(world, h, colour, Math.max(3, pixels), out);
            }
        }
    }

    public static void entrySpatter(ClientLevel world, HitContext ctx, int count, float velocityAdjust) {
        if (count <= 0 || ctx.direction == null) return;
        RandomSource rng = world.getRandom();
        Vec3 d = ctx.direction;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 exit = ctx.exitPoint();
        if (ctx.wound != null) exit = new Vec3(exit.x, ctx.wound.y, exit.z);
        double hw = ctx.entity.getBbWidth() * 0.5;
        float speed = 0.10f + 0.16f * ctx.severity;
        if (ctx.weapon == HitContext.Weapon.ARROW) { speed *= 1.5f; count += count / 2; }
        for (int i = 0; i < count; i++) {
            Vec3 p = exit.add(side.scale((rng.nextDouble() - 0.5) * hw * 0.4)).add(0, (rng.nextDouble() - 0.5) * 0.2, 0);
            Vec3 v = d.scale(speed * (0.5 + rng.nextDouble()))
                    .add(side.scale((rng.nextDouble() - 0.5) * speed))
                    .add(0, rng.nextDouble() * 0.05, 0);
            ClientBloodParticleSpawner.emit(world, ctx.entity, false, p.x, p.y, p.z,
                    v.x * velocityAdjust, v.y * velocityAdjust, v.z * velocityAdjust);
        }
    }

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
