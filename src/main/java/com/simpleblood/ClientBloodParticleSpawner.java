package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

public class ClientBloodParticleSpawner {

    public static void emit(ClientLevel world, LivingEntity entity, boolean splash,
                            double x, double y, double z, double vx, double vy, double vz) {
        emit(world, splash ? BloodParticles.BLOOD_SPLASH : BloodParticles.BLOOD_DRIP, x, y, z, vx, vy, vz);
    }

    public static void emit(ClientLevel world, net.minecraft.core.particles.SimpleParticleType type,
                            double x, double y, double z, double vx, double vy, double vz) {
        if (!BloodParticle.underBudget()) return;
        if (!Guard.ok(Guard.Part.PARTICLES)) return;
        BloodKind kind = BloodParticle.currentBloodKind();
        if (kind.hasPieces()) {
            emitPiece(world, kind, type, x, y, z, vx, vy, vz);
            return;
        }
        if (BloodParticle.currentTransformsToFog() && isPositionInWater(world, x, y, z)
                && SimpleBloodClient.getConfig().underwaterFogEnabled()) {
            com.simpleblood.particle.BloodFogParticle.setCurrentBloodColor(BloodParticle.currentBloodColor());
            com.simpleblood.particle.BloodFogParticle.setGlowing(BloodParticle.currentGlows());
            mergedFog(x, y, z, vx, vy, vz);
        } else {
            Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        }
    }

    private static void emitPiece(ClientLevel world, BloodKind kind, net.minecraft.core.particles.SimpleParticleType type,
                                  double x, double y, double z, double vx, double vy, double vz) {
        net.minecraft.util.RandomSource rng = world.getRandom();
        net.minecraft.core.particles.SimpleParticleType piece;
        switch (kind) {
            case METAL -> piece = BloodParticles.METAL_FLAKE;
            case SPIRIT -> piece = BloodParticles.SPIRIT_SPARK;
            case EMBER -> {
                if (rng.nextFloat() < 0.2f) {
                    Minecraft.getInstance().particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            x, y, z, vx * 0.1, 0.03, vz * 0.1);
                    return;
                }
                piece = BloodParticles.EMBER;
            }
            case WIND -> {
                if (rng.nextBoolean()) return;
                piece = BloodParticles.WIND_PUFF;
            }
            case WOOD -> {
                if (rng.nextFloat() < 0.35f) {
                    BloodParticle.setCurrentKind(BloodKind.LIQUID);
                    try {
                        Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
                    } finally {
                        BloodParticle.setCurrentKind(kind);
                    }
                    return;
                }
                piece = BloodParticles.WOOD_SPLINTER;
            }
            default -> {
                boolean big = type == BloodParticles.BLOOD_SPLASH || type == BloodParticles.BLOOD_STREAK;
                piece = rng.nextFloat() < (big ? 0.6f : 0.25f) ? BloodParticles.BONE_DUST : BloodParticles.BONE_CHIP;
            }
        }
        Minecraft.getInstance().particleEngine.createParticle(piece, x, y, z, vx, vy, vz);
    }

    private static final int FOG_MERGE = 4;
    private static final float FOG_MERGE_SCALE = 1.8f;
    private static int fogCount;

    public static void mergedFog(double x, double y, double z, double vx, double vy, double vz) {
        if (fogCount++ % FOG_MERGE != 0) return;
        com.simpleblood.particle.BloodFogParticle.setScale(FOG_MERGE_SCALE);
        try {
            Minecraft.getInstance().particleEngine.createParticle(BloodParticles.BLOOD_FOG, x, y, z, vx, vy, vz);
        } finally {
            com.simpleblood.particle.BloodFogParticle.setScale(1.0f);
        }
    }

    public static void spawnBloodOnDeath(ClientLevel world, LivingEntity entity) {
        spawnBloodOnDeath(world, entity, null);
    }

    public static void spawnBloodOnDeath(ClientLevel world, LivingEntity entity, HitContext ctx) {
        com.simpleblood.compat.PhysicsModRagdolls.entityDied(entity);
        SimpleBloodConfig config = SimpleBloodClient.getConfig();

        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(entity));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(entity));
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(entity));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(entity));

        boolean canTransform = SimpleBlood.createsFogUnderwater(entity);
        BloodParticle.setShouldTransformToFog(canTransform);

        BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(entity));

        double posX = entity.getX();
        double posY = entity.getY() + entity.getBbHeight() * 0.5;
        double posZ = entity.getZ();

        boolean entityIsUnderwater = isEntityInWater(entity);

        float sizeFactor = entity.getBbWidth();
        float intensityMult = config.deathIntensityMultiplier() * SimpleBlood.getBurstIntensityMultiplier(entity);
        float spreadMult = config.deathSpreadMultiplier();

        int dripCount   = (int)(30 * sizeFactor * intensityMult);
        int splashCount = (int)(25 * sizeFactor * intensityMult);

        BloodSounds.hit(world, entity, Math.min(0.3f + sizeFactor * 0.2f, 1.0f), true, entityIsUnderwater);

        float velocityAdjust = entityIsUnderwater ? 0.3f : 1.0f;

        for (int i = 0; i < dripCount; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 1.8 * spreadMult;
            double offsetY = (world.getRandom().nextDouble()) * entity.getBbHeight() * 0.8;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * entity.getBbWidth() * 1.8 * spreadMult;

            double velX = (world.getRandom().nextDouble() - 0.5) * 0.4 * spreadMult;
            double velY = (-0.5 - world.getRandom().nextDouble() * 1.5) * velocityAdjust;
            double velZ = (world.getRandom().nextDouble() - 0.5) * 0.4 * spreadMult;

            double spawnX = posX + offsetX;
            double spawnY = posY + offsetY;
            double spawnZ = posZ + offsetZ;

            emit(world, BloodParticles.BLOOD_DRIP, spawnX, spawnY, spawnZ, velX, velY, velZ);
        }

        if (ctx != null && ctx.weapon == HitContext.Weapon.EXPLOSION) {
            BloodSpray.radialBurst(world, ctx, splashCount, velocityAdjust);
            splashCount = 0;
        } else if (BloodSpray.isDirectional(ctx)) {
            int directional = Math.round(splashCount * config.directionalShare());
            BloodSpray.exitSpray(world, ctx, directional, velocityAdjust, true);
            if (config.entrySpatterEnabled()) BloodSpray.entrySpatter(world, ctx, Math.max(1, directional / 6), velocityAdjust);
            splashCount -= directional;
        }

        for (int i = 0; i < splashCount; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double radius = (0.2 + world.getRandom().nextDouble() * entity.getBbWidth() * 1.2) * spreadMult;

            double offsetX = Math.cos(angle) * radius;
            double offsetY = (world.getRandom().nextDouble() - 0.3) * entity.getBbHeight() * 0.6;
            double offsetZ = Math.sin(angle) * radius;

            double speed = (0.3 + world.getRandom().nextDouble() * 0.4) * spreadMult;
            double velX = Math.cos(angle) * speed;
            double velY = (-0.2 - world.getRandom().nextDouble() * 0.6) * velocityAdjust;
            double velZ = Math.sin(angle) * speed;

            double spawnX = posX + offsetX;
            double spawnY = posY + offsetY;
            double spawnZ = posZ + offsetZ;

            emit(world, BloodParticles.BLOOD_SPLASH, spawnX, spawnY, spawnZ, velX, velY, velZ);
        }
    }

    public static void spawnBloodForLowHealth(ClientLevel world, LivingEntity entity, SimpleBloodConfig config) {
        float healthPercent = entity.getHealth() / entity.getMaxHealth();
        float threshold = config.lowHealthThreshold();

        if (healthPercent > threshold) {
            return;
        }

        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(entity));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(entity));
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(entity));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(entity));

        boolean canTransform = SimpleBlood.createsFogUnderwater(entity);
        BloodParticle.setShouldTransformToFog(canTransform);

        BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(entity));

        float frequentTier = threshold * 0.5f;
        float splashTier   = threshold * 0.3f;

        int baseChance = healthPercent < frequentTier ? 6 : 10;
        int chance = (int)(baseChance / config.dripFrequencyMultiplier());
        chance = Math.max(1, chance);

        if (world.getRandom().nextInt(chance) == 0) {
            double posX = entity.getX();
            double posY = entity.getY() + entity.getBbHeight() * 0.6;
            double posZ = entity.getZ();
            net.minecraft.world.phys.Vec3 wound = entity instanceof BloodEntityAccess a ? a.simpleblood$wound() : null;
            double spreadX = entity.getBbWidth() * 0.8, spreadZ = spreadX;
            if (wound != null) {
                posX = wound.x;
                posY = wound.y;
                posZ = wound.z;
                spreadX = spreadZ = 0.08 + entity.getBbWidth() * 0.1;
            }

            boolean entityIsUnderwater = isEntityInWater(entity);

            if (world.getRandom().nextInt(5) == 0 && !entityIsUnderwater) {
                playBloodSound(world, entity, posX, posY, posZ, 0.3f);
            }

            float velocityAdjust = entityIsUnderwater ? 0.4f : 1.0f;

            int baseDripCount = healthPercent < frequentTier ? 3 : 2;
            int dripCount = (int)(baseDripCount * config.dripIntensityMultiplier()
                    * SimpleBlood.getDripIntensityMultiplier(entity));

            for (int i = 0; i < dripCount; i++) {
                double offsetX = (world.getRandom().nextDouble() - 0.5) * spreadX;
                double offsetY = (world.getRandom().nextDouble() - 0.5) * 0.2;
                double offsetZ = (world.getRandom().nextDouble() - 0.5) * spreadZ;

                double velX = (world.getRandom().nextDouble() - 0.5) * 0.1;
                double velY = (-1.5 - world.getRandom().nextDouble() * 0.5) * velocityAdjust;
                double velZ = (world.getRandom().nextDouble() - 0.5) * 0.1;

                double spawnX = posX + offsetX;
                double spawnY = posY + offsetY;
                double spawnZ = posZ + offsetZ;

                emit(world, BloodParticles.BLOOD_DRIP, spawnX, spawnY, spawnZ, velX, velY, velZ);
            }

            if (healthPercent < splashTier) {
                int baseSplashCount = world.getRandom().nextInt(2) + 1;
                int splashCount = (int)(baseSplashCount * config.dripIntensityMultiplier()
                        * SimpleBlood.getDripIntensityMultiplier(entity));

                for (int i = 0; i < splashCount; i++) {
                    double offsetX = (world.getRandom().nextDouble() - 0.5) * spreadX * 0.75;
                    double offsetZ = (world.getRandom().nextDouble() - 0.5) * spreadZ * 0.75;

                    double velX = (world.getRandom().nextDouble() - 0.5) * 0.15;
                    double velZ = (world.getRandom().nextDouble() - 0.5) * 0.15;
                    double velY = (-1.2 - world.getRandom().nextDouble() * 0.4) * velocityAdjust;

                    double spawnX = posX + offsetX;
                    double spawnY = posY - 0.1;
                    double spawnZ = posZ + offsetZ;

                    emit(world, BloodParticles.BLOOD_SPLASH, spawnX, spawnY, spawnZ, velX, velY, velZ);
                }
            }
        }
    }

    private static boolean isEntityInWater(LivingEntity entity) {
        return entity.isUnderWater() || entity.isInWater();
    }

    private static void playBloodSound(ClientLevel world, LivingEntity entity, double x, double y, double z, float sizeFactor) {
        if (!SimpleBlood.isSoundEnabledFor(entity)) {
            return;
        }
        SimpleBloodConfig config = SimpleBloodClient.getConfig();

        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.3f + sizeFactor * 0.2f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.8f + world.getRandom().nextFloat() * 0.3f;
        float pitch = basePitch * pitchMult;

        Minecraft.getInstance().getSoundManager().play(
                new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        SimpleBlood.getBloodSound(entity),
                        SoundSource.PLAYERS,
                        volume,
                        pitch,
                        net.minecraft.util.RandomSource.create(),
                        x, y, z
                )
        );
    }

    private static boolean isPositionInWater(ClientLevel world, double x, double y, double z) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);

        if (state.is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }

        double fracX = x - pos.getX();
        double fracY = y - pos.getY();
        double fracZ = z - pos.getZ();

        if (fracX < 0.2 && world.getBlockState(pos.west()).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }
        if (fracX > 0.8 && world.getBlockState(pos.east()).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }
        if (fracY < 0.2 && world.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }
        if (fracY > 0.8 && world.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }
        if (fracZ < 0.2 && world.getBlockState(pos.north()).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }
        if (fracZ > 0.8 && world.getBlockState(pos.south()).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }

        return false;
    }
}
