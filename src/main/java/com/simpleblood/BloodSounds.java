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

    private static final SoundEvent SQUELCH = own("hit.squelch"), DEATH_SPLAT = own("death.splat"), SLAP = own("slap"),
            GIBLET = own("giblet.land"), SIZZLE = own("sizzle"), STUMP = own("stump");
    private static final float GIBLET_VOLUME = 0.16f;
    private static final float SIZZLE_VOLUME = 0.125f;

    private static SoundEvent own(String name) {
        return SoundEvent.createVariableRangeEvent(Ids.mod(name));
    }

    private static final RandomSource RNG = RandomSource.create();

    private static final int MAX_PER_TICK = 2;
    private static final double HEAR_RANGE = 14.0;

    private static long tick = -1;
    private static int playedThisTick;
    private static float channel = 1f;

    private static boolean channel(Preview sound, int percent) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg != null && cfg.audio.muted(sound.name())) return false;
        channel = Math.max(0, percent) / 100f;
        return channel > 0f;
    }

    public static boolean on(Preview sound) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null || !cfg.audio.muted(sound.name());
    }

    public static void landed(ClientLevel level, Vec3 at, boolean intoPuddle, boolean heavy, BloodKind kind) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds || kind != BloodKind.LIQUID) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> landed(level, at, intoPuddle, heavy, kind));
            return;
        }
        if (!channel(Preview.LANDING, cfg.audio.landingVolume)) return;
        com.simpleblood.Tally.count("sound.landingVolume");
        RandomSource rng = RNG;
        float chance = (heavy ? 0.30f : 0.16f) + (intoPuddle ? 0.12f : 0f);
        if (rng.nextFloat() > chance) return;
        if (!budget(level)) return;
        if (intoPuddle) {
            play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, at,
                    0.45f + (heavy ? 0.15f : 0f), 0.70f + rng.nextFloat() * 0.25f);
        } else {
            play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, at,
                    0.35f + (heavy ? 0.15f : 0f), 0.55f + rng.nextFloat() * 0.25f);
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
        if (!channel(Preview.PIECES, cfg.audio.pieceVolume)) return;
        com.simpleblood.Tally.count("sound.pieceVolume");
        RandomSource rng = RNG;
        if (rng.nextFloat() > (kind == BloodKind.METAL ? 0.35f : 0.25f)) return;
        if (!budget(level)) return;
        switch (kind) {
            case METAL -> play(level, SoundEvents.CHAIN_HIT, at, 0.12f, 1.7f + rng.nextFloat() * 0.3f);
            case WOOD -> play(level, SoundEvents.BAMBOO_WOOD_HIT, at, 0.12f, 1.6f + rng.nextFloat() * 0.3f);
            case LIQUID -> play(level, GIBLET, at, GIBLET_VOLUME, 0.9f + rng.nextFloat() * 0.25f);
            default -> play(level, SoundEvents.BONE_BLOCK_HIT, at, 0.14f, 1.6f + rng.nextFloat() * 0.35f);
        }
    }

    public static void hit(ClientLevel level, net.minecraft.world.entity.LivingEntity entity, float volume,
                           boolean death, boolean underwater) {
        if (Capture.active() || !SimpleBlood.isSoundEnabledFor(entity)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> hit(level, entity, volume, death, underwater));
            return;
        }
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !channel(Preview.HITS, cfg.audio.hitVolume)) return;
        com.simpleblood.Tally.count("sound.hitVolume");
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
                if (death) play(level, DEATH_SPLAT, at, 0.3f + 0.25f * volume, 0.9f + p * 0.15f);
                else if (hard) play(level, SQUELCH, at, 0.22f + 0.2f * volume, 0.9f + p * 0.2f);
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
                //? if 1.20.1 {
                /*play(level, SoundEvents.PHANTOM_FLAP, at, 0.15f + 0.2f * volume, death ? 1.1f : 1.6f + p * 0.3f);
                *///?} else {
                play(level, SoundEvents.BREEZE_IDLE_AIR, at, 0.2f + 0.25f * volume, death ? 0.9f : 1.3f + p * 0.3f);
                //?}
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
        if (!channel(Preview.LEDGE_DRIPS, cfg.audio.ledgeDripVolume)) return;
        com.simpleblood.Tally.count("sound.ledgeDripVolume");
        RandomSource rng = RNG;
        long t = level.getGameTime();
        boolean tooSoon = t >= lastDrip && t - lastDrip < DRIP_GAP_TICKS;
        if (tooSoon || rng.nextFloat() > 0.3f || !budget(level)) return;
        lastDrip = t;
        play(level, intoPuddle ? SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                at, 0.4f, 0.45f + rng.nextFloat() * 0.2f);
    }

    public static void intoWater(ClientLevel level, Vec3 at) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> intoWater(level, at));
            return;
        }
        if (!channel(Preview.WATER, cfg.audio.waterVolume)) return;
        com.simpleblood.Tally.count("sound.waterVolume");
        RandomSource rng = RNG;
        if (rng.nextFloat() > 0.18f || !budget(level)) return;
        play(level, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, at, 0.25f, 1.0f + rng.nextFloat() * 0.25f);
    }

    public static void fizzle(ClientLevel level, Vec3 at) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> fizzle(level, at));
            return;
        }
        if (!channel(Preview.WATER, cfg.audio.waterVolume)) return;
        com.simpleblood.Tally.count("sound.waterVolume");
        RandomSource rng = RNG;
        if (rng.nextFloat() > 0.3f || !budget(level)) return;
        play(level, SoundEvents.FIRE_EXTINGUISH, at, 0.05f, 1.8f + rng.nextFloat() * 0.2f);
    }

    public static void sizzle(ClientLevel level, Vec3 at) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> sizzle(level, at));
            return;
        }
        if (!channel(Preview.HEAT, cfg.audio.heatVolume)) return;
        com.simpleblood.Tally.count("sound.heatVolume");
        RandomSource rng = RNG;
        if (!budget(level)) return;
        play(level, SIZZLE, at, SIZZLE_VOLUME, 0.9f + rng.nextFloat() * 0.2f);
    }

    public static void stump(ClientLevel level, Vec3 at) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> stump(level, at));
            return;
        }
        if (!channel(Preview.TEARS, cfg.audio.tearVolume)) return;
        com.simpleblood.Tally.count("sound.stump");
        tear(level, at);
    }

    private static void tear(ClientLevel level, Vec3 at) {
        play(level, SQUELCH, at, 0.45f, 0.5f + RNG.nextFloat() * 0.06f);
        play(level, SLAP, at, 0.16f, 0.5f + RNG.nextFloat() * 0.05f);
    }

    public static void splat(ClientLevel level, Vec3 at, float size) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled()) return;
        if (!nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> splat(level, at, size));
            return;
        }
        if (!channel(Preview.SLAPS, cfg.audio.slapVolume)) return;
        com.simpleblood.Tally.count("sound.slapVolume");
        RandomSource rng = RNG;
        play(level, SLAP, at, 0.3f + 0.35f * size, 0.85f + rng.nextFloat() * 0.1f);
    }

    public static void plop(ClientLevel level, Vec3 at, int pixels) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.soundEnabled() || !cfg.audio.landingSounds) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> plop(level, at, pixels));
            return;
        }
        if (!nearEnough(level, at) || !budget(level)) return;
        if (!channel(Preview.LANDING, cfg.audio.landingVolume)) return;
        com.simpleblood.Tally.count("sound.landingVolume");
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
        if (!channel(Preview.FOOTSTEPS, cfg.audio.footstepVolume)) return;
        com.simpleblood.Tally.count("sound.footstepVolume");
        RandomSource rng = RNG;
        if (snow) {
            play(level, SoundEvents.SNOW_STEP, at, 0.10f + 0.08f * wetness, 1.1f + rng.nextFloat() * 0.2f);
        } else if (tacky) {
            play(level, SoundEvents.HONEY_BLOCK_STEP, at, 0.07f + 0.08f * wetness, 1.3f + rng.nextFloat() * 0.25f);
        } else {
            play(level, SoundEvents.MUD_STEP, at, 0.08f + 0.10f * wetness, 1.25f + rng.nextFloat() * 0.25f);
        }
    }

    public static void armorClank(ClientLevel level, Vec3 at, boolean projectile, boolean dull) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.audio.soundEnabled || !nearEnough(level, at)) return;
        if (!RenderThread.on()) {
            RenderThread.run(() -> armorClank(level, at, projectile, dull));
            return;
        }
        if (!channel(Preview.ARMOR, cfg.audio.armorVolume)) return;
        com.simpleblood.Tally.count("sound.armorVolume");
        float low = dull ? 0.8f : 1f;
        float base = projectile ? 1.3f + RNG.nextFloat() * 0.45f : 0.85f + RNG.nextFloat() * 0.3f;
        play(level, SoundEvents.CHAIN_HIT, at, 0.8f, base * low);
        play(level, SoundEvents.CHAIN_HIT, at, 0.8f, (base + 0.08f + RNG.nextFloat() * 0.1f) * low);
        float ring = projectile ? 1.6f + RNG.nextFloat() * 0.4f : 1.5f + RNG.nextFloat() * 0.35f;
        play(level, SoundEvents.ANVIL_LAND, at, projectile ? 0.32f : 0.4f, ring * low);
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

    public enum Preview { HITS, LANDING, LEDGE_DRIPS, WOUND_DRIPS, SLAPS, WATER, HEAT, PIECES, FOOTSTEPS, ARMOR, TEARS }

    private static boolean previewing;
    private static Preview looping;
    private static long nextPreviewAt, lastPreviewTick;
    private static final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> PREVIEW_SOUNDS = new java.util.ArrayList<>();
    private static String previewFiles = "";
    private static final java.util.List<String> NAMES_NOW = new java.util.ArrayList<>();

    public static void togglePreview(Preview which) {
        boolean again = looping == which;
        stopPreview();
        if (again) return;
        looping = which;
        nextPreviewAt = 0;
        lastPreviewTick = 0;
    }

    public static void stopPreview() {
        looping = null;
        PREVIEW_TURNS.clear();
        previewFiles = "";
        for (net.minecraft.client.resources.sounds.SoundInstance s : PREVIEW_SOUNDS) Minecraft.getInstance().getSoundManager().stop(s);
        PREVIEW_SOUNDS.clear();
    }

    public static Preview previewPlaying() {
        return looping;
    }

    public static String previewFiles() {
        return previewFiles;
    }

    public static boolean previewSounding() {
        PREVIEW_SOUNDS.removeIf(s -> !Minecraft.getInstance().getSoundManager().isActive(s));
        return !PREVIEW_SOUNDS.isEmpty();
    }

    public static void previewTick() {
        if (looping == null) return;
        long now = System.currentTimeMillis();
        if (lastPreviewTick != 0 && now - lastPreviewTick > 500) {
            stopPreview();
            return;
        }
        lastPreviewTick = now;
        if (now < nextPreviewAt) return;
        nextPreviewAt = now + (looping == Preview.ARMOR || looping == Preview.HITS ? 1400 : 1000);
        preview(looping);
    }

    public static void preview(Preview which) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null) return;
        SimpleBloodConfig.AudioSettings a = cfg.audio;
        int percent = switch (which) {
            case HITS -> a.hitVolume;
            case LANDING -> a.landingVolume;
            case LEDGE_DRIPS -> a.ledgeDripVolume;
            case WOUND_DRIPS -> a.woundDripVolume;
            case SLAPS -> a.slapVolume;
            case WATER -> a.waterVolume;
            case HEAT -> a.heatVolume;
            case PIECES -> a.pieceVolume;
            case FOOTSTEPS -> a.footstepVolume;
            case ARMOR -> a.armorVolume;
            case TEARS -> a.tearVolume;
        };
        if (!channel(which, percent)) {
            previewFiles = "muted";
            return;
        }
        com.simpleblood.Tally.count("sound.preview");
        previewing = true;
        NAMES_NOW.clear();
        try {
            RandomSource rng = RNG;
            switch (which) {
                case HITS -> {
                    play(null, SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, null, 0.7f, 0.9f + rng.nextFloat() * 0.2f);
                    play(null, SQUELCH, null, 0.35f, 0.9f + rng.nextFloat() * 0.2f);
                }
                case LANDING -> play(null, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, null, 0.45f, 0.55f + rng.nextFloat() * 0.25f);
                case LEDGE_DRIPS -> play(null, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, null, 0.4f, 0.45f + rng.nextFloat() * 0.2f);
                case WOUND_DRIPS -> play(null, SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, null, 0.4f, 0.8f + rng.nextFloat() * 0.3f);
                case SLAPS -> play(null, SLAP, null, 0.6f, 0.85f + rng.nextFloat() * 0.1f);
                case WATER -> play(null, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, null, 0.3f, 1.0f + rng.nextFloat() * 0.25f);
                case HEAT -> play(null, SIZZLE, null, SIZZLE_VOLUME, 0.9f + rng.nextFloat() * 0.2f);
                case PIECES -> play(null, GIBLET, null, GIBLET_VOLUME, 0.9f + rng.nextFloat() * 0.25f);
                case FOOTSTEPS -> play(null, SoundEvents.MUD_STEP, null, 0.15f, 1.25f + rng.nextFloat() * 0.25f);
                case TEARS -> tear(null, null);
                case ARMOR -> {
                    float base = 0.85f + rng.nextFloat() * 0.3f;
                    play(null, SoundEvents.CHAIN_HIT, null, 0.8f, base);
                    play(null, SoundEvents.CHAIN_HIT, null, 0.8f, base + 0.08f + rng.nextFloat() * 0.1f);
                    play(null, SoundEvents.ANVIL_LAND, null, 0.4f, 1.5f + rng.nextFloat() * 0.35f);
                }
            }
        } finally {
            previewing = false;
            if (!NAMES_NOW.isEmpty()) previewFiles = String.join(" + ", NAMES_NOW);
        }
    }

    private static final java.util.Map<SoundEvent, Integer> PREVIEW_TURNS = new java.util.HashMap<>();

    private static final class InOrder extends net.minecraft.client.resources.sounds.AbstractSoundInstance {
        InOrder(SoundEvent event, float volume, float pitch, int turn) {
            super(event, SoundSource.MASTER, new Turn(turn));
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }
    }

    private static final class Turn extends net.minecraft.world.level.levelgen.LegacyRandomSource {
        private final int turn;

        Turn(int turn) {
            super(0L);
            this.turn = turn;
        }

        @Override
        public int nextInt(int bound) {
            return bound <= 0 ? 0 : Math.floorMod(turn, bound);
        }
    }

    private static String fileOf(net.minecraft.client.resources.sounds.SoundInstance s, SoundEvent event) {
        try {
            String path = s.getSound().getLocation().getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Throwable t) {
            return String.valueOf(event);
        }
    }

    private static void play(ClientLevel level, SoundEvent event, Vec3 at, float volume, float pitch) {
        if (Capture.active()) return;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (previewing) {
            int turn = PREVIEW_TURNS.merge(event, 1, Integer::sum) - 1;
            InOrder s = new InOrder(event, volume * channel * cfg.soundVolumeMultiplier(), pitch * cfg.soundPitchMultiplier(), turn);
            Minecraft.getInstance().getSoundManager().play(s);
            PREVIEW_SOUNDS.add(s);
            if (PREVIEW_SOUNDS.size() > 8) PREVIEW_SOUNDS.remove(0);
            NAMES_NOW.add(fileOf(s, event));
            return;
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                event, SoundSource.PLAYERS,
                volume * channel * cfg.soundVolumeMultiplier(), pitch * cfg.soundPitchMultiplier(),
                RandomSource.create(), at.x, at.y, at.z));
    }
}
