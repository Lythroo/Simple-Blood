package com.simpleblood.surface;

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
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SignFaces {

    record Rect(Vec3 origin, Vec3 u, Vec3 v, double w, double h, Vec3 n, Vec3 pivot, Vec3 axis, Cut cut) {
        Rect withCut(Cut c) {
            return new Rect(origin, u, v, w, h, n, pivot, axis, c);
        }
    }

    record Cut(boolean[] mask, int w, int h) {
        boolean opaque(double a, double b) {
            int x = Math.max(0, Math.min(w - 1, (int) Math.floor(a * w)));
            int y = Math.max(0, Math.min(h - 1, (int) Math.floor(b * h)));
            return mask[y * w + x];
        }
    }

    static boolean[] mask(Rect r, int pw, int ph) {
        if (r.cut == null) return null;
        boolean[] m = new boolean[pw * ph];
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) m[y * pw + x] = r.cut.opaque((x + 0.5) / pw, (y + 0.5) / ph);
        }
        return m;
    }

    private static final Map<BlockState, List<Rect>> CACHE = new ConcurrentHashMap<>();

    private SignFaces() {}

    static void clear() {
        CACHE.clear();
        LIDDED.clear();
        SHELLS.clear();
    }

    private static final Cut WHOLE = new Cut(new boolean[]{true}, 1, 1);

    record ShulkerCuts(Cut shellSides, Cut shellUnder, Cut baseSides, Cut baseTop) {}

    private static final ShulkerCuts SOLID = new ShulkerCuts(null, null, null, null);
    private static final Map<String, ShulkerCuts> SHELLS = new ConcurrentHashMap<>();

    static ShulkerCuts shulkerCuts(BlockState state) {
        net.minecraft.world.item.DyeColor colour = ((ShulkerBoxBlock) state.getBlock()).getColor();
        String name = colour == null ? "shulker" : "shulker_" + colour.getName();
        return SHELLS.computeIfAbsent(name, SignFaces::readShulker);
    }

    private static ShulkerCuts readShulker(String name) {
        var id = com.simpleblood.Ids.parse("minecraft:textures/entity/shulker/" + name + ".png");
        var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return SOLID;
        try (java.io.InputStream in = resource.get().open();
             com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            int s = Math.max(1, img.getWidth() / 64);
            if (img.getWidth() < 64 * s || img.getHeight() < 52 * s) return SOLID;
            return new ShulkerCuts(sides(img, s, 16, 12), square(img, s, 32, 0), sides(img, s, 44, 8), square(img, s, 16, 28));
        } catch (Exception e) {
            return SOLID;
        }
    }

    private static Cut sides(com.mojang.blaze3d.platform.NativeImage img, int s, int v, int h) {
        int w = 16 * s, hh = h * s;
        boolean[] m = new boolean[w * hh];
        java.util.Arrays.fill(m, true);
        boolean holes = false;
        for (int side = 0; side < 4; side++) {
            for (int y = 0; y < hh; y++) {
                for (int x = 0; x < w; x++) {
                    if (Pixels.alpha(img, side * w + x, v * s + y) >= 16) continue;
                    m[y * w + x] = false;
                    m[y * w + (w - 1 - x)] = false;
                    holes = true;
                }
            }
        }
        return holes ? new Cut(m, w, hh) : null;
    }

    private static Cut square(com.mojang.blaze3d.platform.NativeImage img, int s, int u, int v) {
        int w = 16 * s;
        boolean[] m = new boolean[w * w];
        java.util.Arrays.fill(m, true);
        boolean holes = false;
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                if (Pixels.alpha(img, u * s + x, v * s + y) >= 16) continue;
                holes = true;
                int mx = w - 1 - x, my = w - 1 - y;
                int[][] all = {{x, y}, {mx, y}, {x, my}, {mx, my}, {y, x}, {my, x}, {y, mx}, {my, mx}};
                for (int[] p : all) m[p[1] * w + p[0]] = false;
            }
        }
        return holes ? new Cut(m, w, w) : null;
    }

    static boolean applies(BlockState state) {
        return state.getBlock() instanceof SignBlock || state.getBlock() instanceof AbstractBannerBlock;
    }

    private static final Map<BlockState, Boolean> LIDDED = new ConcurrentHashMap<>();

    static boolean lidded(BlockState state) {
        if (!(state.getBlock() instanceof AbstractChestBlock) && !(state.getBlock() instanceof ShulkerBoxBlock)) return false;
        return LIDDED.computeIfAbsent(state, SignFaces::looksLidded);
    }

    private static boolean looksLidded(BlockState state) {
        try {
            if (state.getBlock() instanceof ShulkerBoxBlock) return state.hasProperty(BlockStateProperties.FACING);
            if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return false;
            AABB b = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
            return Math.abs(b.minY) < 1.0e-4 && Math.abs(b.maxY - 14.0 / 16) < 1.0e-4
                    && b.getXsize() > 13.5 / 16 && b.getZsize() > 13.5 / 16;
        } catch (Throwable t) {
            return false;
        }
    }

    record Pose(double angle, double slide) {}

    static final Pose STILL = new Pose(0, 0);

    static Pose pose(net.minecraft.world.level.Level level, BlockPos pos, long gameTime, float partialTick) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ShulkerBoxBlockEntity box) {
            float p = box.getProgress(partialTick);
            if (p <= 0) return STILL;
            com.simpleblood.Tally.count("lid.open");
            return new Pose(-1.5 * Math.PI * p, 0.5 * p);
        }
        if (be instanceof LidBlockEntity lid) {
            float o = lid.getOpenNess(partialTick);
            BlockState state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                if (level.getBlockEntity(pos.relative(ChestBlock.getConnectedDirection(state))) instanceof LidBlockEntity other) {
                    o = Math.max(o, other.getOpenNess(partialTick));
                }
            }
            if (o <= 0) return STILL;
            com.simpleblood.Tally.count("lid.open");
            o = 1f - o;
            o = 1f - o * o * o;
            return new Pose(-o * Math.PI / 2, 0);
        }
        return new Pose(sway(gameTime, partialTick, pos), 0);
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
            } else if (lidded(state) && state.getBlock() instanceof ShulkerBoxBlock) {
                ShulkerCuts cuts = shulkerCuts(state);
                List<Rect> up = new ArrayList<>();
                List<Rect> shell = new ArrayList<>();
                box(shell, 0, 0, 4, 0, 16, 16, 16, true, new Vec3(0.5, 0.5, 0.5));
                for (Rect r : shell) up.add(Math.abs(r.n().y) < 0.5 ? r.withCut(cuts.shellSides()) : r.n().y < 0 ? r.withCut(cuts.shellUnder()) : r);
                for (Rect r : shell) if (r.n().y >= 0) up.add(inside(Math.abs(r.n().y) < 0.5 ? r.withCut(cuts.shellSides()) : r));
                List<Rect> base = new ArrayList<>();
                box(base, 0, 0, 0, 0, 16, 8, 16, true, null);
                for (Rect r : base) up.add(Math.abs(r.n().y) < 0.5 ? r.withCut(cuts.baseSides()) : r.n().y > 0 ? r.withCut(cuts.baseTop()) : r);
                for (Rect r : base) if (r.n().y <= 0) up.add(inside(Math.abs(r.n().y) < 0.5 ? r.withCut(cuts.baseSides()) : r));
                Quaternionf q = state.getValue(BlockStateProperties.FACING).getRotation();
                for (Rect r : up) out.add(turned(r, q));
            } else if (lidded(state)) {
                double yaw = -state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
                ChestType type = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                double x0 = type == ChestType.LEFT ? 0 : 1, x1 = type == ChestType.RIGHT ? 16 : 15;
                box(out, yaw, x0, 0, 1, x1, 9, 15, false, null);
                floor(out, yaw, x0, 10, 1, x1, 15);
                box(out, yaw, x0, 9, 1, x1, 14, 15, true, new Vec3(0.5, 9.0 / 16, 1.0 / 16));
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

    private static Rect inside(Rect r) {
        return new Rect(r.origin(), r.u(), r.v(), r.w(), r.h(), r.n().scale(-1), r.pivot(), r.axis(), r.cut());
    }

    private static void floor(List<Rect> out, double yawDegrees, double x0, double y, double z0, double x1, double z1) {
        rect(out, Math.toRadians(yawDegrees), new Vec3(x0 / 16, y / 16, z0 / 16), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                (x1 - x0) / 16, (z1 - z0) / 16, new Vec3(0, 1, 0), null);
    }

    private static void rect(List<Rect> out, double a, Vec3 origin, Vec3 u, Vec3 v, double w, double h, Vec3 n, Vec3 hinge) {
        Vec3 pivot = hinge == null ? null : turn(hinge.subtract(0.5, 0, 0.5), a).add(0.5, 0, 0.5);
        Vec3 axis = hinge == null ? null : turn(new Vec3(1, 0, 0), a);
        out.add(new Rect(turn(origin.subtract(0.5, 0, 0.5), a).add(0.5, 0, 0.5), turn(u, a), turn(v, a), w, h, turn(n, a), pivot, axis, null));
    }

    static double sway(long gameTime, float partialTick, BlockPos pos) {
        double f = (Math.floorMod((long) (pos.getX() * 7 + pos.getY() * 9 + pos.getZ() * 13) + gameTime, 100L) + partialTick) / 100.0;
        return (-0.0125 + 0.01 * Math.cos(2 * Math.PI * f)) * Math.PI;
    }

    private static Rect turned(Rect r, Quaternionf q) {
        Vec3 c = new Vec3(0.5, 0.5, 0.5);
        Vec3 pivot = r.pivot == null ? null : rotate(q, r.pivot.subtract(c)).add(c);
        Vec3 axis = r.axis == null ? null : rotate(q, new Vec3(0, 1, 0));
        return new Rect(rotate(q, r.origin.subtract(c)).add(c), rotate(q, r.u), rotate(q, r.v), r.w, r.h, rotate(q, r.n), pivot, axis, r.cut);
    }

    private static Vec3 rotate(Quaternionf q, Vec3 p) {
        Vector3f v = q.transform(new Vector3f((float) p.x, (float) p.y, (float) p.z));
        return new Vec3(v.x(), v.y(), v.z());
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

    static BloodSurfaces.Hit clip(net.minecraft.world.level.Level level, BlockState state, BlockPos pos, Vec3 from, Vec3 to) {
        Vec3 f0 = from.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 d0 = to.subtract(from);
        double bestT = Double.MAX_VALUE;
        Rect best = null;
        Vec3 bestF = f0, bestD = d0;
        Pose pose = null;
        for (Rect r : rects(state)) {
            Vec3 f = f0, d = d0;
            if (r.pivot != null) {
                if (pose == null) pose = pose(level, pos, level.getGameTime(), 1f);
                if (pose.angle() != 0 || pose.slide() != 0) {
                    Vec3 a = atRest(f0, r, pose), b = atRest(f0.add(d0), r, pose);
                    f = a;
                    d = b.subtract(a);
                }
            }
            double dn = d.dot(r.n);
            if (dn >= -1.0e-9) continue;
            double t = r.origin.subtract(f).dot(r.n) / dn;
            if (t < -1.0e-6 || t > 1.0 + 1.0e-6 || t >= bestT) continue;
            Vec3 p = f.add(d.scale(t)).subtract(r.origin);
            double a = p.dot(r.u), b = p.dot(r.v);
            double eps = 1.0e-4;
            if (a < -eps || a > r.w + eps || b < -eps || b > r.h + eps) continue;
            if (r.cut != null && !r.cut.opaque(a / r.w, b / r.h)) continue;
            bestT = t;
            best = r;
            bestF = f;
            bestD = d;
        }
        if (best == null) return null;
        double t = Math.max(0.0, bestT);
        return new BloodSurfaces.Hit(pos, facing(best), from.add(d0.scale(t)), bestF.add(bestD.scale(t)).add(pos.getX(), pos.getY(), pos.getZ()));
    }

    static boolean onDrawn(BlockState state, Vec3 local, Direction face) {
        for (Rect r : rects(state)) {
            if (facing(r) != face) continue;
            Vec3 p = local.subtract(r.origin);
            if (Math.abs(p.dot(r.n)) > 0.06) continue;
            double a = p.dot(r.u), b = p.dot(r.v), eps = 0.02;
            if (a < -eps || a > r.w + eps || b < -eps || b > r.h + eps) continue;
            if (r.cut != null && !r.cut.opaque(Math.max(0, Math.min(1, a / r.w)), Math.max(0, Math.min(1, b / r.h)))) continue;
            return true;
        }
        return false;
    }

    private static Vec3 atRest(Vec3 p, Rect r, Pose pose) {
        Vec3 q = p.subtract(r.axis.scale(pose.slide())).subtract(r.pivot);
        double c = Math.cos(-pose.angle()), s = Math.sin(-pose.angle());
        Vec3 k = r.axis, kxq = k.cross(q);
        double kdq = k.dot(q);
        return new Vec3(q.x * c + kxq.x * s + k.x * kdq * (1 - c),
                q.y * c + kxq.y * s + k.y * kdq * (1 - c),
                q.z * c + kxq.z * s + k.z * kdq * (1 - c)).add(r.pivot);
    }
}
