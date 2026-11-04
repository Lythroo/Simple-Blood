package com.simpleblood.surface;

import com.simpleblood.compat.BodyPose;
import com.simpleblood.compat.CreateContraptions;
import com.simpleblood.compat.RigidPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class ContraptionRides {
    private ContraptionRides() {}

    private static final int PLOT_X = -33_000_000;
    private static final int SPAN = 4096;
    private static final int SLOTS = 512;
    private static final int PUT_BACK_TICKS = 40;
    private static final double REACH = 1.5;

    static final class Ride {
        final Entity entity;
        final int slot;
        final BlockPos base;
        volatile Map<BlockPos, BlockState> blocks;
        volatile RigidPose tickPose;
        volatile Vec3 middle;
        volatile double radius;
        final RigidPose framePose = new RigidPose();
        long frame = Long.MIN_VALUE;
        CreateContraptions.PutBack putBack;

        Ride(Entity entity, int slot, Map<BlockPos, BlockState> blocks) {
            this.entity = entity;
            this.slot = slot;
            this.base = new BlockPos(PLOT_X + slot * SPAN, 0, 0);
            setBlocks(blocks);
        }

        void setBlocks(Map<BlockPos, BlockState> b) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (BlockPos p : b.keySet()) {
                x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
                x1 = Math.max(x1, p.getX() + 1); y1 = Math.max(y1, p.getY() + 1); z1 = Math.max(z1, p.getZ() + 1);
            }
            middle = new Vec3(base.getX() + (x0 + x1) * 0.5, base.getY() + (y0 + y1) * 0.5, base.getZ() + (z0 + z1) * 0.5);
            radius = 0.5 * Math.sqrt((double) (x1 - x0) * (x1 - x0) + (double) (y1 - y0) * (y1 - y0) + (double) (z1 - z0) * (z1 - z0));
            blocks = Map.copyOf(b);
        }

        BlockPos local(BlockPos plot) {
            return plot.subtract(base);
        }
    }

    private record Landing(Vec3 at, Vec3 normal, CanvasTile.Lifted pixel, BlockPos block, long until) {}

    private static final Map<Entity, Ride> RIDES = new IdentityHashMap<>();
    private static final AtomicReferenceArray<Ride> BY_SLOT = new AtomicReferenceArray<>(SLOTS);
    private static volatile Ride[] active = new Ride[0];
    private static final List<Landing> LANDING = new ArrayList<>();
    private record Held(CanvasTile tile, long pos, long until) {}

    private static final List<Held> HELD = new ArrayList<>();
    private static final int HOLD_TICKS = 4;
    private static long gameTime;

    static boolean isPlot(BlockPos pos) {
        return pos.getX() < PLOT_X + SLOTS * SPAN && pos.getX() >= PLOT_X - SPAN / 2;
    }

    static Ride at(BlockPos pos) {
        int slot = slotOf(pos);
        return slot < 0 ? null : BY_SLOT.get(slot);
    }

    private static int slotOf(BlockPos pos) {
        if (!isPlot(pos)) return -1;
        int slot = Math.floorDiv(pos.getX() - PLOT_X + SPAN / 2, SPAN);
        return slot < SLOTS ? slot : -1;
    }

    static BlockState state(BlockPos plot) {
        Ride r = at(plot);
        BlockState s = r == null ? null : r.blocks.get(r.local(plot));
        return s == null ? Blocks.AIR.defaultBlockState() : s;
    }

    static Object blockEntity(BlockPos plot) {
        Ride r = at(plot);
        return r == null ? null : CreateContraptions.blockEntity(r.entity, r.local(plot));
    }

    static BodyPose drawn(BlockPos pos, long frame, float partialTick) {
        Ride r = at(pos);
        if (r == null) return null;
        if (r.frame != frame) {
            r.frame = frame;
            if (!CreateContraptions.pose(r.entity, partialTick, r.base, r.framePose)) r.framePose.set(r.tickPose);
        }
        return r.framePose;
    }

    static BodyPose logical(BlockPos pos) {
        Ride r = at(pos);
        return r == null ? null : r.tickPose;
    }

    static List<BodyPose> crossing(Vec3 from, Vec3 to) {
        List<BodyPose> out = null;
        for (Ride r : active) {
            RigidPose pose = r.tickPose;
            Vec3 c = pose.toWorld(r.middle);
            if (distanceToSegment(c, from, to) > r.radius + REACH) continue;
            if (out == null) out = new ArrayList<>(2);
            out.add(pose);
        }
        return out == null ? List.of() : out;
    }

    static boolean any() {
        return active.length > 0;
    }

    private static void listActive() {
        active = RIDES.values().toArray(new Ride[0]);
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    static void tick(ClientLevel level) {
        if (!CreateContraptions.present()) return;
        gameTime = level.getGameTime();
        for (Entity e : level.entitiesForRendering()) {
            if (!CreateContraptions.is(e) || e.isRemoved() || RIDES.containsKey(e)) continue;
            board(level, e);
        }
        for (Iterator<Ride> it = RIDES.values().iterator(); it.hasNext(); ) {
            Ride r = it.next();
            if (r.entity.isRemoved() || r.entity.level() != level) {
                it.remove();
                listActive();
                alight(level, r);
                continue;
            }
            RigidPose now = new RigidPose();
            if (CreateContraptions.pose(r.entity, 1f, r.base, now)) r.tickPose = new RigidPose(now, r.tickPose);
            if ((BloodSurfaces.now() + r.slot) % 20 == 0) {
                Map<BlockPos, BlockState> b = CreateContraptions.blocks(r.entity);
                if (b != null && !b.equals(r.blocks)) r.setBlocks(b);
            }
            CreateContraptions.PutBack pb = CreateContraptions.putBack(r.entity);
            if (pb != null) r.putBack = pb;
        }
        land(level);
        for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
            Held h = it.next();
            if (h.until() > gameTime) continue;
            it.remove();
            BloodSurfaces.dropOff(level, h.tile());
        }
    }

    static boolean hold(BlockPos pos, List<CanvasTile> tiles) {
        if (!CreateContraptions.present() || tiles.isEmpty()) return false;
        for (CanvasTile t : tiles.toArray(new CanvasTile[0])) {
            HELD.add(new Held(t, pos.asLong(), gameTime + HOLD_TICKS));
            BloodSurfaces.removeTile(t);
        }
        com.simpleblood.Tally.count("create.held");
        return true;
    }

    private static void board(ClientLevel level, Entity e) {
        Map<BlockPos, BlockState> blocks = CreateContraptions.blocks(e);
        if (blocks == null) return;
        int slot = -1;
        for (int i = 0; i < SLOTS && slot < 0; i++) if (BY_SLOT.get(i) == null) slot = i;
        if (slot < 0) return;
        Ride r = new Ride(e, slot, blocks);
        RigidPose pose = new RigidPose();
        if (!CreateContraptions.pose(e, 1f, r.base, pose)) return;
        r.tickPose = pose;
        r.putBack = CreateContraptions.putBack(e);
        RIDES.put(e, r);
        BY_SLOT.set(slot, r);
        listActive();
        RigidPose taken = pose.snapped();
        if (!taken.isTranslation()) return;
        int moved = 0;
        for (Map.Entry<BlockPos, BlockState> b : blocks.entrySet()) {
            BlockPos world = BlockPos.containing(taken.toWorld(Vec3.atCenterOf(r.base.offset(b.getKey()))));
            List<CanvasTile> here = new ArrayList<>();
            List<CanvasTile> inWorld = BloodSurfaces.tilesAt(world);
            if (inWorld != null) here.addAll(inWorld);
            for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
                Held h = it.next();
                if (h.pos() != world.asLong()) continue;
                it.remove();
                here.add(h.tile());
            }
            if (here.isEmpty()) continue;
            BlockPos plot = r.base.offset(b.getKey());
            for (CanvasTile t : here) {
                if (t.state.getBlock() != b.getValue().getBlock()) continue;
                CanvasTile copy = new CanvasTile(t, new CanvasTile.Key(plot.asLong(), t.key.face(), t.key.box()), plot,
                        plot.getX() - world.getX(), plot.getY() - world.getY(), plot.getZ() - world.getZ());
                copy.state = b.getValue();
                BloodSurfaces.removeTile(t);
                BloodSurfaces.addTile(copy);
                moved++;
            }
        }
        if (moved > 0) com.simpleblood.Tally.add("create.board", moved);
    }

    private static void alight(ClientLevel level, Ride r) {
        BY_SLOT.set(r.slot, null);
        RigidPose snapped = r.tickPose.snapped();
        long until = level.getGameTime() + PUT_BACK_TICKS;
        Vec3 base = Vec3.atLowerCornerOf(r.base);
        for (CanvasTile t : BloodSurfaces.tilesIn(p -> slotOf(p) == r.slot)) {
            Vec3 n = new Vec3(t.nx, t.ny, t.nz);
            BlockPos localBlock = r.local(t.pos);
            BlockPos block = null;
            Vec3 bn = null;
            for (CanvasTile.Lifted l : t.lift()) {
                Vec3 plot = t.pixelCenter(l.px(), l.py());
                Vec3 at = null, normal = null;
                if (r.putBack != null) {
                    at = r.putBack.point(plot.subtract(base));
                    normal = r.putBack.turn(n);
                    if (block == null && at != null) {
                        Vec3 c = r.putBack.point(Vec3.atCenterOf(localBlock));
                        if (c != null) block = BlockPos.containing(c);
                    }
                }
                if (at == null || normal == null) {
                    at = snapped.toWorld(plot);
                    normal = snapped.turn(n);
                    if (block == null) block = BlockPos.containing(snapped.toWorld(Vec3.atCenterOf(t.pos)));
                }
                bn = normal;
                LANDING.add(new Landing(at, normal, l, block, until));
            }
            BloodSurfaces.removeTile(t);
            if (bn != null) com.simpleblood.Tally.count("create.alight");
        }
    }

    private static void land(ClientLevel level) {
        if (LANDING.isEmpty()) return;
        int now = BloodSurfaces.now();
        long time = level.getGameTime();
        for (Iterator<Landing> it = LANDING.iterator(); it.hasNext(); ) {
            Landing l = it.next();
            if (level.getBlockState(l.block()).isAir()) {
                if (time > l.until()) {
                    it.remove();
                    com.simpleblood.Tally.count("create.lost");
                }
                continue;
            }
            it.remove();
            Direction face = BloodSurfaces.axisDirection(l.normal().x, l.normal().y, l.normal().z);
            Vec3 at = l.at();
            CanvasTile t = BloodSurfaces.tileAt(level, l.block(), face, at);
            if (t != null && Math.abs((at.x - t.ox) * t.nx + (at.y - t.oy) * t.ny + (at.z - t.oz) * t.nz) <= 0.03
                    && t.place(l.pixel(), t.pixelX(at), t.pixelY(at), now)) {
                com.simpleblood.Tally.count("create.landed");
            } else {
                com.simpleblood.Tally.count("create.lost");
            }
        }
    }

    static void clear() {
        RIDES.clear();
        LANDING.clear();
        HELD.clear();
        for (int i = 0; i < SLOTS; i++) BY_SLOT.set(i, null);
        active = new Ride[0];
    }
}
