package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.RenderThread;
import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
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
import java.util.LinkedHashMap;
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

    private static SimpleBloodConfig.SurfaceSettings settings() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null ? new SimpleBloodConfig.SurfaceSettings() : cfg.surfaces;
    }

    private static boolean detailed() {
        return settings().detailedShapes;
    }

    public static boolean isCrossModel(BlockState state) {
        return detailed() && BlockToneSampler.isCrossModel(state);
    }

    public static boolean sways(BlockState state) {
        if (!settings().skipSwayingBlocks || state.isAir()) return false;
        if (com.simpleblood.compat.InteractiveFoliage.sways(state)) return true;
        if (!com.simpleblood.compat.ShaderPacks.inUse()) return false;
        Block b = state.getBlock();
        return state.is(net.minecraft.tags.BlockTags.LEAVES)
                || b instanceof net.minecraft.world.level.block.VineBlock
                || b instanceof net.minecraft.world.level.block.GrowingPlantBlock
                || b instanceof net.minecraft.world.level.block.LanternBlock
                || b instanceof net.minecraft.world.level.block.SugarCaneBlock
                || isPlant(b)
                || BlockToneSampler.isCrossModel(state);
    }

    private static boolean isPlant(Block b) {
        //? if <=1.21.1 {
        /*return b instanceof net.minecraft.world.level.block.BushBlock;
        *///?} else {
        return b instanceof net.minecraft.world.level.block.VegetationBlock;
        //?}
    }

    public static boolean isHandDrawn(BlockState state) {
        return (detailed() && SignFaces.applies(state)) || SignFaces.lidded(state);
    }

    public static boolean hasTiltedFaces(BlockState state) {
        if (!detailed() || state.isAir() || SignFaces.applies(state) || SignFaces.lidded(state)) return false;
        try {
            VoxelShape shape = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) return false;
            ModelFaces faces = modelFaces(state, shape);
            return faces != null && !faces.slanted.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public record Hit(BlockPos pos, Direction face, Vec3 point, Vec3 onPaint, int part) {
        public Hit(BlockPos pos, Direction face, Vec3 point) {
            this(pos, face, point, point, -1);
        }

        public Hit(BlockPos pos, Direction face, Vec3 point, Vec3 onPaint) {
            this(pos, face, point, onPaint, -1);
        }
    }

    public static Hit trace(ClientLevel level, Vec3 from, Vec3 to) {
        return trace(level, from, to, false);
    }

    public static Hit traceMove(ClientLevel level, Vec3 from, Vec3 to) {
        return trace(level, from, to, true);
    }

    private static Hit trace(ClientLevel level, Vec3 from, Vec3 to, boolean moved) {
        if (com.simpleblood.Capture.active()) return com.simpleblood.Capture.trace(from, to);
        Hit hit = traceWorld(level, from, to);
        if (wheels.length > 0) hit = nearerWheel(level, from, to, hit);
        List<? extends com.simpleblood.compat.BodyPose> sable = com.simpleblood.compat.SableSubLevels.present()
                ? com.simpleblood.compat.SableSubLevels.crossing(level, from, to) : List.of();
        List<com.simpleblood.compat.BodyPose> rides = ContraptionRides.any() ? ContraptionRides.crossing(from, to) : List.of();
        if (sable.isEmpty() && rides.isEmpty()) return hit;
        double[] best = {hit == null ? Double.MAX_VALUE : hit.point().distanceToSqr(from)};
        for (com.simpleblood.compat.BodyPose pose : sable) {
            Hit h = traceIn(level, pose, from, to, moved, best);
            if (h != null) { hit = h; com.simpleblood.Tally.count("sable.hit"); }
        }
        for (com.simpleblood.compat.BodyPose pose : rides) {
            Hit h = traceIn(level, pose, from, to, moved, best);
            if (h != null) { hit = h; com.simpleblood.Tally.count("create.hit"); }
        }
        return hit;
    }

    private static volatile BlockPos[] wheels = new BlockPos[0];
    private static final double WHEEL_REACH = 3.0;
    private static final int WHEEL_SCAN_TICKS = 20, WHEEL_SCAN_CHUNKS = 3;
    private static long wheelScan = Long.MIN_VALUE;

    private static void findWheels(ClientLevel level) {
        if (!com.simpleblood.compat.CreateKinetics.present() || Minecraft.getInstance().player == null) return;
        long time = level.getGameTime();
        if (time - wheelScan < WHEEL_SCAN_TICKS && time >= wheelScan) return;
        wheelScan = time;
        BlockPos at = Minecraft.getInstance().player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (at.getX() >> 4) - WHEEL_SCAN_CHUNKS; cx <= (at.getX() >> 4) + WHEEL_SCAN_CHUNKS; cx++) {
            for (int cz = (at.getZ() >> 4) - WHEEL_SCAN_CHUNKS; cz <= (at.getZ() >> 4) + WHEEL_SCAN_CHUNKS; cz++) {
                for (net.minecraft.world.level.block.entity.BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    CreateParts.Model m = CreateParts.of(be.getBlockState());
                    if (m != null && m.multiBlock()) found.add(be.getBlockPos().immutable());
                }
            }
        }
        if (found.size() != wheels.length || !found.equals(List.of(wheels))) wheels = found.toArray(new BlockPos[0]);
    }

    private static synchronized void knowWheel(BlockPos master) {
        for (BlockPos w : wheels) if (w.equals(master)) return;
        BlockPos[] more = java.util.Arrays.copyOf(wheels, wheels.length + 1);
        more[wheels.length] = master.immutable();
        wheels = more;
    }

    private static boolean nearWheel(Vec3 from, Vec3 to) {
        for (BlockPos w : wheels) {
            if (within(Vec3.atCenterOf(w), from, to, WHEEL_REACH)) return true;
        }
        return false;
    }

    private static boolean within(Vec3 c, Vec3 from, Vec3 to, double reach) {
        Vec3 ab = to.subtract(from);
        double len = ab.lengthSqr();
        double t = len < 1.0e-12 ? 0 : Math.max(0, Math.min(1, c.subtract(from).dot(ab) / len));
        return c.distanceToSqr(from.add(ab.scale(t))) <= reach * reach;
    }

    private static Hit nearerWheel(ClientLevel level, Vec3 from, Vec3 to, Hit hit) {
        double best = hit == null ? Double.MAX_VALUE : hit.point().distanceToSqr(from);
        for (BlockPos w : wheels) {
            if (!within(Vec3.atCenterOf(w), from, to, WHEEL_REACH)) continue;
            BlockState state = blockState(level, w);
            CreateParts.Model m = CreateParts.of(state);
            if (m == null || !m.multiBlock()) continue;
            Hit h = clipPart(level, w, w, state, m, from, to);
            if (h == null || h.point().distanceToSqr(from) >= best) continue;
            best = h.point().distanceToSqr(from);
            hit = h;
        }
        return hit;
    }

    private static Hit traceIn(ClientLevel level, com.simpleblood.compat.BodyPose pose, Vec3 from, Vec3 to, boolean moved, double[] best) {
        Hit inPlot = traceWorld(level, moved ? pose.lastToPlot(from) : pose.toPlot(from), pose.toPlot(to));
        if (inPlot == null) return null;
        Vec3 seen = pose.toWorld(inPlot.point());
        double d = seen.distanceToSqr(from);
        if (d >= best[0]) return null;
        best[0] = d;
        return new Hit(inPlot.pos(), inPlot.face(), seen, inPlot.onPaint(), inPlot.part());
    }

    public static boolean movingBodiesCross(ClientLevel level, Vec3 from, Vec3 to) {
        if (ContraptionRides.any() && !ContraptionRides.crossing(from, to).isEmpty()) return true;
        if (nearWheel(from, to)) return true;
        return com.simpleblood.compat.SableSubLevels.present()
                && !com.simpleblood.compat.SableSubLevels.crossing(level, from, to).isEmpty();
    }

    private static Hit traceWorld(ClientLevel level, Vec3 from, Vec3 to) {
        double far = from.distanceToSqr(to);
        if (!(far <= 16 * 16)) return null;
        if (far < 1.0e-8) {
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
        SimpleBlood.LOGGER.warn("Simple Blood: could not read the shape of {} at {}; drops pass through blocks like it", what, pos, t);
    }

    private static Hit clipBlock(ClientLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = blockState(level, pos);
        if (!state.isAir() && com.simpleblood.compat.CreateKinetics.present()) {
            Hit spun = clipSpinning(level, pos, state, from, to);
            if (spun != null) return spun == MISSED ? null : spun;
        }
        return clipStill(level, pos, state, from, to);
    }

    private static final Hit MISSED = new Hit(BlockPos.ZERO, Direction.UP, Vec3.ZERO);

    private static Hit clipSpinning(ClientLevel level, BlockPos pos, BlockState state, Vec3 from, Vec3 to) {
        if (CreateParts.isWheelHelper(state)) {
            BlockPos master = ContraptionRides.isPlot(pos) ? null : com.simpleblood.compat.CreateKinetics.wheelMaster(level, pos, state);
            BlockState ms = master == null ? null : blockState(level, master);
            CreateParts.Model wheel = ms == null ? null : CreateParts.of(ms);
            if (wheel == null) return MISSED;
            knowWheel(master);
            Hit h = clipPart(level, pos, master, ms, wheel, from, to);
            return h == null ? MISSED : h;
        }
        CreateParts.Model model = CreateParts.of(state);
        if (model != null) {
            if (model.multiBlock() && !ContraptionRides.isPlot(pos)) knowWheel(pos);
            Hit onPart = clipPart(level, pos, pos, state, model, from, to);
            if (model.partOnly()) return onPart == null ? MISSED : onPart;
            Hit still = clipStill(level, pos, state, from, to, onPart == null);
            if (onPart == null) return still == null ? MISSED : still;
            if (still != null && still.point().distanceToSqr(from) < onPart.point().distanceToSqr(from) - 1.0e-8) return still;
            return onPart;
        }
        boolean whole = spinsWhole(state);
        com.simpleblood.compat.CreateKinetics.Part part = partForHit(level, pos, state);
        if (part == null) return null;
        double a = part.angleNow(SurfaceRenderer.partialTick());
        if (whole) {
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) return MISSED;
            Vec3 f = spun(part, from, -a), t = spun(part, to, -a);
            BlockHitResult r = shape.clip(f, t, pos);
            if (r == null || r.getType() != HitResult.Type.BLOCK) return MISSED;
            if (a != 0) com.simpleblood.Tally.count("create.spin.hit");
            return new Hit(pos, r.getDirection(), spun(part, r.getLocation(), a), r.getLocation());
        }
        Hit h = clipStill(level, pos, state, from, to);
        if (h == null) return MISSED;
        if (!part.turns(h.face(), false)) return h;
        return new Hit(h.pos(), h.face(), h.point(), spun(part, h.onPaint(), -a));
    }

    private static Hit clipPart(ClientLevel level, BlockPos cell, BlockPos master, BlockState state,
                                CreateParts.Model model, Vec3 from, Vec3 to) {
        com.simpleblood.compat.CreateKinetics.Part part = partForHit(level, master, state);
        double a = part == null ? 0 : part.angleNow(SurfaceRenderer.partialTick());
        Vec3 base = Vec3.atLowerCornerOf(master);
        Vec3 f = (part == null ? from : spun(part, from, -a)).subtract(base);
        Vec3 t = (part == null ? to : spun(part, to, -a)).subtract(base);
        double best = Double.MAX_VALUE;
        int bestFace = -1;
        List<CreateParts.Face> faces = model.faces();
        for (int i = 0; i < faces.size(); i++) {
            double h = faces.get(i).hit(f, t);
            if (h < best) {
                best = h;
                bestFace = i;
            }
        }
        if (bestFace < 0) return null;
        Vec3 rest = f.add(t.subtract(f).scale(best)).add(base);
        Vec3 seen = part == null ? rest : spun(part, rest, a);
        if (!cell.equals(master) && !BlockPos.containing(seen).equals(cell)) return null;
        Vec3 n = faces.get(bestFace).n();
        if (a != 0) com.simpleblood.Tally.count("create.part.hit");
        return new Hit(master, axisDirection(n.x, n.y, n.z), seen, rest, bestFace);
    }

    private static boolean spinsWhole(BlockState state) {
        return com.simpleblood.compat.CreateKinetics.spinsWhole(state) && CreateParts.of(state) == null;
    }

    static Vec3 spun(com.simpleblood.compat.CreateKinetics.Part part, Vec3 p, double angle) {
        if (angle == 0) return p;
        return part.centre.add(SurfaceRenderer.swing(p.subtract(part.centre), part.axis, angle));
    }

    private static com.simpleblood.compat.CreateKinetics.Part partForHit(ClientLevel level, BlockPos pos, BlockState state) {
        if (state == null || !state.hasBlockEntity()) return null;
        Object be;
        if (ContraptionRides.isPlot(pos)) be = ContraptionRides.blockEntity(pos);
        else if (RenderThread.on()) be = level.getBlockEntity(pos);
        else be = level.getChunkAt(pos).getBlockEntities().get(pos);
        return com.simpleblood.compat.CreateKinetics.part(state, be, Vec3.atCenterOf(pos), CreateParts.of(state) != null);
    }

    private static Hit clipStill(ClientLevel level, BlockPos pos, BlockState state, Vec3 from, Vec3 to) {
        return clipStill(level, pos, state, from, to, true);
    }

    private static Hit clipStill(ClientLevel level, BlockPos pos, BlockState state, Vec3 from, Vec3 to, boolean outline) {
        if (state.isAir()) {
            BlockPos under = pos.below();
            BlockState below = blockState(level, under);
            Hit h = null;
            if (isHandDrawn(below)) {
                h = SignFaces.clip(level, below, under, from, to);
            } else if (hasTiltedFaces(below)) {
                h = modelFaces(below, below.getShape(level, under)).clip(under, from, to);
            }
            if (h != null && h.point().y >= pos.getY() - 1.0e-6) return h;
            BlockPos over = pos.above();
            BlockState above = blockState(level, over);
            if (isHandDrawn(above)) {
                h = SignFaces.clip(level, above, over, from, to);
                if (h != null && h.point().y <= pos.getY() + 1 + 1.0e-6) return h;
            }
            return null;
        }
        if (sways(state)) return null;
        if (isHandDrawn(state)) return SignFaces.clip(level, state, pos, from, to);
        if (!detailed() && (SignFaces.applies(state) || BlockToneSampler.isCrossModel(state))) return null;
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;
        ModelFaces faces = modelFaces(state, shape);
        if (faces != null) {
            Hit drawn = faces.clip(pos, from, to);
            if (drawn != null) return drawn;
            if (!faces.withinOutline) return null;
        }
        if (!outline) return null;
        BlockHitResult r = shape.clip(from, to, pos);
        if (r == null || r.getType() != HitResult.Type.BLOCK || (faces != null && r.isInside())) return null;
        return new Hit(pos, r.getDirection(), r.getLocation());
    }

    private static ModelFaces modelFaces(BlockState state, VoxelShape shape) {
        if (!detailed() || BlockToneSampler.isCrossModel(state)) return null;
        if (spinsWhole(state)) return null;
        ModelFaces f = ModelFaces.of(state, shape);
        return f.usable && !f.plain ? f : null;
    }

    public static boolean visibleSolidAt(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        try {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || sways(state)) return false;
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
        if (com.simpleblood.Capture.active()) {
            if (hit != null && pixels > 0) com.simpleblood.Capture.paint(hit, pixels);
            return hit == null ? NOTHING : ONTO_SURFACE;
        }
        SimpleBloodConfig.SurfaceSettings s = settings();
        if (!s.enabled || pixels <= 0 || hit == null || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return NOTHING;
        if (!s.wallsAndCeilings && hit.face() != Direction.UP) return NOTHING;
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.distanceToSqr(hit.point()) > (double) s.renderDistance * s.renderDistance) {
            return NOTHING;
        }
        if (!RenderThread.on()) {
            depositLater(level, hit, colour, pixels, travel, powder, null);
            return ONTO_SURFACE;
        }
        if (!powder && inCauldronWater(level, hit.point().x, hit.point().y, hit.point().z)) {
            intoCauldron(level, hit.point(), colour);
            return ONTO_SURFACE;
        }
        if (!powder && isSnow(blockState(level, hit.pos()))) {
            com.simpleblood.Tally.count("snow.soak");
            pixels = Math.round(pixels * 1.4f);
        }
        syncLevel(level);

        CanvasTile tile = hit.part() >= 0 ? partTile(level, hit.pos(), hit.part(), true) : tileAt(level, hit.pos(), hit.face(), hit.onPaint());
        if (tile == null) return NOTHING;
        int result = tile.depthAt(clampPixel(tile.pixelX(hit.onPaint()), tile.pw), clampPixel(tile.pixelY(hit.onPaint()), tile.ph)) > 0
                ? INTO_PUDDLE : ONTO_SURFACE;
        if (powder) {
            int px = clampPixel(tile.pixelX(hit.onPaint()), tile.pw);
            int py = clampPixel(tile.pixelY(hit.onPaint()), tile.ph);
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
        stampAt(level, tile, hit.onPaint(), colour, pixels, 0, travel);
        return result;
    }

    public static void depositLater(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder, IntConsumer after) {
        if (hit == null || level == null || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) {
            com.simpleblood.Tally.count("drop.later.refused");
            return;
        }
        if (pendingCount.incrementAndGet() > MAX_PENDING) {
            pendingCount.decrementAndGet();
            com.simpleblood.Tally.count("drop.later.full");
            return;
        }
        PENDING.add(new Deferred(level, hit, colour, pixels, travel, powder, after));
    }

    private static void drainPending(ClientLevel level) {
        Deferred d;
        while ((d = PENDING.poll()) != null) {
            pendingCount.decrementAndGet();
            if (d.level() != level) {
                com.simpleblood.Tally.count("drop.later.otherlevel");
                continue;
            }
            int result = deposit(d.level(), d.hit(), d.colour(), d.pixels(), d.travel(), d.powder());
            com.simpleblood.Tally.count(result == NOTHING ? "drop.later.nothing" : "drop.later.painted");
            if (d.after() != null) d.after().accept(result);
        }
    }

    static CanvasTile topTile(ClientLevel level, Hit hit, boolean create) {
        if (hit.face() != Direction.UP || hit.part() >= 0) return null;
        syncLevel(level);
        return tileAt(level, hit.pos(), hit.face(), hit.onPaint(), create);
    }

    private static void stampAt(ClientLevel level, CanvasTile tile, Vec3 point, int colour, int pixels, int depth) {
        stampAt(level, tile, point, colour, pixels, depth, null);
    }

    private static void stampAt(ClientLevel level, CanvasTile tile, Vec3 point, int colour, int pixels, int depth, Vec3 travel) {
        SimpleBloodConfig.SurfaceSettings s = settings();
        int px = clampPixel(tile.pixelX(point), tile.pw);
        int py = clampPixel(tile.pixelY(point), tile.ph);
        if (tile.custom && (tile.ny < 0.99 || isPartTile(tile))) {
            int painted = tile.stamp(px, py, pixels, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), null);
            if (painted > 0 && depth == 0 && isHandDrawn(tile.state) && Math.abs(tile.ny) < 0.3 && tile.vy < -0.7) {
                runDownBoard(tile, px, py, colour, pixels);
            }
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

        if (depth == 0 && tile.face.getAxis().isHorizontal() && pixels >= 3 && RNG.nextFloat() < Math.min(1f, pixels / 8f)) {
            runDownBoard(tile, px, py, colour, pixels);
        }

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

    private static void runDownBoard(CanvasTile tile, int px, int py, int colour, int pixels) {
        int runs = pixels >= 6 ? 2 : 1;
        int budget = Math.max(2, Math.min(tile.ph, Math.round(tile.ph * (0.25f + pixels / 24f))));
        for (int r = 0; r < runs; r++) {
            int col = clampPixel(px + (runs > 1 ? (r == 0 ? -1 : 1) * (1 + RNG.nextInt(2)) : 0), tile.pw);
            int row = -1;
            for (int y = tile.ph - 1; y >= 0; y--) {
                if (tile.filled(col, y)) { row = y; break; }
            }
            if (row < 0) row = py;
            if (row + 1 < tile.ph) startRun(tile, col, row + 1, budget, 1 + pixels / 6, colour, 0);
        }
    }

    private static boolean spillCell(ClientLevel level, CanvasTile tile, int cx, int cy, int colour,
                                     SimpleBloodConfig.SurfaceSettings s, boolean[] droppedOff, int depth) {
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

        boolean outU = cx < 0 || cx >= tile.pw, outV = cy < 0 || cy >= tile.ph;
        if (!outU && !outV) return false;
        double us = cx < 0 ? -1 : 1, vs = cy < 0 ? -1 : 1;
        boolean acrossU = outU;
        if (outU && outV && face == Direction.UP
                && !openBeyond(level, tile, axisDirection(us * tile.ux, us * tile.uy, us * tile.uz))
                && openBeyond(level, tile, axisDirection(vs * tile.vx, vs * tile.vy, vs * tile.vz))) {
            acrossU = false;
        }
        double dx = acrossU ? us * tile.ux : vs * tile.vx;
        double dy = acrossU ? us * tile.uy : vs * tile.vy;
        double dz = acrossU ? us * tile.uz : vs * tile.vz;
        Direction edgeDir = axisDirection(dx, dy, dz);
        int ex = clampPixel(cx, tile.pw), ey = clampPixel(cy, tile.ph);
        Vec3 edge = tile.pixelCenter(ex, ey).add(dx * (0.5 / tile.res), dy * (0.5 / tile.res), dz * (0.5 / tile.res));

        if (tile.facesUp() && s.wallsAndCeilings && edgeDir.getAxis().isHorizontal()
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

        if (tile.facesUp() && !droppedOff[0]) {
            droppedOff[0] = true;
            letGo(level, tile, edge.add(0, -0.02, 0), colour, 0, Double.NaN);
        }
        return false;
    }

    private static boolean pourOver(CanvasTile tile, int px, int py, CanvasTile side, int column, int colour, int depth) {
        RunKey key = new RunKey(side.key, column);
        Integer until = RUN_COOLDOWN.get(key);
        if (until != null && until > now) return false;
        int budget = RUN_SIM.pour(tile, px, py);
        if (budget == 0) {
            RUN_COOLDOWN.put(key, now + 20);
            return false;
        }
        return startRun(side, column, 0, budget, 1, colour, depth, tile, px, py);
    }

    private static boolean exposed(ClientLevel level, BlockPos pos, Direction dir) {
        BlockPos npos = pos.relative(dir);
        BlockState ns = blockState(level, npos);
        VoxelShape shape = ns.getShape(level, npos);
        if (shape.isEmpty() || BlockToneSampler.isCrossModel(ns) || sways(ns) || !Block.isFaceFull(shape, dir.getOpposite())) return true;
        ModelFaces drawn = modelFaces(ns, shape);
        return drawn != null && !drawn.covers(dir.getOpposite());
    }

    private static boolean inFluid(ClientLevel level, BlockPos pos) {
        return !ContraptionRides.isPlot(pos) && !level.getFluidState(pos).isEmpty();
    }

    private static int sideExposure(ClientLevel level, BlockPos pos) {
        int mask = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (exposed(level, pos, d)) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    private static void fogAt(ClientLevel level, Vec3 p, int colour) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.underwaterFogEnabled()) return;
        com.simpleblood.particle.BloodFogParticle.setCurrentBloodColor(new com.simpleblood.BloodColor.Color(
                ((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f));
        com.simpleblood.particle.BloodFogParticle.setGlowing((colour & com.simpleblood.BloodColor.GLOW_BIT) != 0);
        try {
            Minecraft.getInstance().particleEngine.createParticle(com.simpleblood.BloodParticles.BLOOD_FOG,
                    p.x, p.y, p.z, 0, -0.01, 0);
        } finally {
            com.simpleblood.particle.BloodFogParticle.setGlowing(false);
        }
    }

    private static void cloudUp(ClientLevel level, List<Vec3> at, List<Integer> colours) {
        int clouds = Math.min(8, at.size() / 6 + 1);
        for (int k = 0; k < clouds; k++) {
            int i = RNG.nextInt(at.size());
            fogAt(level, at.get(i), colours.get(i));
        }
    }

    private static void runOffNewEdges(ClientLevel level, CanvasTile tile, int newlyOpen) {
        SimpleBloodConfig.SurfaceSettings s = settings();
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

    static Direction axisDirection(double dx, double dy, double dz) {
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

    private static final class Run extends RunSim.Run {
        final int depth;
        int travelled;
        int waterRow = -1;
        Hit floor;
        boolean free;
        double x, y;
        boolean across;
        double dirU, dirV;

        Run(CanvasTile tile, int column, int row, int remaining, int volume, int colour, int depth,
            CanvasTile source, int sourceX, int sourceY) {
            super(tile, column, row, remaining, volume, colour, source, sourceX, sourceY);
            this.depth = depth;
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

        SimpleBloodConfig.SurfaceSettings s = settings();
        side.paintThin(column, startRow, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), 1);
        if (budget > 1) {
            Run run = new Run(side, column, startRow + 1, budget - 1, volume, colour, depth, source, sourceX, sourceY);
            run.floor = floorAhead(side, column, startRow);
            RUN_SIM.add(run);
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
        if (BlockToneSampler.isCrossModel(blockState(level, hit.pos()))) return null;
        return hit;
    }

    private static void advanceRuns(ClientLevel level) {
        if (RUNS.isEmpty()) return;
        SimpleBloodConfig.SurfaceSettings s = settings();
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        List<Run> snapshot = new ArrayList<>(RUNS);
        for (Run run : snapshot) {
            if (!RUN_SIM.due(run)) continue;
            if (TILES.get(run.tile.key) != run.tile || run.remaining <= 0) {
                RUNS.remove(run);
                continue;
            }
            boolean fed = RUN_SIM.drink(run, run.travelled);
            if (run.across) {
                stepAcross(level, run, fed, lifetime, pattern);
                continue;
            }
            if (run.free || run.tile.slope != null && run.tile.slope.turned()) {
                stepTurnedRun(level, run, fed, lifetime, pattern);
                continue;
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
                if (floor != null && Math.abs(floor.oy - run.floor.point().y) < 0.05 && run.depth + 1 <= MAX_FLOW_DEPTH
                        && !acrossStep(level, run.tile, floor, run.floor.point(), run.remaining, run.volume, run.colour, run.depth, run)) {
                    stampAt(level, floor, run.floor.point(), run.colour, Math.max(1, run.volume), run.depth + 1);
                }
                continue;
            }
            if (run.row < run.tile.ph && !run.tile.paintable(run.column, run.row)) {
                RUNS.remove(run);
                if (lidRunOn(level, run.tile, run.tile.pixelCenter(run.column, run.row), run.remaining, run.volume, run.colour, run.depth, run)) continue;
                Vec3 at = run.tile.pixelCenter(run.column, run.row)
                        .add(run.tile.nx * 0.03, 0.5 / run.tile.res + 0.02, run.tile.nz * 0.03);
                Hit ledge = trace(level, at, at.add(0, -0.12, 0));
                if (ledge != null && ledge.face() == Direction.UP && run.depth + 1 <= MAX_FLOW_DEPTH) {
                    CanvasTile top = tileAt(level, ledge.pos(), Direction.UP, ledge.point());
                    if (top != null) stampAt(level, top, ledge.point(), run.colour, Math.max(1, run.volume), run.depth + 1);
                }
                continue;
            }
            if (run.row < run.tile.ph && run.tile.underWater(level, run.column, run.row)) {
                RUNS.remove(run);
                Vec3 at = run.tile.pixelCenter(run.column, run.row);
                fogAt(level, at.add(run.tile.nx * 0.1, 0, run.tile.nz * 0.1), run.colour);
                continue;
            }
            if (run.row < run.tile.ph) {
                int thin = RunSim.thin(run.travelled, run.tile.res);
                run.tile.paintThin(run.column, run.row, run.colour, now, lifetime, RNG, pattern, thin);
                run.row++;
                crept(run, fed);
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

    private static final double STEP_REACH = 0.5 + 1.0e-3;

    private static boolean acrossStep(ClientLevel level, CanvasTile wall, CanvasTile floor, Vec3 at, int remaining,
                                      int volume, int colour, int depth, Run arrived) {
        if (floor.custom || !floor.facesUp() || remaining <= 0 || depth + 1 > MAX_FLOW_DEPTH) return false;
        Direction away = axisDirection(wall.nx, wall.ny, wall.nz);
        if (!away.getAxis().isHorizontal()) return false;
        double du = away.getStepX() * floor.ux + away.getStepZ() * floor.uz;
        double dv = away.getStepX() * floor.vx + away.getStepZ() * floor.vz;
        double a = (at.x - floor.ox) * floor.ux + (at.y - floor.oy) * floor.uy + (at.z - floor.oz) * floor.uz;
        double b = (at.x - floor.ox) * floor.vx + (at.y - floor.oy) * floor.vy + (at.z - floor.oz) * floor.vz;
        double reach = du > 0.5 ? floor.w - a : du < -0.5 ? a : dv > 0.5 ? floor.h - b : dv < -0.5 ? b : Double.MAX_VALUE;
        if (reach > STEP_REACH || !openBeyond(level, floor, away)) return false;
        Run run = new Run(floor, 0, 0, remaining, volume, colour, depth + 1,
                arrived != null ? arrived.source : null, arrived != null ? arrived.sourceX : 0, arrived != null ? arrived.sourceY : 0);
        run.across = true;
        run.x = clampPixel(floor.pixelX(at), floor.pw) + 0.5;
        run.y = clampPixel(floor.pixelY(at), floor.ph) + 0.5;
        run.dirU = Math.abs(du) > 0.5 ? Math.signum(du) : 0;
        run.dirV = Math.abs(dv) > 0.5 ? Math.signum(dv) : 0;
        SimpleBloodConfig.SurfaceSettings s = settings();
        floor.paintThin((int) run.x, (int) run.y, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), 1);
        run.x += run.dirU;
        run.y += run.dirV;
        RUN_SIM.add(run);
        com.simpleblood.Tally.count("step.across");
        return true;
    }

    private static void stepAcross(ClientLevel level, Run run, boolean fed, int lifetime, boolean pattern) {
        CanvasTile t = run.tile;
        int px = (int) Math.floor(run.x), py = (int) Math.floor(run.y);
        if (!t.inside(px, py)) {
            RUNS.remove(run);
            int ex = clampPixel(px, t.pw), ey = clampPixel(py, t.ph);
            double sx = run.dirU * t.ux + run.dirV * t.vx, sz = run.dirU * t.uz + run.dirV * t.vz;
            Direction edgeDir = axisDirection(sx, 0, sz);
            Vec3 edge = t.pixelCenter(ex, ey).add(sx * (0.5 / t.res), 0, sz * (0.5 / t.res));
            CanvasTile side = settings().wallsAndCeilings ? tileAt(level, t.pos, edgeDir, edge) : null;
            boolean fedRun = run.source != null;
            if (side != null && startRun(side, clampPixel(side.pixelX(edge), side.pw), 0, Math.max(1, run.remaining), run.volume,
                    run.colour, run.depth, fedRun ? run.source : null, fedRun ? run.sourceX : 0, fedRun ? run.sourceY : 0)) {
                com.simpleblood.Tally.count("step.over");
                return;
            }
            letGo(level, t, edge.add(0, -0.02, 0), run.colour, Math.max(1, run.volume), Double.NaN);
            return;
        }
        if (!t.paintable(px, py)) {
            RUNS.remove(run);
            int bx = clampPixel((int) Math.floor(run.x - run.dirU), t.pw), by = clampPixel((int) Math.floor(run.y - run.dirV), t.ph);
            for (int k = 0; k < Math.max(1, run.volume); k++) t.paintThin(bx, by, run.colour, now, lifetime, RNG, pattern, 0);
            return;
        }
        t.paintThin(px, py, run.colour, now, lifetime, RNG, pattern, RunSim.thin(run.travelled, t.res));
        run.x += run.dirU;
        run.y += run.dirV;
        crept(run, fed);
        if (!RUNS.contains(run)) {
            for (int k = 0; k < Math.max(1, run.volume) - 1; k++) t.paintThin(px, py, run.colour, now, lifetime, RNG, pattern, 0);
        }
    }

    private static void stepTurnedRun(ClientLevel level, Run run, boolean fed, int lifetime, boolean pattern) {
        CanvasTile t = run.tile;
        Slope slope = t.slope != null ? t.slope : slopeNow(level, t);
        if (slope == null) {
            RUNS.remove(run);
            return;
        }
        if (!run.free) {
            run.free = true;
            run.x = run.column + 0.5;
            run.y = run.row + 0.5;
            com.simpleblood.Tally.count("tilt.run");
        }
        int px = (int) Math.floor(run.x), py = (int) Math.floor(run.y);
        int carried = Math.max(1, run.volume);
        if (slope.drips()) {
            RUNS.remove(run);
            dropFrom(level, t, t.pixelCenter(clampPixel(px, t.pw), clampPixel(py, t.ph)), run.colour, carried);
            com.simpleblood.Tally.count("tilt.run.drop");
            return;
        }
        if (!slope.slides()) {
            RUNS.remove(run);
            if (t.inside(px, py) && t.paintable(px, py)) {
                for (int k = 0; k < carried; k++) t.paintThin(px, py, run.colour, now, lifetime, RNG, pattern, 0);
            }
            com.simpleblood.Tally.count("tilt.run.pool");
            return;
        }
        if (!t.inside(px, py)) {
            RUNS.remove(run);
            if (!slope.turned() && py >= t.ph && px >= 0 && px < t.pw) {
                flowOffWallBottom(level, t, px, run.remaining, run.colour, run.volume, run.depth, new boolean[1], run);
                return;
            }
            if (t.custom ? roundLidEdge(level, run) : ontoNextBlock(run, px, py)) return;
            dropFrom(level, t, t.pixelCenter(px, py), run.colour, carried);
            com.simpleblood.Tally.count("tilt.run.off");
            return;
        }
        if (!t.paintable(px, py)) {
            RUNS.remove(run);
            if (!slope.turned() && lidRunOn(level, t, t.pixelCenter(px, py), run.remaining, run.volume, run.colour, run.depth, run)) return;
            dropFrom(level, t, t.pixelCenter(px, py), run.colour, carried);
            com.simpleblood.Tally.count("tilt.run.off");
            return;
        }
        t.paintThin(px, py, run.colour, now, lifetime, RNG, pattern, RunSim.thin(run.travelled, t.res));
        run.x += slope.stepU();
        run.y += slope.stepV();
        crept(run, fed);
    }

    private static boolean ontoNextBlock(Run run, int px, int py) {
        int[] xy = new int[2];
        CanvasTile next = FLOW.across(run.tile, px, py, xy, true);
        if (next == null || !next.paintable(xy[0], xy[1])) return false;
        double fx = run.x - Math.floor(run.x), fy = run.y - Math.floor(run.y);
        return startFreeRun(next, xy[0] + fx, xy[1] + fy, run.remaining, run.volume, run.colour, run.depth,
                run.source, run.sourceX, run.sourceY, lipCell(next, xy[0], xy[1]));
    }

    private static boolean roundLidEdge(ClientLevel level, Run run) {
        CanvasTile t = run.tile;
        if (t.swayPivot == null || t.slope == null) return false;
        double fu = Math.max(0, Math.min(t.pw, run.x)) / t.pw * t.w, fv = Math.max(0, Math.min(t.ph, run.y)) / t.ph * t.h;
        Vec3 edge = new Vec3(t.ox + t.ux * fu + t.vx * fv, t.oy + t.uy * fu + t.vy * fv, t.oz + t.uz * fu + t.vz * fv);
        double du = t.slope.stepU(), dv = t.slope.stepV();
        double gx = t.ux * du + t.vx * dv, gy = t.uy * du + t.vy * dv, gz = t.uz * du + t.vz * dv;
        double eps = 0.5 / t.res;
        for (CanvasTile o : signTiles(level, t.pos, t.state)) {
            if (o == t || !t.swayPivot.equals(o.swayPivot)) continue;
            if (o.nx * gx + o.ny * gy + o.nz * gz < 0.3) continue;
            double off = (edge.x - o.ox) * o.nx + (edge.y - o.oy) * o.ny + (edge.z - o.oz) * o.nz;
            if (Math.abs(off) > 1.0e-3) continue;
            double a = (edge.x - o.ox) * o.ux + (edge.y - o.oy) * o.uy + (edge.z - o.oz) * o.uz;
            double b = (edge.x - o.ox) * o.vx + (edge.y - o.oy) * o.vy + (edge.z - o.oz) * o.vz;
            if (a < -eps || a > o.w + eps || b < -eps || b > o.h + eps) continue;
            double x = Math.max(0.5, Math.min(o.pw - 0.5, a / o.w * o.pw)), y = Math.max(0.5, Math.min(o.ph - 0.5, b / o.h * o.ph));
            if (!o.paintable((int) x, (int) y)) continue;
            slopeNow(level, o);
            if (startFreeRun(o, x, y, Math.max(1, run.remaining), run.volume, run.colour, run.depth,
                    run.source, run.sourceX, run.sourceY, lipCell(o, (int) x, (int) y))) {
                com.simpleblood.Tally.count("tilt.wrap");
                return true;
            }
        }
        return false;
    }

    private static void crept(Run run, boolean fed) {
        run.travelled++;
        if (!fed) run.remaining--;
        RUN_SIM.schedule(run);
        if (run.remaining <= 0 && run.volume > 1) {
            run.volume--;
            run.remaining = Math.max(1, run.tile.res / 4);
        }
        if (run.remaining <= 0) RUNS.remove(run);
    }

    private static boolean lidRunOn(ClientLevel level, CanvasTile wall, Vec3 at, int remaining, int volume,
                                    int colour, int depth, Run run) {
        if (wall.swayPivot == null || !SignFaces.lidded(wall.state)) return false;
        com.simpleblood.Tally.count(remaining > 0 ? "lid.edge" : "lid.edge.spent");
        if (remaining <= 0) return false;
        SignFaces.Pose pose = SignFaces.pose(level, wall.pos, level.getGameTime(), 1f);
        if (pose.angle() != 0 || pose.slide() != 0) return false;
        Direction facing = axisDirection(wall.nx, wall.ny, wall.nz);
        for (CanvasTile t : signTiles(level, wall.pos, wall.state)) {
            if (t == wall || t.swayPivot != null || axisDirection(t.nx, t.ny, t.nz) != facing) continue;
            if (Math.abs((t.ox - wall.ox) * wall.nx + (t.oy - wall.oy) * wall.ny + (t.oz - wall.oz) * wall.nz) > 1.0e-3) continue;
            double a = (at.x - t.ox) * t.ux + (at.y - t.oy) * t.uy + (at.z - t.oz) * t.uz;
            double b = (at.x - t.ox) * t.vx + (at.y - t.oy) * t.vy + (at.z - t.oz) * t.vz;
            if (a < 0 || a > t.w || b < 0 || b > t.h) continue;
            int col = clampPixel(t.pixelX(at), t.pw), row = clampPixel(t.pixelY(at), t.ph);
            if (!t.paintable(col, row)) continue;
            boolean fed = run != null && run.source != null;
            com.simpleblood.Tally.count("lid.runon");
            return startRun(t, col, row, remaining, volume, colour, depth, fed ? run.source : null, fed ? run.sourceX : 0, fed ? run.sourceY : 0);
        }
        com.simpleblood.Tally.count("lid.edge.nobase");
        return false;
    }

    private static void flowOffWallBottom(ClientLevel level, CanvasTile wall, int column, int remaining,
                                          int colour, int volume, int depth, boolean[] dropped) {
        flowOffWallBottom(level, wall, column, remaining, colour, volume, depth, dropped, null);
    }

    private static void flowOffWallBottom(ClientLevel level, CanvasTile wall, int column, int remaining,
                                          int colour, int volume, int depth, boolean[] dropped, Run run) {
        Direction face = wall.face;
        Vec3 bottom = wall.pixelCenter(column, wall.ph - 1).add(0, -0.5 / wall.res, 0);

        if (lidRunOn(level, wall, bottom.add(0, -0.5 / wall.res, 0), remaining, volume, colour, depth, run)) return;

        if (wall.custom) {
            CanvasTile floor = tileAt(level, wall.pos.below(), Direction.UP, bottom);
            if (floor != null && Math.abs(floor.oy - bottom.y) < 0.05 && depth + 1 <= MAX_FLOW_DEPTH) {
                stampAt(level, floor, bottom, colour, Math.max(1, volume), depth + 1);
            } else if (!dropped[0]) {
                dropped[0] = true;
                letGo(level, wall, bottom.add(0, -0.05, 0), colour, Math.max(1, volume), Double.NaN);
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
                if (depth + 1 <= MAX_FLOW_DEPTH && !acrossStep(level, wall, floor, ground.point(), remaining, volume, colour, depth, run)) {
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
                SimpleBloodConfig.SurfaceSettings s = settings();
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
            letGo(level, wall, bottom.add(0, -0.05, 0), colour, Math.max(1, volume), Double.NaN);
        }
    }

    private static final int DRIP_POOL = 24;
    private static final double DROP_CLEAR = 0.04;
    private static final int LIP_COOLDOWN = 12;
    private static final int[] LIP = new int[2];
    private static final int[] RELEASED = new int[3];

    private static void tilt(ClientLevel level, CanvasTile tile, SimpleBloodConfig.SurfaceSettings s) {
        com.simpleblood.compat.CreateKinetics.Part spin = spinOf(level, tile);
        if (spin != null && Math.abs(spin.rpm()) >= com.simpleblood.compat.CreateKinetics.FLING_RPM) return;
        Slope slope = slopeNow(level, tile);
        boolean turned = slope != null && slope.turned();
        if (turned && !tile.turning) {
            tile.turnDrips = TURN_DROPS;
            tile.turnPours = TURN_TRICKLES;
        }
        tile.turning = turned;
        if (!turned || tile.isEmpty()) return;
        if (slope.drips()) {
            if (tile.turnDrips <= 0 || RNG.nextInt(DRIP_POOL) >= tile.count()) return;
            if (tile.releaseLowest(slope, now, RNG, RELEASED)) {
                dropFrom(level, tile, tile.pixelCenter(RELEASED[0], RELEASED[1]), RELEASED[2], 1);
                com.simpleblood.Tally.count("tilt.drip");
                if (--tile.turnDrips == 0) tile.settleExcess(now, LOWEST);
            }
        } else if (slope.slides() && s.wallsAndCeilings && tile.turnPours > 0 && tile.downhillRim(slope, now, RNG, LIP)) {
            if (spill(tile, slope, LIP[0], LIP[1])) tile.turnPours--;
        }
    }

    private static boolean spill(CanvasTile tile, Slope slope, int x, int y) {
        double sx = x + 0.5 + slope.stepU(), sy = y + 0.5 + slope.stepV();
        int cell = lipCell(tile, (int) Math.floor(sx), (int) Math.floor(sy));
        Integer until = RUN_COOLDOWN.get(new RunKey(tile.key, cell));
        if (until != null && until > now) return false;
        if (CanvasTile.body(tile, x, y, FLOW, RunSim.BODY_TILES).excess() <= 0) return false;
        int budget = RUN_SIM.pour(tile, x, y);
        if (budget == 0) {
            RUN_COOLDOWN.put(new RunKey(tile.key, cell), now + 20);
            return false;
        }
        if (startFreeRun(tile, sx, sy, budget, 1, tile.colourAt(x, y), 0, tile, x, y, cell)) {
            com.simpleblood.Tally.count("tilt.pour");
            return true;
        }
        return false;
    }

    private static int lipCell(CanvasTile tile, int x, int y) {
        return -1 - (y * (tile.pw + 2) + x + 1);
    }

    private static boolean startFreeRun(CanvasTile tile, double x, double y, int budget, int volume, int colour, int depth,
                                        CanvasTile source, int sourceX, int sourceY, int cell) {
        if (depth > MAX_FLOW_DEPTH || budget <= 0) return false;
        RunKey key = new RunKey(tile.key, cell);
        Integer until = RUN_COOLDOWN.get(key);
        if (until != null && until > now) return false;
        RUN_COOLDOWN.put(key, now + LIP_COOLDOWN);
        int px = (int) Math.floor(x), py = (int) Math.floor(y);
        Slope slope = tile.slope != null ? tile.slope : slopeNow(trackedLevel, tile);
        if (tile.inside(px, py) && tile.paintable(px, py)) {
            SimpleBloodConfig.SurfaceSettings s = settings();
            tile.paintThin(px, py, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), 1);
            if (slope != null) {
                x += slope.stepU();
                y += slope.stepV();
            }
            if (budget-- <= 1) return true;
        }
        Run run = new Run(tile, px, py, budget, volume, colour, depth, source, sourceX, sourceY);
        run.free = true;
        run.x = x;
        run.y = y;
        RUN_SIM.add(run);
        return true;
    }

    private static Slope slopeNow(ClientLevel level, CanvasTile tile) {
        boolean lid = tile.swayPivot != null && SignFaces.lidded(tile.state);
        com.simpleblood.compat.BodyPose sub = subLevelPose(level, tile);
        com.simpleblood.compat.CreateKinetics.Part spin = spinOf(level, tile);
        if (!lid && sub == null && spin == null) return null;
        Vec3 u = new Vec3(tile.ux, tile.uy, tile.uz), v = new Vec3(tile.vx, tile.vy, tile.vz), n = new Vec3(tile.nx, tile.ny, tile.nz);
        if (lid) {
            SignFaces.Pose pose = SignFaces.pose(level, tile.pos, level.getGameTime(), 1f);
            if (pose.angle() != 0) {
                u = SurfaceRenderer.swing(u, tile.swayAxis, pose.angle());
                v = SurfaceRenderer.swing(v, tile.swayAxis, pose.angle());
                n = SurfaceRenderer.swing(n, tile.swayAxis, pose.angle());
            }
        }
        if (spin != null) {
            double a = spin.angleNow(1f);
            u = SurfaceRenderer.swing(u, spin.axis, a);
            v = SurfaceRenderer.swing(v, spin.axis, a);
            n = SurfaceRenderer.swing(n, spin.axis, a);
        }
        if (sub != null) {
            u = sub.turn(u);
            v = sub.turn(v);
            n = sub.turn(n);
        }
        if (tile.slope == null) tile.slope = new Slope();
        tile.slope.set(u.y, v.y, n.y, tile.uy, tile.vy, tile.ny);
        return tile.slope;
    }

    private static com.simpleblood.compat.BodyPose subLevelPose(ClientLevel level, CanvasTile tile) {
        if (ContraptionRides.isPlot(tile.pos)) return ContraptionRides.logical(tile.pos);
        if (Boolean.FALSE.equals(tile.onSubLevel) || !com.simpleblood.compat.SableSubLevels.present()) return null;
        com.simpleblood.compat.SableSubLevels.Pose pose = com.simpleblood.compat.SableSubLevels.logical(tile.pos, level.getGameTime());
        if (tile.onSubLevel == null) tile.onSubLevel = pose != null;
        return pose;
    }

    private static Vec3 posed(ClientLevel level, CanvasTile tile, Vec3 rest) {
        return posed(level, tile, rest, false);
    }

    private static Vec3 posed(ClientLevel level, CanvasTile tile, Vec3 rest, boolean before) {
        Vec3 p = rest;
        if (tile.swayPivot != null && SignFaces.lidded(tile.state)) {
            SignFaces.Pose pose = SignFaces.pose(level, tile.pos, level.getGameTime(), before ? 0f : 1f);
            if (pose.angle() != 0) p = tile.swayPivot.add(SurfaceRenderer.swing(p.subtract(tile.swayPivot), tile.swayAxis, pose.angle()));
            if (pose.slide() != 0) p = p.add(tile.swayAxis.scale(pose.slide()));
        }
        com.simpleblood.compat.CreateKinetics.Part spin = spinOf(level, tile);
        if (spin != null) {
            double a = spin.angleNow(1f);
            if (before) a -= spin.rpm() * 2 * Math.PI / 60 / 20;
            p = spun(spin, p, a);
        }
        com.simpleblood.compat.BodyPose sub = subLevelPose(level, tile);
        if (sub == null) return p;
        return before ? sub.lastToWorld(p) : sub.toWorld(p);
    }

    public static final List<double[]> MOVING_DROPS = new ArrayList<>();

    private static final double MAX_SURFACE_SPEED = 1.5;

    private static Vec3 surfaceVelocity(ClientLevel level, CanvasTile tile, Vec3 rest) {
        boolean lid = tile.swayPivot != null && SignFaces.lidded(tile.state);
        if (!lid && spinOf(level, tile) == null && subLevelPose(level, tile) == null) return Vec3.ZERO;
        Vec3 v = posed(level, tile, rest).subtract(posed(level, tile, rest, true));
        double len = v.length();
        return len > MAX_SURFACE_SPEED ? Vec3.ZERO : v;
    }

    private static void letGo(ClientLevel level, CanvasTile tile, Vec3 rest, int colour, int carried, double floorY) {
        Vec3 at = posed(level, tile, rest);
        Vec3 v = surfaceVelocity(level, tile, rest);
        if (v.lengthSqr() < 1.0e-8) {
            BloodParticle.spawnDroplet(level, at.x, at.y, at.z, colour, carried, floorY);
        } else {
            BloodParticle.spawnMoving(level, at.x, at.y, at.z, colour, carried, floorY, v.x, v.y - 0.02, v.z);
            com.simpleblood.Tally.count("drop.moving");
            com.simpleblood.Tally.add("drop.moving.speed.mm", Math.round(v.length() * 1000));
            if (com.simpleblood.Tally.ON && MOVING_DROPS.size() < 256) MOVING_DROPS.add(new double[]{at.x, at.y, at.z, v.x, v.y, v.z});
        }
    }

    private static final Map<Long, com.simpleblood.compat.CreateKinetics.Part> SPIN_PARTS = new HashMap<>();

    private static Object spinningEntity(ClientLevel level, BlockPos pos) {
        return ContraptionRides.isPlot(pos) ? ContraptionRides.blockEntity(pos) : level.getBlockEntity(pos);
    }

    static com.simpleblood.compat.CreateKinetics.Part spinOf(ClientLevel level, CanvasTile tile) {
        if (tile.spinAsked) return tile.spin;
        tile.spinAsked = true;
        tile.spin = null;
        if (!com.simpleblood.compat.CreateKinetics.present() || level == null) return null;
        Object be = spinningEntity(level, tile.pos);
        com.simpleblood.compat.CreateKinetics.Part part = SPIN_PARTS.get(tile.pos.asLong());
        if (part == null || !part.alive(be)) {
            part = com.simpleblood.compat.CreateKinetics.part(tile.state, be, Vec3.atCenterOf(tile.pos), CreateParts.of(tile.state) != null);
            if (part == null) SPIN_PARTS.remove(tile.pos.asLong());
            else SPIN_PARTS.put(tile.pos.asLong(), part);
        }
        if (part != null && (isPartTile(tile) || part.turns(tile.face, tile.custom))) tile.spin = part;
        return tile.spin;
    }

    private static final double FLING_RATE = 1.5;
    private static final double FLING_CLEAR = 0.06;

    private static void fling(ClientLevel level, CanvasTile tile) {
        com.simpleblood.compat.CreateKinetics.Part part = spinOf(level, tile);
        if (part == null || tile.isEmpty()) return;
        double rpm = part.rpm();
        double over = Math.abs(rpm) - com.simpleblood.compat.CreateKinetics.FLING_RPM;
        if (over <= 0) return;
        double perTick = FLING_RATE * over / com.simpleblood.compat.CreateKinetics.FLING_RPM * Math.min(1, tile.count() / 32.0) / 20;
        if (RNG.nextDouble() >= perTick) return;
        int[] unit = tile.releaseUnit(RNG);
        if (unit == null) return;
        double a = part.angleNow(1f);
        Vec3 r = SurfaceRenderer.swing(tile.pixelCenter(unit[0], unit[1]).subtract(part.centre), part.axis, a);
        Vec3 out = r.subtract(part.axis.scale(r.dot(part.axis)));
        Vec3 n = SurfaceRenderer.swing(new Vec3(tile.nx, tile.ny, tile.nz), part.axis, a);
        Vec3 at = part.centre.add(r).add(n.scale(FLING_CLEAR)).add(out.lengthSqr() > 1.0e-6 ? out.normalize().scale(FLING_CLEAR) : Vec3.ZERO);
        Vec3 velocity = surfaceVelocity(level, tile, tile.pixelCenter(unit[0], unit[1]));
        com.simpleblood.compat.BodyPose sub = subLevelPose(level, tile);
        if (sub != null) at = sub.toWorld(at);
        BloodParticle.spawnFlung(level, at.x, at.y, at.z, unit[2], 1, velocity.x, velocity.y, velocity.z);
        com.simpleblood.Tally.count("create.fling");
    }

    private static void dropFrom(ClientLevel level, CanvasTile tile, Vec3 rest, int colour, int volume) {
        letGo(level, tile, rest.add(tile.nx * DROP_CLEAR, tile.ny * DROP_CLEAR, tile.nz * DROP_CLEAR), colour, Math.max(1, volume), Double.NaN);
        com.simpleblood.Tally.count("tilt.drop");
    }

    private static final Map<Long, Integer> HEAT = new HashMap<>();
    private static int heatRound = -1;

    private static void heat(ClientLevel level, CanvasTile tile) {
        if (tile.isEmpty()) return;
        if (now >> 4 != heatRound) {
            heatRound = now >> 4;
            HEAT.clear();
        }
        int h = HEAT.computeIfAbsent(tile.pos.asLong(), k -> heatAt(level, tile.pos));
        if (h == 0) return;
        com.simpleblood.Tally.count("heat.dry");
        tile.accelerateAging(16 * (h == 2 ? 11 : 2));
        if (!tile.sizzled || RNG.nextInt(h == 2 ? 2 : 5) == 0) {
            tile.sizzled = true;
            Vec3 c = tile.center();
            Vec3 at = c.add(tile.nx * 0.05, tile.ny * 0.05, tile.nz * 0.05);
            com.simpleblood.BloodSounds.sizzle(level, at);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.02, 0);
        }
    }

    private static int heatAt(ClientLevel level, BlockPos pos) {
        if (ContraptionRides.isPlot(pos)) return 0;
        if (isHot(level.getBlockState(pos))) return 2;
        int warm = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!isHot(level.getBlockState(m))) continue;
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1) return 2;
                    warm = 1;
                }
            }
        }
        return warm;
    }

    public static boolean isSnow(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.SNOW);
    }

    public static boolean isHot(BlockState state) {
        if (state.isAir()) return false;
        if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
        if (state.is(net.minecraft.tags.BlockTags.FIRE)) return true;
        if (state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.LAVA_CAULDRON)) return true;
        return state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock && net.minecraft.world.level.block.CampfireBlock.isLitCampfire(state);
    }

    private static final Map<Long, List<CanvasTile>> BY_POS = new HashMap<>();
    private static final Map<Long, BlockState> CHANGED = new LinkedHashMap<>();
    private static final java.util.Set<Long> NEAR_CHANGED = new java.util.LinkedHashSet<>();
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    public static void blockChanging(ClientLevel level, BlockPos pos) {
        try {
            if (BY_POS.isEmpty() || !RenderThread.on() || level != trackedLevel) return;
            long key = pos.asLong();
            if (BY_POS.containsKey(key) && !CHANGED.containsKey(key)) CHANGED.put(key, level.getBlockState(pos));
            if (BY_POS.containsKey(key)) NEAR_CHANGED.add(key);
            for (Direction d : ALL_DIRECTIONS) {
                long n = BlockPos.offset(key, d);
                if (BY_POS.containsKey(n)) NEAR_CHANGED.add(n);
            }
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.SURFACES, "following a block that changed", t);
        }
    }

    private static boolean grewOrShrank(ClientLevel level, BlockPos pos, BlockState was, BlockState is, List<CanvasTile> here) {
        if (was.getBlock() != is.getBlock()) return false;
        if (was.getBlock() instanceof SnowLayerBlock) {
            boolean deeper = is.getValue(SnowLayerBlock.LAYERS) > was.getValue(SnowLayerBlock.LAYERS);
            List<CanvasTile> rest = new ArrayList<>();
            for (CanvasTile t : new ArrayList<>(here)) {
                if (deeper && t.face == Direction.UP) remove(t);
                else rest.add(t);
            }
            if (!rest.isEmpty()) carry(level, pos, rest, STAY);
            return true;
        }
        if (was.getBlock() instanceof CakeBlock) {
            carry(level, pos, new ArrayList<>(here), STAY);
            return true;
        }
        return false;
    }

    private record Move(CanvasTile tile, BlockPos to, Direction by, PistonMovingBlockEntity mover) {}

    private record Carried(Vec3 at, Vec3 normal, CanvasTile.Lifted pixel, int res) {}

    private static void followChanges(ClientLevel level) {
        if (CHANGED.isEmpty() && NEAR_CHANGED.isEmpty()) return;
        followMoves(level);
        if (NEAR_CHANGED.isEmpty()) return;
        SimpleBloodConfig.SurfaceSettings s = settings();
        for (Long key : NEAR_CHANGED) {
            List<CanvasTile> here = BY_POS.get(key);
            if (here == null) continue;
            for (CanvasTile tile : here.toArray(new CanvasTile[0])) {
                if (TILES.get(tile.key) == tile && checkBlock(level, tile, s, false) && tile.isEmpty()) remove(tile);
            }
        }
        NEAR_CHANGED.clear();
    }

    private static void followMoves(ClientLevel level) {
        if (CHANGED.isEmpty()) return;
        List<Move> moves = new ArrayList<>();
        for (Map.Entry<Long, BlockState> e : CHANGED.entrySet()) {
            BlockPos pos = BlockPos.of(e.getKey());
            BlockState was = e.getValue(), is = level.getBlockState(pos);
            List<CanvasTile> here = BY_POS.get(e.getKey());
            if (was == is || here == null || here.isEmpty()) continue;
            if (pistonMoves(level, pos, was, is, here, moves)) continue;
            if (grewOrShrank(level, pos, was, is, here)) continue;
            if (is.isAir() && ContraptionRides.hold(pos, here)) continue;
            BlockMotion.Motion motion = BlockMotion.of(level, pos, was, is);
            if (motion != null) carry(level, pos, new ArrayList<>(here), motion);
        }
        CHANGED.clear();
        if (moves.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (Move m : moves) {
            if (TILES.get(m.tile().key) == m.tile()) TILES.remove(m.tile().key);
            unindex(m.tile());
        }
        for (Move m : moves) {
            CanvasTile t = m.tile();
            CanvasTile.Key key = new CanvasTile.Key(m.to().asLong(), t.key.face(), t.key.box());
            CanvasTile copy = new CanvasTile(t, key, m.to(), m.by().getStepX(), m.by().getStepY(), m.by().getStepZ());
            copy.mover = m.mover();
            copy.followsMover = m.mover() != null;
            t.dispose(atlas);
            CanvasTile there = TILES.get(key);
            if (there != null) remove(there);
            putTile(key, copy);
        }
    }

    private static boolean pistonMoves(ClientLevel level, BlockPos pos, BlockState was, BlockState is,
                                       List<CanvasTile> here, List<Move> moves) {
        boolean left = is.isAir() || is.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON);
        if (left && !(was.getBlock() instanceof PistonHeadBlock)) {
            for (Direction d : Direction.values()) {
                BlockPos to = pos.relative(d);
                if (level.getBlockEntity(to) instanceof PistonMovingBlockEntity m && !m.isSourcePiston()
                        && m.getMovementDirection() == d && m.getMovedState() == was) {
                    for (CanvasTile t : here) moves.add(new Move(t, to, d, m));
                    return true;
                }
            }
        }
        if (was.getBlock() instanceof PistonBaseBlock && is.getBlock() == was.getBlock()
                && !was.getValue(PistonBaseBlock.EXTENDED) && is.getValue(PistonBaseBlock.EXTENDED)) {
            Direction facing = is.getValue(PistonBaseBlock.FACING);
            BlockPos head = pos.relative(facing);
            PistonMovingBlockEntity m = level.getBlockEntity(head) instanceof PistonMovingBlockEntity p ? p : null;
            for (CanvasTile t : here) if (onOuterFace(t, pos, facing)) moves.add(new Move(t, head, facing, m));
            return true;
        }
        if (was.getBlock() instanceof PistonHeadBlock && left) {
            Direction facing = was.getValue(PistonHeadBlock.FACING);
            BlockPos base = pos.relative(facing.getOpposite());
            if (!(level.getBlockEntity(base) instanceof PistonMovingBlockEntity m)) return false;
            for (CanvasTile t : here) if (onOuterFace(t, pos, facing)) moves.add(new Move(t, base, facing.getOpposite(), m));
            return true;
        }
        if (is.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON)
                && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity m && m.isSourcePiston()) {
            for (CanvasTile t : here) {
                t.mover = m;
                t.followsMover = false;
            }
            return true;
        }
        return false;
    }

    private static boolean onOuterFace(CanvasTile t, BlockPos pos, Direction side) {
        if (t.custom || t.face != side) return false;
        int edge = switch (side.getAxis()) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        } + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
        return Math.abs(planeCoord(t, side) - edge) < 1.0e-3;
    }

    private static void carry(ClientLevel level, BlockPos pos, List<CanvasTile> tiles, BlockMotion.Motion motion) {
        java.util.Set<CanvasTile> landed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Carried> carried = new ArrayList<>();
        for (CanvasTile t : tiles) {
            Vec3 n = new Vec3(t.nx, t.ny, t.nz);
            for (CanvasTile.Lifted l : t.lift()) carried.add(new Carried(t.pixelCenter(l.px(), l.py()), n, l, t.res));
            remove(t);
        }
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        List<Carried> lost = new ArrayList<>();
        for (Carried c : carried) {
            Vec3 local = c.at().subtract(origin);
            Vec3 at = motion.point(local).add(origin);
            Vec3 n = motion.normal(local, c.normal());
            CanvasTile t = tileAt(level, pos, axisDirection(n.x, n.y, n.z), at);
            if (t == null || Math.abs((at.x - t.ox) * t.nx + (at.y - t.oy) * t.ny + (at.z - t.oz) * t.nz) > 0.03
                    || !t.place(c.pixel(), t.pixelX(at), t.pixelY(at), now)) {
                lost.add(c);
            } else {
                landed.add(t);
            }
        }
        letGoOfTurn(level, landed, lost);
    }

    private static final int TURN_DROPS = 4, TURN_TRICKLES = 2;
    private static final int[] LOWEST = new int[3];

    private static void letGoOfTurn(ClientLevel level, java.util.Set<CanvasTile> landed, List<Carried> lost) {
        int drops = 0, trickles = 0;
        for (CanvasTile t : landed) {
            if (t.facesUp()) continue;
            int excess = t.settleExcess(now, LOWEST);
            if (excess <= 0) continue;
            Vec3 at = t.pixelCenter(LOWEST[0], LOWEST[1]);
            if (t.ny < -0.5) {
                if (drops >= TURN_DROPS) continue;
                drops++;
                com.simpleblood.Tally.count("turn.drop");
                letGo(level, t, at.add(0, -0.03, 0), LOWEST[2], Math.min(3, excess), Double.NaN);
            } else if (Math.abs(t.ny) < 0.5 && trickles < TURN_TRICKLES && settings().wallsAndCeilings
                    && startRun(t, LOWEST[0], LOWEST[1] + 1, Math.min(t.ph, 2 + excess), Math.min(3, 1 + excess / 4), LOWEST[2], 0)) {
                trickles++;
                com.simpleblood.Tally.count("turn.trickle");
            }
        }
        int wet = 0;
        for (Carried c : lost) {
            CanvasTile.Lifted px = c.pixel();
            if (px.powder() || com.simpleblood.BloodMix.wetness(now, px.born(), px.life()) < 0.5f) continue;
            if (wet++ % 6 != 0 || drops >= TURN_DROPS) continue;
            drops++;
            com.simpleblood.Tally.count("turn.drop");
            BloodParticle.spawnDroplet(level, c.at().x, c.at().y, c.at().z, px.rgb(),
                    Math.min(c.res() / 2, 1 + px.depth()), c.at().y - MAX_FALL);
        }
        com.simpleblood.Tally.add("turn.lost", lost.size());
    }

    private static boolean settle(ClientLevel level, CanvasTile tile) {
        BlockState current = level.getBlockState(tile.pos);
        if (current == tile.state) {
            refreshVisibility(level, tile);
            return true;
        }
        if (current.isAir()) return true;
        List<CanvasTile> one = new ArrayList<>(1);
        one.add(tile);
        carry(level, tile.pos, one, STAY);
        return false;
    }

    private static final BlockMotion.Motion STAY = new BlockMotion.Motion() {
        @Override
        public Vec3 point(Vec3 local) {
            return local;
        }

        @Override
        public Vec3 normal(Vec3 local, Vec3 normal) {
            return normal;
        }
    };

    private static List<CanvasTile> crossTiles(ClientLevel level, BlockPos pos, BlockState state) {
        SimpleBloodConfig.SurfaceSettings s = settings();
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
                putTile(key, tile);
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
            //? if <=1.21.1 {
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
        SimpleBloodConfig.SurfaceSettings s = settings();
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
                int pw = Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(r.w() * res)));
                int ph = Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(r.h() * res)));
                tile = new CanvasTile(key, pos, state, origin, r.u(), r.v(), r.w(), r.h(), res, now, SignFaces.mask(r, pw, ph), pw, ph);
                if (r.n().x * tile.nx + r.n().y * tile.ny + r.n().z * tile.nz < 0) tile.flipNormal();
                if (r.pivot() != null) {
                    tile.swayPivot = r.pivot().add(pos.getX(), pos.getY(), pos.getZ());
                    tile.swayAxis = r.axis();
                }
                if (TILES.size() >= s.maxTiles) evictOldest();
                putTile(key, tile);
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
        SimpleBloodConfig.SurfaceSettings s = settings();
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

    private static final class WaterTint {
        int colour;
        float amount;
        int shownStep = -1;
        int shownTick;
    }

    private static final Map<Long, WaterTint> TINTS = new HashMap<>();
    private static final Map<Long, Integer> WATER_COLOURS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final float TINT_PER_DROP = 0.025f, TINT_MAX = 0.5f, TINT_CLEARS = 1f / 1200;
    private static final int TINT_STEPS = 64, TINT_REDRAW_TICKS = 5;
    private static final float TINT_BLUE_PULL = 1.6f;
    private static final double RIM_IN = 2.0 / 16, RIM_OUT = 14.0 / 16;

    private static double cauldronWater(BlockState state) {
        if (!state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) return 0;
        return (6 + 3 * state.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL)) / 16.0;
    }

    public static boolean inCauldronWater(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        double h = cauldronWater(blockState(level, pos));
        if (h <= 0) return false;
        double lx = x - pos.getX(), ly = y - pos.getY(), lz = z - pos.getZ(), e = 1.0e-3;
        return ly <= h + e && lx >= RIM_IN - e && lx <= RIM_OUT + e && lz >= RIM_IN - e && lz <= RIM_OUT + e;
    }

    public static void intoCauldron(ClientLevel level, Vec3 at, int colour) {
        if (!RenderThread.on()) {
            RenderThread.run(() -> intoCauldron(level, at, colour));
            return;
        }
        BlockPos pos = BlockPos.containing(at);
        double h = cauldronWater(blockState(level, pos));
        if (h <= 0) return;
        fogAt(level, new Vec3(at.x, Math.min(at.y, pos.getY() + h - 0.12), at.z), colour);
        com.simpleblood.Tally.count("cauldron.cloud");
        WaterTint t = TINTS.computeIfAbsent(pos.asLong(), k -> new WaterTint());
        float was = t.amount;
        t.amount = Math.min(TINT_MAX, was + TINT_PER_DROP);
        t.colour = was <= 0 ? colour & 0xFFFFFF : mixRgb(t.colour, colour, TINT_PER_DROP / (was + TINT_PER_DROP));
    }

    private static int mixRgb(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return (r << 16) | (g << 8) | bl;
    }

    public static int cauldronWaterColour(BlockPos pos, int base) {
        if (WATER_COLOURS.isEmpty() || pos == null) return base;
        Integer tint = WATER_COLOURS.get(pos.asLong());
        if (tint == null) return base;
        float k = ((tint >>> 24) & 0xFF) / 255.0f, kBlue = Math.min(1, k * TINT_BLUE_PULL);
        int r = Math.round(((base >> 16) & 0xFF) * (1 - k) + ((tint >> 16) & 0xFF) * k);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - k) + ((tint >> 8) & 0xFF) * k);
        int bl = Math.round((base & 0xFF) * (1 - kBlue) + (tint & 0xFF) * kBlue);
        return (base & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    private static void tickTints(ClientLevel level) {
        if (TINTS.isEmpty()) return;
        float opacity = settings().opacity / 100.0f;
        java.util.Iterator<Map.Entry<Long, WaterTint>> it = TINTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, WaterTint> e = it.next();
            WaterTint t = e.getValue();
            BlockPos pos = BlockPos.of(e.getKey());
            BlockState state = level.hasChunkAt(pos) ? level.getBlockState(pos) : null;
            double h = state == null ? 0 : cauldronWater(state);
            t.amount -= t.amount * TINT_CLEARS + 1.0e-5f;
            if (h <= 0 || t.amount <= 0.01f) {
                it.remove();
                if (WATER_COLOURS.remove(e.getKey()) != null) redrawWater(level, pos);
                continue;
            }
            int step = Math.round(t.amount * opacity * TINT_STEPS);
            if (step != t.shownStep && now - t.shownTick >= TINT_REDRAW_TICKS) {
                t.shownStep = step;
                t.shownTick = now;
                int amount = Math.min(255, Math.round(step * 255.0f / TINT_STEPS));
                WATER_COLOURS.put(e.getKey(), (amount << 24) | (t.colour & 0xFFFFFF));
                redrawWater(level, pos);
            }
        }
    }

    private static void redrawWater(ClientLevel level, BlockPos pos) {
        level.setSectionDirtyWithNeighbors(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static void clearTints() {
        TINTS.clear();
        WATER_COLOURS.clear();
    }

    private static void dripFromCeiling(ClientLevel level, CanvasTile tile) {
        if (tile.face != Direction.DOWN || tile.isEmpty()) return;
        if (RNG.nextDouble() > Math.min(0.5, tile.count() / 100.0)) return;
        if (tile.slope != null && tile.slope.turned()) return;
        int[] unit = tile.releaseUnit(RNG);
        if (unit == null) return;
        letGo(level, tile, tile.pixelCenter(unit[0], unit[1]).add(0, -0.03, 0), unit[2], 1, Double.NaN);
    }

    static CanvasTile tileAt(ClientLevel level, BlockPos pos, Direction face, Vec3 point) {
        return tileAt(level, pos, face, point, true);
    }

    private static CanvasTile tileAt(ClientLevel level, BlockPos pos, Direction face, Vec3 point, boolean create) {
        BlockState state = blockState(level, pos);
        if (sways(state)) return null;
        if (com.simpleblood.compat.CreateKinetics.present()) {
            CreateParts.Model part = CreateParts.of(state);
            if ((part != null && part.partOnly()) || CreateParts.isWheelHelper(state)) {
                return null;
            }
        }
        if (isHandDrawn(state)) {
            if (!create) return null;
            if (SignFaces.lidded(state)
                    && !SignFaces.onDrawn(state, point.subtract(pos.getX(), pos.getY(), pos.getZ()), face)) {
                com.simpleblood.Tally.count("lid.miss");
                return null;
            }
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
                refreshVisibility(level, tile);
            } else {
                releaseAsDroplets(level, tile);
                remove(tile);
                tile = null;
            }
        }
        if (tile == null && !create) return null;
        if (tile == null) {
            SimpleBloodConfig.SurfaceSettings s = settings();
            if (TILES.size() >= s.maxTiles) evictOldest();
            tile = new CanvasTile(key, pos, face, state, boxes.get(best), s.resolution == 16 ? 16 : 8, now);
            if ("block".equals(s.highlightMode)) {
                tile.setBaseTones(BlockToneSampler.sample(tile, RNG));
            }
            if (face == Direction.UP) tile.exposedMask = sideExposure(level, pos) | insetEdges(tile);
            putTile(key, tile);
            refreshVisibility(level, tile);
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

    private static boolean[] visiblePixels(ClientLevel level, CanvasTile t) {
        if (t.custom || level == null) return null;
        boolean[][] hidden = {null};
        hidePartFaces(t, hidden);
        boolean[] visible = hidden[0];
        for (int py = 0; py < t.ph; py++) {
            for (int px = 0; px < t.pw; px++) {
                Vec3 p = t.pixelCenter(px, py);
                if (!coveredAt(level, t, p.x + t.nx * 0.01, p.y + t.ny * 0.01, p.z + t.nz * 0.01)) continue;
                if (visible == null) {
                    visible = new boolean[t.pw * t.ph];
                    java.util.Arrays.fill(visible, true);
                }
                visible[py * t.pw + px] = false;
            }
        }
        return visible;
    }

    private static boolean coveredAt(ClientLevel level, CanvasTile t, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        if (at.equals(t.pos) && !ContraptionRides.isPlot(at)) return visibleSolidAt(level, x, y, z);
        BlockState state = blockState(level, at);
        if (state.isAir()) return false;
        double lx = x - at.getX(), ly = y - at.getY(), lz = z - at.getZ();
        VoxelShape outline = state.getShape(level, at);
        ModelFaces drawn = outline.isEmpty() ? null : modelFaces(state, outline);
        if (drawn != null && !drawn.withinOutline) return drawn.insideSolid(lx, ly, lz);
        for (AABB b : state.getCollisionShape(level, at).toAabbs()) {
            if (b.inflate(0.002).contains(lx, ly, lz)) return true;
        }
        return false;
    }

    private static void refreshVisibility(ClientLevel level, CanvasTile t) {
        t.applyVisibility(visiblePixels(level, t));
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
        ModelFaces.Face flat = bestFlatFace(faces, face, lp, flatScore);
        if (tilted != null && tiltedScore <= 0.35 && tiltedScore < flatScore[0]) {
            return planeTile(level, pos, state, tilted.index(), create);
        }
        if (flat == null || flatScore[0] > 0.35) return null;
        if (flat.cut() != null) return planeTile(level, pos, state, flat.quad(), create);
        return quadFaceTile(level, pos, state, face, flat.box(), create);
    }

    private static ModelFaces.Face bestFlatFace(ModelFaces faces, Direction face, Vec3 lp, double[] score) {
        double bestScore = Double.MAX_VALUE;
        ModelFaces.Face bestFace = null;
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
            if (d < bestScore) { bestScore = d; bestFace = f; }
        }
        score[0] = bestScore;
        return bestFace;
    }

    public static long waterTint(BlockPos pos) {
        WaterTint t = TINTS.get(pos.asLong());
        return t == null ? 0 : Math.round(t.amount * 1000);
    }

    public static int bloodOnSeeThrough(ClientLevel level, CanvasTile t) {
        if (isHandDrawn(t.state) || isPartTile(t)) return 0;
        Vec3 base = Vec3.atLowerCornerOf(t.pos).add(modelOffset(level, t.pos, t.state));
        Vec3 n = new Vec3(t.nx, t.ny, t.nz);
        double du = 0.3 / t.pw, dv = 0.3 / t.ph;
        int count = 0;
        for (int py = 0; py < t.ph; py++) {
            for (int px = 0; px < t.pw; px++) {
                if (!t.filled(px, py)) continue;
                boolean seen = false, asked = false;
                for (int k = 0; k < 4 && !seen; k++) {
                    double a = (px + 0.5) / t.pw + ((k & 1) == 0 ? -du : du), b = (py + 0.5) / t.ph + ((k & 2) == 0 ? -dv : dv);
                    Vec3 p = new Vec3(t.ox + t.ux * a * t.w + t.vx * b * t.h, t.oy + t.uy * a * t.w + t.vy * b * t.h,
                            t.oz + t.uz * a * t.w + t.vz * b * t.h).subtract(base);
                    int s = BlockToneSampler.seenAt(t.state, p, n);
                    if (s >= 0) asked = true;
                    if (s == 1) seen = true;
                }
                if (asked && !seen) count++;
            }
        }
        return count;
    }

    public static int bloodOffPart(CanvasTile t) {
        CreateParts.Face f = partFace(t);
        if (f == null) return 0;
        int n = 0;
        for (int py = 0; py < t.ph; py++) {
            for (int px = 0; px < t.pw; px++) {
                if (t.filled(px, py) && !f.seen((px + 0.5) / t.pw * f.w(), (py + 0.5) / t.ph * f.h())) n++;
            }
        }
        return n;
    }

    public static String partMaskArt(CanvasTile t) {
        CreateParts.Face f = partFace(t);
        if (f == null) return "(not on a part)";
        StringBuilder sb = new StringBuilder();
        for (int py = 0; py < t.ph; py++) {
            for (int px = 0; px < t.pw; px++) {
                boolean seen = f.seen((px + 0.5) / t.pw * f.w(), (py + 0.5) / t.ph * f.h());
                sb.append(t.filled(px, py) ? (seen ? '#' : 'X') : seen ? 'o' : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static CreateParts.Face partFace(CanvasTile t) {
        if (!isPartTile(t)) return null;
        CreateParts.Model m = CreateParts.of(t.state);
        int i = -1 - t.key.box();
        return m == null || i >= m.faces().size() ? null : m.faces().get(i);
    }

    public static boolean onTurningPart(CanvasTile t) {
        return isPartTile(t);
    }

    static boolean isPartTile(CanvasTile t) {
        return t.custom && t.key.box() < 0;
    }

    private static CanvasTile partTile(ClientLevel level, BlockPos pos, int face, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), Direction.UP, -1 - face);
        CanvasTile tile = TILES.get(key);
        BlockState state = blockState(level, pos);
        if (tile != null && tile.state != state) { remove(tile); tile = null; }
        if (tile != null || !create) return tile;
        CreateParts.Model model = CreateParts.of(state);
        if (model == null || face >= model.faces().size()) return null;
        CreateParts.Face f = model.faces().get(face);
        SimpleBloodConfig.SurfaceSettings s = settings();
        int res = s.resolution == 16 ? 16 : 8;
        int per = 16 / res;
        int pw = Math.max(1, Math.min(BloodCanvasAtlas.TILE, (f.pw() + per - 1) / per));
        int ph = Math.max(1, Math.min(BloodCanvasAtlas.TILE, (f.ph() + per - 1) / per));
        Vec3 origin = Vec3.atLowerCornerOf(pos).add(f.origin());
        tile = new CanvasTile(key, pos, state, origin, f.u(), f.v(), f.w(), f.h(), res, now, f.mask(pw, ph), pw, ph);
        if (TILES.size() >= s.maxTiles) evictOldest();
        putTile(key, tile);
        return tile;
    }

    private static void hidePartFaces(CanvasTile t, boolean[][] visible) {
        if (t.custom || !com.simpleblood.compat.CreateKinetics.present()) return;
        CreateParts.Model model = CreateParts.of(t.state);
        if (model == null) return;
        double bx = t.pos.getX(), by = t.pos.getY(), bz = t.pos.getZ();
        for (CreateParts.Face f : model.faces()) {
            if (f.n().x * t.nx + f.n().y * t.ny + f.n().z * t.nz < 0.99) continue;
            for (int py = 0; py < t.ph; py++) {
                for (int px = 0; px < t.pw; px++) {
                    Vec3 p = t.pixelCenter(px, py);
                    double x = p.x - bx, y = p.y - by, z = p.z - bz;
                    if (Math.abs(f.off(x, y, z)) > 0.004 || !f.covers(x, y, z)) continue;
                    if (visible[0] == null) {
                        visible[0] = new boolean[t.pw * t.ph];
                        java.util.Arrays.fill(visible[0], true);
                    }
                    visible[0][py * t.pw + px] = false;
                }
            }
        }
    }

    private static final int SLANTED_KEY_BASE = 400;

    private static CanvasTile planeTile(ClientLevel level, BlockPos pos, BlockState state, int index, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), Direction.UP, SLANTED_KEY_BASE + index);
        CanvasTile tile = TILES.get(key);
        if (tile != null && tile.state != state) { remove(tile); tile = null; }
        if (tile == null && !create) return null;
        if (tile == null) {
            List<BlockToneSampler.QuadInfo> quads = BlockToneSampler.allQuads(state);
            if (index >= quads.size()) return null;
            SimpleBloodConfig.SurfaceSettings cfg = settings();
            tile = quadTile(key, pos, state, quads.get(index), cfg.resolution == 16 ? 16 : 8, Vec3.ZERO);
            if (tile == null) return null;
            if (TILES.size() >= cfg.maxTiles) evictOldest();
            putTile(key, tile);
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
                refreshVisibility(level, tile);
            } else {
                releaseAsDroplets(level, tile);
                remove(tile);
                tile = null;
            }
        }
        if (tile == null && !create) return null;
        if (tile == null) {
            SimpleBloodConfig.SurfaceSettings s = settings();
            if (TILES.size() >= s.maxTiles) evictOldest();
            tile = new CanvasTile(key, pos, face, state, bestBox, s.resolution == 16 ? 16 : 8, now);
            if ("block".equals(s.highlightMode)) {
                tile.setBaseTones(BlockToneSampler.sample(tile, RNG));
            }
            if (face == Direction.UP) tile.exposedMask = sideExposure(level, pos) | insetEdges(tile);
            putTile(key, tile);
            refreshVisibility(level, tile);
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
        if (count <= 0) return;

        if (tile.face == Direction.UP && !tile.custom) {
            Vec3 c = tile.center();
            Hit below = trace(level, c.add(0, -0.01, 0), c.add(0, -MAX_FALL, 0));
            if (below != null && below.face() == Direction.UP) {
                double dist = c.y - below.point().y;
                int landTick = now + (int) (4 + 4 * Math.sqrt(Math.max(0.25, dist)));
                List<int[]> snap = tile.snapshot();
                if (snap.isEmpty()) return;
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

    private static void dropAll(ClientLevel level, CanvasTile tile) {
        int group = Math.max(1, (int) Math.ceil(tile.count() / 8.0));
        int seen = 0;
        for (int py = 0; py < tile.ph; py++) {
            for (int px = 0; px < tile.pw; px++) {
                if (!tile.filled(px, py) || seen++ % group != 0) continue;
                dropFrom(level, tile, tile.pixelCenter(px, py), tile.colourAt(px, py), Math.min(tile.res / 2, group));
            }
        }
    }

    private static void landFallingShapes(ClientLevel level) {
        if (FALLING.isEmpty()) return;
        SimpleBloodConfig.SurfaceSettings s = settings();
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
            BlockPos landing = BlockPos.containing(c.x, y + 0.05, c.z);
            if (inFluid(level, landing)) {
                List<Vec3> at = new ArrayList<>();
                List<Integer> colours = new ArrayList<>();
                for (int[] px : f.pixels()) {
                    Vec3 p = f.from().pixelCenter(px[0], px[1]);
                    at.add(new Vec3(p.x, y + 0.3, p.z));
                    colours.add(px[2]);
                }
                if (!at.isEmpty()) cloudUp(level, at, colours);
                continue;
            }
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
                centre.addRipple(clampPixel(centre.pixelX(at), centre.pw), clampPixel(centre.pixelY(at), centre.ph), now,
                        f.pixels().size() * 2);
                splashWallsAround(level, f.pixels(), f.from(), y, s);
                com.simpleblood.BloodSounds.plop(level, at, f.pixels().size());
            }
        }
    }

    private static void splashWallsAround(ClientLevel level, List<int[]> pixels, CanvasTile from, double y,
                                          SimpleBloodConfig.SurfaceSettings s) {
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

    private static final RunSim<Run> RUN_SIM = new RunSim<>(RUNS, FLOW, t -> TILES.get(t.key) == t, RNG, () -> now);

    private static boolean feedRun(CanvasTile tile, int px, int py, CanvasTile side, int column, int colour) {
        Run run = RUN_SIM.inColumn(side, column);
        if (run != null) {
            if (run.source != null) return false;
            run.feedFrom(tile, px, py);
            run.remaining += Math.max(2, side.res / 4);
            return true;
        }
        return pourOver(tile, px, py, side, column, colour, 0);
    }

    private static Object resourceProbe;
    private static int resourceProbeTick;

    private static void watchResources(ClientLevel level) {
        if (BlockToneSampler.reloading()) {
            resourceProbeTick = 20;
            return;
        }
        if (++resourceProbeTick < 20) return;
        resourceProbeTick = 0;
        Object probe = BlockToneSampler.probe();
        if (probe == null) return;
        Object was = resourceProbe;
        resourceProbe = probe;
        if (was != null && was != probe) resourcesChanged(level);
    }

    static void resourcesChanged(ClientLevel level) {
        BlockToneSampler.clear();
        CreateParts.clear();
        ModelFaces.clear();
        SignFaces.clear();
        com.simpleblood.compat.InteractiveFoliage.clear();
        com.simpleblood.mob.MobTextures.resourcesChanged();
        List<CanvasTile> gone = new ArrayList<>();
        for (CanvasTile t : TILES.values()) {
            try {
                if (!stillDrawn(level, t)) gone.add(t);
            } catch (Throwable e) {
                gone.add(t);
            }
        }
        for (CanvasTile t : gone) {
            releaseAsDroplets(level, t);
            remove(t);
        }
        if ("block".equals(settings().highlightMode)) {
            for (CanvasTile t : TILES.values()) {
                if (t.custom) continue;
                t.setBaseTones(BlockToneSampler.sample(t, RNG));
                t.dirty = true;
            }
        }
        com.simpleblood.Tally.count("resources.changed");
        SimpleBlood.LOGGER.info("Resource packs changed: blood follows the new models ({} stains on faces no longer drawn fell off)", gone.size());
    }

    private static boolean stillDrawn(ClientLevel level, CanvasTile t) {
        if (t.custom) {
            int k = t.key.box();
            if (k >= 300 && k < 400) return true;
            if (isPartTile(t)) return false;
            return planeStillDrawn(t);
        }
        if (isQuadTile(t)) return faceStillExists(level, t, t.state);
        VoxelShape shape = t.state.getShape(level, t.pos);
        return !shape.isEmpty() && modelFaces(t.state, shape) == null;
    }

    private static boolean planeStillDrawn(CanvasTile t) {
        Vec3 o = new Vec3(t.ox, t.oy, t.oz);
        Vec3[] want = {o, o.add(t.ux * t.w, t.uy * t.w, t.uz * t.w), o.add(t.vx * t.h, t.vy * t.h, t.vz * t.h)};
        for (BlockToneSampler.QuadInfo q : BlockToneSampler.allQuads(t.state)) {
            for (Vec3 c : q.corners()) {
                Vec3 shift = o.subtract(c.add(t.pos.getX(), t.pos.getY(), t.pos.getZ()));
                if (shift.lengthSqr() > 0.25) continue;
                boolean all = true;
                for (Vec3 w : want) {
                    boolean found = false;
                    for (Vec3 d : q.corners()) {
                        if (w.distanceToSqr(d.add(t.pos.getX(), t.pos.getY(), t.pos.getZ()).add(shift)) < 1.0e-5) { found = true; break; }
                    }
                    if (!found) { all = false; break; }
                }
                if (all) return true;
            }
        }
        return false;
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
        watchResources(level);
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "following Create's contraptions",
                () -> ContraptionRides.tick(level));
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "looking for large water wheels", () -> findWheels(level));
        drainPending(level);
        tickTints(level);
        if (TILES.isEmpty()) return;
        //? if 1.20.1 {
        /*if (mc.isPaused()) {
        *///?} else {
        if (mc.isPaused() || !level.tickRateManager().runsNormally()) {
        //?}
            flushUploads();
            return;
        }
        now++;

        SimpleBloodConfig.SurfaceSettings s = settings();
        if (!s.enabled) {
            clear();
            return;
        }
        followChanges(level);
        advanceRuns(level);
        landFallingShapes(level);
        relaxPuddles(level, s);

        int slice = now & 15;
        int i = 0;
        boolean spinning = com.simpleblood.compat.CreateKinetics.present();
        CanvasTile[] snapshot = TILES.values().toArray(new CanvasTile[0]);
        for (CanvasTile tile : snapshot) {
            if (TILES.get(tile.key) != tile) continue;
            if ((i++ & 15) == slice && !checkBlock(level, tile, s, true)) continue;
            if ((now & 1) == 0) tilt(level, tile, s);
            if (spinning) fling(level, tile);
            if (((i + now) & 7) == 0 && tile.age(now, RNG)) tile.dirty = true;
            if (tile.hasRipples()) tile.dirty = true;
            if (tile.isEmpty()) {
                remove(tile);
            }
        }
        flushUploads();
    }

    private static boolean checkBlock(ClientLevel level, CanvasTile tile, SimpleBloodConfig.SurfaceSettings s, boolean timed) {
        boolean riding = ContraptionRides.isPlot(tile.pos);
        if (riding ? ContraptionRides.at(tile.pos) == null : !level.hasChunkAt(tile.pos)) {
            remove(tile);
            return false;
        }
        if (tile.spinAsked && tile.spin != null && !tile.spin.alive(spinningEntity(level, tile.pos))) tile.spinAsked = false;
        if (tile.mover != null) {
            if (!tile.mover.isRemoved() && level.getBlockEntity(tile.pos) == tile.mover) return false;
            tile.mover = null;
            tile.followsMover = false;
            if (!settle(level, tile)) return false;
        }
        BlockState current = blockState(level, tile.pos);
        if (current != tile.state) {
            if (faceStillExists(level, tile, current)) {
                tile.rebind(current);
                refreshVisibility(level, tile);
            } else {
                com.simpleblood.Tally.count("block.gone");
                if (riding) dropAll(level, tile);
                else releaseAsDroplets(level, tile);
                remove(tile);
                return false;
            }
        }
        if (!tileExposed(level, tile)) {
            remove(tile);
            return false;
        }
        if (inFluid(level, tile.custom ? tile.pos : tile.pos.relative(tile.face)) || cauldronWater(current) > 0) {
            List<int[]> washed = tile.washUnderWater(level);
            if (!washed.isEmpty()) {
                List<Vec3> at = new ArrayList<>(washed.size());
                List<Integer> colours = new ArrayList<>(washed.size());
                for (int[] w : washed) {
                    Vec3 p = tile.pixelCenter(w[0], w[1]);
                    at.add(p.add(tile.nx * 0.15, tile.ny * 0.15, tile.nz * 0.15));
                    colours.add(w[2]);
                }
                cloudUp(level, at, colours);
            }
            if (tile.isEmpty()) {
                remove(tile);
                return false;
            }
        }
        dripFromCeiling(level, tile);
        if (!tile.custom && tile.face == Direction.UP && !tile.isEmpty()) {
            int mask = sideExposure(level, tile.pos) | insetEdges(tile);
            int newlyOpen = mask & ~tile.exposedMask;
            if (mask != tile.exposedMask) refreshVisibility(level, tile);
            tile.exposedMask = mask;
            if (newlyOpen != 0) runOffNewEdges(level, tile, newlyOpen);
        }
        if (timed && !riding && s.rainWashes && level.isRainingAt(tile.pos.relative(tile.face))) {
            tile.accelerateAging(16 * 6);
        }
        if (timed && s.heatDries) heat(level, tile);
        return true;
    }

    private static final int RELAX_BUDGET = 48;
    private static int relaxCursor;

    private static void relaxPuddles(ClientLevel level, SimpleBloodConfig.SurfaceSettings s) {
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

    public static void washAround(ClientLevel level, Vec3 at, double radius, int ticks) {
        if (TILES.isEmpty()) return;
        com.simpleblood.Tally.count("surface.washed");
        double r2 = radius * radius;
        for (CanvasTile tile : TILES.values()) {
            double dx = tile.pos.getX() + 0.5 - at.x, dy = tile.pos.getY() + 0.5 - at.y, dz = tile.pos.getZ() + 0.5 - at.z;
            if (dx * dx + dy * dy + dz * dz <= r2) tile.accelerateAging(ticks);
        }
    }

    private static void remove(CanvasTile tile) {
        CanvasTile gone = TILES.remove(tile.key);
        unindex(tile);
        if (gone != null && gone != tile) unindex(gone);
        tile.dispose(BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null);
    }

    static List<CanvasTile> tilesAt(BlockPos pos) {
        return BY_POS.get(pos.asLong());
    }

    static List<CanvasTile> tilesIn(java.util.function.Predicate<BlockPos> which) {
        List<CanvasTile> out = new ArrayList<>();
        for (CanvasTile t : TILES.values()) if (which.test(t.pos)) out.add(t);
        return out;
    }

    static void removeTile(CanvasTile tile) {
        if (TILES.get(tile.key) == tile) remove(tile);
        else tile.dispose(BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null);
    }

    static void dropOff(ClientLevel level, CanvasTile tile) {
        releaseAsDroplets(level, tile);
    }

    static void addTile(CanvasTile tile) {
        CanvasTile there = TILES.get(tile.key);
        if (there != null && there != tile) remove(there);
        if (TILES.size() >= settings().maxTiles) evictOldest();
        putTile(tile.key, tile);
    }

    static BlockState blockState(ClientLevel level, BlockPos pos) {
        return ContraptionRides.isPlot(pos) ? ContraptionRides.state(pos) : level.getBlockState(pos);
    }

    private static void putTile(CanvasTile.Key key, CanvasTile tile) {
        CanvasTile old = TILES.put(key, tile);
        if (old != null && old != tile) unindex(old);
        BY_POS.computeIfAbsent(tile.pos.asLong(), k -> new ArrayList<>(2)).add(tile);
    }

    private static void unindex(CanvasTile tile) {
        List<CanvasTile> at = BY_POS.get(tile.pos.asLong());
        if (at == null) return;
        at.remove(tile);
        if (at.isEmpty()) BY_POS.remove(tile.pos.asLong());
    }

    public static void clear() {
        if (!RenderThread.on()) {
            RenderThread.run(BloodSurfaces::clear);
            return;
        }
        PENDING.clear();
        pendingCount.set(0);
        com.simpleblood.mob.MobBlood.clear();
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (CanvasTile t : TILES.values()) t.dispose(atlas);
        TILES.clear();
        clearTints();
        BY_POS.clear();
        CHANGED.clear();
        NEAR_CHANGED.clear();
        FALLING.clear();
        RUNS.clear();
        RUN_COOLDOWN.clear();
        Footprints.clear();
        BlockToneSampler.clear();
        ModelFaces.clear();
        SignFaces.clear();
        com.simpleblood.compat.InteractiveFoliage.clear();
        ContraptionRides.clear();
        SPIN_PARTS.clear();
        wheels = new BlockPos[0];
        wheelScan = Long.MIN_VALUE;
        SimpleBlood.LOGGER.debug("Surface blood cleared");
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
