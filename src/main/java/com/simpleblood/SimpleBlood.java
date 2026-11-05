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
            return BuiltInRegistries.ENTITY_TYPE.containsKey(Ids.vanilla(path));
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
        return cfg == null ? null : cfg.moddedEntities.effective(id.toString());
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
        if (entity.canBreatheUnderwater() && !entity.isInWater() && entity.getAirSupply() < entity.getMaxAirSupply()) {
            return true;
        }
        if (entity.getAirSupply() <= 0) {
            return true;
        }
        BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        return entity.level().getBlockState(eye).isSuffocating(entity.level(), eye);
    }

    public static boolean shouldBleedFrom(LivingEntity entity, DamageSource source) {
        boolean always = alwaysBleeds(damageTypeId(source));
        if (!always && blacklisted(source)) return false;
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
        if (!always && !bleedWhenAsphyxiating && isBuiltInAsphyxiation(source)) {
            return false;
        }
        return always || !SimpleBloodAPI.isNonBleedingDamageType(source);
    }

    private static final java.util.Map<String, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType>> DAMAGE_TAGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean blacklisted(DamageSource source) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || cfg.general == null || cfg.general.noBleedDamageTypes == null || cfg.general.noBleedDamageTypes.isEmpty() || source == null) return false;
        String id = damageTypeId(source);
        for (String entry : cfg.general.noBleedDamageTypes) {
            if (entry == null || entry.isBlank()) continue;
            String e = entry.trim();
            if (e.startsWith("#")) {
                try {
                    var tag = DAMAGE_TAGS.computeIfAbsent(e, k -> net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.DAMAGE_TYPE, Ids.parse(withNamespace(k.substring(1)))));
                    if (source.is(tag)) return true;
                } catch (Throwable t) {
                }
            } else if (withNamespace(e).equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean alwaysBleeds(String id) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg != null && cfg.general != null && cfg.general.bleedDamageTypes != null && !id.isEmpty()
                && cfg.general.bleedDamageTypes.contains(id);
    }

    public static boolean drawsBlood(net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> type, String id) {
        if (alwaysBleeds(id)) return true;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null && cfg.general != null && cfg.general.noBleedDamageTypes != null) {
            for (String entry : cfg.general.noBleedDamageTypes) {
                if (entry == null || entry.isBlank()) continue;
                String e = entry.trim();
                if (e.startsWith("#")) {
                    try {
                        var tag = DAMAGE_TAGS.computeIfAbsent(e, k -> net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.DAMAGE_TYPE, Ids.parse(withNamespace(k.substring(1)))));
                        if (type.is(tag)) return false;
                    } catch (Throwable t) {
                    }
                } else if (withNamespace(e).equals(id)) {
                    return false;
                }
            }
        }
        if (type.is(DamageTypes.DROWN) || type.is(DamageTypes.IN_WALL) || type.is(DamageTypes.DRY_OUT)) return false;
        return !SimpleBloodAPI.isNonBleedingDamageType(type);
    }

    private static String withNamespace(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    public static String damageTypeId(DamageSource source) {
        try {
            String key = source.typeHolder().unwrapKey().map(Object::toString).orElse("");
            int slash = key.lastIndexOf(" / ");
            return slash < 0 ? "" : key.substring(slash + 3, key.length() - 1);
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isBuiltInAsphyxiation(DamageSource source) {
        return source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.DRY_OUT);
    }

    public static boolean hasFleshBlood(LivingEntity entity) {
        //? if >=26.2 {
        /*boolean gel = entity instanceof net.minecraft.world.entity.monster.cubemob.Slime;
        *///?} else {
        boolean gel = entity instanceof net.minecraft.world.entity.monster.Slime;
        //?}
        return !gel && bloodKindOf(entity) == BloodKind.LIQUID;
    }

    public static BloodKind bloodKindOf(LivingEntity entity) {
        if (Capture.kind != null && Capture.active()) return Capture.kind;
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (cfg != null) {
                if (mc.player != null && entity.getUUID().equals(mc.player.getUUID())) return BloodKind.parse(cfg.player.clientPlayerBloodKind);
                String carried = cfg.player.colourFromSkins ? SkinColours.kindOf(entity) : null;
                if (carried != null) return BloodKind.parse(carried);
            }
        }
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

    public static String armorOf(LivingEntity entity) {
        return tableValue(entity, m -> m.armor, v -> v.armor, "auto");
    }

    public static String gibletsOf(LivingEntity entity) {
        return tableValue(entity, m -> m.giblets, v -> v.giblets, GibletColours.AUTO);
    }

    private static String tableValue(LivingEntity entity,
                                     java.util.function.Function<SimpleBloodConfig.ModdedEntities.ModdedEntitySettings, String> moddedColumn,
                                     java.util.function.Function<SimpleBloodConfig.VanillaEntities.EntitySettings, String> vanillaColumn,
                                     String fallback) {
        Identifier id = idOf(entity);
        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded = moddedSettings(id);
        if (modded != null) {
            String value = moddedColumn.apply(modded);
            return value == null ? fallback : value;
        }
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null) {
            SimpleBloodConfig.VanillaEntities.EntitySettings vanilla = cfg.vanillaEntities.getSettings(id.getPath());
            if (vanilla != null && vanillaColumn.apply(vanilla) != null) return vanillaColumn.apply(vanilla);
        }
        return fallback;
    }

    public static boolean bloodGlows(LivingEntity entity) {
        SimpleBloodConfig glowCfg = SimpleBloodClient.getConfig();
        if (glowCfg != null && !glowCfg.kinds.glow) return false;
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

    public static float getThrowSpeedMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getThrowSpeedMultiplier() == null ? 1.0f : clampThrow(api.getThrowSpeedMultiplier());
    }

    public static float getThrowUpMultiplier(LivingEntity entity) {
        SimpleBloodAPI.BloodSettings api = apiSettings(idOf(entity));
        return api == null || api.getThrowUpMultiplier() == null ? 1.0f : clampThrow(api.getThrowUpMultiplier());
    }

    private static float clampThrow(float multiplier) {
        return Float.isNaN(multiplier) ? 1.0f : Math.max(0f, Math.min(MAX_THROW_MULTIPLIER, multiplier));
    }

    private static final float MAX_THROW_MULTIPLIER = 10f;

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
