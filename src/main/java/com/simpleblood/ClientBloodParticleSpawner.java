package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

public class ClientBloodParticleSpawner {

    /**
     * One-shot death burst. Spawns a large number of drips and splashes
     * centered on the entity at the moment it dies. Counts and spread
     * scale with the entity's width so bigger mobs bleed more.
     * Now works underwater too - particles will transform to fog when they hit water!
     */
    /**
     * Spawns one blood particle for the current batch (colour, size, fog flags already set on
     * {@link BloodParticle}), turning it into fog when the spot is under water.
     */
    public static void emit(ClientLevel world, LivingEntity entity, boolean splash,
                            double x, double y, double z, double vx, double vy, double vz) {
        emit(world, splash ? BloodParticles.BLOOD_SPLASH : BloodParticles.BLOOD_DRIP, x, y, z, vx, vy, vz);
    }

    /** Same, for an explicit particle type (drip, splash, streak). */
    public static void emit(ClientLevel world, net.minecraft.core.particles.SimpleParticleType type,
                            double x, double y, double z, double vx, double vy, double vz) {
        if (!BloodParticle.underBudget()) return; // the world is bloody enough already
        if (!Guard.ok(Guard.Part.PARTICLES)) return; // switched off after an error
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

    /**
     * Mobs with nothing to bleed: each drop becomes a piece of what they are made of.
     * <ul>
     *   <li>Bone: splashes and sweeping streaks mostly puff into dust, drips mostly break off
     *       as chips, so a hit throws a little cloud with chips flying out of it.</li>
     *   <li>Metal: every drop is a flake.</li>
     *   <li>Wood: mostly splinters; about a third stay drops, of resin, which pool like blood.</li>
     *   <li>Spirit: every drop is a spark.</li>
     *   <li>Wind: every other drop is a gust (they are big).</li>
     *   <li>Ember: most drops are embers; one in five is a wisp of vanilla smoke instead.</li>
     * </ul>
     */
    private static void emitPiece(ClientLevel world, BloodKind kind, net.minecraft.core.particles.SimpleParticleType type,
                                  double x, double y, double z, double vx, double vy, double vz) {
        // The Kinds settings scale how many pieces a drop makes: fewer, or a few more, each
        // extra one flying a little differently so they do not sit on top of each other.
        net.minecraft.util.RandomSource rng = world.getRandom();
        float amount = SimpleBloodClient.getConfig().kinds.amount(kind);
        int pieces = (int) amount + (rng.nextFloat() < amount - (int) amount ? 1 : 0);
        for (int i = 0; i < pieces; i++) {
            double f = i == 0 ? 1.0 : 0.8 + rng.nextDouble() * 0.4;
            emitOnePiece(world, kind, type, x, y, z, vx * f, vy * f, vz * f);
        }
    }

    private static void emitOnePiece(ClientLevel world, BloodKind kind, net.minecraft.core.particles.SimpleParticleType type,
                                     double x, double y, double z, double vx, double vy, double vz) {
        SimpleBloodConfig.KindSettings kinds = SimpleBloodClient.getConfig().kinds;
        net.minecraft.util.RandomSource rng = world.getRandom();
        net.minecraft.core.particles.SimpleParticleType piece;
        switch (kind) {
            case METAL -> piece = BloodParticles.METAL_FLAKE;
            case SPIRIT -> piece = BloodParticles.SPIRIT_SPARK;
            case EMBER -> {
                if (kinds.emberSmoke && rng.nextFloat() < 0.2f) {
                    // Smoke drifts up from the wound rather than flying with the embers.
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
                if (rng.nextFloat() < kinds.resinShare / 100.0f) {
                    // Resin: a real drop, painted and pooled like blood.
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

    /** Drops of blood under water that gather into one cloud. */
    private static final int FOG_MERGE = 4;
    /** That cloud's size against a single drop's: about the same amount of blood, in one place. */
    private static final float FOG_MERGE_SCALE = 1.8f;
    private static int fogCount;

    /**
     * A cloud for blood in water (colour and glow already set on the fog particle). Under water
     * a wound gives off a few big clouds rather than a little one per drop: only every
     * {@link #FOG_MERGE}th drop makes a cloud, and that one is bigger. Render thread only.
     */
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
        // If Physics Mod turns the body into a ragdoll, the ragdoll bleeds on with this blood.
        com.simpleblood.compat.PhysicsModRagdolls.entityDied(entity);
        // Get config for multipliers
        SimpleBloodConfig config = SimpleBloodClient.getConfig();

        // Set the blood color for this entity before spawning particles
        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        // Per-entity particle size (SimpleBloodAPI)
        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(entity));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(entity));
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(entity));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(entity));

        // Set whether particles should transform to fog underwater (API-aware)
        boolean canTransform = SimpleBlood.createsFogUnderwater(entity);
        BloodParticle.setShouldTransformToFog(canTransform);

        // Set whether particles should despawn in water (for snow/ice particles)
        BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(entity));

        double posX = entity.getX();
        double posY = entity.getY() + entity.getBbHeight() * 0.5;
        double posZ = entity.getZ();

        // Check if entity is underwater - affects particle velocity
        boolean entityIsUnderwater = isEntityInWater(entity);

        // Scale everything by entity width and config multipliers (+ per-entity API multiplier)
        float sizeFactor = entity.getBbWidth(); // chicken ~0.6, cow ~0.9, etc.
        float intensityMult = config.deathIntensityMultiplier() * SimpleBlood.getBurstIntensityMultiplier(entity);
        float spreadMult = config.deathSpreadMultiplier();

        int dripCount   = (int)(30 * sizeFactor * intensityMult); // ~18 for chicken, ~27 for cow
        int splashCount = (int)(25 * sizeFactor * intensityMult); // ~15 for chicken, ~22 for cow

        // The body's last sound, in the voice of what it is made of (bubbles under water).
        BloodSounds.hit(world, entity, Math.min(0.3f + sizeFactor * 0.2f, 1.0f), true, entityIsUnderwater);

        // Adjust velocity for underwater particles
        float velocityAdjust = entityIsUnderwater ? 0.3f : 1.0f;

        // --- drips: spread across the full hitbox, fall fast ---
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

        // --- directional share: the killing blow carries blood out the far side ---
        if (ctx != null && ctx.weapon == HitContext.Weapon.EXPLOSION) {
            BloodSpray.radialBurst(world, ctx, splashCount, velocityAdjust);
            splashCount = 0;
        } else if (BloodSpray.isDirectional(ctx)) {
            int directional = Math.round(splashCount * config.directionalShare());
            BloodSpray.exitSpray(world, ctx, directional, velocityAdjust, true);
            if (config.entrySpatterEnabled()) BloodSpray.entrySpatter(world, ctx, Math.max(1, directional / 6), velocityAdjust);
            splashCount -= directional;
        }

        // --- splashes: radial burst outward from the center ---
        for (int i = 0; i < splashCount; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double radius = (0.2 + world.getRandom().nextDouble() * entity.getBbWidth() * 1.2) * spreadMult;

            double offsetX = Math.cos(angle) * radius;
            double offsetY = (world.getRandom().nextDouble() - 0.3) * entity.getBbHeight() * 0.6;
            double offsetZ = Math.sin(angle) * radius;

            // Velocity points outward in the same direction as the offset
            double speed = (0.3 + world.getRandom().nextDouble() * 0.4) * spreadMult;
            double velX = Math.cos(angle) * speed;
            double velY = (-0.2 - world.getRandom().nextDouble() * 0.6) * velocityAdjust;
            double velZ = Math.sin(angle) * speed;

            double spawnX = posX + offsetX;
            double spawnY = posY + offsetY;
            double spawnZ = posZ + offsetZ;

            emit(world, BloodParticles.BLOOD_SPLASH, spawnX, spawnY, spawnZ, velX, velY, velZ);
        }

        // Chunks of meat, if switched on (last: it resets the spawn settings).
        Giblets.death(world, entity, ctx);
        com.simpleblood.mob.MobBlood.death(entity, ctx);
    }

    /**
     * Tick-based low-health drip/splash - called from client tick event.
     * Now works underwater - particles will become fog clouds when they hit water!
     *
     * @param config the config instance to read the threshold from
     */
    public static void spawnBloodForLowHealth(ClientLevel world, LivingEntity entity, SimpleBloodConfig config) {
        float healthPercent = entity.getHealth() / entity.getMaxHealth();
        float threshold = config.lowHealthThreshold();

        if (healthPercent > threshold) {
            return;
        }

        // Set the blood color for this entity before spawning particles
        BloodColor.Color bloodColor = BloodColor.getBloodColor(entity);
        BloodParticle.setCurrentBloodColor(bloodColor);

        // Per-entity particle size (SimpleBloodAPI)
        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(entity));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(entity));
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(entity));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(entity));

        // Set whether particles should transform to fog underwater (API-aware)
        boolean canTransform = SimpleBlood.createsFogUnderwater(entity);
        BloodParticle.setShouldTransformToFog(canTransform);

        // Set whether particles should despawn in water (for snow/ice particles)
        BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(entity));

        // Derive inner tiers relative to the configured threshold
        float frequentTier = threshold * 0.5f; // default: 0.25 (5 hearts)
        float splashTier   = threshold * 0.3f; // default: 0.15 (3 hearts)

        // Better visibility: 1/10 chance for low health, 1/6 for very low
        // Adjust based on drip frequency config
        int baseChance = healthPercent < frequentTier ? 6 : 10;
        int chance = (int)(baseChance / config.dripFrequencyMultiplier());
        chance = Math.max(1, chance); // Never less than 1

        if (world.getRandom().nextInt(chance) == 0) {
            double posX = entity.getX();
            double posY = entity.getY() + entity.getBbHeight() * 0.6;
            double posZ = entity.getZ();
            // A wounded mob keeps bleeding from the wound, wherever the body has turned to.
            net.minecraft.world.phys.Vec3 wound = entity instanceof BloodEntityAccess a ? a.simpleblood$wound() : null;
            double spreadX = entity.getBbWidth() * 0.8, spreadZ = spreadX;
            if (wound != null) {
                posX = wound.x;
                posY = wound.y;
                posZ = wound.z;
                spreadX = spreadZ = 0.08 + entity.getBbWidth() * 0.1;
            }

            // Check if entity is underwater
            boolean entityIsUnderwater = isEntityInWater(entity);

            // Play occasional drip sound (1 in 5 chance when particles spawn, not underwater)
            if (world.getRandom().nextInt(5) == 0 && !entityIsUnderwater) {
                playBloodSound(world, entity, posX, posY, posZ, 0.3f);
            }

            // Adjust velocity for underwater particles
            float velocityAdjust = entityIsUnderwater ? 0.4f : 1.0f;

            // Spawn 2-3 drip particles for better visibility, scaled by config (+ per-entity API)
            int baseDripCount = healthPercent < frequentTier ? 3 : 2;
            int dripCount = (int)(baseDripCount * config.dripIntensityMultiplier()
                    * SimpleBlood.getDripIntensityMultiplier(entity));

            // With blood on mobs on, the wound bleeds onto the body instead: it runs down and the
            // drops fall from where it runs off, not from somewhere inside the mob.
            if (com.simpleblood.mob.MobBlood.seep(entity, Math.max(1, dripCount))) return;

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

            // Add splash particles when critically low, scaled by config (+ per-entity API)
            if (healthPercent < splashTier) {
                int baseSplashCount = world.getRandom().nextInt(2) + 1; // 1-2 splashes
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

    /**
     * Checks if an entity is in water.
     */
    private static boolean isEntityInWater(LivingEntity entity) {
        return entity.isUnderWater() || entity.isInWater();
    }

    /**
     * Plays a blood drip/splash sound effect.
     * Uses vanilla water drip sound with modified pitch for blood effect.
     * Sound is played at the specified position for directional audio.
     *
     * @param sizeFactor scales volume based on entity size
     */
    private static void playBloodSound(ClientLevel world, LivingEntity entity, double x, double y, double z, float sizeFactor) {
        // Sound must be enabled globally AND for this entity (SimpleBloodAPI).
        if (!SimpleBlood.isSoundEnabledFor(entity)) {
            return;
        }
        SimpleBloodConfig config = SimpleBloodClient.getConfig();

        // Get volume and pitch multipliers from config
        float volumeMult = config.soundVolumeMultiplier();
        float pitchMult = config.soundPitchMultiplier();

        float baseVolume = Math.min(0.3f + sizeFactor * 0.2f, 1.0f);
        float volume = baseVolume * volumeMult;

        float basePitch = 0.8f + world.getRandom().nextFloat() * 0.3f; // 0.8 - 1.1
        float pitch = basePitch * pitchMult;

        // Play positional sound using sound manager (custom per-entity sound via API)
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

    /**
     * Check if a position is in water.
     * More thorough than simple block check - checks the position and adjacent blocks.
     */
    private static boolean isPositionInWater(ClientLevel world, double x, double y, double z) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);

        if (state.is(net.minecraft.world.level.block.Blocks.WATER)) {
            return true;
        }

        // Check fractional position - if close to edge, check adjacent block
        double fracX = x - pos.getX();
        double fracY = y - pos.getY();
        double fracZ = z - pos.getZ();

        // Check adjacent blocks if near edge (within 0.2 blocks)
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