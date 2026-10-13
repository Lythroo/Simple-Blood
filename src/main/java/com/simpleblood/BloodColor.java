package com.simpleblood;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Determines blood color for an entity.
 *
 * Resolution priority:
 *   1. Players (you: your configured colour; others: the colour their skin carries, see
 *      {@link SkinColours}, or the configured one for other players)
 *   2. API registrations (via {@link SimpleBloodAPI})
 *   3. Modded-entity config settings
 *   4. Vanilla-entity config settings
 *   5. Default red
 *
 * Every result gets a small random variation for visual interest.
 */
public final class BloodColor {

    /**
     * Marks glowing blood in a stored 0xRRGGBB colour, in the byte above the colour. Everything
     * that reads a colour masks its channels, so the mark rides along unseen wherever the
     * blood goes (puddles, runs, drips off ledges, footprints) and only the renderer looks
     * for it.
     */
    public static final int GLOW_BIT = 0x01000000;

    private BloodColor() {}

    /** RGB color, components in 0.0 - 1.0. */
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

    /** Default darker red blood for most mammals. */
    private static final Color DEFAULT_RED = new Color(0.40f, 0.012f, 0.012f);

    /**
     * Resolve the blood color for an entity (with random variation applied).
     */
    public static Color getBloodColor(LivingEntity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String entityType = id.getPath();
        String fullEntityId = id.toString();

        SimpleBloodConfig config = SimpleBloodClient.getConfig();

        // Players: separate colors for the client player vs everyone else.
        if (entityType.equals("player") && config != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                if (entity.getUUID().equals(client.player.getUUID())) {
                    return addColorVariation(config.player.getClientPlayerColor());
                }
                int carried = config.player.colourFromSkins ? SkinColours.colourOf(entity) : SkinColours.NONE;
                return addColorVariation(carried != SkinColours.NONE
                        ? new Color(carried)
                        : config.player.getOtherPlayersColor());
            }
        }

        // API override - supports a dynamic per-instance colour provider.
        if (SimpleBloodAPI.hasCustomSettings(fullEntityId)) {
            SimpleBloodAPI.BloodSettings apiSettings = SimpleBloodAPI.getEntityBloodSettings(fullEntityId);
            if (apiSettings != null) {
                Integer rgb = apiSettings.resolveColorRGB(entity);
                if (rgb != null) {
                    return addColorVariation(new Color(rgb));
                }
            }
        }

        // Modded-entity config override.
        if (config != null && config.moddedEntities.effective(fullEntityId) != null) {
            SimpleBloodConfig.ModdedEntities.ModdedEntitySettings modded =
                    config.moddedEntities.effective(fullEntityId); // set up, or guessed from its name
            if (modded != null && modded.enabled) {
                return addColorVariation(modded.toColor());
            }
        }

        // Vanilla-entity config.
        if (config != null) {
            SimpleBloodConfig.VanillaEntities.EntitySettings vanilla =
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

    /**
     * Apply a small (+/-10%) random variation per component. Uses the shared
     * thread-local RNG instead of allocating a new {@link java.util.Random} each call.
     */
    private static Color addColorVariation(Color baseColor) {
        final float variance = 0.10f;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float red = clamp(baseColor.red * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float green = clamp(baseColor.green * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        float blue = clamp(baseColor.blue * (1.0f + (random.nextFloat() * 2 - 1) * variance), 0.0f, 1.0f);
        return new Color(red, green, blue);
    }
}
