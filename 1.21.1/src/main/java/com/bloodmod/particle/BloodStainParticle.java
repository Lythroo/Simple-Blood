package com.bloodmod.particle;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodModClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;

public class BloodStainParticle extends SpriteBillboardParticle {

    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f));

    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 0.4f;

    private final float baseAlpha; 

    private boolean isOnGroundStain = false; 

    public BloodStainParticle(ClientWorld world, double x, double y, double z,
                              double velX, double velY, double velZ,
                              Sprite sprite,
                              float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ);

        this.setSprite(sprite);

        int durationSeconds = BloodModClient.getConfig().stainDurationSeconds();
        float particleSizeMult = BloodModClient.getConfig().particleSizeMultiplier();
        float stainSizeMult = BloodModClient.getConfig().stainSizeMultiplier();

        this.maxAge = durationSeconds * 20;

        this.setColor(red, green, blue);

        float baseScale = MIN_SCALE + world.random.nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.scale = baseScale * particleSizeMult * stainSizeMult;

        this.baseAlpha = 0.35f + world.random.nextFloat() * 0.15f;
        this.alpha = baseAlpha;

        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;
    }

    @Override
    public void tick() {

        super.tick();

        if (this.age >= this.maxAge) {
            this.markDead();
            return;
        }

        if (!isOnGroundStain && this.onGround) {
            isOnGroundStain = true;

            this.velocityX = 0;
            this.velocityY = 0;
            this.velocityZ = 0;
        }

        if (isOnGroundStain && this.age % 10 == 0) {
            BlockPos posBelow = BlockPos.ofFloored(x, y - 0.1, z);
            if (world.getBlockState(posBelow).isAir()) {

                isOnGroundStain = false;
            }
        }

        if (isOnGroundStain) {
            this.velocityX = 0;
            this.velocityY = 0;
            this.velocityZ = 0;
        }

        float lifeFraction = 1.0f - (float) age / maxAge;
        if (lifeFraction < 0.4f) {
            this.alpha = baseAlpha * (lifeFraction / 0.4f);
        }
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ) {
            BloodColor.Color color = currentBloodColor.get();
            Sprite sprite = this.spriteProvider.getSprite(world.getRandom());
            return new BloodStainParticle(world, x, y, z, velX, velY, velZ, sprite,
                    color.red, color.green, color.blue);
        }
    }
}