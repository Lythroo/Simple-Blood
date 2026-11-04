package com.simpleblood.surface;

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

    record Face(Direction dir, AABB box, int quad, Cutout cut) {}

    record Cutout(Vec3 origin, Vec3 u, Vec3 v, double w, double h, boolean[] mask, int pw, int ph) {
        boolean opaque(double lx, double ly, double lz) {
            double dx = lx - origin.x, dy = ly - origin.y, dz = lz - origin.z;
            int gx = (int) Math.floor((dx * u.x + dy * u.y + dz * u.z) / w * pw);
            int gy = (int) Math.floor((dx * v.x + dy * v.y + dz * v.z) / h * ph);
            gx = Math.max(0, Math.min(pw - 1, gx));
            gy = Math.max(0, Math.min(ph - 1, gy));
            return mask[gy * pw + gx];
        }
    }

    record Slanted(int index, Vec3 c0, Vec3 e1, Vec3 e2, Vec3 n, Cutout cut) {
        double score(Vec3 lp) {
            Vec3 d = lp.subtract(c0);
            double a = d.dot(e1) / e1.lengthSqr(), b = d.dot(e2) / e2.lengthSqr();
            double outside = Math.max(0, Math.max(-a, a - 1)) * e1.length() + Math.max(0, Math.max(-b, b - 1)) * e2.length();
            return Math.abs(d.dot(n)) * 4 + outside;
        }
    }

    private static final Map<BlockState, ModelFaces> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE = 2048;
    private static final int MAX_QUADS = 1024;
    private static final double REACH = 0.25;
    private static final double EDGE = 0.5 / 16;

    final List<Face> faces;
    final List<Slanted> slanted;
    private final List<Solid> solids;
    final boolean usable;
    final boolean plain;
    final boolean withinOutline;
    private final AABB reach;
    private final boolean[] covered = new boolean[6];

    private static final ModelFaces NONE = new ModelFaces(List.of(), List.of(), List.of(), false, false, false, null);

    private record Solid(AABB box, List<Cutout> cuts) {
        boolean contains(double lx, double ly, double lz) {
            if (!box.inflate(0.002).contains(lx, ly, lz)) return false;
            for (Cutout c : cuts) if (!c.opaque(lx, ly, lz)) return false;
            return true;
        }
    }

    private ModelFaces(List<Face> faces, List<Slanted> slanted, List<Solid> solids, boolean usable, boolean plain,
                       boolean withinOutline, AABB reach) {
        this.faces = faces;
        this.slanted = slanted;
        this.solids = solids;
        this.usable = usable;
        this.plain = plain;
        this.withinOutline = withinOutline;
        this.reach = reach;
        for (Direction d : Direction.values()) {
            if (plain) { covered[d.ordinal()] = true; continue; }
            double side = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            for (Face f : faces) {
                if (f.dir != d || f.cut != null) continue;
                AABB b = f.box;
                boolean whole = switch (d.getAxis()) {
                    case X -> Math.abs(b.minX - side) <= EDGE && b.minY <= EDGE && b.maxY >= 1 - EDGE && b.minZ <= EDGE && b.maxZ >= 1 - EDGE;
                    case Y -> Math.abs(b.minY - side) <= EDGE && b.minX <= EDGE && b.maxX >= 1 - EDGE && b.minZ <= EDGE && b.maxZ >= 1 - EDGE;
                    case Z -> Math.abs(b.minZ - side) <= EDGE && b.minX <= EDGE && b.maxX >= 1 - EDGE && b.minY <= EDGE && b.maxY >= 1 - EDGE;
                };
                if (whole) { covered[d.ordinal()] = true; break; }
            }
        }
    }

    boolean covers(Direction side) {
        return covered[side.ordinal()];
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
        if (quads.isEmpty() || quads.size() > MAX_QUADS || outline.isEmpty()) return NONE;
        boolean full = net.minecraft.world.level.block.Block.isShapeFullBlock(outline);
        boolean glass = clearIsSolid(state);
        Cutout[] cuts = new Cutout[quads.size()];
        boolean holes = false;
        for (int i = 0; i < quads.size() && !glass; i++) {
            cuts[i] = cutout(quads.get(i));
            holes |= cuts[i] != null;
        }
        if (full && !holes && plainCube(quads)) return new ModelFaces(List.of(), List.of(), List.of(), true, true, true, null);
        AABB cell = new AABB(0, 0, 0, 1, 1, 1).inflate(REACH);
        List<Face> faces = new ArrayList<>();
        List<Slanted> slanted = new ArrayList<>();
        AABB reach = null;
        for (int i = 0; i < quads.size(); i++) {
            BlockToneSampler.QuadInfo q = quads.get(i);
            Direction dir = BlockToneSampler.quadFacing(q);
            AABB box = BlockToneSampler.quadBox(q);
            if (!box.inflate(1.0e-4).intersects(cell)) continue;
            if (dir == null) {
                Vec3[] c = q.corners();
                Vec3 e1 = c[1].subtract(c[0]), e2 = c[3].subtract(c[0]);
                Vec3 n = e1.cross(c[2].subtract(c[0]));
                if (e1.lengthSqr() < 1.0e-8 || e2.lengthSqr() < 1.0e-8 || n.lengthSqr() < 1.0e-10) continue;
                slanted.add(new Slanted(i, c[0], e1, e2, n.normalize(), cuts[i]));
                reach = reach == null ? box : reach.minmax(box);
                continue;
            }
            if (box.getXsize() < 1.0e-4 && dir.getAxis() != Direction.Axis.X) continue;
            if (box.getYsize() < 1.0e-4 && dir.getAxis() != Direction.Axis.Y) continue;
            if (box.getZsize() < 1.0e-4 && dir.getAxis() != Direction.Axis.Z) continue;
            faces.add(new Face(dir, box, i, cuts[i]));
            reach = reach == null ? box : reach.minmax(box);
        }
        if (faces.isEmpty() && slanted.isEmpty()) return NONE;
        boolean swungOut = state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                && state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN);
        AABB bounds = (swungOut ? new AABB(0, 0, 0, 1, 1, 1) : outline.bounds()).inflate(1.0 / 16 + 1.0e-3);
        boolean within = !full && bounds.minX <= reach.minX && bounds.minY <= reach.minY && bounds.minZ <= reach.minZ
                && bounds.maxX >= reach.maxX && bounds.maxY >= reach.maxY && bounds.maxZ >= reach.maxZ;
        for (Face f : faces) if (f.cut != null) within = false;
        for (Slanted t : slanted) if (t.cut != null) within = false;
        return new ModelFaces(List.copyOf(faces), List.copyOf(slanted), List.copyOf(solids(faces)), true, false, within, reach.inflate(1.0e-3));
    }

    private static boolean clearIsSolid(BlockState state) {
        return state.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS;
    }

    private static boolean plainCube(List<BlockToneSampler.QuadInfo> quads) {
        for (BlockToneSampler.QuadInfo q : quads) {
            Direction dir = BlockToneSampler.quadFacing(q);
            if (dir == null) return false;
            AABB b = BlockToneSampler.quadBox(q);
            double plane = switch (dir.getAxis()) {
                case X -> b.minX;
                case Y -> b.minY;
                case Z -> b.minZ;
            };
            double side = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            if (Math.abs(plane - side) > EDGE) return false;
        }
        return true;
    }

    private static Cutout cutout(BlockToneSampler.QuadInfo q) {
        float minU = 1e9f, minV = 1e9f, maxU = -1e9f, maxV = -1e9f;
        for (int i = 0; i < 4; i++) {
            minU = Math.min(minU, q.u()[i]); maxU = Math.max(maxU, q.u()[i]);
            minV = Math.min(minV, q.v()[i]); maxV = Math.max(maxV, q.v()[i]);
        }
        if (maxU - minU < 1e-4 || maxV - minV < 1e-4) return null;
        int o = -1, ue = -1, ve = -1;
        for (int i = 0; i < 4; i++) {
            boolean uMin = Math.abs(q.u()[i] - minU) < 1e-4, vMin = Math.abs(q.v()[i] - minV) < 1e-4;
            boolean uMax = Math.abs(q.u()[i] - maxU) < 1e-4, vMax = Math.abs(q.v()[i] - maxV) < 1e-4;
            if (uMin && vMin) o = i;
            else if (uMax && vMin) ue = i;
            else if (uMin && vMax) ve = i;
        }
        if (o < 0 || ue < 0 || ve < 0) return null;
        Vec3 uVec = q.corners()[ue].subtract(q.corners()[o]), vVec = q.corners()[ve].subtract(q.corners()[o]);
        double w = uVec.length(), h = vVec.length();
        if (w < 1e-4 || h < 1e-4) return null;
        int[] size = BlockToneSampler.spriteSize(q.sprite());
        int pw = Math.max(1, Math.min(64, Math.round((maxU - minU) * size[0])));
        int ph = Math.max(1, Math.min(64, Math.round((maxV - minV) * size[1])));
        boolean[] mask = BlockToneSampler.alphaMask(q.sprite(), minU, maxU, minV, maxV, pw, ph);
        if (mask == null) return null;
        boolean holes = false;
        for (boolean m : mask) if (!m) { holes = true; break; }
        if (!holes) return null;
        return new Cutout(q.corners()[o], uVec.scale(1.0 / w), vVec.scale(1.0 / h), w, h, mask, pw, ph);
    }

    private static List<Solid> solids(List<Face> faces) {
        List<Solid> out = new ArrayList<>();
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
                out.add(solid(faces, new AABB(up.box.minX, faces.get(match).box.minY, up.box.minZ, up.box.maxX, up.box.minY, up.box.maxZ)));
            } else {
                out.add(solid(faces, new AABB(up.box.minX, up.box.minY - 1.0 / 16, up.box.minZ, up.box.maxX, up.box.minY, up.box.maxZ)));
            }
        }
        for (int j = 0; j < faces.size(); j++) {
            Face down = faces.get(j);
            if (down.dir != Direction.DOWN || used[j]) continue;
            out.add(solid(faces, new AABB(down.box.minX, down.box.minY, down.box.minZ, down.box.maxX, down.box.minY + 1.0 / 16, down.box.maxZ)));
        }
        return out;
    }

    private static Solid solid(List<Face> faces, AABB box) {
        List<Cutout> cuts = new ArrayList<>(0);
        AABB grown = box.inflate(1.0e-3);
        for (Face f : faces) {
            if (f.cut == null) continue;
            double plane = switch (f.dir.getAxis()) {
                case X -> f.box.minX;
                case Y -> f.box.minY;
                case Z -> f.box.minZ;
            };
            double lo = switch (f.dir.getAxis()) {
                case X -> box.minX;
                case Y -> box.minY;
                case Z -> box.minZ;
            };
            double hi = switch (f.dir.getAxis()) {
                case X -> box.maxX;
                case Y -> box.maxY;
                case Z -> box.maxZ;
            };
            boolean onSide = Math.abs(plane - lo) < 1.0e-3 || Math.abs(plane - hi) < 1.0e-3;
            if (onSide && grown.contains(f.box.getCenter())) cuts.add(f.cut);
        }
        return new Solid(box, List.copyOf(cuts));
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
        for (Solid b : solids) {
            if (b.contains(lx, ly, lz)) return true;
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
        if (reach == null || (!reach.contains(lf) && reach.clip(lf, lf.add(ld)).isEmpty())) return null;
        for (Slanted s : slanted) {
            double dn = ld.dot(s.n);
            if (dn >= -1.0e-9) continue;
            double t = s.c0.subtract(lf).dot(s.n) / dn;
            if (t < -1.0e-6 || t > 1.0 + 1.0e-6 || t >= bestT) continue;
            Vec3 p = lf.add(ld.scale(t)).subtract(s.c0);
            double a = p.dot(s.e1) / s.e1.lengthSqr(), b = p.dot(s.e2) / s.e2.lengthSqr();
            double eps = 1.0e-3;
            if (a < -eps || a > 1 + eps || b < -eps || b > 1 + eps) continue;
            Vec3 at = lf.add(ld.scale(t));
            if (s.cut != null && !s.cut.opaque(at.x, at.y, at.z)) continue;
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
            if (f.cut != null && !f.cut.opaque(px, py, pz)) continue;
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
