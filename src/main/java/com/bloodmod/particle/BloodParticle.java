package com.bloodmod.particle;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodParticles;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public class BloodParticle extends BillboardParticle {

    // Thread-local storage for current entity's blood color
    // This allows the Factory to know what color to use when creating particles
    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f)); // Default red

    // Thread-local storage for whether current entity should transform to fog underwater
    private static final ThreadLocal<Boolean> shouldTransformToFog =
            ThreadLocal.withInitial(() -> true); // Default true

    // Thread-local storage for whether particles should despawn when touching water
    // Used for snow/ice particles that should melt on contact with water
    private static final ThreadLocal<Boolean> shouldDespawnInWater =
            ThreadLocal.withInitial(() -> false); // Default false

    /**
     * Set the blood color for the next batch of particles to be created.
     * Must be called before spawning particles.
     */
    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    /**
     * Set whether particles should transform to fog when hitting water.
     * Set to false for entities that emit debris/particles instead of blood clouds.
     */
    public static void setShouldTransformToFog(boolean shouldTransform) {
        shouldTransformToFog.set(shouldTransform);
    }

    /**
     * Set whether particles should despawn when touching water.
     * Set to true for snow/ice particles that should melt in water.
     */
    public static void setShouldDespawnInWater(boolean shouldDespawn) {
        shouldDespawnInWater.set(shouldDespawn);
    }

    // ---------------------------------------------------------------
    // Tuning Constants (base values, multiplied by config)
    // ---------------------------------------------------------------
    private static final float BASE_GRAVITY   = 0.04f;   // blocks / tick²
    private static final float BASE_DRAG      = 0.98f;   // per-tick velocity multiplier
    private static final int   BASE_LIFE = 40;      // ticks (2 s)
    private static final int   JITTER    = 10;      // ± ticks

    // Base particle size range (will be multiplied by config)
    private static final float MIN_SCALE = 0.08f;
    private static final float MAX_SCALE = 0.16f;

    // Fog particle constants (base values, multiplied by config)
    private static final float FOG_MIN_SCALE = 1.2f;
    private static final float FOG_MAX_SCALE = 2.4f;
    private static final int FOG_BASE_LIFE = 60;

    // ---------------------------------------------------------------
    // Instance Variables
    // ---------------------------------------------------------------
    private boolean isFog = false;
    private boolean isOnGround = false;
    private final float baseRed, baseGreen, baseBlue;
    private final boolean canTransformToFog;
    private final boolean shouldMeltInWater; // Whether this particle despawns when touching water
    private float targetScale; // For smooth expansion

    // ---------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------
    protected BloodParticle(ClientWorld world,
                            double x, double y, double z,
                            double velX, double velY, double velZ,
                            Sprite sprite,
                            float sizeMultiplier,
                            float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite);

        // Get config for lifetime multiplier
        float lifetimeMultiplier = BloodModClient.getConfig().particleLifetimeMultiplier();

        // Jittered lifetime with config multiplier
        int baseLifetime = BASE_LIFE + (int)(world.random.nextFloat() * JITTER * 2) - JITTER;
        this.maxAge = (int)(baseLifetime * lifetimeMultiplier);

        // Store base colors for fog transition
        this.baseRed = red;
        this.baseGreen = green;
        this.baseBlue = blue;

        // Store whether this particle can transform to fog (from ThreadLocal)
        this.canTransformToFog = shouldTransformToFog.get();

        // Store whether this particle should despawn in water (from ThreadLocal)
        this.shouldMeltInWater = shouldDespawnInWater.get();

        // Set color from parameters
        this.setColor(red, green, blue);

        this.alpha = 1.0f;

        // Particle size with config multiplier
        float baseScale = MIN_SCALE + world.random.nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.scale = baseScale * sizeMultiplier;
        this.targetScale = this.scale; // Start with no expansion
    }

    // ---------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------
    @Override
    public void tick() {
        super.tick(); // position += velocity, age++

        if (!isFog) {
            // Regular blood particle behavior

            // Check if particle is in water
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            BlockState state = world.getBlockState(pos);
            boolean inWater = state.isOf(Blocks.WATER);

            // Snow/ice particles should despawn (melt) when touching water
            if (inWater && shouldMeltInWater) {
                this.markDead();
                return;
            }

            if (inWater && canTransformToFog && BloodModClient.getConfig().underwaterFogEnabled()) {
                // Transform into fog!
                transformToFog();
            } else {
                // Get config multipliers
                float gravityMult = BloodModClient.getConfig().particleGravityMultiplier();
                float dragMult = BloodModClient.getConfig().particleDragMultiplier();

                // Apply gravity with config multiplier
                velocityY -= BASE_GRAVITY * gravityMult;

                // Apply drag - use much stronger water drag if in water and not transforming to fog
                float dragMultiplier = BASE_DRAG * dragMult; // Default air drag

                if (inWater && !canTransformToFog) {
                    // Particles that don't transform to fog (like bone dust, debris) slow down MUCH more in water
                    // This simulates heavy debris sinking slowly through water
                    dragMultiplier = 0.70f; // Very strong drag underwater
                }

                velocityX *= dragMultiplier;
                velocityY *= dragMultiplier;
                velocityZ *= dragMultiplier;

                // Check if particle is on the ground (collided with a block below)
                if (!isOnGround && onGround) {
                    isOnGround = true;

                    // Check if blood stains are enabled
                    boolean stainsEnabled = BloodModClient.getConfig().bloodStainsEnabled();

                    // Check if this particle type actually spawns stains
                    // Debris particles (shouldMeltInWater or !canTransformToFog) don't spawn stains
                    boolean willSpawnStain = stainsEnabled && !shouldMeltInWater && canTransformToFog;

                    // Spawn blood stain on the ground (if applicable)
                    spawnBloodStain();

                    if (willSpawnStain) {
                        // Stains are enabled AND this particle actually spawned a stain
                        // Disappear instantly since the stain replaces this particle
                        this.markDead();
                        return;
                    } else {
                        // Either stains are disabled OR this is a debris particle that doesn't spawn stains
                        // Keep the old behavior (expand and fade)
                        // Expand to 2-2.5x size when hitting ground (splat effect)
                        targetScale = this.scale * (1.0f + world.random.nextFloat() * 0.2f);
                        // Stop moving
                        velocityX = 0;
                        velocityY = 0;
                        velocityZ = 0;
                    }
                }

                // Check if ground beneath particle was removed (every 10 ticks for performance)
                // Only needed when stains are disabled (otherwise particle is already dead)
                if (isOnGround && age % 10 == 0) {
                    BlockPos posBelow = BlockPos.ofFloored(x, y - 0.1, z);
                    boolean hasBlockBelow = !world.getBlockState(posBelow).isAir();

                    if (!hasBlockBelow) {
                        // Block was removed - start falling again
                        isOnGround = false;
                        // Add small downward velocity to start falling
                        velocityY = -0.1;
                    }
                }

                // Smoothly expand towards target scale when on ground
                // Only needed when stains are disabled (otherwise particle is already dead)
                if (isOnGround && this.scale < targetScale) {
                    this.scale += (targetScale - this.scale) * 0.15f; // 15% per tick = quick expansion
                }

                // Fade out over the last 25% of lifetime
                float lifeFraction = 1.0f - (float) age / maxAge;
                if (lifeFraction < 0.25f) {
                    this.alpha = lifeFraction / 0.25f;
                }
            }
        } else {
            // Fog behavior
            tickFog();
        }
    }

    /**
     * Transform blood particle into underwater fog cloud
     */
    private void transformToFog() {
        isFog = true;

        // Get config multipliers
        float fogLifetimeMult = BloodModClient.getConfig().fogLifetimeMultiplier();

        // Reset age for fog lifetime with config multiplier
        this.age = 0;
        int baseFogLife = FOG_BASE_LIFE + (int)(world.random.nextFloat() * 20) - 10;
        this.maxAge = (int)(baseFogLife * fogLifetimeMult);

        // Start at current size - will expand gradually

        // Make color more desaturated/cloudy
        float desaturation = 0.6f;
        this.setColor(
                baseRed * desaturation + 0.4f * 0.4f,
                baseGreen * desaturation + 0.4f * 0.4f,
                baseBlue * desaturation + 0.4f * 0.4f
        );

        // Slow down velocity dramatically
        this.velocityX *= 0.1f;
        this.velocityY *= 0.1f;
        this.velocityZ *= 0.1f;

        // Start semi-transparent with config multiplier
        float fogOpacityMult = BloodModClient.getConfig().fogOpacityMultiplier();
        this.alpha = 0.5f * fogOpacityMult;
    }

    /**
     * Tick behavior for fog particles underwater
     */
    private void tickFog() {
        // Very slow drift
        velocityX *= 0.92f;
        velocityY *= 0.92f;
        velocityZ *= 0.92f;

        // Gentle downward sinking (blood is heavier than water)
        velocityY -= 0.002f;

        // Random wobble for natural movement
        velocityX += (world.random.nextFloat() - 0.5f) * 0.001f;
        velocityZ += (world.random.nextFloat() - 0.5f) * 0.001f;

        // Fade out over lifetime with config opacity multiplier
        float lifeFraction = 1.0f - (float) age / maxAge;
        float fogOpacityMult = BloodModClient.getConfig().fogOpacityMultiplier();
        this.alpha = lifeFraction * 0.6f * fogOpacityMult;

        // Smoothly expand over time
        // Target size based on config multipliers
        float fogSizeMult = BloodModClient.getConfig().fogSizeMultiplier();
        float particleSizeMult = BloodModClient.getConfig().particleSizeMultiplier();
        float targetScale = (FOG_MIN_SCALE + world.random.nextFloat() * (FOG_MAX_SCALE - FOG_MIN_SCALE))
                * particleSizeMult * fogSizeMult;

        // Gradually grow towards target size (1% per tick = smooth expansion)
        if (this.scale < targetScale) {
            this.scale += (targetScale - this.scale) * 0.01f;
        }
    }

    /**
     * Spawns a blood stain particle at the current position when particle hits ground.
     * Stains are flat particles that stick to the ground and fade over time.
     */
    private void spawnBloodStain() {
        // Check if blood stains are enabled in config
        if (!BloodModClient.getConfig().bloodStainsEnabled()) {
            return;
        }

        // Don't spawn stains for fog particles, particles that melt in water, or debris particles
        // Debris particles (iron golem parts, skeleton bones, etc.) don't leave liquid stains
        if (isFog || shouldMeltInWater || !canTransformToFog) {
            return;
        }

        // Set the blood color for the stain
        com.bloodmod.particle.BloodStainParticle.setCurrentBloodColor(
                new BloodColor.Color(baseRed, baseGreen, baseBlue)
        );

        // Spawn stain slightly below the ground surface for better visibility
        net.minecraft.client.MinecraftClient.getInstance().particleManager.addParticle(
                BloodParticles.BLOOD_STAIN,
                x, y + 0.01, z,  // Slightly above to prevent z-fighting
                0, 0, 0  // No velocity
        );
    }

    // ---------------------------------------------------------------
    // Render type
    // ---------------------------------------------------------------
    @Override
    protected RenderType getRenderType() {
        // Note: PARTICLE_ATLAS_NO_CULL doesn't exist in 1.21.11
        // Using TRANSLUCENT for all particles, but fog particles are larger and more opaque
        // to improve visibility through water surface
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    // ---------------------------------------------------------------
    // Factory
    // ---------------------------------------------------------------
    /**
     * Registered via ParticleFactoryRegistry.getInstance().register().
     *
     * Uses SpriteProvider to randomly select from all available textures
     * defined in the particle JSON file, creating visual variety.
     */
    public static class Factory implements ParticleFactory<SimpleParticleType> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        // 1.21.11: createParticle has a Random as the 9th (last) parameter
        @Override
        public Particle createParticle(SimpleParticleType type,
                                       ClientWorld world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       Random random) {
            // Get particle size multiplier from config
            float sizeMultiplier = BloodModClient.getConfig().particleSizeMultiplier();

            // Get the current blood color from thread-local storage
            BloodColor.Color color = currentBloodColor.get();

            // Use random to select from available textures instead of always using index 0
            Sprite sprite = this.spriteProvider.getSprite(random);
            return new BloodParticle(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier,
                    color.red, color.green, color.blue);
        }
    }
}