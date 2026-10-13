package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.RenderThread;
import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
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

/**
 * The surface-blood "canvas engine": owns every {@link CanvasTile}, turns droplet impacts
 * into paint operations, runs the little flow simulation (blood creeping down block sides
 * and pooling below), ages the paint, drops or re-homes tiles whose block changed, and hands
 * the live tiles to the renderer. Entirely client-side.
 *
 * Everything in here belongs to the render thread: the tiles, the runs, the random source.
 * Tracing (read-only) may happen from any thread; painting from another thread (a particle
 * ticked by AsyncParticles) is queued and applied on the next client tick.
 */
public final class BloodSurfaces {

    private static final Map<CanvasTile.Key, CanvasTile> TILES = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create();
    private static ClientLevel trackedLevel;
    private static int now;

    /** A paint request from another thread, waiting for the render thread. */
    private record Deferred(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder, IntConsumer after) {}

    private static final ConcurrentLinkedQueue<Deferred> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger pendingCount = new AtomicInteger();
    /** More than this many waiting deposits means something runs wild; the rest are dropped. */
    private static final int MAX_PENDING = 4096;

    /** Maximum distance blood released from a broken block may fall before it disappears. */
    private static final double MAX_FALL = 10.0;
    /** How many times blood may hop from one block to the next while flowing down. */
    private static final int MAX_FLOW_DEPTH = 5;

    private BloodSurfaces() {}

    /** Current tick counter used for pixel ages. */
    public static int now() { return now; }

    public static Collection<CanvasTile> tiles() { return TILES.values(); }

    public static int tileCount() { return TILES.size(); }

    private static SimpleBloodConfig.SurfaceSettings settings() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg == null ? new SimpleBloodConfig.SurfaceSettings() : cfg.surfaces;
    }

    // ------------------------------------------------------------------ impacts

    /** Whether fences, stairs, signs and plants are followed in detail (a setting; off is cheaper). */
    private static boolean detailed() {
        return settings().detailedShapes;
    }

    /** Whether a block is drawn as crossed planes that take blood (see {@link BlockToneSampler#isCrossModel}). */
    public static boolean isCrossModel(BlockState state) {
        return detailed() && BlockToneSampler.isCrossModel(state);
    }

    /**
     * Whether a block takes no blood because the shader pack in use sways it (leaves, grass and
     * other plants, vines, lanterns): paint on it would stay put while the block moves through
     * it. Drops pass it and land on whatever is behind or below. Without a pack nothing sways.
     */
    public static boolean sways(BlockState state) {
        if (!settings().skipSwayingBlocks || state.isAir() || !com.simpleblood.compat.ShaderPacks.inUse()) return false;
        Block b = state.getBlock();
        return state.is(net.minecraft.tags.BlockTags.LEAVES)
                || b instanceof net.minecraft.world.level.block.VineBlock
                || b instanceof net.minecraft.world.level.block.GrowingPlantBlock // kelp, cave and nether vines
                || b instanceof net.minecraft.world.level.block.LanternBlock
                || b instanceof net.minecraft.world.level.block.SugarCaneBlock
                || isPlant(b)
                || BlockToneSampler.isCrossModel(state);
    }

    /** Grass, flowers, crops, saplings, bushes and other small plants (their base class was renamed). */
    private static boolean isPlant(Block b) {
        //? if <=1.21.1 {
        /*return b instanceof net.minecraft.world.level.block.BushBlock;
        *///?} else {
        return b instanceof net.minecraft.world.level.block.VegetationBlock;
        //?}
    }

    /**
     * Whether a block is a sign or banner, drawn from a hand-written description: a standing
     * sign's board and a banner's cloth poke into the block above, a wall banner's cloth hangs
     * into the block below.
     */
    public static boolean isHandDrawn(BlockState state) {
        return detailed() && SignFaces.applies(state);
    }

    /**
     * Whether a block draws tilted faces a drop could pass without ever colliding (a lectern's
     * desk has no hitbox of its own), so drops inside it, or just above it, must look for them
     * every tick like they do for signs.
     */
    public static boolean hasTiltedFaces(BlockState state) {
        if (!detailed() || state.isAir() || SignFaces.applies(state)) return false;
        try {
            VoxelShape shape = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) return false;
            ModelFaces faces = modelFaces(state, shape);
            return faces != null && !faces.slanted.isEmpty();
        } catch (Throwable t) {
            return false; // a modded block that wants a real level for its shape
        }
    }

    /** Result of a droplet hitting a block: where, which block, which face. */
    public record Hit(BlockPos pos, Direction face, Vec3 point) {}

    /**
     * Finds the surface a moving droplet hit between two positions. Returns {@code null}
     * when nothing solid is in the way.
     */
    public static Hit trace(ClientLevel level, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 1.0e-8) {
            to = from.add(0, -0.05, 0);
        }
        // What you can see, not the hitbox: the drawn faces of a fence or stair, the outline
        // of everything else (plants exist, cactus spines are where the texture is).
        Vec3 end = to;
        return BlockGetter.<Hit, Object>traverseBlocks(from, end, null,
                (ctx, pos) -> clipBlockSafely(level, pos, from, end), ctx -> null);
    }

    private static boolean shapeTrouble;

    /**
     * A modded block whose shape or model cannot be read (it wants a block entity, it is mid
     * reload, its model throws) simply takes no blood; the first such block is logged.
     */
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

    /**
     * Where the segment first meets one block: its drawn faces when the model is followed,
     * its outline otherwise, the nearer of the two when both apply (an outline that starts
     * inside a fence's hitbox says nothing, so it is skipped).
     */
    private static Hit clipBlock(ClientLevel level, BlockPos pos, Vec3 from, Vec3 to) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            // A standing sign's board, a banner's cloth or a lectern's desk stands a little
            // proud of its block, and a wall banner's cloth hangs below its block: test those
            // from the air next to them.
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
        if (sways(state)) return null; // shader packs sway it: on to what is behind
        if (isHandDrawn(state)) return SignFaces.clip(state, pos, from, to); // the outline box is not the board
        if (!detailed() && (SignFaces.applies(state) || BlockToneSampler.isCrossModel(state))) return null; // plain mode: drops pass plants and signs
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;
        ModelFaces faces = modelFaces(state, shape);
        if (faces != null) {
            // Drawn faces first (tilted ones included); the outline only where they miss.
            Hit drawn = faces.clip(pos, from, to);
            if (drawn != null) return drawn;
        }
        BlockHitResult r = shape.clip(from, to, pos);
        if (r == null || r.getType() != HitResult.Type.BLOCK || (faces != null && r.isInside())) return null;
        return new Hit(pos, r.getDirection(), r.getLocation());
    }

    /** The drawn faces to follow for this block, or null when its outline is the better guide. */
    private static ModelFaces modelFaces(BlockState state, VoxelShape shape) {
        if (!detailed() || Block.isShapeFullBlock(shape) || BlockToneSampler.isCrossModel(state)) return null;
        ModelFaces f = ModelFaces.of(state, shape);
        return f.usable ? f : null;
    }

    /**
     * Whether a world point is inside something you can see: for a fence or stair one of its
     * drawn cuboids, for any other block its outline. Hitboxes reach further than that
     * (fences collide half a block above their wood), and blood should not stop there.
     */
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
            return true; // treat it as solid: the drop stops rather than ghosts through
        }
    }

    /**
     * Deposits {@code pixels} blood pixels at an impact. The paint grows from the impact
     * point across the face, continues onto a neighbouring face at the same height, starts
     * runs down block sides, and lets a droplet roll off where nothing can hold it.
     *
     * @param colour blood colour as 0xRRGGBB
     */
    public static int deposit(ClientLevel level, Hit hit, int colour, int pixels) {
        return deposit(level, hit, colour, pixels, null);
    }

    /**
     * @param travel direction the blood was moving when it landed (any length; null = none):
     *               the splat stretches along it
     */
    public static int deposit(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel) {
        return deposit(level, hit, colour, pixels, travel, false);
    }

    /** Result of {@link #deposit}: nothing painted, painted on bare surface, painted into blood. */
    public static final int NOTHING = 0, ONTO_SURFACE = 1, INTO_PUDDLE = 2;

    /**
     * @param powder snow rather than blood: the pixels sit as a flat pile and never flow
     * @return {@link #NOTHING}, {@link #ONTO_SURFACE} or {@link #INTO_PUDDLE}
     */
    public static int deposit(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder) {
        SimpleBloodConfig.SurfaceSettings s = settings();
        if (!s.enabled || pixels <= 0 || hit == null || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return NOTHING;
        if (!s.wallsAndCeilings && hit.face() != Direction.UP) return NOTHING;
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.distanceToSqr(hit.point()) > (double) s.renderDistance * s.renderDistance) {
            return NOTHING;
        }
        if (!RenderThread.on()) {
            // A particle ticked on a worker thread (AsyncParticles does that): the tiles are
            // the render thread's alone, so the paint waits for the next client tick.
            depositLater(level, hit, colour, pixels, travel, powder, null);
            return ONTO_SURFACE;
        }
        syncLevel(level);

        CanvasTile tile = tileAt(level, hit.pos(), hit.face(), hit.point());
        if (tile == null) return NOTHING;
        int result = tile.depthAt(clampPixel(tile.pixelX(hit.point()), tile.pw), clampPixel(tile.pixelY(hit.point()), tile.ph)) > 0
                ? INTO_PUDDLE : ONTO_SURFACE;
        if (powder) {
            // A pile: lay it down and stop. No runs, no drops, no ripples.
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

    /**
     * Queues a deposit for the render thread, from any thread. {@code after} (may be null)
     * receives the result there, so a sound can still follow the paint.
     */
    public static void depositLater(ClientLevel level, Hit hit, int colour, int pixels, Vec3 travel, boolean powder, IntConsumer after) {
        if (hit == null || level == null || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
        if (pendingCount.incrementAndGet() > MAX_PENDING) {
            pendingCount.decrementAndGet();
            return;
        }
        PENDING.add(new Deferred(level, hit, colour, pixels, travel, powder, after));
    }

    /** Applies the deposits other threads queued since the last tick. Render thread. */
    private static void drainPending(ClientLevel level) {
        Deferred d;
        while ((d = PENDING.poll()) != null) {
            pendingCount.decrementAndGet();
            if (d.level() != level) continue; // queued for a world that is gone
            int result = deposit(d.level(), d.hit(), d.colour(), d.pixels(), d.travel(), d.powder());
            if (d.after() != null) d.after().accept(result);
        }
    }

    /**
     * The top-face tile a hit landed on, for footprints. With {@code create} false only a
     * tile that already exists (i.e. has blood on it) is returned.
     */
    static CanvasTile topTile(ClientLevel level, Hit hit, boolean create) {
        if (hit.face() != Direction.UP) return null;
        syncLevel(level);
        return tileAt(level, hit.pos(), hit.face(), hit.point(), create);
    }

    /**
     * Paints a blob of {@code pixels} at {@code point} on a tile and deals with everything the
     * blob touches: neighbouring faces, edges (runs), and drop-offs.
     *
     * @param depth how many blocks this blood has already flowed down (bounds cascades)
     */
    private static void stampAt(ClientLevel level, CanvasTile tile, Vec3 point, int colour, int pixels, int depth) {
        stampAt(level, tile, point, colour, pixels, depth, null);
    }

    private static void stampAt(ClientLevel level, CanvasTile tile, Vec3 point, int colour, int pixels, int depth, Vec3 travel) {
        SimpleBloodConfig.SurfaceSettings s = settings();
        int px = clampPixel(tile.pixelX(point), tile.pw);
        int py = clampPixel(tile.pixelY(point), tile.ph);
        if (tile.custom && tile.ny < 0.99) {
            // A plant or dripstone plane: paint what fits on the leaves, nothing spills off it.
            // (A sign board's top edge, facing up, spills over and runs down the board.)
            int painted = tile.stamp(px, py, pixels, colour, now, s.lifetimeSeconds * 20, RNG, !"block".equals(s.highlightMode), null);
            // An upright board or cloth (a sign, a banner): what hits it trickles down it and
            // drips off the bottom edge.
            if (painted > 0 && depth == 0 && isHandDrawn(tile.state) && Math.abs(tile.ny) < 0.3 && tile.vy < -0.7) {
                runDownBoard(tile, px, py, colour, pixels);
            }
            return;
        }
        List<int[]> spill = new ArrayList<>();
        // Project the travel direction onto the face: its in-plane part stretches the splat,
        // scaled by how much of the motion was along the surface (a drop falling straight
        // down leaves a round splat).
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

        // Splats that sit on a block edge also start creeping down the side.
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

    /**
     * Blood that hit an upright board or cloth (a sign, a banner) starts trickling down it from
     * below the splat: one run for a drop, two for a splash, as far as the blood carries.
     */
    private static void runDownBoard(CanvasTile tile, int px, int py, int colour, int pixels) {
        int runs = pixels >= 6 ? 2 : 1;
        int budget = Math.max(2, Math.min(tile.ph, Math.round(tile.ph * (0.25f + pixels / 24f))));
        for (int r = 0; r < runs; r++) {
            int col = clampPixel(px + (runs > 1 ? (r == 0 ? -1 : 1) * (1 + RNG.nextInt(2)) : 0), tile.pw);
            // Start just under the lowest blood of the splat in that column.
            int row = -1;
            for (int y = tile.ph - 1; y >= 0; y--) {
                if (tile.filled(col, y)) { row = y; break; }
            }
            if (row < 0) row = py;
            if (row + 1 < tile.ph) startRun(tile, col, row + 1, budget, 1 + pixels / 6, colour, 0);
        }
    }

    /**
     * Handles one pixel that growth pushed outside a tile.
     *
     * @return true when a run down a side face was started
     */
    private static boolean spillCell(ClientLevel level, CanvasTile tile, int cx, int cy, int colour,
                                     SimpleBloodConfig.SurfaceSettings s, boolean[] droppedOff, int depth) {
        Vec3 p = tile.pixelCenter(cx, cy);
        Direction face = tile.face;
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);

        // 0. On a fence or stair the face may end where it meets more of the same block (a
        //    bar's top runs into the post): blood pushed that way is simply out of sight.
        if (isQuadTile(tile) && hiddenInBlock(tile, p)) return false;

        // 1. A neighbouring face on the same plane simply continues the puddle.
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

        // Which edge of the tile did the pixel cross? Past a corner it crossed two: take the one
        // that is open, so blood goes over the side with nothing next to it rather than into
        // the block beside it (where a drop would be stuck inside the block, black, for good).
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

        // 2. Top face reaching an edge: blood starts creeping down that side of the block.
        if (tile.facesUp() && s.wallsAndCeilings && edgeDir.getAxis().isHorizontal()
                && openBeyond(level, tile, edgeDir)) {
            CanvasTile side = tileAt(level, tile.pos, edgeDir, edge);
            if (side != null) {
                return pourOver(tile, ex, ey, side, clampPixel(side.pixelX(edge), side.pw), colour, depth);
            }
        }

        // 3. Blood at the bottom edge of a wall: pool on the floor, continue on the wall
        //    below, or drip off.
        if (face.getAxis().isHorizontal() && edgeDir == Direction.DOWN) {
            flowOffWallBottom(level, tile, ex, 1, colour, 1, depth, droppedOff);
            return false;
        }

        // 4. Nothing to hold it: a drop lets go at the edge and falls straight down.
        if (tile.facesUp() && !droppedOff[0]) {
            droppedOff[0] = true;
            BloodParticle.spawnRunoff(level, edge.x, edge.y - 0.02, edge.z, colour);
        }
        return false;
    }

    /**
     * Blood on a top face at pixel (px, py) reaches an open edge: start a run down the side
     * face there, fed by the pool the pixel belongs to. Like water over the lip of a basin,
     * the run keeps drawing from that whole pool while it creeps, so the puddle sinks as a
     * whole - and while it is sinking a second spout is unlikely: each run already pouring
     * from the pool raises the volume another one needs before it may start.
     */
    private static boolean pourOver(CanvasTile tile, int px, int py, CanvasTile side, int column, int colour, int depth) {
        RunKey key = new RunKey(side.key, column);
        Integer until = RUN_COOLDOWN.get(key);
        if (until != null && until > now) return false;
        CanvasTile.Body body = CanvasTile.body(tile, px, py, FLOW, BODY_TILES);
        int mass = Math.max(1, body.mass());
        int pouring = 0;
        for (Run run : RUNS) if (run.source != null && body.contains(run.source)) pouring++;
        // (in proportion to the face: a 2 px fence bar pours along its whole length, a full
        // block top needs a real pool)
        if (pouring > 0 && mass < Math.max(1, POUR_GATE * tile.pw * tile.ph / 256) * (pouring + 1)) {
            RUN_COOLDOWN.put(key, now + 20); // not enough head yet: ask again in a second
            return false;
        }
        // The creep may go as far as half the pool could fill, three block edges at most.
        int budget = Math.max(2, Math.min(3 * tile.res, mass / 2));
        return startRun(side, column, 0, budget, 1, colour, depth, tile, px, py);
    }

    /** Units a pool at 16 px must hold per run already pouring from it before another may start. */
    private static final int POUR_GATE = 32;
    /** Tiles a body of blood may span for draining purposes. */
    private static final int BODY_TILES = 6;

    /** Whether the block next to {@code pos} in direction {@code dir} leaves that face visible. */
    private static boolean exposed(ClientLevel level, BlockPos pos, Direction dir) {
        BlockPos npos = pos.relative(dir);
        BlockState ns = level.getBlockState(npos);
        VoxelShape shape = ns.getShape(level, npos);
        return shape.isEmpty() || BlockToneSampler.isCrossModel(ns) || sways(ns) || !Block.isFaceFull(shape, dir.getOpposite());
    }

    private static boolean inFluid(ClientLevel level, BlockPos pos) {
        return !level.getFluidState(pos).isEmpty();
    }

    /** Bit mask (by {@link Direction#ordinal()}) of the horizontal sides of a block open to air. */
    private static int sideExposure(ClientLevel level, BlockPos pos) {
        int mask = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (exposed(level, pos, d)) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    /** A small blood cloud where running blood meets water (if underwater fog is enabled). */
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

    /**
     * Blood that water has just taken (washed off a flooded surface, or fallen into water):
     * a few clouds at some of the pixels, about one per six, at most eight.
     */
    private static void cloudUp(ClientLevel level, List<Vec3> at, List<Integer> colours) {
        int clouds = Math.min(8, at.size() / 6 + 1);
        for (int k = 0; k < clouds; k++) {
            int i = RNG.nextInt(at.size());
            fogAt(level, at.get(i), colours.get(i));
        }
    }

    /**
     * A side of this block just opened up (a neighbour was broken): blood on the top face
     * touching that side now runs down it.
     */
    private static void runOffNewEdges(ClientLevel level, CanvasTile tile, int newlyOpen) {
        SimpleBloodConfig.SurfaceSettings s = settings();
        boolean[] dropped = {false};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if ((newlyOpen & (1 << d.ordinal())) == 0) continue;
            // Which tile border does this direction correspond to?
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

    /** Direction for an axis-aligned unit vector (version-independent). */
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

    // ------------------------------------------------------------------ flow simulation

    /**
     * A trickle creeping down a vertical face one pixel at a time. {@code remaining} is how
     * many more pixels it may travel in total (set from the size of the puddle feeding it);
     * {@code volume} is how much blood arrives wherever it ends up.
     */
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
        /** The top-face pool this run drains, and the pixel of it at the lip (null: a splash on a wall). */
        CanvasTile source;
        int sourceX, sourceY;
        /** Last row the run may paint before it meets water there (-1: none); it clouds up when it does. */
        int waterRow = -1;
        /** A top face in front of the wall above its bottom edge (a fence bar under the post, a slab); the run pools there. */
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
            // Ticks per pixel, scaled so the creep covers the same distance per second at any
            // resolution: roughly 2.5-3.5 s per block edge, slow enough to watch.
            this.interval = Math.max(2, (6 + RNG.nextInt(4)) * 8 / tile.res);
            this.nextTick = now + interval;
        }
    }

    private record RunKey(CanvasTile.Key tile, int column) {}

    private static final List<Run> RUNS = new ArrayList<>();
    /** Columns that recently started a run, so a growing puddle does not spawn one per tick. */
    private static final Map<RunKey, Integer> RUN_COOLDOWN = new HashMap<>();

    /**
     * Starts a run at the top of a side tile: paints the first pixel immediately (so the
     * trickle visibly connects to the puddle) and queues the rest for the ticker.
     */
    private static boolean startRun(CanvasTile side, int column, int budget, int volume, int colour, int depth) {
        return startRun(side, column, 0, budget, volume, colour, depth);
    }

    /** Starts a run at an arbitrary row of a vertical face (e.g. a splash part-way up a wall). */
    private static boolean startRun(CanvasTile side, int column, int startRow, int budget, int volume, int colour, int depth) {
        return startRun(side, column, startRow, budget, volume, colour, depth, null, 0, 0);
    }

    /** Starts a run fed by the pool at (sourceX, sourceY) on {@code source} (null: unfed, it just creeps its budget). */
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
            RUNS.add(run);
        }
        return true;
    }

    /**
     * The first top face straight below a run's start, just in front of the wall, that lies
     * above the wall's bottom edge: the bar under a fence post's side, a slab leaning on a
     * wall. Null when the run may creep to the bottom edge undisturbed.
     */
    private static Hit floorAhead(CanvasTile wall, int column, int row) {
        ClientLevel level = trackedLevel;
        if (level == null || wall.custom || !wall.face.getAxis().isHorizontal()) return null;
        Direction face = wall.face;
        Vec3 start = wall.pixelCenter(column, row).add(face.getStepX() * 0.02, 0.5 / wall.res, face.getStepZ() * 0.02);
        double bottom = worldBounds(wall).minY;
        Hit hit = trace(level, start, new Vec3(start.x, bottom + 0.005, start.z));
        if (hit == null || hit.face() != Direction.UP || hit.point().y < bottom + 0.02) return null;
        if (BlockToneSampler.isCrossModel(level.getBlockState(hit.pos()))) return null; // grass in front: creep on behind it
        return hit;
    }

    /** Advances every active run by at most one pixel. */
    private static void advanceRuns(ClientLevel level) {
        if (RUNS.isEmpty()) return;
        SimpleBloodConfig.SurfaceSettings s = settings();
        int lifetime = s.lifetimeSeconds * 20;
        boolean pattern = !"block".equals(s.highlightMode);
        // Runs may start new runs while we iterate, so work on a snapshot.
        List<Run> snapshot = new ArrayList<>(RUNS);
        for (Run run : snapshot) {
            if (now < run.nextTick) continue;
            if (TILES.get(run.tile.key) != run.tile || run.remaining <= 0) {
                RUNS.remove(run); // the wall is gone (its blood fell) or the trickle dried up
                continue;
            }
            boolean fed = false;
            if (run.source != null) {
                // Fed runs draw a unit from their pool for every pixel they creep; half of it
                // stays on the wall as film, the rest travels on to wherever the run ends.
                // While the pool feeds it the trickle keeps going (down the next block too, and
                // off the bottom as drops). When the pool is down to a film (or gone) it only
                // creeps a little further on what is already on the wall, then stops.
                if (TILES.get(run.source.key) != run.source
                        || !CanvasTile.body(run.source, run.sourceX, run.sourceY, FLOW, BODY_TILES)
                                .draw(run.source.pixelCenter(run.sourceX, run.sourceY), RNG)) {
                    run.source = null;
                    run.remaining = Math.min(run.remaining, Math.max(1, run.tile.res / 4));
                } else {
                    fed = true;
                    if ((run.travelled & 1) == 1) run.volume++;
                }
            }
            if (run.waterRow >= 0 && run.row > run.waterRow) {
                // Into the water: the trickle ends where the surface is, and clouds up there.
                RUNS.remove(run);
                Vec3 at = run.tile.pixelCenter(run.column, Math.min(run.tile.ph - 1, run.waterRow));
                fogAt(level, at.add(run.tile.face.getStepX() * 0.1, -0.05, run.tile.face.getStepZ() * 0.1), run.colour);
                continue;
            }
            if (run.floor != null && run.row < run.tile.ph
                    && run.tile.pixelCenter(run.column, run.row).y - 0.5 / run.tile.res < run.floor.point().y - 1.0e-4) {
                // Something to stand on before the bottom edge: pool there instead.
                RUNS.remove(run);
                CanvasTile floor = tileAt(level, run.floor.pos(), Direction.UP, run.floor.point());
                if (floor != null && Math.abs(floor.oy - run.floor.point().y) < 0.05 && run.depth + 1 <= MAX_FLOW_DEPTH) {
                    stampAt(level, floor, run.floor.point(), run.colour, Math.max(1, run.volume), run.depth + 1);
                }
                continue;
            }
            if (run.row < run.tile.ph && !run.tile.paintable(run.column, run.row)) {
                // Something covers the face from here down (more of the block, or a block
                // pressed against it): the trickle stops at it, pooling on its top if it has one.
                RUNS.remove(run);
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
                // Water has come up the wall since the trickle started: it ends there and clouds up.
                RUNS.remove(run);
                Vec3 at = run.tile.pixelCenter(run.column, run.row);
                fogAt(level, at.add(run.tile.nx * 0.1, 0, run.tile.nz * 0.1), run.colour);
                continue;
            }
            if (run.row < run.tile.ph) {
                // The film gets thinner the further it has trickled.
                int thin = 1 + Math.min(2, run.travelled / (run.tile.res / 2));
                run.tile.paintThin(run.column, run.row, run.colour, now, lifetime, RNG, pattern, thin);
                run.row++;
                run.travelled++;
                if (!fed) run.remaining--;
                run.nextTick = now + run.interval;
                if (run.remaining <= 0 && run.volume > 1) {
                    // Out of film but still carrying blood: that blood is what creeps on, a
                    // unit spent every few pixels, so a heavy trickle reaches the edge and drips.
                    run.volume--;
                    run.remaining = Math.max(1, run.tile.res / 4);
                }
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

    /**
     * Blood reached the bottom edge of a vertical face: pool onto the floor block in front,
     * continue down the face of the block below, or drip off into the air.
     */
    private static void flowOffWallBottom(ClientLevel level, CanvasTile wall, int column, int remaining,
                                          int colour, int volume, int depth, boolean[] dropped) {
        flowOffWallBottom(level, wall, column, remaining, colour, volume, depth, dropped, null);
    }

    /** @param run the run that arrived at the bottom edge, so a continuation keeps drinking from its pool */
    private static void flowOffWallBottom(ClientLevel level, CanvasTile wall, int column, int remaining,
                                          int colour, int volume, int depth, boolean[] dropped, Run run) {
        Direction face = wall.face;
        Vec3 bottom = wall.pixelCenter(column, wall.ph - 1).add(0, -0.5 / wall.res, 0);

        if (wall.custom) {
            // A dripstone plane stands on the block below: pool at its foot.
            CanvasTile floor = tileAt(level, wall.pos.below(), Direction.UP, bottom);
            if (floor != null && Math.abs(floor.oy - bottom.y) < 0.05 && depth + 1 <= MAX_FLOW_DEPTH) {
                stampAt(level, floor, bottom, colour, Math.max(1, volume), depth + 1);
            } else if (!dropped[0]) {
                dropped[0] = true;
                BloodParticle.spawnDroplet(level, bottom.x, bottom.y - 0.05, bottom.z, colour, Math.max(1, volume), Double.NaN);
            }
            return;
        }

        // Blood that reaches water clouds up instead of running on below. For a face inside the
        // block (a fence post) "in front" is the block itself: a waterlogged fence.
        BlockPos inFront = planeAtBoundary(wall) ? wall.pos.relative(face) : wall.pos;
        if (inFluid(level, inFront)) {
            fogAt(level, bottom.add(face.getStepX() * 0.1, -0.05, face.getStepZ() * 0.1), colour);
            return;
        }
        // Water in front of the block below. Its surface sits a little under this edge (water
        // is not a full block tall), so the trickle carries on down that face as far as the
        // surface and clouds up there, touching the water instead of stopping with a dry gap.
        boolean waterBelow = inFluid(level, inFront.below());

        // Floor whose top is level with the wall's bottom edge: found by looking straight down
        // just in front of the edge, so a fence post pools at its own foot and a wall at the
        // block in front of it alike.
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

        // The wall continues below: keep trickling on the next block's face.
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
                        waterRow = (int) Math.floor((lower.oy - surface) * lower.res); // last row still above the water
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

        // Nothing there: the blood wraps around the bottom corner of the block (or of the
        // fence bar, whose underside is its own), then a drop lets go from the underside and
        // falls straight down.
        if (!edgeAtBoundary(wall, Direction.DOWN) || exposed(level, wall.pos, Direction.DOWN)) {
            CanvasTile under = tileAt(level, wall.pos, Direction.DOWN, bottom);
            if (under != null && Math.abs(under.oy - bottom.y) < 0.05) {
                SimpleBloodConfig.SurfaceSettings s = settings();
                boolean pattern = !"block".equals(s.highlightMode);
                double step = 1.0 / under.res;
                // Two pixels inward from the edge, along the direction the wall faces away from.
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

    // ------------------------------------------------------------------ moving blocks

    /** Tiles by block position, so a block that changes finds its blood at once. */
    private static final Map<Long, List<CanvasTile>> BY_POS = new HashMap<>();
    /** Blocks carrying blood that were just set, with the state they had; followed up next tick. */
    private static final Map<Long, BlockState> CHANGED = new LinkedHashMap<>();

    /**
     * The client level is about to set the block at {@code pos} (the old one is still there).
     * If it carries blood, remember what it was: next tick the blood follows the change, swinging
     * with a door, riding a piston, or staying put. Called for every block the client sets, so
     * it does nothing at all for blocks without blood.
     */
    public static void blockChanging(ClientLevel level, BlockPos pos) {
        try {
            if (BY_POS.isEmpty() || !RenderThread.on() || level != trackedLevel) return;
            long key = pos.asLong();
            if (!BY_POS.containsKey(key) || CHANGED.containsKey(key)) return;
            CHANGED.put(key, level.getBlockState(pos));
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.SURFACES, "following a block that changed", t);
        }
    }

    /** Where a tile goes when its block rides a piston, and the moving block it rides on. */
    private record Move(CanvasTile tile, BlockPos to, Direction by, PistonMovingBlockEntity mover) {}

    /** One painted pixel on its way to another face: where it was, which way that face looked. */
    private record Carried(Vec3 at, Vec3 normal, CanvasTile.Lifted pixel, int res) {}

    private static void followChanges(ClientLevel level) {
        if (CHANGED.isEmpty()) return;
        List<Move> moves = new ArrayList<>();
        for (Map.Entry<Long, BlockState> e : CHANGED.entrySet()) {
            BlockPos pos = BlockPos.of(e.getKey());
            BlockState was = e.getValue(), is = level.getBlockState(pos);
            List<CanvasTile> here = BY_POS.get(e.getKey());
            if (was == is || here == null || here.isEmpty()) continue;
            if (pistonMoves(level, pos, was, is, here, moves)) continue;
            BlockMotion.Motion motion = BlockMotion.of(level, pos, was, is);
            if (motion != null) carry(level, pos, new ArrayList<>(here), motion);
            // Anything else is looked at by the regular block checks.
        }
        CHANGED.clear();
        if (moves.isEmpty()) return;
        // Lift every riding tile first: a row of pushed blocks moves into each other's places.
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
            if (there != null) remove(there); // left behind where a moving block now is
            putTile(key, copy);
        }
    }

    /**
     * Blood on a block a piston moves goes with it: a pushed or pulled block takes all of
     * its blood, a piston arm going out takes the blood on the face of the piston, and coming
     * back brings the blood on its head. Returns whether this change was a piston at work.
     */
    private static boolean pistonMoves(ClientLevel level, BlockPos pos, BlockState was, BlockState is,
                                       List<CanvasTile> here, List<Move> moves) {
        boolean left = is.isAir() || is.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON);
        if (left && !(was.getBlock() instanceof PistonHeadBlock)) {
            // Pushed or pulled: the block now rides a moving piston next door.
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
            // The arm goes out: what is on the face of the piston goes out on its head.
            Direction facing = is.getValue(PistonBaseBlock.FACING);
            BlockPos head = pos.relative(facing);
            PistonMovingBlockEntity m = level.getBlockEntity(head) instanceof PistonMovingBlockEntity p ? p : null;
            for (CanvasTile t : here) if (onOuterFace(t, pos, facing)) moves.add(new Move(t, head, facing, m));
            return true;
        }
        if (was.getBlock() instanceof PistonHeadBlock && left) {
            // The arm comes back: what is on the plate of the head comes back onto the piston.
            Direction facing = was.getValue(PistonHeadBlock.FACING);
            BlockPos base = pos.relative(facing.getOpposite());
            if (!(level.getBlockEntity(base) instanceof PistonMovingBlockEntity m)) return false; // broken, not pulled in
            for (CanvasTile t : here) if (onOuterFace(t, pos, facing)) moves.add(new Move(t, base, facing.getOpposite(), m));
            return true; // the blood on the arm itself drips off with the regular checks
        }
        if (is.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON)
                && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity m && m.isSourcePiston()) {
            // The piston stands in for its arm while that retracts: its blood holds still till then.
            for (CanvasTile t : here) {
                t.mover = m;
                t.followsMover = false;
            }
            return true;
        }
        return false;
    }

    /** Whether a tile lies on the outer face of the block towards {@code side}. */
    private static boolean onOuterFace(CanvasTile t, BlockPos pos, Direction side) {
        if (t.custom || t.face != side) return false;
        int edge = switch (side.getAxis()) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        } + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
        return Math.abs(planeCoord(t, side) - edge) < 1.0e-3;
    }

    /**
     * The parts of a block moved where it stands (a door swung, a gate opened, a button went
     * in): every painted pixel goes where its bit of the block went, onto the face it ends up
     * on, keeping its depth and age. Pixels that find no face there drip off.
     */
    private static void carry(ClientLevel level, BlockPos pos, List<CanvasTile> tiles, BlockMotion.Motion motion) {
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
            }
        }
        // What has nowhere to go falls off, a drop for every few pixels.
        for (int i = 0; i < lost.size(); i += 6) {
            Carried c = lost.get(i);
            BloodParticle.spawnDroplet(level, c.at().x, c.at().y, c.at().z, c.pixel().rgb(),
                    Math.min(c.res() / 2, 1 + c.pixel().depth()), c.at().y - MAX_FALL);
        }
    }

    /**
     * A tile came to rest after riding a piston. Where the block it rode is there again as it
     * was, the tile simply stays; where it changed (the face of the piston went out onto the
     * head, or back), the blood is set down on the faces of what is there now.
     *
     * @return whether the tile is still there to be checked as usual
     */
    private static boolean settle(ClientLevel level, CanvasTile tile) {
        BlockState current = level.getBlockState(tile.pos);
        if (current == tile.state) {
            refreshVisibility(level, tile);
            return true;
        }
        if (current.isAir()) return true; // gone after all: the usual checks let it drip
        List<CanvasTile> one = new ArrayList<>(1);
        one.add(tile);
        carry(level, tile.pos, one, STAY);
        return false;
    }

    /** Blood that stays where it is, set down again on whatever faces are there now. */
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

    // ------------------------------------------------------------------ dripstone

    /**
     * Coats a pointed dripstone's two cross-planes with blood at the tip (masked to the stone
     * in its texture) and starts runs down them.
     *
     * @param amount 0..1 how much blood
     */
    /**
     * Tiles for every unculled quad of a cross-model block, one per drawn face, built from
     * the model's own baked geometry: pixels sit exactly on the sprite's texels and each tile
     * is masked by its quad's part of the texture. Opposite faces of the same plane are
     * linked as twins so paint shows on both sides. Cached in {@link #TILES}.
     */
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
        // Link opposite faces of the same plane.
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

    /**
     * The random nudge the renderer gives this block's model (grass and flowers sit a little
     * off centre, differently in every block); zero for everything else.
     */
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

    /** A tile lying exactly on one baked quad, moved by the model's random offset. */
    private static CanvasTile quadTile(CanvasTile.Key key, BlockPos pos, BlockState state,
                                       BlockToneSampler.QuadInfo quad, int res, Vec3 offset) {
        // Corners by their sprite-space UV: origin = min u, min v; u-end = max u, min v; v-end = min u, max v.
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
        if (o < 0 || ue < 0 || ve < 0) return null; // not an axis-aligned texture mapping
        Vec3 origin = quad.corners()[o].add(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
        Vec3 uVec = quad.corners()[ue].subtract(quad.corners()[o]);
        Vec3 vVec = quad.corners()[ve].subtract(quad.corners()[o]);
        double w = uVec.length(), h = vVec.length();
        if (w < 1e-4 || h < 1e-4) return null;
        Vec3 u = uVec.scale(1.0 / w), v = vVec.scale(1.0 / h);
        // Pixel grid = the sprite texels this quad covers (scaled for the chunky 8 px mode).
        int[] size = BlockToneSampler.spriteSize(quad.sprite());
        int pw = Math.max(1, Math.round((maxU - minU) * size[0] * res / 16.0f));
        int ph = Math.max(1, Math.round((maxV - minV) * size[1] * res / 16.0f));
        pw = Math.min(BloodCanvasAtlas.TILE, pw);
        ph = Math.min(BloodCanvasAtlas.TILE, ph);
        boolean[] mask = BlockToneSampler.alphaMask(quad.sprite(), minU, maxU, minV, maxV, pw, ph);
        CanvasTile tile = new CanvasTile(key, pos, state, origin, u, v, w, h, res, now, mask, pw, ph);
        // The quad's own facing (vertex winding) decides which side is the front.
        Vec3 c0 = quad.corners()[0], c1 = quad.corners()[1], c2 = quad.corners()[2];
        Vec3 geom = c1.subtract(c0).cross(c2.subtract(c0));
        if (geom.lengthSqr() > 1e-8 && geom.dot(new Vec3(tile.nx, tile.ny, tile.nz)) < 0) {
            tile.flipNormal();
        }
        return tile;
    }

    /**
     * Tiles for the drawn rectangles of a sign (board, post, plank), built once per block and
     * cached in {@link #TILES} under keys 300+. Plain planes without a texture mask: sign
     * wood is opaque.
     */
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
                tile = new CanvasTile(key, pos, state, origin, r.u(), r.v(), r.w(), r.h(), res, now, null);
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

    /**
     * The plane tile nearest to the point, front side facing it. With {@code face} given,
     * planes mostly facing that way are preferred (the side of a sign board a spill goes to).
     */
    private static CanvasTile nearestPlane(List<CanvasTile> tiles, Vec3 point, Direction face) {
        CanvasTile best = null;
        double bestDist = Double.MAX_VALUE;
        for (CanvasTile t : tiles) {
            if (face != null && axisDirection(t.nx, t.ny, t.nz) != face) continue;
            Vec3 c = t.center();
            double d = Math.abs((point.x - c.x) * t.nx + (point.y - c.y) * t.ny + (point.z - c.z) * t.nz) * 4;
            // Prefer the face looking toward the point (its twin is painted along anyway).
            double facing = (point.x - c.x) * t.nx + (point.y - c.y) * t.ny + (point.z - c.z) * t.nz;
            if (facing < 0) d += 0.01;
            // And the rectangle the point actually lies in: a sign post and its board share
            // planes, and a hit on the board must not end up on the post.
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
            if (tile.twin != null && tile.twin.hashCode() < tile.hashCode()) continue; // one of each pair
            // Splat near the top of the stone: the tip is where it went in.
            int px = tile.pw / 2 + RNG.nextInt(3) - 1;
            int py = Math.max(1, (int) (tile.ph * (0.15 + RNG.nextDouble() * 0.2)));
            int pixels = Math.max(3, Math.round((6 + amount * 18) * (res * res / 256.0f)));
            tile.stamp(px, py, pixels, colour, now, lifetime, RNG, pattern, null);
            // Two runs creeping down the spike.
            for (int r = 0; r < 2; r++) {
                int col = clampPixel(px + RNG.nextInt(5) - 2, tile.pw);
                startRun(tile, col, Math.min(tile.ph - 1, py + 1), (int) (tile.ph * (0.4 + amount * 0.6)), 1 + Math.round(amount * 2), colour, 0);
            }
        }
    }

    // ------------------------------------------------------------------ ceiling drips

    /**
     * Blood on the underside of a block lets go a drop now and then; the more blood the
     * more often. Each drop takes a unit with it, so a ceiling stain slowly drains.
     */
    private static void dripFromCeiling(ClientLevel level, CanvasTile tile) {
        if (tile.face != Direction.DOWN || tile.isEmpty()) return;
        // Per 16-tick slice: a 20-pixel stain drips about every 4 seconds.
        if (RNG.nextDouble() > Math.min(0.5, tile.count() / 100.0)) return;
        int[] unit = tile.releaseUnit(RNG);
        if (unit == null) return;
        Vec3 p = tile.pixelCenter(unit[0], unit[1]);
        BloodParticle.spawnDroplet(level, p.x, p.y - 0.03, p.z, unit[2], 1, Double.NaN);
    }

    // ------------------------------------------------------------------ tiles

    /**
     * Returns the tile for the collision box face under {@code point}, creating it if the
     * block has such a face. {@code null} when the block has no collision there.
     */
    private static CanvasTile tileAt(ClientLevel level, BlockPos pos, Direction face, Vec3 point) {
        return tileAt(level, pos, face, point, true);
    }

    /** @param create make the tile when there is none yet (false: look up only) */
    private static CanvasTile tileAt(ClientLevel level, BlockPos pos, Direction face, Vec3 point, boolean create) {
        BlockState state = level.getBlockState(pos);
        if (sways(state)) return null;
        if (isHandDrawn(state)) {
            if (!create) return null;
            return nearestPlane(signTiles(level, pos, state), point, face);
        }
        if (isCrossModel(state)) {
            if (!create) return null;
            return nearestPlane(crossTiles(level, pos, state), point, null); // the nearer of the two planes
        }
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;
        // Fences, walls, stairs, gates, trapdoors...: the outline shape is a box (or a union of
        // boxes) that need not follow the drawn geometry. Use the model's own faces.
        ModelFaces faces = modelFaces(state, shape);
        if (faces != null) return modelTile(level, pos, state, faces, face, point, create);
        List<AABB> boxes = shape.toAabbs();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        double lx = point.x - pos.getX(), ly = point.y - pos.getY(), lz = point.z - pos.getZ();
        for (int i = 0; i < boxes.size(); i++) {
            AABB b = boxes.get(i);
            // Distance from the point to this box's face plane, plus how far outside the face rect it is.
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

    /**
     * Whether the (changed) block still has a collision face on this tile's plane covering
     * the tile centre, i.e. the paint can simply stay where it is.
     */
    private static boolean faceStillExists(ClientLevel level, CanvasTile tile, BlockState state) {
        if (tile.custom) return false;
        if (isQuadTile(tile)) {
            // The new state still draws a face with this facing and rectangle (a fence that
            // gained an arm keeps the blood on its post and other arms).
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

    /**
     * Which pixels of a block face can be seen: the spot just in front of each must not be
     * inside something solid, whether more of the same block (an anvil's neck standing on
     * its base, a lectern's column on its foot, a fence bar meeting the post) or a block
     * pressed against it. Blood keeps to the visible ones. Null when every pixel shows.
     */
    private static boolean[] visiblePixels(ClientLevel level, CanvasTile t) {
        if (t.custom || level == null) return null;
        boolean[] visible = null;
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

    /**
     * Whether a point just in front of a face is inside something that covers it: more of the
     * face's own block as drawn, or a neighbour that is solid there (a slab, a carpet, a wall).
     * Things one walks through (grass, flowers, torches) leave the blood under them showing.
     */
    private static boolean coveredAt(ClientLevel level, CanvasTile t, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        if (at.equals(t.pos)) return visibleSolidAt(level, x, y, z);
        BlockState state = level.getBlockState(at);
        if (state.isAir()) return false;
        double lx = x - at.getX(), ly = y - at.getY(), lz = z - at.getZ();
        for (AABB b : state.getCollisionShape(level, at).toAabbs()) {
            if (b.inflate(0.002).contains(lx, ly, lz)) return true;
        }
        return false;
    }

    /** Works out (again) which pixels of a block face show, e.g. after the block changed. */
    private static void refreshVisibility(ClientLevel level, CanvasTile t) {
        t.applyVisibility(visiblePixels(level, t));
    }

    /** Tile keys for model-quad tiles start here (cross-model planes use 100+). */
    private static final int QUAD_KEY_BASE = 200;

    /**
     * The tile for the model face under {@code point} on a block drawn from several boxes:
     * the axis-aligned quad facing {@code face} whose plane is nearest to the point and whose
     * rectangle holds it (or nearly). One tile per drawn face, keyed by quad index, so blood
     * sits on exactly what is rendered: a fence post's four sides and top, each arm's faces.
     */
    private static CanvasTile modelTile(ClientLevel level, BlockPos pos, BlockState state,
                                        ModelFaces faces, Direction face, Vec3 point, boolean create) {
        // The nearest tilted face that mostly looks the requested way, if it beats every flat one.
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

    /** The flat model face looking {@code face}-ward nearest to a block-local point; its score goes to {@code score[0]}. */
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

    /** Tiles for the faces of rotated elements start here. */
    private static final int SLANTED_KEY_BASE = 400;

    /**
     * The tile on a tilted model face (a lectern's desk): a plane tile built from the baked
     * quad itself, masked by its texture, like the planes of a plant. It holds paint but
     * nothing runs off it.
     */
    private static CanvasTile slantedTile(ClientLevel level, BlockPos pos, BlockState state, ModelFaces.Slanted s, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(pos.asLong(), Direction.UP, SLANTED_KEY_BASE + s.index());
        CanvasTile tile = TILES.get(key);
        if (tile != null && tile.state != state) { remove(tile); tile = null; }
        if (tile == null && !create) return null;
        if (tile == null) {
            List<BlockToneSampler.QuadInfo> quads = BlockToneSampler.allQuads(state);
            if (s.index() >= quads.size()) return null;
            SimpleBloodConfig.SurfaceSettings cfg = settings();
            tile = quadTile(key, pos, state, quads.get(s.index()), cfg.resolution == 16 ? 16 : 8, Vec3.ZERO);
            if (tile == null) return null;
            if (TILES.size() >= cfg.maxTiles) evictOldest();
            putTile(key, tile);
        }
        return tile;
    }

    /** The tile for one flat model face (a fence bar's top, a stair's step), keyed by its rectangle. */
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

    /**
     * Key part for a drawn face: its plane and rectangle to 1/32 block. Keyed by geometry, not
     * by quad index, so the tile survives a model change that merely adds or reorders quads.
     */
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

    /** Whether a point on the tile's plane (a spilled pixel) sits inside the block's own wood, out of sight. */
    private static boolean hiddenInBlock(CanvasTile t, Vec3 p) {
        Direction f = t.face;
        return visibleSolidAt(trackedLevel, p.x + f.getStepX() * 0.01, p.y + f.getStepY() * 0.01, p.z + f.getStepZ() * 0.01);
    }

    /** The tile's rectangle in world space. */
    private static AABB worldBounds(CanvasTile t) {
        double ax = t.ox + t.ux * t.w, ay = t.oy + t.uy * t.w, az = t.oz + t.uz * t.w;
        double bx = t.ox + t.vx * t.h, by = t.oy + t.vy * t.h, bz = t.oz + t.vz * t.h;
        double cx = ax + t.vx * t.h, cy = ay + t.vy * t.h, cz = az + t.vz * t.h;
        return new AABB(Math.min(Math.min(t.ox, ax), Math.min(bx, cx)), Math.min(Math.min(t.oy, ay), Math.min(by, cy)), Math.min(Math.min(t.oz, az), Math.min(bz, cz)),
                Math.max(Math.max(t.ox, ax), Math.max(bx, cx)), Math.max(Math.max(t.oy, ay), Math.max(by, cy)), Math.max(Math.max(t.oz, az), Math.max(bz, cz)));
    }

    /** Whether the tile's face lies on the block's boundary (where a neighbour could cover it). */
    private static boolean planeAtBoundary(CanvasTile t) {
        double plane = planeCoord(t, t.face);
        double edge = switch (t.face) {
            case UP -> t.pos.getY() + 1; case DOWN -> t.pos.getY();
            case SOUTH -> t.pos.getZ() + 1; case NORTH -> t.pos.getZ();
            case EAST -> t.pos.getX() + 1; default -> t.pos.getX();
        };
        return Math.abs(plane - edge) < 1.0e-3;
    }

    /** Whether the tile's edge in direction {@code d} sits on the block boundary. */
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

    /** Horizontal edges of a top face that end inside the block (a post top): always open. */
    private static int insetEdges(CanvasTile t) {
        int mask = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!edgeAtBoundary(t, d)) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    /** Whether the tile's face can be seen at all: inset faces always, boundary faces unless a neighbour covers them. */
    private static boolean tileExposed(ClientLevel level, CanvasTile t) {
        return t.custom || !planeAtBoundary(t) || exposed(level, t.pos, t.face);
    }

    /**
     * Whether blood at the tile's edge in direction {@code d} has air (not a neighbour, not
     * water) to pour into: an edge inside the block always does; a block-boundary edge asks
     * the neighbour.
     */
    private static boolean openBeyond(ClientLevel level, CanvasTile t, Direction d) {
        if (!edgeAtBoundary(t, d)) return true;
        return exposed(level, t.pos, d) && !inFluid(level, t.pos.relative(d));
    }

    /** A puddle whose block vanished, on its way down to the surface below. */
    private record FallingShape(List<int[]> pixels, CanvasTile from, Vec3 landing, int landTick) {}

    private static final List<FallingShape> FALLING = new ArrayList<>();

    /**
     * The block under a tile is gone. A puddle on a top face falls as a whole: its shape is
     * re-painted, pixel for pixel, on the first surface straight below once the drops would
     * have arrived, with a few straight-falling drops to carry the eye. Blood on other faces
     * simply drips off.
     */
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
                // A handful of drops falling straight down, spread over the puddle.
                int drops = Math.min(6, Math.max(1, count / 8));
                for (int i = 0; i < drops; i++) {
                    int[] px = snap.get(RNG.nextInt(snap.size()));
                    Vec3 p = tile.pixelCenter(px[0], px[1]);
                    BloodParticle.spawnDroplet(level, p.x, p.y, p.z, px[2], 0, tile.oy - MAX_FALL);
                }
                return;
            }
        }

        // No landing spot straight below (or not a puddle): drip off in a few drops.
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

    /** Lands every falling shape whose time has come: same pixels, same x/z, new height. */
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
            // Re-trace: the world may have changed while the blood was in the air.
            Vec3 c = f.from().center();
            Hit below = trace(level, c.add(0, -0.01, 0), c.add(0, -MAX_FALL, 0));
            if (below == null || below.face() != Direction.UP) continue;
            double y = below.point().y;
            BlockPos landing = BlockPos.containing(c.x, y + 0.05, c.z);
            if (inFluid(level, landing)) {
                // It fell into water: it clouds up there instead of painting the bottom.
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
                    target.paintPowder(tx, ty, px[2], now, lifetime, RNG); // snow lands as snow
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

    /**
     * A shape that landed next to a wall spatters it: a little blood a few pixels up the wall
     * that then runs back down into the puddle.
     */
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
                // The wall must stand right next to this pixel, on the landing level.
                BlockPos floorPos = BlockPos.containing(at.add(0, -0.02, 0));
                BlockPos wallPos = floorPos.relative(d).above();
                Vec3 edge = at.add(d.getStepX() * (1.0 / from.res), -0.02, d.getStepZ() * (1.0 / from.res));
                if (!BlockPos.containing(edge).equals(floorPos.relative(d))) continue; // not at the border
                if (!exposed(level, wallPos, d.getOpposite())) continue;
                Vec3 wallPoint = new Vec3(
                        d.getAxis() == Direction.Axis.X ? (d.getStepX() > 0 ? wallPos.getX() : wallPos.getX() + 1) : at.x,
                        y + 0.05,
                        d.getAxis() == Direction.Axis.Z ? (d.getStepZ() > 0 ? wallPos.getZ() : wallPos.getZ() + 1) : at.z);
                CanvasTile wall = tileAt(level, wallPos, d.getOpposite(), wallPoint);
                if (wall == null) continue;
                int height = 2 + RNG.nextInt(3);                       // pixels above the floor
                int row = Math.max(0, wall.ph - 1 - height);
                int col = clampPixel(wall.pixelX(wallPoint), wall.pw);
                wall.paint(col, row, px[2], now, lifetime, RNG, pattern);
                if (RNG.nextBoolean()) wall.paint(clampPixel(col + (RNG.nextBoolean() ? 1 : -1), wall.pw), row, px[2], now, lifetime, RNG, pattern);
                startRun(wall, col, row + 1, height + 1, 1, px[2], 1);
                splashes++;
            }
        }
    }

    /**
     * Puddle lighting on a tile depends on the paint just across its border, so whenever a
     * tile is painted its same-plane neighbours are re-uploaded too.
     */
    private static void touchNeighbours(CanvasTile tile) {
        if (tile.custom || tile.face != Direction.UP) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            CanvasTile n = TILES.get(new CanvasTile.Key(tile.pos.relative(d).asLong(), Direction.UP, 0));
            if (n != null && !n.isEmpty()) n.dirty = true;
        }
    }

    /** Direction in which a pixel just outside a tile lies (null when it is inside). */
    private static Direction edgeDirection(CanvasTile tile, int px, int py) {
        double dx, dy, dz;
        if (px < 0)              { dx = -tile.ux; dy = -tile.uy; dz = -tile.uz; }
        else if (px >= tile.pw)  { dx = tile.ux;  dy = tile.uy;  dz = tile.uz; }
        else if (py < 0)         { dx = -tile.vx; dy = -tile.vy; dz = -tile.vz; }
        else if (py >= tile.ph)  { dx = tile.vx;  dy = tile.vy;  dz = tile.vz; }
        else return null;
        return axisDirection(dx, dy, dz);
    }

    /** World point on this tile's plane, half a pixel past its border at (px, py). */
    private static Vec3 edgePoint(CanvasTile tile, int px, int py, Direction edgeDir) {
        int ex = clampPixel(px, tile.pw), ey = clampPixel(py, tile.ph);
        double step = 0.5 / tile.res;
        return tile.pixelCenter(ex, ey).add(edgeDir.getStepX() * step, edgeDir.getStepY() * step, edgeDir.getStepZ() * step);
    }

    /**
     * The flow context handed to the tiles: how a puddle continues past a block seam, where it
     * can pour over an edge, and how that pour becomes a run down the side.
     */
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

    /**
     * The relaxation found blood at the lip of pixel (px, py) wanting over the edge into the
     * side-face column. A run already in that column is fed from the pool by itself, so the
     * pixel keeps its unit (false); otherwise a new spout may open, subject to the pour gate
     * and the per-column cooldown. An unfed leftover trickle in the column is hooked up to
     * this pool instead and carries on.
     */
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

    // ------------------------------------------------------------------ ticking

    /** Called once per client tick. */
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
        drainPending(level);
        if (TILES.isEmpty()) return;
        // The world stands still (the pause menu, /tick freeze, a paused Flashback replay): the
        // blood does too. No running, spreading or drying until it moves again.
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
        // Work on a snapshot: painting during the loop (runs, falling shapes) creates tiles.
        CanvasTile[] snapshot = TILES.values().toArray(new CanvasTile[0]);
        for (CanvasTile tile : snapshot) {
            if (TILES.get(tile.key) != tile) continue; // already replaced or removed this tick
            // Spread the block checks over 16 ticks.
            if ((i++ & 15) == slice) {
                if (!level.hasChunkAt(tile.pos)) {
                    remove(tile);
                    continue;
                }
                if (tile.mover != null) {
                    // Riding a piston (or holding still while one retracts): wait for it to land.
                    if (!tile.mover.isRemoved() && level.getBlockEntity(tile.pos) == tile.mover) continue;
                    tile.mover = null;
                    tile.followsMover = false;
                    if (!settle(level, tile)) continue;
                }
                BlockState current = level.getBlockState(tile.pos);
                if (current != tile.state) {
                    if (faceStillExists(level, tile, current)) {
                        tile.rebind(current); // e.g. grass became dirt: the paint stays put
                        refreshVisibility(level, tile);
                    } else {
                        // The block is gone: the blood drops onto whatever is below.
                        releaseAsDroplets(level, tile);
                        remove(tile);
                        continue;
                    }
                }
                // A block placed over the blood hides it: it is simply gone.
                if (!tileExposed(level, tile)) {
                    remove(tile);
                    continue;
                }
                // Water has come over the blood (a flooded hole, a waterlogged block): what it
                // covers washes off and clouds up in the water.
                if (inFluid(level, tile.custom ? tile.pos : tile.pos.relative(tile.face))) {
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
                        continue;
                    }
                }
                dripFromCeiling(level, tile);
                // A neighbour that was broken opens a new edge for the puddle to run over.
                if (!tile.custom && tile.face == Direction.UP && !tile.isEmpty()) {
                    int mask = sideExposure(level, tile.pos) | insetEdges(tile);
                    int newlyOpen = mask & ~tile.exposedMask;
                    if (mask != tile.exposedMask) refreshVisibility(level, tile);
                    tile.exposedMask = mask;
                    if (newlyOpen != 0) runOffNewEdges(level, tile, newlyOpen);
                }
                if (s.rainWashes && level.isRainingAt(tile.pos.relative(tile.face))) {
                    tile.accelerateAging(16 * 6); // rain: ages roughly 7x faster
                }
            }
            // Aging is cheap per tile but there can be thousands: do each tile every 8 ticks.
            if (((i + now) & 7) == 0 && tile.age(now, RNG)) tile.dirty = true;
            if (tile.hasRipples()) tile.dirty = true; // ripples animate every tick
            if (tile.isEmpty()) {
                remove(tile);
            }
        }
        // The client tick runs on the render thread but outside any render pass, which is
        // the safe moment to write into the atlas texture.
        flushUploads();
    }

    /** Puddles relaxed per tick at most; the rest wait for the next tick (round robin). */
    private static final int RELAX_BUDGET = 48;
    private static int relaxCursor;

    /**
     * Runs the volume relaxation on active top-face tiles. A tile stays active while blood is
     * still moving on it; a settled puddle costs nothing until something changes it.
     */
    private static void relaxPuddles(ClientLevel level, SimpleBloodConfig.SurfaceSettings s) {
        if ((now & 3) != 0) return; // four times a second is plenty for a creeping film
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
        CanvasTile gone = TILES.remove(tile.key);
        unindex(tile);
        if (gone != null && gone != tile) unindex(gone);
        tile.dispose(BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null);
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

    /** Drops every tile (world change, config reload, resource reload). Any thread. */
    public static void clear() {
        if (!RenderThread.on()) {
            RenderThread.run(BloodSurfaces::clear);
            return;
        }
        PENDING.clear();
        pendingCount.set(0);
        com.simpleblood.mob.MobBlood.clear(); // the blood on mobs goes with it
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (CanvasTile t : TILES.values()) t.dispose(atlas);
        TILES.clear();
        BY_POS.clear();
        CHANGED.clear();
        FALLING.clear();
        RUNS.clear();
        RUN_COOLDOWN.clear();
        Footprints.clear();
        BlockToneSampler.clear();
        ModelFaces.clear();
        SignFaces.clear();
        SimpleBlood.LOGGER.debug("Surface blood cleared");
    }

    /** Uploads every dirty tile. Runs on the render thread, outside render passes. */
    private static void flushUploads() {
        if (TILES.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.get();
        float opacity = settings().opacity / 100.0f;
        for (CanvasTile t : TILES.values()) {
            if (t.dirty || t.slot < 0) t.refresh(atlas, now, opacity, FLOW);
        }
    }
}
