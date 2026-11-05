package com.simpleblood.gui;

import com.simpleblood.SimpleBloodConfig;

public final class Presets {

    private Presets() {}

    public record Preset(String name, String description, java.util.function.Consumer<SimpleBloodConfig> apply) {}

    public static final Preset[] ALL = {
            new Preset("simpleblood.preset.vanilla", "simpleblood.preset.the_defaults_restrained_blood_that", c -> {
                defaults(c);
                c.general.bloodAmount = 100;
                c.general.particleBudget = 1500;
                drops(c, 210, 100, 100, 100);
                bursts(c, 100, 100, 100, 100, 100, 100);
                bleeding(c, 50, 100, 100, 15);
                puddles(c, 16, "block", 100, 92, 60, 2048, 48, true, true, true, true, 6);
                hits(c, 65, true, true);
                water(c, true, 100, 100, 100);
                sound(c, 100, 100, true, true);
            }),
            new Preset("simpleblood.preset.subtle", "simpleblood.preset.a_hint_of_blood_small", c -> {
                defaults(c);
                c.general.bloodAmount = 55;
                c.general.particleBudget = 800;
                drops(c, 160, 80, 110, 100);
                bursts(c, 80, 80, 80, 150, 80, 90);
                bleeding(c, 35, 60, 70, 8);
                puddles(c, 16, "block", 65, 78, 25, 768, 40, true, true, true, true, 3);
                hits(c, 50, true, false);
                water(c, true, 80, 80, 70);
                sound(c, 70, 100, true, false);
                throwing(c, 85, 75);
                ragdolls(c, 5, 60, 60, 40, 75, 75, 20, 60);
                armor(c, 35, true);
                pieces(c, 70);
                gibletLook(c, 70, true);
                mobBlood(c, 70, 30);
            }),
            new Preset("simpleblood.preset.realistic", "simpleblood.preset.heavy_drops_mostly_out_of", c -> {
                defaults(c);
                c.general.bloodAmount = 90;
                c.general.particleBudget = 1500;
                drops(c, 180, 100, 130, 90);
                bursts(c, 90, 110, 80, 120, 100, 80);
                bleeding(c, 45, 90, 90, 40);
                puddles(c, 16, "block", 110, 94, 300, 2048, 48, true, true, true, true, 8);
                hits(c, 85, true, true);
                water(c, true, 110, 140, 90);
                sound(c, 90, 95, true, true);
                throwing(c, 100, 75);
                ragdolls(c, 15, 100, 90, 90, 125, 100, 45, 100);
                armor(c, 50, true);
                pieces(c, 100);
                gibletLook(c, 100, true);
                mobBlood(c, 100, 60);
            }),
            new Preset("simpleblood.preset.cinematic", "simpleblood.preset.big_floaty_sprays_puddles_that", c -> {
                defaults(c);
                c.general.bloodAmount = 130;
                c.general.particleBudget = 2500;
                drops(c, 230, 120, 90, 110);
                bursts(c, 120, 130, 110, 80, 140, 120);
                bleeding(c, 60, 120, 120, 25);
                puddles(c, 16, "block", 140, 96, 150, 2500, 64, true, true, true, true, 9);
                hits(c, 80, true, true);
                water(c, true, 130, 130, 120);
                sound(c, 110, 95, true, true);
                throwing(c, 120, 130);
                ragdolls(c, 12, 120, 130, 140, 125, 125, 50, 110);
                armor(c, 60, true);
                pieces(c, 120);
                gibletLook(c, 120, true);
                mobBlood(c, 120, 60);
            }),
            new Preset("simpleblood.preset.retro", "simpleblood.preset.chunky_double_size_pixels_with", c -> {
                defaults(c);
                c.general.bloodAmount = 120;
                c.general.particleBudget = 1500;
                drops(c, 260, 90, 120, 80);
                bursts(c, 130, 70, 120, 100, 150, 130);
                bleeding(c, 50, 100, 100, 12);
                puddles(c, 8, "pattern", 120, 100, 90, 2048, 48, true, true, true, true, 6);
                hits(c, 65, true, true);
                water(c, true, 100, 100, 100);
                sound(c, 100, 110, true, true);
                throwing(c, 110, 110);
                ragdolls(c, 8, 100, 110, 110, 100, 100, 35, 100);
                armor(c, 50, true);
                pieces(c, 120);
                gibletLook(c, 100, true);
                mobBlood(c, 100, 45);
            }),
            new Preset("simpleblood.preset.gore", "simpleblood.preset.way_too_much_blood_puddles", c -> {
                defaults(c);
                c.general.bloodAmount = 220;
                c.general.particleBudget = 4000;
                drops(c, 240, 130, 100, 100);
                bursts(c, 200, 120, 140, 50, 220, 150);
                bleeding(c, 75, 180, 170, 45);
                puddles(c, 16, "block", 220, 100, 400, 3000, 80, false, true, true, true, 12);
                hits(c, 70, true, true);
                water(c, true, 160, 150, 140);
                sound(c, 130, 90, true, true);
                c.gore.giblets = true;
                c.mobCoat.enabled = true;
                throwing(c, 130, 120);
                ragdolls(c, 30, 160, 170, 200, 200, 150, 80, 150);
                armor(c, 70, true);
                pieces(c, 150);
                gibletLook(c, 150, true);
                mobBlood(c, 150, 90);
            }),
            new Preset("simpleblood.preset.performance", "simpleblood.preset.fewer_drops_fewer_painted_faces", c -> {
                defaults(c);
                c.general.bloodAmount = 80;
                c.general.particleBudget = 500;
                drops(c, 200, 70, 110, 100);
                bursts(c, 80, 70, 100, 200, 80, 100);
                bleeding(c, 40, 60, 80, 8);
                puddles(c, 16, "block", 90, 90, 30, 512, 32, true, false, false, false, 4);
                hits(c, 65, false, false);
                water(c, false, 100, 70, 100);
                sound(c, 100, 100, false, false);
                throwing(c, 90, 90);
                ragdolls(c, 5, 60, 60, 70, 75, 100, 20, 50);
                armor(c, 50, false);
                pieces(c, 60);
                gibletLook(c, 70, false);
                mobBlood(c, 80, 30);
            }),
    };

    private static void drops(SimpleBloodConfig c, int size, int lifetime, int gravity, int drag) {
        c.particles.particleSize = size;
        c.particles.particleLifetime = lifetime;
        c.particles.particleGravity = gravity;
        c.particles.particleDrag = drag;
    }

    private static void bursts(SimpleBloodConfig c, int hitIntensity, int hitDuration, int hitSpread, int cooldownMs,
                               int deathIntensity, int deathSpread) {
        c.hitBurst.burstIntensity = hitIntensity;
        c.hitBurst.burstDuration = hitDuration;
        c.hitBurst.burstSpread = hitSpread;
        c.hitBurst.damageCooldown = cooldownMs;
        c.deathBurst.deathIntensity = deathIntensity;
        c.deathBurst.deathSpread = deathSpread;
    }

    private static void bleeding(SimpleBloodConfig c, int threshold, int frequency, int intensity, int seconds) {
        c.lowHealth.threshold = threshold;
        c.lowHealth.dripFrequency = frequency;
        c.lowHealth.dripIntensity = intensity;
        c.lowHealth.dripDurationSeconds = seconds;
    }

    private static void puddles(SimpleBloodConfig c, int resolution, String highlights, int amount, int opacity,
                                int lifetimeSeconds, int maxTiles, int renderDistance, boolean rainWashes,
                                boolean wallsAndCeilings, boolean detailedShapes, boolean footprints, int footprintSteps) {
        c.surfaces.resolution = resolution;
        c.surfaces.highlightMode = highlights;
        c.surfaces.amount = amount;
        c.surfaces.opacity = opacity;
        c.surfaces.lifetimeSeconds = lifetimeSeconds;
        c.surfaces.maxTiles = maxTiles;
        c.surfaces.renderDistance = renderDistance;
        c.surfaces.rainWashes = rainWashes;
        c.surfaces.wallsAndCeilings = wallsAndCeilings;
        c.surfaces.detailedShapes = detailedShapes;
        c.surfaces.footprints = footprints;
        c.surfaces.footprintSteps = footprintSteps;
    }

    private static void hits(SimpleBloodConfig c, int share, boolean weaponFlavour, boolean exitSpray) {
        c.directional.share = share;
        c.directional.weaponFlavour = weaponFlavour;
        c.directional.entrySpatter = exitSpray;
    }

    private static void throwing(SimpleBloodConfig c, int speed, int up) {
        c.particles.throwSpeed = speed;
        c.particles.throwUp = up;
    }

    private static void ragdolls(SimpleBloodConfig c, int bleedSeconds, int drips, int splashes, int spurts,
                                 int supply, int stainSize, int wholeStains, int smears) {
        c.physicsMod.bleedSeconds = bleedSeconds;
        c.physicsMod.drips = drips;
        c.physicsMod.splashes = splashes;
        c.physicsMod.spurts = spurts;
        c.physicsMod.bloodSupply = supply;
        c.physicsMod.stainSize = stainSize;
        c.physicsMod.wholeRagdollStains = wholeStains;
        c.physicsMod.smearAmount = smears;
    }

    private static void armor(SimpleBloodConfig c, int bleed, boolean shards) {
        c.combat.armorBleed = bleed;
        c.combat.armorShards = shards;
    }

    private static void pieces(SimpleBloodConfig c, int amount) {
        c.kinds.boneAmount = amount;
        c.kinds.metalAmount = amount;
        c.kinds.woodAmount = amount;
        c.kinds.emberAmount = amount;
        c.kinds.sparkAmount = amount;
        c.kinds.windAmount = amount;
    }

    private static void gibletLook(SimpleBloodConfig c, int amount, boolean trails) {
        c.gore.gibletAmount = amount;
        c.gore.gibletTrails = trails;
    }

    private static void mobBlood(SimpleBloodConfig c, int amount, int seconds) {
        c.mobCoat.amount = amount;
        c.mobCoat.lifetimeSeconds = seconds;
    }

    private static void water(SimpleBloodConfig c, boolean clouds, int size, int lifetime, int opacity) {
        c.underwater.transformToFog = clouds;
        c.underwater.fogSize = size;
        c.underwater.fogLifetime = lifetime;
        c.underwater.fogOpacity = opacity;
    }

    private static void sound(SimpleBloodConfig c, int volume, int pitch, boolean landing, boolean footsteps) {
        SimpleBloodConfig.AudioSettings d = new SimpleBloodConfig.AudioSettings();
        c.audio.soundVolume = volume;
        c.audio.soundPitch = pitch;
        c.audio.landingVolume = landing ? d.landingVolume : 0;
        c.audio.pieceVolume = landing ? d.pieceVolume : 0;
        c.audio.ledgeDripVolume = landing ? d.ledgeDripVolume : 0;
        c.audio.waterVolume = landing ? d.waterVolume : 0;
        c.audio.footstepVolume = footsteps ? d.footstepVolume : 0;
        c.audio.landingSounds = true;
        c.audio.footstepSounds = true;
    }

    public static void defaults(SimpleBloodConfig c) {
        SimpleBloodConfig d = new SimpleBloodConfig();
        keepChoices(c, d);
        c.general.bloodAmount = d.general.bloodAmount;
        c.general.particleBudget = d.general.particleBudget;
        c.particles = d.particles;
        c.surfaces = d.surfaces;
        c.hitBurst = d.hitBurst;
        c.deathBurst = d.deathBurst;
        c.lowHealth = d.lowHealth;
        c.audio = d.audio;
        c.underwater = d.underwater;
        c.kinds = d.kinds;
        c.physicsMod = d.physicsMod;
        c.gore = d.gore;
        c.mobCoat = d.mobCoat;
        c.directional = d.directional;
        c.combat = d.combat;
    }

    private static void keepChoices(SimpleBloodConfig from, SimpleBloodConfig into) {
        into.particles.hitBurst = from.particles.hitBurst;
        into.particles.deathBurst = from.particles.deathBurst;
        into.particles.lowHealthDrip = from.particles.lowHealthDrip;
        into.surfaces.enabled = from.surfaces.enabled;
        into.surfaces.skipSwayingBlocks = from.surfaces.skipSwayingBlocks;
        into.surfaces.viewBobSteps = from.surfaces.viewBobSteps;
        into.audio.soundEnabled = from.audio.soundEnabled;
        into.audio.muted = from.audio.muted == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(from.audio.muted);
        into.underwater.shaderCloudOpacity = from.underwater.shaderCloudOpacity;
        into.gore.giblets = from.gore.giblets;
        into.mobCoat.enabled = from.mobCoat.enabled;
        SimpleBloodConfig.DirectionalSettings fd = from.directional, id = into.directional;
        id.enabled = fd.enabled;
        id.swordWound = fd.swordWound;
        id.axeWound = fd.axeWound;
        id.maceWound = fd.maceWound;
        id.arrowWound = fd.arrowWound;
        id.fistWound = fd.fistWound;
        id.spearWound = fd.spearWound;
        id.itemWounds = fd.itemWounds == null ? new java.util.HashMap<>() : new java.util.HashMap<>(fd.itemWounds);
    }
}
