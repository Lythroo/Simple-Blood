package com.bloodmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BloodModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = getConfigPath();

    public GeneralSettings general = new GeneralSettings();
    public PlayerSettings player = new PlayerSettings();
    public ParticleSettings particles = new ParticleSettings();
    public SurfaceSettings surfaces = new SurfaceSettings();
    public HitBurstSettings hitBurst = new HitBurstSettings();
    public DeathBurstSettings deathBurst = new DeathBurstSettings();
    public LowHealthSettings lowHealth = new LowHealthSettings();
    public AudioSettings audio = new AudioSettings();
    public UnderwaterSettings underwater = new UnderwaterSettings();
    public DirectionalSettings directional = new DirectionalSettings();
    public VanillaEntities vanillaEntities = new VanillaEntities();
    public ModdedEntities moddedEntities = new ModdedEntities();

    private static Path getConfigPath() {
        try {
            //? if fabric {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            //?} else {
            /*Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            *///?}
            return configDir.resolve("bloodmod.json");
        } catch (Exception e) {
            BloodMod.LOGGER.error("Failed to get config path", e);
            return null;
        }
    }

    public static void save(BloodModConfig config) {
        if (CONFIG_PATH == null) {
            BloodMod.LOGGER.error("Config path is null, cannot save");
            return;
        }

        try {
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json);
            BloodMod.LOGGER.info("Config saved to {}", CONFIG_PATH);
        } catch (IOException e) {
            BloodMod.LOGGER.error("Failed to save config", e);
        }
    }

    public static BloodModConfig load() {
        if (CONFIG_PATH == null || !Files.exists(CONFIG_PATH)) {
            BloodMod.LOGGER.info("Config file not found, using defaults");
            return defaults();
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            BloodModConfig config = GSON.fromJson(json, BloodModConfig.class);
            if (config == null) throw new IllegalStateException("empty config file");
            config.fillGaps();
            config.vanillaEntities.ensureDefaults();
            BloodMod.LOGGER.info("Config loaded from {}", CONFIG_PATH);
            return config;
        } catch (Exception e) {
            BloodMod.LOGGER.error("Failed to load config, using defaults", e);
            return defaults();
        }
    }

    public static BloodModConfig defaults() {
        BloodModConfig config = new BloodModConfig();
        config.vanillaEntities.initializeDefaults();
        return config;
    }

    public void fillGaps() {
        if (general == null) general = new GeneralSettings();
        if (player == null) player = new PlayerSettings();
        if (particles == null) particles = new ParticleSettings();
        if (surfaces == null) surfaces = new SurfaceSettings();
        if (hitBurst == null) hitBurst = new HitBurstSettings();
        if (deathBurst == null) deathBurst = new DeathBurstSettings();
        if (lowHealth == null) lowHealth = new LowHealthSettings();
        if (audio == null) audio = new AudioSettings();
        if (underwater == null) underwater = new UnderwaterSettings();
        if (directional == null) directional = new DirectionalSettings();
        if (vanillaEntities == null) vanillaEntities = new VanillaEntities();
        if (moddedEntities == null) moddedEntities = new ModdedEntities();
    }

    public boolean globalEnabled() { return general.modEnabled; }
    public float masterAmount() { return Math.max(0f, general.bloodAmount / 100.0f); }
    public int particleBudget() { return Math.max(50, general.particleBudget); }
    public float surfaceAmount() { return (surfaces.amount / 100.0f) * masterAmount(); }
    public boolean playerBleed() { return player.playerBleed; }
    public int clientPlayerBloodColor() { return player.clientPlayerBloodColor; }
    public int otherPlayersBloodColor() { return player.otherPlayersBloodColor; }

    public boolean hitBurstEnabled() { return particles.hitBurst; }
    public boolean deathBurstEnabled() { return particles.deathBurst; }
    public boolean lowHealthEnabled() { return particles.lowHealthDrip; }

    public float particleSizeMultiplier() { return particles.getParticleSizeMultiplier() * chunkyDropScale(); }
    public float chunkyDropScale() { return surfaces.enabled && surfaces.resolution == 8 ? 1.75f : 1f; }
    public float chunkyStreakScale() { return surfaces.enabled && surfaces.resolution == 8 ? 1.3f : 1f; }
    public float particleLifetimeMultiplier() { return particles.getParticleLifetimeMultiplier(); }
    public float particleGravityMultiplier() { return particles.getParticleGravityMultiplier(); }
    public float particleDragMultiplier() { return particles.getParticleDragMultiplier(); }

    public float burstIntensityMultiplier() { return hitBurst.getBurstIntensityMultiplier() * masterAmount(); }
    public float burstDurationMultiplier() { return hitBurst.getBurstDurationMultiplier(); }
    public float burstSpreadMultiplier() { return hitBurst.getBurstSpreadMultiplier(); }
    public long damageCooldownMs() { return hitBurst.getDamageCooldownMs(); }

    public float deathIntensityMultiplier() { return deathBurst.getDeathIntensityMultiplier() * masterAmount(); }
    public float deathSpreadMultiplier() { return deathBurst.getDeathSpreadMultiplier(); }

    public float lowHealthThreshold() { return lowHealth.getThresholdAsFloat(); }
    public float dripFrequencyMultiplier() { return lowHealth.getDripFrequencyMultiplier(); }
    public float dripIntensityMultiplier() { return lowHealth.getDripIntensityMultiplier() * masterAmount(); }
    public long dripWindowMs() { return lowHealth.getDripWindowMs(); }

    public boolean soundEnabled() { return audio.soundEnabled; }
    public float soundVolumeMultiplier() { return audio.getSoundVolumeMultiplier(); }
    public float soundPitchMultiplier() { return audio.getSoundPitchMultiplier(); }

    public boolean underwaterFogEnabled() { return underwater.transformToFog; }

    public boolean directionalEnabled() { return directional.enabled; }
    public float directionalShare() { return directional.share / 100.0f; }
    public boolean weaponFlavourEnabled() { return directional.weaponFlavour; }
    public boolean entrySpatterEnabled() { return directional.entrySpatter; }
    public float fogLifetimeMultiplier() { return underwater.getFogLifetimeMultiplier(); }
    public float fogOpacityMultiplier() { return underwater.getFogOpacityMultiplier(); }
    public float fogSizeMultiplier() { return underwater.getFogSizeMultiplier(); }

    public boolean bloodStainsEnabled() { return surfaces.enabled; }

    public boolean doesEntityBleed(String entityType) {
        return vanillaEntities.doesEntityBleed(entityType);
    }

    public boolean shouldEntityDripAtLowHealth(String entityType) {
        return vanillaEntities.shouldEntityDripAtLowHealth(entityType);
    }

    public static class GeneralSettings {
        public boolean modEnabled = true;
        public int bloodAmount = 100;
        public int particleBudget = 1500;
    }

    public static class PlayerSettings {
        public boolean playerBleed = true;
        public int clientPlayerBloodColor = 0x660303;
        public int otherPlayersBloodColor = 0x660303;

        public BloodColor.Color getClientPlayerColor() {
            float r = ((clientPlayerBloodColor >> 16) & 0xFF) / 255.0f;
            float g = ((clientPlayerBloodColor >> 8) & 0xFF) / 255.0f;
            float b = (clientPlayerBloodColor & 0xFF) / 255.0f;
            return new BloodColor.Color(r, g, b);
        }

        public BloodColor.Color getOtherPlayersColor() {
            float r = ((otherPlayersBloodColor >> 16) & 0xFF) / 255.0f;
            float g = ((otherPlayersBloodColor >> 8) & 0xFF) / 255.0f;
            float b = (otherPlayersBloodColor & 0xFF) / 255.0f;
            return new BloodColor.Color(r, g, b);
        }
    }

    public static class ParticleSettings {
        public boolean hitBurst = true;
        public boolean deathBurst = true;
        public boolean lowHealthDrip = true;
        public int particleSize = 210;
        public int particleLifetime = 100;
        public int particleGravity = 100;
        public int particleDrag = 100;

        public float getParticleSizeMultiplier() {
            return particleSize / 100.0f;
        }

        public float getParticleLifetimeMultiplier() {
            return particleLifetime / 100.0f;
        }

        public float getParticleGravityMultiplier() {
            return particleGravity / 100.0f;
        }

        public float getParticleDragMultiplier() {
            return particleDrag / 100.0f;
        }
    }

    public static class SurfaceSettings {
        public boolean enabled = true;
        public int resolution = 16;
        public String highlightMode = "block";
        public int amount = 100;
        public int opacity = 92;
        public int lifetimeSeconds = 60;
        public int maxTiles = 2048;
        public int renderDistance = 48;
        public boolean rainWashes = true;
        public boolean wallsAndCeilings = true;
        public boolean detailedShapes = true;
        public boolean footprints = true;
        public int footprintSteps = 6;
    }

    public static class HitBurstSettings {
        public int burstIntensity = 100;
        public int burstDuration = 100;
        public int burstSpread = 100;
        public int damageCooldown = 100;

        public float getBurstIntensityMultiplier() {
            return burstIntensity / 100.0f;
        }

        public float getBurstDurationMultiplier() {
            return burstDuration / 100.0f;
        }

        public float getBurstSpreadMultiplier() {
            return burstSpread / 100.0f;
        }

        public long getDamageCooldownMs() {
            return damageCooldown;
        }
    }

    public static class DeathBurstSettings {
        public int deathIntensity = 100;
        public int deathSpread = 100;

        public float getDeathIntensityMultiplier() {
            return deathIntensity / 100.0f;
        }

        public float getDeathSpreadMultiplier() {
            return deathSpread / 100.0f;
        }
    }

    public static class LowHealthSettings {
        public int threshold = 50;
        public int dripFrequency = 100;
        public int dripIntensity = 100;
        public int dripDurationSeconds = 15;

        public long getDripWindowMs() {
            return dripDurationSeconds * 1000L;
        }

        public float getThresholdAsFloat() {
            return threshold / 100.0f;
        }

        public float getDripFrequencyMultiplier() {
            return dripFrequency / 100.0f;
        }

        public float getDripIntensityMultiplier() {
            return dripIntensity / 100.0f;
        }
    }

    public static class AudioSettings {
        public boolean soundEnabled = true;
        public int soundVolume = 100;
        public int soundPitch = 100;
        public boolean landingSounds = true;
        public boolean footstepSounds = true;

        public float getSoundVolumeMultiplier() {
            return soundVolume / 100.0f;
        }

        public float getSoundPitchMultiplier() {
            return soundPitch / 100.0f;
        }
    }

    public static class DirectionalSettings {
        public boolean enabled = true;
        public int share = 65;
        public boolean weaponFlavour = true;
        public boolean entrySpatter = true;
    }

    public static class UnderwaterSettings {
        public boolean transformToFog = true;
        public int fogLifetime = 100;
        public int fogOpacity = 100;
        public int fogSize = 100;

        public float getFogLifetimeMultiplier() {
            return fogLifetime / 100.0f;
        }

        public float getFogOpacityMultiplier() {
            return fogOpacity / 100.0f;
        }

        public float getFogSizeMultiplier() {
            return fogSize / 100.0f;
        }
    }

    public static class VanillaEntities {
        public Map<String, EntitySettings> entities = new HashMap<>();

        public static final int CURRENT_VERSION = 2;
        public int version = 0;

        public void initializeDefaults() {
            version = CURRENT_VERSION;
            BloodKind L = BloodKind.LIQUID, D = BloodKind.DEBRIS, E = BloodKind.EMBER, P = BloodKind.POWDER;

            int RED         = 0x7A0F0F;
            int DARK_RED    = 0x4A1414;
            int PIG_RED     = 0x8A1A1A;
            int BONE        = 0xC9C2A8;
            int SOOT_BONE   = 0x3D3A3A;
            int DRIED_DUST  = 0xA08C6E;
            int NIGHT_BLUE  = 0x2A3660;
            int SCULK       = 0x0F4B58;
            int DRAGON      = 0x7A22A6;
            int END_PURPLE  = 0x5E1C82;
            int SHULKER     = 0x8A3AA0;
            int WITHER      = 0x1E1626;
            int FLAME       = 0xF5A623;
            int LAVA        = 0xD3540A;
            int SULFUR      = 0xE0D040;
            int GHAST       = 0xC9C9D9;
            int HAPPY_PINK  = 0xF2BFD9;
            int STRIDER     = 0xC93A1A;
            int IRON        = 0x8C8C8C;
            int COPPER      = 0xB87333;
            int SNOW        = 0xEDF4FA;
            int SLIME       = 0x7FCF4E;
            int CREEPER     = 0x2F6B1E;
            int HEMOLYMPH   = 0x3B7A2A;
            int BUG_GRAY    = 0x8F8F80;
            int RESIN       = 0xD98B34;
            int GUST        = 0xC8ECF2;
            int SPIRIT      = 0x9FB6DC;
            int HONEY       = 0xC9961A;
            int INK         = 0x101828;
            int GLOW_INK    = 0x2FAF9E;
            int GUARDIAN    = 0x2E7F86;
            int NAUTILUS    = 0x1E3F7A;

            entities.put("zombie", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("zombie_villager", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("husk", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("drowned", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("zombie_horse", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("zombified_piglin", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("zoglin", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("giant", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("camel_husk", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("zombie_nautilus", new EntitySettings(true, true, true, DARK_RED, L));
            entities.put("phantom", new EntitySettings(true, true, true, NIGHT_BLUE, L));
            entities.put("skeleton", new EntitySettings(true, false, false, BONE, D));
            entities.put("stray", new EntitySettings(true, false, false, BONE, D));
            entities.put("bogged", new EntitySettings(true, false, false, BONE, D));
            entities.put("skeleton_horse", new EntitySettings(true, false, false, BONE, D));
            entities.put("wither_skeleton", new EntitySettings(true, false, false, SOOT_BONE, D));
            entities.put("parched", new EntitySettings(true, false, false, DRIED_DUST, D));

            entities.put("warden", new EntitySettings(true, true, true, SCULK, L));
            entities.put("ender_dragon", new EntitySettings(true, true, true, DRAGON, L));
            entities.put("wither", new EntitySettings(true, false, false, WITHER, D));

            entities.put("blaze", new EntitySettings(true, false, false, FLAME, E));
            entities.put("magma_cube", new EntitySettings(true, false, false, LAVA, E));
            entities.put("sulfur_cube", new EntitySettings(true, false, false, SULFUR, E));
            entities.put("ghast", new EntitySettings(true, true, true, GHAST, L));
            entities.put("happy_ghast", new EntitySettings(true, true, true, HAPPY_PINK, L));
            entities.put("strider", new EntitySettings(true, true, true, STRIDER, L));
            entities.put("hoglin", new EntitySettings(true, true, true, PIG_RED, L));
            entities.put("piglin", new EntitySettings(true, true, true, PIG_RED, L));
            entities.put("piglin_brute", new EntitySettings(true, true, true, PIG_RED, L));

            entities.put("enderman", new EntitySettings(true, true, true, END_PURPLE, L));
            entities.put("endermite", new EntitySettings(true, true, true, END_PURPLE, L));
            entities.put("shulker", new EntitySettings(true, true, true, SHULKER, L));

            entities.put("iron_golem", new EntitySettings(true, false, false, IRON, D));
            entities.put("copper_golem", new EntitySettings(true, false, false, COPPER, D));
            entities.put("snow_golem", new EntitySettings(true, false, true, SNOW, P));
            entities.put("creaking", new EntitySettings(true, false, false, RESIN, D));
            entities.put("creaking_transient", new EntitySettings(true, false, false, RESIN, D));
            entities.put("breeze", new EntitySettings(true, false, false, GUST, D));
            entities.put("allay", new EntitySettings(true, false, false, SPIRIT, D));
            entities.put("vex", new EntitySettings(true, false, false, SPIRIT, D));
            entities.put("slime", new EntitySettings(true, false, true, SLIME, L));
            entities.put("creeper", new EntitySettings(true, true, true, CREEPER, L));
            entities.put("silverfish", new EntitySettings(true, true, true, BUG_GRAY, L));

            for (String s : new String[]{"cow", "mooshroom", "chicken", "pig", "sheep", "horse", "donkey", "mule",
                    "llama", "trader_llama", "camel", "rabbit", "killer_bunny", "turtle", "armadillo", "sniffer",
                    "wolf", "cat", "ocelot", "fox", "parrot", "bat", "panda", "polar_bear", "goat",
                    "dolphin", "cod", "salmon", "tropical_fish", "pufferfish", "axolotl", "tadpole", "frog"}) {
                entities.put(s, new EntitySettings(true, true, true, RED, L));
            }

            entities.put("squid", new EntitySettings(true, true, true, INK, L));
            entities.put("glow_squid", new EntitySettings(true, true, true, GLOW_INK, L));
            entities.put("guardian", new EntitySettings(true, true, true, GUARDIAN, L));
            entities.put("elder_guardian", new EntitySettings(true, true, true, GUARDIAN, L));
            entities.put("nautilus", new EntitySettings(true, true, true, NAUTILUS, L));

            entities.put("bee", new EntitySettings(true, true, true, HONEY, L));
            entities.put("spider", new EntitySettings(true, true, true, HEMOLYMPH, L));
            entities.put("cave_spider", new EntitySettings(true, true, true, HEMOLYMPH, L));

            for (String s : new String[]{"villager", "wandering_trader", "witch", "evoker", "vindicator", "pillager",
                    "ravager", "illusioner"}) {
                entities.put(s, new EntitySettings(true, true, true, RED, L));
            }

            entities.put("armor_stand", new EntitySettings(false, false, false, RED));
            entities.put("mannequin", new EntitySettings(false, false, false, RED));
        }

        public void ensureDefaults() {
            if (version < CURRENT_VERSION) {
                entities.clear();
                initializeDefaults();
                return;
            }
            VanillaEntities defaults = new VanillaEntities();
            defaults.initializeDefaults();

            for (Map.Entry<String, EntitySettings> entry : defaults.entities.entrySet()) {
                entities.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        public EntitySettings getSettings(String entityType) {
            return entities.get(entityType);
        }

        public boolean doesEntityBleed(String entityType) {
            EntitySettings settings = entities.get(entityType);
            return settings == null || settings.enabled;
        }

        public boolean shouldEntityDripAtLowHealth(String entityType) {
            EntitySettings settings = entities.get(entityType);
            if (settings == null) return true;
            return settings.enabled && settings.canDripAtLowHealth;
        }

        public static class EntitySettings {
            public boolean enabled = true;
            public boolean canDripAtLowHealth = true;
            public boolean transformToStains = true;
            public int bloodColor = 0x660303;
            public String kind = "liquid";

            public EntitySettings() {
            }

            public EntitySettings(boolean enabled, boolean canDripAtLowHealth, boolean transformToStains, int bloodColor) {
                this(enabled, canDripAtLowHealth, transformToStains, bloodColor, BloodKind.LIQUID);
            }

            public EntitySettings(boolean enabled, boolean canDripAtLowHealth, boolean transformToStains, int bloodColor, BloodKind kind) {
                this.enabled = enabled;
                this.canDripAtLowHealth = canDripAtLowHealth;
                this.transformToStains = transformToStains;
                this.bloodColor = bloodColor;
                this.kind = kind.key();
            }

            public BloodKind getKind() {
                return BloodKind.parse(kind);
            }

            public BloodColor.Color toColor() {
                float r = ((bloodColor >> 16) & 0xFF) / 255.0f;
                float g = ((bloodColor >> 8) & 0xFF) / 255.0f;
                float b = (bloodColor & 0xFF) / 255.0f;

                return new BloodColor.Color(r, g, b);
            }
        }
    }

    public static class ModdedEntities {
        public Map<String, ModdedEntitySettings> customEntities = new HashMap<>();

        public ModdedEntitySettings getSettings(String entityId) {
            return customEntities.get(entityId);
        }

        public boolean hasCustomSettings(String entityId) {
            return customEntities.containsKey(entityId);
        }

        public void putSettings(String entityId, ModdedEntitySettings settings) {
            customEntities.put(entityId, settings);
        }

        public static class ModdedEntitySettings {
            public boolean enabled = true;
            public boolean canDripAtLowHealth = true;
            public boolean transformToStains = true;
            public int bloodColor = 0x660303;
            public String kind = "liquid";

            public BloodKind getKind() {
                return BloodKind.parse(kind);
            }

            public ModdedEntitySettings() {
            }

            public ModdedEntitySettings(boolean enabled, boolean canDripAtLowHealth, boolean transformToStains, int bloodColor) {
                this.enabled = enabled;
                this.canDripAtLowHealth = canDripAtLowHealth;
                this.transformToStains = transformToStains;
                this.bloodColor = bloodColor;
            }

            public BloodColor.Color toColor() {
                float r = ((bloodColor >> 16) & 0xFF) / 255.0f;
                float g = ((bloodColor >> 8) & 0xFF) / 255.0f;
                float b = (bloodColor & 0xFF) / 255.0f;

                return new BloodColor.Color(r, g, b);
            }
        }
    }
}
