package com.bloodmod.particle;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodModClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Blood stain particle that appears on the ground when blood hits it.
 * These stains fade over time (10-20 seconds by default, configurable).
 * Reuses the existing blood particle textures.
 */
public class BloodStainParticle extends BillboardParticle {

    // Thread-local storage for current entity's blood color
    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f));

    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    // Base stain size range (will be multiplied by config)
    // Larger than regular blood particles since they're on the ground
    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 0.4f;

    private final float baseAlpha; // Starting alpha for darker stains
    private boolean isOnGroundStain = false; // Track if we're stuck to ground

    public BloodStainParticle(ClientWorld world, double x, double y, double z,
                              double velX, double velY, double velZ,
                              Sprite sprite,
                              float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite);

        // Get config settings
        int durationSeconds = BloodModClient.getConfig().stainDurationSeconds();
        float particleSizeMult = BloodModClient.getConfig().particleSizeMultiplier();
        float stainSizeMult = BloodModClient.getConfig().stainSizeMultiplier();

        // Set lifetime directly from config (in ticks: seconds * 20)
        this.maxAge = durationSeconds * 20;

        // Set color
        this.setColor(red, green, blue);

        // Random size with config multipliers
        float baseScale = MIN_SCALE + world.random.nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.scale = baseScale * particleSizeMult * stainSizeMult;

        // opacity
        // Still vary slightly for visual variety (0.85-1.0)
        this.baseAlpha = 0.35f + world.random.nextFloat() * 0.15f;
        this.alpha = baseAlpha;

        // Start with no velocity - spawned on ground
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
    }

    @Override
    public void tick() {
        // Always call super.tick() to handle collision detection properly
        super.tick();

        if (this.age >= this.maxAge) {
            this.markDead();
            return;
        }

        // Detect when we land on ground
        if (!isOnGroundStain && this.onGround) {
            isOnGroundStain = true;
            // Stop all movement
            this.velocityX = 0;
            this.velocityY = 0;
            this.velocityZ = 0;
        }

        // If stuck to ground, check if block beneath was removed (every 10 ticks)
        if (isOnGroundStain && this.age % 10 == 0) {
            BlockPos posBelow = BlockPos.ofFloored(x, y - 0.1, z);
            if (world.getBlockState(posBelow).isAir()) {
                // Ground removed - start falling again
                isOnGroundStain = false;
            }
        }

        // If we're on ground, force velocity to 0 to stay put
        if (isOnGroundStain) {
            this.velocityX = 0;
            this.velocityY = 0;
            this.velocityZ = 0;
        }

        // Fade out over the last 40% of lifetime
        float lifeFraction = 1.0f - (float) age / maxAge;
        if (lifeFraction < 0.4f) {
            this.alpha = baseAlpha * (lifeFraction / 0.4f);
        }
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       Random random) {
            BloodColor.Color color = currentBloodColor.get();
            Sprite sprite = this.spriteProvider.getSprite(random);
            return new BloodStainParticle(world, x, y, z, velX, velY, velZ, sprite,
                    color.red, color.green, color.blue);
        }
    }
}