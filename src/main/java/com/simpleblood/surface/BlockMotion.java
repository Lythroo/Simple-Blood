package com.simpleblood.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

final class BlockMotion {
    private BlockMotion() {}

    interface Motion {
        Vec3 point(Vec3 local);
        Vec3 normal(Vec3 local, Vec3 normal);
    }

    private static final double EPS = 1.0e-4;

    static Motion of(BlockGetter level, BlockPos pos, BlockState from, BlockState to) {
        if (from == to || from.getBlock() != to.getBlock()) return null;
        if (from.getBlock() instanceof FenceGateBlock) return gate(from, to);
        VoxelShape a = from.getShape(level, pos), b = to.getShape(level, pos);
        if (a.isEmpty() || b.isEmpty()) return null;
        List<AABB> as = a.toAabbs(), bs = b.toAabbs();
        if (as.size() != 1 || bs.size() != 1) return null;
        AABB boxA = as.get(0), boxB = bs.get(0);
        if (same(boxA, boxB)) return null;
        Motion hinge = hinge(boxA, boxB);
        return hinge != null ? hinge : press(boxA, boxB);
    }

    private static Motion hinge(AABB a, AABB b) {
        for (int k = 0; k < 3; k++) {
            if (Math.abs(min(a, k) - min(b, k)) > EPS || Math.abs(max(a, k) - max(b, k)) > EPS) continue;
            int i = (k + 1) % 3, j = (k + 2) % 3;
            double ai = size(a, i), aj = size(a, j), bi = size(b, i), bj = size(b, j);
            if (Math.abs(ai - bj) > EPS || Math.abs(aj - bi) > EPS || Math.abs(ai - aj) < EPS) continue;
            double loI = Math.max(min(a, i), min(b, i)), hiI = Math.min(max(a, i), max(b, i));
            double loJ = Math.max(min(a, j), min(b, j)), hiJ = Math.min(max(a, j), max(b, j));
            if (hiI < loI - EPS || hiJ < loJ - EPS) continue;
            double pi = (loI + hiI) / 2, pj = (loJ + hiJ) / 2;
            for (int sign = -1; sign <= 1; sign += 2) {
                QuarterTurn turn = new QuarterTurn(k, pi, pj, sign);
                if (turn.point(a.getCenter()).distanceTo(b.getCenter()) < 1.0e-3) return turn;
            }
        }
        return null;
    }

    private record QuarterTurn(int axis, double pi, double pj, int sign) implements Motion {
        @Override
        public Vec3 point(Vec3 p) {
            return turn(p, pi, pj);
        }

        @Override
        public Vec3 normal(Vec3 local, Vec3 n) {
            return turn(n, 0, 0);
        }

        private Vec3 turn(Vec3 p, double ci, double cj) {
            double[] c = {p.x, p.y, p.z};
            int i = (axis + 1) % 3, j = (axis + 2) % 3;
            double di = c[i] - ci, dj = c[j] - cj;
            c[i] = ci - sign * dj;
            c[j] = cj + sign * di;
            return new Vec3(c[0], c[1], c[2]);
        }
    }

    private static Motion press(AABB a, AABB b) {
        for (int k = 0; k < 3; k++) {
            double sa = size(a, k), sb = size(b, k);
            if (sa < EPS || sb < EPS) return null;
            double ratio = sb / sa;
            if ((ratio < 0.4 || ratio > 2.5) && Math.abs(sb - sa) > 2.0 / 16) return null;
        }
        return new Motion() {
            @Override
            public Vec3 point(Vec3 p) {
                return new Vec3(map(p.x, a.minX, a.maxX, b.minX, b.maxX),
                        map(p.y, a.minY, a.maxY, b.minY, b.maxY),
                        map(p.z, a.minZ, a.maxZ, b.minZ, b.maxZ));
            }

            @Override
            public Vec3 normal(Vec3 local, Vec3 n) {
                return n;
            }
        };
    }

    private static double map(double v, double a0, double a1, double b0, double b1) {
        return b0 + (v - a0) / (a1 - a0) * (b1 - b0);
    }

    private static Motion gate(BlockState from, BlockState to) {
        boolean wasOpen = from.getValue(FenceGateBlock.OPEN), isOpen = to.getValue(FenceGateBlock.OPEN);
        if (wasOpen == isOpen) return null;
        Direction open = (isOpen ? to : from).getValue(FenceGateBlock.FACING);
        boolean spansX = open.getAxis() == Direction.Axis.Z;
        int out = spansX ? open.getStepZ() : open.getStepX();
        double reach = 7.0 / 16;
        double mid = 0.5;
        QuarterTurn leftTurn = gateTurn(spansX, 1.0 / 16, mid + out * reach);
        QuarterTurn rightTurn = gateTurn(spansX, 15.0 / 16, mid + out * reach);
        if (leftTurn == null || rightTurn == null) return null;
        return new Motion() {
            private Motion half(Vec3 p) {
                double s = spansX ? p.x : p.z, f = spansX ? p.z : p.x;
                if (!wasOpen) {
                    if (s < 2.0 / 16 || s > 14.0 / 16) return null;
                    return s < mid ? leftTurn : rightTurn;
                }
                if (Math.abs(f - mid) < 1.5 / 16) return null;
                return s < mid ? inverse(leftTurn) : inverse(rightTurn);
            }

            @Override
            public Vec3 point(Vec3 p) {
                Motion m = half(p);
                return m == null ? p : m.point(p);
            }

            @Override
            public Vec3 normal(Vec3 local, Vec3 n) {
                Motion m = half(local);
                return m == null ? n : m.normal(local, n);
            }
        };
    }

    private static QuarterTurn gateTurn(boolean spansX, double s, double toF) {
        Vec3 from = new Vec3(0.5, 0.5, 0.5);
        Vec3 target = spansX ? new Vec3(s, 0.5, toF) : new Vec3(toF, 0.5, s);
        double px = spansX ? s : 0.5, pz = spansX ? 0.5 : s;
        for (int sign = -1; sign <= 1; sign += 2) {
            QuarterTurn t = new QuarterTurn(1, pz, px, sign);
            if (t.point(from).distanceTo(target) < 1.0e-3) return t;
        }
        return null;
    }

    private static QuarterTurn inverse(QuarterTurn t) {
        return new QuarterTurn(t.axis(), t.pi(), t.pj(), -t.sign());
    }

    private static boolean same(AABB a, AABB b) {
        for (int k = 0; k < 3; k++) {
            if (Math.abs(min(a, k) - min(b, k)) > EPS || Math.abs(max(a, k) - max(b, k)) > EPS) return false;
        }
        return true;
    }

    private static double min(AABB b, int k) { return k == 0 ? b.minX : k == 1 ? b.minY : b.minZ; }
    private static double max(AABB b, int k) { return k == 0 ? b.maxX : k == 1 ? b.maxY : b.maxZ; }
    private static double size(AABB b, int k) { return max(b, k) - min(b, k); }
}
