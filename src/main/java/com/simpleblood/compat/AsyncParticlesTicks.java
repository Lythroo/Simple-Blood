package com.simpleblood.compat;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncParticlesTicks {

    private AsyncParticlesTicks() {}

    private static final String[] CLASSES = {
            "fabric.fun.qu_an.minecraft.asyncparticles.client.core.particle.tick.AsyncTickBehavior",
            "neoforge.fun.qu_an.minecraft.asyncparticles.client.core.particle.tick.AsyncTickBehavior",
            "fun.qu_an.minecraft.asyncparticles.client.core.particle.tick.AsyncTickBehavior",
    };
    private static final int MAX_HELD = 2048;

    private static volatile boolean looked;
    private static Object behavior;
    private static Method tailTick;
    private static final ConcurrentLinkedQueue<Runnable> HELD = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger held = new AtomicInteger();

    private static synchronized void lookUp() {
        if (looked) return;
        ClassLoader cl = AsyncParticlesTicks.class.getClassLoader();
        for (String name : CLASSES) {
            try {
                Class<?> c = Class.forName(name, false, cl);
                behavior = c.getMethod("getInstance").invoke(null);
                tailTick = c.getMethod("isTailTick");
                tailTick.invoke(behavior);
                com.simpleblood.SimpleBlood.LOGGER.info("AsyncParticles found: blood spawned on a catch-up tick is kept for the frame's last tick");
                break;
            } catch (ClassNotFoundException e) {
            } catch (Throwable t) {
                behavior = null;
                com.simpleblood.SimpleBlood.LOGGER.warn("AsyncParticles found, but not a version Simple Blood knows; blood spawned on catch-up ticks may be lost ({})", t.toString());
                break;
            }
        }
        looked = true;
    }

    public static boolean evictingSpawns() {
        if (!looked) lookUp();
        if (behavior == null) return false;
        try {
            return !(Boolean) tailTick.invoke(behavior);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hold(Runnable again) {
        if (held.incrementAndGet() > MAX_HELD) {
            held.decrementAndGet();
            return false;
        }
        HELD.add(again);
        com.simpleblood.Tally.count("async.held");
        return true;
    }

    public static void tick(Minecraft mc) {
        if (HELD.isEmpty()) return;
        if (mc.level == null) {
            HELD.clear();
            held.set(0);
            return;
        }
        if (evictingSpawns()) return;
        Runnable r;
        while ((r = HELD.poll()) != null) {
            held.decrementAndGet();
            r.run();
            com.simpleblood.Tally.count("async.readded");
        }
    }
}
