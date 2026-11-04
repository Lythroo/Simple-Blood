package com.simpleblood.mob;

import com.simpleblood.HitContext;
import com.simpleblood.surface.BloodCanvasAtlas;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class MobCoat {
    final java.util.Map<Long, Object> shapes = new java.util.HashMap<>();
    java.util.List<ModelFacesOfMob.Face> lastFaces;
    int onRagdoll = -1000;
    net.minecraft.world.phys.Vec3 lastPos;

    void noteShapes(java.util.List<ModelFacesOfMob.Face> faces) {
        for (ModelFacesOfMob.Face f : faces) if (f.shape != 0) shapes.putIfAbsent(f.shape, f.key);
    }

    record Splat(Vec3 at, Vec3 dir, HitContext.Weapon weapon, float size, int units, int made, int colour, boolean drop) {
        Splat(Vec3 at, Vec3 dir, HitContext.Weapon weapon, float size, int units, int made) {
            this(at, dir, weapon, size, units, made, 0, false);
        }
    }

    record Drip(Vec3 at, int colour) {}

    final int entityId;
    final int colour;
    final Map<Object, MobFace> faces = new java.util.HashMap<>();
    final List<Splat> pending = new ArrayList<>();
    final List<Drip> drips = new ArrayList<>();
    int lastSim = -1;
    int lastSeen;
    int wash;
    int rinsed;
    Object woundFace;
    int woundX, woundY, woundW, woundH;
    int seep;
    int strokes;

    MobCoat(int entityId, int colour) {
        this.entityId = entityId;
        this.colour = colour;
    }

    boolean isEmpty() {
        return faces.isEmpty() && pending.isEmpty();
    }

    MobFace face(ModelFacesOfMob.Face f) {
        return faces.computeIfAbsent(f.key, k -> {
            MobFace canvas = new MobFace(f.pw, f.ph);
            if (f.alpha != null) {
                boolean[] drawn = new boolean[f.pw * f.ph];
                boolean any = false;
                for (int y = 0; y < f.ph; y++) {
                    for (int x = 0; x < f.pw; x++) {
                        double[] ab = f.edgeWeights((x + 0.5) / f.pw, (y + 0.5) / f.ph);
                        drawn[y * f.pw + x] = f.drawnAt(ab[0], ab[1]);
                        any |= !drawn[y * f.pw + x];
                    }
                }
                if (any) canvas.drawn = drawn;
            }
            return canvas;
        });
    }

    void paint(Splat splat, List<ModelFacesOfMob.Face> faces, int now, int lifetime, RandomSource rng) {
        Vec3 dir = splat.dir();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float size = splat.size();
        int units = splat.units();
        if (splat.drop()) {
            if (size > 1f) blotch(splat, Vec3.ZERO, faces, units, false, now, lifetime, rng);
            else stain(splat, Vec3.ZERO, faces, units, false, now, lifetime, rng);
            return;
        }
        if (splat.weapon() != HitContext.Weapon.ARROW) stain(splat, Vec3.ZERO, faces, units + 3, true, now, lifetime, rng);
        switch (splat.weapon()) {
            case BLADE -> {
                double tilt = (rng.nextDouble() - 0.5) * Math.toRadians(70);
                Vec3 along = side.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
                double half = 0.22 * size;
                for (double k = -half; k <= half; k += 0.025) {
                    boolean middle = Math.abs(k) < 0.04;
                    Vec3 off = along.scale(k).add(up.scale((rng.nextDouble() - 0.5) * 0.012));
                    stain(splat, off, faces, middle ? units + 2 : 2, false, now, lifetime, rng);
                }
            }
            case AXE -> {
                double tilt = (rng.nextDouble() - 0.5) * Math.toRadians(40);
                Vec3 along = up.scale(Math.cos(tilt)).add(side.scale(Math.sin(tilt)));
                Vec3 across = along.cross(dir).normalize();
                double half = 0.11 * size;
                for (double k = -half; k <= half; k += 0.025) {
                    boolean middle = Math.abs(k) < 0.05;
                    for (int row = -1; row <= 1; row += 2) {
                        Vec3 off = along.scale(k).add(across.scale(row * 0.018));
                        stain(splat, off, faces, middle ? units + 3 : units, false, now, lifetime, rng);
                    }
                }
            }
            case MACE -> {
                double radius = 0.12 * size;
                for (double a = -radius; a <= radius; a += 0.028) {
                    for (double b = -radius; b <= radius; b += 0.028) {
                        double r = Math.sqrt(a * a + b * b);
                        double rim = radius * (0.8 + rng.nextDouble() * 0.3);
                        if (r > rim) continue;
                        int u = r < radius * 0.3 ? units + 3 : r < radius * 0.7 ? 2 : (rng.nextFloat() < 0.6f ? 1 : 0);
                        if (u > 0) stain(splat, side.scale(a).add(up.scale(b)), faces, u, false, now, lifetime, rng);
                    }
                }
                for (int i = 0; i < 6; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2, r = radius * (1.2 + rng.nextDouble() * 0.6);
                    stain(splat, side.scale(Math.cos(ang) * r).add(up.scale(Math.sin(ang) * r)), faces, 1, false, now, lifetime, rng);
                }
            }
            case SPEAR -> {
                double tilt = (rng.nextDouble() - 0.5) * Math.toRadians(30);
                Vec3 along = up.scale(Math.cos(tilt)).add(side.scale(Math.sin(tilt)));
                Vec3 across = along.cross(dir).normalize();
                double half = 0.07 * size;
                for (double k = -half; k <= half; k += 0.02) {
                    double width = 0.03 * size * (1 - Math.abs(k) / (half + 1e-6));
                    for (double w = -width; w <= width + 1e-6; w += 0.02) {
                        boolean middle = Math.abs(k) < 0.03 && Math.abs(w) < 0.02;
                        stain(splat, along.scale(k).add(across.scale(w)), faces, middle ? units + 3 : units, false, now, lifetime, rng);
                    }
                }
                for (double k = 0.04; k <= 0.04 + 0.12 * size; k += 0.025) {
                    stain(splat, up.scale(-k).add(side.scale((rng.nextDouble() - 0.5) * 0.015)), faces, 2, false, now, lifetime, rng);
                }
            }
            case ARROW -> {
                stain(splat, Vec3.ZERO, faces, units + 3, true, now, lifetime, rng);
                for (int i = 0; i < 3; i++) {
                    stain(splat, side.scale((rng.nextDouble() - 0.5) * 0.05).add(up.scale((rng.nextDouble() - 0.5) * 0.05)), faces, 1, false, now, lifetime, rng);
                }
            }
            default -> {
                int blots = Math.max(1, Math.round(3 * size));
                for (int i = 0; i < blots; i++) {
                    Vec3 off = i == 0 ? Vec3.ZERO : side.scale((rng.nextDouble() - 0.5) * 0.3 * size).add(up.scale((rng.nextDouble() - 0.5) * 0.3 * size));
                    blotch(splat, off, faces, units, false, now, lifetime, rng);
                }
            }
        }
    }

    private int tint(Splat splat) {
        return splat.colour() != 0 ? splat.colour() : colour;
    }

    private void stain(Splat splat, Vec3 offset, List<ModelFacesOfMob.Face> faces, int units, boolean wound,
                       int now, int lifetime, RandomSource rng) {
        int[] px = new int[2];
        ModelFacesOfMob.Face f = strike(splat.at().add(offset), splat.dir(), faces, px);
        if (f == null && wound) f = nearest(splat.at().add(offset), splat.dir(), faces, px);
        if (f == null) return;
        MobFace canvas = face(f);
        if (canvas.add(px[0], px[1], units, tint(splat), now, lifetime, rng)) strokes++;
        if (wound) markWound(f, canvas, px);
    }

    private void blotch(Splat splat, Vec3 offset, List<ModelFacesOfMob.Face> faces, int units, boolean wound,
                        int now, int lifetime, RandomSource rng) {
        int[] px = new int[2];
        ModelFacesOfMob.Face f = strike(splat.at().add(offset), splat.dir(), faces, px);
        if (f == null) return;
        MobFace canvas = face(f);
        int cx = px[0], cy = px[1];
        int colour = tint(splat);
        if (canvas.add(cx, cy, units, colour, now, lifetime, rng)) strokes++;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx | dy) == 0 || !canvas.inside(cx + dx, cy + dy)) continue;
                if (rng.nextFloat() < (dx == 0 || dy == 0 ? 0.7f : 0.35f)) {
                    canvas.add(cx + dx, cy + dy, Math.max(1, units - 3), colour, now, lifetime, rng);
                }
            }
        }
        if (wound) markWound(f, canvas, px);
    }

    private void markWound(ModelFacesOfMob.Face f, MobFace canvas, int[] px) {
        woundFace = f.key;
        woundX = px[0];
        woundY = px[1];
        woundW = canvas.pw;
        woundH = canvas.ph;
    }

    private static ModelFacesOfMob.Face strike(Vec3 point, Vec3 dir, List<ModelFacesOfMob.Face> faces, int[] px) {
        Vec3 from = point.subtract(dir.scale(1.2));
        ModelFacesOfMob.Face best = null;
        double bestT = Double.MAX_VALUE, bestA = 0, bestB = 0;
        for (ModelFacesOfMob.Face f : faces) {
            double facing = f.wnx * dir.x + f.wny * dir.y + f.wnz * dir.z;
            if (facing > -0.05) continue;
            double d = (f.wx(0) - from.x) * f.wnx + (f.wy(0) - from.y) * f.wny + (f.wz(0) - from.z) * f.wnz;
            double tt = d / facing;
            if (tt <= 0 || tt >= bestT || tt > 4.0) continue;
            double[] loc = f.locate(from.x + dir.x * tt, from.y + dir.y * tt, from.z + dir.z * tt);
            if (loc == null || !f.holds(loc[0], loc[1])) continue;
            if (!f.drawnAt(loc[0], loc[1])) {
                com.simpleblood.Tally.count("coat.hole");
                continue;
            }
            best = f;
            bestT = tt;
            bestA = loc[0];
            bestB = loc[1];
        }
        if (best == null) return null;
        px[0] = (int) Math.max(0, Math.min(best.pw - 1, best.sAt(bestA, bestB) * best.pw));
        px[1] = (int) Math.max(0, Math.min(best.ph - 1, best.tAt(bestA, bestB) * best.ph));
        return best;
    }

    private static ModelFacesOfMob.Face nearest(Vec3 point, Vec3 dir, List<ModelFacesOfMob.Face> faces, int[] px) {
        ModelFacesOfMob.Face best = null;
        double bestD = Double.MAX_VALUE, bestA = 0, bestB = 0;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (ModelFacesOfMob.Face f : faces) {
                if (pass == 0 && f.wnx * dir.x + f.wny * dir.y + f.wnz * dir.z > 0.2) continue;
                double[] loc = f.locate(point.x, point.y, point.z);
                if (loc == null) continue;
                double a = Math.max(0, Math.min(1, loc[0])), b = Math.max(0, Math.min(1, loc[1]));
                if (pass == 0 && !f.drawnAt(a, b)) continue;
                double x = f.wx(0) + (f.wx(1) - f.wx(0)) * a + (f.wx(3) - f.wx(0)) * b;
                double y = f.wy(0) + (f.wy(1) - f.wy(0)) * a + (f.wy(3) - f.wy(0)) * b;
                double z = f.wz(0) + (f.wz(1) - f.wz(0)) * a + (f.wz(3) - f.wz(0)) * b;
                double d = point.distanceToSqr(x, y, z);
                if (d < bestD) {
                    bestD = d;
                    best = f;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        if (best == null) return null;
        com.simpleblood.Tally.count("coat.nearest");
        px[0] = (int) Math.max(0, Math.min(best.pw - 1, best.sAt(bestA, bestB) * best.pw));
        px[1] = (int) Math.max(0, Math.min(best.ph - 1, best.tAt(bestA, bestB) * best.ph));
        return best;
    }

    void bleed(int now, int lifetime, RandomSource rng) {
        if (seep <= 0 || woundFace == null) return;
        MobFace canvas = faces.computeIfAbsent(woundFace, k -> new MobFace(woundW, woundH));
        canvas.add(Math.min(woundX, canvas.pw - 1), Math.min(woundY, canvas.ph - 1), seep, colour, now, lifetime, rng);
        seep = 0;
    }

    private static final int RUN_DEPTH = 3;

    void flow(List<ModelFacesOfMob.Face> posed, int now, int lifetime, RandomSource rng, boolean runs, boolean drips) {
        if (!runs && !drips) return;
        for (ModelFacesOfMob.Face f : posed) {
            MobFace canvas = faces.get(f.key);
            if (canvas == null || canvas.count == 0) continue;
            if (f.wny < -0.7) {
                if (!drips) continue;
                for (int y = 0; y < canvas.ph; y++) {
                    for (int x = 0; x < canvas.pw; x++) {
                        if (canvas.depthAt(x, y) >= 2 && rng.nextFloat() < 0.08f) {
                            int c = canvas.take(x, y);
                            letGo(f, f.at((x + 0.5) / canvas.pw, (y + 0.5) / canvas.ph), c, posed, now, lifetime, rng);
                        }
                    }
                }
                continue;
            }
            if (!runs) continue;
            double gx = f.wnx * f.wny, gy = -1 + f.wny * f.wny, gz = f.wnz * f.wny;
            double glen = Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (glen < 0.35) continue;
            double[] at0 = f.locate(f.wx(0) + gx, f.wy(0) + gy, f.wz(0) + gz);
            if (at0 == null) continue;
            double ds = f.sAt(at0[0], at0[1]) - f.s[0], dt = f.tAt(at0[0], at0[1]) - f.t[0];
            double px = ds * canvas.pw, py = dt * canvas.ph;
            int stepX = Math.abs(px) > Math.abs(py) * 0.5 ? (int) Math.signum(px) : 0;
            int stepY = Math.abs(py) > Math.abs(px) * 0.5 ? (int) Math.signum(py) : 0;
            if (stepX == 0 && stepY == 0) continue;
            int ys = stepY > 0 ? canvas.ph - 1 : 0, ye = stepY > 0 ? -1 : canvas.ph, yd = stepY > 0 ? -1 : 1;
            int xs = stepX > 0 ? canvas.pw - 1 : 0, xe = stepX > 0 ? -1 : canvas.pw, xd = stepX > 0 ? -1 : 1;
            for (int y = ys; y != ye; y += yd) {
                for (int x = xs; x != xe; x += xd) {
                    if (canvas.depthAt(x, y) < RUN_DEPTH || rng.nextFloat() > 0.6f) continue;
                    int nx = x + stepX, ny = y + stepY;
                    if (rng.nextFloat() < 0.15f) {
                        if (stepY != 0) nx += rng.nextBoolean() ? 1 : -1;
                        else ny += rng.nextBoolean() ? 1 : -1;
                    }
                    if (canvas.inside(nx, ny)) {
                        int c = canvas.take(x, y);
                        canvas.add(nx, ny, 1, c, now, lifetime, rng);
                    } else {
                        overEdge(f, canvas, x, y, nx, ny, posed, now, lifetime, rng, drips);
                    }
                }
            }
        }
    }

    private void overEdge(ModelFacesOfMob.Face f, MobFace canvas, int x, int y, int nx, int ny,
                          List<ModelFacesOfMob.Face> posed, int now, int lifetime, RandomSource rng, boolean drips) {
        double fx = Math.max(0, Math.min(1, (nx + 0.5) / canvas.pw)), fy = Math.max(0, Math.min(1, (ny + 0.5) / canvas.ph));
        Vec3 edge = f.at(fx, fy);
        for (ModelFacesOfMob.Face o : posed) {
            if (o == f || o.cube != f.cube) continue;
            double[] loc = o.locate(edge.x, edge.y, edge.z);
            if (loc == null || Math.abs(loc[2]) > 0.02 || loc[0] < -0.05 || loc[0] > 1.05 || loc[1] < -0.05 || loc[1] > 1.05) continue;
            if (o.wny > 0.7) continue;
            MobFace next = face(o);
            int tx = (int) Math.max(0, Math.min(next.pw - 1, o.sAt(loc[0], loc[1]) * next.pw));
            int ty = (int) Math.max(0, Math.min(next.ph - 1, o.tAt(loc[0], loc[1]) * next.ph));
            int c = canvas.take(x, y);
            next.add(tx, ty, 1, c, now, lifetime, rng);
            return;
        }
        if (!drips) return;
        int c = canvas.take(x, y);
        letGo(f, edge, c, posed, now, lifetime, rng);
    }

    private void letGo(ModelFacesOfMob.Face from, Vec3 at, int colour, List<ModelFacesOfMob.Face> posed,
                       int now, int lifetime, RandomSource rng) {
        Vec3 below = at.add(0, -0.06, 0);
        int cube = cubeAround(below, from.cube, posed);
        if (cube < 0) {
            drips.add(new Drip(at, colour));
            return;
        }
        ModelFacesOfMob.Face best = null;
        double bestOff = Double.MAX_VALUE, bestA = 0, bestB = 0;
        for (ModelFacesOfMob.Face o : posed) {
            if (o.cube != cube || Math.abs(o.wny) > 0.7) continue;
            double[] loc = o.locate(below.x, below.y, below.z);
            if (loc == null) continue;
            double off = Math.abs(loc[2]);
            if (off < bestOff) {
                best = o;
                bestOff = off;
                bestA = Math.max(0, Math.min(1, loc[0]));
                bestB = Math.max(0, Math.min(1, loc[1]));
            }
        }
        if (best == null) return;
        MobFace next = face(best);
        int tx = (int) Math.max(0, Math.min(next.pw - 1, best.sAt(bestA, bestB) * next.pw));
        int ty = (int) Math.max(0, Math.min(next.ph - 1, best.tAt(bestA, bestB) * next.ph));
        next.add(tx, ty, 1, colour, now, lifetime, rng);
    }

    private static int cubeAround(Vec3 p, int except, List<ModelFacesOfMob.Face> posed) {
        int current = -1, sides = 0;
        boolean inside = true;
        for (int i = 0; i <= posed.size(); i++) {
            ModelFacesOfMob.Face f = i < posed.size() ? posed.get(i) : null;
            if (f == null || f.cube != current) {
                if (current >= 0 && current != except && inside && sides == 6) return current;
                if (f == null) break;
                current = f.cube;
                sides = 0;
                inside = true;
            }
            sides++;
            double d = (p.x - f.wx(0)) * f.wnx + (p.y - f.wy(0)) * f.wny + (p.z - f.wz(0)) * f.wnz;
            if (d > 0.002) inside = false;
        }
        return -1;
    }

    void age(int now, BloodCanvasAtlas atlas) {
        Iterator<MobFace> it = faces.values().iterator();
        while (it.hasNext()) {
            MobFace f = it.next();
            if (f.age(now, wash) || now - f.lastSeen > 20 * 60) {
                f.dispose(atlas);
                it.remove();
            }
        }
    }

    void dispose(BloodCanvasAtlas atlas) {
        for (MobFace f : faces.values()) f.dispose(atlas);
        faces.clear();
        pending.clear();
        drips.clear();
    }
}
