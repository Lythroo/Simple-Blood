package com.simpleblood;

import com.simpleblood.BloodColor.Color;
import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Public API for other mods to control how Simple Blood behaves for their entities.
 *
 * Everything registered here takes priority over the JSON config and the built-in
 * defaults. All fields on {@link BloodSettings} are optional - anything you don't set
 * falls through to the config/defaults, so you only override what you care about.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // In your CLIENT initializer:
 * SimpleBloodAPI.registerEntityBlood(MyEntities.GRIZZLY_BEAR, new SimpleBloodAPI.BloodSettings()
 *         .setColor(0x8B0000)
 *         .setParticleSizeMultiplier(1.4f)
 *         .setBurstIntensityMultiplier(1.5f));
 *
 * // A sap-bleeding tree creature that leaves no stains and never drips:
 * SimpleBloodAPI.registerEntityBlood("mymod:treant", new SimpleBloodAPI.BloodSettings()
 *         .setColor(0x5B3A1A)
 *         .setCanDripAtLowHealth(false)
 *         .setTransformToStains(false));
 *
 * // A slime whose blood colour follows its own tint, dynamically:
 * SimpleBloodAPI.registerEntityBlood("mymod:rainbow_slime", new SimpleBloodAPI.BloodSettings()
 *         .setColorProvider(e -> ((MyRainbowSlime) e).getTintRGB()));
 *
 * // A water elemental that DOES bleed when it dries out (opt out of the default
 * // suffocation/drowning/dry-out suppression):
 * SimpleBloodAPI.registerEntityBlood("mymod:water_elemental", new SimpleBloodAPI.BloodSettings()
 *         .setBleedWhenAsphyxiating(true));
 *
 * // Full control over which hits bleed:
 * SimpleBloodAPI.registerEntityBlood("mymod:golem", new SimpleBloodAPI.BloodSettings()
 *         .setBleedPredicate((entity, source) -> !source.is(DamageTypes.FALL)));
 *
 * // Globally stop fire/lava damage from ever producing blood:
 * SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.IN_FIRE);
 * SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.LAVA);
 *
 * // 1.0.0: what comes out (liquid, debris, embers, snow powder), and whether feet leave prints:
 * SimpleBloodAPI.registerEntityBlood("mymod:clockwork_knight", new SimpleBloodAPI.BloodSettings()
 *         .setBloodKind(BloodKind.DEBRIS)
 *         .setLeavesFootprints(false));
 *
 * // 1.0.0: trigger the effects yourself (client side, render thread):
 * SimpleBloodAPI.spawnHit(entity, 6.0f);                        // a burst as if hit for 6 damage
 * SimpleBloodAPI.spawnDeath(entity);                            // the death burst
 * SimpleBloodAPI.paint(level, from, to, 0x8B0000, 12);          // blood on the first block face along a line
 *
 * // 1.2.0: armour, metal bodies, blood on bodies, pools, ragdolls:
 * SimpleBloodAPI.registerEntityBlood("mymod:steam_knight", new SimpleBloodAPI.BloodSettings()
 *         .setMetalBody(true));                                 // swords spark off it, with a clank
 * SimpleBloodAPI.registerHardItem("mymod:mithril_chestplate", 0xD8E6F0); // sparks and pale shards
 * SimpleBloodAPI.registerSoftItem("mymod:chainmail_robe");      // cloth, despite its name
 * SimpleBloodAPI.registerRagdollSource("mymod", (level, out) -> MyRagdolls.forEach(r -> out.accept(r.mobId(), r.limbs())));
 * SimpleBloodAPI.spawnSparks(level, at, towardsPlayer);         // sparks and a clank
 * SimpleBloodAPI.stainBody(entity, at, direction, 0x8B0000);    // blood stays on its body
 * SimpleBloodAPI.spawnPool(entity);                             // a pool spreads under it
 *
 * // 1.3.0: per-mob throw, your own explosions, your own moving block structures:
 * SimpleBloodAPI.registerEntityBlood("mymod:giant", new SimpleBloodAPI.BloodSettings()
 *         .setThrowSpeedMultiplier(1.6f)                        // its drops fly harder...
 *         .setThrowUpMultiplier(0.5f));                         // ...and lower
 * SimpleBloodAPI.spawnExplosion(level, centre, 4.0f);           // a client-side blast of your own
 * SimpleBloodAPI.registerMovingStructures("mymod", myShips);    // blood rides on your ships
 * }</pre>
 *
 * <p>Everything here is client side: call it from your client initializer or client code.
 */
public final class SimpleBloodAPI {

    private SimpleBloodAPI() {}

    /** entity id (e.g. {@code "mymod:custom_mob"}) → settings. */
    // Other mods register from their own init and we read from particle threads: concurrent maps.
    private static final Map<String, BloodSettings> customBloodRegistry = new java.util.concurrent.ConcurrentHashMap<>();

    /** Extra damage types (beyond the built-in drown/suffocation/dry-out) that never bleed. */
    private static final Set<ResourceKey<DamageType>> nonBleedingDamageTypes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ═══════════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════

    /** Register settings for an entity by its {@link EntityType} (recommended). */
    public static void registerEntityBlood(EntityType<?> entityType, BloodSettings settings) {
        registerEntityBlood(BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString(), settings);
    }

    /** Register settings for an entity by identifier. */
    public static void registerEntityBlood(Identifier entityId, BloodSettings settings) {
        registerEntityBlood(entityId.toString(), settings);
    }

    /** Register settings for an entity by string identifier, e.g. {@code "mymod:custom_mob"}. */
    public static void registerEntityBlood(String entityId, BloodSettings settings) {
        if (entityId == null || settings == null) return;
        customBloodRegistry.put(entityId, settings);
        SimpleBlood.LOGGER.info("Registered custom blood settings for entity: {}", entityId);
    }

    /** @return settings for this entity id, or {@code null} if none are registered. */
    public static BloodSettings getEntityBloodSettings(String entityId) {
        return entityId == null ? null : customBloodRegistry.get(entityId);
    }

    /** @return settings for this entity instance, or {@code null} if none are registered. */
    public static BloodSettings getEntityBloodSettings(LivingEntity entity) {
        return customBloodRegistry.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    /** @return {@code true} if custom settings exist for this entity id. */
    public static boolean hasCustomSettings(String entityId) {
        return entityId != null && customBloodRegistry.containsKey(entityId);
    }

    /** Remove the settings registered for an entity id. */
    public static void unregisterEntityBlood(String entityId) {
        if (entityId == null) return;
        customBloodRegistry.remove(entityId);
        SimpleBlood.LOGGER.info("Unregistered custom blood settings for entity: {}", entityId);
    }

    /** Remove all registered settings (mostly for testing / reload). */
    public static void clearAllRegistrations() {
        customBloodRegistry.clear();
        SimpleBlood.LOGGER.info("Cleared all custom blood registrations");
    }

    /** @return an unmodifiable view of every registered entity id. */
    public static Set<String> getRegisteredEntities() {
        return Collections.unmodifiableSet(customBloodRegistry.keySet());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GLOBAL DAMAGE-TYPE SUPPRESSION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Mark a damage type so it never produces blood for any entity (e.g. fire, magic).
     * The built-in drown / suffocation / dry-out types are always suppressed on top of
     * whatever you add here (unless an entity opts in via {@code setBleedWhenAsphyxiating}
     * or a {@code bleedPredicate}).
     */
    public static void addNonBleedingDamageType(ResourceKey<DamageType> type) {
        if (type != null) nonBleedingDamageTypes.add(type);
    }

    /** Stop suppressing blood for a damage type previously added via {@link #addNonBleedingDamageType}. */
    public static void removeNonBleedingDamageType(ResourceKey<DamageType> type) {
        if (type != null) nonBleedingDamageTypes.remove(type);
    }

    /** @return {@code true} if this damage type is a developer-registered non-bleeding one. */
    public static boolean isNonBleedingDamageType(net.minecraft.core.Holder<DamageType> type) {
        for (ResourceKey<DamageType> key : nonBleedingDamageTypes) {
            if (type.is(key)) return true;
        }
        return false;
    }

    /** @return {@code true} if this source matches a developer-registered non-bleeding damage type. */
    public static boolean isNonBleedingDamageType(DamageSource source) {
        if (nonBleedingDamageTypes.isEmpty()) return false;
        for (ResourceKey<DamageType> key : nonBleedingDamageTypes) {
            if (source.is(key)) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EFFECTS ON DEMAND (client side; call on the render thread)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Plays a hit burst on an entity as if it had just taken {@code damage}: same colour,
     * kind, size and puddles as a real hit, without the entity being hurt. Does nothing on
     * the server, for a dead entity, or when the mod is disabled.
     *
     * @return whether a burst was started
     */
    public static boolean spawnHit(LivingEntity entity, float damage) {
        return Guard.call(Guard.Part.API, "spawnHit", () -> {
            if (entity == null || !(entity.level() instanceof ClientLevel level) || entity.isDeadOrDying() || damage <= 0) return false;
            SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
            if (cfg == null || !cfg.globalEnabled() || !SimpleBlood.shouldEntityBleed(entity)) return false;
            SimpleBloodClient.addBurstTask(new ClientBloodBurstTask(level, entity, damage));
            return true;
        }, false);
    }

    /** Plays the death burst on an entity (client side). Returns whether it did. Any thread. */
    public static boolean spawnDeath(LivingEntity entity) {
        return Guard.call(Guard.Part.API, "spawnDeath", () -> {
            if (entity == null || !(entity.level() instanceof ClientLevel level)) return false;
            SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
            if (cfg == null || !cfg.globalEnabled() || !SimpleBlood.shouldEntityBleed(entity)) return false;
            RenderThread.run(() -> Guard.run(Guard.Part.API, "spawnDeath", () -> ClientBloodParticleSpawner.spawnBloodOnDeath(level, entity)));
            return true;
        }, false);
    }

    /**
     * Paints blood on the first block face met along the line {@code from -> to}, the way a
     * landing drop would (it then spreads, runs down sides and dries like any puddle).
     * {@code pixels} is roughly how many canvas pixels to lay down (a drop leaves 1-3, a
     * splash 3-6, at 16 px per block edge). Respects the "Puddles" settings. Client side.
     *
     * @return whether anything was painted
     */
    public static boolean paint(Level level, Vec3 from, Vec3 to, int rgb, int pixels) {
        return Guard.call(Guard.Part.API, "paint", () -> {
            if (!(level instanceof ClientLevel client) || from == null || to == null) return false;
            BloodSurfaces.Hit hit = BloodSurfaces.trace(client, from, to);
            return hit != null && BloodSurfaces.deposit(client, hit, rgb & 0xFFFFFF, pixels, to.subtract(from)) != BloodSurfaces.NOTHING;
        }, false);
    }

    /**
     * 1.2.0: sparks struck off metal at {@code at}, flying towards {@code towards} (a direction;
     * null = up), with the metal clank. Respects the player's Armor settings. Client side.
     */
    public static void spawnSparks(Level level, Vec3 at, Vec3 towards) {
        Guard.run(Guard.Part.API, "spawnSparks", () -> {
            if (level instanceof ClientLevel client && at != null) RenderThread.run(() -> ArmorSparks.strike(client, at, towards, false));
        });
    }

    /**
     * 1.2.0: blood landing on a mob's body at {@code at} (world position on or near it),
     * travelling along {@code direction}, in colour {@code rgb}: it stays on the body like a
     * drop thrown onto it ("Blood on mobs" must be on). Client side.
     */
    public static void stainBody(LivingEntity entity, Vec3 at, Vec3 direction, int rgb) {
        Guard.run(Guard.Part.API, "stainBody", () -> {
            if (entity == null || at == null) return;
            Vec3 dir = direction == null || direction.lengthSqr() < 1.0e-8 ? new Vec3(0, -1, 0) : direction.normalize();
            int colour = (rgb & 0xFFFFFF) == 0 ? 0x010101 : rgb & 0xFFFFFF;
            RenderThread.run(() -> com.simpleblood.mob.MobBlood.landed(entity, at, dir, colour, 1.2f));
        });
    }

    /**
     * 1.2.0: a pool of the mob's blood spreading on the ground under it, as under a body that
     * has just died ("Pool under the body" must be on). Client side.
     */
    public static void spawnPool(LivingEntity entity) {
        Guard.run(Guard.Part.API, "spawnPool", () -> {
            if (entity != null) RenderThread.run(() -> com.simpleblood.mob.MobBlood.poolNow(entity));
        });
    }

    /** Removes all blood from blocks (what {@code /simpleblood clear} does). Any thread. */
    public static void clearSurfaceBlood() {
        Guard.run(Guard.Part.API, "clearSurfaceBlood", BloodSurfaces::clear);
    }

    /** How many block faces currently hold blood. */
    public static int surfaceBloodCount() {
        return BloodSurfaces.tileCount();
    }

    /**
     * 1.3.0: an explosion went off at {@code centre} with power {@code radius} (TNT is 4, a
     * creeper 3), for a blast of your own that the client never hears about from the server.
     * Mobs it hurts in the next few ticks throw their blood away from it (harder the nearer
     * they stood) and get blood on the side that faced it, and a creeper lit or about to blow
     * right at {@code centre} that is gone from the level within a few ticks (the server
     * removed it: it blew up) bursts in its own blood.
     *
     * <p>Not needed for vanilla-style explosions ({@code Level.explode} on the server, which
     * sends the explode packet): Simple Blood sees those itself. Call it on the client before
     * the hurt mobs' health drops arrive, or in the same tick. Does nothing on a server level
     * or when Simple Blood is switched off. Any thread (the work is done on the render thread).
     */
    public static void spawnExplosion(Level level, Vec3 centre, float radius) {
        Guard.run(Guard.Part.API, "spawnExplosion", () -> {
            if (!(level instanceof ClientLevel client) || centre == null || !(radius > 0)) return;
            SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
            if (cfg == null || !cfg.globalEnabled()) return;
            RenderThread.run(() -> Guard.run(Guard.Part.API, "spawnExplosion", () -> {
                Blasts.heard(client, centre, radius);
                CreeperBlast.heard(client, centre, radius);
            }));
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ARMOUR AND WEAPONS (1.2.0)
    // ═══════════════════════════════════════════════════════════════════════

    /** item id → shard colour of a hard item, or -1 for one that is soft. */
    private static final Map<String, Integer> itemHardness = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * An item that is hard metal: armour of it takes blows (sparks, shards in {@code flakeRgb},
     * a clank, less blood), and a weapon of it strikes sparks off hard armour. Simple Blood
     * guesses from item ids ("iron", "steel", "plate"...); this overrides the guess.
     */
    public static void registerHardItem(String itemId, int flakeRgb) {
        itemHardness.put(itemId, flakeRgb & 0xFFFFFF);
    }

    /** An item that is soft (cloth, leather, wood), whatever its id sounds like. */
    public static void registerSoftItem(String itemId) {
        itemHardness.put(itemId, -1);
    }

    /** Back to Simple Blood's own guess for this item. */
    public static void unregisterItem(String itemId) {
        itemHardness.remove(itemId);
    }

    /** Internal: a registered item's shard colour, -1 if registered soft, null if not registered. */
    public static Integer itemHardness(String itemId) {
        return itemHardness.get(itemId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RAGDOLLS (1.2.0)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Ragdolls from your mod, so they bleed like Physics Mod's: limbs drip, splash and stain
     * where they land hard, and smear the ground when they slide. Called every client tick.
     */
    @FunctionalInterface
    public interface RagdollSource {
        /**
         * Hand each ragdoll to {@code out}: an id that stays the same while it exists, and the
         * world position of each of its limbs this tick (null for a missing limb). If the id is
         * a living entity's, the ragdoll bleeds that entity's blood; otherwise it takes the
         * blood of a mob that just died where it appeared.
         */
        void collect(ClientLevel level, java.util.function.BiConsumer<Integer, Vec3[]> out);
    }

    private static final Map<String, RagdollSource> ragdollSources = new java.util.concurrent.ConcurrentHashMap<>();

    /** Registers (or replaces) a ragdoll source under a name of your choice, e.g. your mod id. */
    public static void registerRagdollSource(String name, RagdollSource source) {
        ragdollSources.put(name, source);
    }

    public static void unregisterRagdollSource(String name) {
        ragdollSources.remove(name);
    }

    /** Internal: every registered ragdoll source, by name. */
    public static Map<String, RagdollSource> ragdollSources() {
        return ragdollSources;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MOVING BLOCK STRUCTURES (1.3.0)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 1.3.0: block structures from your mod that move as one body (ships, flying machines,
     * moving platforms), so blood on them rides along exactly as it does on Create
     * contraptions and Sable (Aeronautics) structures: it is drawn where the structure is, it
     * runs and drips the way gravity falls across the structure as it tilts, drops that fly
     * into it land on it, and blood that lets go of it keeps its motion.
     *
     * <p>It is for structures whose blocks really are in the client's level, somewhere out of
     * the way (a "plot", like a Valkyrien Skies shipyard or a Sable sub-level), and are drawn
     * where the structure is through a pose. Blood is painted on those plot blocks and drawn
     * through the pose you give for them.
     *
     * <p>Simple Blood asks a source often (every flying drop, every blood-covered face every
     * frame), sometimes off the render thread (with AsyncParticles), so answer quickly and
     * thread-safely, and return null or an empty list as soon as you can tell a position or
     * move has nothing to do with your structures. A source that throws, or hands out a pose
     * that throws, is switched off with a line in the log; the game carries on.
     *
     * <pre>{@code
     * SimpleBloodAPI.registerMovingStructures("mymod", new SimpleBloodAPI.MovingStructures() {
     *     public List<? extends SimpleBloodAPI.MovingStructures.Pose> crossing(ClientLevel level, Vec3 from, Vec3 to) {
     *         List<SimpleBloodAPI.MovingStructures.Pose> out = new ArrayList<>();
     *         for (Ship ship : MyShips.near(level, from, to)) out.add(poseOf(ship, 1f));
     *         return out;
     *     }
     *     public SimpleBloodAPI.MovingStructures.Pose drawn(ClientLevel level, BlockPos plot, float partialTick) {
     *         Ship ship = MyShips.holding(level, plot);
     *         return ship == null ? null : poseOf(ship, partialTick);
     *     }
     *     public SimpleBloodAPI.MovingStructures.Pose logical(ClientLevel level, BlockPos plot) {
     *         Ship ship = MyShips.holding(level, plot);
     *         return ship == null ? null : poseOf(ship, 1f);
     *     }
     * });
     * }</pre>
     */
    public interface MovingStructures {

        /**
         * Where a structure stands: from its plot to the world and back. A point of the plot
         * (a block of the structure where it is stored) is drawn in the world at
         * {@link #toWorld}; a direction turns with {@link #turn}.
         */
        interface Pose {
            /** A plot point where the structure holds it in the world now. */
            Vec3 toWorld(Vec3 plot);

            /** A world point into the plot. */
            Vec3 toPlot(Vec3 world);

            /** A direction (its length kept) turned the way the structure is turned. */
            Vec3 turn(Vec3 direction);

            /**
             * A world point into the plot as the structure stood a tick ago. A drop that moved
             * during the tick is traced against the structure moving too (a falling ship meets
             * a falling drop). Default: as now (the structure counts as still).
             */
            default Vec3 lastToPlot(Vec3 world) {
                return toPlot(world);
            }

            /**
             * Where a plot point was in the world a tick ago: with {@link #toWorld}, its
             * velocity, which blood letting go of the structure keeps. Default: as now.
             */
            default Vec3 lastToWorld(Vec3 plot) {
                return toWorld(plot);
            }
        }

        /**
         * The structures whose blocks a drop moving from {@code from} to {@code to} (world
         * points, usually under a block apart) may meet this tick, each with its pose this
         * tick (and the tick before, see {@link Pose#lastToPlot}). Include any whose bounds
         * come within a block or two of the move: they may have moved during the tick. Empty
         * (never null) when none.
         */
        java.util.List<? extends Pose> crossing(ClientLevel level, Vec3 from, Vec3 to);

        /**
         * The pose to draw the blood on block {@code plot} with this frame, if that block lies
         * in one of your structures' plots ({@code partialTick} between ticks, as the
         * structure itself is drawn); null for any other block. Render thread.
         */
        Pose drawn(ClientLevel level, net.minecraft.core.BlockPos plot, float partialTick);

        /**
         * The pose this tick of the structure whose plot holds block {@code plot} (how gravity
         * falls across its blood, where its drips let go), null for any other block.
         */
        Pose logical(ClientLevel level, net.minecraft.core.BlockPos plot);
    }

    /**
     * 1.3.0: registers (or replaces) a source of moving block structures under a name of your
     * choice, e.g. your mod id. See {@link MovingStructures}. Client side.
     */
    public static void registerMovingStructures(String name, MovingStructures source) {
        if (name == null || source == null) return;
        com.simpleblood.surface.MovingBodies.register(name, source);
        SimpleBlood.LOGGER.info("Registered moving block structures: {}", name);
    }

    /** 1.3.0: removes the source registered under {@code name}. */
    public static void unregisterMovingStructures(String name) {
        if (name != null) com.simpleblood.surface.MovingBodies.unregister(name);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOLVED PER-MOB VALUES (1.2.0)
    // ═══════════════════════════════════════════════════════════════════════

    /** Whether this mob's body is metal (sparks and a clank when struck by metal). */
    public static boolean hasMetalBody(LivingEntity entity) {
        BloodSettings s = getEntityBloodSettings(entity);
        if (s != null && s.getMetalBody() != null) return s.getMetalBody();
        String armor = SimpleBlood.armorOf(entity);
        if (armor.equals("metal")) return true;
        if (armor.equals("armor")) return false; // armoured, not metal through and through
        return SimpleBlood.bloodKindOf(entity) == BloodKind.METAL;
    }

    /** Whether blood may stay on this mob's body. */
    public static boolean takesBloodOnBody(LivingEntity entity) {
        BloodSettings s = getEntityBloodSettings(entity);
        return s == null || s.getBloodOnBody() == null || s.getBloodOnBody();
    }

    /** Whether a pool spreads under this mob's body when it dies. */
    public static boolean leavesPool(LivingEntity entity) {
        BloodSettings s = getEntityBloodSettings(entity);
        return s == null || s.getLeavesPool() == null || s.getLeavesPool();
    }

    /** Whether giblets may fly off this mob. */
    public static boolean throwsGiblets(LivingEntity entity) {
        BloodSettings s = getEntityBloodSettings(entity);
        return s == null || s.getGiblets() == null || s.getGiblets();
    }

    /** The giblet colour another mod gave this mob ({@code 0xRRGGBB}), or null to work it out. */
    public static Integer gibletColour(LivingEntity entity) {
        BloodSettings s = getEntityBloodSettings(entity);
        return s == null ? null : s.getGibletColor();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Per-entity blood settings. Every option is nullable: leave it unset to inherit
     * the config/default value. Build with the fluent {@code setX(...)} methods.
     */
    public static final class BloodSettings {
        private Integer colorRGB = null;
        private Function<LivingEntity, Integer> colorProvider = null;
        private Boolean canBleed = true;
        private Boolean canDripAtLowHealth = true;
        private Boolean transformToStains = true;
        private BloodKind bloodKind = null;
        private Boolean leavesFootprints = null;
        private Boolean glows = null;
        private Boolean createsFogUnderwater = null;
        private Boolean particlesDespawnInWater = null;
        private Boolean bleedWhenAsphyxiating = null;
        private Float particleSizeMultiplier = null;
        private Float burstIntensityMultiplier = null;
        private Float dripIntensityMultiplier = null;
        private Float throwSpeedMultiplier = null;
        private Float throwUpMultiplier = null;
        private SoundEvent bloodSound = null;
        private Boolean soundEnabled = null;
        private BiPredicate<LivingEntity, DamageSource> bleedPredicate = null;
        private Boolean metalBody = null;
        private Boolean bloodOnBody = null;
        private Boolean leavesPool = null;
        private Boolean giblets = null;
        private Integer gibletColor = null;

        /**
         * 1.2.0: a body of metal. Metal weapons and arrows strike sparks off it, with a clank.
         * Default: on for {@link BloodKind#METAL} mobs (iron golems), off for the rest.
         */
        public BloodSettings setMetalBody(boolean metal) {
            this.metalBody = metal;
            return this;
        }

        /**
         * 1.2.0: whether blood stays on this mob's body ("Blood on mobs": its own wounds, and
         * blood thrown onto it). Turn it off for mobs whose model it would look wrong on.
         * Default: on (when the player has Blood on mobs on).
         */
        public BloodSettings setBloodOnBody(boolean onBody) {
            this.bloodOnBody = onBody;
            return this;
        }

        /** 1.2.0: whether a pool spreads under the body when it dies. Default: on for mobs that stain. */
        public BloodSettings setLeavesPool(boolean pool) {
            this.leavesPool = pool;
            return this;
        }

        /** 1.2.0: whether chunks of meat fly off it (when the player has giblets on). Default: on for {@code LIQUID} blood. */
        public BloodSettings setGiblets(boolean giblets) {
            this.giblets = giblets;
            return this;
        }

        public Boolean getMetalBody() { return metalBody; }
        public Boolean getBloodOnBody() { return bloodOnBody; }
        public Boolean getLeavesPool() { return leavesPool; }
        public Boolean getGiblets() { return giblets; }

        /**
         * 1.3.0: the colour of the chunks that fly off it, as {@code 0xRRGGBB}. Default: worked
         * out (raw meat for red blood, otherwise the colour of the mob's own texture). A
         * player's own pick for the mob still wins.
         */
        public BloodSettings setGibletColor(int rgb) {
            this.gibletColor = rgb & 0xFFFFFF;
            return this;
        }

        public Integer getGibletColor() { return gibletColor; }

        // ── Colour ────────────────────────────────────────────────────────
        /** Static blood colour as {@code 0xRRGGBB}. */
        public BloodSettings setColor(int rgb) {
            this.colorRGB = rgb;
            return this;
        }

        /** Static blood colour from separate 0–255 components. */
        public BloodSettings setColor(int red, int green, int blue) {
            this.colorRGB = (red << 16) | (green << 8) | blue;
            return this;
        }

        /**
         * Dynamic blood colour, resolved per entity instance at spawn time
         * ({@code 0xRRGGBB}). Takes priority over {@link #setColor(int)}. Return
         * {@code null} from the function to fall back to the static colour/config.
         */
        public BloodSettings setColorProvider(Function<LivingEntity, Integer> provider) {
            this.colorProvider = provider;
            return this;
        }

        // ── Core behaviour ────────────────────────────────────────────────
        /** Whether this entity bleeds at all. */
        public BloodSettings setCanBleed(boolean canBleed) {
            this.canBleed = canBleed;
            return this;
        }

        /** Whether this entity drips blood while wounded (false for constructs/undead). */
        public BloodSettings setCanDripAtLowHealth(boolean canDrip) {
            this.canDripAtLowHealth = canDrip;
            return this;
        }

        /** Whether particles leave stains on the ground and fog clouds in water. */
        /**
         * Whether the entity's blood sticks to blocks (puddles, runs down walls, stains on
         * plants and dripstone) and clouds up in water. {@code false} keeps it as flying
         * drops that fade where they land.
         */
        public BloodSettings setTransformToStains(boolean transform) {
            this.transformToStains = transform;
            return this;
        }

        // ── Water / environment ───────────────────────────────────────────
        /**
         * Force whether particles turn into fog clouds underwater. If unset this is
         * derived from {@code transformToStains} and {@code canDripAtLowHealth}.
         */
        public BloodSettings setCreatesFogUnderwater(boolean createsFog) {
            this.createsFogUnderwater = createsFog;
            return this;
        }

        /**
         * Whether this entity's particles should vanish on contact with water (like a
         * snow golem's snow melting) instead of drifting/staining.
         */
        public BloodSettings setParticlesDespawnInWater(boolean despawn) {
            this.particlesDespawnInWater = despawn;
            return this;
        }

        /**
         * Whether this entity still bleeds from suffocation / drowning / drying out.
         * Simple Blood suppresses those by default (fish out of water, a mob buried in
         * sand, ...); set {@code true} to opt this entity back in.
         */
        public BloodSettings setBleedWhenAsphyxiating(boolean bleed) {
            this.bleedWhenAsphyxiating = bleed;
            return this;
        }

        // ── Intensity / size / sound ──────────────────────────────────────
        /** Scale this entity's particle size (1.0 = default). */
        public BloodSettings setParticleSizeMultiplier(float multiplier) {
            this.particleSizeMultiplier = multiplier;
            return this;
        }

        /** Scale the number of particles in hit and death bursts (1.0 = default). */
        public BloodSettings setBurstIntensityMultiplier(float multiplier) {
            this.burstIntensityMultiplier = multiplier;
            return this;
        }

        /** Scale the number of low-health drip particles (1.0 = default). */
        public BloodSettings setDripIntensityMultiplier(float multiplier) {
            this.dripIntensityMultiplier = multiplier;
            return this;
        }

        /**
         * 1.3.0: how hard this mob's hit and death drops are thrown, on top of the player's
         * Drops > Speed setting (1.0 = default, 2.0 twice as fast, 0.5 half as fast). A big
         * beast can fling its blood far, a small one barely. Clamped to 0..10.
         */
        public BloodSettings setThrowSpeedMultiplier(float multiplier) {
            this.throwSpeedMultiplier = multiplier;
            return this;
        }

        /**
         * 1.3.0: how high this mob's hit and death drops are thrown, on top of the player's
         * Drops > Upward setting (1.0 = default; only the rising part of a throw is scaled, as
         * with the setting). Clamped to 0..10.
         */
        public BloodSettings setThrowUpMultiplier(float multiplier) {
            this.throwUpMultiplier = multiplier;
            return this;
        }

        /** Use a custom sound for this entity's blood instead of the default drip. */
        public BloodSettings setBloodSound(SoundEvent sound) {
            this.bloodSound = sound;
            return this;
        }

        /** Enable/disable blood sounds specifically for this entity. */
        public BloodSettings setSoundEnabled(boolean enabled) {
            this.soundEnabled = enabled;
            return this;
        }

        // ── Advanced ──────────────────────────────────────────────────────
        /**
         * Full control over whether a given hit bleeds. Receives the entity and the
         * {@link DamageSource}; return {@code true} to bleed. When set, this overrides
         * the default asphyxiation/damage-type suppression for this entity.
         */
        public BloodSettings setBleedPredicate(BiPredicate<LivingEntity, DamageSource> predicate) {
            this.bleedPredicate = predicate;
            return this;
        }

        // ── Getters ───────────────────────────────────────────────────────
        /** @return the static colour as a {@link Color}, or {@code null} if unset. */
        public Color getColor() {
            return colorRGB == null ? null : new Color(colorRGB);
        }

        /**
         * Resolve the effective colour for a specific entity: the dynamic provider if
         * present (and non-null), otherwise the static colour. {@code null} = unset.
         */
        public Integer resolveColorRGB(LivingEntity entity) {
            if (colorProvider != null) {
                try {
                    Integer dynamic = colorProvider.apply(entity);
                    if (dynamic != null) return dynamic;
                } catch (Exception e) {
                    SimpleBlood.LOGGER.debug("Blood colour provider threw for {}: {}",
                            entity.getType().getDescriptionId(), e.getMessage());
                }
            }
            return colorRGB;
        }

        public Integer getColorRGB() { return colorRGB; }
        public Function<LivingEntity, Integer> getColorProvider() { return colorProvider; }
        public Boolean getCanBleed() { return canBleed; }
        public Boolean getCanDripAtLowHealth() { return canDripAtLowHealth; }
        public Boolean getTransformToStains() { return transformToStains; }

        /**
         * What comes out: liquid blood, solid debris, glowing embers or snow powder. Decides
         * puddles (liquid and powder only), water fog (liquid), sweeps and craters (liquid),
         * fizzling in water (embers). Unset: the config's per-mob kind, else liquid.
         */
        public BloodSettings setBloodKind(BloodKind kind) {
            this.bloodKind = kind;
            return this;
        }

        /**
         * Whether this entity's feet pick up fresh blood and leave a trail of prints. Unset:
         * yes, whenever the "Footprints" setting is on. Set {@code false} for things that
         * hover, roll or have no feet to speak of.
         */
        public BloodSettings setLeavesFootprints(boolean leaves) {
            this.leavesFootprints = leaves;
            return this;
        }

        /**
         * 1.0.2: whether the blood glows in the dark: drops, clouds in water and fresh puddles
         * are drawn at full brightness, and puddles lose the glow as they dry. Default: no
         * (the glow squid's does).
         */
        public BloodSettings setGlows(boolean glows) {
            this.glows = glows;
            return this;
        }

        public BloodKind getBloodKind() { return bloodKind; }
        public Boolean getGlows() { return glows; }
        public Boolean getLeavesFootprints() { return leavesFootprints; }
        public Boolean getCreatesFogUnderwater() { return createsFogUnderwater; }
        public Boolean getParticlesDespawnInWater() { return particlesDespawnInWater; }
        public Boolean getBleedWhenAsphyxiating() { return bleedWhenAsphyxiating; }
        public Float getParticleSizeMultiplier() { return particleSizeMultiplier; }
        public Float getBurstIntensityMultiplier() { return burstIntensityMultiplier; }
        public Float getDripIntensityMultiplier() { return dripIntensityMultiplier; }
        /** 1.3.0: see {@link #setThrowSpeedMultiplier}; null when unset. */
        public Float getThrowSpeedMultiplier() { return throwSpeedMultiplier; }
        /** 1.3.0: see {@link #setThrowUpMultiplier}; null when unset. */
        public Float getThrowUpMultiplier() { return throwUpMultiplier; }
        public SoundEvent getBloodSound() { return bloodSound; }
        public Boolean getSoundEnabled() { return soundEnabled; }
        public BiPredicate<LivingEntity, DamageSource> getBleedPredicate() { return bleedPredicate; }
    }
}
