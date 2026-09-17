package com.bloodmod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

public final class BloodColor {

    private BloodColor() {}

    public static final class Color {
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

    private static final Color DEFAULT_RED = new Color(0.40f, 0.012f, 0.012f);

    public static Color getBloodColor(LivingEntity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String entityType = id.getPath();
        String fullEntityId = id.toString();

        BloodModConfig config = BloodModClient.getConfig();

        if (entityType.equals("player") && config != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                boolean self = entity.getUUID().equals(client.player.getUUID());
                return addColorVariation(self
                        ? config.player.getClientPlayerColor()
                        : config.player.getOtherPlayersColor());
            }
        }

        if (BloodModAPI.hasCustomSettings(fullEntityId)) {
            BloodModAPI.BloodSettings apiSettings = BloodModAPI.getEntityBloodSettings(fullEntityId);
            if (apiSettings != null) {
                Integer rgb = apiSettings.resolveColorRGB(entity);
                if (rgb != null) {
                    return addColorVariation(new Color(rgb));
                }
            }
        }

        if (config != null && config.moddedEntities.hasCustomSettings(fullEntityId)) {
            BloodModConfig.ModdedEntities.ModdedEntitySettings modded =
                    config.moddedEntities.getSettings(fullEntityId);
            if (modded != null && modded.enabled) {
                return addColorVariation(modded.toColor());
            }
        }

        if (config != null) {
            BloodModConfig.VanillaEntities.EntitySettings vanilla =
                    config.vanillaEntities.getSettings(entityType);
            if (vanilla != null && vanilla.enabled) {
                return addColorVariation(vanilla.toColor());
            }
        }

        return addColorVariation(DEFAULT_RED);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Color addColorVariation(Color baseColor) {
        final float variance = 0.10f;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float red = clamp(baseColor.red * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float green = clamp(baseColor.green * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float blue = clamp(baseColor.blue * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        return new Color(red, green, blue);
    }
}
