package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Chunks of meat knocked off a mob that bleeds (Gore settings, off unless switched on). A hard
 * hit now and then throws one; a death throws a handful. They fly back towards whatever struck,
 * bleed as they go and where they land (see {@link com.simpleblood.particle.DebrisPieceParticle}).
 */
public final class Giblets {
    private Giblets() {}

    /** Chunks per point of damage on a hit, before the Amount setting. */
    private static final float PER_DAMAGE = 0.06f;
    private static final int MAX_ON_HIT = 3;
    private static final int MAX_ON_DEATH = 10;
    /** How wide the fan of chunks thrown back towards the attacker is. */
    private static final double FAN = Math.toRadians(170);

    /** A hit: the harder, the likelier a chunk comes off. */
    public static void hit(ClientLevel level, LivingEntity entity, HitContext ctx) {
        if (!wanted(entity)) return;
        float expected = ctx.damage * PER_DAMAGE * amount();
        throwSome(level, entity, ctx, Math.min(MAX_ON_HIT, roll(level.getRandom(), expected)));
    }

    /**
     * A death: a handful, more off bigger bodies and harder killing blows. Puts the spawn
     * settings back to their defaults afterwards, so it goes after the rest of a death burst.
     */
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

    /** The whole part of {@code expected}, plus one more with the chance of what is left over. */
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
        BloodParticle.setBallistic(true, 1.0f); // chunks fly where they are thrown
        try {
            boolean underwater = entity.isInWater();
            Vec3 from = ctx != null && ctx.wound != null ? ctx.wound : entity.position().add(0, entity.getBbHeight() * 0.6, 0);
            Vec3 along = BloodSpray.isDirectional(ctx) ? ctx.direction : null;
            double spread = 0.1 + entity.getBbWidth() * 0.15;
            // Towards the attacker (the blow travelled the other way), anywhere in a fan around it.
            double towards = along != null ? Math.atan2(-along.z, -along.x) : 0;
            for (int i = 0; i < count; i++) {
                double a = along != null ? towards + (rng.nextDouble() - 0.5) * FAN : rng.nextDouble() * Math.PI * 2;
                double out = 0.06 + rng.nextDouble() * 0.1;
                double vx = Math.cos(a) * out, vz = Math.sin(a) * out;
                double vy = 0.12 + rng.nextDouble() * 0.12; // a gentle lob
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
