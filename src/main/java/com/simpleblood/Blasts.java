package com.simpleblood;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

public final class Blasts {

    private Blasts() {}

    public record Blast(Vec3 centre, float radius, long tick) {}

    private static final int REMEMBER_TICKS = 10;
    private static final int MAX_KEPT = 32;
    private static final double REACH = 2.0, REACH_SLACK = 1.0;
    public static final float TYPICAL_RADIUS = 4.0f;
    private static final int GUST_TICKS = 8;
    private static final double GUST = 0.3;

    private static volatile Blast[] recent = new Blast[0];
    private static volatile WeakReference<ClientLevel> levelRef = new WeakReference<>(null);
    private static volatile long blowsUntil = Long.MIN_VALUE;

    public static void heard(ClientLevel level, Vec3 centre, float radius) {
        if (levelRef.get() != level) {
            levelRef = new WeakReference<>(level);
            recent = new Blast[0];
        }
        long now = level.getGameTime();
        Blast[] old = recent;
        Blast[] kept = new Blast[Math.min(old.length, MAX_KEPT - 1) + 1];
        int n = 0;
        for (int i = Math.max(0, old.length - (MAX_KEPT - 1)); i < old.length; i++) {
            if (Math.abs(now - old[i].tick()) <= REMEMBER_TICKS) kept[n++] = old[i];
        }
        kept[n++] = new Blast(centre, Math.max(0.5f, radius), now);
        recent = n == kept.length ? kept : java.util.Arrays.copyOf(kept, n);
        blowsUntil = now + GUST_TICKS;
        Tally.count("blast.heard");
    }

    public static Blast nearest(Level level, Vec3 at) {
        if (!(level instanceof ClientLevel client) || levelRef.get() != client) return null;
        long now = client.getGameTime();
        Blast best = null;
        double bestD = Double.MAX_VALUE;
        for (Blast b : recent) {
            if (Math.abs(now - b.tick()) > REMEMBER_TICKS) continue;
            double d = b.centre().distanceTo(at);
            if (d > b.radius() * REACH + REACH_SLACK || d >= bestD) continue;
            best = b;
            bestD = d;
        }
        return best;
    }

    public static float push(double distance, double radius) {
        if (radius <= 0) return 0f;
        return (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / (radius * REACH)));
    }

    public interface Gusted {
        void blow(double dx, double dy, double dz);
    }

    public static void gust(Level level, double x, double y, double z, Gusted drop) {
        long now = level.getGameTime();
        if (now > blowsUntil || levelRef.get() != level) return;
        for (Blast b : recent) {
            long age = now - b.tick();
            if (age < 0 || age >= GUST_TICKS) continue;
            double dx = x - b.centre().x, dy = y - b.centre().y, dz = z - b.centre().z;
            double d2 = dx * dx + dy * dy + dz * dz, reach = b.radius() * REACH;
            if (d2 > reach * reach || d2 < 1.0e-6) continue;
            double d = Math.sqrt(d2);
            double f = GUST * push(d, b.radius()) * (1.0 - (double) age / GUST_TICKS) / d;
            drop.blow(dx * f, dy * f, dz * f);
            Tally.count("blast.gust");
        }
    }
}
