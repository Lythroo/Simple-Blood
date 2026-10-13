package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import com.simpleblood.SimpleBloodClient;
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

/**
 * Poof-style fog particle for blood in water.
 * Similar to vanilla poof particles but uses custom textures that can't be overridden.
 * Expands quickly, drifts slowly, and fades out.
 */
//? if <=1.21.1 {
/*public class BloodFogParticle extends net.minecraft.client.particle.TextureSheetParticle implements DrawnBeforeWater {
*///?} else {
public class BloodFogParticle extends SingleQuadParticle implements DrawnBeforeWater {
//?}

    // Thread-local storage for current entity's blood color
    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f));

    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    // Thread-local: how much bigger than one drop's worth the next cloud is (merged clouds).
    private static final ThreadLocal<Float> currentScale = ThreadLocal.withInitial(() -> 1.0f);

    /** Set how much bigger the next cloud is than a single drop's cloud (1 = one drop). */
    public static void setScale(float scale) {
        currentScale.set(scale);
    }

    // Thread-local: whether the next cloud glows (glow squid ink).
    private static final ThreadLocal<Boolean> currentGlowing = ThreadLocal.withInitial(() -> false);

    /** Set whether the next cloud glows in the dark. */
    public static void setGlowing(boolean glows) {
        currentGlowing.set(glows);
    }

    private final boolean glows = currentGlowing.get();

    //? if <26.1 {
    /*@Override
    protected int getLightColor(float partialTick) {
        return glows ? 0xF000F0 : super.getLightColor(partialTick);
    }

    private int cloudLight(float partialTick) {
        return getLightColor(partialTick);
    }
    *///?} else {
    @Override
    protected int getLightCoords(float partialTick) {
        return glows ? 0xF000F0 : super.getLightCoords(partialTick);
    }

    private int cloudLight(float partialTick) {
        return getLightCoords(partialTick);
    }
    //?}

    // Fog constants
    // A cloud of blood in water spreads wide: about half a block to a block across. (Near the
    // surface getScaleFactorForDepth still shrinks it, so it never pokes out of the water.)
    private static final float MIN_SCALE = 0.22f;
    private static final float MAX_SCALE = 0.5f;
    /** Share of its full size a cloud starts at: blood clouds up at once, it never starts as a speck. */
    private static final float START_FRACTION = 0.6f;
    private static final int BASE_LIFETIME = 120; // 6 seconds (20 ticks/sec)

    private final float targetScale;
    private final float baseAlpha;
    private float desiredAlpha; // Store our desired alpha to reapply after parent operations
    private final SpriteSet spriteProvider; // Store for animation
    private final float animationSpeed; // Unique animation speed multiplier for each particle
    private float initialSubmersionDepth; // Cache initial depth for smooth start

    private boolean counted;

    /** Drawn for a shader pack: brighter and denser (see the constructor). Decided once, when the cloud forms. */
    private final boolean shaders = com.simpleblood.compat.ShaderPacks.inUse();

    /**
     * Drawn by UnderwaterParticles before the water surface rather than with the particles, so it
     * shows through the surface from above. Glowing ink under a shader pack stays a particle.
     */
    private final boolean beforeWater = !(shaders && glows);

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            BloodParticle.countDeath();
        }
        if (beforeWater) UnderwaterParticles.remove(this);
        super.remove();
    }

    public BloodFogParticle(ClientLevel world, double x, double y, double z,
                            double velX, double velY, double velZ,
                            SpriteSet spriteProvider,
                            float red, float green, float blue) {
        // Start with sprite 0 for animation sequence
        //? if <=1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(spriteProvider.get(0, 7));
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, spriteProvider.get(0, 7));
        //?}
        BloodParticle.countBirth();
        this.counted = true;

        // Store sprite provider for animation
        this.spriteProvider = spriteProvider;

        // Random animation speed multiplier (0.7 to 1.3) makes each particle unique
        this.animationSpeed = 0.7f + this.random.nextFloat() * 0.6f;

        // Get config multipliers
        float lifetimeMult = SimpleBloodClient.getConfig().fogLifetimeMultiplier();
        float sizeMult = SimpleBloodClient.getConfig().fogSizeMultiplier();
        float opacityMult = SimpleBloodClient.getConfig().fogOpacityMultiplier();

        // Lifetime with random variation
        int baseLife = BASE_LIFETIME + this.random.nextInt(20) - 10;
        float merged = currentScale.get();
        // A bigger cloud takes a little longer to thin out.
        this.lifetime = (int)(baseLife * lifetimeMult * (1f + (merged - 1f) * 0.3f));

        // Set color directly from blood color
        // The white fog texture will be tinted with the actual blood color
        if (glows) {
            // Glowing ink is light, not murk: paler and much denser, as bright as the
            // squid's own ink.
            red += (1f - red) * 0.35f;
            green += (1f - green) * 0.35f;
            blue += (1f - blue) * 0.35f;
        }
        // Under a shader pack the water surface seen from below is laid over the cloud (drawn
        // before the water, see UnderwaterParticles) and washes its colour out to grey: a brighter,
        // stronger red makes up for it.
        if (shaders && !glows) {
            red = Math.min(1f, red * 1.7f);
            green = Math.min(1f, green * 1.7f);
            blue = Math.min(1f, blue * 1.7f);
        }
        this.setColor(red, green, blue);

        // Target scale with config multiplier
        float baseTargetScale = MIN_SCALE + this.random.nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.targetScale = baseTargetScale * sizeMult * merged;

        // IMPROVED: Calculate initial submersion depth and start at appropriate size
        this.initialSubmersionDepth = calculateSubmersionDepth(x, y, z);
        float initialScaleFactor = getScaleFactorForDepth(this.initialSubmersionDepth);

        // Start at part of the target for that depth (see START_FRACTION)
        // This makes the fog texture more visible from the start
        this.quadSize = this.targetScale * initialScaleFactor * START_FRACTION;

        // Start with lower opacity - more transparent
        float glowDensity = glows ? 1.6f * SimpleBloodClient.getConfig().kinds.glowCloudOpacity / 100.0f : 1f;
        float shaderDensity = !shaders || glows ? 1f
                : 2.4f * SimpleBloodClient.getConfig().underwater.shaderCloudOpacity / 100.0f;
        this.baseAlpha = Math.min(0.85f, 0.25f * opacityMult * glowDensity * shaderDensity);

        // Apply initial surface fade if near surface
        float initialSurfaceFade = 1.0f;
        if (this.initialSubmersionDepth < 0.15f) {
            initialSurfaceFade = this.initialSubmersionDepth / 0.15f;
        }

        this.desiredAlpha = baseAlpha * initialSurfaceFade;
        applyAlpha();

        // Very slow velocity with barely any sinking
        this.xd = velX * 0.05f + (this.random.nextFloat() - 0.5f) * 0.02f;
        this.yd = velY * 0.05f - 0.008f; // Barely sinks - reduced from -0.02f
        this.zd = velZ * 0.05f + (this.random.nextFloat() - 0.5f) * 0.02f;
        if (beforeWater) UnderwaterParticles.add(this);
    }

    @Override
    public void tick() {
        // Whatever goes wrong in here must not take the game down: the cloud goes and the
        // particles are switched off.
        if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.PARTICLES)) {
            this.remove();
            return;
        }
        try {
            drift();
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "a blood cloud drifting", t);
            this.remove();
        }
    }

    private void drift() {
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // Calculate desired alpha BEFORE super.tick() so we know what it should be
        float agePercent = (float)this.age / this.lifetime;

        // Ultra-smooth single-stage fade for maximum smoothness
        if (agePercent < 0.65f) {
            // First 65%: Stay at full opacity - particles need to be visible
            this.desiredAlpha = baseAlpha;
        } else {
            // Final 35%: Very slow, smooth fade using ease-in quad
            // Starts barely noticeable, gradually becomes more apparent
            float fadeProgress = (agePercent - 0.65f) / 0.35f; // 0.0 to 1.0

            // Apply ease-in quad for ultra-smooth, gentle fade
            // Squared function makes it start VERY slow
            float easedFade = easeInQuad(fadeProgress);

            // Fade from 100% to 0% very smoothly
            this.desiredAlpha = baseAlpha * (1.0f - easedFade);
        }

        // Very light drag - particles drift slowly
        this.xd *= 0.97f;  // Increased from 0.95f for more drifting
        this.yd *= 0.99f;  // Very minimal Y drag for barely any sinking
        this.zd *= 0.97f;  // Increased from 0.95f for more drifting

        // Barely any downward drift (reduced from -0.002f to -0.0005f)
        this.yd -= 0.0005f;

        // Add sideways drifting - gentle current effect
        float driftStrength = 0.008f;
        this.xd += (this.random.nextFloat() - 0.5f) * driftStrength;
        this.zd += (this.random.nextFloat() - 0.5f) * driftStrength;

        // Subtle wobble for natural underwater movement (reduced from 0.003f)
        this.xd += (this.random.nextFloat() - 0.5f) * 0.002f;
        this.zd += (this.random.nextFloat() - 0.5f) * 0.002f;

        // Let parent class handle position updates and tracking
        super.tick();

        // ═══════════════════════════════════════════════════════════════════════
        // SUBMERSION-BASED SCALING - keeps fog from sticking out of water
        // ═══════════════════════════════════════════════════════════════════════

        // Calculate how submerged the particle is
        float submersionDepth = getSubmersionDepth();

        // Get scale factor based on depth (using helper method)
        float submersionScaleFactor = getScaleFactorForDepth(submersionDepth);

        // Also fade particles that are very close to the surface
        if (submersionDepth < 0.15f) {
            // Fade particles within 0.15 blocks of surface
            float surfaceFade = submersionDepth / 0.15f;
            this.desiredAlpha *= surfaceFade;
        }

        // Very slow expansion over first 85% of lifetime with ease-out for smooth growth
        // Expands quickly at first, then slows down naturally
        // NOW ALSO SCALED BY SUBMERSION DEPTH
        float adjustedTargetScale = this.targetScale * submersionScaleFactor;

        if (agePercent < 0.85f) {
            // Apply ease-out cubic for natural expansion
            float expansionProgress = agePercent / 0.85f; // 0.0 to 1.0
            float easedProgress = easeOutCubic(expansionProgress);
            // Grows from the size it was born at (not from nothing, which would make it pop
            // big for its first frame and then shrink before growing).
            this.quadSize = adjustedTargetScale * (START_FRACTION + (1f - START_FRACTION) * easedProgress);
        } else {
            this.quadSize = adjustedTargetScale;
        }

        // CRITICAL: Force-apply desired alpha at the very end to override any parent class changes
        // This prevents parent class from resetting alpha to 1.0 or any other value
        applyAlpha();

        // Animate through sprites 0-7 once over the particle's lifetime
        // Each particle has a unique animation speed for variety
        float animationProgress = ((float)this.age / this.lifetime) * this.animationSpeed;
        int spriteIndex = Math.min(7, (int)(animationProgress * 8.0f));
        this.setSprite(this.spriteProvider.get(spriteIndex, 7));
    }

    private void applyAlpha() {
        this.alpha = this.desiredAlpha;
    }

    //? if <=1.21.1 {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTick) {
        if (beforeWater) return; // UnderwaterParticles draws it, before the water surface
        super.render(buffer, camera, partialTick);
    }
    *///?} elif <26.1 {
    /*@Override
    public void extract(net.minecraft.client.renderer.state.QuadParticleRenderState state, net.minecraft.client.Camera camera, float partialTick) {
        if (beforeWater) return; // UnderwaterParticles draws it, before the water surface
        super.extract(state, camera, partialTick);
    }
    *///?} else {
    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state, net.minecraft.client.Camera camera, float partialTick) {
        if (beforeWater) return; // UnderwaterParticles draws it, before the water surface
        super.extract(state, camera, partialTick);
    }
    //?}

    @Override
    public boolean isIn(ClientLevel level) {
        return isAlive() && this.level == level;
    }

    @Override
    public boolean drawnBeforeWater() {
        return beforeWater;
    }

    @Override
    public void quadBeforeWater(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                net.minecraft.client.Camera camera, float partialTick) {
        net.minecraft.world.phys.Vec3 cam = DrawnBeforeWater.cameraPos(camera);
        DrawnBeforeWater.quad(vc, pose, camera.rotation(),
                (float) (net.minecraft.util.Mth.lerp(partialTick, xo, x) - cam.x),
                (float) (net.minecraft.util.Mth.lerp(partialTick, yo, y) - cam.y),
                (float) (net.minecraft.util.Mth.lerp(partialTick, zo, z) - cam.z),
                getQuadSize(partialTick), getU0(), getU1(), getV0(), getV1(),
                rCol, gCol, bCol, alpha, cloudLight(partialTick));
    }

    /**
     * Calculate how deeply submerged this particle is in water.
     * Returns distance in blocks from particle center to water surface.
     * 0.0 = at or above surface, 1.0+ = fully submerged
     */
    private float getSubmersionDepth() {
        return calculateSubmersionDepth(x, y, z);
    }

    /**
     * Static helper to calculate submersion depth at any position.
     * Used both in constructor and tick method.
     */
    private float calculateSubmersionDepth(double posX, double posY, double posZ) {
        net.minecraft.core.BlockPos particlePos = net.minecraft.core.BlockPos.containing(posX, posY, posZ);

        // First check if we're even in water at current position
        if (!level.getBlockState(particlePos).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return 0.0f; // Not in water at all
        }

        // Find the water surface above this particle
        // Check up to 4 blocks above for better detection
        for (int checkY = 0; checkY <= 4; checkY++) {
            net.minecraft.core.BlockPos checkPos = particlePos.above(checkY);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);

            // If we find air or non-water block, the surface is at this Y level
            if (!state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                // Water surface is at the bottom of this air block
                double waterSurfaceY = checkPos.getY();
                // Calculate depth (how far below surface we are)
                float depth = (float)(waterSurfaceY - posY);
                return Math.max(0.0f, depth); // Never negative
            }
        }

        // If we're more than 4 blocks deep, just return a large value
        return 4.0f;
    }

    /**
     * Get scale factor based on submersion depth.
     * Extracted as helper method for reuse.
     */
    private float getScaleFactorForDepth(float submersionDepth) {
        if (submersionDepth < 0.3f) {
            // Near surface: 10-30% size
            return 0.1f + (submersionDepth / 0.3f) * 0.2f;
        } else if (submersionDepth < 1.0f) {
            // Transition: 30-100% size
            return 0.3f + ((submersionDepth - 0.3f) / 0.7f) * 0.7f;
        } else {
            // Deep: full size
            return 1.0f;
        }
    }

    //? if <=1.21.1 {
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

    /**
     * Ease-out cubic function for smooth deceleration
     * Starts fast, ends slow - good for expansion
     */
    private float easeOutCubic(float t) {
        float f = t - 1.0f;
        return f * f * f + 1.0f;
    }

    /**
     * Ease-in-out cubic function for smooth acceleration and deceleration
     * Good for fade transitions
     */
    private float easeInOutCubic(float t) {
        if (t < 0.5f) {
            return 4.0f * t * t * t;
        } else {
            float f = 2.0f * t - 2.0f;
            return 0.5f * f * f * f + 1.0f;
        }
    }

    /**
     * Ease-in quad function for smooth fade out
     * Stays visible longer, then fades quickly
     */
    private float easeInQuad(float t) {
        return t * t;
    }

    /**
     * Ultra-smooth ease for very gradual fade
     * Uses quartic easing for extremely gentle transitions
     */
    private float easeInQuart(float t) {
        return t * t * t * t;
    }

    /**
     * Exponential ease-out for extremely smooth fade
     * Creates a very gentle, barely noticeable fade
     */
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
        //? if <=1.21.1 {
        /*public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ) {
        *///?} else {
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       RandomSource random) {
        //?}
            if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.PARTICLES)) return null;
            try {
                BloodColor.Color color = currentBloodColor.get();
                return new BloodFogParticle(world, x, y, z, velX, velY, velZ, this.spriteProvider,
                        color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "creating a blood cloud", t);
                return null;
            }
        }
    }
}