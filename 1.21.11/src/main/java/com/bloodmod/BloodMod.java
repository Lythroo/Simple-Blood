package com.bloodmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BloodMod implements ModInitializer {
    public static final String MOD_ID = "bloodmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Simple Blood (Client-Only) - Server registration...");

        BloodParticles.register();

        LOGGER.info("Simple Blood particle types registered!");
    }

    public static boolean shouldEntityBleed(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String fullEntityId = id.toString(); 

        if (BloodModAPI.hasCustomSettings(fullEntityId)) {
            BloodModAPI.BloodSettings apiSettings = BloodModAPI.getEntityBloodSettings(fullEntityId);
            if (apiSettings != null && apiSettings.getCanBleed() != null) {
                return apiSettings.getCanBleed();
            }
        }

        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg != null && cfg.moddedEntities.hasCustomSettings(fullEntityId)) {
            BloodModConfig.ModdedEntities.ModdedEntitySettings moddedSettings =
                    cfg.moddedEntities.getSettings(fullEntityId);
            if (moddedSettings != null) {
                return moddedSettings.enabled;
            }
        }

        if (cfg == null) {

            return true;
        }
        return cfg.doesEntityBleed(id.getPath());
    }

    public static boolean shouldEntityDripAtLowHealth(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String fullEntityId = id.toString(); 

        if (BloodModAPI.hasCustomSettings(fullEntityId)) {
            BloodModAPI.BloodSettings apiSettings = BloodModAPI.getEntityBloodSettings(fullEntityId);
            if (apiSettings != null && apiSettings.getCanDripAtLowHealth() != null) {
                return apiSettings.getCanDripAtLowHealth();
            }
        }

        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg != null && cfg.moddedEntities.hasCustomSettings(fullEntityId)) {
            BloodModConfig.ModdedEntities.ModdedEntitySettings moddedSettings =
                    cfg.moddedEntities.getSettings(fullEntityId);
            if (moddedSettings != null) {
                return moddedSettings.canDripAtLowHealth;
            }
        }

        if (cfg == null) {
            return true;
        }
        return cfg.shouldEntityDripAtLowHealth(id.getPath());
    }

    public static boolean shouldEntityTransformToStains(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String fullEntityId = id.toString(); 

        if (BloodModAPI.hasCustomSettings(fullEntityId)) {
            BloodModAPI.BloodSettings apiSettings = BloodModAPI.getEntityBloodSettings(fullEntityId);
            if (apiSettings != null && apiSettings.getTransformToStains() != null) {
                return apiSettings.getTransformToStains();
            }
        }

        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg != null && cfg.moddedEntities.hasCustomSettings(fullEntityId)) {
            BloodModConfig.ModdedEntities.ModdedEntitySettings moddedSettings =
                    cfg.moddedEntities.getSettings(fullEntityId);
            if (moddedSettings != null) {
                return moddedSettings.transformToStains;
            }
        }

        return true;
    }
}