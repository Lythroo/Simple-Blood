package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.BloodParticles;
import com.simpleblood.Guard;
import com.simpleblood.RenderThread;
import com.simpleblood.surface.BloodSurfaces;
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

//? if <=1.21.1 {
/*public class BloodParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class BloodParticle extends SingleQuadParticle {
//?}

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

    // Thread-local per-entity particle size multiplier (from the SimpleBloodAPI). Default 1.0.
    private static final ThreadLocal<Float> entitySizeMultiplier =
            ThreadLocal.withInitial(() -> 1.0f);

    // Thread-local blood kind of the entity being spawned for (liquid / debris / ember / powder).
    private static final ThreadLocal<com.simpleblood.BloodKind> currentKind =
            ThreadLocal.withInitial(() -> com.simpleblood.BloodKind.LIQUID);

    // Thread-local: whether the next batch glows in the dark (glow squid ink).
    private static final ThreadLocal<Boolean> spawnGlows = ThreadLocal.withInitial(() -> false);

    /** Set whether the next batch of particles glows (full brightness, glowing puddles). */
    public static void setGlowing(boolean glows) {
        spawnGlows.set(glows);
    }

    public static boolean currentGlows() {
        return spawnGlows.get();
    }

    /** Set what kind of blood the next batch of particles is; drives painting and lighting. */
    public static void setCurrentKind(com.simpleblood.BloodKind kind) {
        currentKind.set(kind);
    }

    public static com.simpleblood.BloodKind currentBloodKind() {
        return currentKind.get();
    }

    /**
     * Set the blood color for the next batch of particles to be created.
     * Must be called before spawning particles.
     */
    public static void setCurrentBloodColor(BloodColor.Color color) {
        currentBloodColor.set(color);
    }

    /** Colour set for the next batch (used by subclasses' factories). */
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

    /**
     * Set whether particles should transform to fog when hitting water.
     * Set to false for entities that emit debris/particles instead of blood clouds.
     */
    public static void setShouldTransformToFog(boolean shouldTransform) {
        shouldTransformToFog.set(shouldTransform);
    }

    private static final ThreadLocal<Boolean> spawnPaintsSurfaces = ThreadLocal.withInitial(() -> true);

    public static boolean currentPaintsSurfaces() {
        return spawnPaintsSurfaces.get();
    }

    /**
     * Set whether the next particles may paint blocks (puddles, runs, stains). Off for
     * entities whose blood is meant to stay as drops (API {@code setTransformToStains(false)}).
     */
    public static void setPaintsSurfaces(boolean paints) {
        spawnPaintsSurfaces.set(paints);
    }

    /**
     * Puts every per-spawn setting on this thread back to its default (red liquid blood that
     * paints, normal size and flight). The setters above stick until changed, so a spawner that
     * is not working for a particular mob starts from here; otherwise it inherits whatever the
     * last mob hit left behind, e.g. a skeleton whose blood never paints.
     */
    public static void resetSpawnState() {
        currentBloodColor.remove();
        shouldTransformToFog.remove();
        shouldDespawnInWater.remove();
        entitySizeMultiplier.remove();
        currentKind.remove();
        spawnGlows.remove();
        spawnPaintsSurfaces.remove();
        spawnExact.remove();
        spawnLifeScale.remove();
    }

    /**
     * Set whether particles should despawn when touching water.
     * Set to true for snow/ice particles that should melt in water.
     */
    public static void setShouldDespawnInWater(boolean shouldDespawn) {
        shouldDespawnInWater.set(shouldDespawn);
    }

    /**
     * Set a per-entity particle size multiplier for the next batch of particles.
     * Applied on top of the global config size. 1.0 = no change.
     */
    public static void setEntitySizeMultiplier(float multiplier) {
        entitySizeMultiplier.set(multiplier);
    }

    // ---------------------------------------------------------------
    // Budget: how many blood particles are alive right now
    // ---------------------------------------------------------------
    // Particles may be created and removed on worker threads (AsyncParticles), so the count
    // is atomic.
    private static final java.util.concurrent.atomic.AtomicInteger alive = new java.util.concurrent.atomic.AtomicInteger();

    /** Blood particles (all kinds, fog included) currently alive. */
    public static int aliveCount() { return alive.get(); }

    /** Whether another particle fits under the configured budget. */
    public static boolean underBudget() {
        com.simpleblood.SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null || alive.get() < cfg.particleBudget();
    }

    /** Called by every blood particle when it is created / removed. */
    static void countBirth() { alive.incrementAndGet(); }
    static void countDeath() { alive.updateAndGet(n -> Math.max(0, n - 1)); }

    /** The particle engine dropped everything (level change): start counting afresh. */
    public static void resetBudget() { alive.set(0); }

    private boolean counted;

    // How many times a droplet has already rolled off an edge (bounds runoff chains).
    private static final ThreadLocal<Integer> runoffDepth = ThreadLocal.withInitial(() -> 0);
    // Blood volume (canvas pixels) a droplet carries; 0 = derive from its size.
    private static final ThreadLocal<Integer> spawnCarried = ThreadLocal.withInitial(() -> 0);
    // Droplets released from broken blocks vanish below this height (NaN = no limit).
    private static final ThreadLocal<Double> spawnFloorY = ThreadLocal.withInitial(() -> Double.NaN);
    // Ballistic spawns keep the exact velocity they are given (vanilla's constructor otherwise
    // normalises it to a gentle puff) and may live longer, for blood thrown high.
    private static final ThreadLocal<Boolean> spawnExact = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Float> spawnLifeScale = ThreadLocal.withInitial(() -> 1.0f);

    /** Following spawns fly exactly as told (no velocity normalising) and live {@code lifeScale} times as long. */
    public static void setBallistic(boolean exact, float lifeScale) {
        spawnExact.set(exact);
        spawnLifeScale.set(lifeScale);
    }

    /** Whether the next spawn flies exactly as told (see {@link #setBallistic}). */
    static boolean ballisticSpawn() { return spawnExact.get(); }

    /** Lifetime factor for the next spawn (see {@link #setBallistic}). */
    static float nextLifeScale() { return spawnLifeScale.get(); }

    /**
     * Spawns a droplet that rolls off a block edge when surface blood spills past it.
     * Called by the surface engine; the drop lands and paints wherever it ends up.
     */
    public static void spawnRunoff(ClientLevel level, double x, double y, double z, int colour) {
        spawnDroplet(level, x, y, z, colour, 0, Double.NaN);
    }

    /**
     * Spawns a droplet carrying {@code carried} pixels of blood (0 = size based) that is
     * removed once it falls below {@code floorY}. Used when the block under surface blood
     * disappears and the blood drops onto whatever is beneath.
     */
    public static void spawnDroplet(ClientLevel level, double x, double y, double z, int colour,
                                    int carried, double floorY) {
        if (!RenderThread.on()) {
            // The particle engine is fed from the render thread only.
            RenderThread.run(() -> spawnDroplet(level, x, y, z, colour, carried, floorY));
            return;
        }
        int depth = runoffDepth.get();
        if (depth >= 2) return;
        // Never inside a solid block: there it could only hang, unlit and black, painting
        // nothing (the blood stays where it was instead).
        BlockPos at = BlockPos.containing(x, y, z);
        if (level.getBlockState(at).isCollisionShapeFullBlock(level, at)) return;
        // Runoff keeps the surface simulation honest, so it may overshoot the budget a little.
        com.simpleblood.SimpleBloodConfig budgetCfg = SimpleBloodClient.getConfig();
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
        com.simpleblood.BloodKind prevKind = currentKind.get();
        currentKind.set(com.simpleblood.BloodKind.LIQUID);
        boolean prevGlow = spawnGlows.get();
        spawnGlows.set((colour & BloodColor.GLOW_BIT) != 0); // glowing blood drips glowing
        boolean prevPaint = spawnPaintsSurfaces.get();
        spawnPaintsSurfaces.set(true);
        runoffDepth.set(depth + 1);
        spawnCarried.set(carried);
        spawnFloorY.set(floorY);
        try {
            // A drop lets go and falls straight down; no sideways kick, no scatter.
            Minecraft.getInstance().particleEngine.createParticle(BloodParticles.BLOOD_DROP,
                    x, y, z, 0.0, -0.02, 0.0);
        } finally {
            runoffDepth.set(depth);
            spawnCarried.set(0);
            spawnFloorY.set(Double.NaN);
            currentKind.set(prevKind);
            spawnGlows.set(prevGlow);
            spawnPaintsSurfaces.set(prevPaint);
            currentBloodColor.set(prev);
            shouldTransformToFog.set(prevFog);
            shouldDespawnInWater.set(prevMelt);
            entitySizeMultiplier.set(prevSize);
        }
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

    // ---------------------------------------------------------------
    // Instance Variables
    // ---------------------------------------------------------------
    private boolean isOnGround = false;
    /**
     * Falling through a hitbox that has nothing visible in it: fences and walls collide half
     * a block above their posts, so a drop that stops there keeps going, without collision,
     * until it meets the wood you can actually see (or leaves the hitbox).
     */
    private boolean ghost;
    /** Age at which a drop that found nothing to paint began soaking away (-1: it has not). */
    private int soakFrom = -1;
    /** Our copy of vanilla's private stoppedByCollision: the fall was stopped, the drop stays put. */
    private boolean stopped;
    private static final int SOAK_TICKS = 6;
    boolean splash;                            // splash particles leave bigger stains than drips
    private final boolean mayPaint;             // this entity's blood may stick to blocks at all
    protected float landingScale = 1f;          // how much blood the drop carries onto the block, relative to a splash
    protected final com.simpleblood.BloodKind kind; // what this particle is made of
    protected final boolean glows;              // drawn at full brightness, and marks the paint it leaves
    private final int chainDepth;               // runoff chain depth this droplet belongs to
    private final int carriedPixels;            // blood volume carried (0 = size based)
    private final double floorY;                // vanish below this height (NaN = none)
    private final float baseRed, baseGreen, baseBlue;
    private final boolean canTransformToFog;
    private final boolean shouldMeltInWater; // Whether this particle despawns when touching water
    private float targetScale; // For smooth expansion

    // ---------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------
    protected BloodParticle(ClientLevel world,
                            double x, double y, double z,
                            double velX, double velY, double velZ,
                            TextureAtlasSprite sprite,
                            float sizeMultiplier,
                            float red, float green, float blue) {
        //? if <=1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(sprite);
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, sprite);
        //?}

        countBirth();
        this.counted = true;
        if (spawnExact.get()) {
            // Thrown blood: fly exactly where aimed, at the speed given.
            this.xd = velX;
            this.yd = velY;
            this.zd = velZ;
        }

        // Get config for lifetime multiplier
        float lifetimeMultiplier = SimpleBloodClient.getConfig().particleLifetimeMultiplier();

        // Jittered lifetime with config multiplier
        int baseLifetime = BASE_LIFE + (int)(world.getRandom().nextFloat() * JITTER * 2) - JITTER;
        this.lifetime = (int)(baseLifetime * lifetimeMultiplier * spawnLifeScale.get());

        // Store base colors for fog transition
        this.baseRed = red;
        this.baseGreen = green;
        this.baseBlue = blue;

        // Store whether this particle can transform to fog (from ThreadLocal)
        this.canTransformToFog = shouldTransformToFog.get();

        // Store whether this particle should despawn in water (from ThreadLocal)
        this.shouldMeltInWater = shouldDespawnInWater.get();
        this.kind = currentKind.get();
        this.glows = spawnGlows.get();
        this.mayPaint = spawnPaintsSurfaces.get();
        this.chainDepth = runoffDepth.get();
        this.carriedPixels = spawnCarried.get();
        this.floorY = spawnFloorY.get();

        // Set color from parameters
        this.setColor(red, green, blue);

        // MORE OPAQUE - particles are now highly visible
        this.alpha = 0.95f;

        // Particle size with config multiplier
        float baseScale = MIN_SCALE + world.getRandom().nextFloat() * (MAX_SCALE - MIN_SCALE);
        this.quadSize = baseScale * sizeMultiplier * entitySizeMultiplier.get();
        this.targetScale = this.quadSize; // Start with no expansion

        // SAFETY FALLBACK: Check if particle spawned underwater
        // Most fog is now spawned directly, but this catches edge cases
        BlockPos spawnPos = BlockPos.containing(x, y, z);
        BlockState spawnState = world.getBlockState(spawnPos);
        if (spawnState.is(Blocks.WATER) && canTransformToFog && SimpleBloodClient.getConfig().underwaterFogEnabled()) {
            // Spawn fog particle and kill this blood particle
            spawnFogParticle();
            this.lifetime = 0;
        } else if (spawnState.is(Blocks.WATER) && shouldMeltInWater) {
            // Snow/ice particles spawned in water should die immediately
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

    // ---------------------------------------------------------------
    // Water Detection
    // ---------------------------------------------------------------
    /**
     * Improved water detection that checks multiple positions to ensure we never miss water.
     * Checks the particle's current position AND the position it will be at after movement.
     * This prevents the one-frame delay that caused particles to glitch.
     */
    private boolean isInWater() {
        // Check current position
        BlockPos currentPos = BlockPos.containing(x, y, z);
        BlockState currentState = level.getBlockState(currentPos);
        if (currentState.is(Blocks.WATER)) {
            return true;
        }

        // Check position after velocity is applied (predictive check)
        // This catches water before we actually move into it
        BlockPos nextPos = BlockPos.containing(x + xd, y + yd, z + zd);
        if (!nextPos.equals(currentPos)) {
            BlockState nextState = level.getBlockState(nextPos);
            if (nextState.is(Blocks.WATER)) {
                return true;
            }
        }

        // Additional check: test the 8 surrounding horizontal positions at current Y level
        // This catches cases where the particle is at a block boundary
        int blockX = currentPos.getX();
        int blockY = currentPos.getY();
        int blockZ = currentPos.getZ();

        // Get fractional position within the block (0.0 to 1.0)
        double fracX = x - blockX;
        double fracZ = z - blockZ;

        // If we're close to a block edge (within 0.1 blocks), check the adjacent block too
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

    // ---------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------
    @Override
    public void tick() {
        // Whatever goes wrong in here (a modded block, another mod's thread) must not take
        // the game down: the drop is dropped and the particles are switched off.
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
        // CRITICAL: Check water BEFORE super.tick() to spawn fog on the exact frame
        // we touch water, not one frame later
        if (!shouldMeltInWater) {
            // Check if particle is entering water
            boolean inWater = isInWater();

            if (inWater && canTransformToFog && SimpleBloodClient.getConfig().underwaterFogEnabled()) {
                // Thrown blood falling in from above (not spilled under water, and not the
                // runoff of a pool draining into it): a faint patter on the surface.
                if (age > 0 && chainDepth == 0) com.simpleblood.BloodSounds.intoWater(level, new Vec3(x, y, z));
                // Spawn fog particle at current position BEFORE we die
                spawnFogParticle();
                // Kill this blood particle (fog replaces it)
                this.remove();
                return;
            }
        }

        // Handle despawn-in-water particles (snow/ice) BEFORE super.tick()
        if (shouldMeltInWater && isInWater()) {
            if (kind == com.simpleblood.BloodKind.EMBER) com.simpleblood.BloodSounds.fizzle(level, new Vec3(x, y, z));
            this.remove();
            return;
        }

        // Remember where we were and where we meant to go, so a blocked move can be
        // traced back to the exact surface (and face) that stopped us.
        double prevX = x, prevY = y, prevZ = z;
        double wantX = xd, wantY = yd, wantZ = zd;

        // Born inside a hitbox (a drop letting go under a fence bar, blood from a mob standing
        // in one): the physics would pin it there, so it starts out ghosting.
        if (age == 0 && !ghost && insideCollision(x, y, z)) ghost = true;

        if (ghost) {
            // Inside a phantom hitbox: move freely, and look for the visible surface ourselves.
            this.xo = x; this.yo = y; this.zo = z;
            if (this.age++ >= this.lifetime) { this.remove(); return; }
            this.setPos(x + xd, y + yd, z + zd);
            boolean paints = SimpleBloodClient.getConfig().surfaces.enabled && kind.paintsSurfaces() && mayPaint;
            if (paints && depositOnSurface(prevX, prevY, prevZ, wantX, wantY, wantZ, false)) {
                this.remove();
                return;
            }
            if (!insideCollision(x, y, z)) ghost = false; // out the other side: collide normally again
        } else {
            super.tick(); // position += velocity, age++
        }
        if (this.removed) return;

        // Blood released from a broken block only travels so far before it is gone.
        if (!Double.isNaN(floorY) && y < floorY) {
            this.remove();
            return;
        }

        // Regular blood particle behavior
        // Get config multipliers
        float gravityMult = SimpleBloodClient.getConfig().particleGravityMultiplier();
        float dragMult = SimpleBloodClient.getConfig().particleDragMultiplier();

        // Apply gravity with config multiplier
        yd -= BASE_GRAVITY * gravityMult;

        // Apply drag - use much stronger water drag if in water and fog is disabled
        float dragMultiplier = BASE_DRAG * dragMult; // Default air drag

        if (isInWater() && (!canTransformToFog || !SimpleBloodClient.getConfig().underwaterFogEnabled())) {
            // Particles slow down MUCH more in water when:
            // 1. They cannot transform to fog (like bone dust, debris), OR
            // 2. Underwater fog is disabled in config
            // This simulates particles moving slowly through water
            dragMultiplier = 0.70f; // Very strong drag underwater
        }

        xd *= dragMultiplier;
        yd *= dragMultiplier;
        zd *= dragMultiplier;

        // Did a block stop us on any axis? Compare the move we got with the move we wanted.
        boolean blocked = Math.abs((x - prevX) - wantX) > 1.0e-6
                || Math.abs((y - prevY) - wantY) > 1.0e-6
                || Math.abs((z - prevZ) - wantZ) > 1.0e-6;

        // Plants and other cross-model blocks have no hitbox, so the move never stops there;
        // when we enter such a block, check whether we passed through its leaves.
        // Signs and banners have no hitbox either, and their boards and cloth reach into the
        // blocks above or below, so those are checked every tick, from next door too.
        if (!blocked && !isOnGround && kind.paintsSurfaces() && mayPaint && SimpleBloodClient.getConfig().surfaces.enabled) {
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
                thin = entered && BloodSurfaces.isCrossModel(hereState) && !BloodSurfaces.sways(hereState);
            }
            if (thin && depositOnSurface(prevX, prevY, prevZ, wantX, wantY, wantZ, false)) {
                this.remove();
                return;
            }
        }

        if (blocked && !isOnGround) {
            // Debris (golems, skeletons) and embers never paint surfaces; liquid blood and
            // snow powder do.
            boolean paints = SimpleBloodClient.getConfig().surfaces.enabled && kind.paintsSurfaces() && mayPaint;

            if (paints && depositOnSurface(prevX, prevY, prevZ, wantX, wantY, wantZ, true)) {
                // The surface engine now owns this blood; the droplet is gone.
                this.remove();
                return;
            }
            // Stopped by a hitbox with nothing to see at the point of contact (the empty half
            // block above a fence or wall post): pretend it is not there and carry on.
            if (paints && phantomContact(prevX, prevY, prevZ, wantX, wantY, wantZ)) {
                ghost = true;
                stopped = false;
                xd = wantX; yd = wantY; zd = wantZ;
                this.setPos(prevX + wantX, prevY + wantY, prevZ + wantZ);
                return;
            }
            if (onGround) {
                // No surface blood: come to rest and fade where we landed.
                isOnGround = true;
                xd = 0;
                yd = 0;
                zd = 0;
                // With surface blood on, a drop that found nothing it can paint (a lectern, a
                // block out of range) does not lie about as a flat sprite: it soaks away.
                if (paints && soakFrom < 0) soakFrom = age;
            }
        }

        // Check if ground beneath particle was removed (every 10 ticks for performance)
        // Only needed when the particle came to rest instead of painting.
        if (isOnGround && age % 10 == 0) {
            BlockPos posBelow = BlockPos.containing(x, y - 0.1, z);
            boolean hasBlockBelow = !level.getBlockState(posBelow).isAir();

            if (!hasBlockBelow) {
                // Block was removed - start falling again
                isOnGround = false;
                stopped = false;
                // Add small downward velocity to start falling
                yd = -0.1;
            }
        }

        // Smoothly expand towards target scale when on ground
        if (isOnGround && this.quadSize < targetScale) {
            this.quadSize += (targetScale - this.quadSize) * 0.15f; // 15% per tick = quick expansion
        }

        if (soakFrom >= 0) {
            float left = 1.0f - (age - soakFrom) / (float) SOAK_TICKS;
            if (left <= 0f) {
                this.remove();
                return;
            }
            this.alpha = Math.min(this.alpha, 0.95f * left);
            return;
        }

        // Fade out over the last 25% of lifetime - MORE OPAQUE
        float lifeFraction = 1.0f - (float) age / lifetime;
        if (lifeFraction < 0.25f) {
            this.alpha = 0.95f * (lifeFraction / 0.25f);
        }
    }

    /**
     * Whether the block that just stopped us has no visible shape where we touched it: its
     * collision box covers more than is drawn. {@link #move} already ignores the half block
     * fences and walls collide above their posts; this catches the rest, such as the gaps
     * between a fence's bars.
     */
    private boolean phantomContact(double fromX, double fromY, double fromZ, double wantX, double wantY, double wantZ) {
        double len = Math.sqrt(wantX * wantX + wantY * wantY + wantZ * wantZ);
        if (len > 1.0e-6) {
            // A hair past where the move would have ended: inside whatever stopped us.
            double s = (len + 0.03) / len;
            double cx = fromX + wantX * s, cy = fromY + wantY * s, cz = fromZ + wantZ * s;
            for (net.minecraft.world.phys.AABB b : solidBoxes(BlockPos.containing(cx, cy, cz))) {
                // Something visible really there? For a fence that is its wood, not the gap between its bars.
                if (b.inflate(0.002).contains(cx, cy, cz)) return !BloodSurfaces.visibleSolidAt(level, cx, cy, cz);
            }
        }
        // The centre's path missed the hitbox: the droplet's box clipped it from the side or
        // came down on its rim (an angled drop onto the top of a fence's hitbox). Judge by
        // where the box actually touches it.
        Vec3 p = contactPoint();
        return p != null && !BloodSurfaces.visibleSolidAt(level, p.x, p.y, p.z);
    }

    /**
     * The surface the droplet's box touches when the line along its move misses: the box
     * clipped a corner or an edge while the centre passed beside it (the rim of a block, the
     * top of a fence post). Traces from the centre to the {@link #contactPoint}, so the blood
     * lands on that edge. Null if nothing drawn is there.
     */
    private BloodSurfaces.Hit traceToContact() {
        Vec3 end = contactPoint();
        if (end == null) return null;
        Vec3 centre = new Vec3(x, y, z);
        Vec3 toEnd = end.subtract(centre);
        // Centre already inside the box: whatever is under it.
        if (toEnd.lengthSqr() < 1.0e-8) return BloodSurfaces.trace(level, centre.add(0, 0.1, 0), centre.add(0, -0.1, 0));
        Vec3 dir = toEnd.normalize();
        return BloodSurfaces.trace(level, end.subtract(dir.scale(0.15)), end.add(dir.scale(0.02)));
    }

    /**
     * Where the droplet's box touches a block: the nearest point on the collision boxes it
     * touches, a hair inside that box (the nearest point often lies right on an edge, where a
     * line only grazes the block), or the centre itself when that is inside one. Null if the
     * droplet touches nothing.
     */
    private Vec3 contactPoint() {
        net.minecraft.world.phys.AABB box = getBoundingBox().inflate(0.05);
        Vec3 centre = new Vec3(x, y, z);
        Vec3 best = null, bestMid = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                net.minecraft.util.Mth.floor(box.minX), net.minecraft.util.Mth.floor(box.minY), net.minecraft.util.Mth.floor(box.minZ),
                net.minecraft.util.Mth.floor(box.maxX), net.minecraft.util.Mth.floor(box.maxY), net.minecraft.util.Mth.floor(box.maxZ))) {
            for (net.minecraft.world.phys.AABB w : solidBoxes(pos)) {
                if (!w.intersects(box)) continue;
                Vec3 q = new Vec3(Math.max(w.minX, Math.min(w.maxX, x)),
                        Math.max(w.minY, Math.min(w.maxY, y)),
                        Math.max(w.minZ, Math.min(w.maxZ, z)));
                double d = q.distanceToSqr(centre);
                if (d < bestDist) { bestDist = d; best = q; bestMid = w.getCenter(); }
            }
        }
        if (best == null) return null;
        if (bestDist < 1.0e-8) return centre;
        Vec3 inward = bestMid.subtract(best);
        return inward.lengthSqr() < 1.0e-8 ? best : best.add(inward.normalize().scale(0.02));
    }

    /** Whether a point lies inside any block collision box (as {@link #solidBoxes} cuts them). */
    private boolean insideCollision(double px, double py, double pz) {
        for (net.minecraft.world.phys.AABB b : solidBoxes(BlockPos.containing(px, py, pz))) {
            if (b.inflate(0.002).contains(px, py, pz)) return true;
        }
        return false;
    }

    /**
     * A block's collision boxes in world space, cut down to the height it is drawn: fences,
     * walls and closed gates collide half a block above their posts, an invisible ledge blood
     * would otherwise land and freeze on. Everything else keeps its collision as it is.
     */
    private java.util.List<net.minecraft.world.phys.AABB> solidBoxes(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Air, and blocks that take no blood (leaves, lanterns...): drops fall on through.
        if (state.isAir() || BloodSurfaces.sways(state)) return java.util.List.of();
        net.minecraft.world.phys.shapes.VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return java.util.List.of();
        net.minecraft.world.phys.shapes.VoxelShape drawn = state.getShape(level, pos);
        double top = drawn.isEmpty() ? Double.MAX_VALUE : drawn.max(net.minecraft.core.Direction.Axis.Y);
        java.util.List<net.minecraft.world.phys.AABB> out = new java.util.ArrayList<>();
        for (net.minecraft.world.phys.AABB b : collision.toAabbs()) {
            if (b.minY >= top) continue;
            out.add(new net.minecraft.world.phys.AABB(
                    b.minX + pos.getX(), b.minY + pos.getY(), b.minZ + pos.getZ(),
                    b.maxX + pos.getX(), Math.min(b.maxY, top) + pos.getY(), b.maxZ + pos.getZ()));
        }
        return out;
    }

    /** Vanilla's particle move, colliding with {@link #solidBoxes} instead of the full hitboxes. */
    @Override
    public void move(double dx, double dy, double dz) {
        if (stopped) return; // vanilla freezes a particle once something stops its fall; so do we
        double ox = dx, oy = dy, oz = dz;
        if (this.hasPhysics && (dx != 0 || dy != 0 || dz != 0) && dx * dx + dy * dy + dz * dz < 10000.0) {
            net.minecraft.world.phys.AABB box = getBoundingBox();
            net.minecraft.world.phys.AABB swept = box.expandTowards(dx, dy, dz).inflate(1.0e-7);
            java.util.List<net.minecraft.world.phys.AABB> solids = new java.util.ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(
                    net.minecraft.util.Mth.floor(swept.minX), net.minecraft.util.Mth.floor(swept.minY) - 1, net.minecraft.util.Mth.floor(swept.minZ),
                    net.minecraft.util.Mth.floor(swept.maxX), net.minecraft.util.Mth.floor(swept.maxY), net.minecraft.util.Mth.floor(swept.maxZ))) {
                for (net.minecraft.world.phys.AABB b : solidBoxes(pos)) {
                    if (b.intersects(swept)) solids.add(b);
                }
            }
            // Same order as vanilla: Y first, then the larger horizontal axis.
            dy = clip(solids, box, 1, dy);
            box = box.move(0, dy, 0);
            if (Math.abs(dx) < Math.abs(dz)) {
                dz = clip(solids, box, 2, dz);
                box = box.move(0, 0, dz);
                dx = clip(solids, box, 0, dx);
            } else {
                dx = clip(solids, box, 0, dx);
                box = box.move(dx, 0, 0);
                dz = clip(solids, box, 2, dz);
            }
        }
        if (dx != 0 || dy != 0 || dz != 0) {
            setBoundingBox(getBoundingBox().move(dx, dy, dz));
            setLocationFromBoundingbox();
        }
        if (Math.abs(oy) >= 1.0e-5 && Math.abs(dy) < 1.0e-5) stopped = true;
        this.onGround = oy != dy && oy < 0;
        if (ox != dx) this.xd = 0;
        if (oz != dz) this.zd = 0;
    }

    /** How far {@code box} can move along an axis (0 x, 1 y, 2 z) before it meets a solid box. */
    private static double clip(java.util.List<net.minecraft.world.phys.AABB> solids, net.minecraft.world.phys.AABB box, int axis, double d) {
        if (d == 0) return 0;
        final double eps = 1.0e-7;
        for (net.minecraft.world.phys.AABB s : solids) {
            boolean overlapX = s.maxX > box.minX + eps && s.minX < box.maxX - eps;
            boolean overlapY = s.maxY > box.minY + eps && s.minY < box.maxY - eps;
            boolean overlapZ = s.maxZ > box.minZ + eps && s.minZ < box.maxZ - eps;
            switch (axis) {
                case 0 -> {
                    if (!overlapY || !overlapZ) continue;
                    if (d > 0 && s.minX >= box.maxX - eps) d = Math.min(d, s.minX - box.maxX);
                    else if (d < 0 && s.maxX <= box.minX + eps) d = Math.max(d, s.maxX - box.minX);
                }
                case 1 -> {
                    if (!overlapX || !overlapZ) continue;
                    if (d > 0 && s.minY >= box.maxY - eps) d = Math.min(d, s.minY - box.maxY);
                    else if (d < 0 && s.maxY <= box.minY + eps) d = Math.max(d, s.maxY - box.minY);
                }
                default -> {
                    if (!overlapX || !overlapY) continue;
                    if (d > 0 && s.minZ >= box.maxZ - eps) d = Math.min(d, s.minZ - box.maxZ);
                    else if (d < 0 && s.maxZ <= box.minZ + eps) d = Math.max(d, s.maxZ - box.minZ);
                }
            }
        }
        // Never move backwards (a box starting a hair inside another).
        return d > 0 ? Math.max(0, d) : Math.min(0, d);
    }

    /**
     * Spawn a new blood fog particle at the current position.
     * This replaces the old transformation system with a separate fog particle.
     * NOTE: This is now mainly used as a fallback for particles that enter water after spawning.
     * Most underwater fog is spawned directly by the spawning code.
     */
    private void spawnFogParticle() {
        double px = x, py = y, pz = z, vx = xd, vy = yd, vz = zd;
        // The particle engine is fed from the render thread only (this tick may be on a
        // worker thread under AsyncParticles).
        RenderThread.run(() -> {
            // Set the blood color for the fog particle
            com.simpleblood.particle.BloodFogParticle.setCurrentBloodColor(
                    new BloodColor.Color(baseRed, baseGreen, baseBlue)
            );
            com.simpleblood.particle.BloodFogParticle.setGlowing(glows);

            // Drops falling into water gather into a few big clouds, like blood spilled under it.
            com.simpleblood.ClientBloodParticleSpawner.mergedFog(px, py, pz, vx, vy, vz);
        });
    }

    /**
     * Hands this droplet's blood to the surface engine. Traces the intended move (extended by
     * the particle's own size, since the bounding box stops before the centre reaches the
     * face) to find the exact block face and point of impact.
     *
     * @param touching a block stopped the move, so the droplet's box is touching a surface
     *                 even when the line along the move misses it
     * @return true when the blood was painted onto a surface
     */
    private boolean depositOnSurface(double fromX, double fromY, double fromZ,
                                     double wantX, double wantY, double wantZ, boolean touching) {
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
            // Landed but the ray missed (e.g. resting on an edge): treat the block below as hit.
            hit = BloodSurfaces.trace(level, new Vec3(x, y + 0.05, z), new Vec3(x, y - 0.2, z));
        }
        if (hit == null && touching) hit = traceToContact();
        if (hit == null) return false;

        int colour = ((int) (baseRed * 255) << 16) | ((int) (baseGreen * 255) << 8) | (int) (baseBlue * 255);
        if (glows) colour |= BloodColor.GLOW_BIT; // the puddle glows too, while it is fresh
        float amount = SimpleBloodClient.getConfig().surfaceAmount();
        int pixels;
        if (carriedPixels > 0) {
            pixels = carriedPixels; // blood that fell off a broken block keeps its volume
        } else {
            // The particle's own random: the level's one is not for sharing between threads.
            int base = 1 + random.nextInt(2) + (quadSize > 0.22f ? 1 : 0);
            if (splash) base += 2 + random.nextInt(2); // splashes spread out on impact
            // Finer canvases need more pixels for the same painted area (16 px: about 3x).
            int res = SimpleBloodClient.getConfig().surfaces.resolution;
            float areaScale = res >= 16 ? 3.0f : 1.0f;
            pixels = Math.max(amount > 0 ? 1 : 0, Math.round(base * amount * areaScale * landingScale));
            // Runoff droplets carry less, so a cascade down stairs thins out.
            if (chainDepth > 0) pixels = Math.max(1, pixels - chainDepth);
        }

        Vec3 travel = new Vec3(wantX, wantY, wantZ);
        boolean powder = kind == com.simpleblood.BloodKind.POWDER;
        BloodSurfaces.Hit landed = hit;
        if (!RenderThread.on()) {
            // Ticked on a worker thread (AsyncParticles): the paint and its sound wait for the
            // render thread, and the drop is handed over now.
            BloodSurfaces.depositLater(level, landed, colour, pixels, travel, powder, result -> landedSound(landed, result));
            return true;
        }
        int result = BloodSurfaces.deposit(level, hit, colour, pixels, travel, powder);
        landedSound(hit, result);
        // Nothing painted (a lectern, a block out of range): the drop is still ours and
        // comes to rest where it landed.
        return result != BloodSurfaces.NOTHING;
    }

    /** Thrown blood taps or plinks where it lands; blood running off a ledge drips. */
    private void landedSound(BloodSurfaces.Hit hit, int result) {
        if (result == BloodSurfaces.NOTHING) return;
        boolean intoPuddle = result == BloodSurfaces.INTO_PUDDLE;
        if (chainDepth == 0) com.simpleblood.BloodSounds.landed(level, hit.point(), intoPuddle, splash, kind);
        else com.simpleblood.BloodSounds.drip(level, hit.point(), intoPuddle);
    }

    // ---------------------------------------------------------------
    // Lighting: embers and glowing blood light themselves
    // ---------------------------------------------------------------
    //? if <26.1 {
    /*@Override
    protected int getLightColor(float partialTick) {
        return kind == com.simpleblood.BloodKind.EMBER || glows ? 0xF000F0 : super.getLightColor(partialTick);
    }
    *///?} else {
    @Override
    protected int getLightCoords(float partialTick) {
        return kind == com.simpleblood.BloodKind.EMBER || glows ? 0xF000F0 : super.getLightCoords(partialTick);
    }
    //?}

    // ---------------------------------------------------------------
    // Render type
    // ---------------------------------------------------------------
    //? if <=1.21.1 {
    /*@Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?} else {
    @Override
    protected Layer getLayer() {
        // Using TRANSLUCENT for all particles; fog particles are larger and more opaque
        // to improve visibility through the water surface.
        return Layer.TRANSLUCENT;
    }
    //?}

    // ---------------------------------------------------------------
    // Factory
    // ---------------------------------------------------------------
    /**
     * Registered via ParticleFactoryRegistry.getInstance().register().
     *
     * Uses SpriteProvider to randomly select from all available textures
     * defined in the particle JSON file, creating visual variety.
     */
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
            if (!Guard.ok(Guard.Part.PARTICLES)) return null;
            try {
                float sizeMultiplier = SimpleBloodClient.getConfig().particleSizeMultiplier();
                BloodColor.Color color = currentBloodColor.get();

                // Select a sprite at random for visual variety.
                //? if <=1.21.1 {
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