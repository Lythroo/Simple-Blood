package com.simpleblood;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class Blasts {

    private Blasts() {}

    public record Blast(Vec3 centre, float radius, long tick) {}

    private static final int REMEMBER_TICKS = 10;
    private static final int MAX_KEPT = 32;
    private static final double REACH = 2.0, REACH_SLACK = 1.0;
    public static final float TYPICAL_RADIUS = 4.0f;

    private static final List<Blast> RECENT = new ArrayList<>();
    private static WeakReference<ClientLevel> levelRef = new WeakReference<>(null);

    public static void heard(ClientLevel level, Vec3 centre, float radius) {
        sync(level);
        long now = level.getGameTime();
        RECENT.removeIf(b -> Math.abs(now - b.tick()) > REMEMBER_TICKS);
        if (RECENT.size() >= MAX_KEPT) RECENT.remove(0);
        RECENT.add(new Blast(centre, Math.max(0.5f, radius), now));
        Tally.count("blast.heard");
    }

    public static Blast nearest(Level level, Vec3 at) {
        if (!(level instanceof ClientLevel client) || levelRef.get() != client) return null;
        long now = client.getGameTime();
        Blast best = null;
        double bestD = Double.MAX_VALUE;
        for (Blast b : RECENT) {
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

    private static void sync(ClientLevel level) {
        if (levelRef.get() == level) return;
        levelRef = new WeakReference<>(level);
        RECENT.clear();
    }
}
