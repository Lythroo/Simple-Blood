package com.simpleblood;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SimpleBloodConfig {

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
    public KindSettings kinds = new KindSettings();
    public PhysicsModSettings physicsMod = new PhysicsModSettings();
    public GoreSettings gore = new GoreSettings();
    public MobCoatSettings mobCoat = new MobCoatSettings();
    public DirectionalSettings directional = new DirectionalSettings();
    public VanillaEntities vanillaEntities = new VanillaEntities();
    public ModdedEntities moddedEntities = new ModdedEntities();

    /**
     * Get the config file path
     */
    private static Path getConfigPath() {
        try {
            //? if fabric {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            //?} elif 1.20.1 {
            /*Path configDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
            *///?} else {
            /*Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            *///?}
            Path path = configDir.resolve("simpleblood.json");
            // Until 1.0.2 the mod was called "bloodmod" inside, and so was its config file.
            Path legacy = configDir.resolve("bloodmod.json");
            if (!Files.exists(path) && Files.exists(legacy)) {
                try {
                    Files.move(legacy, path);
                    SimpleBlood.LOGGER.info("Moved config {} to {}", legacy.getFileName(), path.getFileName());
                } catch (Exception e) {
                    SimpleBlood.LOGGER.warn("Could not move the old config {}; starting from defaults", legacy, e);
                }
            }
            return path;
        } catch (Exception e) {
            SimpleBlood.LOGGER.error("Failed to get config path", e);
            return null;
        }
    }

    /**
     * Save config to JSON file
     */
    public static void save(SimpleBloodConfig config) {
        if (CONFIG_PATH == null) {
            SimpleBlood.LOGGER.error("Config path is null, cannot save");
            return;
        }

        try {
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json);
            SimpleBlood.LOGGER.info("Config saved to {}", CONFIG_PATH);
        } catch (IOException e) {
            SimpleBlood.LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * Load config from JSON file
     */
    public static SimpleBloodConfig load() {
        if (CONFIG_PATH == null || !Files.exists(CONFIG_PATH)) {
            SimpleBlood.LOGGER.info("Config file not found, using defaults");
            return defaults();
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            SimpleBloodConfig config = GSON.fromJson(json, SimpleBloodConfig.class);
            if (config == null) throw new IllegalStateException("empty config file");
            config.fillGaps();
            config.vanillaEntities.ensureDefaults(); // Ensure any missing entities get defaults
            SimpleBlood.LOGGER.info("Config loaded from {}", CONFIG_PATH);
            return config;
        } catch (Exception e) {
            SimpleBlood.LOGGER.error("Failed to load config, using defaults", e);
            return defaults();
        }
    }

    /** A complete, default config. */
    public static SimpleBloodConfig defaults() {
        SimpleBloodConfig config = new SimpleBloodConfig();
        config.vanillaEntities.initializeDefaults(); // Initialize all default entity settings
        return config;
    }

    /**
     * A hand-edited file can leave whole sections as {@code null}; every section gets its
     * defaults back so nothing downstream has to check.
     */
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
        if (kinds == null) kinds = new KindSettings();
        if (physicsMod == null) physicsMod = new PhysicsModSettings();
        if (gore == null) gore = new GoreSettings();
        if (mobCoat == null) mobCoat = new MobCoatSettings();
        if (directional == null) directional = new DirectionalSettings();
        if (vanillaEntities == null) vanillaEntities = new VanillaEntities();
        if (moddedEntities == null) moddedEntities = new ModdedEntities();
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS (for backwards compatibility)
    // ══════════════════════════════════════════════════════════════════════

    public boolean globalEnabled() { return general.modEnabled; }
    /** The master amount as a multiplier (1.0 = default). */
    public float masterAmount() { return Math.max(0f, general.bloodAmount / 100.0f); }
    public int particleBudget() { return Math.max(50, general.particleBudget); }
    /** Surface pixel amount multiplier including the master amount. */
    public float surfaceAmount() { return (surfaces.amount / 100.0f) * masterAmount(); }
    public boolean playerBleed() { return player.playerBleed; }
    public int clientPlayerBloodColor() { return player.clientPlayerBloodColor; }
    public int otherPlayersBloodColor() { return player.otherPlayersBloodColor; }

    public boolean hitBurstEnabled() { return particles.hitBurst; }
    public boolean deathBurstEnabled() { return particles.deathBurst; }
    public boolean lowHealthEnabled() { return particles.lowHealthDrip; }

    public float particleSizeMultiplier() { return particles.getParticleSizeMultiplier() * chunkyDropScale(); }
    /**
     * Size for bone and metal pieces: the size slider relative to its default. They paint
     * nothing, so the chunky-canvas boost does not apply, and their sprites are sized for 1.0.
     */
    public float pieceSizeMultiplier() { return particles.particleSize / (float) ParticleSettings.DEFAULT_SIZE; }
    /** Drops are drawn bigger on the chunky (8 px) canvas so they match the puddles they leave. */
    public float chunkyDropScale() { return surfaces.enabled && surfaces.resolution == 8 ? 1.75f : 1f; }
    /** Sweep streaks grow a little less: they are big already. */
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

    // Delegate to VanillaEntities for entity-specific settings
    public boolean doesEntityBleed(String entityType) {
        return vanillaEntities.doesEntityBleed(entityType);
    }

    public boolean shouldEntityDripAtLowHealth(String entityType) {
        return vanillaEntities.shouldEntityDripAtLowHealth(entityType);
    }

    // ══════════════════════════════════════════════════════════════════════
    // GENERAL SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class GeneralSettings {
        public boolean modEnabled = true;
        /** Master amount in percent: scales every burst, drip, spray and stain at once. */
        public int bloodAmount = 100;
        /** Blood particles (drips, splashes, drops, streaks, fog) alive at once; more are dropped. */
        public int particleBudget = 1500;
        /** The "Blood" button on the title and pause screens. {@code /simpleblood} works either way. */
        public boolean menuButton = true;
        /** Physics Mod ragdolls keep bleeding, splash where they land and smear the ground. */
        public boolean physicsModRagdolls = true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // PLAYER SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class PlayerSettings {
        public boolean playerBleed = true;
        public int clientPlayerBloodColor = 0x660303;  // RGB color for client player (dark red)
        public int otherPlayersBloodColor = 0x660303;  // RGB color for other players (dark red)
        /** Other players bleed the colour their skin carries, when it carries one ({@link SkinColours}). */
        public boolean colourFromSkins = true;
        /** The colour of your own skin last taken over as your blood colour (-1: none yet). */
        public int skinColourAdopted = -1;

        /**
         * Get client player blood color as BloodColor.Color object
         */
        public BloodColor.Color getClientPlayerColor() {
            float r = ((clientPlayerBloodColor >> 16) & 0xFF) / 255.0f;
            float g = ((clientPlayerBloodColor >> 8) & 0xFF) / 255.0f;
            float b = (clientPlayerBloodColor & 0xFF) / 255.0f;
            return new BloodColor.Color(r, g, b);
        }

        /**
         * Get other players blood color as BloodColor.Color object
         */
        public BloodColor.Color getOtherPlayersColor() {
            float r = ((otherPlayersBloodColor >> 16) & 0xFF) / 255.0f;
            float g = ((otherPlayersBloodColor >> 8) & 0xFF) / 255.0f;
            float b = (otherPlayersBloodColor & 0xFF) / 255.0f;
            return new BloodColor.Color(r, g, b);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PARTICLE SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class ParticleSettings {
        public boolean hitBurst = true;
        public boolean deathBurst = true;
        public boolean lowHealthDrip = true;
        public static final int DEFAULT_SIZE = 210;
        public int particleSize = DEFAULT_SIZE;
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

    // ══════════════════════════════════════════════════════════════════════
    // SURFACE BLOOD SETTINGS (blood painted onto block faces by the canvas engine)
    // ══════════════════════════════════════════════════════════════════════

    public static class SurfaceSettings {
        public boolean enabled = true;
        /** Canvas pixels per block edge: 16 (vanilla texel size, default) or 8 (chunky). */
        public int resolution = 16;
        /** "block" = tones follow the block texture underneath, "pattern" = flat hashed tones. */
        public String highlightMode = "block";
        /** Percentage scale of how many pixels each landing droplet paints. */
        public int amount = 100;
        /** Opacity of painted pixels in percent. */
        public int opacity = 92;
        /** How long a painted pixel lives before it has dried away completely. */
        public int lifetimeSeconds = 60;
        /** Upper bound on live canvas tiles (one tile per stained block face). */
        public int maxTiles = 2048;
        /** Tiles further than this from the camera are neither painted nor drawn. */
        public int renderDistance = 48;
        /** Rain speeds up drying/washing of exposed blood. */
        public boolean rainWashes = true;
        /** Paint walls and ceilings, not only the ground. */
        public boolean wallsAndCeilings = true;
        /**
         * Fences, walls, stairs, signs and plants take blood on the shape you see (their model)
         * instead of their hitbox. Costs a little more per drop and per painted block.
         */
        public boolean detailedShapes = true;
        /**
         * With a shader pack on, leaves, grass and other plants, vines and lanterns take no blood:
         * the pack sways them, and blood painted on them would stay put. Drops pass them and land behind.
         */
        public boolean skipSwayingBlocks = true;
        /** Feet that walk through fresh blood leave a fading trail of prints. */
        public boolean footprints = true;
        /** How many steps a print trail lasts after leaving the puddle. */
        public int footprintSteps = 6;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HIT BURST SETTINGS
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // DEATH BURST SETTINGS
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // LOW HEALTH SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class LowHealthSettings {
        public int threshold = 50; // Percentage (50 = 50% = 10 hearts)
        public int dripFrequency = 100;
        public int dripIntensity = 100;
        /** Wounded entities only drip for this long after they last took damage. */
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

    // ══════════════════════════════════════════════════════════════════════
    // AUDIO SETTINGS
    // ══════════════════════════════════════════════════════════════════════

    public static class AudioSettings {
        public boolean soundEnabled = true;
        public int soundVolume = 100;
        public int soundPitch = 100;
        /** Drops tap onto surfaces and plink into puddles. */
        public boolean landingSounds = true;
        /** Feet squelch through fresh blood. */
        public boolean footstepSounds = true;

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

    // ══════════════════════════════════════════════════════════════════════
    // DIRECTIONAL SPLATTER (blood leaves the body along the blow)
    // ══════════════════════════════════════════════════════════════════════

    public static class DirectionalSettings {
        public boolean enabled = true;
        /** Percent of a burst that follows the attack direction; the rest keeps the classic random spray. */
        public int share = 65;
        /** Blades sweep, axes chop, arrows punch through, maces slam. */
        public boolean weaponFlavour = true;
        /** A little blood carries on out of the far side of the body. */
        public boolean entrySpatter = true;
    }

    public static class UnderwaterSettings {
        public boolean transformToFog = true;
        public int fogLifetime = 100;
        public int fogOpacity = 100;
        public int fogSize = 100;
        /** How thick clouds look while a shader pack is on (drawn darker and denser for it). */
        public int shaderCloudOpacity = 100;

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

    /**
     * What mobs with nothing to bleed throw instead (bone, metal, wood, embers, sparks, wind),
     * and glowing blood. Amounts are per cent of the pieces a drop would otherwise make.
     */
    public static class KindSettings {
        public int boneAmount = 100;
        /** How thick the bone dust puffs look. */
        public int boneDustOpacity = 100;
        public int metalAmount = 100;
        /** Metal flakes flash as they turn into the light. */
        public boolean metalGlint = true;
        public int woodAmount = 100;
        /** Share of wood drops that stay resin drops, which pool like blood. */
        public int resinShare = 35;
        public int emberAmount = 100;
        /** A wisp of smoke now and then among the embers. */
        public boolean emberSmoke = true;
        public int sparkAmount = 100;
        public int windAmount = 100;
        /** How thick the gusts look. */
        public int windOpacity = 100;
        /** How long chips, flakes, splinters, embers and sparks last. */
        public int pieceLifetime = 100;
        /** Blood marked as glowing in the mob table (glow squid ink) glows at all. */
        public boolean glow = true;
        /** How thick glowing clouds under water look. */
        public int glowCloudOpacity = 100;

        /** Pieces per drop for this kind: 1 is one piece for every drop. */
        public float amount(BloodKind kind) {
            int pct = switch (kind) {
                case BONE, DEBRIS -> boneAmount;
                case METAL -> metalAmount;
                case WOOD -> woodAmount;
                case EMBER -> emberAmount;
                case SPIRIT -> sparkAmount;
                case WIND -> windAmount;
                default -> 100;
            };
            return Math.max(0, pct) / 100.0f;
        }

        public float pieceLifetimeMultiplier() {
            return Math.max(10, pieceLifetime) / 100.0f;
        }
    }

    /**
     * Chunks of meat off mobs that bleed. Off unless switched on, so the mod is not that gory
     * out of the box. The Gore preset switches them on; the other presets leave them alone.
     */
    /**
     * Blood that sticks to the mob that was hit: it rides along, runs down and drips off. Off
     * unless switched on; presets leave the switch alone.
     */
    public static class MobCoatSettings {
        public boolean enabled = false;
        /** How much of each hit stays on the body. */
        public int amount = 100;
        /** How long it lasts before it has dried away. */
        public int lifetimeSeconds = 45;
        /** Heavy blood runs down the body. */
        public boolean runs = true;
        /** Blood drips off the body and stains the ground below. */
        public boolean drips = true;
    }

    public static class GoreSettings {
        /** Chunks of meat fly off bleeding mobs: now and then on a hard hit, a handful on death. */
        public boolean giblets = false;
        /** How many fly off. */
        public int gibletAmount = 100;
        /** They bleed as they fly and where they land. */
        public boolean gibletTrails = true;
    }

    /** Ragdolls and mob pieces of Physics Mod (switched on in {@link GeneralSettings#physicsModRagdolls}). */
    public static class PhysicsModSettings {
        /** How long a ragdoll keeps bleeding, in seconds. */
        public int bleedSeconds = 8;
        /** How much limbs and pieces drip. */
        public int drips = 100;
        /** Drops thrown up where a limb or piece lands hard. */
        public int splashes = 100;
        /** A stain where a limb or piece lands hard. */
        public boolean stains = true;
        /** How big those stains are. */
        public int stainSize = 100;
        /** Share of hard landings of a whole ragdoll that stain (broken ragdolls and pieces always do). */
        public int wholeRagdollStains = 35;
        /** Smears where limbs and pieces slide along the ground. */
        public boolean smears = true;
        /** How often sliding limbs and pieces smear. */
        public int smearAmount = 100;
        /** Mobs that break into blocky or fractured pieces bleed too, not just ragdolls. */
        public boolean pieces = true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // VANILLA ENTITIES - Unified settings for vanilla Minecraft mobs
    // ══════════════════════════════════════════════════════════════════════

    public static class VanillaEntities {
        /**
         * Map of entity type to settings.
         * Key: entity type (e.g., "zombie", "cow", "allay")
         * Value: EntitySettings with all configuration
         */
        public Map<String, EntitySettings> entities = new HashMap<>();

        /** Bumped when the built-in table changes shape; older tables are rebuilt on load. */
        public static final int CURRENT_VERSION = 5;
        public int version = 0;

        /**
         * Initialize all default entity settings.
         * Called when creating a new config or when config file doesn't exist.
         */
        public void initializeDefaults() {
            version = CURRENT_VERSION;
            BloodKind L = BloodKind.LIQUID, D = BloodKind.DEBRIS, E = BloodKind.EMBER, P = BloodKind.POWDER,
                    B = BloodKind.BONE, M = BloodKind.METAL, W = BloodKind.WOOD, S = BloodKind.SPIRIT,
                    G = BloodKind.WIND;

            // Colours are picked from what the mob is made of, or from the particles it already
            // emits in vanilla (slime balls, dragon breath, portal dust, flames, sculk, ink...).
            int RED         = 0x7A0F0F;  // warm-blooded animals, people
            int DARK_RED    = 0x4A1414;  // undead: old, thick blood
            int PIG_RED     = 0x8A1A1A;  // piglins, hoglins
            int BONE        = 0xC9C2A8;  // skeleton bone dust
            int SOOT_BONE   = 0x3D3A3A;  // wither skeleton
            int DRIED_DUST  = 0xA08C6E;  // parched
            int NIGHT_BLUE  = 0x2A3660;  // phantom
            int SCULK       = 0x0F4B58;  // warden (sculk teal)
            int DRAGON      = 0x7A22A6;  // ender dragon (dragon breath)
            int END_PURPLE  = 0x5E1C82;  // enderman, endermite (portal dust)
            int SHULKER     = 0x8A3AA0;  // shulker
            int WITHER      = 0x1E1626;  // wither
            int FLAME       = 0xF5A623;  // blaze embers (flame particle)
            int LAVA        = 0xD3540A;  // magma cube (lava particle)
            int SULFUR      = 0xD8D98A;  // sulfur cube: pale goo (sulfur_cube_goo particle)
            int GHAST       = 0xC9C9D9;  // ghast: pale, tear-like
            int HAPPY_PINK  = 0xF2BFD9;  // happy ghast
            int STRIDER     = 0xC93A1A;  // strider: lava-warm
            int IRON        = 0xC9C7C1;  // iron golem flakes: light steel, from its texture
            int COPPER      = 0xB87333;  // copper golem flakes
            int SNOW        = 0xEDF4FA;  // snow golem
            int SLIME       = 0x7FCF4E;  // slime (slime ball particle)
            int CREEPER     = 0x2F6B1E;  // creeper: plant green
            int HEMOLYMPH   = 0x3B7A2A;  // spiders: greenish insect blood
            int BUG_GRAY    = 0x8F8F80;  // silverfish
            int RESIN       = 0xD98B34;  // creaking: resin
            int GUST        = 0xC8ECF2;  // breeze: wind puffs
            int ALLAY       = 0x2CBFF0;  // allay: its own cyan glow
            int SPIRIT      = 0x9FB6DC;  // vex: glimmer
            int HONEY       = 0xC9961A;  // bee
            int INK         = 0x101828;  // squid ink
            int GLOW_INK    = 0x2FAF9E;  // glow squid (glow ink sac)
            int GUARDIAN    = 0x2E7F86;  // guardians: teal
            int NAUTILUS    = 0x1E3F7A;  // nautilus

            // ── UNDEAD ──
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
            entities.put("skeleton", new EntitySettings(true, false, false, BONE, B));
            entities.put("stray", new EntitySettings(true, false, false, BONE, B));
            entities.put("bogged", new EntitySettings(true, false, false, BONE, B));
            entities.put("skeleton_horse", new EntitySettings(true, false, false, BONE, B));
            entities.put("wither_skeleton", new EntitySettings(true, false, false, SOOT_BONE, B));
            entities.put("parched", new EntitySettings(true, false, false, DRIED_DUST, B));

            // ── BOSSES & SPECIAL ──
            entities.put("warden", new EntitySettings(true, true, true, SCULK, L));
            entities.put("ender_dragon", new EntitySettings(true, true, true, DRAGON, L));
            entities.put("wither", new EntitySettings(true, false, false, WITHER, B));

            // ── NETHER ──
            entities.put("blaze", new EntitySettings(true, false, false, FLAME, E));
            entities.put("magma_cube", new EntitySettings(true, false, false, LAVA, E));
            entities.put("sulfur_cube", new EntitySettings(true, false, true, SULFUR, L));
            entities.put("ghast", new EntitySettings(true, true, true, GHAST, L));
            entities.put("happy_ghast", new EntitySettings(true, true, true, HAPPY_PINK, L));
            entities.put("strider", new EntitySettings(true, true, true, STRIDER, L));
            entities.put("hoglin", new EntitySettings(true, true, true, PIG_RED, L));
            entities.put("piglin", new EntitySettings(true, true, true, PIG_RED, L));
            entities.put("piglin_brute", new EntitySettings(true, true, true, PIG_RED, L));

            // ── END ──
            entities.put("enderman", new EntitySettings(true, true, true, END_PURPLE, L));
            entities.put("endermite", new EntitySettings(true, true, true, END_PURPLE, L));
            entities.put("shulker", new EntitySettings(true, true, true, SHULKER, L));

            // ── CONSTRUCTS & NON-ORGANIC ──
            entities.put("iron_golem", new EntitySettings(true, false, false, IRON, M));
            entities.put("copper_golem", new EntitySettings(true, false, false, COPPER, M));
            entities.put("snow_golem", new EntitySettings(true, false, true, SNOW, P));
            // The creaking's colour is its resin; the splinters mix it with bark brown.
            entities.put("creaking", new EntitySettings(true, false, true, RESIN, W));
            entities.put("creaking_transient", new EntitySettings(true, false, true, RESIN, W));
            entities.put("breeze", new EntitySettings(true, false, false, GUST, G));
            entities.put("allay", new EntitySettings(true, false, false, ALLAY, S));
            entities.put("vex", new EntitySettings(true, false, false, SPIRIT, S));
            entities.put("slime", new EntitySettings(true, false, true, SLIME, L));
            entities.put("creeper", new EntitySettings(true, true, true, CREEPER, L));
            entities.put("silverfish", new EntitySettings(true, true, true, BUG_GRAY, L));

            // ── PASSIVE ANIMALS ──
            for (String s : new String[]{"cow", "mooshroom", "chicken", "pig", "sheep", "horse", "donkey", "mule",
                    "llama", "trader_llama", "camel", "rabbit", "killer_bunny", "turtle", "armadillo", "sniffer",
                    "wolf", "cat", "ocelot", "fox", "parrot", "bat", "panda", "polar_bear", "goat",
                    "dolphin", "cod", "salmon", "tropical_fish", "pufferfish", "axolotl", "tadpole", "frog"}) {
                entities.put(s, new EntitySettings(true, true, true, RED, L));
            }

            // ── AQUATIC WITH ODD BLOOD ──
            entities.put("squid", new EntitySettings(true, true, true, INK, L));
            entities.put("glow_squid", new EntitySettings(true, true, true, GLOW_INK, L));
            entities.put("guardian", new EntitySettings(true, true, true, GUARDIAN, L));
            entities.put("elder_guardian", new EntitySettings(true, true, true, GUARDIAN, L));
            entities.put("nautilus", new EntitySettings(true, true, true, NAUTILUS, L));

            // ── BUGS ──
            entities.put("bee", new EntitySettings(true, true, true, HONEY, L));
            entities.put("spider", new EntitySettings(true, true, true, HEMOLYMPH, L));
            entities.put("cave_spider", new EntitySettings(true, true, true, HEMOLYMPH, L));

            // ── VILLAGERS & ILLAGERS ──
            for (String s : new String[]{"villager", "wandering_trader", "witch", "evoker", "vindicator", "pillager",
                    "ravager", "illusioner"}) {
                entities.put(s, new EntitySettings(true, true, true, RED, L));
            }

            // Glow squid ink glows, like the squid.
            entities.get("glow_squid").glows = true;

            // Inanimate display entities - never bleed.
            entities.put("armor_stand", new EntitySettings(false, false, false, RED));
            entities.put("mannequin", new EntitySettings(false, false, false, RED));
        }

        /**
         * Ensure all entities have default settings.
         * Called after loading config to add any missing entities.
         */
        public void ensureDefaults() {
            if (version < 2) {
                // The per-mob table was overhauled (blood kinds, colours): start from the new defaults.
                entities.clear();
                initializeDefaults();
                return;
            }
            VanillaEntities defaults = new VanillaEntities();
            defaults.initializeDefaults();

            if (version < 3) {
                // 1.0.2 fixed these colours. Entries the player never changed take the new defaults.
                refreshIfUntouched(defaults, "sulfur_cube", 0xE0D040);
                refreshIfUntouched(defaults, "allay", 0x9FB6DC);
                refreshIfUntouched(defaults, "iron_golem", 0x8C8C8C);
                version = 3;
            }
            if (version < 4) {
                // New kinds: bone, metal, wood, spirit and wind. Only mobs still on plain debris
                // move over (taking the new default's puddle setting with it: resin pools); a
                // hand-picked kind stays.
                for (String id : new String[]{"skeleton", "stray", "bogged", "skeleton_horse", "wither_skeleton",
                        "parched", "wither", "iron_golem", "copper_golem", "creaking", "creaking_transient",
                        "breeze", "allay", "vex"}) {
                    EntitySettings current = entities.get(id);
                    if (current != null && current.getKind() == BloodKind.DEBRIS) {
                        EntitySettings fresh = defaults.entities.get(id);
                        current.kind = fresh.kind;
                        current.transformToStains = fresh.transformToStains;
                    }
                }
                version = 4;
            }
            if (version < 5) {
                // Glowing blood is new; the glow squid's starts glowing.
                EntitySettings glowSquid = entities.get("glow_squid");
                if (glowSquid != null) glowSquid.glows = true;
                version = 5;
            }

            // Add any missing entities from defaults
            for (Map.Entry<String, EntitySettings> entry : defaults.entities.entrySet()) {
                entities.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        /** Replaces an entry with its new default while it still holds the old default colour. */
        private void refreshIfUntouched(VanillaEntities defaults, String entityType, int oldColour) {
            EntitySettings current = entities.get(entityType);
            if (current != null && current.bloodColor == oldColour) {
                entities.put(entityType, defaults.entities.get(entityType));
            }
        }

        /**
         * Get settings for an entity type.
         */
        public EntitySettings getSettings(String entityType) {
            return entities.get(entityType);
        }

        /**
         * Check if entity type can bleed at all.
         */
        public boolean doesEntityBleed(String entityType) {
            EntitySettings settings = entities.get(entityType);
            return settings == null || settings.enabled; // default true if not in map
        }

        /**
         * Check if entity should drip blood when at low health.
         */
        public boolean shouldEntityDripAtLowHealth(String entityType) {
            EntitySettings settings = entities.get(entityType);
            if (settings == null) return true; // default true if not in map
            return settings.enabled && settings.canDripAtLowHealth;
        }

        /**
         * Settings for a single vanilla entity.
         */
        public static class EntitySettings {
            public boolean enabled = true;              // Can this entity bleed?
            public boolean canDripAtLowHealth = true;   // Can drip blood at low health?
            public boolean transformToStains = true;    // Do particles turn into stains/fog clouds?
            public int bloodColor = 0x660303;           // RGB color (0xRRGGBB)
            public String kind = "liquid";              // liquid | debris | ember | powder (see BloodKind)
            public boolean glows = false;               // drops and fresh puddles glow in the dark

            /**
             * Default constructor for GSON
             */
            public EntitySettings() {
            }

            /**
             * Constructor with all parameters
             */
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

            /**
             * Convert to BloodColor.Color object
             */
            public BloodColor.Color toColor() {
                float r = ((bloodColor >> 16) & 0xFF) / 255.0f;
                float g = ((bloodColor >> 8) & 0xFF) / 255.0f;
                float b = (bloodColor & 0xFF) / 255.0f;

                return new BloodColor.Color(r, g, b);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MODDED ENTITIES - Custom settings for modded mobs
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Configuration for custom modded entities.
     * Users can add entries like "modname:mob_name" to configure blood behavior
     * for entities from other mods.
     */
    public static class ModdedEntities {
        /**
         * List of custom modded entity configurations.
         * Each entry maps an entity ID (e.g., "mymod:custom_mob") to its blood settings.
         */
        public Map<String, ModdedEntitySettings> customEntities = new HashMap<>();

        /**
         * Get blood settings for a modded entity.
         *
         * @param entityId Full entity identifier (e.g., "mymod:custom_mob")
         * @return Settings if configured, null otherwise
         */
        public ModdedEntitySettings getSettings(String entityId) {
            return customEntities.get(entityId);
        }

        /** Guesses for mobs nobody has set up, made once per mob (see {@link com.simpleblood.MobTable#guess}). */
        private transient Map<String, ModdedEntitySettings> guessed;

        /**
         * What a mob from another mod bleeds: its entry when it has one, otherwise a first
         * guess from its name. Null for vanilla mobs, which have their own table.
         */
        public ModdedEntitySettings effective(String entityId) {
            ModdedEntitySettings set = customEntities.get(entityId);
            if (set != null || entityId.startsWith("minecraft:")) return set;
            if (guessed == null) guessed = new HashMap<>();
            return guessed.computeIfAbsent(entityId, id -> {
                net.minecraft.resources.Identifier parsed = net.minecraft.resources.Identifier.tryParse(id);
                return parsed == null ? null : com.simpleblood.MobTable.guess(parsed);
            });
        }

        /** Whether this mob's entry is only a guess (nobody has changed it). */
        public boolean isGuess(String entityId) {
            return !customEntities.containsKey(entityId);
        }

        /** The entry to change for a mob: a guessed one becomes a real entry first. */
        public ModdedEntitySettings editable(String entityId) {
            ModdedEntitySettings set = customEntities.get(entityId);
            if (set != null) return set;
            ModdedEntitySettings from = effective(entityId);
            ModdedEntitySettings copy = from == null ? new ModdedEntitySettings() : new ModdedEntitySettings(from.enabled, from.canDripAtLowHealth, from.transformToStains, from.bloodColor);
            if (from != null) {
                copy.kind = from.kind;
                copy.glows = from.glows;
            }
            customEntities.put(entityId, copy);
            return copy;
        }

        /**
         * Check if a modded entity has custom settings configured.
         */
        public boolean hasCustomSettings(String entityId) {
            return customEntities.containsKey(entityId);
        }

        /**
         * Add or update settings for a modded entity.
         * This is primarily for config file management.
         */
        public void putSettings(String entityId, ModdedEntitySettings settings) {
            customEntities.put(entityId, settings);
        }

        /**
         * Settings for a single modded entity.
         */
        public static class ModdedEntitySettings {
            public boolean enabled = true;              // Can this entity bleed?
            public boolean canDripAtLowHealth = true;   // Can drip blood at low health?
            public boolean transformToStains = true;    // Do particles turn into stains/fog clouds?
            public int bloodColor = 0x660303;           // RGB color (0xRRGGBB)
            public String kind = "liquid";              // liquid | debris | ember | powder (see BloodKind)
            public boolean glows = false;               // drops and fresh puddles glow in the dark

            public BloodKind getKind() {
                return BloodKind.parse(kind);
            }

            /**
             * Default constructor for GSON
             */
            public ModdedEntitySettings() {
            }

            /**
             * Constructor with all parameters
             */
            public ModdedEntitySettings(boolean enabled, boolean canDripAtLowHealth, boolean transformToStains, int bloodColor) {
                this.enabled = enabled;
                this.canDripAtLowHealth = canDripAtLowHealth;
                this.transformToStains = transformToStains;
                this.bloodColor = bloodColor;
            }

            /**
             * Convert to BloodColor.Color object
             */
            public BloodColor.Color toColor() {
                float r = ((bloodColor >> 16) & 0xFF) / 255.0f;
                float g = ((bloodColor >> 8) & 0xFF) / 255.0f;
                float b = (bloodColor & 0xFF) / 255.0f;

                return new BloodColor.Color(r, g, b);
            }
        }
    }
}