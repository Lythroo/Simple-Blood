package com.bloodmod.particle;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodParticles;
import com.bloodmod.Guard;
import com.bloodmod.RenderThread;
import com.bloodmod.surface.BloodSurfaces;
import net.minecraft.world.phys.Vec3;
//? if >1.21.1 {
import net.minecraft.client.particle.SingleQuadParticle.Layer;
//?}
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

//? if 1.21.1 {
/*public class BloodParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class BloodParticle extends SingleQuadParticle {
//?}

    private static final ThreadLocal<BloodColor.Color> currentBloodColor =
            ThreadLocal.withInitial(() -> new BloodColor.Color(0.55f, 0.03f, 0.03f));

    private static final ThreadLocal<Boolean> shouldTransformToFog =
            ThreadLocal.withInitial(() -> true);

    private static final ThreadLocal<Boolean> shouldDespawnInWater =
            ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Float> entitySizeMultiplier =
            ThreadLocal.withInitial(() -> 1.0f);

    private static final ThreadLocal<com.bloodmod.BloodKind> currentKind =
            ThreadLocal.withInitial(() -> com.bloodmod.BloodKind.LIQUID);

    public static void setCurrentKind(com.bloodmod.BloodKind kind) {
        currentKind.set(kind);
    }

    public static com.bloodmod.BloodKind currentBloodKind() {
        return currentKind.get();
    }

    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    protected static BloodColor.Color currentColor() {
        return currentBloodColor.get();
    }

    public static BloodColor.Color currentBloodColor() {
        return currentBloodColor.get();
    }

    public static float currentSizeMultiplier() {
        return entitySizeMultiplier.get();
    }

    public static boolean currentTransformsToFog() {
        return shouldTransformToFog.get();
    }

    public static void setShouldTransformToFog(boolean shouldTransform) {
        shouldTransformToFog.set(shouldTransform);
    }

    private static final ThreadLocal<Boolean> spawnPaintsSurfaces = ThreadLocal.withInitial(() -> true);

    public static void setPaintsSurfaces(boolean paints) {
        spawnPaintsSurfaces.set(paints);
    }

    public static void setShouldDespawnInWater(boolean shouldDespawn) {
        shouldDespawnInWater.set(shouldDespawn);
    }

    public static void setEntitySizeMultiplier(float multiplier) {
        entitySizeMultiplier.set(multiplier);
    }

    private static final java.util.concurrent.atomic.AtomicInteger alive = new java.util.concurrent.atomic.AtomicInteger();

    public static int aliveCount() { return alive.get(); }

    public static boolean underBudget() {
        com.bloodmod.BloodModConfig cfg = BloodModClient.getConfig();
        return cfg == null || alive.get() < cfg.particleBudget();
    }

    static void countBirth() { alive.incrementAndGet(); }
    static void countDeath() { alive.updateAndGet(n -> Math.max(0, n - 1)); }

    public static void resetBudget() { alive.set(0); }

    private boolean counted;

    private static final ThreadLocal<Integer> runoffDepth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> spawnCarried = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Double> spawnFloorY = ThreadLocal.withInitial(() -> Double.NaN);
    private static final ThreadLocal<Boolean> spawnExact = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Float> spawnLifeScale = ThreadLocal.withInitial(() -> 1.0f);

    public static void setBallistic(boolean exact, float lifeScale) {
        spawnExact.set(exact);
        spawnLifeScale.set(lifeScale);
    }

    public static void spawnRunoff(ClientLevel level, double x, double y, double z, int colour) {
        spawnDroplet(level, x, y, z, colour, 0, Double.NaN);
    }

    public static void spawnDroplet(ClientLevel level, double x, double y, double z, int colour,
                                    int carried, double floorY) {
        if (!RenderThread.on()) {
            RenderThread.run(() -> spawnDroplet(level, x, y, z, colour, carried, floorY));
            return;
        }
        int depth = runoffDepth.get();
        if (depth >= 2) return;
        com.bloodmod.BloodModConfig budgetCfg = BloodModClient.getConfig();
        if (budgetCfg != null && alive.get() > budgetCfg.particleBudget() * 5 / 4) return;
        BloodColor.Color prev = currentBloodColor.get();
        boolean prevFog = shouldTransformToFog.get();
        boolean prevMelt = shouldDespawnInWater.get();
        float prevSize = entitySizeMultiplier.get();
        currentBloodColor.set(new BloodColor.Color(
                ((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f));
        shouldTransformToFog.set(true);
        shouldDespawnInWater.set(false);
        entitySizeMultiplier.set(1.0f);
        com.bloodmod.BloodKind prevKind = currentKind.get();
        currentKind.set(com.bloodmod.BloodKind.LIQUID);
        boolean prevPaint = spawnPaintsSurfaces.get();
        spawnPaintsSurfaces.set(true);
        runoffDepth.set(depth + 1);
        spawnCarried.set(carried);
        spawnFloorY.set(floorY);
        try {
            Minecraft.getInstance().particleEngine.createParticle(BloodParticles.BLOOD_DROP,
                    x, y, z, 0.0, -0.02, 0.0);
        } finally {
            runoffDepth.set(depth);
            spawnCarried.set(0);
            spawnFloorY.set(Double.NaN);
            currentKind.set(prevKind);
            spawnPaintsSurfaces.set(prevPaint);
            currentBloodColor.set(prev);
            shouldTransformToFog.set(prevFog);
            shouldDespawnInWater.set(prevMelt);
            entitySizeMultiplier.set(prevSize);
        }
    }

    private static final float BASE_GRAVITY   = 0.04f;
    private static final float BASE_DRAG      = 0.98f;
    private static final int   BASE_LIFE = 40;
    private static final int   JITTER    = 10;

    private static final float MIN_SCALE = 0.08f;
    private static final float MAX_SCALE = 0.16f;

    private boolean isOnGround = false;
    private boolean ghost;
    boolean splash;
    private final boolean mayPaint;
    protected float landingScale = 1f;
    protected final com.bloodmod.BloodKind kind;
    private final int chainDepth;
    private final int carriedPixels;
    private final double floorY;
    private final float baseRed, baseGreen, baseBlue;
    private final boolean canTransformToFog;
    private final boolean shouldMeltInWater;
    private float targetScale;

    protected BloodParticle(ClientLevel world,
                            double x, double y, double z,
                            double velX, double velY, double velZ,
                            TextureAtlasSprite sprite,
                            float sizeMultiplier,
                            float red, float green, float blue) {
        //? if 1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(sprite);
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, sprite);
        //?}

        countBirth();
        this.counted = true;
        if (spawnExact.get()) {
            this.xd = velX;
            this.yd = velY;
            this.zd = velZ;
        }

        float lifetimeMultiplier = BloodModClient.getConfig().particleLifetimeMultiplier();

        int baseLifetime = BASE_LIFE + (int)(world.getRandom().nextFloat() * JITTER * 2) - JITTER;
        this.lifetime = (int)(baseLifetime * lifetimeMultiplier * spawnLifeScale.get());

        this.baseRed = red;
        this.baseGreen = green;
        this.baseBlue = blue;

        this.canTransformToFog = shouldTransformToFog.get();

        this.shouldMeltInWater = shouldDespawnInWater.get();
        this.kind = currentKind.get();
        this.mayPaint = spawnPaintsSurfaces.get();
        this.chainDepth = runoffDepth.get();
        this.carriedPixels = spawnCarried.get();
        this.floorY = spawnFloorY.get();

        this.setColor(red, green, blue);

        this.alpha = 0.95f;

        float baseScale = MIN_SCALE + world.getRandom().nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.quadSize = baseScale * sizeMultiplier * entitySizeMultiplier.get();
        this.targetScale = this.quadSize;

        BlockPos spawnPos = BlockPos.containing(x, y, z);
        BlockState spawnState = world.getBlockState(spawnPos);
        if (spawnState.is(Blocks.WATER) && canTransformToFog && BloodModClient.getConfig().underwaterFogEnabled()) {
            spawnFogParticle();
            this.lifetime = 0;
        } else if (spawnState.is(Blocks.WATER) && shouldMeltInWater) {
            this.lifetime = 0;
        }
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            countDeath();
        }
        super.remove();
    }

    private boolean isInWater() {
        BlockPos currentPos = BlockPos.containing(x, y, z);
        BlockState currentState = level.getBlockState(currentPos);
        if (currentState.is(Blocks.WATER)) {
            return true;
        }

        BlockPos nextPos = BlockPos.containing(x + xd, y + yd, z + zd);
        if (!nextPos.equals(currentPos)) {
            BlockState nextState = level.getBlockState(nextPos);
            if (nextState.is(Blocks.WATER)) {
                return true;
            }
        }

        int blockX = currentPos.getX();
        int blockY = currentPos.getY();
        int blockZ = currentPos.getZ();

        double fracX = x - blockX;
        double fracZ = z - blockZ;

        if (fracX < 0.1) {
            if (level.getBlockState(new BlockPos(blockX - 1, blockY, blockZ)).is(Blocks.WATER)) {
                return true;
            }
        } else if (fracX > 0.9) {
            if (level.getBlockState(new BlockPos(blockX + 1, blockY, blockZ)).is(Blocks.WATER)) {
                return true;
            }
        }

        if (fracZ < 0.1) {
            if (level.getBlockState(new BlockPos(blockX, blockY, blockZ - 1)).is(Blocks.WATER)) {
                return true;
            }
        } else if (fracZ > 0.9) {
            if (level.getBlockState(new BlockPos(blockX, blockY, blockZ + 1)).is(Blocks.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void tick() {
        if (!Guard.ok(Guard.Part.PARTICLES)) {
            this.remove();
            return;
        }
        try {
            tickBlood();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, "a blood drop moving", t);
            this.remove();
        }
    }

    private void tickBlood() {
        if (!shouldMeltInWater) {
            boolean inWater = isInWater();

            if (inWater && canTransformToFog && BloodModClient.getConfig().underwaterFogEnabled()) {
                spawnFogParticle();
                this.remove();
                return;
            }
        }

        if (shouldMeltInWater && isInWater()) {
            this.remove();
            return;
        }

        double prevX = x, prevY = y, prevZ = z;
        double wantX = xd, wantY = yd, wantZ = zd;

        if (age == 0 && !ghost && insideCollision(x, y, z)) ghost = true;

        if (ghost) {
            this.xo = x; this.yo = y; this.zo = z;
            if (this.age++ >= this.lifetime) { this.remove(); return; }
            this.setPos(x + xd, y + yd, z + zd);
            boolean paints = BloodModClient.getConfig().surfaces.enabled && kind.paintsSurfaces() && mayPaint;
            if (paints && depositOnSurface(prevX, prevY, prevZ, wantX, wantY, wantZ)) {
                this.remove();
                return;
            }
            if (!insideCollision(x, y, z)) ghost = false;
        } else {
            super.tick();
        }
        if (this.removed) return;

        if (!Double.isNaN(floorY) && y < floorY) {
            this.remove();
            return;
        }

        float gravityMult = BloodModClient.getConfig().particleGravityMultiplier();
        float dragMult = BloodModClient.getConfig().particleDragMultiplier();

        yd -= BASE_GRAVITY * gravityMult;

        float dragMultiplier = BASE_DRAG * dragMult;

        if (isInWater() && (!canTransformToFog || !BloodModClient.getConfig().underwaterFogEnabled())) {
            dragMultiplier = 0.70f;
        }

        xd *= dragMultiplier;
        yd *= dragMultiplier;
        zd *= dragMultiplier;

        boolean blocked = Math.abs((x - prevX) - wantX) > 1.0e-6
                || Math.abs((y - prevY) - wantY) > 1.0e-6
                || Math.abs((z - prevZ) - wantZ) > 1.0e-6;

        if (!blocked && !isOnGround && kind.paintsSurfaces() && mayPaint && BloodModClient.getConfig().surfaces.enabled) {
            BlockPos here = BlockPos.containing(x, y, z);
            boolean entered = !here.equals(BlockPos.containing(prevX, prevY, prevZ)) || age == 1;
            BlockState hereState = level.getBlockState(here);
            boolean thin = false;
            if (hereState.isAir()) {
                BlockState below = level.getBlockState(here.below());
                thin = BloodSurfaces.isHandDrawn(below) || BloodSurfaces.hasTiltedFaces(below)
                        || BloodSurfaces.isHandDrawn(level.getBlockState(here.above()));
            } else if (BloodSurfaces.isHandDrawn(hereState) || BloodSurfaces.hasTiltedFaces(hereState)) {
                thin = true;
            } else if (hereState.getCollisionShape(level, here).isEmpty()) {
                thin = entered && BloodSurfaces.isCrossModel(hereState);
            }
            if (thin && depositOnSurface(prevX, prevY, prevZ, wantX, wantY, wantZ)) {
                this.remove();
                return;
            }
        }

        if (blocked && !isOnGround) {
            boolean paints = BloodModClient.getConfig().surfaces.enabled && kind.paintsSurfaces() && mayPaint;

            if (paints && depositOnSurface(prevX, prevY, prevZ, wantX, wantY, wantZ)) {
                this.remove();
                return;
            }
            if (paints && phantomContact(prevX, prevY, prevZ, wantX, wantY, wantZ)) {
                ghost = true;
                xd = wantX; yd = wantY; zd = wantZ;
                this.setPos(prevX + wantX, prevY + wantY, prevZ + wantZ);
                return;
            }
            if (onGround) {
                isOnGround = true;
                xd = 0;
                yd = 0;
                zd = 0;
            }
        }

        if (isOnGround && age % 10 == 0) {
            BlockPos posBelow = BlockPos.containing(x, y - 0.1, z);
            boolean hasBlockBelow = !level.getBlockState(posBelow).isAir();

            if (!hasBlockBelow) {
                isOnGround = false;
                yd = -0.1;
            }
        }

        if (isOnGround && this.quadSize < targetScale) {
            this.quadSize += (targetScale - this.quadSize) * 0.15f;
        }

        float lifeFraction = 1.0f - (float) age / lifetime;
        if (lifeFraction < 0.25f) {
            this.alpha = 0.95f * (lifeFraction / 0.25f);
        }
    }

    private boolean phantomContact(double fromX, double fromY, double fromZ, double wantX, double wantY, double wantZ) {
        double len = Math.sqrt(wantX * wantX + wantY * wantY + wantZ * wantZ);
        if (len < 1.0e-6) return false;
        double s = (len + 0.03) / len;
        double cx = fromX + wantX * s, cy = fromY + wantY * s, cz = fromZ + wantZ * s;
        BlockPos pos = BlockPos.containing(cx, cy, cz);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        net.minecraft.world.phys.shapes.VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return false;
        double lx = cx - pos.getX(), ly = cy - pos.getY(), lz = cz - pos.getZ();
        boolean inCollision = false;
        for (net.minecraft.world.phys.AABB b : collision.toAabbs()) {
            if (b.inflate(0.002).contains(lx, ly, lz)) { inCollision = true; break; }
        }
        if (!inCollision) return false;
        return !BloodSurfaces.visibleSolidAt(level, cx, cy, cz);
    }

    private boolean insideCollision(double px, double py, double pz) {
        BlockPos pos = BlockPos.containing(px, py, pz);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        double lx = px - pos.getX(), ly = py - pos.getY(), lz = pz - pos.getZ();
        for (net.minecraft.world.phys.AABB b : state.getCollisionShape(level, pos).toAabbs()) {
            if (b.inflate(0.002).contains(lx, ly, lz)) return true;
        }
        return false;
    }

    private void spawnFogParticle() {
        double px = x, py = y, pz = z, vx = xd, vy = yd, vz = zd;
        RenderThread.run(() -> {
            com.bloodmod.particle.BloodFogParticle.setCurrentBloodColor(
                    new BloodColor.Color(baseRed, baseGreen, baseBlue)
            );

            Minecraft.getInstance().particleEngine.createParticle(
                    BloodParticles.BLOOD_FOG,
                    px, py, pz,
                    vx, vy, vz
            );
        });
    }

    private boolean depositOnSurface(double fromX, double fromY, double fromZ,
                                     double wantX, double wantY, double wantZ) {
        Vec3 from = new Vec3(fromX, fromY, fromZ);
        Vec3 dir = new Vec3(wantX, wantY, wantZ);
        double len = dir.length();
        if (len < 1.0e-6) {
            dir = new Vec3(0, -1, 0);
            len = 1;
        }
        Vec3 to = from.add(dir.scale((len + this.bbWidth + 0.05) / len));

        BloodSurfaces.Hit hit = BloodSurfaces.trace(level, from, to);
        if (hit == null && onGround) {
            hit = BloodSurfaces.trace(level, new Vec3(x, y + 0.05, z), new Vec3(x, y - 0.2, z));
        }
        if (hit == null) return false;

        int colour = ((int) (baseRed * 255) << 16) | ((int) (baseGreen * 255) << 8) | (int) (baseBlue * 255);
        float amount = BloodModClient.getConfig().surfaceAmount();
        int pixels;
        if (carriedPixels > 0) {
            pixels = carriedPixels;
        } else {
            int base = 1 + random.nextInt(2) + (quadSize > 0.22f ? 1 : 0);
            if (splash) base += 2 + random.nextInt(2);
            int res = BloodModClient.getConfig().surfaces.resolution;
            float areaScale = res >= 16 ? 3.0f : 1.0f;
            pixels = Math.max(amount > 0 ? 1 : 0, Math.round(base * amount * areaScale * landingScale));
            if (chainDepth > 0) pixels = Math.max(1, pixels - chainDepth);
        }

        Vec3 travel = new Vec3(wantX, wantY, wantZ);
        boolean powder = kind == com.bloodmod.BloodKind.POWDER;
        BloodSurfaces.Hit landed = hit;
        if (!RenderThread.on()) {
            BloodSurfaces.depositLater(level, landed, colour, pixels, travel, powder, result -> {
                if (result != BloodSurfaces.NOTHING && chainDepth == 0) {
                    com.bloodmod.BloodSounds.landed(level, landed.point(), result == BloodSurfaces.INTO_PUDDLE, splash, kind);
                }
            });
            return true;
        }
        int result = BloodSurfaces.deposit(level, hit, colour, pixels, travel, powder);
        if (result != BloodSurfaces.NOTHING && chainDepth == 0) {
            com.bloodmod.BloodSounds.landed(level, hit.point(), result == BloodSurfaces.INTO_PUDDLE, splash, kind);
        }
        return result != BloodSurfaces.NOTHING;
    }

    //? if <26.1 {
    /*@Override
    protected int getLightColor(float partialTick) {
        return kind == com.bloodmod.BloodKind.EMBER ? 0xF000F0 : super.getLightColor(partialTick);
    }
    *///?} else {
    @Override
    protected int getLightCoords(float partialTick) {
        return kind == com.bloodmod.BloodKind.EMBER ? 0xF000F0 : super.getLightCoords(partialTick);
    }
    //?}

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

    public static class Factory implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet spriteProvider;
        private final boolean splash;

        public Factory(SpriteSet spriteProvider) {
            this(spriteProvider, false);
        }

        public Factory(SpriteSet spriteProvider, boolean splash) {
            this.spriteProvider = spriteProvider;
            this.splash = splash;
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
            if (!Guard.ok(Guard.Part.PARTICLES)) return null;
            try {
                float sizeMultiplier = BloodModClient.getConfig().particleSizeMultiplier();
                BloodColor.Color color = currentBloodColor.get();

                //? if 1.21.1 {
                /*TextureAtlasSprite sprite = this.spriteProvider.get(world.getRandom());
                *///?} else {
                TextureAtlasSprite sprite = this.spriteProvider.get(random);
                //?}
                BloodParticle particle = new BloodParticle(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier,
                        color.red, color.green, color.blue);
                particle.splash = splash;
                return particle;
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, "creating a blood drop", t);
                return null;
            }
        }
    }
}
