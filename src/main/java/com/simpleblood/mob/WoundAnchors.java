package com.simpleblood.mob;

import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WoundAnchors {

    private WoundAnchors() {}

    private static final class Anchor {
        final Vec3 struck;
        final long marked;
        Object face;
        double a, b;
        Vec3 now;
        long seen;

        Anchor(Vec3 struck, long marked) {
            this.struck = struck;
            this.marked = marked;
        }
    }

    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
    private static final int MAX = 128;
    private static final long FRESH_MS = 250;

    public static void mark(int entityId, Vec3 wound) {
        if (wound == null) {
            ANCHORS.remove(entityId);
            return;
        }
        if (!ANCHORS.containsKey(entityId) && ANCHORS.size() >= MAX) {
            long now = System.currentTimeMillis();
            ANCHORS.values().removeIf(an -> expired(an, now));
            if (ANCHORS.size() >= MAX) return;
        }
        ANCHORS.put(entityId, new Anchor(wound, System.currentTimeMillis()));
    }

    public static boolean wants(int entityId) {
        Anchor an = ANCHORS.get(entityId);
        if (an == null) return false;
        if (expired(an, System.currentTimeMillis())) {
            ANCHORS.remove(entityId);
            return false;
        }
        return true;
    }

    private static boolean expired(Anchor an, long now) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        long keep = cfg == null ? 15_000 : cfg.lowHealth.dripDurationSeconds * 1000L + 2_000;
        return now - an.marked > keep;
    }

    static void posed(int entityId, List<ModelFacesOfMob.Face> faces) {
        Anchor an = ANCHORS.get(entityId);
        if (an == null || faces.isEmpty()) return;
        if (an.face == null) find(an, faces);
        for (ModelFacesOfMob.Face f : faces) {
            if (!f.key.equals(an.face)) continue;
            an.now = point(f, an.a, an.b).add(f.wnx * 0.03, f.wny * 0.03, f.wnz * 0.03);
            an.seen = System.currentTimeMillis();
            return;
        }
    }

    private static void find(Anchor an, List<ModelFacesOfMob.Face> faces) {
        double best = Double.MAX_VALUE;
        for (ModelFacesOfMob.Face f : faces) {
            double[] at = f.locate(an.struck.x, an.struck.y, an.struck.z);
            if (at == null) continue;
            double a = Math.max(0, Math.min(1, at[0])), b = Math.max(0, Math.min(1, at[1]));
            double d = point(f, a, b).distanceToSqr(an.struck);
            if (d < best) {
                best = d;
                an.face = f.key;
                an.a = a;
                an.b = b;
            }
        }
    }

    private static Vec3 point(ModelFacesOfMob.Face f, double a, double b) {
        return new Vec3(f.wx(0) + (f.wx(1) - f.wx(0)) * a + (f.wx(3) - f.wx(0)) * b,
                f.wy(0) + (f.wy(1) - f.wy(0)) * a + (f.wy(3) - f.wy(0)) * b,
                f.wz(0) + (f.wz(1) - f.wz(0)) * a + (f.wz(3) - f.wz(0)) * b);
    }

    public static Vec3 at(int entityId) {
        Anchor an = ANCHORS.get(entityId);
        if (an == null || an.now == null || System.currentTimeMillis() - an.seen > FRESH_MS) return null;
        com.simpleblood.Tally.count("anchor.used");
        return an.now;
    }

    public static void clear() {
        ANCHORS.clear();
    }
}
