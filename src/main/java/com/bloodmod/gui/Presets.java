package com.bloodmod.gui;

import com.bloodmod.BloodModConfig;

public final class Presets {

    private Presets() {}

    public record Preset(String name, String description, java.util.function.Consumer<BloodModConfig> apply) {}

    public static final Preset[] ALL = {
            new Preset("Vanilla+", "The defaults. Restrained blood that fits the game.", c -> {
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
            new Preset("Subtle", "A hint of blood. Small, quick, gone soon.", c -> {
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
            }),
            new Preset("Realistic", "Heavy drops, mostly out of the wound, stains that stick around for minutes.", c -> {
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
            }),
            new Preset("Cinematic", "Big floaty sprays, puddles that last, all the extras on.", c -> {
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
            }),
            new Preset("Retro", "Chunky double-size pixels with a flat pattern. Bright and bouncy.", c -> {
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
            }),
            new Preset("Gore", "Way too much blood. Puddles everywhere, for ages.", c -> {
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
            }),
            new Preset("Performance", "Fewer drops, fewer painted faces, no clouds. For slower machines.", c -> {
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
            }),
    };

    private static void drops(BloodModConfig c, int size, int lifetime, int gravity, int drag) {
        c.particles.particleSize = size;
        c.particles.particleLifetime = lifetime;
        c.particles.particleGravity = gravity;
        c.particles.particleDrag = drag;
    }

    private static void bursts(BloodModConfig c, int hitIntensity, int hitDuration, int hitSpread, int cooldownMs,
                               int deathIntensity, int deathSpread) {
        c.hitBurst.burstIntensity = hitIntensity;
        c.hitBurst.burstDuration = hitDuration;
        c.hitBurst.burstSpread = hitSpread;
        c.hitBurst.damageCooldown = cooldownMs;
        c.deathBurst.deathIntensity = deathIntensity;
        c.deathBurst.deathSpread = deathSpread;
    }

    private static void bleeding(BloodModConfig c, int threshold, int frequency, int intensity, int seconds) {
        c.lowHealth.threshold = threshold;
        c.lowHealth.dripFrequency = frequency;
        c.lowHealth.dripIntensity = intensity;
        c.lowHealth.dripDurationSeconds = seconds;
    }

    private static void puddles(BloodModConfig c, int resolution, String highlights, int amount, int opacity,
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

    private static void hits(BloodModConfig c, int share, boolean weaponFlavour, boolean exitSpray) {
        c.directional.share = share;
        c.directional.weaponFlavour = weaponFlavour;
        c.directional.entrySpatter = exitSpray;
    }

    private static void water(BloodModConfig c, boolean clouds, int size, int lifetime, int opacity) {
        c.underwater.transformToFog = clouds;
        c.underwater.fogSize = size;
        c.underwater.fogLifetime = lifetime;
        c.underwater.fogOpacity = opacity;
    }

    private static void sound(BloodModConfig c, int volume, int pitch, boolean landing, boolean footsteps) {
        c.audio.soundVolume = volume;
        c.audio.soundPitch = pitch;
        c.audio.landingSounds = landing;
        c.audio.footstepSounds = footsteps;
    }

    public static void defaults(BloodModConfig c) {
        BloodModConfig d = new BloodModConfig();
        c.general.bloodAmount = d.general.bloodAmount;
        c.general.particleBudget = d.general.particleBudget;
        c.particles = d.particles;
        c.surfaces = d.surfaces;
        c.hitBurst = d.hitBurst;
        c.deathBurst = d.deathBurst;
        c.lowHealth = d.lowHealth;
        c.audio = d.audio;
        c.underwater = d.underwater;
        c.directional = d.directional;
        c.player.playerBleed = d.player.playerBleed;
    }
}
