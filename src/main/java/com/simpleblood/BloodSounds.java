package com.simpleblood;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The sounds of blood (and what stands in for it) meeting the world. A hit sounds like what
 * the mob is made of: a thick drop with a wet squelch under a hard blow, bone cracking, a
 * metal tink, embers crackling, snow crunching. Then the small sounds afterwards: drops
 * tapping onto stone and plinking into puddles, drips falling from a bloody ledge, drops
 * pattering into water, embers hissing out, chips and flakes landing, a whole puddle slopping
 * onto the block below, feet squelching through fresh blood and sticking in drying blood.
 * All vanilla sound events, mostly pitched down (blood is thicker than water), rate limited
 * so a burst of thirty drops does not turn into a rain stick.
 *
 * Sounds start on the render thread only; a call from anywhere else is handed over to it.
 */
public final class BloodSounds {

    private BloodSounds() {}

    /** Our own dice: the level's random source is not for sharing between threads. */
    private static final RandomSource RNG = RandomSource.create();

    /** Landing sounds allowed per client tick, world-wide. */
    private static final int MAX_PER_TICK = 2;
    /** Sounds further away than this are never started (they would be inaudible anyway). */
    private static final double HEAR_RANGE = 14.0;

    private static long tick = -1;
    private static int playedThisTick;

    /**
     * A drop (or a splash-sized blob) landed on a surface.
     *
     * @param intoPuddle it landed in blood that was already there
     * @param heavy      a splash particle rather than a small drip
     */
    public static void landed(ClientLevel level, Vec3 at, boolean intoPuddle, boolean heavy, BloodKind kind) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds || kind != BloodKind.LIQUID) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> landed(level, at, intoPuddle, heavy, kind));
            return;
        }
        RandomSource rng = RNG;
        // Most drops are silent; the odd one is heard, more often when it is heavy or lands wet.
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

    /** A bone chip, metal flake or splinter hit the ground: a dry tick, a small clink, a hollow tock. */
    public static void pieceLanded(ClientLevel level, Vec3 at, BloodKind kind) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> pieceLanded(level, at, kind));
            return;
        }
        RandomSource rng = RNG;
        // A hit throws a dozen pieces; only a few are heard.
        if (rng.nextFloat() > (kind == BloodKind.METAL ? 0.35f : 0.25f)) return;
        if (!budget(level)) return;
        switch (kind) {
            case METAL -> play(level, SoundEvents.CHAIN_HIT, at, 0.12f, 1.7f + rng.nextFloat() * 0.3f);
            case WOOD -> play(level, SoundEvents.BAMBOO_WOOD_HIT, at, 0.12f, 1.6f + rng.nextFloat() * 0.3f);
            case LIQUID -> play(level, SoundEvents.SLIME_SQUISH_SMALL, at, 0.18f, 1.2f + rng.nextFloat() * 0.3f); // a giblet
            default -> play(level, SoundEvents.BONE_BLOCK_HIT, at, 0.14f, 1.6f + rng.nextFloat() * 0.35f);
        }
    }

    /**
     * A hit, or the killing blow, in the voice of what the mob is made of. A sound set for
     * the mob through the API replaces all of this. Under water only blood is heard, as a
     * muffled burst of bubbles while it clouds up.
     *
     * @param volume base loudness 0..1, from how hard the blow was (or, for a death, how big
     *               the body is)
     */
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
        boolean hard = death || volume >= 0.52f; // about 6 damage and up
        switch (kind) {
            case LIQUID -> {
                // A thick drop; a hard blow adds a low wet squelch, a death a heavier splat.
                play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, at, volume, 0.9f + p * 0.2f);
                if (death) play(level, SoundEvents.SLIME_BLOCK_BREAK, at, 0.22f + 0.2f * volume, 0.5f + p * 0.12f);
                else if (hard) play(level, SoundEvents.SLIME_SQUISH_SMALL, at, 0.15f + 0.15f * volume, 0.6f + p * 0.15f);
            }
            case BONE -> {
                // A dry crack, high and short; a death breaks properly and puffs dust.
                play(level, SoundEvents.BONE_BLOCK_HIT, at, 0.3f + 0.3f * volume, 1.25f + p * 0.25f);
                if (death) {
                    play(level, SoundEvents.BONE_BLOCK_BREAK, at, 0.35f + 0.25f * volume, 0.9f + p * 0.15f);
                    play(level, SoundEvents.SAND_BREAK, at, 0.18f, 1.5f + p * 0.2f);
                }
            }
            case METAL -> {
                // Shards: a light tink over the golem's own clang, a rattle when it falls apart.
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
                // A knock on wood; a death splits it.
                play(level, SoundEvents.WOOD_HIT, at, 0.3f + 0.3f * volume, 1.05f + p * 0.25f);
                if (death) play(level, SoundEvents.WOOD_BREAK, at, 0.4f + 0.2f * volume, 0.85f + p * 0.15f);
            }
            case SPIRIT -> {
                // A glassy chime; a death shatters into a little shower of them.
                play(level, SoundEvents.AMETHYST_BLOCK_CHIME, at, 0.3f + 0.3f * volume, 1.4f + p * 0.4f);
                if (death) play(level, SoundEvents.SMALL_AMETHYST_BUD_BREAK, at, 0.5f, 1.2f + p * 0.2f);
            }
            case WIND -> {
                // A soft rush of air under the breeze's own sounds.
                //? if 1.20.1 {
                /*play(level, SoundEvents.PHANTOM_FLAP, at, 0.15f + 0.2f * volume, death ? 1.1f : 1.6f + p * 0.3f); // no breeze yet
                *///?} else {
                play(level, SoundEvents.BREEZE_IDLE_AIR, at, 0.2f + 0.25f * volume, death ? 0.9f : 1.3f + p * 0.3f);
                //?}
            }
            default -> {
                // Plain debris: dry bits.
                play(level, SoundEvents.GRAVEL_HIT, at, 0.2f + 0.25f * volume, 1.3f + p * 0.25f);
                if (death) play(level, SoundEvents.SAND_BREAK, at, 0.3f, 1.2f + p * 0.2f);
            }
        }
    }

    /** Gap between two ledge drips, world-wide: a draining pool drips, it does not rattle. */
    private static final int DRIP_GAP_TICKS = 12;
    private static long lastDrip = -1000;

    /**
     * A drop that ran off a ledge or ceiling landed. Most fall silently; the odd one is heard
     * as a slow, hollow drip, so a bloody overhang keeps dripping like a cave.
     */
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

    /** A drop of blood fell into water (it clouds up there): a faint patter. */
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

    /** An ember went out in water: a tiny hiss. */
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

    /** A big wet slap: a mace smash or slam throwing a body's worth of blood at once. */
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

    /** A whole puddle dropped from a broken block onto the surface below. */
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

    /**
     * A foot came down in blood: a wet squelch in fresh blood, a sticky tack once it has
     * started to dry, a soft crunch in snow.
     *
     * @param tacky the blood underfoot is drying (still sticks to the sole, no longer splashes)
     */
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
