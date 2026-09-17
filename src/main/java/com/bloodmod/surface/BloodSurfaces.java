package com.bloodmod.surface;

import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodModConfig;
import com.bloodmod.RenderThread;
import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public final class BloodSurfaces {

    private static final Map<CanvasTile.Key, CanvasTile> TILES = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create();
    private static ClientLevel trackedLevel;
    private static int now;

    private record Deferred(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder, IntConsumer after) {}

    private static final ConcurrentLinkedQueue<Deferred> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger pendingCount = new AtomicInteger();
    private static final int MAX_PENDING = 4096;

    private static final double MAX_FALL = 10.0;
    private static final int MAX_FLOW_DEPTH = 5;

    private BloodSurfaces() {}

    public static int now() { return now; }

    public static Collection<CanvasTile> tiles() { return TILES.values(); }

    public static int tileCount() { return TILES.size(); }

    private static BloodModConfig.SurfaceSettings settings() {
        BloodModConfig cfg = BloodModClient.getConfig();
        return cfg == null ? new BloodModConfig.SurfaceSettings() : cfg.surfaces;
    }

    private static boolean detailed() {
        return settings().detailedShapes;
    }

    public static boolean isCrossModel(BlockState state) {
        return detailed() && BlockToneSampler.isCrossModel(state);
    }

    public static boolean isHandDrawn(BlockState state) {
        return detailed() && SignFaces.applies(state);
    }

    public static boolean hasTiltedFaces(BlockState state) {
        if (!detailed() || state.isAir() || SignFaces.applies(state)) return false;
        try {
            VoxelShape shape = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) return false;
            ModelFaces faces = modelFaces(state, shape);
            return faces != null && !faces.slanted.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public record Hit(BlockPos pos, Direction face, Vec3 point) {}

    public static Hit trace(ClientLevel level, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 1.0e-8) {
            to = from.add(0, -0.05, 0);
        }
        Vec3 end = to;
        return BlockGetter.<Hit, Object>traverseBlocks(from, end, null,
                (ctx, pos) -> clipBlockSafely(level, pos, from, end), ctx -> null);
    }

    private static boolean shapeTrouble;

    private static Hit clipBlockSafely(ClientLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        try {
            return clipBlock(level, pos, from, to);
        } catch (Throwable t) {
            shapeTrouble(level, pos, t);
            return null;
        }
    }

    private static void shapeTrouble(ClientLevel level, BlockPos pos, Throwable t) {
        if (shapeTrouble) return;
        shapeTrouble = true;
        String what;
        try {
            what = String.valueOf(level.getBlockState(pos));
        } catch (Throwable ignored) {
            what = "?";
        }
        BloodMod.LOGGER.warn("Simple Blood: could not read the shape of {} at {}; drops pass through blocks like it", what, pos, t);
    }

    private static Hit clipBlock(ClientLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            BlockPos under = pos.below();
            BlockState below = level.getBlockState(under);
            Hit h = null;
            if (isHandDrawn(below)) {
                h = SignFaces.clip(below, under, from, to);
            } else if (hasTiltedFaces(below)) {
                h = modelFaces(below, below.getShape(level, under)).clip(under, from, to);
            }
            if (h != null && h.point().y >= pos.getY() - 1.0e-6) return h;
            BlockPos over = pos.above();
            BlockState above = level.getBlockState(over);
            if (isHandDrawn(above)) {
                h = SignFaces.clip(above, over, from, to);
                if (h != null && h.point().y <= pos.getY() + 1 + 1.0e-6) return h;
            }
            return null;
        }
        if (isHandDrawn(state)) return SignFaces.clip(state, pos, from, to);
        if (!detailed() && (SignFaces.applies(state) || BlockToneSampler.isCrossModel(state))) return null;
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;
        ModelFaces faces = modelFaces(state, shape);
        if (faces != null) {
            Hit drawn = faces.clip(pos, from, to);
            if (drawn != null) return drawn;
        }
        BlockHitResult r = shape.clip(from, to, pos);
        if (r == null || r.getType() != HitResult.Type.BLOCK || (faces != null && r.isInside())) return null;
        return new Hit(pos, r.getDirection(), r.getLocation());
    }

    private static ModelFaces modelFaces(BlockState state, VoxelShape shape) {
        if (!detailed() || Block.isShapeFullBlock(shape) || BlockToneSampler.isCrossModel(state)) return null;
        ModelFaces f = ModelFaces.of(state, shape);
        return f.usable ? f : null;
    }

    public static boolean visibleSolidAt(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        try {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return false;
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) return false;
            double lx = x - pos.getX(), ly = y - pos.getY(), lz = z - pos.getZ();
            ModelFaces faces = modelFaces(state, shape);
            if (faces != null) return faces.insideSolid(lx, ly, lz);
            for (AABB b : shape.toAabbs()) {
                if (b.inflate(0.002).contains(lx, ly, lz)) return true;
            }
            return false;
        } catch (Throwable t) {
            shapeTrouble(level, pos, t);
            return true;
        }
    }

    public static int deposit(ClientLevel level, Hit hit, int colour, int pixels) {
        return deposit(level, hit, colour, pixels, null);
    }

    public static int deposit(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel) {
        return deposit(level, hit, colour, pixels, travel, false);
    }

    public static final int NOTHING = 0, ONTO_SURFACE = 1, INTO_PUDDLE = 2;

    public static int deposit(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder) {
        BloodModConfig.SurfaceSettings s = settings();
        if (!s.enabled || pixels <= 0 || hit == null || !com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.SURFACES)) return NOTHING;
        if (!s.wallsAndCeilings && hit.face() != Direction.UP) return NOTHING;
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.distanceToSqr(hit.point()) > (double) s.renderDistance * s.renderDistance) {
            return NOTHING;
        }
        if (!RenderThread.on()) {
            depositLater(level, hit, colour, pixels, travel, powder, null);
            return ONTO_SURFACE;
        }
        syncLevel(level);

        CanvasTile tile = tileAt(level, hit.pos(), hit.face(), hit.point());
        if (tile == null) return NOTHING;
        int result = tile.depthAt(clampPixel(tile.pixelX(hit.point()), tile.pw), clampPixel(tile.pixelY(hit.point()), tile.ph)) > 0
                ? INTO_PUDDLE : ONTO_SURFACE;
        if (powder) {
            int px = clampPixel(tile.pixelX(hit.point()), tile.pw);
            int py = clampPixel(tile.pixelY(hit.point()), tile.ph);
            List<int[]> spill = new ArrayList<>();
            tile.stampPowder(px, py, pixels, colour, now, s.lifetimeSeconds * 20, RNG, spill);
            for (int[] cell : spill) {
                Vec3 p = tile.pixelCenter(cell[0], cell[1]);
                Direction face = tile.face;
                BlockPos npos = BlockPos.containing(p.subtract(face.getStepX() * 0.02, face.getStepY() * 0.02, face.getStepZ() * 0.02));
                if (npos.equals(tile.pos)) continue;
                CanvasTile n = tileAt(level, npos, face, p);
                if (n != null && Math.abs(planeCoord(n, face) - planeCoord(tile, face)) < 1.0e-3) {
                    n.paintPowder(clampPixel(n.pixelX(p), n.pw), clampPixel(n.pixelY(p), n.ph), colour, now, s.lifetimeSeconds * 20, RNG);
                }
            }
            return result;
        }
        stampAt(level, tile, hit.point(), colour, pixels, 0, travel);
        return result;
    }

    public static void depositLater(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder, IntConsumer after) {
        if (hit == null || level == null || !com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.SURFACES)) return;
        if (pendingCount.incrementAndGet() > MAX_PENDING) {
            pendingCount.decrementAndGet();
            return;
        }
        PENDING.add(new Deferred(level, hit, colour, pixels, travel, powder, after));
    }

    private static void drainPending(ClientLevel level) {
        Deferred d;
        while ((d = PENDING.poll()) != null) {
            pendingCount.decrementAndGet();
            if (d.level() != level) continue;
            int result = deposit(d.level(), d.hit(), d.colour(), d.pixels(), d.travel(), d.powder());
            if (d.after() != null) d.after().accept(result);
        }
    }

    static CanvasTile topTile(ClientLevel level, Hit hit, boolean create) {
        if (hit.face() != Direction.UP) return null;
        syncLevel(level);
        return tileAt(level, hit.pos(), hit.face(), hit.point(), create);
    }

    private static void stampAt(ClientLevel level, CanvasTile tile, Vec3 point, int colour, int pixels, int depth) {
        stampAt(level, tile, point, colour, pixels, depth, null);
    }

    private static void stampAt(ClientLevel level, CanvasTile tile, Vec3 point, int colour, int pixels, int depth, Vec3 travel) {
        BloodModConfig.SurfaceSettings s = settings();
        int px = clampPixel(tile.pixelX(point), tile.pw);
        int py = clampPixel(tile.pixelY(point), tile.ph);
        if (tile.custom && tile.ny < 0.99) {
            tile.stamp(px, py, pixels, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), null);
            return;
        }
        List<int[]> spill = new ArrayList<>();
        double bu = 0, bv = 0;
        if (travel != null && travel.lengthSqr() > 1.0e-6) {
            Vec3 t = travel.normalize();
            bu = t.x * tile.ux + t.y * tile.uy + t.z * tile.uz;
            bv = t.x * tile.vx + t.y * tile.vy + t.z * tile.vz;
        }
        tile.stamp(px, py, pixels, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), spill, bu, bv);

        boolean[] droppedOff = {false};
        for (int[] cell : spill) {
            spillCell(level, tile, cell[0], cell[1], colour, s, droppedOff, depth);
        }
        touchNeighbours(tile);

        if (tile.face == Direction.UP && tile.pw > 1 && tile.ph > 1) {
            int started = 0;
            for (int dx = -2; dx <= 2 && started < 2; dx++) {
                for (int dy = -2; dy <= 2 && started < 2; dy++) {
                    int ex = px + dx, ey = py + dy;
                    if (!tile.filled(ex, ey)) continue;
                    boolean onEdge = ex == 0 || ey == 0 || ex == tile.pw - 1 || ey == tile.ph - 1;
                    if (!onEdge) continue;
                    int ox = ex == 0 ? -1 : (ex == tile.pw - 1 ? tile.pw : ex);
                    int oy = ey == 0 ? -1 : (ey == tile.ph - 1 ? tile.ph : ey);
                    if (spillCell(level, tile, ox, oy, colour, s, droppedOff, depth)) started++;
                }
            }
        }
    }

    private static boolean spillCell(ClientLevel level, CanvasTile tile, int cx, int cy, int colour,
                                     BloodModConfig.SurfaceSettings s, boolean[] droppedOff, int depth) {
        Vec3 p = tile.pixelCenter(cx, cy);
        Direction face = tile.face;
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);

        if (isQuadTile(tile) && hiddenInBlock(tile, p)) return false;

        Vec3 probe = p.subtract(face.getStepX() * 0.02, face.getStepY() * 0.02, face.getStepZ() * 0.02);
        BlockPos npos = BlockPos.containing(probe);
        if (!npos.equals(tile.pos)) {
            CanvasTile neighbour = tileAt(level, npos, face, p);
            if (neighbour != null && Math.abs(planeCoord(neighbour, face) - planeCoord(tile, face)) < 1.0e-3) {
                neighbour.paint(clampPixel(neighbour.pixelX(p), neighbour.pw),
                        clampPixel(neighbour.pixelY(p), neighbour.ph), colour, now, lifetime, RNG, pattern);
                return false;
            }
        }

        double dx = 0, dy = 0, dz = 0;
        if (cx < 0)              { dx = -tile.ux; dy = -tile.uy; dz = -tile.uz; }
        else if (cx >= tile.pw)  { dx = tile.ux;  dy = tile.uy;  dz = tile.uz; }
        else if (cy < 0)         { dx = -tile.vx; dy = -tile.vy; dz = -tile.vz; }
        else if (cy >= tile.ph)  { dx = tile.vx;  dy = tile.vy;  dz = tile.vz; }
        else return false;
        Direction edgeDir = axisDirection(dx, dy, dz);
        int ex = clampPixel(cx, tile.pw), ey = clampPixel(cy, tile.ph);
        Vec3 edge = tile.pixelCenter(ex, ey).add(dx * (0.5 / tile.res), dy * (0.5 / tile.res), dz * (0.5 / tile.res));

        if (face == Direction.UP && s.wallsAndCeilings && edgeDir.getAxis().isHorizontal()
                && openBeyond(level, tile, edgeDir)) {
            CanvasTile side = tileAt(level, tile.pos, edgeDir, edge);
            if (side != null) {
                return pourOver(tile, ex, ey, side, clampPixel(side.pixelX(edge), side.pw), colour, depth);
            }
        }

        if (face.getAxis().isHorizontal() && edgeDir == Direction.DOWN) {
            flowOffWallBottom(level, tile, ex, 1, colour, 1, depth, droppedOff);
            return false;
        }

        if (face == Direction.UP && !droppedOff[0]) {
            droppedOff[0] = true;
            BloodParticle.spawnRunoff(level, edge.x, edge.y - 0.02, edge.z, colour);
        }
        return false;
    }

    private static boolean pourOver(CanvasTile tile, int px, int py, CanvasTile side, int column, int colour, int depth) {
        RunKey key = new RunKey(side.key, column);
        Integer until = RUN_COOLDOWN.get(key);
        if (until != null && until > now) return false;
        CanvasTile.Body body = CanvasTile.body(tile, px, py, FLOW, BODY_TILES);
        int mass = Math.max(1, body.mass());
        int pouring = 0;
        for (Run run : RUNS) if (run.source != null && body.contains(run.source)) pouring++;
        if (pouring > 0 && mass < Math.max(1, POUR_GATE * tile.pw * tile.ph / 256) * (pouring + 1)) {
            RUN_COOLDOWN.put(key, now + 20);
            return false;
        }
        int budget = Math.max(2, Math.min(3 * tile.res, mass / 2));
        return startRun(side, column, 0, budget, 1, colour, depth, tile, px, py);
    }

    private static final int POUR_GATE = 32;
    private static final int BODY_TILES = 6;

    private static boolean exposed(ClientLevel level, BlockPos pos, Direction dir) {
        BlockPos npos = pos.relative(dir);
        BlockState ns = level.getBlockState(npos);
        VoxelShape shape = ns.getShape(level, npos);
        return shape.isEmpty() || BlockToneSampler.isCrossModel(ns) || !Block.isFaceFull(shape, dir.getOpposite());
    }

    private static boolean inFluid(ClientLevel level, BlockPos pos) {
        return !level.getFluidState(pos).isEmpty();
    }

    private static int sideExposure(ClientLevel level, BlockPos pos) {
        int mask = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (exposed(level, pos, d)) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    private static void fogAt(ClientLevel level, Vec3 p, int colour) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null || !cfg.underwaterFogEnabled()) return;
        com.bloodmod.particle.BloodFogParticle.setCurrentBloodColor(new com.bloodmod.BloodColor.Color(
                ((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f));
        Minecraft.getInstance().particleEngine.createParticle(com.bloodmod.BloodParticles.BLOOD_FOG,
                p.x, p.y, p.z, 0, -0.01, 0);
    }

    private static void runOffNewEdges(ClientLevel level, CanvasTile tile, int newlyOpen) {
        BloodModConfig.SurfaceSettings s = settings();
        boolean[] dropped = {false};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if ((newlyOpen & (1 << d.ordinal())) == 0) continue;
            double du = d.getStepX() * tile.ux + d.getStepZ() * tile.uz;
            double dv = d.getStepX() * tile.vx + d.getStepZ() * tile.vz;
            int started = 0;
            if (Math.abs(du) > 0.5) {
                int x = du > 0 ? tile.pw - 1 : 0, ox = du > 0 ? tile.pw : -1;
                for (int y = 0; y < tile.ph && started < 4; y += 2) {
                    if (tile.filled(x, y) && spillCell(level, tile, ox, y, tile.colourAt(x, y), s, dropped, 0)) started++;
                }
            } else if (Math.abs(dv) > 0.5) {
                int y = dv > 0 ? tile.ph - 1 : 0, oy = dv > 0 ? tile.ph : -1;
                for (int x = 0; x < tile.pw && started < 4; x += 2) {
                    if (tile.filled(x, y) && spillCell(level, tile, x, oy, tile.colourAt(x, y), s, dropped, 0)) started++;
                }
            }
        }
    }

    private static Direction axisDirection(double dx, double dy, double dz) {
        if (dx > 0.5) return Direction.EAST;
        if (dx < -0.5) return Direction.WEST;
        if (dy > 0.5) return Direction.UP;
        if (dy < -0.5) return Direction.DOWN;
        if (dz > 0.5) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static double planeCoord(CanvasTile t, Direction face) {
        return switch (face.getAxis()) {
            case X -> t.ox;
            case Y -> t.oy;
            case Z -> t.oz;
        };
    }

    private static int clampPixel(int v, int max) {
        return v < 0 ? 0 : Math.min(max - 1, v);
    }

    private static final class Run {
        final CanvasTile tile;
        final int column;
        int volume;
        final int colour;
        final int interval;
        final int depth;
        int row;
        int remaining;
        int nextTick;
        int travelled;
        CanvasTile source;
        int sourceX, sourceY;
        int waterRow = -1;
        Hit floor;

        Run(CanvasTile tile, int column, int row, int remaining, int volume, int colour, int depth,
            CanvasTile source, int sourceX, int sourceY) {
            this.tile = tile;
            this.column = column;
            this.row = row;
            this.remaining = remaining;
            this.volume = volume;
            this.colour = colour;
            this.depth = depth;
            this.source = source;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.interval = Math.max(2, (6 + RNG.nextInt(4)) * 8 / tile.res);
            this.nextTick = now + interval;
        }
    }

    private record RunKey(CanvasTile.Key tile, int column) {}

    private static final List<Run> RUNS = new ArrayList<>();
    private static final Map<RunKey, Integer> RUN_COOLDOWN = new HashMap<>();

    private static boolean startRun(CanvasTile side, int column, int budget, int volume, int colour, int depth) {
        return startRun(side, column, 0, budget, volume, colour, depth);
    }

    private static boolean startRun(CanvasTile side, int column, int startRow, int budget, int volume, int colour, int depth) {
        return startRun(side, column, startRow, budget, volume, colour, depth, null, 0, 0);
    }

    private static boolean startRun(CanvasTile side, int column, int startRow, int budget, int volume, int colour, int depth,
                                    CanvasTile source, int sourceX, int sourceY) {
        if (depth > MAX_FLOW_DEPTH || budget <= 0 || startRow >= side.ph) return false;
        RunKey key = new RunKey(side.key, column);
        Integer until = RUN_COOLDOWN.get(key);
        if (until != null && until > now) return false;
        RUN_COOLDOWN.put(key, now + 80);

        BloodModConfig.SurfaceSettings s = settings();
        side.paintThin(column, startRow, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), 1);
        if (budget > 1) {
            Run run = new Run(side, column, startRow + 1, budget - 1, volume, colour, depth, source, sourceX, sourceY);
            run.floor = floorAhead(side, column, startRow);
            RUNS.add(run);
        }
        return true;
    }

    private static Hit floorAhead(CanvasTile wall, int column, int row) {
        ClientLevel level = trackedLevel;
        if (level == null || wall.custom || !wall.face.getAxis().isHorizontal()) return null;
        Direction face = wall.face;
        Vec3 start = wall.pixelCenter(column, row).add(face.getStepX() * 0.02, 0.5 / wall.res, face.getStepZ() * 0.02);
        double bottom = worldBounds(wall).minY;
        Hit hit = trace(level, start, new Vec3(start.x, bottom + 0.005, start.z));
        if (hit == null || hit.face() != Direction.UP || hit.point().y < bottom + 0.02) return null;
        if (BlockToneSampler.isCrossModel(level.getBlockState(hit.pos()))) return null;
        return hit;
    }

    private static void advanceRuns(ClientLevel level) {
        if (RUNS.isEmpty()) return;
        BloodModConfig.SurfaceSettings s = settings();
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        List<Run> snapshot = new ArrayList<>(RUNS);
        for (Run run : snapshot) {
            if (now < run.nextTick) continue;
            if (TILES.get(run.tile.key) != run.tile || run.remaining <= 0) {
                RUNS.remove(run);
                continue;
            }
            if (run.source != null) {
                if (TILES.get(run.source.key) != run.source
                        || !CanvasTile.body(run.source, run.sourceX, run.sourceY, FLOW, BODY_TILES)
                                .draw(run.source.pixelCenter(run.sourceX, run.sourceY), RNG)) {
                    run.source = null;
                    run.remaining = Math.min(run.remaining, Math.max(1, run.tile.res / 4));
                } else if ((run.travelled & 1) == 1) {
                    run.volume++;
                }
            }
            if (run.waterRow >= 0 && run.row > run.waterRow) {
                RUNS.remove(run);
                Vec3 at = run.tile.pixelCenter(run.column, Math.min(run.tile.ph - 1, run.waterRow));
                fogAt(level, at.add(run.tile.face.getStepX() * 0.1, -0.05, run.tile.face.getStepZ() * 0.1), run.colour);
                continue;
            }
            if (run.floor != null && run.row < run.tile.ph
                    && run.tile.pixelCenter(run.column, run.row).y - 0.5 / run.tile.res < run.floor.point().y - 1.0e-4) {
                RUNS.remove(run);
                CanvasTile floor = tileAt(level, run.floor.pos(), Direction.UP, run.floor.point());
                if (floor != null && Math.abs(floor.oy - run.floor.point().y) < 0.05 && run.depth + 1 <= MAX_FLOW_DEPTH) {
                    stampAt(level, floor, run.floor.point(), run.colour, Math.max(1, run.volume), run.depth + 1);
                }
                continue;
            }
            if (run.row < run.tile.ph) {
                int thin = 1 + Math.min(2, run.travelled / (run.tile.res / 2));
                run.tile.paintThin(run.column, run.row, run.colour, now, lifetime, RNG, pattern, thin);
                run.row++;
                run.travelled++;
                run.remaining--;
                run.nextTick = now + run.interval;
                if (run.remaining <= 0) RUNS.remove(run);
            } else {
                RUNS.remove(run);
                boolean[] dropped = {false};
                flowOffWallBottom(level, run.tile, run.column, run.remaining, run.colour, run.volume, run.depth, dropped, run);
            }
        }
        if ((now & 255) == 0) {
            RUN_COOLDOWN.values().removeIf(t -> t < now);
        }
    }

    private static void flowOffWallBottom(ClientLevel level, CanvasTile wall, int column, int remaining,
                                          int colour, int volume, int depth, boolean[] dropped) {
        flowOffWallBottom(level, wall, column, remaining, colour, volume, depth, dropped, null);
    }

    private static void flowOffWallBottom(ClientLevel level, CanvasTile wall, int column, int remaining,
                                          int colour, int volume, int depth, boolean[] dropped, Run run) {
        Direction face = wall.face;
        Vec3 bottom = wall.pixelCenter(column, wall.ph - 1).add(0, -0.5 / wall.res, 0);

        if (wall.custom) {
            CanvasTile floor = tileAt(level, wall.pos.below(), Direction.UP, bottom);
            if (floor != null && Math.abs(floor.oy - bottom.y) < 0.05 && depth + 1 <= MAX_FLOW_DEPTH) {
                stampAt(level, floor, bottom, colour, Math.max(1, volume), depth + 1);
            } else if (!dropped[0]) {
                dropped[0] = true;
                BloodParticle.spawnDroplet(level, bottom.x, bottom.y - 0.05, bottom.z, colour, Math.max(1, volume), Double.NaN);
            }
            return;
        }

        BlockPos inFront = planeAtBoundary(wall) ? wall.pos.relative(face) : wall.pos;
        if (inFluid(level, inFront)) {
            fogAt(level, bottom.add(face.getStepX() * 0.1, -0.05, face.getStepZ() * 0.1), colour);
            return;
        }
        boolean waterBelow = inFluid(level, inFront.below());

        Vec3 foot = bottom.add(face.getStepX() * 0.04, 0, face.getStepZ() * 0.04);
        Hit ground = trace(level, foot.add(0, 0.06, 0), foot.add(0, -0.08, 0));
        if (ground != null && ground.face() == Direction.UP && Math.abs(ground.point().y - bottom.y) < 0.05) {
            CanvasTile floor = tileAt(level, ground.pos(), Direction.UP, ground.point());
            if (floor != null && Math.abs(floor.oy - bottom.y) < 0.05) {
                if (depth + 1 <= MAX_FLOW_DEPTH) {
                    stampAt(level, floor, ground.point(), colour, Math.max(1, volume), depth + 1);
                }
                return;
            }
        }

        if (remaining > 0 || waterBelow) {
            BlockPos below = wall.pos.below();
            Vec3 probe = bottom.add(0, -0.02, 0);
            if (!planeAtBoundary(wall) || exposed(level, below, face)) {
                CanvasTile lower = tileAt(level, below, face, probe);
                if (lower != null && Math.abs(planeCoord(lower, face) - planeCoord(wall, face)) < 1.0e-3) {
                    int col = clampPixel(lower.pixelX(probe), lower.pw);
                    int budget = remaining;
                    int waterRow = -1;
                    if (waterBelow) {
                        BlockPos water = inFront.below();
                        double surface = water.getY() + level.getFluidState(water).getHeight(level, water);
                        waterRow = (int) Math.floor((lower.oy - surface) * lower.res);
                        if (waterRow < 0) {
                            fogAt(level, bottom.add(face.getStepX() * 0.1, -0.05, face.getStepZ() * 0.1), colour);
                            return;
                        }
                        budget = Math.max(remaining, waterRow + 2);
                    }
                    boolean fed = run != null && run.source != null;
                    if (startRun(lower, col, 0, budget, volume, colour, depth,
                            fed ? run.source : null, fed ? run.sourceX : 0, fed ? run.sourceY : 0)) {
                        if (waterRow >= 0) for (Run r : RUNS) if (r.tile == lower && r.column == col) r.waterRow = waterRow;
                        return;
                    }
                }
            }
            if (waterBelow) {
                fogAt(level, bottom.add(face.getStepX() * 0.1, -0.05, face.getStepZ() * 0.1), colour);
                return;
            }
        }

        if (!edgeAtBoundary(wall, Direction.DOWN) || exposed(level, wall.pos, Direction.DOWN)) {
            CanvasTile under = tileAt(level, wall.pos, Direction.DOWN, bottom);
            if (under != null && Math.abs(under.oy - bottom.y) < 0.05) {
                BloodModConfig.SurfaceSettings s = settings();
                boolean pattern = !"block".equals(s.highlightMode);
                double step = 1.0 / under.res;
                for (int i = 0; i < 2; i++) {
                    Vec3 p = bottom.subtract(face.getStepX() * step * (i + 0.5), 0, face.getStepZ() * step * (i + 0.5));
                    under.paint(clampPixel(under.pixelX(p), under.pw), clampPixel(under.pixelY(p), under.ph),
                            colour, now, s.lifetimeSeconds * 20, RNG, pattern);
                }
                bottom = bottom.subtract(face.getStepX() * step, 0, face.getStepZ() * step);
            }
        }
        if (!dropped[0]) {
            dropped[0] = true;
            BloodParticle.spawnDroplet(level, bottom.x, bottom.y - 0.05, bottom.z, colour, Math.max(1, volume), Double.NaN);
        }
    }

    private static List<CanvasTile> crossTiles(ClientLevel level, BlockPos pos, BlockState state) {
        BloodModConfig.SurfaceSettings s = settings();
        int res = s.resolution == 16 ? 16 : 8;
        List<BlockToneSampler.QuadInfo> quads = BlockToneSampler.unculledQuads(state);
        List<CanvasTile> tiles = new ArrayList<>(quads.size());
        Vec3 offset = modelOffset(level, pos, state);
        for (int q = 0; q < quads.size() && q < 8; q++) {
            CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), Direction.UP, 100 + q);
            CanvasTile tile = TILES.get(key);
            if (tile != null && tile.state != state) { remove(tile); tile = null; }
            if (tile == null) {
                tile = quadTile(key, pos, state, quads.get(q), res, offset);
                if (tile == null) continue;
                if (TILES.size() >= s.maxTiles) evictOldest();
                TILES.put(key, tile);
            }
            tiles.add(tile);
        }
        for (CanvasTile a : tiles) {
            if (a.twin != null) continue;
            for (CanvasTile b : tiles) {
                if (a == b || b.twin != null) continue;
                double dot = a.nx * b.nx + a.ny * b.ny + a.nz * b.nz;
                if (dot < -0.98 && a.center().distanceToSqr(b.center()) < 1.0e-4) {
                    a.twin = b;
                    b.twin = a;
                    break;
                }
            }
        }
        return tiles;
    }

    private static Vec3 modelOffset(ClientLevel level, BlockPos pos, BlockState state) {
        try {
            //? if 1.21.1 {
            /*return state.getOffset(level, pos);
            *///?} else {
            return state.getOffset(pos);
            //?}
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    private static CanvasTile quadTile(CanvasTile.Key key, BlockPos pos, BlockState state,
                                       BlockToneSampler.QuadInfo quad, int res, Vec3 offset) {
        int o = -1, ue = -1, ve = -1;
        float minU = 1e9f, minV = 1e9f, maxU = -1e9f, maxV = -1e9f;
        for (int i = 0; i < 4; i++) {
            minU = Math.min(minU, quad.u()[i]); maxU = Math.max(maxU, quad.u()[i]);
            minV = Math.min(minV, quad.v()[i]); maxV = Math.max(maxV, quad.v()[i]);
        }
        if (maxU - minU < 1e-4 || maxV - minV < 1e-4) return null;
        for (int i = 0; i < 4; i++) {
            boolean uMin = Math.abs(quad.u()[i] - minU) < 1e-4, vMin = Math.abs(quad.v()[i] - minV) < 1e-4;
            boolean uMax = Math.abs(quad.u()[i] - maxU) < 1e-4, vMax = Math.abs(quad.v()[i] - maxV) < 1e-4;
            if (uMin && vMin) o = i;
            else if (uMax && vMin) ue = i;
            else if (uMin && vMax) ve = i;
        }
        if (o < 0 || ue < 0 || ve < 0) return null;
        Vec3 origin = quad.corners()[o].add(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
        Vec3 uVec = quad.corners()[ue].subtract(quad.corners()[o]);
        Vec3 vVec = quad.corners()[ve].subtract(quad.corners()[o]);
        double w = uVec.length(), h = vVec.length();
        if (w < 1e-4 || h < 1e-4) return null;
        Vec3 u = uVec.scale(1.0 / w), v = vVec.scale(1.0 / h);
        int[] size = BlockToneSampler.spriteSize(quad.sprite());
        int pw = Math.max(1, Math.round((maxU - minU) * size[0] * res / 16.0f));
        int ph = Math.max(1, Math.round((maxV - minV) * size[1] * res / 16.0f));
        pw = Math.min(BloodCanvasAtlas.TILE, pw);
        ph = Math.min(BloodCanvasAtlas.TILE, ph);
        boolean[] mask = BlockToneSampler.alphaMask(quad.sprite(), minU, maxU, minV, maxV, pw, ph);
        CanvasTile tile = new CanvasTile(key, pos, state, origin, u, v, w, h, res, now, mask, pw, ph);
        Vec3 c0 = quad.corners()[0], c1 = quad.corners()[1], c2 = quad.corners()[2];
        Vec3 geom = c1.subtract(c0).cross(c2.subtract(c0));
        if (geom.lengthSqr() > 1e-8 && geom.dot(new Vec3(tile.nx, tile.ny, tile.nz)) < 0) {
            tile.flipNormal();
        }
        return tile;
    }

    private static List<CanvasTile> signTiles(ClientLevel level, BlockPos pos, BlockState state) {
        BloodModConfig.SurfaceSettings s = settings();
        int res = s.resolution == 16 ? 16 : 8;
        List<SignFaces.Rect> rects = SignFaces.rects(state);
        List<CanvasTile> tiles = new ArrayList<>(rects.size());
        for (int i = 0; i < rects.size(); i++) {
            CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), Direction.UP, 300 + i);
            CanvasTile tile = TILES.get(key);
            if (tile != null && tile.state != state) { remove(tile); tile = null; }
            if (tile == null) {
                SignFaces.Rect r = rects.get(i);
                Vec3 origin = r.origin().add(pos.getX(), pos.getY(), pos.getZ());
                tile = new CanvasTile(key, pos, state, origin, r.u(), r.v(), r.w(), r.h(), res, now, null);
                if (r.n().x * tile.nx + r.n().y * tile.ny + r.n().z * tile.nz < 0) tile.flipNormal();
                if (r.pivot() != null) {
                    tile.swayPivot = r.pivot().add(pos.getX(), pos.getY(), pos.getZ());
                    tile.swayAxis = r.axis();
                }
                if (TILES.size() >= s.maxTiles) evictOldest();
                TILES.put(key, tile);
            }
            tiles.add(tile);
        }
        return tiles;
    }

    private static CanvasTile nearestPlane(List<CanvasTile> tiles, Vec3 point, Direction face) {
        CanvasTile best = null;
        double bestDist = Double.MAX_VALUE;
        for (CanvasTile t : tiles) {
            if (face != null && axisDirection(t.nx, t.ny, t.nz) != face) continue;
            Vec3 c = t.center();
            double d = Math.abs((point.x - c.x) * t.nx + (point.y - c.y) * t.ny + (point.z - c.z) * t.nz) * 4;
            double facing = (point.x - c.x) * t.nx + (point.y - c.y) * t.ny + (point.z - c.z) * t.nz;
            if (facing < 0) d += 0.01;
            double a = (point.x - t.ox) * t.ux + (point.y - t.oy) * t.uy + (point.z - t.oz) * t.uz;
            double b = (point.x - t.ox) * t.vx + (point.y - t.oy) * t.vy + (point.z - t.oz) * t.vz;
            d += Math.max(0, Math.max(-a, a - t.w)) + Math.max(0, Math.max(-b, b - t.h));
            if (d < bestDist) { bestDist = d; best = t; }
        }
        if (best == null && face != null) return nearestPlane(tiles, point, null);
        return best;
    }

    public static void coatDripstone(ClientLevel level, BlockPos pos, BlockState state, int colour, float amount) {
        BloodModConfig.SurfaceSettings s = settings();
        int res = s.resolution == 16 ? 16 : 8;
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        for (CanvasTile tile : crossTiles(level, pos, state)) {
            if (tile.twin != null && tile.twin.hashCode() < tile.hashCode()) continue;
            int px = tile.pw / 2 + RNG.nextInt(3) - 1;
            int py = Math.max(1, (int) (tile.ph * (0.15 + RNG.nextDouble() * 0.2)));
            int pixels = Math.max(3, Math.round((6 + amount * 18) * (res * res / 256.0f)));
            tile.stamp(px, py, pixels, colour, now, lifetime, RNG, pattern, null);
            for (int r = 0; r < 2; r++) {
                int col = clampPixel(px + RNG.nextInt(5) - 2, tile.pw);
                startRun(tile, col, Math.min(tile.ph - 1, py + 1), (int) (tile.ph * (0.4 + amount * 0.6)), 1 + Math.round(amount * 2), colour, 0);
            }
        }
    }

    private static void dripFromCeiling(ClientLevel level, CanvasTile tile) {
        if (tile.face != Direction.DOWN || tile.isEmpty()) return;
        if (RNG.nextDouble() > Math.min(0.5, tile.count() / 100.0)) return;
        int[] unit = tile.releaseUnit(RNG);
        if (unit == null) return;
        Vec3 p = tile.pixelCenter(unit[0], unit[1]);
        BloodParticle.spawnDroplet(level, p.x, p.y - 0.03, p.z, unit[2], 1, Double.NaN);
    }

    private static CanvasTile tileAt(ClientLevel level, BlockPos pos, Direction face, Vec3 point) {
        return tileAt(level, pos, face, point, true);
    }

    private static CanvasTile tileAt(ClientLevel level, BlockPos pos, Direction face, Vec3 point, boolean create) {
        BlockState state = level.getBlockState(pos);
        if (isHandDrawn(state)) {
            if (!create) return null;
            return nearestPlane(signTiles(level, pos, state), point, face);
        }
        if (isCrossModel(state)) {
            if (!create) return null;
            return nearestPlane(crossTiles(level, pos, state), point, null);
        }
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;
        ModelFaces faces = modelFaces(state, shape);
        if (faces != null) return modelTile(level, pos, state, faces, face, point, create);
        List<AABB> boxes = shape.toAabbs();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        double lx = point.x - pos.getX(), ly = point.y - pos.getY(), lz = point.z - pos.getZ();
        for (int i = 0; i < boxes.size(); i++) {
            AABB b = boxes.get(i);
            double plane = switch (face) {
                case UP -> Math.abs(ly - b.maxY);
                case DOWN -> Math.abs(ly - b.minY);
                case NORTH -> Math.abs(lz - b.minZ);
                case SOUTH -> Math.abs(lz - b.maxZ);
                case WEST -> Math.abs(lx - b.minX);
                default -> Math.abs(lx - b.maxX);
            };
            double outside = 0;
            if (face.getAxis() != Direction.Axis.X) outside += Math.max(0, Math.max(b.minX - lx, lx - b.maxX));
            if (face.getAxis() != Direction.Axis.Y) outside += Math.max(0, Math.max(b.minY - ly, ly - b.maxY));
            if (face.getAxis() != Direction.Axis.Z) outside += Math.max(0, Math.max(b.minZ - lz, lz - b.maxZ));
            double d = plane * 4 + outside;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        if (best < 0 || bestDist > 0.35) return null;

        CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), face, best);
        CanvasTile tile = TILES.get(key);
        if (tile != null && tile.state != state) {
            if (faceStillExists(level, tile, state)) {
                tile.rebind(state);
            } else {
                releaseAsDroplets(level, tile);
                remove(tile);
                tile = null;
            }
        }
        if (tile == null && !create) return null;
        if (tile == null) {
            BloodModConfig.SurfaceSettings s = settings();
            if (TILES.size() >= s.maxTiles) evictOldest();
            tile = new CanvasTile(key, pos, face, state, boxes.get(best), s.resolution == 16 ? 16 : 8, now);
            if ("block".equals(s.highlightMode)) {
                tile.setBaseTones(BlockToneSampler.sample(tile, RNG));
            }
            if (face == Direction.UP) tile.exposedMask = sideExposure(level, pos) | insetEdges(tile);
            TILES.put(key, tile);
        }
        return tile;
    }

    private static boolean faceStillExists(ClientLevel level, CanvasTile tile, BlockState state) {
        if (tile.custom) return false;
        if (isQuadTile(tile)) {
            VoxelShape shape = state.getShape(level, tile.pos);
            ModelFaces faces = shape.isEmpty() ? null : modelFaces(state, shape);
            if (faces == null) return false;
            AABB have = worldBounds(tile).move(-tile.pos.getX(), -tile.pos.getY(), -tile.pos.getZ());
            return faces.face(tile.face, have) != null;
        }
        VoxelShape shape = state.getShape(level, tile.pos);
        if (shape.isEmpty()) return false;
        Vec3 c = tile.center();
        double lx = c.x - tile.pos.getX(), ly = c.y - tile.pos.getY(), lz = c.z - tile.pos.getZ();
        for (AABB b : shape.toAabbs()) {
            double plane = switch (tile.face) {
                case UP -> Math.abs(ly - b.maxY);
                case DOWN -> Math.abs(ly - b.minY);
                case NORTH -> Math.abs(lz - b.minZ);
                case SOUTH -> Math.abs(lz - b.maxZ);
                case WEST -> Math.abs(lx - b.minX);
                default -> Math.abs(lx - b.maxX);
            };
            if (plane > 1.0e-3) continue;
            boolean inU = tile.face.getAxis() == Direction.Axis.X || (lx >= b.minX - 1e-6 && lx <= b.maxX + 1e-6);
            boolean inV = tile.face.getAxis() == Direction.Axis.Y || (ly >= b.minY - 1e-6 && ly <= b.maxY + 1e-6);
            boolean inW = tile.face.getAxis() == Direction.Axis.Z || (lz >= b.minZ - 1e-6 && lz <= b.maxZ + 1e-6);
            if (inU && inV && inW) return true;
        }
        return false;
    }

    private static final int QUAD_KEY_BASE = 200;

    private static CanvasTile modelTile(ClientLevel level, BlockPos pos, BlockState state,
                                        ModelFaces faces, Direction face, Vec3 point, boolean create) {
        Vec3 lp = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        ModelFaces.Slanted tilted = null;
        double tiltedScore = Double.MAX_VALUE;
        for (ModelFaces.Slanted s : faces.slanted) {
            if (ModelFaces.facing(s.n()) != face) continue;
            double score = s.score(lp);
            if (score < tiltedScore) { tiltedScore = score; tilted = s; }
        }
        double[] flatScore = {Double.MAX_VALUE};
        AABB flat = bestFlatFace(faces, face, lp, flatScore);
        if (tilted != null && tiltedScore <= 0.35 && tiltedScore < flatScore[0]) {
            return slantedTile(level, pos, state, tilted, create);
        }
        if (flat == null || flatScore[0] > 0.35) return null;
        return quadFaceTile(level, pos, state, face, flat, create);
    }

    private static AABB bestFlatFace(ModelFaces faces, Direction face, Vec3 lp, double[] score) {
        double bestScore = Double.MAX_VALUE;
        AABB bestBox = null;
        double lx = lp.x, ly = lp.y, lz = lp.z;
        for (ModelFaces.Face f : faces.faces) {
            if (f.dir() != face) continue;
            AABB b = f.box();
            double plane = switch (face.getAxis()) {
                case X -> Math.abs(lx - b.minX);
                case Y -> Math.abs(ly - b.minY);
                case Z -> Math.abs(lz - b.minZ);
            };
            double outside = 0;
            if (face.getAxis() != Direction.Axis.X) outside += Math.max(0, Math.max(b.minX - lx, lx - b.maxX));
            if (face.getAxis() != Direction.Axis.Y) outside += Math.max(0, Math.max(b.minY - ly, ly - b.maxY));
            if (face.getAxis() != Direction.Axis.Z) outside += Math.max(0, Math.max(b.minZ - lz, lz - b.maxZ));
            double d = plane * 4 + outside;
            if (d < bestScore) { bestScore = d; bestBox = b; }
        }
        score[0] = bestScore;
        return bestBox;
    }

    private static final int SLANTED_KEY_BASE = 400;

    private static CanvasTile slantedTile(ClientLevel level, BlockPos pos, BlockState state, ModelFaces.Slanted s, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), Direction.UP, SLANTED_KEY_BASE + s.index());
        CanvasTile tile = TILES.get(key);
        if (tile != null && tile.state != state) { remove(tile); tile = null; }
        if (tile == null && !create) return null;
        if (tile == null) {
            List<BlockToneSampler.QuadInfo> quads = BlockToneSampler.allQuads(state);
            if (s.index() >= quads.size()) return null;
            BloodModConfig.SurfaceSettings cfg = settings();
            tile = quadTile(key, pos, state, quads.get(s.index()), cfg.resolution == 16 ? 16 : 8, Vec3.ZERO);
            if (tile == null) return null;
            if (TILES.size() >= cfg.maxTiles) evictOldest();
            TILES.put(key, tile);
        }
        return tile;
    }

    private static CanvasTile quadFaceTile(ClientLevel level, BlockPos pos, BlockState state,
                                           Direction face, AABB bestBox, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), face, quadKeyBox(face, bestBox));
        CanvasTile tile = TILES.get(key);
        if (tile != null && tile.state != state) {
            if (faceStillExists(level, tile, state)) {
                tile.rebind(state);
            } else {
                releaseAsDroplets(level, tile);
                remove(tile);
                tile = null;
            }
        }
        if (tile == null && !create) return null;
        if (tile == null) {
            BloodModConfig.SurfaceSettings s = settings();
            if (TILES.size() >= s.maxTiles) evictOldest();
            tile = new CanvasTile(key, pos, face, state, bestBox, s.resolution == 16 ? 16 : 8, now);
            if ("block".equals(s.highlightMode)) {
                tile.setBaseTones(BlockToneSampler.sample(tile, RNG));
            }
            if (face == Direction.UP) tile.exposedMask = sideExposure(level, pos) | insetEdges(tile);
            TILES.put(key, tile);
        }
        return tile;
    }

    private static boolean isQuadTile(CanvasTile t) {
        return !t.custom && t.key.box() >= QUAD_KEY_BASE;
    }

    private static int quadKeyBox(Direction face, AABB b) {
        int plane, a0, a1, b0, b1;
        switch (face.getAxis()) {
            case X -> { plane = q32(b.minX); a0 = q32(b.minY); a1 = q32(b.maxY); b0 = q32(b.minZ); b1 = q32(b.maxZ); }
            case Y -> { plane = q32(b.minY); a0 = q32(b.minX); a1 = q32(b.maxX); b0 = q32(b.minZ); b1 = q32(b.maxZ); }
            default -> { plane = q32(b.minZ); a0 = q32(b.minX); a1 = q32(b.maxX); b0 = q32(b.minY); b1 = q32(b.maxY); }
        }
        return QUAD_KEY_BASE + (((plane * 33 + a0) * 33 + a1) * 33 + b0) * 33 + b1;
    }

    private static int q32(double v) {
        return Math.max(0, Math.min(32, (int) Math.round(v * 32)));
    }

    private static boolean hiddenInBlock(CanvasTile t, Vec3 p) {
        Direction f = t.face;
        return visibleSolidAt(trackedLevel, p.x + f.getStepX() * 0.01, p.y + f.getStepY() * 0.01, p.z + f.getStepZ() * 0.01);
    }

    private static AABB worldBounds(CanvasTile t) {
        double ax = t.ox + t.ux * t.w, ay = t.oy + t.uy * t.w, az = t.oz + t.uz * t.w;
        double bx = t.ox + t.vx * t.h, by = t.oy + t.vy * t.h, bz = t.oz + t.vz * t.h;
        double cx = ax + t.vx * t.h, cy = ay + t.vy * t.h, cz = az + t.vz * t.h;
        return new AABB(Math.min(Math.min(t.ox, ax), Math.min(bx, cx)), Math.min(Math.min(t.oy, ay), Math.min(by, cy)), Math.min(Math.min(t.oz, az), Math.min(bz, cz)),
                Math.max(Math.max(t.ox, ax), Math.max(bx, cx)), Math.max(Math.max(t.oy, ay), Math.max(by, cy)), Math.max(Math.max(t.oz, az), Math.max(bz, cz)));
    }

    private static boolean planeAtBoundary(CanvasTile t) {
        double plane = planeCoord(t, t.face);
        double edge = switch (t.face) {
            case UP -> t.pos.getY() + 1; case DOWN -> t.pos.getY();
            case SOUTH -> t.pos.getZ() + 1; case NORTH -> t.pos.getZ();
            case EAST -> t.pos.getX() + 1; default -> t.pos.getX();
        };
        return Math.abs(plane - edge) < 1.0e-3;
    }

    private static boolean edgeAtBoundary(CanvasTile t, Direction d) {
        AABB b = worldBounds(t);
        return switch (d) {
            case EAST -> Math.abs(b.maxX - (t.pos.getX() + 1)) < 1.0e-3;
            case WEST -> Math.abs(b.minX - t.pos.getX()) < 1.0e-3;
            case SOUTH -> Math.abs(b.maxZ - (t.pos.getZ() + 1)) < 1.0e-3;
            case NORTH -> Math.abs(b.minZ - t.pos.getZ()) < 1.0e-3;
            case UP -> Math.abs(b.maxY - (t.pos.getY() + 1)) < 1.0e-3;
            default -> Math.abs(b.minY - t.pos.getY()) < 1.0e-3;
        };
    }

    private static int insetEdges(CanvasTile t) {
        int mask = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!edgeAtBoundary(t, d)) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    private static boolean tileExposed(ClientLevel level, CanvasTile t) {
        return t.custom || !planeAtBoundary(t) || exposed(level, t.pos, t.face);
    }

    private static boolean openBeyond(ClientLevel level, CanvasTile t, Direction d) {
        if (!edgeAtBoundary(t, d)) return true;
        return exposed(level, t.pos, d) && !inFluid(level, t.pos.relative(d));
    }

    private record FallingShape(List<int[]> pixels, CanvasTile from, Vec3 landing, int landTick) {}

    private static final List<FallingShape> FALLING = new ArrayList<>();

    private static void releaseAsDroplets(ClientLevel level, CanvasTile tile) {
        int count = tile.count();
        if (count == 0) return;

        if (tile.face == Direction.UP && !tile.custom) {
            Vec3 c = tile.center();
            Hit below = trace(level, c.add(0, -0.01, 0), c.add(0, -MAX_FALL, 0));
            if (below != null && below.face() == Direction.UP) {
                double dist = c.y - below.point().y;
                int landTick = now + (int) (4 + 4 * Math.sqrt(Math.max(0.25, dist)));
                List<int[]> snap = tile.snapshot();
                FALLING.add(new FallingShape(snap, tile, below.point(), landTick));
                int drops = Math.min(6, Math.max(1, count / 8));
                for (int i = 0; i < drops; i++) {
                    int[] px = snap.get(RNG.nextInt(snap.size()));
                    Vec3 p = tile.pixelCenter(px[0], px[1]);
                    BloodParticle.spawnDroplet(level, p.x, p.y, p.z, px[2], 0, tile.oy - MAX_FALL);
                }
                return;
            }
        }

        int group = Math.max(1, (int) Math.ceil(count / 8.0));
        int carried = Math.min(tile.res / 2, group);
        double floorY = tile.oy - MAX_FALL;
        int seen = 0;
        for (int py = 0; py < tile.ph; py++) {
            for (int px = 0; px < tile.pw; px++) {
                if (!tile.filled(px, py)) continue;
                if (seen++ % group != 0) continue;
                Vec3 p = tile.pixelCenter(px, py);
                BloodParticle.spawnDroplet(level, p.x, p.y, p.z, tile.colourAt(px, py), carried, floorY);
            }
        }
    }

    private static void landFallingShapes(ClientLevel level) {
        if (FALLING.isEmpty()) return;
        BloodModConfig.SurfaceSettings s = settings();
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        Iterator<FallingShape> it = FALLING.iterator();
        while (it.hasNext()) {
            FallingShape f = it.next();
            if (now < f.landTick()) continue;
            it.remove();
            Vec3 c = f.from().center();
            Hit below = trace(level, c.add(0, -0.01, 0), c.add(0, -MAX_FALL, 0));
            if (below == null || below.face() != Direction.UP) continue;
            double y = below.point().y;
            CanvasTile centre = null;
            for (int[] px : f.pixels()) {
                Vec3 p = f.from().pixelCenter(px[0], px[1]);
                Vec3 at = new Vec3(p.x, y, p.z);
                CanvasTile target = tileAt(level, BlockPos.containing(at.add(0, -0.02, 0)), Direction.UP, at);
                if (target == null || Math.abs(target.oy - y) > 0.05) continue;
                int tx = clampPixel(target.pixelX(at), target.pw), ty = clampPixel(target.pixelY(at), target.ph);
                if (px.length > 4 && px[4] == 1) {
                    target.paintPowder(tx, ty, px[2], now, lifetime, RNG);
                } else {
                    for (int u = 0; u < Math.max(1, px[3]); u++) {
                        target.paint(tx, ty, px[2], now, lifetime, RNG, pattern);
                    }
                }
                if (centre == null) centre = target;
                touchNeighbours(target);
            }
            if (centre != null) {
                Vec3 at = new Vec3(c.x, y, c.z);
                centre.addRipple(clampPixel(centre.pixelX(at), centre.pw), clampPixel(centre.pixelY(at), centre.ph), now);
                splashWallsAround(level, f.pixels(), f.from(), y, s);
                com.bloodmod.BloodSounds.plop(level, at, f.pixels().size());
            }
        }
    }

    private static void splashWallsAround(ClientLevel level, List<int[]> pixels, CanvasTile from, double y,
                                          BloodModConfig.SurfaceSettings s) {
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int splashes = 0;
            for (int[] px : pixels) {
                if (splashes >= 2) break;
                if (RNG.nextInt(3) != 0) continue;
                Vec3 p = from.pixelCenter(px[0], px[1]);
                Vec3 at = new Vec3(p.x, y, p.z);
                BlockPos floorPos = BlockPos.containing(at.add(0, -0.02, 0));
                BlockPos wallPos = floorPos.relative(d).above();
                Vec3 edge = at.add(d.getStepX() * (1.0 / from.res), -0.02, d.getStepZ() * (1.0 / from.res));
                if (!BlockPos.containing(edge).equals(floorPos.relative(d))) continue;
                if (!exposed(level, wallPos, d.getOpposite())) continue;
                Vec3 wallPoint = new Vec3(
                        d.getAxis() == Direction.Axis.X ? (d.getStepX() > 0 ? wallPos.getX() : wallPos.getX() + 1) : at.x,
                        y + 0.05,
                        d.getAxis() == Direction.Axis.Z ? (d.getStepZ() > 0 ? wallPos.getZ() : wallPos.getZ() + 1) : at.z);
                CanvasTile wall = tileAt(level, wallPos, d.getOpposite(), wallPoint);
                if (wall == null) continue;
                int height = 2 + RNG.nextInt(3);
                int row = Math.max(0, wall.ph - 1 - height);
                int col = clampPixel(wall.pixelX(wallPoint), wall.pw);
                wall.paint(col, row, px[2], now, lifetime, RNG, pattern);
                if (RNG.nextBoolean()) wall.paint(clampPixel(col + (RNG.nextBoolean() ? 1 : -1), wall.pw), row, px[2], now, lifetime, RNG, pattern);
                startRun(wall, col, row + 1, height + 1, 1, px[2], 1);
                splashes++;
            }
        }
    }

    private static void touchNeighbours(CanvasTile tile) {
        if (tile.custom || tile.face != Direction.UP) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            CanvasTile n = TILES.get(new CanvasTile.Key(tile.pos.relative(d).asLong(), Direction.UP, 0));
            if (n != null && !n.isEmpty()) n.dirty = true;
        }
    }

    private static Direction edgeDirection(CanvasTile tile, int px, int py) {
        double dx, dy, dz;
        if (px < 0)              { dx = -tile.ux; dy = -tile.uy; dz = -tile.uz; }
        else if (px >= tile.pw)  { dx = tile.ux;  dy = tile.uy;  dz = tile.uz; }
        else if (py < 0)         { dx = -tile.vx; dy = -tile.vy; dz = -tile.vz; }
        else if (py >= tile.ph)  { dx = tile.vx;  dy = tile.vy;  dz = tile.vz; }
        else return null;
        return axisDirection(dx, dy, dz);
    }

    private static Vec3 edgePoint(CanvasTile tile, int px, int py, Direction edgeDir) {
        int ex = clampPixel(px, tile.pw), ey = clampPixel(py, tile.ph);
        double step = 0.5 / tile.res;
        return tile.pixelCenter(ex, ey).add(edgeDir.getStepX() * step, edgeDir.getStepY() * step, edgeDir.getStepZ() * step);
    }

    private static final CanvasTile.FlowContext FLOW = new CanvasTile.FlowContext() {
        @Override
        public boolean filledAcross(CanvasTile tile, int px, int py) {
            Vec3 p = tile.pixelCenter(px, py);
            Direction face = tile.face;
            BlockPos npos = BlockPos.containing(p.subtract(face.getStepX() * 0.02, face.getStepY() * 0.02, face.getStepZ() * 0.02));
            if (npos.equals(tile.pos)) return false;
            ClientLevel level = trackedLevel;
            CanvasTile n = level == null ? null : tileAt(level, npos, face, p, false);
            if (n == null || Math.abs(planeCoord(n, face) - planeCoord(tile, face)) > 1.0e-3) return false;
            return n.filled(n.pixelX(p), n.pixelY(p));
        }

        @Override
        public CanvasTile across(CanvasTile tile, int px, int py, int[] xy, boolean create) {
            ClientLevel level = trackedLevel;
            if (level == null) return null;
            Vec3 p = tile.pixelCenter(px, py);
            Direction face = tile.face;
            BlockPos npos = BlockPos.containing(p.subtract(face.getStepX() * 0.02, face.getStepY() * 0.02, face.getStepZ() * 0.02));
            if (npos.equals(tile.pos)) return null;
            CanvasTile n = tileAt(level, npos, face, p, create);
            if (n == null || Math.abs(planeCoord(n, face) - planeCoord(tile, face)) > 1.0e-3) return null;
            xy[0] = clampPixel(n.pixelX(p), n.pw);
            xy[1] = clampPixel(n.pixelY(p), n.ph);
            return n;
        }

        @Override
        public boolean canDrain(CanvasTile tile, int px, int py) {
            ClientLevel level = trackedLevel;
            if (level == null || tile.face != Direction.UP || !settings().wallsAndCeilings) return false;
            Direction d = edgeDirection(tile, px, py);
            if (d == null || !d.getAxis().isHorizontal()) return false;
            if ((tile.exposedMask & (1 << d.ordinal())) == 0) return false;
            return openBeyond(level, tile, d);
        }

        @Override
        public boolean drain(CanvasTile tile, int px, int py, int colour) {
            ClientLevel level = trackedLevel;
            if (level == null) return false;
            Direction d = edgeDirection(tile, px, py);
            if (d == null) return false;
            Vec3 edge = edgePoint(tile, px, py, d);
            CanvasTile side = tileAt(level, tile.pos, d, edge);
            if (side == null) return false;
            return feedRun(tile, clampPixel(px, tile.pw), clampPixel(py, tile.ph), side, clampPixel(side.pixelX(edge), side.pw), colour);
        }
    };

    private static boolean feedRun(CanvasTile tile, int px, int py, CanvasTile side, int column, int colour) {
        for (Run run : RUNS) {
            if (run.tile == side && run.column == column) {
                if (run.source != null) return false;
                run.source = tile;
                run.sourceX = px;
                run.sourceY = py;
                run.remaining += Math.max(2, side.res / 4);
                return true;
            }
        }
        return pourOver(tile, px, py, side, column, colour, 0);
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            if (!TILES.isEmpty()) clear();
            if (!PENDING.isEmpty()) {
                PENDING.clear();
                pendingCount.set(0);
            }
            return;
        }
        syncLevel(level);
        now++;
        drainPending(level);
        if (TILES.isEmpty()) return;

        BloodModConfig.SurfaceSettings s = settings();
        if (!s.enabled) {
            clear();
            return;
        }
        advanceRuns(level);
        landFallingShapes(level);
        relaxPuddles(level, s);

        int slice = now & 15;
        int i = 0;
        CanvasTile[] snapshot = TILES.values().toArray(new CanvasTile[0]);
        for (CanvasTile tile : snapshot) {
            if (TILES.get(tile.key) != tile) continue;
            if ((i++ & 15) == slice) {
                if (!level.hasChunkAt(tile.pos)) {
                    remove(tile);
                    continue;
                }
                BlockState current = level.getBlockState(tile.pos);
                if (current != tile.state) {
                    if (faceStillExists(level, tile, current)) {
                        tile.rebind(current);
                    } else {
                        releaseAsDroplets(level, tile);
                        remove(tile);
                        continue;
                    }
                }
                if (!tileExposed(level, tile)) {
                    remove(tile);
                    continue;
                }
                dripFromCeiling(level, tile);
                if (!tile.custom && tile.face == Direction.UP && !tile.isEmpty()) {
                    int mask = sideExposure(level, tile.pos) | insetEdges(tile);
                    int newlyOpen = mask & ~tile.exposedMask;
                    tile.exposedMask = mask;
                    if (newlyOpen != 0) runOffNewEdges(level, tile, newlyOpen);
                }
                if (s.rainWashes && level.isRainingAt(tile.pos.relative(tile.face))) {
                    tile.accelerateAging(16 * 6);
                }
            }
            if (((i + now) & 7) == 0 && tile.age(now, RNG)) tile.dirty = true;
            if (tile.hasRipples()) tile.dirty = true;
            if (tile.isEmpty()) {
                remove(tile);
            }
        }
        flushUploads();
    }

    private static final int RELAX_BUDGET = 48;
    private static int relaxCursor;

    private static void relaxPuddles(ClientLevel level, BloodModConfig.SurfaceSettings s) {
        if ((now & 3) != 0) return;
        CanvasTile[] all = TILES.values().toArray(new CanvasTile[0]);
        if (all.length == 0) return;
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        int done = 0;
        for (int k = 0; k < all.length && done < RELAX_BUDGET; k++) {
            CanvasTile tile = all[(relaxCursor + k) % all.length];
            if (!tile.active || tile.custom || tile.face != Direction.UP || TILES.get(tile.key) != tile) continue;
            done++;
            if (!tile.relax(FLOW, RNG, now, lifetime, pattern)) {
                tile.active = false;
            } else {
                touchNeighbours(tile);
            }
        }
        relaxCursor = (relaxCursor + Math.max(1, done)) % Math.max(1, all.length);
    }

    private static void syncLevel(ClientLevel level) {
        if (level != trackedLevel) {
            clear();
            trackedLevel = level;
        }
    }

    private static void evictOldest() {
        CanvasTile oldest = null;
        for (CanvasTile t : TILES.values()) {
            if (oldest == null || t.lastTouched < oldest.lastTouched) oldest = t;
        }
        if (oldest != null) remove(oldest);
    }

    private static void remove(CanvasTile tile) {
        TILES.remove(tile.key);
        tile.dispose(BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null);
    }

    public static void clear() {
        if (!RenderThread.on()) {
            RenderThread.run(BloodSurfaces::clear);
            return;
        }
        PENDING.clear();
        pendingCount.set(0);
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (CanvasTile t : TILES.values()) t.dispose(atlas);
        TILES.clear();
        FALLING.clear();
        RUNS.clear();
        RUN_COOLDOWN.clear();
        Footprints.clear();
        BlockToneSampler.clear();
        ModelFaces.clear();
        SignFaces.clear();
        BloodMod.LOGGER.debug("Surface blood cleared");
    }

    private static void flushUploads() {
        if (TILES.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.get();
        float opacity = settings().opacity / 100.0f;
        for (CanvasTile t : TILES.values()) {
            if (t.dirty || t.slot < 0) t.refresh(atlas, now, opacity, FLOW);
        }
    }
}
