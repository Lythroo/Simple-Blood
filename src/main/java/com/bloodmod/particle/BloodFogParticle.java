package com.bloodmod.particle;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodModClient;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
//? if >1.21.1 {
import net.minecraft.client.particle.SingleQuadParticle.Layer;
//?}
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

//? if 1.21.1 {
/*public class BloodFogParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class BloodFogParticle extends SingleQuadParticle {
//?}

    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f));

    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    private static final float MIN_SCALE = 0.08f;
    private static final float MAX_SCALE = 0.35f;
    private static final int BASE_LIFETIME = 120;

    private final float targetScale;
    private final float baseAlpha;
    private float desiredAlpha;
    private final SpriteSet spriteProvider;
    private final float animationSpeed;
    private float initialSubmersionDepth;

    private boolean counted;

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            BloodParticle.countDeath();
        }
        super.remove();
    }

    public BloodFogParticle(ClientLevel world, double x, double y, double z,
                            double velX, double velY, double velZ,
                            SpriteSet spriteProvider,
                            float red, float green, float blue) {
        //? if 1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(spriteProvider.get(0, 7));
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, spriteProvider.get(0, 7));
        //?}
        BloodParticle.countBirth();
        this.counted = true;

        this.spriteProvider = spriteProvider;

        this.animationSpeed = 0.7f + this.random.nextFloat() * 0.6f;

        float lifetimeMult = BloodModClient.getConfig().fogLifetimeMultiplier();
        float sizeMult = BloodModClient.getConfig().fogSizeMultiplier();
        float opacityMult = BloodModClient.getConfig().fogOpacityMultiplier();

        int baseLife = BASE_LIFETIME + this.random.nextInt(20) - 10;
        this.lifetime = (int)(baseLife * lifetimeMult);

        this.setColor(red, green, blue);

        float baseTargetScale = MIN_SCALE + this.random.nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.targetScale = baseTargetScale * sizeMult;

        this.initialSubmersionDepth = calculateSubmersionDepth(x, y, z);
        float initialScaleFactor = getScaleFactorForDepth(this.initialSubmersionDepth);

        this.quadSize = this.targetScale * initialScaleFactor * 0.4f;

        this.baseAlpha = 0.25f * opacityMult;

        float initialSurfaceFade = 1.0f;
        if (this.initialSubmersionDepth < 0.15f) {
            initialSurfaceFade = this.initialSubmersionDepth / 0.15f;
        }

        this.desiredAlpha = baseAlpha * initialSurfaceFade;
        this.alpha = this.desiredAlpha;

        this.xd = velX * 0.05f + (this.random.nextFloat() - 0.5f) * 0.02f;
        this.yd = velY * 0.05f - 0.008f;
        this.zd = velZ * 0.05f + (this.random.nextFloat() - 0.5f) * 0.02f;
    }

    @Override
    public void tick() {
        if (!com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.PARTICLES)) {
            this.remove();
            return;
        }
        try {
            drift();
        } catch (Throwable t) {
            com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.PARTICLES, "a blood cloud drifting", t);
            this.remove();
        }
    }

    private void drift() {
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float agePercent = (float)this.age / this.lifetime;

        if (agePercent < 0.65f) {
            this.desiredAlpha = baseAlpha;
        } else {
            float fadeProgress = (agePercent - 0.65f) / 0.35f;

            float easedFade = easeInQuad(fadeProgress);

            this.desiredAlpha = baseAlpha * (1.0f - easedFade);
        }

        this.xd *= 0.97f;
        this.yd *= 0.99f;
        this.zd *= 0.97f;

        this.yd -= 0.0005f;

        float driftStrength = 0.008f;
        this.xd += (this.random.nextFloat() - 0.5f) * driftStrength;
        this.zd += (this.random.nextFloat() - 0.5f) * driftStrength;

        this.xd += (this.random.nextFloat() - 0.5f) * 0.002f;
        this.zd += (this.random.nextFloat() - 0.5f) * 0.002f;

        super.tick();

        float submersionDepth = getSubmersionDepth();

        float submersionScaleFactor = getScaleFactorForDepth(submersionDepth);

        if (submersionDepth < 0.15f) {
            float surfaceFade = submersionDepth / 0.15f;
            this.desiredAlpha *= surfaceFade;
        }

        float adjustedTargetScale = this.targetScale * submersionScaleFactor;

        if (agePercent < 0.85f) {
            float expansionProgress = agePercent / 0.85f;
            float easedProgress = easeOutCubic(expansionProgress);
            this.quadSize = adjustedTargetScale * easedProgress;
        } else {
            this.quadSize = adjustedTargetScale;
        }

        this.alpha = this.desiredAlpha;

        float animationProgress = ((float)this.age / this.lifetime) * this.animationSpeed;
        int spriteIndex = Math.min(7, (int)(animationProgress * 8.0f));
        this.setSprite(this.spriteProvider.get(spriteIndex, 7));
    }

    private float getSubmersionDepth() {
        return calculateSubmersionDepth(x, y, z);
    }

    private float calculateSubmersionDepth(double posX, double posY, double posZ) {
        net.minecraft.core.BlockPos particlePos = net.minecraft.core.BlockPos.containing(posX, posY, posZ);

        if (!level.getBlockState(particlePos).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return 0.0f;
        }

        for (int checkY = 0; checkY <= 4; checkY++) {
            net.minecraft.core.BlockPos checkPos = particlePos.above(checkY);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);

            if (!state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                double waterSurfaceY = checkPos.getY();
                float depth = (float)(waterSurfaceY - posY);
                return Math.max(0.0f, depth);
            }
        }

        return 4.0f;
    }

    private float getScaleFactorForDepth(float submersionDepth) {
        if (submersionDepth < 0.3f) {
            return 0.1f + (submersionDepth / 0.3f) * 0.2f;
        } else if (submersionDepth < 1.0f) {
            return 0.3f + ((submersionDepth - 0.3f) / 0.7f) * 0.7f;
        } else {
            return 1.0f;
        }
    }

    //? if 1.21.1 {
    /*@Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?} else {
    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
    //?}

    private float easeOutCubic(float t) {
        float f = t - 1.0f;
        return f * f * f + 1.0f;
    }

    private float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4.0f * t * t * t;
        } else {
            float f = 2.0f * t - 2.0f;
            return 0.5f * f * f * f + 1.0f;
        }
    }

    private float easeInQuad(float t) {
        return t * t;
    }

    private float easeInQuart(float t) {
        return t * t * t * t;
    }

    private float easeOutExpo(float t) {
        if (t >= 1.0f) return 1.0f;
        return 1.0f - (float)Math.pow(2, -10 * t);
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        //? if 1.21.1 {
        /*public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ) {
        *///?} else {
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       RandomSource random) {
        //?}
            if (!com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.PARTICLES)) return null;
            try {
                BloodColor.Color color = currentBloodColor.get();
                return new BloodFogParticle(world, x, y, z, velX, velY, velZ, this.spriteProvider,
                        color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.PARTICLES, "creating a blood cloud", t);
                return null;
            }
        }
    }
}
