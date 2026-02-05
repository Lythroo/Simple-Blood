package com.bloodmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal server-side initialization - only registers particle types.
 * All actual blood spawning logic is client-side.
 */
public class BloodMod implements ModInitializer {
    public static final String MOD_ID = "bloodmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Simple Blood (Client-Only) - Server registration...");

        // Register particle types (required on both sides for networking)
        BloodParticles.register();

        LOGGER.info("Simple Blood particle types registered!");
    }

    /**
     * Checks the config for whether this entity type should produce blood.
     * This method is called from client-side code, so it uses the client config.
     */
    public static boolean shouldEntityBleed(LivingEntity entity) {
        // Always use the client config since blood particles only spawn client-side
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null) {
            // Fallback: should not happen, but default to true
            return true;
        }
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return cfg.doesEntityBleed(id.getPath());
    }

    /**
     * Checks if this entity should drip blood at low health.
     * Some entities (skeletons, golems, slimes, etc.) don't have circulatory systems
     * so they shouldn't continuously drip blood when wounded.
     */
    public static boolean shouldEntityDripAtLowHealth(LivingEntity entity) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null) {
            return true;
        }
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return cfg.shouldEntityDripAtLowHealth(id.getPath());
    }
}