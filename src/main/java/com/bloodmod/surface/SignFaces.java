package com.bloodmod.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SignFaces {

    record Rect(Vec3 origin, Vec3 u, Vec3 v, double w, double h, Vec3 n, Vec3 pivot, Vec3 axis) {}

    private static final Map<BlockState, List<Rect>> CACHE = new ConcurrentHashMap<>();

    private SignFaces() {}

    static void clear() {
        CACHE.clear();
    }

    static boolean applies(BlockState state) {
        return state.getBlock() instanceof SignBlock || state.getBlock() instanceof AbstractBannerBlock;
    }

    static List<Rect> rects(BlockState state) {
        List<Rect> r = CACHE.get(state);
        if (r == null) {
            if (CACHE.size() > 2048) CACHE.clear();
            r = build(state);
            CACHE.put(state, r);
        }
        return r;
    }

    private static List<Rect> build(BlockState state) {
        List<Rect> out = new ArrayList<>();
        try {
            if (state.getBlock() instanceof StandingSignBlock) {
                double yaw = -22.5 * state.getValue(StandingSignBlock.ROTATION);
                box(out, yaw, 7.33333, 0, 7.33333, 8.66667, 9.33333, 8.66667, false, null);
                box(out, yaw, 0, 9.33333, 7.33333, 16, 17.33333, 8.66667, true, null);
            } else if (state.getBlock() instanceof WallSignBlock) {
                double yaw = -state.getValue(WallSignBlock.FACING).toYRot();
                box(out, yaw, 0, 4.33333, 0.33333, 16, 12.33333, 1.66667, true, null);
            } else if (state.getBlock() instanceof CeilingHangingSignBlock) {
                double yaw = -22.5 * state.getValue(CeilingHangingSignBlock.ROTATION);
                box(out, yaw, 1, 0, 7, 15, 10, 9, true, null);
            } else if (state.getBlock() instanceof WallHangingSignBlock) {
                double yaw = -state.getValue(WallHangingSignBlock.FACING).toYRot();
                box(out, yaw, 1, 0, 7, 15, 10, 9, true, null);
                box(out, yaw, 0, 14, 6, 16, 16, 10, true, null);
            } else if (state.getBlock() instanceof BannerBlock) {
                double yaw = -22.5 * state.getValue(BannerBlock.ROTATION);
                box(out, yaw, 7.33333, 0, 7.33333, 8.66667, 28, 8.66667, false, null);
                box(out, yaw, 1.33333, 28, 7.33333, 14.66667, 29.33333, 8.66667, true, null);
                box(out, yaw, 1.33333, 2.66667, 8.66667, 14.66667, 29.33333, 9.33333, true, new Vec3(0.5, 29.33333 / 16, 0.5));
            } else if (state.getBlock() instanceof WallBannerBlock) {
                double yaw = -state.getValue(WallBannerBlock.FACING).toYRot();
                box(out, yaw, 1.33333, 12.33333, 0.33333, 14.66667, 13.66667, 1.66667, true, null);
                box(out, yaw, 1.33333, -13, 1.66667, 14.66667, 13.66667, 2.33333, true, new Vec3(0.5, 13.66667 / 16, 1.0 / 16));
            }
        } catch (Throwable t) {
            out.clear();
        }
        return List.copyOf(out);
    }

    private static void box(List<Rect> out, double yawDegrees,
                            double x0, double y0, double z0, double x1, double y1, double z1, boolean top, Vec3 hinge) {
        double minX = x0 / 16, minY = y0 / 16, minZ = z0 / 16, maxX = x1 / 16, maxY = y1 / 16, maxZ = z1 / 16;
        double sx = maxX - minX, sy = maxY - minY, sz = maxZ - minZ;
        double a = Math.toRadians(yawDegrees);
        if (top) rect(out, a, new Vec3(minX, maxY, minZ), new Vec3(1, 0, 0), new Vec3(0, 0, 1), sx, sz, new Vec3(0, 1, 0), hinge);
        rect(out, a, new Vec3(minX, minY, maxZ), new Vec3(1, 0, 0), new Vec3(0, 0, -1), sx, sz, new Vec3(0, -1, 0), hinge);
        rect(out, a, new Vec3(maxX, maxY, minZ), new Vec3(-1, 0, 0), new Vec3(0, -1, 0), sx, sy, new Vec3(0, 0, -1), hinge);
        rect(out, a, new Vec3(minX, maxY, maxZ), new Vec3(1, 0, 0), new Vec3(0, -1, 0), sx, sy, new Vec3(0, 0, 1), hinge);
        rect(out, a, new Vec3(minX, maxY, minZ), new Vec3(0, 0, 1), new Vec3(0, -1, 0), sz, sy, new Vec3(-1, 0, 0), hinge);
        rect(out, a, new Vec3(maxX, maxY, maxZ), new Vec3(0, 0, -1), new Vec3(0, -1, 0), sz, sy, new Vec3(1, 0, 0), hinge);
    }

    private static void rect(List<Rect> out, double a, Vec3 origin, Vec3 u, Vec3 v, double w, double h, Vec3 n, Vec3 hinge) {
        Vec3 pivot = hinge == null ? null : turn(hinge.subtract(0.5, 0, 0.5), a).add(0.5, 0, 0.5);
        Vec3 axis = hinge == null ? null : turn(new Vec3(1, 0, 0), a);
        out.add(new Rect(turn(origin.subtract(0.5, 0, 0.5), a).add(0.5, 0, 0.5), turn(u, a), turn(v, a), w, h, turn(n, a), pivot, axis));
    }

    static double sway(long gameTime, BlockPos pos) {
        double f = Math.floorMod((long) (pos.getX() * 7 + pos.getY() * 9 + pos.getZ() * 13) + gameTime, 100L) / 100.0;
        return (-0.0125 + 0.01 * Math.cos(2 * Math.PI * f)) * Math.PI;
    }

    private static Vec3 turn(Vec3 p, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
    }

    static Direction facing(Rect r) {
        Vec3 n = r.n;
        double ax = Math.abs(n.x), ay = Math.abs(n.y), az = Math.abs(n.z);
        if (ay >= ax && ay >= az) return n.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return n.x > 0 ? Direction.EAST : Direction.WEST;
        return n.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    static BloodSurfaces.Hit clip(BlockState state, BlockPos pos, Vec3 from, Vec3 to) {
        Vec3 f = from.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 d = to.subtract(from);
        double bestT = Double.MAX_VALUE;
        Rect best = null;
        for (Rect r : rects(state)) {
            double dn = d.dot(r.n);
            if (dn >= -1.0e-9) continue;
            double t = r.origin.subtract(f).dot(r.n) / dn;
            if (t < -1.0e-6 || t > 1.0 + 1.0e-6 || t >= bestT) continue;
            Vec3 p = f.add(d.scale(t)).subtract(r.origin);
            double a = p.dot(r.u), b = p.dot(r.v);
            double eps = 1.0e-4;
            if (a < -eps || a > r.w + eps || b < -eps || b > r.h + eps) continue;
            bestT = t;
            best = r;
        }
        if (best == null) return null;
        double t = Math.max(0.0, bestT);
        return new BloodSurfaces.Hit(pos, facing(best), from.add(d.scale(t)));
    }
}
