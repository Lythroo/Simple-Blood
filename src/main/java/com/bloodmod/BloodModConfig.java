package com.bloodmod;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Config(name = BloodMod.MOD_ID)
public class BloodModConfig implements ConfigData {

    @ConfigEntry.Gui.CollapsibleObject
    public GeneralSettings general = new GeneralSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public ParticleSettings particles = new ParticleSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public BloodStainSettings bloodStains = new BloodStainSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public HitBurstSettings hitBurst = new HitBurstSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public DeathBurstSettings deathBurst = new DeathBurstSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public LowHealthSettings lowHealth = new LowHealthSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public AudioSettings audio = new AudioSettings();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Category("particles")
    public UnderwaterSettings underwater = new UnderwaterSettings();

    @ConfigEntry.Gui.TransitiveObject
    @ConfigEntry.Category("entities")
    public EntityOverrides entities = new EntityOverrides();

    // ══════════════════════════════════════════════════════════════════════
    // GENERAL SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class GeneralSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean modEnabled = true;

        @ConfigEntry.Gui.Tooltip
        public boolean playerBleed = true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // PARTICLE SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class ParticleSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean hitBurst = true;

        @ConfigEntry.Gui.Tooltip
        public boolean deathBurst = true;

        @ConfigEntry.Gui.Tooltip
        public boolean lowHealthDrip = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 300)
        public int particleSize = 210;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
        public int particleLifetime = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
        public int particleGravity = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 150)
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

    // ══════════════════════════════════════════════════════════════════════
    // BLOOD STAIN SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class BloodStainSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean enabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 300)
        public int stainSize = 80;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 30)
        public int stainDurationSeconds = 5;

        public float getStainSizeMultiplier() {
            return stainSize / 100.0f;
        }

        public int getStainDurationSeconds() {
            return stainDurationSeconds;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HIT BURST SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class HitBurstSettings {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
        public int burstIntensity = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
        public int burstDuration = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
        public int burstSpread = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 500)
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

    // ══════════════════════════════════════════════════════════════════════
    // DEATH BURST SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class DeathBurstSettings {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 300)
        public int deathIntensity = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
        public int deathSpread = 100;

        public float getDeathIntensityMultiplier() {
            return deathIntensity / 100.0f;
        }

        public float getDeathSpreadMultiplier() {
            return deathSpread / 100.0f;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOW HEALTH SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class LowHealthSettings {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 5, max = 100)
        public int lowHealthThreshold = 50;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
        public int dripFrequency = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 200)
        public int dripIntensity = 100;

        public float getThresholdAsFloat() {
            return lowHealthThreshold / 100.0f;
        }

        public float getDripFrequencyMultiplier() {
            return dripFrequency / 100.0f;
        }

        public float getDripIntensityMultiplier() {
            return dripIntensity / 100.0f;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUDIO SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class AudioSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean soundEnabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 200)
        public int soundVolume = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 150)
        public int soundPitch = 100;

        public float getSoundVolumeMultiplier() {
            return soundVolume / 100.0f;
        }

        public float getSoundPitchMultiplier() {
            return soundPitch / 100.0f;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UNDERWATER SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class UnderwaterSettings {
        @ConfigEntry.Gui.Tooltip
        public boolean enableFogTransformation = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 300)
        public int fogSize = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
        public int fogLifetime = 100;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 20, max = 100)
        public int fogOpacity = 60;

        public float getFogSizeMultiplier() {
            return fogSize / 100.0f;
        }

        public float getFogLifetimeMultiplier() {
            return fogLifetime / 100.0f;
        }

        public float getFogOpacityMultiplier() {
            return fogOpacity / 100.0f;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BACKWARDS COMPATIBILITY ACCESSORS
    // ══════════════════════════════════════════════════════════════════════

    public boolean globalEnabled() { return general.modEnabled; }
    public boolean playerBleed() { return general.playerBleed; }
    public boolean hitBurstEnabled() { return particles.hitBurst; }
    public boolean deathBurstEnabled() { return particles.deathBurst; }
    public boolean lowHealthEnabled() { return particles.lowHealthDrip; }
    public float lowHealthThreshold() { return lowHealth.getThresholdAsFloat(); }
    public boolean soundEnabled() { return audio.soundEnabled; }
    public float particleSizeMultiplier() { return particles.getParticleSizeMultiplier(); }

    // New accessors
    public boolean bloodStainsEnabled() { return bloodStains.enabled; }
    public float particleLifetimeMultiplier() { return particles.getParticleLifetimeMultiplier(); }
    public float particleGravityMultiplier() { return particles.getParticleGravityMultiplier(); }
    public float particleDragMultiplier() { return particles.getParticleDragMultiplier(); }
    public float stainSizeMultiplier() { return bloodStains.getStainSizeMultiplier(); }
    public int stainDurationSeconds() { return bloodStains.getStainDurationSeconds(); }
    public float burstIntensityMultiplier() { return hitBurst.getBurstIntensityMultiplier(); }
    public float burstDurationMultiplier() { return hitBurst.getBurstDurationMultiplier(); }
    public float burstSpreadMultiplier() { return hitBurst.getBurstSpreadMultiplier(); }
    public long damageCooldownMs() { return hitBurst.getDamageCooldownMs(); }
    public float deathIntensityMultiplier() { return deathBurst.getDeathIntensityMultiplier(); }
    public float deathSpreadMultiplier() { return deathBurst.getDeathSpreadMultiplier(); }
    public float dripFrequencyMultiplier() { return lowHealth.getDripFrequencyMultiplier(); }
    public float dripIntensityMultiplier() { return lowHealth.getDripIntensityMultiplier(); }
    public float soundVolumeMultiplier() { return audio.getSoundVolumeMultiplier(); }
    public float soundPitchMultiplier() { return audio.getSoundPitchMultiplier(); }
    public boolean underwaterFogEnabled() { return underwater.enableFogTransformation; }
    public float fogSizeMultiplier() { return underwater.getFogSizeMultiplier(); }
    public float fogLifetimeMultiplier() { return underwater.getFogLifetimeMultiplier(); }
    public float fogOpacityMultiplier() { return underwater.getFogOpacityMultiplier(); }

    // ══════════════════════════════════════════════════════════════════════
    // ENTITY REGISTRY MAPPING (EXCLUDED FROM GUI)
    // ══════════════════════════════════════════════════════════════════════

    @ConfigEntry.Gui.Excluded
    private static final Map<String, String> REGISTRY_TO_FIELD = Map.ofEntries(
            // ── Undead ────────────────────────────────────────────────────
            Map.entry("skeleton",            "skeleton"),
            Map.entry("wither_skeleton",     "witherSkeleton"),
            Map.entry("stray",               "stray"),
            Map.entry("bogged",              "bogged"),
            Map.entry("parched",             "parched"),
            Map.entry("zombie",              "zombie"),
            Map.entry("zombie_villager",     "zombieVillager"),
            Map.entry("husk",                "husk"),
            Map.entry("drowned",             "drowned"),
            Map.entry("phantom",             "phantom"),
            Map.entry("skeleton_horse",      "skeletonHorse"),
            Map.entry("zombie_horse",        "zombieHorse"),
            Map.entry("zombified_piglin",    "zombifiedPiglin"),

            // ── Bosses & Special ──────────────────────────────────────────
            Map.entry("wither",              "wither"),
            Map.entry("ender_dragon",        "enderDragon"),
            Map.entry("enderman",            "enderman"),
            Map.entry("endermite",           "endermite"),
            Map.entry("shulker",             "shulker"),
            Map.entry("warden",              "warden"),

            // ── Non-Organic ───────────────────────────────────────────────
            Map.entry("slime",               "slime"),
            Map.entry("magma_cube",          "magmaCube"),
            Map.entry("blaze",               "blaze"),
            Map.entry("breeze",              "breeze"),
            Map.entry("ghast",               "ghast"),
            Map.entry("happy_ghast",         "happyGhast"),
            Map.entry("vex",                 "vex"),
            Map.entry("allay",               "allay"),
            Map.entry("iron_golem",          "ironGolem"),
            Map.entry("snow_golem",          "snowGolem"),
            Map.entry("copper_golem",        "copperGolem"),
            Map.entry("creaking",            "creaking"),
            Map.entry("creeper",             "creeper"),
            Map.entry("silverfish",          "silverfish"),

            // ── Passive Animals ───────────────────────────────────────────
            Map.entry("cow",                 "cow"),
            Map.entry("mooshroom",           "mooshroom"),
            Map.entry("chicken",             "chicken"),
            Map.entry("pig",                 "pig"),
            Map.entry("sheep",               "sheep"),
            Map.entry("horse",               "horse"),
            Map.entry("donkey",              "donkey"),
            Map.entry("mule",                "mule"),
            Map.entry("llama",               "llama"),
            Map.entry("trader_llama",        "traderLlama"),
            Map.entry("camel",               "camel"),
            Map.entry("rabbit",              "rabbit"),
            Map.entry("turtle",              "turtle"),
            Map.entry("armadillo",           "armadillo"),
            Map.entry("sniffer",             "sniffer"),

            // ── Tameable & Friendly ───────────────────────────────────────
            Map.entry("wolf",                "wolf"),
            Map.entry("cat",                 "cat"),
            Map.entry("ocelot",              "ocelot"),
            Map.entry("fox",                 "fox"),
            Map.entry("parrot",              "parrot"),

            // ── Aquatic ───────────────────────────────────────────────────
            Map.entry("dolphin",             "dolphin"),
            Map.entry("squid",               "squid"),
            Map.entry("glow_squid",          "glowSquid"),
            Map.entry("cod",                 "cod"),
            Map.entry("salmon",              "salmon"),
            Map.entry("tropical_fish",       "tropicalFish"),
            Map.entry("pufferfish",          "pufferfish"),
            Map.entry("axolotl",             "axolotl"),
            Map.entry("tadpole",             "tadpole"),
            Map.entry("frog",                "frog"),
            Map.entry("guardian",            "guardian"),
            Map.entry("elder_guardian",      "elderGuardian"),

            // ── Neutral & Hostile ─────────────────────────────────────────
            Map.entry("bee",                 "bee"),
            Map.entry("panda",               "panda"),
            Map.entry("polar_bear",          "polarBear"),
            Map.entry("goat",                "goat"),
            Map.entry("spider",              "spider"),
            Map.entry("cave_spider",         "caveSpider"),

            // ── Villagers & Illagers ──────────────────────────────────────
            Map.entry("villager",            "villager"),
            Map.entry("wandering_trader",    "wanderingTrader"),
            Map.entry("witch",               "witch"),
            Map.entry("evoker",              "evoker"),
            Map.entry("vindicator",          "vindicator"),
            Map.entry("pillager",            "pillager"),
            Map.entry("ravager",             "ravager"),
            Map.entry("illusioner",          "illusioner"),

            // ── Nether ────────────────────────────────────────────────────
            Map.entry("strider",             "strider"),
            Map.entry("hoglin",              "hoglin"),
            Map.entry("zoglin",              "zoglin"),
            Map.entry("piglin",              "piglin"),
            Map.entry("piglin_brute",        "piglinBrute")
    );

    /**
     * Entities that should NOT drip blood when at low health.
     * These are entities that don't have traditional circulatory systems:
     * - Undead (skeletons, zombies) - bones/rot don't drip
     * - Golems (iron, snow, copper) - constructs don't bleed
     * - Slimes/magma cubes - gelatinous, not bleeding
     * - Blazes, vexes, allays - magical/fire entities
     * - Endermen, shulkers - dimensional beings
     */
    @ConfigEntry.Gui.Excluded
    private static final Set<String> NO_LOW_HEALTH_DRIP = new HashSet<>(Arrays.asList(
            // All undead
            "skeleton", "wither_skeleton", "stray", "bogged", "parched",
            "zombie", "zombie_villager", "husk", "drowned",
            "phantom", "skeleton_horse", "zombie_horse", "zombified_piglin",
            "wither",

            // Golems and constructs
            "iron_golem", "snow_golem", "copper_golem", "creaking",

            // Non-organic
            "slime", "magma_cube", "blaze", "breeze",
            "vex", "allay",
            "endermite", "shulker",
            "silverfish", "creeper"
    ));

    // ══════════════════════════════════════════════════════════════════════
    // ENTITY OVERRIDE METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Check if the entity with the given registry path should bleed.
     * Returns false if the entity is disabled in config.
     *
     * @param entityRegistryPath the entity type's registry path (e.g., "zombie", "cow")
     * @return whether this entity should produce blood particles
     */
    public boolean doesEntityBleed(String entityRegistryPath) {
        String fieldName = REGISTRY_TO_FIELD.get(entityRegistryPath);
        if (fieldName == null) {
            // Unknown entity - default to true (bleed)
            return true;
        }

        try {
            java.lang.reflect.Field field = EntityOverrides.class.getField(fieldName);
            return field.getBoolean(entities);
        } catch (Exception e) {
            BloodMod.LOGGER.error("Failed to check bleed status for entity: " + entityRegistryPath, e);
            return true;
        }
    }

    /**
     * Check if the entity should drip blood at low health.
     * Some entities (skeletons, golems, etc.) don't have circulatory systems
     * and shouldn't continuously drip.
     *
     * @param entityRegistryPath the entity type's registry path
     * @return whether this entity should drip at low health
     */
    public boolean shouldEntityDripAtLowHealth(String entityRegistryPath) {
        // First check if entity bleeds at all
        if (!doesEntityBleed(entityRegistryPath)) {
            return false;
        }

        // Then check if it's in the no-drip list
        return !NO_LOW_HEALTH_DRIP.contains(entityRegistryPath);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ENTITY OVERRIDES
    // ══════════════════════════════════════════════════════════════════════

    public static class EntityOverrides {
        // ── Undead ────────────────────────────────────────────────────
        @ConfigEntry.Category("undead")
        public boolean skeleton           = true;

        @ConfigEntry.Category("undead")
        public boolean witherSkeleton     = true;

        @ConfigEntry.Category("undead")
        public boolean stray              = true;

        @ConfigEntry.Category("undead")
        public boolean bogged             = true;

        @ConfigEntry.Category("undead")
        public boolean parched            = true;

        @ConfigEntry.Category("undead")
        public boolean zombie             = true;

        @ConfigEntry.Category("undead")
        public boolean zombieVillager     = true;

        @ConfigEntry.Category("undead")
        public boolean husk               = true;

        @ConfigEntry.Category("undead")
        public boolean drowned            = true;

        @ConfigEntry.Category("undead")
        public boolean phantom            = true;

        @ConfigEntry.Category("undead")
        public boolean skeletonHorse      = true;

        @ConfigEntry.Category("undead")
        public boolean zombieHorse        = true;

        @ConfigEntry.Category("undead")
        public boolean zombifiedPiglin    = true;

        // ── Bosses & Special ──────────────────────────────────────────
        @ConfigEntry.Category("bosses")
        public boolean wither             = true;

        @ConfigEntry.Category("bosses")
        public boolean enderDragon        = true;

        @ConfigEntry.Category("bosses")
        public boolean enderman           = true;

        @ConfigEntry.Category("bosses")
        public boolean endermite          = true;

        @ConfigEntry.Category("bosses")
        public boolean warden             = true;

        @ConfigEntry.Category("bosses")
        public boolean shulker            = true;

        // ── Non-Organic ───────────────────────────────────────────────────
        @ConfigEntry.Category("nonorganic")
        public boolean slime              = true;

        @ConfigEntry.Category("nonorganic")
        public boolean magmaCube          = true;

        @ConfigEntry.Category("nonorganic")
        public boolean blaze              = true;

        @ConfigEntry.Category("nonorganic")
        public boolean breeze             = true;

        @ConfigEntry.Category("nonorganic")
        public boolean ghast              = true;

        @ConfigEntry.Category("nonorganic")
        public boolean happyGhast         = true;

        @ConfigEntry.Category("nonorganic")
        public boolean vex                = true;

        @ConfigEntry.Category("nonorganic")
        public boolean allay              = true;

        @ConfigEntry.Category("nonorganic")
        public boolean ironGolem          = true;

        @ConfigEntry.Category("nonorganic")
        public boolean snowGolem          = true;

        @ConfigEntry.Category("nonorganic")
        public boolean copperGolem        = true;

        @ConfigEntry.Category("nonorganic")
        public boolean creaking           = true;

        @ConfigEntry.Category("nonorganic")
        public boolean creeper            = true;

        @ConfigEntry.Category("nonorganic")
        public boolean silverfish         = true;

        // ── Passive Animals ───────────────────────────────────────────────
        @ConfigEntry.Category("passive")
        public boolean cow                = true;

        @ConfigEntry.Category("passive")
        public boolean mooshroom          = true;

        @ConfigEntry.Category("passive")
        public boolean chicken            = true;

        @ConfigEntry.Category("passive")
        public boolean pig                = true;

        @ConfigEntry.Category("passive")
        public boolean sheep              = true;

        @ConfigEntry.Category("passive")
        public boolean horse              = true;

        @ConfigEntry.Category("passive")
        public boolean donkey             = true;

        @ConfigEntry.Category("passive")
        public boolean mule               = true;

        @ConfigEntry.Category("passive")
        public boolean llama              = true;

        @ConfigEntry.Category("passive")
        public boolean traderLlama        = true;

        @ConfigEntry.Category("passive")
        public boolean camel              = true;

        @ConfigEntry.Category("passive")
        public boolean rabbit             = true;

        @ConfigEntry.Category("passive")
        public boolean turtle             = true;

        @ConfigEntry.Category("passive")
        public boolean armadillo          = true;

        @ConfigEntry.Category("passive")
        public boolean sniffer            = true;

        // ── Tameable & Friendly ───────────────────────────────────────────
        @ConfigEntry.Category("tameable")
        public boolean wolf               = true;

        @ConfigEntry.Category("tameable")
        public boolean cat                = true;

        @ConfigEntry.Category("tameable")
        public boolean ocelot             = true;

        @ConfigEntry.Category("tameable")
        public boolean fox                = true;

        @ConfigEntry.Category("tameable")
        public boolean parrot             = true;

        // ── Aquatic ───────────────────────────────────────────────────────
        @ConfigEntry.Category("aquatic")
        public boolean dolphin            = true;

        @ConfigEntry.Category("aquatic")
        public boolean squid              = true;

        @ConfigEntry.Category("aquatic")
        public boolean glowSquid          = true;

        @ConfigEntry.Category("aquatic")
        public boolean cod                = true;

        @ConfigEntry.Category("aquatic")
        public boolean salmon             = true;

        @ConfigEntry.Category("aquatic")
        public boolean tropicalFish       = true;

        @ConfigEntry.Category("aquatic")
        public boolean pufferfish         = true;

        @ConfigEntry.Category("aquatic")
        public boolean axolotl            = true;

        @ConfigEntry.Category("aquatic")
        public boolean tadpole            = true;

        @ConfigEntry.Category("aquatic")
        public boolean frog               = true;

        @ConfigEntry.Category("aquatic")
        public boolean guardian           = true;

        @ConfigEntry.Category("aquatic")
        public boolean elderGuardian      = true;

        // ── Neutral & Hostile ─────────────────────────────────────────────
        @ConfigEntry.Category("neutral")
        public boolean bee                = true;

        @ConfigEntry.Category("neutral")
        public boolean panda              = true;

        @ConfigEntry.Category("neutral")
        public boolean polarBear          = true;

        @ConfigEntry.Category("neutral")
        public boolean goat               = true;

        @ConfigEntry.Category("neutral")
        public boolean spider             = true;

        @ConfigEntry.Category("neutral")
        public boolean caveSpider         = true;

        // ── Villagers & Illagers ──────────────────────────────────────────
        @ConfigEntry.Category("villagers")
        public boolean villager           = true;

        @ConfigEntry.Category("villagers")
        public boolean wanderingTrader    = true;

        @ConfigEntry.Category("villagers")
        public boolean witch              = true;

        @ConfigEntry.Category("villagers")
        public boolean evoker             = true;

        @ConfigEntry.Category("villagers")
        public boolean vindicator         = true;

        @ConfigEntry.Category("villagers")
        public boolean pillager           = true;

        @ConfigEntry.Category("villagers")
        public boolean ravager            = true;

        @ConfigEntry.Category("villagers")
        public boolean illusioner         = true;

        // ── Nether ────────────────────────────────────────────────────────
        @ConfigEntry.Category("nether")
        public boolean strider            = true;

        @ConfigEntry.Category("nether")
        public boolean hoglin             = true;

        @ConfigEntry.Category("nether")
        public boolean zoglin             = true;

        @ConfigEntry.Category("nether")
        public boolean piglin             = true;

        @ConfigEntry.Category("nether")
        public boolean piglinBrute        = true;
    }
}