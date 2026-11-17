package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class Coughing {
    private Coughing() {}

    private static final double MOUTH_DOWN = 0.15, MOUTH_AHEAD = 0.3;
    private static final double COUGH_SPEED = 0.16, COUGH_SPEED_RANDOM = 0.08, STRAY = 0.035;
    private static final int PRUNE_EVERY = 200;

    private static final RandomSource RNG = RandomSource.create();

    private static final class Fit {
        long nextAt = -1;
        int coughsLeft;
        long seen;
    }

    private static final Int2ObjectOpenHashMap<Fit> FITS = new Int2ObjectOpenHashMap<>();
    private static int levelId;
    private static long tick;

    public static void tick(Minecraft client) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        ClientLevel level = client.level;
        if (cfg == null || level == null) return;
        if (!cfg.cough.enabled || !cfg.globalEnabled() || !cfg.playerBleed()) {
            if (!FITS.isEmpty()) FITS.clear();
            return;
        }
        Tally.count("cough.tick");
        int id = System.identityHashCode(level);
        if (id != levelId) {
            FITS.clear();
            levelId = id;
        }
        tick++;
        for (AbstractClientPlayer player : level.players()) {
            if (player != client.player && !cfg.cough.otherPlayers) continue;
            step(level, player, cfg.cough);
        }
        if (tick % PRUNE_EVERY == 0) prune();
    }

    private static void step(ClientLevel level, AbstractClientPlayer player, SimpleBloodConfig.CoughSettings s) {
        float severity = CoughTiming.severity(player.getHealth(), player.getMaxHealth(), s.threshold / 100f);
        Fit fit = FITS.get(player.getId());
        if (severity < 0f) Tally.count("cough.skip.healthy");
        else if (!canCough(player)) Tally.count("cough.skip.cannot");
        if (severity < 0f || !canCough(player)) {
            if (fit != null) fit.nextAt = -1;
            return;
        }
        if (fit == null) {
            fit = new Fit();
            FITS.put(player.getId(), fit);
        }
        fit.seen = tick;
        float frequency = s.frequency / 100f;
        if (fit.nextAt < 0) {
            fit.nextAt = tick + CoughTiming.gapTicks(severity, frequency, RNG.nextFloat()) / 3;
            return;
        }
        if (tick < fit.nextAt) return;
        if (fit.coughsLeft <= 0) {
            fit.coughsLeft = Math.max(1, s.fitLength);
            Tally.count("cough.fit");
        }
        cough(level, player, severity, s.amount / 100f);
        fit.coughsLeft--;
        fit.nextAt = tick + (fit.coughsLeft > 0 ? CoughTiming.COUGH_GAP_TICKS
                : CoughTiming.gapTicks(severity, frequency, RNG.nextFloat()));
    }

    private static boolean canCough(AbstractClientPlayer player) {
        return player.isAlive() && !player.isDeadOrDying() && !player.isCreative() && !player.isSpectator()
                && !player.isInvisible() && !SimpleBlood.isEnvironmentalNoBleed(player)
                && SimpleBlood.bloodKindOf(player) != BloodKind.POWDER;
    }

    private static void cough(ClientLevel level, AbstractClientPlayer player, float severity, float amount) {
        int drops = CoughTiming.drops(severity, amount, RNG.nextFloat());
        if (drops <= 0) return;
        prepare(player);
        BloodParticle.carryMotionOf(player);
        BloodParticle.setBallistic(true, 1.0f);
        try {
            Vec3 look = player.getViewVector(1.0f);
            Vec3 eye = player.getEyePosition();
            double mx = eye.x + look.x * MOUTH_AHEAD, my = eye.y - MOUTH_DOWN + look.y * MOUTH_AHEAD, mz = eye.z + look.z * MOUTH_AHEAD;
            for (int k = 0; k < drops; k++) {
                double speed = COUGH_SPEED + RNG.nextDouble() * COUGH_SPEED_RANDOM;
                ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_SPLASH, mx, my, mz,
                        look.x * speed + RNG.nextGaussian() * STRAY,
                        look.y * speed - 0.02 + RNG.nextGaussian() * STRAY,
                        look.z * speed + RNG.nextGaussian() * STRAY);
            }
            BloodSounds.cough(level, new Vec3(mx, my, mz));
            Tally.count("cough.cough");
            Tally.add("cough.drop", drops);
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
            BloodParticle.clearCarry();
        }
    }

    private static void prepare(AbstractClientPlayer player) {
        BloodParticle.setCurrentBloodColor(BloodColor.getBloodColor(player));
        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(player));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(player));
        BloodParticle.setStreaks(SimpleBlood.hasFleshBlood(player));
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(player));
        BloodParticle.setPaintsSurfaces(SimpleBlood.shouldEntityTransformToStains(player));
        BloodParticle.setShouldTransformToFog(SimpleBlood.createsFogUnderwater(player));
        BloodParticle.setShouldDespawnInWater(SimpleBlood.particlesDespawnInWater(player));
    }

    private static void prune() {
        ObjectIterator<Fit> it = FITS.values().iterator();
        while (it.hasNext()) {
            if (tick - it.next().seen > PRUNE_EVERY) it.remove();
        }
    }
}
