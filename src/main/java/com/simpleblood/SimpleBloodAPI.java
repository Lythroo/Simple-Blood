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

    /** Removes all blood from blocks (what {@code /simpleblood clear} does). Any thread. */
    public static void clearSurfaceBlood() {
        Guard.run(Guard.Part.API, "clearSurfaceBlood", BloodSurfaces::clear);
    }

    /** How many block faces currently hold blood. */
    public static int surfaceBloodCount() {
        return BloodSurfaces.tileCount();
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
        private SoundEvent bloodSound = null;
        private Boolean soundEnabled = null;
        private BiPredicate<LivingEntity, DamageSource> bleedPredicate = null;

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
        public SoundEvent getBloodSound() { return bloodSound; }
        public Boolean getSoundEnabled() { return soundEnabled; }
        public BiPredicate<LivingEntity, DamageSource> getBleedPredicate() { return bleedPredicate; }
    }
}
