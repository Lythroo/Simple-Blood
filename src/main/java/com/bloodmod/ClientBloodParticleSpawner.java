package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public class ClientBloodParticleSpawner {

    /**
     * One-shot death burst. Spawns a large number of drips and splashes
     * centered on the entity at the moment it dies. Counts and spread
     * scale with the entity's width so bigger mobs bleed more.
     * Now works underwater too - particles will transform to fog when they hit water!
     */
    public static void spawnBloodOnDeath(ClientWorld world, LivingEntity entity) {
        // Get config for multipliers
        BloodModConfig config = BloodModClient.getConfig();

        // Set the blood color for this entity before spawning particles
        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        // Set whether particles should transform to fog underwater
        BloodParticle.setShouldTransformToFog(BloodMod.shouldEntityDripAtLowHealth(entity));

        // Set whether particles should despawn in water (for snow/ice particles)
        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater(entity));

        double posX = entity.getX();
        double posY = entity.getY() + entity.getHeight() * 0.5;
        double posZ = entity.getZ();

        // Scale everything by entity width and config multipliers
        float sizeFactor = entity.getWidth(); // chicken ~0.6, cow ~0.9, etc.
        float intensityMult = config.deathIntensityMultiplier();
        float spreadMult = config.deathSpreadMultiplier();

        int dripCount   = (int)(30 * sizeFactor * intensityMult); // ~18 for chicken, ~27 for cow
        int splashCount = (int)(25 * sizeFactor * intensityMult); // ~15 for chicken, ~22 for cow

        // Play blood drip sound (works underwater too)
        if (!isEntityInWater(entity)) {
            playBloodSound(world, posX, posY, posZ, sizeFactor);
        }

        // --- drips: spread across the full hitbox, fall fast ---
        for (int i = 0; i < dripCount; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * entity.getWidth() * 1.8 * spreadMult;
            double offsetY = (world.random.nextDouble()) * entity.getHeight() * 0.8;
            double offsetZ = (world.random.nextDouble() - 0.5) * entity.getWidth() * 1.8 * spreadMult;

            double velX = (world.random.nextDouble() - 0.5) * 0.4 * spreadMult;
            double velY = -0.5 - world.random.nextDouble() * 1.5;
            double velZ = (world.random.nextDouble() - 0.5) * 0.4 * spreadMult;

            MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_DRIP,
                    posX + offsetX, posY + offsetY, posZ + offsetZ,
                    velX, velY, velZ
            );
        }

        // --- splashes: radial burst outward from the center ---
        for (int i = 0; i < splashCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double radius = (0.2 + world.random.nextDouble() * entity.getWidth() * 1.2) * spreadMult;

            double offsetX = Math.cos(angle) * radius;
            double offsetY = (world.random.nextDouble() - 0.3) * entity.getHeight() * 0.6;
            double offsetZ = Math.sin(angle) * radius;

            // Velocity points outward in the same direction as the offset
            double speed = (0.3 + world.random.nextDouble() * 0.4) * spreadMult;
            double velX = Math.cos(angle) * speed;
            double velY = -0.2 - world.random.nextDouble() * 0.6;
            double velZ = Math.sin(angle) * speed;

            MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_SPLASH,
                    posX + offsetX, posY + offsetY, posZ + offsetZ,
                    velX, velY, velZ
            );
        }
    }

    /**
     * Tick-based low-health drip/splash - called from client tick event.
     * Now works underwater - particles will become fog clouds when they hit water!
     *
     * @param config the config instance to read the threshold from
     */
    public static void spawnBloodForLowHealth(ClientWorld world, LivingEntity entity, BloodModConfig config) {
        float healthPercent = entity.getHealth() / entity.getMaxHealth();
        float threshold = config.lowHealthThreshold();

        if (healthPercent > threshold) {
            return;
        }

        // Set the blood color for this entity before spawning particles
        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        // Set whether particles should transform to fog underwater
        BloodParticle.setShouldTransformToFog(BloodMod.shouldEntityDripAtLowHealth(entity));

        // Set whether particles should despawn in water (for snow/ice particles)
        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater(entity));

        // Derive inner tiers relative to the configured threshold
        float frequentTier = threshold * 0.5f; // default: 0.25 (5 hearts)
        float splashTier   = threshold * 0.3f; // default: 0.15 (3 hearts)

        // Better visibility: 1/10 chance for low health, 1/6 for very low
        // Adjust based on drip frequency config
        int baseChance = healthPercent < frequentTier ? 6 : 10;
        int chance = (int)(baseChance / config.dripFrequencyMultiplier());
        chance = Math.max(1, chance); // Never less than 1

        if (world.random.nextInt(chance) == 0) {
            double posX = entity.getX();
            double posY = entity.getY() + entity.getHeight() * 0.6;
            double posZ = entity.getZ();

            // Play occasional drip sound (1 in 5 chance when particles spawn, not underwater)
            if (world.random.nextInt(5) == 0 && !isEntityInWater(entity)) {
                playBloodSound(world, posX, posY, posZ, 0.3f);
            }

            // Spawn 2-3 drip particles for better visibility, scaled by config
            int baseDripCount = healthPercent < frequentTier ? 3 : 2;
            int dripCount = (int)(baseDripCount * config.dripIntensityMultiplier());

            for (int i = 0; i < dripCount; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * entity.getWidth() * 0.8;
                double offsetY = (world.random.nextDouble() - 0.5) * 0.2;
                double offsetZ = (world.random.nextDouble() - 0.5) * entity.getWidth() * 0.8;

                double velX = (world.random.nextDouble() - 0.5) * 0.1;
                double velY = -1.5 - world.random.nextDouble() * 0.5;
                double velZ = (world.random.nextDouble() - 0.5) * 0.1;

                MinecraftClient.getInstance().particleManager.addParticle(
                        BloodParticles.BLOOD_DRIP,
                        posX + offsetX,
                        posY + offsetY,
                        posZ + offsetZ,
                        velX, velY, velZ
                );
            }

            // Add splash particles when critically low, scaled by config
            if (healthPercent < splashTier) {
                int baseSplashCount = world.random.nextInt(2) + 1; // 1-2 splashes
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
                            -1.2 - world.random.nextDouble() * 0.4,
                            velZ
                    );
                }
            }
        }
    }

    /**
     * Checks if an entity is in water.
     */
    private static boolean isEntityInWater(LivingEntity entity) {
        return entity.isSubmergedInWater() || entity.isTouchingWater();
    }

    /**
     * Plays a blood drip/splash sound effect.
     * Uses vanilla water drip sound with modified pitch for blood effect.
     * Sound is played at the specified position for directional audio.
     *
     * @param sizeFactor scales volume based on entity size
     */
    private static void playBloodSound(ClientWorld world, double x, double y, double z, float sizeFactor) {
        // Check if sound is enabled in config
        BloodModConfig config = BloodModClient.getConfig();
        if (!config.soundEnabled()) {
            return;
        }

        // Get volume and pitch multipliers from config
        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.3f + sizeFactor * 0.2f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.8f + world.random.nextFloat() * 0.3f; // 0.8 - 1.1
        float pitch = basePitch * pitchMult;

        // Play positional sound using sound manager
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

    /**
     * Determines if this entity's particles should despawn when touching water.
     * Snow golems emit snow/ice particles that should melt in water.
     */
    private static boolean shouldParticlesDespawnInWater(LivingEntity entity) {
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType());
        String entityType = id.getPath();

        // Snow golems emit snow particles that should melt in water
        return entityType.equals("snow_golem");
    }
}