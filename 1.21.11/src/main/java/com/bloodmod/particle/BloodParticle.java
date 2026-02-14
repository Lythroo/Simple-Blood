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

    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f)); 

    private static final ThreadLocal<Boolean> shouldTransformToFog =
            ThreadLocal.withInitial(() -> true); 

    private static final ThreadLocal<Boolean> shouldDespawnInWater =
            ThreadLocal.withInitial(() -> false); 

    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    public static void setShouldTransformToFog(boolean shouldTransform) {
        shouldTransformToFog.set(shouldTransform);
    }

    public static void setShouldDespawnInWater(boolean shouldDespawn) {
        shouldDespawnInWater.set(shouldDespawn);
    }

    private static final float BASE_GRAVITY   = 0.04f;   

    private static final float BASE_DRAG      = 0.98f;   

    private static final int   BASE_LIFE = 40;      

    private static final int   JITTER    = 10;      

    private static final float MIN_SCALE = 0.08f;
    private static final float MAX_SCALE = 0.16f;

    private static final float FOG_MIN_SCALE = 1.2f;
    private static final float FOG_MAX_SCALE = 2.4f;
    private static final int FOG_BASE_LIFE = 60;

    private boolean isFog = false;
    private boolean isOnGround = false;
    private final float baseRed, baseGreen, baseBlue;
    private final boolean canTransformToFog;
    private final boolean shouldMeltInWater; 

    private float targetScale; 

    protected BloodParticle(ClientWorld world,
                            double x, double y, double z,
                            double velX, double velY, double velZ,
                            Sprite sprite,
                            float sizeMultiplier,
                            float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite);

        float lifetimeMultiplier = BloodModClient.getConfig().particleLifetimeMultiplier();

        int baseLifetime = BASE_LIFE + (int)(world.random.nextFloat() * JITTER * 2) - JITTER;
        this.maxAge = (int)(baseLifetime * lifetimeMultiplier);

        this.baseRed = red;
        this.baseGreen = green;
        this.baseBlue = blue;

        this.canTransformToFog = shouldTransformToFog.get();

        this.shouldMeltInWater = shouldDespawnInWater.get();

        this.setColor(red, green, blue);

        this.alpha = 1.0f;

        float baseScale = MIN_SCALE + world.random.nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.scale = baseScale * sizeMultiplier;
        this.targetScale = this.scale; 

        BlockPos spawnPos = BlockPos.ofFloored(x, y, z);
        BlockState spawnState = world.getBlockState(spawnPos);
        if (spawnState.isOf(Blocks.WATER) && canTransformToFog && BloodModClient.getConfig().underwaterFogEnabled()) {

            transformToFog();
        } else if (spawnState.isOf(Blocks.WATER) && shouldMeltInWater) {

            this.maxAge = 0; 

        }
    }

    private boolean isInWater() {

        BlockPos currentPos = BlockPos.ofFloored(x, y, z);
        BlockState currentState = world.getBlockState(currentPos);
        if (currentState.isOf(Blocks.WATER)) {
            return true;
        }

        BlockPos nextPos = BlockPos.ofFloored(x + velocityX, y + velocityY, z + velocityZ);
        if (!nextPos.equals(currentPos)) {
            BlockState nextState = world.getBlockState(nextPos);
            if (nextState.isOf(Blocks.WATER)) {
                return true;
            }
        }

        int blockX = currentPos.getX();
        int blockY = currentPos.getY();
        int blockZ = currentPos.getZ();

        double fracX = x - blockX;
        double fracZ = z - blockZ;

        if (fracX < 0.1) {
            if (world.getBlockState(new BlockPos(blockX - 1, blockY, blockZ)).isOf(Blocks.WATER)) {
                return true;
            }
        } else if (fracX > 0.9) {
            if (world.getBlockState(new BlockPos(blockX + 1, blockY, blockZ)).isOf(Blocks.WATER)) {
                return true;
            }
        }

        if (fracZ < 0.1) {
            if (world.getBlockState(new BlockPos(blockX, blockY, blockZ - 1)).isOf(Blocks.WATER)) {
                return true;
            }
        } else if (fracZ > 0.9) {
            if (world.getBlockState(new BlockPos(blockX, blockY, blockZ + 1)).isOf(Blocks.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void tick() {

        if (isFog && !BloodModClient.getConfig().underwaterFogEnabled()) {
            revertFromFog();
        }

        if (!isFog && !shouldMeltInWater) {

            boolean inWater = isInWater();

            if (inWater && canTransformToFog && BloodModClient.getConfig().underwaterFogEnabled()) {

                transformToFog();
            }
        }

        if (!isFog && shouldMeltInWater && isInWater()) {
            this.markDead();
            return;
        }

        super.tick(); 

        if (!isFog) {

            float gravityMult = BloodModClient.getConfig().particleGravityMultiplier();
            float dragMult = BloodModClient.getConfig().particleDragMultiplier();

            velocityY -= BASE_GRAVITY * gravityMult;

            float dragMultiplier = BASE_DRAG * dragMult; 

            if (isInWater() && !canTransformToFog) {

                dragMultiplier = 0.70f; 

            }

            velocityX *= dragMultiplier;
            velocityY *= dragMultiplier;
            velocityZ *= dragMultiplier;

            if (!isOnGround && onGround) {
                isOnGround = true;

                boolean stainsEnabled = BloodModClient.getConfig().bloodStainsEnabled();

                boolean willSpawnStain = stainsEnabled && !shouldMeltInWater && canTransformToFog;

                if (willSpawnStain) {

                    spawnBloodStain();

                    this.markDead();
                } else {

                    velocityX = 0;
                    velocityY = 0;
                    velocityZ = 0;

                }
            }

            if (isOnGround && age % 10 == 0) {
                BlockPos posBelow = BlockPos.ofFloored(x, y - 0.1, z);
                boolean hasBlockBelow = !world.getBlockState(posBelow).isAir();

                if (!hasBlockBelow) {

                    isOnGround = false;

                    velocityY = -0.1;
                }
            }

            if (isOnGround && this.scale < targetScale) {
                this.scale += (targetScale - this.scale) * 0.15f; 

            }

            float lifeFraction = 1.0f - (float) age / maxAge;
            if (lifeFraction < 0.25f) {
                this.alpha = lifeFraction / 0.25f;
            }
        } else {

            tickFog();
        }
    }

    private void transformToFog() {
        isFog = true;

        float fogLifetimeMult = BloodModClient.getConfig().fogLifetimeMultiplier();

        this.age = 0;
        int baseFogLife = FOG_BASE_LIFE + (int)(world.random.nextFloat() * 20) - 10;
        this.maxAge = (int)(baseFogLife * fogLifetimeMult);

        float desaturation = 0.6f;
        this.setColor(
                baseRed * desaturation + 0.4f * 0.4f,
                baseGreen * desaturation + 0.4f * 0.4f,
                baseBlue * desaturation + 0.4f * 0.4f
        );

        this.velocityX *= 0.1f;
        this.velocityY *= 0.1f;
        this.velocityZ *= 0.1f;

        float fogOpacityMult = BloodModClient.getConfig().fogOpacityMultiplier();
        this.alpha = 0.5f * fogOpacityMult;
    }

    private void revertFromFog() {
        isFog = false;

        this.setColor(baseRed, baseGreen, baseBlue);

        this.alpha = 1.0f;

        float lifetimeMultiplier = BloodModClient.getConfig().particleLifetimeMultiplier();
        int baseLifetime = BASE_LIFE + (int)(world.random.nextFloat() * JITTER * 2) - JITTER;
        int normalMaxAge = (int)(baseLifetime * lifetimeMultiplier);

        if (this.age < normalMaxAge) {
            this.maxAge = normalMaxAge;
        } else {

            this.maxAge = this.age + 20; 

        }

    }

    private void tickFog() {

        velocityX *= 0.92f;
        velocityY *= 0.92f;
        velocityZ *= 0.92f;

        velocityY -= 0.002f;

        velocityX += (world.random.nextFloat() - 0.5f) * 0.001f;
        velocityZ += (world.random.nextFloat() - 0.5f) * 0.001f;

        float lifeFraction = 1.0f - (float) age / maxAge;
        float fogOpacityMult = BloodModClient.getConfig().fogOpacityMultiplier();
        this.alpha = lifeFraction * 0.6f * fogOpacityMult;

        float fogSizeMult = BloodModClient.getConfig().fogSizeMultiplier();
        float particleSizeMult = BloodModClient.getConfig().particleSizeMultiplier();
        float targetScale = (FOG_MIN_SCALE + world.random.nextFloat() * (FOG_MAX_SCALE - FOG_MIN_SCALE))
                * particleSizeMult * fogSizeMult;

        if (this.scale < targetScale) {
            this.scale += (targetScale - this.scale) * 0.01f;
        }
    }

    private void spawnBloodStain() {

        if (!BloodModClient.getConfig().bloodStainsEnabled()) {
            return;
        }

        if (isFog || shouldMeltInWater || !canTransformToFog) {
            return;
        }

        com.bloodmod.particle.BloodStainParticle.setCurrentBloodColor(
                new BloodColor.Color(baseRed, baseGreen, baseBlue)
        );

        net.minecraft.client.MinecraftClient.getInstance().particleManager.addParticle(
                BloodParticles.BLOOD_STAIN,
                x, y + 0.01, z,  

                0, 0, 0  

        );
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
        public Particle createParticle(SimpleParticleType type,
                                       ClientWorld world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       Random random) {

            float sizeMultiplier = BloodModClient.getConfig().particleSizeMultiplier();

            BloodColor.Color color = currentBloodColor.get();

            Sprite sprite = this.spriteProvider.getSprite(random);
            return new BloodParticle(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier,
                    color.red, color.green, color.blue);
        }
    }
}