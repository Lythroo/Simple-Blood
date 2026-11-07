package com.simpleblood.mob;

import com.simpleblood.geom.OrientedBox;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class RagdollBodies {

    private RagdollBodies() {}

    public record Body(String mod, int id, Object source, int mobId, OrientedBox[] parts,
                       double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public boolean near(double ax, double ay, double az, double bx, double by, double bz) {
            return Math.max(ax, bx) >= minX && Math.min(ax, bx) <= maxX && Math.max(ay, by) >= minY && Math.min(ay, by) <= maxY
                    && Math.max(az, bz) >= minZ && Math.min(az, bz) <= maxZ;
        }

        public boolean is(String mod, int id) {
            return this.id == id && this.mod.equals(mod);
        }
    }

    public record Hit(Body body, int part, Vec3 at, Vec3 normal, double t) {}

    public static final int BORN_GRACE_TICKS = 10;
    private static final double BORN_MARGIN = 0.1;

    private static volatile List<Body> snapshot = List.of();

    public static List<Body> all() {
        return snapshot;
    }

    public static void publish(List<Body> bodies) {
        snapshot = bodies.isEmpty() ? List.of() : List.copyOf(bodies);
    }

    public static Body body(String mod, int id, Object source, int mobId, OrientedBox[] parts) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        boolean any = false;
        for (OrientedBox p : parts) {
            if (p == null) continue;
            any = true;
            minX = Math.min(minX, p.cx - p.radius);
            minY = Math.min(minY, p.cy - p.radius);
            minZ = Math.min(minZ, p.cz - p.radius);
            maxX = Math.max(maxX, p.cx + p.radius);
            maxY = Math.max(maxY, p.cy + p.radius);
            maxZ = Math.max(maxZ, p.cz + p.radius);
        }
        return any ? new Body(mod, id, source, mobId, parts, minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    public static Body find(String mod, int id) {
        for (Body b : snapshot) if (b.is(mod, id)) return b;
        return null;
    }

    public static Body find(Object source) {
        for (Body b : snapshot) if (b.source() == source) return b;
        return null;
    }

    public static Hit firstEntry(Body body, Vec3 from, Vec3 to) {
        if (!body.near(from.x, from.y, from.z, to.x, to.y, to.z)) return null;
        int best = -1;
        double bestT = Double.POSITIVE_INFINITY;
        for (int i = 0; i < body.parts().length; i++) {
            OrientedBox p = body.parts()[i];
            if (p == null) continue;
            double t = p.entry(from.x, from.y, from.z, to.x, to.y, to.z, null);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best < 0 ? null : hit(body, best, from.x, from.y, from.z, to.x, to.y, to.z, bestT);
    }

    public static Hit dropHit(double ax, double ay, double az, double bx, double by, double bz,
                              double bornX, double bornY, double bornZ, boolean young) {
        List<Body> bodies = snapshot;
        Body bestBody = null;
        int bestPart = -1;
        double bestT = Double.POSITIVE_INFINITY;
        for (Body body : bodies) {
            if (!body.near(ax, ay, az, bx, by, bz)) continue;
            OrientedBox[] parts = body.parts();
            for (int i = 0; i < parts.length; i++) {
                OrientedBox p = parts[i];
                if (p == null || young && p.contains(bornX, bornY, bornZ, BORN_MARGIN)) continue;
                double t = p.entry(ax, ay, az, bx, by, bz, null);
                if (t >= 0 && t < bestT) {
                    bestT = t;
                    bestBody = body;
                    bestPart = i;
                }
            }
        }
        return bestBody == null ? null : hit(bestBody, bestPart, ax, ay, az, bx, by, bz, bestT);
    }

    private static Hit hit(Body body, int part, double ax, double ay, double az, double bx, double by, double bz, double t) {
        double[] n = new double[3];
        body.parts()[part].entry(ax, ay, az, bx, by, bz, n);
        return new Hit(body, part, new Vec3(ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t), new Vec3(n[0], n[1], n[2]), t);
    }

    public static Vec3 partNearForTests(Vec3 near) {
        OrientedBox best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Body b : snapshot) {
            for (OrientedBox p : b.parts()) {
                if (p == null) continue;
                double d = near.distanceToSqr(p.cx, p.cy, p.cz);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best == null ? null : new Vec3(best.cx, best.cy, best.cz);
    }

    public static String describePartNearForTests(Vec3 near) {
        String best = "no ragdoll part";
        double bestD = Double.POSITIVE_INFINITY;
        for (Body b : snapshot) {
            for (int i = 0; i < b.parts().length; i++) {
                OrientedBox p = b.parts()[i];
                if (p == null || near.distanceToSqr(p.cx, p.cy, p.cz) >= bestD) continue;
                bestD = near.distanceToSqr(p.cx, p.cy, p.cz);
                best = String.format(java.util.Locale.ROOT, "%s mob %d part %d/%d at %.2f %.2f %.2f half %.3f %.3f %.3f",
                        b.mod(), b.mobId(), i, b.parts().length, p.cx, p.cy, p.cz, p.hx, p.hy, p.hz);
            }
        }
        return best;
    }

    public static double gapToPartForTests(Vec3 p) {
        double best = Double.POSITIVE_INFINITY;
        double[] at = new double[3], normal = new double[3];
        for (Body b : snapshot) {
            for (OrientedBox part : b.parts()) {
                if (part == null) continue;
                if (part.contains(p.x, p.y, p.z)) return 0;
                part.nearestOnSurface(p.x, p.y, p.z, at, normal);
                best = Math.min(best, p.distanceTo(new Vec3(at[0], at[1], at[2])));
            }
        }
        return best;
    }
}
