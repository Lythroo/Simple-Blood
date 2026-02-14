package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;

public class ClientBloodBurstTask {

    private static final int MIN_TICKS = 3;   

    private static final int MAX_TICKS = 16;  

    private static final float DAMAGE_CAP = 20.0f;

    private final ClientWorld world;
    private final LivingEntity entity;
    private final float damage;
    private final int durationTicks;
    private int ticksRemaining;
    private boolean soundPlayed;
    private final BloodColor.Color bloodColor;
    private final boolean entityIsUnderwater; 

    public ClientBloodBurstTask(ClientWorld world, LivingEntity entity, float damage) {
        this.world = world;
        this.entity = entity;
        this.damage = damage;

        this.bloodColor = BloodColor.getBloodColor(entity);

        this.entityIsUnderwater = entity.isSubmergedInWater() || entity.isTouchingWater();

        BloodModConfig config = BloodModClient.getConfig();

        int calculatedTicks;
        if (damage < 3.0f) {

            calculatedTicks = 3 + (int)(damage * 0.5f); 

        } else {

            float t = Math.min(damage / DAMAGE_CAP, 1.0f);
            calculatedTicks = MIN_TICKS + (int)((MAX_TICKS - MIN_TICKS) * t);
        }

        this.durationTicks = (int)(calculatedTicks * config.burstDurationMultiplier());
        this.ticksRemaining = durationTicks;
        this.soundPlayed = false;
    }

    public boolean tick() {
        if (ticksRemaining <= 0) return false;

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.isPaused()) {
            return true; 

        }

        if (entity.isDead() || entity.isRemoved()) return false;

        BloodParticle.setCurrentBloodColor(bloodColor);

        BloodParticle.setShouldTransformToFog(shouldEntityCreateFog());

        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater());

        if (!soundPlayed) {
            if (!entityIsUnderwater) {
                playHitSound();
            }
            soundPlayed = true;
        }

        double bbMinX = entity.getX() - entity.getWidth() * 0.5;
        double bbMinY = entity.getY();
        double bbMinZ = entity.getZ() - entity.getWidth() * 0.5;
        double bbW    = entity.getWidth();
        double bbH    = entity.getHeight();

        BloodModConfig config = BloodModClient.getConfig();
        float spreadMult = config.burstSpreadMultiplier();
        float intensityMult = config.burstIntensityMultiplier();

        float spreadFactor = Math.min(damage / 10.0f, 2.0f) * spreadMult;

        int totalDrips  = (int)(Math.min(1 + (int)(damage * 1.2f), 15) * intensityMult);
        int totalSplash = (int)(Math.min(1 + (int)(damage * 1.8f), 23) * intensityMult);

        int dripsThisTick  = Math.max(1, totalDrips / durationTicks);
        int splashThisTick = Math.max(1, totalSplash / durationTicks);

        float velocityAdjust = entityIsUnderwater ? 0.3f : 1.0f;

        for (int i = 0; i < dripsThisTick; i++) {
            double spawnX = bbMinX + world.random.nextDouble() * bbW;
            double spawnY = bbMinY + world.random.nextDouble() * bbH;
            double spawnZ = bbMinZ + world.random.nextDouble() * bbW;

            double velX = (world.random.nextDouble() - 0.5) * 0.2 * spreadFactor;
            double velY = (-1.2 - world.random.nextDouble() * 0.8 * spreadFactor) * velocityAdjust;
            double velZ = (world.random.nextDouble() - 0.5) * 0.2 * spreadFactor;

            net.minecraft.client.MinecraftClient.getInstance().particleManager.addParticle(
                    BloodParticles.BLOOD_DRIP,
                    spawnX, spawnY, spawnZ,
                    velX, velY, velZ
            );
        }

        for (int i = 0; i < splashThisTick; i++) {
            double spawnX = bbMinX + world.random.nextDouble() * bbW;
            double spawnY = bbMinY + world.random.nextDouble() * bbH;
            double spawnZ = bbMinZ + world.random.nextDouble() * bbW;

            double angle = world.random.nextDouble() * Math.PI * 2;
            double speed = (0.1 + world.random.nextDouble() * 0.15) * spreadFactor;

            double velX = Math.cos(angle) * speed * 0.5;
            double velY = ((-0.8 - world.random.nextDouble() * 0.6) * spreadFactor) * velocityAdjust;
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

    private void playHitSound() {

        BloodModConfig config = BloodModClient.getConfig();
        if (!config.soundEnabled()) {
            return;
        }

        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.4f + damage * 0.02f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.9f + world.random.nextFloat() * 0.2f;
        float pitch = basePitch * pitchMult;

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

    private boolean shouldEntityCreateFog() {

        return BloodMod.shouldEntityTransformToStains(entity) && BloodMod.shouldEntityDripAtLowHealth(entity);
    }

    private boolean shouldParticlesDespawnInWater() {
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType());
        String entityType = id.getPath();

        return entityType.equals("snow_golem");
    }
}