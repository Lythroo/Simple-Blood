package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;

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

    private final ClientLevel world;
    private final LivingEntity entity;
    private final float damage;
    private final HitContext ctx;
    private final int durationTicks;
    private int ticksRemaining;
    private boolean soundPlayed;
    private final BloodColor.Color bloodColor;
    private final boolean entityIsUnderwater; // Cache whether entity is underwater at burst start

    public ClientBloodBurstTask(ClientLevel world, LivingEntity entity, float damage) {
        this(world, entity, HitContext.of(entity, damage, null, false, entity.getHealth() + damage));
    }

    public ClientBloodBurstTask(ClientLevel world, LivingEntity entity, HitContext ctx) {
        this.world = world;
        this.entity = entity;
        this.damage = ctx.damage;
        this.ctx = ctx;

        // Get and store the blood color for this entity
        this.bloodColor = BloodColor.getBloodColor(entity);

        // Cache whether entity is underwater - if true, all particles spawn as fog
        this.entityIsUnderwater = entity.isUnderWater() || entity.isInWater();

        // Get config for duration multiplier
        SimpleBloodConfig config = SimpleBloodClient.getConfig();

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
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.isPaused()) {
            return true; // Keep task alive but don't process
        }

        // Entity dead or fully removed - stop immediately
        if (entity.isDeadOrDying() || entity.isRemoved()) return false;

        // Set the blood color before spawning particles each tick
        BloodParticle.setCurrentBloodColor(bloodColor);

        // Per-entity particle size (SimpleBloodAPI)
        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(entity));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(entity));
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(entity));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(entity));

        // Set whether particles should transform to fog underwater
        // Entities without circulatory systems (golems, skeletons, etc.) emit debris, not fog
        BloodParticle.setShouldTransformToFog(shouldEntityCreateFog());

        // Set whether particles should despawn in water (for snow/ice particles)
        BloodParticle.setShouldDespawnInWater(shouldParticlesDespawnInWater());

        // Play sound on first tick of burst (not underwater)
        if (!soundPlayed) {
            BloodSounds.hit(world, entity, Math.min(0.4f + damage * 0.02f, 1.0f), false, entityIsUnderwater);
            soundPlayed = true;
        }

        // Bounding box re-read every tick so it follows knockback
        double bbMinX = entity.getX() - entity.getBbWidth() * 0.5;
        double bbMinY = entity.getY();
        double bbMinZ = entity.getZ() - entity.getBbWidth() * 0.5;
        double bbW    = entity.getBbWidth();
        double bbH    = entity.getBbHeight();

        // Blood comes from the wound, not from all over: a small patch around where the blow
        // landed (following the body as it turns). Falls and explosions hurt everywhere.
        net.minecraft.world.phys.Vec3 wound = entity instanceof BloodEntityAccess a ? a.simpleblood$wound() : null;
        if (ctx.wound == null) wound = null;
        double patch = 0.06 + bbW * 0.12;

        // Get config multipliers
        SimpleBloodConfig config = SimpleBloodClient.getConfig();
        float spreadMult = config.burstSpreadMultiplier();
        float intensityMult = config.burstIntensityMultiplier();

        // Per-entity burst intensity (SimpleBloodAPI)
        intensityMult *= SimpleBlood.getBurstIntensityMultiplier(entity);

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

        // Adjust particle velocity for underwater spawning
        // Underwater particles need less downward velocity since they'll be fog clouds
        float velocityAdjust = entityIsUnderwater ? 0.3f : 1.0f;

        // --- drip particles ---
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

        // --- directional share: exit spray along the blow, a little spatter at the entry ---
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

        // --- splash particles ---
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

    /**
     * Plays sound effect when entity is hit and bleeding.
     * Sound is played at the entity's position for directional audio.
     */
    /** Whether this entity's particles turn into fog clouds underwater (API-aware). */
    private boolean shouldEntityCreateFog() {
        return SimpleBlood.createsFogUnderwater(entity);
    }

    /** Whether this entity's particles vanish on contact with water (API-aware). */
    private boolean shouldParticlesDespawnInWater() {
        return SimpleBlood.particlesDespawnInWater(entity);
    }

    /**
     * Check if a position is in water.
     * More thorough than simple block check - checks the position and adjacent blocks.
     */
    private boolean isPositionInWater(double x, double y, double z) {
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