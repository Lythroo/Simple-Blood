package com.simpleblood;

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
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
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

    public static void pieceLanded(ClientLevel level, Vec3 at, BloodKind kind) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> pieceLanded(level, at, kind));
            return;
        }
        RandomSource rng = RNG;
        if (rng.nextFloat() > (kind == BloodKind.METAL ? 0.35f : 0.25f)) return;
        if (!budget(level)) return;
        switch (kind) {
            case METAL -> play(level, SoundEvents.CHAIN_HIT, at, 0.12f, 1.7f + rng.nextFloat() * 0.3f);
            case WOOD -> play(level, SoundEvents.BAMBOO_WOOD_HIT, at, 0.12f, 1.6f + rng.nextFloat() * 0.3f);
            default -> play(level, SoundEvents.BONE_BLOCK_HIT, at, 0.14f, 1.6f + rng.nextFloat() * 0.35f);
        }
    }

    public static void hit(ClientLevel level, net.minecraft.world.entity.LivingEntity entity, float volume,
                           boolean death, boolean underwater) {
        if (!SimpleBlood.isSoundEnabledFor(entity)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> hit(level, entity, volume, death, underwater));
            return;
        }
        RandomSource rng = RNG;
        Vec3 at = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        float p = rng.nextFloat();
        SoundEvent custom = SimpleBlood.customBloodSound(entity);
        if (custom != null) {
            if (!underwater) play(level, custom, at, volume, 0.9f + p * 0.2f);
            return;
        }
        BloodKind kind = SimpleBlood.bloodKindOf(entity);
        if (underwater) {
            if (kind == BloodKind.LIQUID) {
                play(level, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, at, 0.2f + 0.25f * volume, 0.5f + p * 0.2f);
            }
            return;
        }
        boolean hard = death || volume >= 0.52f;
        switch (kind) {
            case LIQUID -> {
                play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, at, volume, 0.9f + p * 0.2f);
                if (death) play(level, SoundEvents.SLIME_BLOCK_BREAK, at, 0.22f + 0.2f * volume, 0.5f + p * 0.12f);
                else if (hard) play(level, SoundEvents.SLIME_SQUISH_SMALL, at, 0.15f + 0.15f * volume, 0.6f + p * 0.15f);
            }
            case BONE -> {
                play(level, SoundEvents.BONE_BLOCK_HIT, at, 0.3f + 0.3f * volume, 1.25f + p * 0.25f);
                if (death) {
                    play(level, SoundEvents.BONE_BLOCK_BREAK, at, 0.35f + 0.25f * volume, 0.9f + p * 0.15f);
                    play(level, SoundEvents.SAND_BREAK, at, 0.18f, 1.5f + p * 0.2f);
                }
            }
            case METAL -> {
                play(level, SoundEvents.CHAIN_HIT, at, 0.2f + 0.25f * volume, 1.6f + p * 0.3f);
                if (death) play(level, SoundEvents.CHAIN_BREAK, at, 0.35f + 0.25f * volume, 1.1f + p * 0.2f);
            }
            case EMBER -> {
                play(level, SoundEvents.CAMPFIRE_CRACKLE, at, 0.4f + 0.4f * volume, 1.1f + p * 0.25f);
                if (hard) play(level, SoundEvents.LAVA_POP, at, 0.2f + 0.2f * volume, 0.8f + p * 0.3f);
            }
            case POWDER -> {
                play(level, SoundEvents.SNOW_BREAK, at, 0.3f + 0.3f * volume, 1.15f + p * 0.2f);
                if (death) play(level, SoundEvents.POWDER_SNOW_BREAK, at, 0.4f + 0.2f * volume, 0.95f + p * 0.1f);
            }
            case WOOD -> {
                play(level, SoundEvents.WOOD_HIT, at, 0.3f + 0.3f * volume, 1.05f + p * 0.25f);
                if (death) play(level, SoundEvents.WOOD_BREAK, at, 0.4f + 0.2f * volume, 0.85f + p * 0.15f);
            }
            case SPIRIT -> {
                play(level, SoundEvents.AMETHYST_BLOCK_CHIME, at, 0.3f + 0.3f * volume, 1.4f + p * 0.4f);
                if (death) play(level, SoundEvents.SMALL_AMETHYST_BUD_BREAK, at, 0.5f, 1.2f + p * 0.2f);
            }
            case WIND -> {
                play(level, SoundEvents.BREEZE_IDLE_AIR, at, 0.2f + 0.25f * volume, death ? 0.9f : 1.3f + p * 0.3f);
            }
            default -> {
                play(level, SoundEvents.GRAVEL_HIT, at, 0.2f + 0.25f * volume, 1.3f + p * 0.25f);
                if (death) play(level, SoundEvents.SAND_BREAK, at, 0.3f, 1.2f + p * 0.2f);
            }
        }
    }

    private static final int DRIP_GAP_TICKS = 12;
    private static long lastDrip = -1000;

    public static void drip(ClientLevel level, Vec3 at, boolean intoPuddle) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> drip(level, at, intoPuddle));
            return;
        }
        RandomSource rng = RNG;
        long t = level.getGameTime();
        if (t - lastDrip < DRIP_GAP_TICKS || rng.nextFloat() > 0.3f || !budget(level)) return;
        lastDrip = t;
        play(level, intoPuddle ? SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                at, 0.12f, 0.45f + rng.nextFloat() * 0.2f);
    }

    public static void intoWater(ClientLevel level, Vec3 at) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> intoWater(level, at));
            return;
        }
        RandomSource rng = RNG;
        if (rng.nextFloat() > 0.18f || !budget(level)) return;
        play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, at, 0.07f, 1.0f + rng.nextFloat() * 0.25f);
    }

    public static void fizzle(ClientLevel level, Vec3 at) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> fizzle(level, at));
            return;
        }
        RandomSource rng = RNG;
        if (rng.nextFloat() > 0.3f || !budget(level)) return;
        play(level, SoundEvents.FIRE_EXTINGUISH, at, 0.05f, 1.8f + rng.nextFloat() * 0.2f);
    }

    public static void splat(ClientLevel level, Vec3 at, float size) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> splat(level, at, size));
            return;
        }
        RandomSource rng = RNG;
        play(level, SoundEvents.SLIME_BLOCK_FALL, at, 0.25f + 0.3f * size, 0.5f + rng.nextFloat() * 0.1f);
        play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, at, 0.2f + 0.15f * size, 0.5f + rng.nextFloat() * 0.1f);
    }

    public static void plop(ClientLevel level, Vec3 at, int pixels) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
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

    public static void squelch(ClientLevel level, Vec3 at, float wetness, boolean tacky, boolean snow) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.footstepSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> squelch(level, at, wetness, tacky, snow));
            return;
        }
        RandomSource rng = RNG;
        if (snow) {
            play(level, SoundEvents.SNOW_STEP, at, 0.10f + 0.08f * wetness, 1.1f + rng.nextFloat() * 0.2f);
        } else if (tacky) {
            play(level, SoundEvents.HONEY_BLOCK_STEP, at, 0.07f + 0.08f * wetness, 1.3f + rng.nextFloat() * 0.25f);
        } else {
            play(level, SoundEvents.MUD_STEP, at, 0.08f + 0.10f * wetness, 1.25f + rng.nextFloat() * 0.25f);
        }
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
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                event, SoundSource.PLAYERS,
                volume * cfg.soundVolumeMultiplier(), pitch * cfg.soundPitchMultiplier(),
                RandomSource.create(), at.x, at.y, at.z));
    }
}
