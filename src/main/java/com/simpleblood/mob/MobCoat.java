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

/**
 * The blood on one mob: a canvas per face of its model, by the model's own polygon objects,
 * so it stays put on the body however the model moves. Hits waiting for the next frame (the
 * mob's pose is only known while it is drawn), and the units that ran off its faces this tick.
 */
final class MobCoat {
    /** A hit waiting to be painted: where the blow landed (from the mob), which way it went, with what, how hard. */
    record Splat(Vec3 at, Vec3 dir, HitContext.Weapon weapon, float size, int units, int made) {}

    /** A drop that left the body: where, and its colour (with the glow mark). */
    record Drip(Vec3 at, int colour) {}

    final int entityId;
    final int colour;                 // the mob's blood, glow mark included
    final Map<Object, MobFace> faces = new java.util.HashMap<>(); // keys: the model's polygons, or cube sides
    final List<Splat> pending = new ArrayList<>();
    final List<Drip> drips = new ArrayList<>();
    int lastSim = -1;                 // clock of the last run-down step
    int lastSeen;                     // clock of the last frame the mob was drawn
    int wash;                         // extra drying per tick: in water or rain
    // The wound: where the last blow struck the body. A badly hurt mob bleeds from here, onto
    // its own body, and the blood runs down and drips off.
    Object woundFace;
    int woundX, woundY, woundW, woundH;
    int seep;                         // units waiting to well up at the wound

    MobCoat(int entityId, int colour) {
        this.entityId = entityId;
        this.colour = colour;
    }

    boolean isEmpty() {
        return faces.isEmpty() && pending.isEmpty();
    }

    MobFace face(ModelFacesOfMob.Face f) {
        return faces.computeIfAbsent(f.key, k -> new MobFace(f.pw, f.ph));
    }

    // ------------------------------------------------------------------ painting

    /**
     * Paints a hit. The shape is laid out in the world, across the blow, as a set of points; a
     * ray from each, along the blow, stains the first face it meets that faces it. So the mark
     * wraps round the body the way the blow did, and only its outside, on the near side, takes
     * blood. Where the blow struck gets the most, so it runs down from there.
     * <ul>
     *   <li>Blades: a long thin slash across the body, a little tilted.</li>
     *   <li>Axes: a short deep gash, two pixels wide, nearer upright.</li>
     *   <li>Maces: a round crater, heavy in the middle, ragged at the rim, with spatter.</li>
     *   <li>Arrows and other shots: a small deep puncture.</li>
     *   <li>Anything else (fists, claws, bites, falls): a few irregular splotches.</li>
     * </ul>
     */
    void paint(Splat splat, List<ModelFacesOfMob.Face> faces, int now, int lifetime, RandomSource rng) {
        Vec3 dir = splat.dir();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float size = splat.size();
        int units = splat.units();
        // The point the blow itself struck: the heaviest blood, and the wound a hurt mob bleeds from.
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
                        double rim = radius * (0.8 + rng.nextDouble() * 0.3); // ragged edge
                        if (r > rim) continue;
                        int u = r < radius * 0.3 ? units + 3 : r < radius * 0.7 ? 2 : (rng.nextFloat() < 0.6f ? 1 : 0);
                        if (u > 0) stain(splat, side.scale(a).add(up.scale(b)), faces, u, false, now, lifetime, rng);
                    }
                }
                for (int i = 0; i < 6; i++) { // spatter thrown out of the crater
                    double ang = rng.nextDouble() * Math.PI * 2, r = radius * (1.2 + rng.nextDouble() * 0.6);
                    stain(splat, side.scale(Math.cos(ang) * r).add(up.scale(Math.sin(ang) * r)), faces, 1, false, now, lifetime, rng);
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

    /** One point of a shape: stains the pixel its ray strikes; the blow's own point becomes the wound. */
    private void stain(Splat splat, Vec3 offset, List<ModelFacesOfMob.Face> faces, int units, boolean wound,
                       int now, int lifetime, RandomSource rng) {
        int[] px = new int[2];
        ModelFacesOfMob.Face f = strike(splat.at().add(offset), splat.dir(), faces, px);
        if (f == null) return;
        MobFace canvas = face(f);
        canvas.add(px[0], px[1], units, colour, now, lifetime, rng);
        if (wound) markWound(f, canvas, px);
    }

    /** An irregular splotch around the point its ray strikes. */
    private void blotch(Splat splat, Vec3 offset, List<ModelFacesOfMob.Face> faces, int units, boolean wound,
                        int now, int lifetime, RandomSource rng) {
        int[] px = new int[2];
        ModelFacesOfMob.Face f = strike(splat.at().add(offset), splat.dir(), faces, px);
        if (f == null) return;
        MobFace canvas = face(f);
        int cx = px[0], cy = px[1];
        canvas.add(cx, cy, units, colour, now, lifetime, rng);
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

    /**
     * The face a ray along the blow strikes first, through a point of the hit, from just
     * outside the body; null when it misses. Writes the struck pixel into {@code px}.
     */
    private static ModelFacesOfMob.Face strike(Vec3 point, Vec3 dir, List<ModelFacesOfMob.Face> faces, int[] px) {
        Vec3 from = point.subtract(dir.scale(1.2));
        ModelFacesOfMob.Face best = null;
        double bestT = Double.MAX_VALUE, bestA = 0, bestB = 0;
        for (ModelFacesOfMob.Face f : faces) {
            double facing = f.wnx * dir.x + f.wny * dir.y + f.wnz * dir.z;
            if (facing > -0.05) continue; // seen edge-on or from behind
            // Where the ray crosses the face's plane.
            double d = (f.wx(0) - from.x) * f.wnx + (f.wy(0) - from.y) * f.wny + (f.wz(0) - from.z) * f.wnz;
            double tt = d / facing;
            if (tt <= 0 || tt >= bestT || tt > 4.0) continue;
            double[] loc = f.locate(from.x + dir.x * tt, from.y + dir.y * tt, from.z + dir.z * tt);
            if (loc == null || loc[0] < 0 || loc[0] > 1 || loc[1] < 0 || loc[1] > 1) continue;
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

    /** Lets the units waiting at the wound well up there (the canvas comes back if it had dried away). */
    void bleed(int now, int lifetime, RandomSource rng) {
        if (seep <= 0 || woundFace == null) return;
        MobFace canvas = faces.computeIfAbsent(woundFace, k -> new MobFace(woundW, woundH));
        canvas.add(Math.min(woundX, canvas.pw - 1), Math.min(woundY, canvas.ph - 1), seep, colour, now, lifetime, rng);
        seep = 0;
    }

    // ------------------------------------------------------------------ running and dripping

    /** Units a pixel needs before it runs on. */
    private static final int RUN_DEPTH = 3;

    /**
     * One step of the blood moving: heavy pixels run downhill across their face, over its edge
     * onto the next face of the same cube, or drip off the body where nothing is below. Faces
     * looking down drip.
     */
    void flow(List<ModelFacesOfMob.Face> posed, int now, int lifetime, RandomSource rng, boolean runs, boolean drips) {
        if (!runs && !drips) return;
        for (ModelFacesOfMob.Face f : posed) {
            MobFace canvas = faces.get(f.key);
            if (canvas == null || canvas.count == 0) continue;
            if (f.wny < -0.7) {
                // The underside: blood gathers and lets go in drops.
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
            // Downhill across the face, as a step in canvas pixels.
            double gx = f.wnx * f.wny, gy = -1 + f.wny * f.wny, gz = f.wnz * f.wny; // gravity along the face
            double glen = Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (glen < 0.35) continue; // lying flat: it pools
            double[] at0 = f.locate(f.wx(0) + gx, f.wy(0) + gy, f.wz(0) + gz);
            if (at0 == null) continue;
            double ds = f.sAt(at0[0], at0[1]) - f.s[0], dt = f.tAt(at0[0], at0[1]) - f.t[0];
            double px = ds * canvas.pw, py = dt * canvas.ph;
            int stepX = Math.abs(px) > Math.abs(py) * 0.5 ? (int) Math.signum(px) : 0;
            int stepY = Math.abs(py) > Math.abs(px) * 0.5 ? (int) Math.signum(py) : 0;
            if (stepX == 0 && stepY == 0) continue;
            // Bottom row first along the slope, so a unit moves once per step.
            int ys = stepY > 0 ? canvas.ph - 1 : 0, ye = stepY > 0 ? -1 : canvas.ph, yd = stepY > 0 ? -1 : 1;
            int xs = stepX > 0 ? canvas.pw - 1 : 0, xe = stepX > 0 ? -1 : canvas.pw, xd = stepX > 0 ? -1 : 1;
            for (int y = ys; y != ye; y += yd) {
                for (int x = xs; x != xe; x += xd) {
                    if (canvas.depthAt(x, y) < RUN_DEPTH || rng.nextFloat() > 0.6f) continue;
                    int nx = x + stepX, ny = y + stepY;
                    // Now and then the trickle wanders a pixel sideways.
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

    /** A unit running off its face: onto the next face of the same cube, or off the body as a drop. */
    private void overEdge(ModelFacesOfMob.Face f, MobFace canvas, int x, int y, int nx, int ny,
                          List<ModelFacesOfMob.Face> posed, int now, int lifetime, RandomSource rng, boolean drips) {
        double fx = Math.max(0, Math.min(1, (nx + 0.5) / canvas.pw)), fy = Math.max(0, Math.min(1, (ny + 0.5) / canvas.ph));
        Vec3 edge = f.at(fx, fy);
        for (ModelFacesOfMob.Face o : posed) {
            if (o == f || o.cube != f.cube) continue;
            double[] loc = o.locate(edge.x, edge.y, edge.z);
            if (loc == null || Math.abs(loc[2]) > 0.02 || loc[0] < -0.05 || loc[0] > 1.05 || loc[1] < -0.05 || loc[1] > 1.05) continue;
            if (o.wny > 0.7) continue; // running up onto a top face: no
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

    /**
     * A unit leaving the body at a point. Where another part of the body is right below it (a
     * creeper's belly over its legs, a zombie's arm over its hip), the drop would fall through
     * that part: it runs on down that part's side instead. Otherwise it falls as a drop.
     */
    private void letGo(ModelFacesOfMob.Face from, Vec3 at, int colour, List<ModelFacesOfMob.Face> posed,
                       int now, int lifetime, RandomSource rng) {
        Vec3 below = at.add(0, -0.06, 0);
        int cube = cubeAround(below, from.cube, posed);
        if (cube < 0) {
            drips.add(new Drip(at, colour));
            return;
        }
        // Onto the nearest side of that part, where the point lands on it.
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
        if (best == null) return; // soaked up
        MobFace next = face(best);
        int tx = (int) Math.max(0, Math.min(next.pw - 1, best.sAt(bestA, bestB) * next.pw));
        int ty = (int) Math.max(0, Math.min(next.ph - 1, best.tAt(bestA, bestB) * next.ph));
        next.add(tx, ty, 1, colour, now, lifetime, rng);
    }

    /** A whole cube of the model (all six sides drawn) other than {@code except} that holds the point, or -1. */
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
            if (d > 0.002) inside = false; // in front of this side: outside the cube
        }
        return -1;
    }

    /** Ages every face; clean faces give their tile back. */
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
