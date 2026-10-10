package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class Giblets {
    private Giblets() {}

    private static final float PER_DAMAGE = 0.06f;
    private static final int MAX_ON_HIT = 3;
    private static final int MAX_ON_DEATH = 10;

    public static void hit(ClientLevel level, LivingEntity entity, HitContext ctx) {
        if (!wanted(entity)) return;
        float expected = ctx.damage * PER_DAMAGE * amount();
        throwSome(level, entity, ctx, Math.min(MAX_ON_HIT, roll(level.getRandom(), expected)));
    }

    public static void death(ClientLevel level, LivingEntity entity, HitContext ctx) {
        if (!wanted(entity)) return;
        float expected = (2f + entity.getBbWidth() * 3f) * (ctx != null && ctx.overkill ? 1.5f : 1f) * amount();
        throwSome(level, entity, ctx, Math.min(MAX_ON_DEATH, roll(level.getRandom(), expected)));
    }

    private static boolean wanted(LivingEntity entity) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg != null && cfg.gore.giblets && SimpleBlood.bloodKindOf(entity) == BloodKind.LIQUID;
    }

    private static float amount() {
        return Math.max(0, SimpleBloodClient.getConfig().gore.gibletAmount) / 100f;
    }

    private static int roll(RandomSource rng, float expected) {
        int n = (int) expected;
        return n + (rng.nextFloat() < expected - n ? 1 : 0);
    }

    private static void throwSome(ClientLevel level, LivingEntity entity, HitContext ctx, int count) {
        if (count <= 0) return;
        RandomSource rng = level.getRandom();
        BloodParticle.setCurrentBloodColor(BloodColor.getBloodColor(entity));
        BloodParticle.setCurrentKind(BloodKind.LIQUID);
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(entity));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(entity));
        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(entity));
        BloodParticle.setBallistic(true, 1.0f);
        try {
            boolean underwater = entity.isInWater();
            Vec3 from = ctx != null && ctx.wound != null ? ctx.wound : entity.position().add(0, entity.getBbHeight() * 0.6, 0);
            Vec3 along = BloodSpray.isDirectional(ctx) ? ctx.direction : null;
            double spread = 0.1 + entity.getBbWidth() * 0.15;
            for (int i = 0; i < count; i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double out = 0.08 + rng.nextDouble() * 0.14;
                double vx = Math.cos(a) * out, vz = Math.sin(a) * out;
                double vy = 0.18 + rng.nextDouble() * 0.2;
                if (along != null) {
                    double push = 0.15 + rng.nextDouble() * 0.15;
                    vx = vx * 0.5 + along.x * push;
                    vz = vz * 0.5 + along.z * push;
                }
                if (underwater) {
                    vx *= 0.3;
                    vy *= 0.3;
                    vz *= 0.3;
                }
                Minecraft.getInstance().particleEngine.createParticle(BloodParticles.GIBLET,
                        from.x + (rng.nextDouble() - 0.5) * spread, from.y + (rng.nextDouble() - 0.5) * spread,
                        from.z + (rng.nextDouble() - 0.5) * spread, vx, vy, vz);
            }
        } finally {
            BloodParticle.resetSpawnState();
        }
    }
}
