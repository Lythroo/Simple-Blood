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

public final class SimpleBlood {
    public static final String MOD_ID = "simpleblood";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private SimpleBlood() {}

    private static Identifier idOf(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    public static boolean vanillaMobExists(String path) {
        try {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.withDefaultNamespace(path));
        } catch (Exception e) {
            return false;
        }
    }

    private static SimpleBloodAPI.BloodSettings apiSettings(Identifier id) {
        return SimpleBloodAPI.hasCustomSettings(id.toString())
                ? SimpleBloodAPI.getEntityBloodSettings(id.toString())
                : null;
    }

    private static SimpleBloodConfig.ModdedEntities.ModdedEntitySettings moddedSettings(Identifier id) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null && cfg.moddedEntities.hasCustomSettings(id.toString())) {
            return cfg.moddedEntities.getSettings(id.toString());
        }
        return null;
    }

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

    public static boolean isEnvironmentalNoBleed(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && Boolean.TRUE.equals(api.getBleedWhenAsphyxiating())) {
            return false;
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

    public static boolean bloodGlows(LivingEntity entity) {
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

    public static boolean leavesFootprints(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getLeavesFootprints() == null || api.getLeavesFootprints();
    }

    public static boolean createsFogUnderwater(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getCreatesFogUnderwater() != null) {
            return api.getCreatesFogUnderwater();
        }
        return bloodKindOf(entity) == BloodKind.LIQUID && shouldEntityTransformToStains(entity);
    }

    public static boolean particlesDespawnInWater(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getParticlesDespawnInWater() != null) {
            return api.getParticlesDespawnInWater();
        }
        BloodKind kind = bloodKindOf(entity);
        return kind == BloodKind.POWDER || kind == BloodKind.EMBER;
    }

    public static float getParticleSizeMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getParticleSizeMultiplier() != null) ? api.getParticleSizeMultiplier() : 1.0f;
    }

    public static float getBurstIntensityMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getBurstIntensityMultiplier() != null) ? api.getBurstIntensityMultiplier() : 1.0f;
    }

    public static float getDripIntensityMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getDripIntensityMultiplier() != null) ? api.getDripIntensityMultiplier() : 1.0f;
    }

    public static SoundEvent getBloodSound(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getBloodSound() != null) {
            return api.getBloodSound();
        }
        return SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA;
    }

    public static SoundEvent customBloodSound(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api != null ? api.getBloodSound() : null;
    }

    public static boolean isSoundEnabledFor(LivingEntity entity) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) {
            return false;
        }
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getSoundEnabled() == null || api.getSoundEnabled();
    }
}
