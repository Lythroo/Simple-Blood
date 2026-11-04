package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.world.phys.Vec3;

public final class Capture {

    private Capture() {}

    public enum Part { DRIPS, SPLASH, DIRECTIONAL, EXIT, FLAVOUR, OTHER }

    public interface Sink {
        void particle(net.minecraft.core.particles.ParticleOptions type, double x, double y, double z,
                      double vx, double vy, double vz, int colour, boolean exact);

        BloodSurfaces.Hit trace(Vec3 from, Vec3 to);

        void paint(BloodSurfaces.Hit hit, int pixels);
    }

    private static Sink sink;
    private static SimpleBloodConfig config;
    private static Thread owner;

    public static SimpleBloodConfig config() {
        return config != null && sink != null && Thread.currentThread() == owner ? config : null;
    }

    public static void run(Sink to, SimpleBloodConfig with, Runnable code) {
        SimpleBloodConfig was = config;
        Thread wasOwner = owner;
        config = with;
        owner = Thread.currentThread();
        try {
            run(to, code);
        } finally {
            config = was;
            owner = wasOwner;
        }
    }
    public static Part part = Part.OTHER;
    public static BloodKind kind;

    public static boolean active() {
        return sink != null;
    }

    public static void run(Sink to, Runnable code) {
        Sink was = sink;
        Part wasPart = part;
        sink = to;
        try {
            code.run();
        } finally {
            sink = was;
            part = wasPart;
            BloodParticle.resetSpawnState();
        }
    }

    public static boolean take(net.minecraft.core.particles.ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        if (sink == null) return false;
        BloodColor.Color c = BloodParticle.currentBloodColor();
        int rgb = c == null ? 0x8A0707 : ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        sink.particle(type, x, y, z, vx, vy, vz, rgb, BloodParticle.ballisticSpawn());
        return true;
    }

    public static BloodSurfaces.Hit trace(Vec3 from, Vec3 to) {
        return sink.trace(from, to);
    }

    public static void paint(BloodSurfaces.Hit hit, int pixels) {
        sink.paint(hit, pixels);
    }
}
