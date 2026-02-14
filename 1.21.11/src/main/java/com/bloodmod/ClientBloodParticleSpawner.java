package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public class ClientBloodParticleSpawner {

    public static void spawnBloodOnDeath(ClientWorld world, LivingEntity entity) {

        BloodModConfig config = BloodModClient.getConfig();

        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        boolean canTransform = BloodMod.shouldEntityTransformToStains(entity) && BloodMod.shouldEntityDripAtLowHealth(entity);
        BloodParticle.setShouldTransformToFog(canTransform);

        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater(entity));

        double posX = entity.getX();
        double posY = entity.getY() + entity.getHeight() * 0.5;
        double posZ = entity.getZ();

        boolean entityIsUnderwater = isEntityInWater(entity);

        float sizeFactor = entity.getWidth(); 

        float intensityMult = config.deathIntensityMultiplier();
        float spreadMult = config.deathSpreadMultiplier();

        int dripCount   = (int)(30 * sizeFactor * intensityMult); 

        int splashCount = (int)(25 * sizeFactor * intensityMult); 

        if (!entityIsUnderwater) {
            playBloodSound(world, posX, posY, posZ, sizeFactor);
        }

        float velocityAdjust = entityIsUnderwater ? 0.3f : 1.0f;

        for (int i = 0; i < dripCount; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * entity.getWidth() * 1.8 * spreadMult;
            double offsetY = (world.random.nextDouble()) * entity.getHeight() * 0.8;
            double offsetZ = (world.random.nextDouble() - 0.5) * entity.getWidth() * 1.8 * spreadMult;

            double velX = (world.random.nextDouble() - 0.5) * 0.4 * spreadMult;
            double velY = (-0.5 - world.random.nextDouble() * 1.5) * velocityAdjust;
            double velZ = (world.random.nextDouble() - 0.5) * 0.4 * spreadMult;

            MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_DRIP,
                    posX + offsetX, posY + offsetY, posZ + offsetZ,
                    velX, velY, velZ
            );
        }

        for (int i = 0; i < splashCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double radius = (0.2 + world.random.nextDouble() * entity.getWidth() * 1.2) * spreadMult;

            double offsetX = Math.cos(angle) * radius;
            double offsetY = (world.random.nextDouble() - 0.3) * entity.getHeight() * 0.6;
            double offsetZ = Math.sin(angle) * radius;

            double speed = (0.3 + world.random.nextDouble() * 0.4) * spreadMult;
            double velX = Math.cos(angle) * speed;
            double velY = (-0.2 - world.random.nextDouble() * 0.6) * velocityAdjust;
            double velZ = Math.sin(angle) * speed;

            MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_SPLASH,
                    posX + offsetX, posY + offsetY, posZ + offsetZ,
                    velX, velY, velZ
            );
        }
    }

    public static void spawnBloodForLowHealth(ClientWorld world, LivingEntity entity, BloodModConfig config) {
        float healthPercent = entity.getHealth() / entity.getMaxHealth();
        float threshold = config.lowHealthThreshold();

        if (healthPercent > threshold) {
            return;
        }

        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        boolean canTransform = BloodMod.shouldEntityTransformToStains(entity) && BloodMod.shouldEntityDripAtLowHealth(entity);
        BloodParticle.setShouldTransformToFog(canTransform);

        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater(entity));

        float frequentTier = threshold * 0.5f; 

        float splashTier   = threshold * 0.3f; 

        int baseChance = healthPercent < frequentTier ? 6 : 10;
        int chance = (int)(baseChance / config.dripFrequencyMultiplier());
        chance = Math.max(1, chance); 

        if (world.random.nextInt(chance) == 0) {
            double posX = entity.getX();
            double posY = entity.getY() + entity.getHeight() * 0.6;
            double posZ = entity.getZ();

            boolean entityIsUnderwater = isEntityInWater(entity);

            if (world.random.nextInt(5) == 0 && !entityIsUnderwater) {
                playBloodSound(world, posX, posY, posZ, 0.3f);
            }

            float velocityAdjust = entityIsUnderwater ? 0.4f : 1.0f;

            int baseDripCount = healthPercent < frequentTier ? 3 : 2;
            int dripCount = (int)(baseDripCount * config.dripIntensityMultiplier());

            for (int i = 0; i < dripCount; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * entity.getWidth() * 0.8;
                double offsetY = (world.random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (world.random.nextDouble() - 0.5) * entity.getWidth() * 0.8;

                double velX = (world.random.nextDouble() - 0.5) * 0.1;
                double velY = (-1.5 - world.random.nextDouble() * 0.5) * velocityAdjust;
                double velZ = (world.random.nextDouble() - 0.5) * 0.1;

                MinecraftClient.getInstance().particleManager.addParticle(
                        BloodParticles.BLOOD_DRIP,
                        posX + offsetX,
                        posY + offsetY,
                        posZ + offsetZ,
                        velX, velY, velZ
                );
            }

            if (healthPercent < splashTier) {
                int baseSplashCount = world.random.nextInt(2) + 1; 

                int splashCount = (int)(baseSplashCount * config.dripIntensityMultiplier());

                for (int i = 0; i < splashCount; i++) {
                    double offsetX = (world.random.nextDouble() - 0.5) * entity.getWidth() * 0.6;
                    double offsetZ = (world.random.nextDouble() - 0.5) * entity.getWidth() * 0.6;

                    double velX = (world.random.nextDouble() - 0.5) * 0.15;
                    double velZ = (world.random.nextDouble() - 0.5) * 0.15;

                    MinecraftClient.getInstance().particleManager.addParticle(
                            BloodParticles.BLOOD_SPLASH,
                            posX + offsetX,
                            posY - 0.1,
                            posZ + offsetZ,
                            velX,
                            (-1.2 - world.random.nextDouble() * 0.4) * velocityAdjust,
                            velZ
                    );
                }
            }
        }
    }

    private static boolean isEntityInWater(LivingEntity entity) {
        return entity.isSubmergedInWater() || entity.isTouchingWater();
    }

    private static void playBloodSound(ClientWorld world, double x, double y, double z, float sizeFactor) {

        BloodModConfig config = BloodModClient.getConfig();
        if (!config.soundEnabled()) {
            return;
        }

        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.3f + sizeFactor * 0.2f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.8f + world.random.nextFloat() * 0.3f; 

        float pitch = basePitch * pitchMult;

        MinecraftClient.getInstance().getSoundManager().play(
                new net.minecraft.client.sound.PositionedSoundInstance(
                        SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_LAVA,
                        SoundCategory.PLAYERS,
                        volume,
                        pitch,
                        net.minecraft.util.math.random.Random.create(),
                        x, y, z
                )
        );
    }

    private static boolean shouldParticlesDespawnInWater(LivingEntity entity) {
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType());
        String entityType = id.getPath();

        return entityType.equals("snow_golem");
    }
}