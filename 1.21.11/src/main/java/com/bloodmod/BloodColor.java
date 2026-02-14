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

public class BloodColor {

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

    private static final Map<Identifier, Color> creeperColorCache = new HashMap<>();

    private static Method getColorMethod = null;
    private static boolean reflectionAttempted = false;

    private static final Color RED = new Color(0.40f, 0.012f, 0.012f);

    private static final Color BRIGHT_RED = new Color(0.50f, 0.03f, 0.03f);

    private static final Color DARK_RED = new Color(0.25f, 0.008f, 0.008f);

    private static final Color PURPLE = new Color(0.45f, 0.10f, 0.45f);

    private static final Color DEEP_PURPLE = new Color(0.20f, 0.08f, 0.30f);

    private static final Color GREEN = new Color(0.15f, 0.45f, 0.10f);

    private static final Color SLIME_GREEN = new Color(0.30f, 0.70f, 0.20f);

    private static final Color BLUE = new Color(0.10f, 0.20f, 0.50f);

    private static final Color DEEP_BLUE = new Color(0.15f, 0.25f, 0.40f);

    private static final Color ORANGE = new Color(0.60f, 0.30f, 0.05f);

    private static final Color BRIGHT_ORANGE = new Color(0.90f, 0.50f, 0.10f);

    private static final Color LAVA = new Color(0.80f, 0.20f, 0.05f);

    private static final Color YELLOW = new Color(0.70f, 0.60f, 0.10f);

    private static final Color GRAY = new Color(0.40f, 0.40f, 0.40f);
    private static final Color LIGHT_GRAY = new Color(0.50f, 0.50f, 0.52f);

    private static final Color WHITE = new Color(0.85f, 0.85f, 0.85f);
    private static final Color ICY_WHITE = new Color(0.85f, 0.90f, 0.95f);

    private static final Color HAPPY_PINK = new Color(0.95f, 0.75f, 0.85f);

    private static final Color CYAN = new Color(0.40f, 0.70f, 0.75f);

    private static final Color NIGHT_BLUE = new Color(0.15f, 0.20f, 0.35f);

    private static final Color POTION_GREEN = new Color(0.25f, 0.35f, 0.15f);

    private static final Color SPIRIT_BLUE = new Color(0.60f, 0.70f, 0.85f);

    private static final Color CREEPER_GREEN = new Color(0.12f, 0.42f, 0.08f);

    private static final Color BONE = new Color(0.75f, 0.72f, 0.65f);

    private static final Color BLACK = new Color(0.08f, 0.08f, 0.08f);

    private static final Color COPPER_FRESH = new Color(0.75f, 0.38f, 0.20f);

    private static final Color COPPER_EXPOSED = new Color(0.65f, 0.42f, 0.32f);

    private static final Color COPPER_WEATHERED = new Color(0.45f, 0.50f, 0.35f);

    private static final Color COPPER_OXIDIZED = new Color(0.30f, 0.55f, 0.40f);

    private static final Color WOOD_SAP = new Color(0.55f, 0.35f, 0.15f);

    private static final Color DRIED_DUST = new Color(0.60f, 0.52f, 0.40f);

    public static Color getBloodColor(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String entityType = id.getPath();
        String fullEntityId = id.toString(); 

        if (BloodModAPI.hasCustomSettings(fullEntityId)) {
            BloodModAPI.BloodSettings apiSettings = BloodModAPI.getEntityBloodSettings(fullEntityId);
            if (apiSettings != null && apiSettings.getColor() != null) {
                return addColorVariation(apiSettings.getColor());
            }
        }

        BloodModConfig config = BloodModClient.getConfig();
        if (config != null && config.moddedEntities.hasCustomSettings(fullEntityId)) {
            BloodModConfig.ModdedEntities.ModdedEntitySettings moddedSettings =
                    config.moddedEntities.getSettings(fullEntityId);
            if (moddedSettings != null && moddedSettings.enabled) {
                return addColorVariation(moddedSettings.toColor());
            }
        }

        if (config != null && config.bloodColors.enableCustomColors) {

            if (entityType.equals("player") && entity instanceof net.minecraft.entity.player.PlayerEntity) {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                boolean isClientPlayer = client.player != null && entity.getUuid().equals(client.player.getUuid());

                int colorInt = isClientPlayer ? config.bloodColors.playerBlood : config.bloodColors.otherPlayersBlood;
                Color customColor = new Color(colorInt);
                return addColorVariation(customColor);
            }

            Color customColor = getCustomColorForEntity(entityType, config);
            if (customColor != null) {

                return addColorVariation(customColor);
            }
        }

        Color baseColor;

        if (entityType.equals("creeper") && entity instanceof CreeperEntity) {
            baseColor = getCreeperTextureColor(entity);
        }

        else if (entityType.equals("copper_golem")) {
            baseColor = getCopperGolemColor(entity);
        }
        else {
            baseColor = getBaseColorForEntity(entityType);
        }

        return addColorVariation(baseColor);
    }

    private static Color getCreeperTextureColor(LivingEntity entity) {
        try {

            Identifier textureId = Identifier.of("minecraft", "textures/entity/creeper/creeper.png");

            if (creeperColorCache.containsKey(textureId)) {
                return creeperColorCache.get(textureId);
            }

            Color sampledColor = sampleTextureColor(textureId);

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

        return CREEPER_GREEN;
    }

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

            if (!reflectionAttempted) {
                reflectionAttempted = true;
                try {

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

            int width = image.getWidth();
            int height = image.getHeight();
            int startX = width / 4;
            int endX = (width * 3) / 4;
            int startY = height / 4;
            int endY = (height * 3) / 4;

            long totalR = 0, totalG = 0, totalB = 0;
            int sampleCount = 0;

            for (int x = startX; x < endX; x += 2) {
                for (int y = startY; y < endY; y += 2) {
                    try {
                        int color = (Integer) getColorMethod.invoke(image, x, y);

                        int alpha = (color >> 24) & 0xFF;
                        if (alpha < 200) continue; 

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

    private static Color getCopperGolemColor(LivingEntity entity) {
        try {

            int age = entity.age;

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

        return COPPER_FRESH;
    }

    private static Color getBaseColorForEntity(String entityType) {

        if (entityType.equals("skeleton") || entityType.equals("stray") ||
                entityType.equals("bogged") || entityType.equals("skeleton_horse")) {
            return BONE;
        }

        if (entityType.equals("wither_skeleton")) {
            return BLACK;
        }

        if (entityType.equals("parched")) {
            return DRIED_DUST;
        }

        if (entityType.equals("zombie") || entityType.equals("zombie_villager") ||
                entityType.equals("husk") || entityType.equals("drowned") ||
                entityType.equals("zombie_horse") || entityType.equals("zombified_piglin") ||
                entityType.equals("zoglin")) {
            return DARK_RED;
        }

        if (entityType.equals("enderman") || entityType.equals("endermite") ||
                entityType.equals("shulker") || entityType.equals("ender_dragon")) {
            return PURPLE;
        }

        if (entityType.equals("spider") || entityType.equals("cave_spider")) {
            return GREEN;
        }

        if (entityType.equals("slime")) {
            return SLIME_GREEN;
        }

        if (entityType.equals("squid") || entityType.equals("glow_squid")) {
            return BLUE;
        }

        if (entityType.equals("guardian") || entityType.equals("elder_guardian")) {
            return DEEP_BLUE;
        }

        if (entityType.equals("dolphin") || entityType.equals("axolotl")) {
            return BRIGHT_RED; 

        }

        if (entityType.equals("cod") || entityType.equals("salmon") ||
                entityType.equals("tropical_fish") || entityType.equals("pufferfish") ||
                entityType.equals("tadpole")) {
            return RED;
        }

        if (entityType.equals("frog")) {
            return RED;
        }

        if (entityType.equals("blaze")) {
            return BRIGHT_ORANGE;
        }

        if (entityType.equals("magma_cube")) {
            return LAVA;
        }

        if (entityType.equals("hoglin") || entityType.equals("piglin") ||
                entityType.equals("piglin_brute")) {
            return ORANGE;
        }

        if (entityType.equals("strider")) {
            return new Color(0.70f, 0.25f, 0.10f);
        }

        if (entityType.equals("ghast")) {
            return WHITE;
        }

        if (entityType.equals("happy_ghast")) {
            return WHITE;
        }

        if (entityType.equals("bee")) {
            return YELLOW;
        }

        if (entityType.equals("creeper")) {
            return CREEPER_GREEN;
        }

        if (entityType.equals("iron_golem")) {
            return GRAY;
        }

        if (entityType.equals("snow_golem")) {
            return ICY_WHITE;
        }

        if (entityType.equals("creaking")) {
            return WOOD_SAP;
        }

        if (entityType.equals("warden")) {
            return DEEP_PURPLE;
        }

        if (entityType.equals("wither")) {
            return BLACK;
        }

        if (entityType.equals("breeze")) {
            return CYAN;
        }

        if (entityType.equals("phantom")) {
            return NIGHT_BLUE;
        }

        if (entityType.equals("vex") || entityType.equals("allay")) {
            return SPIRIT_BLUE;
        }

        if (entityType.equals("witch")) {
            return POTION_GREEN;
        }

        if (entityType.equals("silverfish")) {
            return LIGHT_GRAY;
        }

        if (entityType.equals("ravager")) {
            return new Color(0.45f, 0.08f, 0.08f);
        }

        return RED;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Color getCustomColorForEntity(String entityType, BloodModConfig config) {
        int colorInt = 0;
        int brightness = 100;

        if (entityType.equals("player")) {

            return null;
        }

        else if (entityType.equals("zombie") || entityType.equals("zombie_villager") ||
                entityType.equals("husk") || entityType.equals("drowned") ||
                entityType.equals("zombie_horse") || entityType.equals("zombified_piglin") ||
                entityType.equals("zoglin")) {
            colorInt = config.bloodColors.zombieBlood;
            brightness = config.bloodColors.zombieBloodBrightness;
        }

        else if (entityType.equals("skeleton") || entityType.equals("stray") ||
                entityType.equals("wither_skeleton") || entityType.equals("skeleton_horse")) {
            colorInt = config.bloodColors.skeletonBlood;
            brightness = config.bloodColors.skeletonBloodBrightness;
        }

        else if (entityType.equals("enderman") || entityType.equals("endermite") ||
                entityType.equals("shulker") || entityType.equals("ender_dragon")) {
            colorInt = config.bloodColors.endBlood;
            brightness = config.bloodColors.endBloodBrightness;
        }

        else if (entityType.equals("spider") || entityType.equals("cave_spider")) {
            colorInt = config.bloodColors.spiderBlood;
            brightness = config.bloodColors.spiderBloodBrightness;
        }

        else if (entityType.equals("slime")) {
            colorInt = config.bloodColors.slimeBlood;
            brightness = config.bloodColors.slimeBloodBrightness;
        }

        else if (entityType.equals("squid") || entityType.equals("glow_squid") ||
                entityType.equals("guardian") || entityType.equals("elder_guardian")) {
            colorInt = config.bloodColors.aquaticBlood;
            brightness = config.bloodColors.aquaticBloodBrightness;
        }

        else if (entityType.equals("hoglin") || entityType.equals("piglin") ||
                entityType.equals("piglin_brute") || entityType.equals("strider") ||
                entityType.equals("magma_cube") || entityType.equals("ghast") ||
                entityType.equals("happy_ghast")) {
            colorInt = config.bloodColors.netherBlood;
            brightness = config.bloodColors.netherBloodBrightness;
        }

        else if (entityType.equals("blaze")) {
            colorInt = config.bloodColors.blazeBlood;
            brightness = config.bloodColors.blazeBloodBrightness;
        }

        else if (entityType.equals("bee")) {
            colorInt = config.bloodColors.beeBlood;
            brightness = config.bloodColors.beeBloodBrightness;
        }

        else if (entityType.equals("creeper")) {
            colorInt = config.bloodColors.creeperBlood;
            brightness = config.bloodColors.creeperBloodBrightness;
        }

        else if (entityType.equals("iron_golem") || entityType.equals("snow_golem") ||
                entityType.equals("copper_golem")) {
            colorInt = config.bloodColors.golemBlood;
            brightness = config.bloodColors.golemBloodBrightness;
        }

        else {
            colorInt = config.bloodColors.defaultBlood;
            brightness = config.bloodColors.defaultBloodBrightness;
        }

        return config.bloodColors.intToColor(colorInt, brightness);
    }

    private static Color addColorVariation(Color baseColor) {
        float variance = 0.10f;
        java.util.Random random = new java.util.Random();
        float red = clamp(baseColor.red * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float green = clamp(baseColor.green * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float blue = clamp(baseColor.blue * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);

        return new Color(red, green, blue);
    }
}