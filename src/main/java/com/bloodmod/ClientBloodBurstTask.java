package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;

public class ClientBloodBurstTask {

    private static final int MIN_TICKS = 3;
    private static final int MAX_TICKS = 16;
    private static final float DAMAGE_CAP = 20.0f;

    private final ClientLevel world;
    private final LivingEntity entity;
    private final float damage;
    private final HitContext ctx;
    private final int durationTicks;
    private int ticksRemaining;
    private boolean soundPlayed;
    private final BloodColor.Color bloodColor;
    private final boolean entityIsUnderwater;

    public ClientBloodBurstTask(ClientLevel world, LivingEntity entity, float damage) {
        this(world, entity, HitContext.of(entity, damage, null, false, entity.getHealth() + damage));
    }

    public ClientBloodBurstTask(ClientLevel world, LivingEntity entity, HitContext ctx) {
        this.world = world;
        this.entity = entity;
        this.damage = ctx.damage;
        this.ctx = ctx;

        this.bloodColor = BloodColor.getBloodColor(entity);

        this.entityIsUnderwater = entity.isUnderWater() || entity.isInWater();

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

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.isPaused()) {
            return true;
        }

        if (entity.isDeadOrDying() || entity.isRemoved()) return false;

        BloodParticle.setCurrentBloodColor(bloodColor);

        BloodParticle.setEntitySizeMultiplier(BloodMod.getParticleSizeMultiplier(entity));
        BloodParticle.setCurrentKind(BloodMod.bloodKindOf(entity));
        BloodParticle.setPaintsSurfaces(BloodMod.shouldEntityTransformToStains(entity));

        BloodParticle.setShouldTransformToFog(shouldEntityCreateFog());

        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater());

        if (!soundPlayed) {
            if (!entityIsUnderwater) {
                playHitSound();
            }
            soundPlayed = true;
        }

        double bbMinX = entity.getX() - entity.getBbWidth() * 0.5;
        double bbMinY = entity.getY();
        double bbMinZ = entity.getZ() - entity.getBbWidth() * 0.5;
        double bbW    = entity.getBbWidth();
        double bbH    = entity.getBbHeight();

        net.minecraft.world.phys.Vec3 wound = entity instanceof BloodEntityAccess a ? a.bloodmod$wound() : null;
        if (ctx.wound == null) wound = null;
        double patch = 0.06 + bbW * 0.12;

        BloodModConfig config = BloodModClient.getConfig();
        float spreadMult = config.burstSpreadMultiplier();
        float intensityMult = config.burstIntensityMultiplier();

        intensityMult *= BloodMod.getBurstIntensityMultiplier(entity);

        float spreadFactor = Math.min(damage / 10.0f, 2.0f) * spreadMult;

        int totalDrips  = (int)(Math.min(1 + (int)(damage * 1.2f), 15) * intensityMult);
        int totalSplash = (int)(Math.min(1 + (int)(damage * 1.8f), 23) * intensityMult);

        int dripsThisTick  = Math.max(1, totalDrips / durationTicks);
        int splashThisTick = Math.max(1, totalSplash / durationTicks);

        float velocityAdjust = entityIsUnderwater ? 0.3f : 1.0f;

        for (int i = 0; i < dripsThisTick; i++) {
            double spawnX, spawnY, spawnZ;
            if (wound != null) {
                spawnX = wound.x + (world.getRandom().nextDouble() - 0.5) * 2 * patch;
                spawnY = wound.y + (world.getRandom().nextDouble() - 0.5) * 2 * patch;
                spawnZ = wound.z + (world.getRandom().nextDouble() - 0.5) * 2 * patch;
            } else {
                spawnX = bbMinX + world.getRandom().nextDouble() * bbW;
                spawnY = bbMinY + world.getRandom().nextDouble() * bbH;
                spawnZ = bbMinZ + world.getRandom().nextDouble() * bbW;
            }

            double velX = (world.getRandom().nextDouble() - 0.5) * 0.2 * spreadFactor;
            double velY = (-1.2 - world.getRandom().nextDouble() * 0.8 * spreadFactor) * velocityAdjust;
            double velZ = (world.getRandom().nextDouble() - 0.5) * 0.2 * spreadFactor;

            ClientBloodParticleSpawner.emit(world, BloodParticles.BLOOD_DRIP, spawnX, spawnY, spawnZ, velX, velY, velZ);
        }

        if (ctx.weapon == HitContext.Weapon.EXPLOSION || ctx.weapon == HitContext.Weapon.FALL) {
            BloodSpray.radialBurst(world, ctx, splashThisTick, velocityAdjust);
            splashThisTick = 0;
        } else if (BloodSpray.isDirectional(ctx)) {
            int directional = Math.round(splashThisTick * config.directionalShare());
            BloodSpray.exitSpray(world, ctx, directional, velocityAdjust, ticksRemaining == durationTicks);
            if (config.entrySpatterEnabled() && ticksRemaining == durationTicks) {
                BloodSpray.entrySpatter(world, ctx, 2 + (int) (ctx.severity * 3), velocityAdjust);
            }
            splashThisTick -= directional;
        }

        for (int i = 0; i < splashThisTick; i++) {
            double spawnX, spawnY, spawnZ;
            if (wound != null) {
                spawnX = wound.x + (world.getRandom().nextDouble() - 0.5) * 2 * patch;
                spawnY = wound.y + (world.getRandom().nextDouble() - 0.5) * 2 * patch;
                spawnZ = wound.z + (world.getRandom().nextDouble() - 0.5) * 2 * patch;
            } else {
                spawnX = bbMinX + world.getRandom().nextDouble() * bbW;
                spawnY = bbMinY + world.getRandom().nextDouble() * bbH;
                spawnZ = bbMinZ + world.getRandom().nextDouble() * bbW;
            }

            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double speed = (0.1 + world.getRandom().nextDouble() * 0.15) * spreadFactor;

            double velX = Math.cos(angle) * speed * 0.5;
            double velY = ((-0.8 - world.getRandom().nextDouble() * 0.6) * spreadFactor) * velocityAdjust;
            double velZ = Math.sin(angle) * speed * 0.5;

            ClientBloodParticleSpawner.emit(world, BloodParticles.BLOOD_SPLASH, spawnX, spawnY, spawnZ, velX, velY, velZ);
        }

        ticksRemaining--;
        return ticksRemaining > 0;
    }

    private void playHitSound() {
        BloodModConfig config = BloodModClient.getConfig();
        if (!BloodMod.isSoundEnabledFor(entity)) {
            return;
        }

        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.4f + damage * 0.02f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.9f + world.getRandom().nextFloat() * 0.2f;
        float pitch = basePitch * pitchMult;

        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        BloodMod.getBloodSound(entity),
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        volume,
                        pitch,
                        net.minecraft.util.RandomSource.create(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ()
                )
        );
    }

    private boolean shouldEntityCreateFog() {
        return BloodMod.createsFogUnderwater(entity);
    }

    private boolean shouldParticlesDespawnInWater() {
        return BloodMod.particlesDespawnInWater(entity);
    }

    private boolean isPositionInWater(double x, double y, double z) {
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
