package com.bloodmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Config(name = BloodMod.MOD_ID)
public class BloodModConfig implements ConfigData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = getConfigPath();

    public GeneralSettings general = new GeneralSettings();
    public ParticleSettings particles = new ParticleSettings();
    public BloodStainSettings bloodStains = new BloodStainSettings();
    public HitBurstSettings hitBurst = new HitBurstSettings();
    public DeathBurstSettings deathBurst = new DeathBurstSettings();
    public LowHealthSettings lowHealth = new LowHealthSettings();
    public AudioSettings audio = new AudioSettings();
    public UnderwaterSettings underwater = new UnderwaterSettings();
    public BloodColorSettings bloodColors = new BloodColorSettings();
    public EntityOverrides entities = new EntityOverrides();
    public ModdedEntities moddedEntities = new ModdedEntities();

    private static Path getConfigPath() {
        try {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
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
            return new BloodModConfig();
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            BloodModConfig config = GSON.fromJson(json, BloodModConfig.class);
            BloodMod.LOGGER.info("Config loaded from {}", CONFIG_PATH);
            return config;
        } catch (Exception e) {
            BloodMod.LOGGER.error("Failed to load config, using defaults", e);
            return new BloodModConfig();
        }
    }

    public boolean globalEnabled() { return general.modEnabled; }
    public boolean playerBleed() { return general.playerBleed; }

    public boolean hitBurstEnabled() { return particles.hitBurst; }
    public boolean deathBurstEnabled() { return particles.deathBurst; }
    public boolean lowHealthEnabled() { return particles.lowHealthDrip; }

    public float particleSizeMultiplier() { return particles.getParticleSizeMultiplier(); }
    public float particleLifetimeMultiplier() { return particles.getParticleLifetimeMultiplier(); }
    public float particleGravityMultiplier() { return particles.getParticleGravityMultiplier(); }
    public float particleDragMultiplier() { return particles.getParticleDragMultiplier(); }

    public int stainDurationSeconds() { return bloodStains.getStainDurationSeconds(); }
    public float stainSizeMultiplier() { return bloodStains.getStainSizeMultiplier(); }

    public float burstIntensityMultiplier() { return hitBurst.getBurstIntensityMultiplier(); }
    public float burstDurationMultiplier() { return hitBurst.getBurstDurationMultiplier(); }
    public float burstSpreadMultiplier() { return hitBurst.getBurstSpreadMultiplier(); }
    public long damageCooldownMs() { return hitBurst.getDamageCooldownMs(); }

    public float deathIntensityMultiplier() { return deathBurst.getDeathIntensityMultiplier(); }
    public float deathSpreadMultiplier() { return deathBurst.getDeathSpreadMultiplier(); }

    public float lowHealthThreshold() { return lowHealth.getThresholdAsFloat(); }
    public float dripFrequencyMultiplier() { return lowHealth.getDripFrequencyMultiplier(); }
    public float dripIntensityMultiplier() { return lowHealth.getDripIntensityMultiplier(); }

    public boolean soundEnabled() { return audio.soundEnabled; }
    public float soundVolumeMultiplier() { return audio.getSoundVolumeMultiplier(); }
    public float soundPitchMultiplier() { return audio.getSoundPitchMultiplier(); }

    public boolean underwaterFogEnabled() { return underwater.transformToFog; }
    public float fogLifetimeMultiplier() { return underwater.getFogLifetimeMultiplier(); }
    public float fogOpacityMultiplier() { return underwater.getFogOpacityMultiplier(); }
    public float fogSizeMultiplier() { return underwater.getFogSizeMultiplier(); }

    public boolean bloodStainsEnabled() { return bloodStains.enabled; }

    public boolean doesEntityBleed(String entityType) {
        return entities.doesEntityBleed(entityType);
    }

    public boolean shouldEntityDripAtLowHealth(String entityType) {
        return entities.shouldEntityDripAtLowHealth(entityType);
    }

    public static class GeneralSettings {
        public boolean modEnabled = true;
        public boolean playerBleed = true;
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

    public static class BloodStainSettings {
        public boolean enabled = true;
        public int stainSize = 80;
        public int stainDurationSeconds = 5;

        public float getStainSizeMultiplier() {
            return stainSize / 100.0f;
        }

        public int getStainDurationSeconds() {
            return stainDurationSeconds;
        }
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

        public float getSoundVolumeMultiplier() {
            return soundVolume / 100.0f;
        }

        public float getSoundPitchMultiplier() {
            return soundPitch / 100.0f;
        }
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

    public static class EntityOverrides {

        private static final Set<String> NO_BLOOD = new HashSet<>(Arrays.asList(

        ));

        private static final Set<String> NO_LOW_HEALTH_DRIP = new HashSet<>(Arrays.asList(
                "skeleton", "stray", "wither_skeleton", "bogged", "skeleton_horse",
                "iron_golem", "snow_golem", "copper_golem",
                "slime", "magma_cube",
                "creaking", "parched"
        ));

        public boolean doesEntityBleed(String entityType) {
            if (NO_BLOOD.contains(entityType)) {
                return false;
            }

            try {
                java.lang.reflect.Field field = getClass().getDeclaredField(
                        convertEntityTypeToFieldName(entityType));
                return field.getBoolean(this);
            } catch (Exception e) {

                return true;
            }
        }

        public boolean shouldEntityDripAtLowHealth(String entityType) {
            return !NO_LOW_HEALTH_DRIP.contains(entityType) && doesEntityBleed(entityType);
        }

        private String convertEntityTypeToFieldName(String entityType) {
            String[] parts = entityType.split("_");
            StringBuilder fieldName = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                fieldName.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    fieldName.append(parts[i].substring(1));
                }
            }
            return fieldName.toString();
        }

        public boolean zombie              = true;
        public boolean zombieVillager      = true;
        public boolean husk                = true;
        public boolean drowned             = true;
        public boolean zombieHorse         = true;
        public boolean skeleton            = true;
        public boolean stray               = true;
        public boolean witherSkeleton      = true;
        public boolean bogged              = true;
        public boolean zombifiedPiglin     = true;
        public boolean phantom             = true;

        public boolean warden              = true;
        public boolean enderDragon         = true;
        public boolean wither              = true;
        public boolean blaze               = true;
        public boolean ghast               = true;
        public boolean magmaCube           = true;
        public boolean enderman            = true;
        public boolean endermite           = true;
        public boolean shulker             = true;

        public boolean creeper             = true;
        public boolean silverfish          = true;
        public boolean ironGolem           = true;
        public boolean snowGolem           = true;
        public boolean slime               = true;

        public boolean cow                = true;
        public boolean mooshroom          = true;
        public boolean chicken            = true;
        public boolean pig                = true;
        public boolean sheep              = true;
        public boolean horse              = true;
        public boolean donkey             = true;
        public boolean mule               = true;
        public boolean llama              = true;
        public boolean traderLlama        = true;
        public boolean camel              = true;
        public boolean rabbit             = true;
        public boolean turtle             = true;
        public boolean armadillo          = true;
        public boolean sniffer            = true;

        public boolean wolf               = true;
        public boolean cat                = true;
        public boolean ocelot             = true;
        public boolean fox                = true;
        public boolean parrot             = true;

        public boolean dolphin            = true;
        public boolean squid              = true;
        public boolean glowSquid          = true;
        public boolean cod                = true;
        public boolean salmon             = true;
        public boolean tropicalFish       = true;
        public boolean pufferfish         = true;
        public boolean axolotl            = true;
        public boolean tadpole            = true;
        public boolean frog               = true;
        public boolean guardian           = true;
        public boolean elderGuardian      = true;

        public boolean bee                = true;
        public boolean panda              = true;
        public boolean polarBear          = true;
        public boolean goat               = true;
        public boolean spider             = true;
        public boolean caveSpider         = true;

        public boolean villager           = true;
        public boolean wanderingTrader    = true;
        public boolean witch              = true;
        public boolean evoker             = true;
        public boolean vindicator         = true;
        public boolean pillager           = true;
        public boolean ravager            = true;
        public boolean illusioner         = true;

        public boolean strider            = true;
        public boolean hoglin             = true;
        public boolean zoglin             = true;
        public boolean piglin             = true;
        public boolean piglinBrute        = true;
    }

    public static class BloodColorSettings {
        public boolean enableCustomColors = false;

        public int defaultBlood = 0x660303;      

        public int playerBlood = 0x660303;       

        public int otherPlayersBlood = 0x660303; 

        public int zombieBlood = 0x400202;       

        public int skeletonBlood = 0xBFB89D;     

        public int endBlood = 0x73197A;          

        public int spiderBlood = 0x267319;       

        public int slimeBlood = 0x4CB833;        

        public int aquaticBlood = 0x19337F;      

        public int netherBlood = 0xD94D0D;       

        public int blazeBlood = 0xE67F19;        

        public int beeBlood = 0xB39919;          

        public int creeperBlood = 0x1E5C14;      

        public int golemBlood = 0x666666;        

        public int defaultBloodBrightness = 100;
        public int playerBloodBrightness = 100;
        public int otherPlayersBloodBrightness = 100;
        public int zombieBloodBrightness = 100;
        public int skeletonBloodBrightness = 100;
        public int endBloodBrightness = 100;
        public int spiderBloodBrightness = 100;
        public int slimeBloodBrightness = 100;
        public int aquaticBloodBrightness = 100;
        public int netherBloodBrightness = 100;
        public int blazeBloodBrightness = 100;
        public int beeBloodBrightness = 100;
        public int creeperBloodBrightness = 100;
        public int golemBloodBrightness = 100;

        public BloodColor.Color intToColor(int rgb, int brightness) {
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;

            float brightnessMult = brightness / 100.0f;
            r = Math.min(1.0f, r * brightnessMult);
            g = Math.min(1.0f, g * brightnessMult);
            b = Math.min(1.0f, b * brightnessMult);

            return new BloodColor.Color(r, g, b);
        }

        public BloodColor.Color intToColor(int rgb) {
            return intToColor(rgb, 100);
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