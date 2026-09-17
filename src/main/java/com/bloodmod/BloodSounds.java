package com.bloodmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class BloodSounds {

    private BloodSounds() {}

    private static final RandomSource RNG = RandomSource.create();

    private static final int MAX_PER_TICK = 2;
    private static final double HEAR_RANGE = 14.0;

    private static long tick = -1;
    private static int playedThisTick;

    public static void landed(ClientLevel level, Vec3 at, boolean intoPuddle, boolean heavy, BloodKind kind) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds || kind != BloodKind.LIQUID) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> landed(level, at, intoPuddle, heavy, kind));
            return;
        }
        RandomSource rng = RNG;
        float chance = (heavy ? 0.30f : 0.16f) + (intoPuddle ? 0.12f : 0f);
        if (rng.nextFloat() > chance) return;
        if (!budget(level)) return;
        if (intoPuddle) {
            play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, at,
                    0.16f + (heavy ? 0.06f : 0f), 0.70f + rng.nextFloat() * 0.25f);
        } else {
            play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, at,
                    0.10f + (heavy ? 0.05f : 0f), 0.55f + rng.nextFloat() * 0.25f);
        }
    }

    public static void plop(ClientLevel level, Vec3 at, int pixels) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> plop(level, at, pixels));
            return;
        }
        if (!nearEnough(level, at) || !budget(level)) return;
        RandomSource rng = RNG;
        float size = Math.min(1f, pixels / 60f);
        play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, at,
                0.18f + 0.22f * size, 0.45f + (1f - size) * 0.25f + rng.nextFloat() * 0.1f);
    }

    public static void squelch(ClientLevel level, Vec3 at, float wetness) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.footstepSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> squelch(level, at, wetness));
            return;
        }
        RandomSource rng = RNG;
        play(level, SoundEvents.MUD_STEP, at, 0.08f + 0.10f * wetness, 1.25f + rng.nextFloat() * 0.25f);
    }

    private static boolean nearEnough(ClientLevel level, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.distanceToSqr(at) <= HEAR_RANGE * HEAR_RANGE;
    }

    private static boolean budget(ClientLevel level) {
        long t = level.getGameTime();
        if (t != tick) {
            tick = t;
            playedThisTick = 0;
        }
        return playedThisTick++ < MAX_PER_TICK;
    }

    private static void play(ClientLevel level, SoundEvent event, Vec3 at, float volume, float pitch) {
        BloodModConfig cfg = BloodModClient.getConfig();
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                event, SoundSource.PLAYERS,
                volume * cfg.soundVolumeMultiplier(), pitch * cfg.soundPitchMultiplier(),
                RandomSource.create(), at.x, at.y, at.z));
    }
}
