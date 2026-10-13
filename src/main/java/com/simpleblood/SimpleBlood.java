package com.simpleblood;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared, loader-agnostic logic for Simple Blood.
 *
 * This class holds the mod id/logger and the per-entity "should this bleed / drip /
 * transform" decisions. Loader entrypoints (Fabric / NeoForge) live in their own
 * packages and call into here; there is intentionally no loader-specific code here.
 *
 * Decision priority for every query:
 *   1. API registrations (via {@link SimpleBloodAPI})
 *   2. Modded-entity config settings
 *   3. Vanilla-entity config settings
 *   4. Sensible default
 */
public final class SimpleBlood {
    public static final String MOD_ID = "simpleblood";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private SimpleBlood() {}

    /** Full registry id, e.g. {@code minecraft:zombie} or {@code mymod:custom_mob}. */
    private static Identifier idOf(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    /**
     * Whether a vanilla mob (registry path such as {@code creaking}) exists in the game this
     * client runs: the built-in table lists every mob up to the newest version, and the
     * settings screen hides the ones this version does not have.
     */
    public static boolean vanillaMobExists(String path) {
        try {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(Ids.vanilla(path));
        } catch (Exception e) {
            return false; // not a valid id
        }
    }

    /** API override for an entity, or {@code null} if none is registered. */
    private static SimpleBloodAPI.BloodSettings apiSettings(Identifier id) {
        return SimpleBloodAPI.hasCustomSettings(id.toString())
                ? SimpleBloodAPI.getEntityBloodSettings(id.toString())
                : null;
    }

    /** Modded-entity config override for an entity, or {@code null} if none exists. */
    private static SimpleBloodConfig.ModdedEntities.ModdedEntitySettings moddedSettings(Identifier id) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null ? null : cfg.moddedEntities.effective(id.toString());
    }

    /**
     * Whether this entity produces blood at all.
     */
    public static boolean shouldEntityBleed(LivingEntity entity) {
        Identifier id = idOf(entity);

        SimpleBloodAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getCanBleed() != null) {
            return api.getCanBleed();
        }

        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.enabled;
        }

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null || cfg.doesEntityBleed(id.getPath());
    }

    /**
     * Whether this entity drips blood while wounded. Entities without a circulatory
     * system (constructs, undead, slimes, ...) should not continuously drip.
     */
    public static boolean shouldEntityDripAtLowHealth(LivingEntity entity) {
        Identifier id = idOf(entity);

        SimpleBloodAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getCanDripAtLowHealth() != null) {
            return api.getCanDripAtLowHealth();
        }

        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.canDripAtLowHealth;
        }

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null || cfg.shouldEntityDripAtLowHealth(id.getPath());
    }

    /**
     * Whether this entity's particles transform into stains on the ground and fog
     * clouds in water. When false, particles stay as-is.
     */
    public static boolean shouldEntityTransformToStains(LivingEntity entity) {
        Identifier id = idOf(entity);

        SimpleBloodAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getTransformToStains() != null) {
            return api.getTransformToStains();
        }

        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.transformToStains;
        }

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null) {
            SimpleBloodConfig.VanillaEntities.EntitySettings vanilla =
                    cfg.vanillaEntities.getSettings(id.getPath());
            if (vanilla != null) {
                return vanilla.transformToStains;
            }
        }
        return true;
    }

    /**
     * True when the entity's health loss is environmental asphyxiation rather than a
     * wound, so no blood should be produced. Covers:
     *   - a water-breather out of water (fish/squid/etc. drying out),
     *   - any entity out of air (drowning), and
     *   - an entity whose head is inside a suffocating block (buried in sand, crushed).
     *
     * Used by every client-side blood trigger that lacks an authoritative DamageSource
     * (hit burst and low-health drip). Death uses the DamageSource directly.
     */
    public static boolean isEnvironmentalNoBleed(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && Boolean.TRUE.equals(api.getBleedWhenAsphyxiating())) {
            return false; // dev opted this entity into bleeding while asphyxiating
        }
        if (entity.canBreatheUnderwater() && !entity.isInWater()) {
            return true;
        }
        if (entity.getAirSupply() <= 0) {
            return true;
        }
        BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        return entity.level().getBlockState(eye).isSuffocating(entity.level(), eye);
    }

    // ══════════════════════════════════════════════════════════════════════
    // API-AWARE RESOLVERS (consulted by the client-side blood pipeline)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Central decision for whether a specific hit should bleed, honouring (in order):
     * a per-entity {@code bleedPredicate}; the {@code bleedWhenAsphyxiating} opt-in;
     * the built-in drown/suffocation/dry-out suppression; and developer-registered
     * non-bleeding damage types.
     */
    public static boolean shouldBleedFrom(LivingEntity entity, DamageSource source) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getBleedPredicate() != null) {
            try {
                return api.getBleedPredicate().test(entity, source);
            } catch (Exception e) {
                LOGGER.debug("Bleed predicate threw for {}: {}",
                        entity.getType().getDescriptionId(), e.getMessage());
            }
        }
        boolean bleedWhenAsphyxiating = api != null && Boolean.TRUE.equals(api.getBleedWhenAsphyxiating());
        if (!bleedWhenAsphyxiating && isBuiltInAsphyxiation(source)) {
            return false;
        }
        return !SimpleBloodAPI.isNonBleedingDamageType(source);
    }

    private static boolean isBuiltInAsphyxiation(DamageSource source) {
        return source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DRY_OUT);
    }

    /** What comes out of this entity: liquid blood, debris, embers or snow powder. */
    public static BloodKind bloodKindOf(LivingEntity entity) {
        Identifier id = idOf(entity);
        SimpleBloodAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getBloodKind() != null) {
            return api.getBloodKind();
        }
        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.getKind();
        }
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null) {
            SimpleBloodConfig.VanillaEntities.EntitySettings vanilla = cfg.vanillaEntities.getSettings(id.getPath());
            if (vanilla != null) {
                return vanilla.getKind();
            }
        }
        return BloodKind.LIQUID;
    }

    /** Whether this entity's blood glows in the dark: API, then modded table, then vanilla table. */
    public static boolean bloodGlows(LivingEntity entity) {
        SimpleBloodConfig glowCfg = SimpleBloodClient.getConfig();
        if (glowCfg != null && !glowCfg.kinds.glow) return false; // glowing blood switched off
        Identifier id = idOf(entity);
        SimpleBloodAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getGlows() != null) return api.getGlows();
        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) return modded.glows;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null) {
            SimpleBloodConfig.VanillaEntities.EntitySettings vanilla = cfg.vanillaEntities.getSettings(id.getPath());
            if (vanilla != null) return vanilla.glows;
        }
        return false;
    }

    /** Whether this entity's feet take up blood and leave prints (API override, default yes). */
    public static boolean leavesFootprints(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getLeavesFootprints() == null || api.getLeavesFootprints();
    }

    /** Whether this entity's particles turn into fog clouds underwater. */
    public static boolean createsFogUnderwater(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getCreatesFogUnderwater() != null) {
            return api.getCreatesFogUnderwater();
        }
        return bloodKindOf(entity) == BloodKind.LIQUID && shouldEntityTransformToStains(entity);
    }

    /** Whether this entity's particles vanish on contact with water (snow, embers). */
    public static boolean particlesDespawnInWater(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getParticlesDespawnInWater() != null) {
            return api.getParticlesDespawnInWater();
        }
        BloodKind kind = bloodKindOf(entity);
        return kind == BloodKind.POWDER || kind == BloodKind.EMBER;
    }

    /** Per-entity particle size multiplier (1.0 when unset). */
    public static float getParticleSizeMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getParticleSizeMultiplier() != null) ? api.getParticleSizeMultiplier() : 1.0f;
    }

    /** Per-entity hit/death burst intensity multiplier (1.0 when unset). */
    public static float getBurstIntensityMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getBurstIntensityMultiplier() != null) ? api.getBurstIntensityMultiplier() : 1.0f;
    }

    /** Per-entity low-health drip intensity multiplier (1.0 when unset). */
    public static float getDripIntensityMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getDripIntensityMultiplier() != null) ? api.getDripIntensityMultiplier() : 1.0f;
    }

    /** The blood sound for this entity (custom via API, else the default drip). */
    public static SoundEvent getBloodSound(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getBloodSound() != null) {
            return api.getBloodSound();
        }
        return SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA;
    }

    /** The sound set for this entity through the API, or null for the mod's own. */
    public static SoundEvent customBloodSound(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api != null ? api.getBloodSound() : null;
    }

    /** Whether blood sounds should play for this entity (global config AND per-entity toggle). */
    public static boolean isSoundEnabledFor(LivingEntity entity) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) {
            return false;
        }
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getSoundEnabled() == null || api.getSoundEnabled();
    }
}
