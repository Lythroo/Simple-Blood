package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;

/**
 * Emits the blood-on-hit burst over 0.3-0.8 seconds, scaled by damage and config.
 * Reads the entity's position fresh every tick so the particles
 * follow it as it moves/gets knocked back.
 * Stops immediately when the entity dies or is removed.
 * Now works underwater - particles will transform to fog when they hit water!
 */
public class ClientBloodBurstTask {

    /** Burst duration range in ticks: weak hit ... strong hit (0.15 s ... 0.8 s). */
    private static final int MIN_TICKS = 3;   // 0.15 s (3 ticks) for very weak hits
    private static final int MAX_TICKS = 16;  // 0.8 s for strong hits
    /** Damage value that maps to MAX_TICKS; anything above is clamped. */
    private static final float DAMAGE_CAP = 20.0f;

    private final ClientWorld world;
    private final LivingEntity entity;
    private final float damage;
    private final int durationTicks;
    private int ticksRemaining;
    private boolean soundPlayed;
    private final BloodColor.Color bloodColor;

    public ClientBloodBurstTask(ClientWorld world, LivingEntity entity, float damage) {
        this.world = world;
        this.entity = entity;
        this.damage = damage;

        // Get and store the blood color for this entity
        this.bloodColor = BloodColor.getBloodColor(entity);

        // Get config for duration multiplier
        BloodModConfig config = BloodModClient.getConfig();

        // For very small damage (< 2 hearts), use even shorter duration
        // Half heart (1 damage): 3 ticks (0.15s)
        // One heart (2 damage): 4-5 ticks (0.2-0.25s)
        // Two hearts (4 damage): 6-7 ticks (0.3-0.35s)
        // Strong hits (10+ damage): 12-16 ticks (0.6-0.8s)
        int calculatedTicks;
        if (damage < 3.0f) {
            // For weak hits (< 1.5 hearts), use very short duration
            calculatedTicks = 3 + (int)(damage * 0.5f); // 3-4 ticks
        } else {
            // For medium-strong hits, scale normally
            float t = Math.min(damage / DAMAGE_CAP, 1.0f);
            calculatedTicks = MIN_TICKS + (int)((MAX_TICKS - MIN_TICKS) * t);
        }

        // Apply config duration multiplier
        this.durationTicks = (int)(calculatedTicks * config.burstDurationMultiplier());
        this.ticksRemaining = durationTicks;
        this.soundPlayed = false;
    }

    /**
     * Returns {@code true} while the burst is still active.
     */
    public boolean tick() {
        if (ticksRemaining <= 0) return false;

        // Skip processing if game is paused (singleplayer only)
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.isPaused()) {
            return true; // Keep task alive but don't process
        }

        // Entity dead or fully removed - stop immediately
        if (entity.isDead() || entity.isRemoved()) return false;

        // Set the blood color before spawning particles each tick
        BloodParticle.setCurrentBloodColor(bloodColor);

        // Set whether particles should transform to fog underwater
        // Entities without circulatory systems (golems, skeletons, etc.) emit debris, not fog
        BloodParticle.setShouldTransformToFog(shouldEntityCreateFog());

        // Set whether particles should despawn in water (for snow/ice particles)
        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater());

        // Play sound on first tick of burst (not underwater)
        if (!soundPlayed) {
            if (!entity.isSubmergedInWater() && !entity.isTouchingWater()) {
                playHitSound();
            }
            soundPlayed = true;
        }

        // Bounding box re-read every tick so it follows knockback
        double bbMinX = entity.getX() - entity.getWidth() * 0.5;
        double bbMinY = entity.getY();
        double bbMinZ = entity.getZ() - entity.getWidth() * 0.5;
        double bbW    = entity.getWidth();
        double bbH    = entity.getHeight();

        // Get config multipliers
        BloodModConfig config = BloodModClient.getConfig();
        float spreadMult = config.burstSpreadMultiplier();
        float intensityMult = config.burstIntensityMultiplier();

        float spreadFactor = Math.min(damage / 10.0f, 2.0f) * spreadMult;

        // Improved scaling: less blood on weak hits, more on strong hits
        // Apply config intensity multiplier
        // Weak hit (1-2 damage): ~1-3 drips, ~1-4 splashes total
        // Medium hit (5 damage): ~6-8 drips, ~9-12 splashes total
        // Strong hit (10+ damage): ~12-15 drips, ~18-23 splashes total
        int totalDrips  = (int)(Math.min(1 + (int)(damage * 1.2f), 15) * intensityMult);
        int totalSplash = (int)(Math.min(1 + (int)(damage * 1.8f), 23) * intensityMult);

        int dripsThisTick  = Math.max(1, totalDrips / durationTicks);
        int splashThisTick = Math.max(1, totalSplash / durationTicks);

        // --- drip particles ---
        for (int i = 0; i < dripsThisTick; i++) {
            double spawnX = bbMinX + world.random.nextDouble() * bbW;
            double spawnY = bbMinY + world.random.nextDouble() * bbH;
            double spawnZ = bbMinZ + world.random.nextDouble() * bbW;

            double velX = (world.random.nextDouble() - 0.5) * 0.2 * spreadFactor;
            double velY = -1.2 - world.random.nextDouble() * 0.8 * spreadFactor;
            double velZ = (world.random.nextDouble() - 0.5) * 0.2 * spreadFactor;

            net.minecraft.client.MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_DRIP,
                    spawnX, spawnY, spawnZ,
                    velX, velY, velZ
            );
        }

        // --- splash particles ---
        for (int i = 0; i < splashThisTick; i++) {
            double spawnX = bbMinX + world.random.nextDouble() * bbW;
            double spawnY = bbMinY + world.random.nextDouble() * bbH;
            double spawnZ = bbMinZ + world.random.nextDouble() * bbW;

            double angle = world.random.nextDouble() * Math.PI * 2;
            double speed = (0.1 + world.random.nextDouble() * 0.15) * spreadFactor;

            double velX = Math.cos(angle) * speed * 0.5;
            double velY = (-0.8 - world.random.nextDouble() * 0.6) * spreadFactor;
            double velZ = Math.sin(angle) * speed * 0.5;

            net.minecraft.client.MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_SPLASH,
                    spawnX, spawnY, spawnZ,
                    velX, velY, velZ
            );
        }

        ticksRemaining--;
        return ticksRemaining > 0;
    }

    /**
     * Plays sound effect when entity is hit and bleeding.
     * Sound is played at the entity's position for directional audio.
     */
    private void playHitSound() {
        // Check if sound is enabled in config
        BloodModConfig config = BloodModClient.getConfig();
        if (!config.soundEnabled()) {
            return;
        }

        // Get volume and pitch multipliers from config
        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.4f + damage * 0.02f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.9f + world.random.nextFloat() * 0.2f;
        float pitch = basePitch * pitchMult;

        // Play positional sound using sound manager
        net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(
                new net.minecraft.client.sound.PositionedSoundInstance(
                        SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_LAVA,
                        net.minecraft.sound.SoundCategory.PLAYERS,
                        volume,
                        pitch,
                        net.minecraft.util.math.random.Random.create(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ()
                )
        );
    }

    /**
     * Determines if this entity should create fog clouds underwater.
     * Entities that emit debris (golems, skeletons) should not create fog.
     */
    private boolean shouldEntityCreateFog() {
        // Use the same logic as low health drip - entities without circulatory systems
        // shouldn't create fog clouds (they emit debris/particles instead)
        return BloodMod.shouldEntityDripAtLowHealth(entity);
    }

    /**
     * Determines if this entity's particles should despawn when touching water.
     * Snow golems emit snow/ice particles that should melt in water.
     */
    private boolean shouldParticlesDespawnInWater() {
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType());
        String entityType = id.getPath();

        // Snow golems emit snow particles that should melt in water
        return entityType.equals("snow_golem");
    }
}