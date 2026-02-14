package com.bloodmod;

import com.bloodmod.BloodColor.Color;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Public API for other mods to register custom blood settings for their entities.
 *
 * Usage example:
 * <pre>
 * // In your mod's initialization:
 * BloodModAPI.registerEntityBlood(
 *     Identifier.of("mymod", "custom_mob"),
 *     new BloodModAPI.BloodSettings()
 *         .setColor(0xFF0000)  // Red color
 *         .setBrightness(120)  // 120% brightness
 *         .setCanBleed(true)
 *         .setCanDripAtLowHealth(true)
 * );
 * </pre>
 */
public class BloodModAPI {

    /**
     * Registry of custom blood settings for modded entities.
     * Key: entity identifier (e.g., "mymod:custom_mob")
     * Value: blood settings
     */
    private static final Map<String, BloodSettings> customBloodRegistry = new HashMap<>();

    /**
     * Register custom blood settings for an entity.
     * This will override both default behavior and config settings for this entity.
     *
     * @param entityId The entity identifier (e.g., Identifier.of("mymod", "custom_mob"))
     * @param settings The blood settings to apply
     */
    public static void registerEntityBlood(Identifier entityId, BloodSettings settings) {
        String key = entityId.toString(); // Converts to "mymod:custom_mob"
        customBloodRegistry.put(key, settings);
        BloodMod.LOGGER.info("Registered custom blood settings for entity: {}", key);
    }

    /**
     * Register custom blood settings for an entity using string identifier.
     *
     * @param entityId The entity identifier as a string (e.g., "mymod:custom_mob")
     * @param settings The blood settings to apply
     */
    public static void registerEntityBlood(String entityId, BloodSettings settings) {
        customBloodRegistry.put(entityId, settings);
        BloodMod.LOGGER.info("Registered custom blood settings for entity: {}", entityId);
    }

    /**
     * Get custom blood settings for an entity.
     *
     * @param entityId The entity identifier as a string (e.g., "mymod:custom_mob")
     * @return The blood settings, or null if not registered
     */
    public static BloodSettings getEntityBloodSettings(String entityId) {
        return customBloodRegistry.get(entityId);
    }

    /**
     * Check if an entity has custom blood settings registered via the API.
     *
     * @param entityId The entity identifier as a string
     * @return true if custom settings exist
     */
    public static boolean hasCustomSettings(String entityId) {
        return customBloodRegistry.containsKey(entityId);
    }

    /**
     * Remove custom blood settings for an entity.
     *
     * @param entityId The entity identifier
     */
    public static void unregisterEntityBlood(String entityId) {
        customBloodRegistry.remove(entityId);
        BloodMod.LOGGER.info("Unregistered custom blood settings for entity: {}", entityId);
    }

    /**
     * Clear all registered custom blood settings.
     * Useful for testing or mod reload scenarios.
     */
    public static void clearAllRegistrations() {
        customBloodRegistry.clear();
        BloodMod.LOGGER.info("Cleared all custom blood registrations");
    }

    /**
     * Blood settings for an entity.
     * Use the builder pattern to configure settings.
     */
    public static class BloodSettings {
        private Integer colorRGB = null;        // RGB color (0xRRGGBB)
        private Boolean canBleed = true;        // Can this entity bleed?
        private Boolean canDripAtLowHealth = true; // Can drip blood at low health?
        private Boolean transformToStains = true;  // Transform to stains/fog clouds?

        /**
         * Set the blood color as an RGB integer (0xRRGGBB format).
         * Example: 0xFF0000 for red, 0x00FF00 for green
         */
        public BloodSettings setColor(int rgb) {
            this.colorRGB = rgb;
            return this;
        }

        /**
         * Set the blood color as separate RGB components (0-255 each).
         */
        public BloodSettings setColor(int red, int green, int blue) {
            this.colorRGB = (red << 16) | (green << 8) | blue;
            return this;
        }

        /**
         * Set whether this entity can bleed at all.
         * If false, no blood particles will spawn for this entity.
         */
        public BloodSettings setCanBleed(boolean canBleed) {
            this.canBleed = canBleed;
            return this;
        }

        /**
         * Set whether this entity can drip blood when at low health.
         * Set to false for entities without circulatory systems (constructs, undead, etc.)
         */
        public BloodSettings setCanDripAtLowHealth(boolean canDrip) {
            this.canDripAtLowHealth = canDrip;
            return this;
        }

        /**
         * Set whether particles transform to bloodstains on ground and fog clouds in water.
         * Set to false to keep particles as particles (no transformation).
         */
        public BloodSettings setTransformToStains(boolean transform) {
            this.transformToStains = transform;
            return this;
        }

        /**
         * Get the blood color as a BloodColor.Color object.
         * Returns null if no color was set.
         */
        public Color getColor() {
            if (colorRGB == null) return null;

            float r = ((colorRGB >> 16) & 0xFF) / 255.0f;
            float g = ((colorRGB >> 8) & 0xFF) / 255.0f;
            float b = (colorRGB & 0xFF) / 255.0f;

            return new Color(r, g, b);
        }

        public Integer getColorRGB() {
            return colorRGB;
        }

        public Boolean getCanBleed() {
            return canBleed;
        }

        public Boolean getCanDripAtLowHealth() {
            return canDripAtLowHealth;
        }

        public Boolean getTransformToStains() {
            return transformToStains;
        }
    }
}