package com.bloodmod.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ModelFaces {

    record Face(Direction dir, AABB box) {}

    record Slanted(int index, Vec3 c0, Vec3 e1, Vec3 e2, Vec3 n) {
        double score(Vec3 lp) {
            Vec3 d = lp.subtract(c0);
            double a = d.dot(e1) / e1.lengthSqr(), b = d.dot(e2) / e2.lengthSqr();
            double outside = Math.max(0, Math.max(-a, a - 1)) * e1.length() + Math.max(0, Math.max(-b, b - 1)) * e2.length();
            return Math.abs(d.dot(n)) * 4 + outside;
        }
    }

    private static final Map<BlockState, ModelFaces> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE = 2048;
    private static final int MAX_QUADS = 64;
    private static final double SLACK = 1.0 / 16 + 1.0e-3;

    final List<Face> faces;
    final List<Slanted> slanted;
    final List<AABB> solids;
    final boolean usable;

    private ModelFaces(List<Face> faces, List<Slanted> slanted, List<AABB> solids, boolean usable) {
        this.faces = faces;
        this.slanted = slanted;
        this.solids = solids;
        this.usable = usable;
    }

    static void clear() {
        CACHE.clear();
    }

    static ModelFaces of(BlockState state, VoxelShape outline) {
        ModelFaces f = CACHE.get(state);
        if (f == null) {
            if (CACHE.size() >= MAX_CACHE) CACHE.clear();
            f = build(state, outline);
            CACHE.put(state, f);
        }
        return f;
    }

    private static ModelFaces build(BlockState state, VoxelShape outline) {
        List<BlockToneSampler.QuadInfo> quads = BlockToneSampler.allQuads(state);
        if (quads.isEmpty() || quads.size() > MAX_QUADS || outline.isEmpty()) {
            return new ModelFaces(List.of(), List.of(), List.of(), false);
        }
        AABB bounds = outline.bounds().inflate(SLACK);
        List<Face> faces = new ArrayList<>();
        List<Slanted> slanted = new ArrayList<>();
        boolean agrees = true;
        for (int i = 0; i < quads.size(); i++) {
            Direction dir = BlockToneSampler.quadFacing(quads.get(i));
            AABB box = BlockToneSampler.quadBox(quads.get(i));
            if (dir == null) {
                Vec3[] c = quads.get(i).corners();
                Vec3 e1 = c[1].subtract(c[0]), e2 = c[3].subtract(c[0]);
                Vec3 n = e1.cross(c[2].subtract(c[0]));
                if (e1.lengthSqr() < 1.0e-8 || e2.lengthSqr() < 1.0e-8 || n.lengthSqr() < 1.0e-10) continue;
                slanted.add(new Slanted(i, c[0], e1, e2, n.normalize()));
                if (box.minX < bounds.minX || box.maxX > bounds.maxX || box.minY < bounds.minY || box.maxY > bounds.maxY
                        || box.minZ < bounds.minZ || box.maxZ > bounds.maxZ) {
                    agrees = false;
                }
                continue;
            }
            if (box.getXsize() < 1.0e-4 && dir.getAxis() != Direction.Axis.X) continue;
            if (box.getYsize() < 1.0e-4 && dir.getAxis() != Direction.Axis.Y) continue;
            if (box.getZsize() < 1.0e-4 && dir.getAxis() != Direction.Axis.Z) continue;
            faces.add(new Face(dir, box));
            if (box.minX < bounds.minX || box.maxX > bounds.maxX || box.minY < bounds.minY || box.maxY > bounds.maxY
                    || box.minZ < bounds.minZ || box.maxZ > bounds.maxZ) {
                agrees = false;
            }
        }
        if ((faces.isEmpty() && slanted.isEmpty()) || !agrees) return new ModelFaces(List.of(), List.of(), List.of(), false);
        return new ModelFaces(List.copyOf(faces), List.copyOf(slanted), List.copyOf(solids(faces)), true);
    }

    private static List<AABB> solids(List<Face> faces) {
        List<AABB> out = new ArrayList<>();
        boolean[] used = new boolean[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            Face up = faces.get(i);
            if (up.dir != Direction.UP) continue;
            int match = -1;
            for (int j = 0; j < faces.size(); j++) {
                Face down = faces.get(j);
                if (down.dir != Direction.DOWN || used[j] || down.box.minY >= up.box.minY - 1.0e-4) continue;
                if (!sameFootprint(up.box, down.box)) continue;
                if (match < 0 || down.box.minY > faces.get(match).box.minY) match = j;
            }
            if (match >= 0) {
                used[match] = true;
                out.add(new AABB(up.box.minX, faces.get(match).box.minY, up.box.minZ, up.box.maxX, up.box.minY, up.box.maxZ));
            } else {
                out.add(new AABB(up.box.minX, up.box.minY - 1.0 / 16, up.box.minZ, up.box.maxX, up.box.minY, up.box.maxZ));
            }
        }
        for (int j = 0; j < faces.size(); j++) {
            Face down = faces.get(j);
            if (down.dir != Direction.DOWN || used[j]) continue;
            out.add(new AABB(down.box.minX, down.box.minY, down.box.minZ, down.box.maxX, down.box.minY + 1.0 / 16, down.box.maxZ));
        }
        return out;
    }

    private static boolean sameFootprint(AABB a, AABB b) {
        return Math.abs(a.minX - b.minX) < 1.0e-3 && Math.abs(a.maxX - b.maxX) < 1.0e-3
                && Math.abs(a.minZ - b.minZ) < 1.0e-3 && Math.abs(a.maxZ - b.maxZ) < 1.0e-3;
    }

    Face face(Direction dir, AABB box) {
        for (Face f : faces) {
            if (f.dir != dir) continue;
            AABB b = f.box;
            if (Math.abs(b.minX - box.minX) < 1e-3 && Math.abs(b.maxX - box.maxX) < 1e-3
                    && Math.abs(b.minY - box.minY) < 1e-3 && Math.abs(b.maxY - box.maxY) < 1e-3
                    && Math.abs(b.minZ - box.minZ) < 1e-3 && Math.abs(b.maxZ - box.maxZ) < 1e-3) return f;
        }
        return null;
    }

    boolean insideSolid(double lx, double ly, double lz) {
        for (AABB b : solids) {
            if (b.inflate(0.002).contains(lx, ly, lz)) return true;
        }
        return false;
    }

    BloodSurfaces.Hit clip(BlockPos pos, Vec3 from, Vec3 to) {
        double fx = from.x - pos.getX(), fy = from.y - pos.getY(), fz = from.z - pos.getZ();
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double bestT = Double.MAX_VALUE;
        Face best = null;
        Slanted bestSlanted = null;
        Vec3 lf = new Vec3(fx, fy, fz), ld = new Vec3(dx, dy, dz);
        for (Slanted s : slanted) {
            double dn = ld.dot(s.n);
            if (dn >= -1.0e-9) continue;
            double t = s.c0.subtract(lf).dot(s.n) / dn;
            if (t < -1.0e-6 || t > 1.0 + 1.0e-6 || t >= bestT) continue;
            Vec3 p = lf.add(ld.scale(t)).subtract(s.c0);
            double a = p.dot(s.e1) / s.e1.lengthSqr(), b = p.dot(s.e2) / s.e2.lengthSqr();
            double eps = 1.0e-3;
            if (a < -eps || a > 1 + eps || b < -eps || b > 1 + eps) continue;
            bestT = t;
            bestSlanted = s;
        }
        for (Face f : faces) {
            double d, o, plane;
            switch (f.dir.getAxis()) {
                case X -> { d = dx; o = fx; plane = f.box.minX; }
                case Y -> { d = dy; o = fy; plane = f.box.minY; }
                default -> { d = dz; o = fz; plane = f.box.minZ; }
            }
            double n = f.dir.getStepX() + f.dir.getStepY() + f.dir.getStepZ();
            if (d * n >= -1.0e-9) continue;
            double t = (plane - o) / d;
            if (t < -1.0e-6 || t > 1.0 + 1.0e-6 || t >= bestT) continue;
            double px = fx + dx * t, py = fy + dy * t, pz = fz + dz * t;
            double eps = 1.0e-4;
            if (f.dir.getAxis() != Direction.Axis.X && (px < f.box.minX - eps || px > f.box.maxX + eps)) continue;
            if (f.dir.getAxis() != Direction.Axis.Y && (py < f.box.minY - eps || py > f.box.maxY + eps)) continue;
            if (f.dir.getAxis() != Direction.Axis.Z && (pz < f.box.minZ - eps || pz > f.box.maxZ + eps)) continue;
            bestT = t;
            best = f;
        }
        if (best == null && bestSlanted == null) return null;
        double t = Math.max(0.0, bestT);
        Direction dir = best != null ? best.dir : facing(bestSlanted.n);
        return new BloodSurfaces.Hit(pos, dir, from.add(dx * t, dy * t, dz * t));
    }

    static Direction facing(Vec3 n) {
        double ax = Math.abs(n.x), ay = Math.abs(n.y), az = Math.abs(n.z);
        if (ay >= ax && ay >= az) return n.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return n.x > 0 ? Direction.EAST : Direction.WEST;
        return n.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
