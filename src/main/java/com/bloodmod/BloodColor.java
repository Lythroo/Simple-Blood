package com.bloodmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Determines blood color based on entity type.
 * Uses biologically-inspired color categories for different entity types.
 * Special handling for creepers - samples their texture dynamically via reflection.
 */
public class BloodColor {

    /**
     * Represents RGB color values for blood particles
     */
    public static class Color {
        public final float red;
        public final float green;
        public final float blue;

        public Color(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public Color(int hex) {
            this.red = ((hex >> 16) & 0xFF) / 255.0f;
            this.green = ((hex >> 8) & 0xFF) / 255.0f;
            this.blue = (hex & 0xFF) / 255.0f;
        }
    }

    // Cache for creeper texture colors (identifier -> sampled color)
    private static final Map<Identifier, Color> creeperColorCache = new HashMap<>();

    // Cached reflection method for accessing NativeImage.getColor
    private static Method getColorMethod = null;
    private static boolean reflectionAttempted = false;

    // ═══════════════════════════════════════════════════════════════════════
    // COLOR CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════

    // Standard red blood for most mammals
    private static final Color RED = new Color(0.40f, 0.02f, 0.02f);

    // Bright red for high-oxygen creatures
    private static final Color BRIGHT_RED = new Color(0.50f, 0.03f, 0.03f);

    // Darker red for zombies (coagulated)
    private static final Color DARK_RED = new Color(0.35f, 0.05f, 0.05f);

    // Purple/magenta blood for End creatures
    private static final Color PURPLE = new Color(0.45f, 0.10f, 0.45f);

    // Dark purple for warden
    private static final Color DEEP_PURPLE = new Color(0.20f, 0.08f, 0.30f);

    // Green blood for spiders
    private static final Color GREEN = new Color(0.15f, 0.45f, 0.10f);

    // Bright green for slimes
    private static final Color SLIME_GREEN = new Color(0.30f, 0.70f, 0.20f);

    // Blue blood for aquatic creatures (hemocyanin-based)
    private static final Color BLUE = new Color(0.10f, 0.20f, 0.50f);

    // Dark blue for guardians
    private static final Color DEEP_BLUE = new Color(0.15f, 0.25f, 0.40f);

    // Orange/amber blood for nether creatures
    private static final Color ORANGE = new Color(0.60f, 0.30f, 0.05f);

    // Bright orange for blazes
    private static final Color BRIGHT_ORANGE = new Color(0.90f, 0.50f, 0.10f);

    // Lava-like for magma cubes
    private static final Color LAVA = new Color(0.80f, 0.20f, 0.05f);

    // Yellow/gold blood for bees
    private static final Color YELLOW = new Color(0.70f, 0.60f, 0.10f);

    // Gray tones for constructs
    private static final Color GRAY = new Color(0.40f, 0.40f, 0.40f);
    private static final Color LIGHT_GRAY = new Color(0.50f, 0.50f, 0.52f);

    // White/pale colors
    private static final Color WHITE = new Color(0.85f, 0.85f, 0.85f);
    private static final Color ICY_WHITE = new Color(0.85f, 0.90f, 0.95f);

    // Happy pink for happy ghast
    private static final Color HAPPY_PINK = new Color(0.95f, 0.75f, 0.85f);

    // Cyan/light blue for breeze
    private static final Color CYAN = new Color(0.40f, 0.70f, 0.75f);

    // Night blue for phantom
    private static final Color NIGHT_BLUE = new Color(0.15f, 0.20f, 0.35f);

    // Potion green for witch
    private static final Color POTION_GREEN = new Color(0.25f, 0.35f, 0.15f);

    // Pale blue for spirits
    private static final Color SPIRIT_BLUE = new Color(0.60f, 0.70f, 0.85f);

    // Default bright green for creepers (fallback)
    private static final Color CREEPER_GREEN = new Color(0.12f, 0.42f, 0.08f);

    // Bone dust for skeletons (off-white particles representing bone fragments/marrow)
    private static final Color BONE = new Color(0.75f, 0.72f, 0.65f);

    // Black/very dark for wither skeleton
    private static final Color BLACK = new Color(0.08f, 0.08f, 0.08f);

    // Copper colors for copper golem oxidation stages
    // Fresh copper - bright orange-brown
    private static final Color COPPER_FRESH = new Color(0.75f, 0.38f, 0.20f);
    // Exposed copper - slightly oxidized, more brown
    private static final Color COPPER_EXPOSED = new Color(0.65f, 0.42f, 0.32f);
    // Weathered copper - greenish-brown patina starting
    private static final Color COPPER_WEATHERED = new Color(0.45f, 0.50f, 0.35f);
    // Oxidized copper - full green patina (verdigris)
    private static final Color COPPER_OXIDIZED = new Color(0.30f, 0.55f, 0.40f);

    // Wood sap/resin for Creaking (orange-brown tree sap)
    private static final Color WOOD_SAP = new Color(0.55f, 0.35f, 0.15f);

    // Dried/mummified dust for Parched (tan/brown dust particles)
    private static final Color DRIED_DUST = new Color(0.60f, 0.52f, 0.40f);

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get the blood color for a given entity.
     * Returns a color with slight random variation for visual interest.
     * Special handling for creepers - samples their actual texture color.
     *
     * @param entity The entity to get blood color for
     * @return Color object with RGB values (0.0 - 1.0)
     */
    public static Color getBloodColor(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String entityType = id.getPath();

        Color baseColor;

        // Special handling for creepers - sample their texture
        if (entityType.equals("creeper") && entity instanceof CreeperEntity) {
            baseColor = getCreeperTextureColor(entity);
        }
        // Special handling for copper golem - check oxidation stage
        else if (entityType.equals("copper_golem")) {
            baseColor = getCopperGolemColor(entity);
        }
        else {
            baseColor = getBaseColorForEntity(entityType);
        }

        // Add slight random variation (±10%) for visual interest
        float variance = 0.10f;
        java.util.Random random = new java.util.Random();
        float red = clamp(baseColor.red * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float green = clamp(baseColor.green * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float blue = clamp(baseColor.blue * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);

        return new Color(red, green, blue);
    }

    /**
     * Sample the creeper's texture to get its dominant color using reflection.
     * This allows blood color to match texture packs!
     */
    private static Color getCreeperTextureColor(LivingEntity entity) {
        try {
            // The creeper texture path
            Identifier textureId = Identifier.of("minecraft", "textures/entity/creeper/creeper.png");

            // Check cache first
            if (creeperColorCache.containsKey(textureId)) {
                return creeperColorCache.get(textureId);
            }

            // Try to sample the texture
            Color sampledColor = sampleTextureColor(textureId);

            // Cache the result
            if (sampledColor != null) {
                creeperColorCache.put(textureId, sampledColor);
                BloodMod.LOGGER.info("Successfully sampled creeper texture! Blood color: R={}, G={}, B={}",
                        sampledColor.red, sampledColor.green, sampledColor.blue);
                return sampledColor;
            } else {
                BloodMod.LOGGER.debug("Failed to sample creeper texture, using default green");
            }
        } catch (Exception e) {
            BloodMod.LOGGER.debug("Exception while sampling creeper texture: {}", e.getMessage());
        }

        // Fallback to default creeper green
        return CREEPER_GREEN;
    }

    /**
     * Sample a texture to get its average color using reflection to access private methods.
     * Focuses on the body area (middle portion) to avoid transparent areas.
     */
    private static Color sampleTextureColor(Identifier textureId) {
        NativeImage image = null;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getResourceManager() == null) {
                return null;
            }

            Optional<Resource> resourceOpt = client.getResourceManager().getResource(textureId);
            if (resourceOpt.isEmpty()) {
                return null;
            }

            InputStream inputStream = resourceOpt.get().getInputStream();
            image = NativeImage.read(inputStream);

            // Try to get the getColor method via reflection (only once)
            if (!reflectionAttempted) {
                reflectionAttempted = true;
                try {
                    // Try different possible method names across versions
                    try {
                        getColorMethod = NativeImage.class.getDeclaredMethod("getColor", int.class, int.class);
                    } catch (NoSuchMethodException e1) {
                        try {
                            getColorMethod = NativeImage.class.getDeclaredMethod("getPixelColor", int.class, int.class);
                        } catch (NoSuchMethodException e2) {
                            getColorMethod = NativeImage.class.getDeclaredMethod("getPixelRgba", int.class, int.class);
                        }
                    }
                    getColorMethod.setAccessible(true);
                    BloodMod.LOGGER.info("Successfully accessed NativeImage color method: {}", getColorMethod.getName());
                } catch (Exception e) {
                    BloodMod.LOGGER.warn("Could not find any color reading method in NativeImage: {}", e.getMessage());
                    return null;
                }
            }

            if (getColorMethod == null) {
                return null;
            }

            // Sample from the middle portion of the texture
            int width = image.getWidth();
            int height = image.getHeight();
            int startX = width / 4;
            int endX = (width * 3) / 4;
            int startY = height / 4;
            int endY = (height * 3) / 4;

            long totalR = 0, totalG = 0, totalB = 0;
            int sampleCount = 0;

            // Sample every few pixels for performance
            for (int x = startX; x < endX; x += 2) {
                for (int y = startY; y < endY; y += 2) {
                    try {
                        int color = (Integer) getColorMethod.invoke(image, x, y);

                        // Try ABGR format first (most common)
                        int alpha = (color >> 24) & 0xFF;
                        if (alpha < 200) continue; // Skip transparent pixels

                        int r = (color >> 0) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = (color >> 16) & 0xFF;

                        totalR += r;
                        totalG += g;
                        totalB += b;
                        sampleCount++;
                    } catch (Exception e) {
                        continue;
                    }
                }
            }

            if (sampleCount > 0) {
                float avgR = (totalR / (float)sampleCount) / 255.0f;
                float avgG = (totalG / (float)sampleCount) / 255.0f;
                float avgB = (totalB / (float)sampleCount) / 255.0f;

                // Darken for blood effect
                float darkenFactor = 0.4f;
                return new Color(avgR * darkenFactor, avgG * darkenFactor, avgB * darkenFactor);
            }
        } catch (Exception e) {
            BloodMod.LOGGER.debug("Error sampling texture: {}", e.getMessage());
        } finally {
            if (image != null) {
                image.close();
            }
        }

        return null;
    }

    /**
     * Get the blood color for a copper golem based on its oxidation stage.
     * Copper golems oxidize over time like copper blocks in Minecraft.
     *
     * Note: Since we can't easily access NBT data in 1.21.11 without the entity class itself,
     * we'll use a simple heuristic based on entity age or just return a default copper color.
     * For a proper implementation, the copper golem mod would need to expose its oxidation
     * state through entity data trackers.
     */
    private static Color getCopperGolemColor(LivingEntity entity) {
        try {
            // Try to access entity age as a proxy for oxidation
            // This is a fallback since we can't easily access custom NBT in 1.21.11
            int age = entity.age;

            // Map age to oxidation levels (very rough approximation)
            // Fresh: 0-6000 ticks (5 minutes)
            // Exposed: 6000-12000 ticks (10 minutes)
            // Weathered: 12000-18000 ticks (15 minutes)
            // Oxidized: 18000+ ticks
            if (age < 6000) {
                BloodMod.LOGGER.debug("Copper golem age {}: using fresh copper", age);
                return COPPER_FRESH;
            } else if (age < 12000) {
                BloodMod.LOGGER.debug("Copper golem age {}: using exposed copper", age);
                return COPPER_EXPOSED;
            } else if (age < 18000) {
                BloodMod.LOGGER.debug("Copper golem age {}: using weathered copper", age);
                return COPPER_WEATHERED;
            } else {
                BloodMod.LOGGER.debug("Copper golem age {}: using oxidized copper", age);
                return COPPER_OXIDIZED;
            }
        } catch (Exception e) {
            BloodMod.LOGGER.debug("Could not determine copper golem oxidation, using fresh copper color: {}", e.getMessage());
        }

        // Fallback to fresh copper
        return COPPER_FRESH;
    }

    /**
     * Get the base blood color for an entity type (without random variation)
     */
    private static Color getBaseColorForEntity(String entityType) {
        // ══════════════════════════════════════════════════════════════════
        // SKELETONS - Bone dust/fragments (off-white particles)
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("skeleton") || entityType.equals("stray") ||
                entityType.equals("bogged") || entityType.equals("skeleton_horse")) {
            return BONE;
        }

        // Wither skeleton - very dark/black particles
        if (entityType.equals("wither_skeleton")) {
            return BLACK;
        }

        // Parched - dried/mummified mob, emits tan/brown dust particles
        if (entityType.equals("parched")) {
            return DRIED_DUST;
        }

        // ══════════════════════════════════════════════════════════════════
        // ZOMBIES - Coagulated dark red blood
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("zombie") || entityType.equals("zombie_villager") ||
                entityType.equals("husk") || entityType.equals("drowned") ||
                entityType.equals("zombie_horse") || entityType.equals("zombified_piglin") ||
                entityType.equals("zoglin")) {
            return DARK_RED;
        }

        // ══════════════════════════════════════════════════════════════════
        // END CREATURES - Purple blood
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("enderman") || entityType.equals("endermite") ||
                entityType.equals("shulker") || entityType.equals("ender_dragon")) {
            return PURPLE;
        }

        // ══════════════════════════════════════════════════════════════════
        // SPIDERS - Green blood
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("spider") || entityType.equals("cave_spider")) {
            return GREEN;
        }

        // ══════════════════════════════════════════════════════════════════
        // SLIMES - Bright green
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("slime")) {
            return SLIME_GREEN;
        }

        // ══════════════════════════════════════════════════════════════════
        // AQUATIC CREATURES
        // ══════════════════════════════════════════════════════════════════

        // Squid and similar invertebrates have blue blood (hemocyanin-based)
        if (entityType.equals("squid") || entityType.equals("glow_squid")) {
            return BLUE;
        }

        // Guardians are fish-like but have a darker, more alien blood
        if (entityType.equals("guardian") || entityType.equals("elder_guardian")) {
            return DEEP_BLUE;
        }

        // Dolphins and axolotls are vertebrates with red blood
        if (entityType.equals("dolphin") || entityType.equals("axolotl")) {
            return BRIGHT_RED; // Mammals/amphibians with high oxygen
        }

        // Fish have standard red blood (they're vertebrates)
        if (entityType.equals("cod") || entityType.equals("salmon") ||
                entityType.equals("tropical_fish") || entityType.equals("pufferfish") ||
                entityType.equals("tadpole")) {
            return RED;
        }

        // Frogs (amphibians)
        if (entityType.equals("frog")) {
            return RED;
        }

        // ══════════════════════════════════════════════════════════════════
        // NETHER CREATURES - Orange/Fire blood
        // ══════════════════════════════════════════════════════════════════

        // Blaze - bright orange fire
        if (entityType.equals("blaze")) {
            return BRIGHT_ORANGE;
        }

        // Magma cube - lava-like
        if (entityType.equals("magma_cube")) {
            return LAVA;
        }

        // Nether mobs - orange blood
        if (entityType.equals("hoglin") || entityType.equals("piglin") ||
                entityType.equals("piglin_brute")) {
            return ORANGE;
        }

        // Strider - lava-like
        if (entityType.equals("strider")) {
            return new Color(0.70f, 0.25f, 0.10f);
        }

        // Ghast - pale white
        if (entityType.equals("ghast")) {
            return WHITE;
        }

        // Happy Ghast - same pale white as regular ghast
        if (entityType.equals("happy_ghast")) {
            return WHITE;
        }

        // ══════════════════════════════════════════════════════════════════
        // BEES - Yellow/Gold blood (Hemolymph)
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("bee")) {
            return YELLOW;
        }

        // ══════════════════════════════════════════════════════════════════
        // CREEPER - Explosive Green
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("creeper")) {
            return CREEPER_GREEN;
        }

        // ══════════════════════════════════════════════════════════════════
        // GOLEMS & CONSTRUCTS - Gray/White
        // ══════════════════════════════════════════════════════════════════
        if (entityType.equals("iron_golem")) {
            return GRAY;
        }

        if (entityType.equals("snow_golem")) {
            return ICY_WHITE;
        }

        // Creaking - wooden construct, bleeds orange-brown tree sap/resin
        if (entityType.equals("creaking")) {
            return WOOD_SAP;
        }

        // ══════════════════════════════════════════════════════════════════
        // BOSSES & SPECIAL
        // ══════════════════════════════════════════════════════════════════

        // Warden - deep dark purple
        if (entityType.equals("warden")) {
            return DEEP_PURPLE;
        }

        // Wither - very dark/black
        if (entityType.equals("wither")) {
            return BLACK;
        }

        // ══════════════════════════════════════════════════════════════════
        // ELEMENTALS & SPIRITS
        // ══════════════════════════════════════════════════════════════════

        // Breeze - cyan/light blue
        if (entityType.equals("breeze")) {
            return CYAN;
        }

        // Phantom - night blue
        if (entityType.equals("phantom")) {
            return NIGHT_BLUE;
        }

        // Vex & Allay - pale spirit blue
        if (entityType.equals("vex") || entityType.equals("allay")) {
            return SPIRIT_BLUE;
        }

        // ══════════════════════════════════════════════════════════════════
        // MAGIC USERS
        // ══════════════════════════════════════════════════════════════════

        // Witch - potion green
        if (entityType.equals("witch")) {
            return POTION_GREEN;
        }

        // ══════════════════════════════════════════════════════════════════
        // MISC HOSTILES
        // ══════════════════════════════════════════════════════════════════

        // Silverfish - light gray
        if (entityType.equals("silverfish")) {
            return LIGHT_GRAY;
        }

        // Ravager - aggressive dark red
        if (entityType.equals("ravager")) {
            return new Color(0.45f, 0.08f, 0.08f);
        }

        // ══════════════════════════════════════════════════════════════════
        // DEFAULT: Standard Red Blood
        // ══════════════════════════════════════════════════════════════════
        // This covers all mammals and most other creatures:
        // - All passive animals (cow, pig, sheep, horse, llama, etc.)
        // - Villagers and illagers (except witch)
        // - Players
        // - Wolves, cats, foxes, ocelots
        // - Polar bears, pandas, goats
        // - Any modded entities not explicitly listed
        return RED;
    }

    /**
     * Clamp a float value between min and max
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}