package com.bloodmod;

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

public final class BloodMod {
    public static final String MOD_ID = "bloodmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private BloodMod() {}

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

    private static BloodModAPI.BloodSettings apiSettings(Identifier id) {
        return BloodModAPI.hasCustomSettings(id.toString())
                ? BloodModAPI.getEntityBloodSettings(id.toString())
                : null;
    }

    private static BloodModConfig.ModdedEntities.ModdedEntitySettings moddedSettings(Identifier id) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg != null && cfg.moddedEntities.hasCustomSettings(id.toString())) {
            return cfg.moddedEntities.getSettings(id.toString());
        }
        return null;
    }

    public static boolean shouldEntityBleed(LivingEntity entity) {
        Identifier id = idOf(entity);

        BloodModAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getCanBleed() != null) {
            return api.getCanBleed();
        }

        BloodModConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.enabled;
        }

        BloodModConfig cfg = BloodModClient.getConfig();
        return cfg == null || cfg.doesEntityBleed(id.getPath());
    }

    public static boolean shouldEntityDripAtLowHealth(LivingEntity entity) {
        Identifier id = idOf(entity);

        BloodModAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getCanDripAtLowHealth() != null) {
            return api.getCanDripAtLowHealth();
        }

        BloodModConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.canDripAtLowHealth;
        }

        BloodModConfig cfg = BloodModClient.getConfig();
        return cfg == null || cfg.shouldEntityDripAtLowHealth(id.getPath());
    }

    public static boolean shouldEntityTransformToStains(LivingEntity entity) {
        Identifier id = idOf(entity);

        BloodModAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getTransformToStains() != null) {
            return api.getTransformToStains();
        }

        BloodModConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.transformToStains;
        }

        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg != null) {
            BloodModConfig.VanillaEntities.EntitySettings vanilla =
                    cfg.vanillaEntities.getSettings(id.getPath());
            if (vanilla != null) {
                return vanilla.transformToStains;
            }
        }
        return true;
    }

    public static boolean isEnvironmentalNoBleed(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
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
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
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
        return !BloodModAPI.isNonBleedingDamageType(source);
    }

    private static boolean isBuiltInAsphyxiation(DamageSource source) {
        return source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DRY_OUT);
    }

    public static BloodKind bloodKindOf(LivingEntity entity) {
        Identifier id = idOf(entity);
        BloodModAPI.BloodSettings api = apiSettings(id);
        if (api != null && api.getBloodKind() != null) {
            return api.getBloodKind();
        }
        BloodModConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            return modded.getKind();
        }
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg != null) {
            BloodModConfig.VanillaEntities.EntitySettings vanilla = cfg.vanillaEntities.getSettings(id.getPath());
            if (vanilla != null) {
                return vanilla.getKind();
            }
        }
        return BloodKind.LIQUID;
    }

    public static boolean leavesFootprints(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getLeavesFootprints() == null || api.getLeavesFootprints();
    }

    public static boolean createsFogUnderwater(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getCreatesFogUnderwater() != null) {
            return api.getCreatesFogUnderwater();
        }
        return bloodKindOf(entity) == BloodKind.LIQUID && shouldEntityTransformToStains(entity);
    }

    public static boolean particlesDespawnInWater(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getParticlesDespawnInWater() != null) {
            return api.getParticlesDespawnInWater();
        }
        BloodKind kind = bloodKindOf(entity);
        return kind == BloodKind.POWDER || kind == BloodKind.EMBER;
    }

    public static float getParticleSizeMultiplier(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getParticleSizeMultiplier() != null) ? api.getParticleSizeMultiplier() : 1.0f;
    }

    public static float getBurstIntensityMultiplier(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getBurstIntensityMultiplier() != null) ? api.getBurstIntensityMultiplier() : 1.0f;
    }

    public static float getDripIntensityMultiplier(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        return (api != null && api.getDripIntensityMultiplier() != null) ? api.getDripIntensityMultiplier() : 1.0f;
    }

    public static SoundEvent getBloodSound(LivingEntity entity) {
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        if (api != null && api.getBloodSound() != null) {
            return api.getBloodSound();
        }
        return SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA;
    }

    public static boolean isSoundEnabledFor(LivingEntity entity) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) {
            return false;
        }
        BloodModAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getSoundEnabled() == null || api.getSoundEnabled();
    }
}
