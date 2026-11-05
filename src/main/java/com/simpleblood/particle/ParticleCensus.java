package com.simpleblood.particle;

import net.minecraft.client.particle.Particle;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ParticleCensus {
    private ParticleCensus() {}

    private static final long STALE_TICKS = 3;
    private static final long FIRST_TICK_GRACE = 20;
    private static final long SWEEP_INTERVAL = 20;

    private static final ConcurrentLinkedQueue<Stamp> COUNTED = new ConcurrentLinkedQueue<>();
    private static volatile long clock;

    public static final class Stamp {
        private final Particle particle;
        private volatile long lastTick;

        private Stamp(Particle particle, long lastTick) {
            this.particle = particle;
            this.lastTick = lastTick;
        }

        void ticked() {
            lastTick = clock;
        }
    }

    static Stamp join(Particle particle) {
        Stamp stamp = new Stamp(particle, clock + FIRST_TICK_GRACE);
        COUNTED.add(stamp);
        return stamp;
    }

    public static void tick() {
        long now = ++clock;
        if (now % SWEEP_INTERVAL != 0 || com.simpleblood.compat.AsyncParticlesTicks.evictingSpawns()) return;
        for (Iterator<Stamp> it = COUNTED.iterator(); it.hasNext(); ) {
            Stamp stamp = it.next();
            if (!stamp.particle.isAlive()) {
                it.remove();
            } else if (now - stamp.lastTick > STALE_TICKS) {
                it.remove();
                stamp.particle.remove();
                com.simpleblood.Tally.count("particle.evicted");
            }
        }
    }

    static void clear() {
        COUNTED.clear();
    }
}
