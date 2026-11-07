package com.simpleblood.mob;

import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WoundAnchors {

    private WoundAnchors() {}

    private static final class Spot {
        final Vec3 struck;
        Object face;
        double a, b;
        Vec3 now;
        Vec3 normal;
        long seen;

        Spot(Vec3 struck) {
            this.struck = struck;
        }

        void follow(List<ModelFacesOfMob.Face> faces) {
            if (face == null) find(faces);
            for (ModelFacesOfMob.Face f : faces) {
                if (!f.key.equals(face)) continue;
                now = point(f, a, b).add(f.wnx * SKIN_GAP, f.wny * SKIN_GAP, f.wnz * SKIN_GAP);
                normal = new Vec3(f.wnx, f.wny, f.wnz);
                seen = System.currentTimeMillis();
                return;
            }
        }

        boolean fresh() {
            return now != null && System.currentTimeMillis() - seen <= FRESH_MS;
        }

        private void find(List<ModelFacesOfMob.Face> faces) {
            double best = Double.MAX_VALUE;
            for (ModelFacesOfMob.Face f : faces) {
                double[] at = f.locate(struck.x, struck.y, struck.z);
                if (at == null) continue;
                double fa = Math.max(0, Math.min(1, at[0])), fb = Math.max(0, Math.min(1, at[1]));
                double d = point(f, fa, fb).distanceToSqr(struck);
                if (d < best) {
                    best = d;
                    face = f.key;
                    a = fa;
                    b = fb;
                }
            }
        }
    }

    private static final class Anchor {
        final Spot spot;
        final long marked;

        Anchor(Vec3 struck, long marked) {
            this.spot = new Spot(struck);
            this.marked = marked;
        }
    }

    public static final class Pin {
        private final Spot spot;
        private final long until;

        private Pin(Vec3 struck, long until) {
            this.spot = new Spot(struck);
            this.until = until;
        }

        public Vec3 at() {
            return spot.fresh() ? spot.now : null;
        }

        public Vec3 facing() {
            return spot.fresh() ? spot.normal : null;
        }
    }

    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
    private static final Map<Integer, List<Pin>> PINS = new HashMap<>();
    private static final int MAX = 128;
    private static final int MAX_PINS = 64;
    private static int pinCount;
    private static final long FRESH_MS = 250;
    private static final double SKIN_GAP = 0.03;

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

    public static Pin pin(int entityId, Vec3 struck, long lastsMs) {
        if (struck == null) return null;
        long now = System.currentTimeMillis();
        if (pinCount >= MAX_PINS) {
            PINS.values().forEach(pins -> pins.removeIf(p -> now > p.until));
            PINS.values().removeIf(List::isEmpty);
            pinCount = 0;
            PINS.values().forEach(pins -> pinCount += pins.size());
            if (pinCount >= MAX_PINS) return null;
        }
        Pin pin = new Pin(struck, now + lastsMs);
        PINS.computeIfAbsent(entityId, k -> new ArrayList<>(2)).add(pin);
        pinCount++;
        return pin;
    }

    public static void unpin(int entityId, Pin pin) {
        List<Pin> pins = PINS.get(entityId);
        if (pin == null || pins == null || !pins.remove(pin)) return;
        pinCount--;
        if (pins.isEmpty()) PINS.remove(entityId);
    }

    public static boolean wants(int entityId) {
        boolean pinned = PINS.containsKey(entityId);
        Anchor an = ANCHORS.get(entityId);
        if (an == null) return pinned;
        if (expired(an, System.currentTimeMillis())) {
            ANCHORS.remove(entityId);
            return pinned;
        }
        return true;
    }

    private static boolean expired(Anchor an, long now) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        long keep = cfg == null ? 15_000 : cfg.lowHealth.dripDurationSeconds * 1000L + 2_000;
        return now - an.marked > keep;
    }

    static void posed(int entityId, List<ModelFacesOfMob.Face> faces) {
        if (faces.isEmpty()) return;
        Anchor an = ANCHORS.get(entityId);
        if (an != null) an.spot.follow(faces);
        List<Pin> pins = PINS.get(entityId);
        if (pins == null) return;
        long now = System.currentTimeMillis();
        for (int i = pins.size() - 1; i >= 0; i--) {
            Pin p = pins.get(i);
            if (now > p.until) {
                pins.remove(i);
                pinCount--;
                continue;
            }
            p.spot.follow(faces);
        }
        if (pins.isEmpty()) PINS.remove(entityId);
    }

    private static Vec3 point(ModelFacesOfMob.Face f, double a, double b) {
        return new Vec3(f.wx(0) + (f.wx(1) - f.wx(0)) * a + (f.wx(3) - f.wx(0)) * b,
                f.wy(0) + (f.wy(1) - f.wy(0)) * a + (f.wy(3) - f.wy(0)) * b,
                f.wz(0) + (f.wz(1) - f.wz(0)) * a + (f.wz(3) - f.wz(0)) * b);
    }

    public static Vec3 at(int entityId) {
        Anchor an = ANCHORS.get(entityId);
        if (an == null || !an.spot.fresh()) return null;
        com.simpleblood.Tally.count("anchor.used");
        return an.spot.now;
    }

    public static void clear() {
        ANCHORS.clear();
        PINS.clear();
        pinCount = 0;
    }
}
