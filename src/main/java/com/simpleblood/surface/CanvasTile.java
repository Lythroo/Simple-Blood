package com.simpleblood.surface;

import com.simpleblood.BloodColor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pixel canvas glued onto one face of one collision box of a block.
 *
 * The canvas resolution is fixed per block ({@code res} pixels per block edge), so a full
 * block face is {@code res x res} pixels and a slab top or stair step gets proportionally
 * fewer. Every painted pixel carries a <em>depth</em> (units of blood). Fresh splats are
 * placed by Eden growth ({@link #stamp}); blood that lands in an existing puddle is added as
 * depth and then spread by {@link #relax}, a tiny cellular relaxation that conserves volume:
 * deep spots push blood to shallower neighbours, only genuinely deep blood spreads onto
 * empty pixels (surface tension), and blood at an open edge drains over it. Pixels age
 * individually, dry darker and shallower, and are eroded from the edge inward.
 *
 * Nothing here touches the GPU except {@link #refresh}, which rewrites this tile's
 * {@link NativeImage} and uploads it into the shared {@link BloodCanvasAtlas}.
 */
public final class CanvasTile {

    /** Tone indices stored per pixel; 0 = empty. */
    static final byte EMPTY = 0, DARK = 1, BASE = 2, LIGHT = 3;
    private static final float[] TONE_FACTOR = {0f, 0.87f, 1.0f, 1.28f};

    /** Deepest a pixel can get; deeper blood spreads out instead. */
    static final int MAX_DEPTH = 6;
    /** Depth from which blood spreads onto empty pixels of the same surface. */
    private static final int SPREAD_DEPTH = 3;

    /** Identity of the surface this tile covers. */
    public record Key(long pos, Direction face, int box) {}

    /** Answers whether a pixel just outside this tile (on the neighbouring face) is painted. */
    interface NeighbourLookup {
        boolean filledAcross(CanvasTile tile, int px, int py);
    }

    /** What the relaxation may do with blood that reaches a tile border. */
    interface FlowContext extends NeighbourLookup {
        /**
         * The tile continuing this surface across the border at (px, py) (created on demand),
         * with the matching pixel written to {@code xy}; {@code null} when the surface ends.
         */
        CanvasTile across(CanvasTile tile, int px, int py, int[] xy, boolean create);

        /** Whether blood at the border pixel (px, py) can pour over the block edge there. */
        boolean canDrain(CanvasTile tile, int px, int py);

        /** Pours one unit of blood over the edge at (px, py) into a run down the side; false if it cannot right now. */
        boolean drain(CanvasTile tile, int px, int py, int colour);
    }

    /** Ticks a landing ripple takes to expand and fade. */
    static final int RIPPLE_TICKS = 22;

    final Key key;
    final BlockPos pos;
    final Direction face;
    BlockState state;
    /** A free-standing plane (e.g. a dripstone cross-plane) instead of a block face. */
    final boolean custom;
    /** Plane normal; for block faces this equals the face direction. */
    double nx, ny, nz;
    /** Pixels that may be painted (null = all); custom planes mask to the block texture. */
    private boolean[] mask;
    /** The tile on the other side of the same plane (cross models draw both faces). */
    CanvasTile twin;
    /** For banner cloth: the world point on the bar it swings from, and the bar's direction (null: still). */
    Vec3 swayPivot, swayAxis;
    private boolean propagating;

    // Face rectangle in world space: origin corner, unit axes, extents (blocks).
    final double ox, oy, oz;
    final double ux, uy, uz;
    final double vx, vy, vz;
    final double w, h;
    final int pw, ph;              // pixel dimensions
    final int res;

    private final byte[] tone;     // chosen tone per pixel (0 = empty)
    private final byte[] baseTone; // tone suggested by the block texture underneath
    private final byte[] depth;    // units of blood per pixel (0 = empty)
    private final int[] rgb;       // blood colour per pixel (0xRRGGBB)
    private final int[] born;      // tick the pixel was painted
    private final int[] life;      // lifetime in ticks (jittered per pixel)
    private final byte[] thin;     // 0 = pooled blood, 1..3 = increasingly thin running film
    private final boolean[] powder;// snow pile pixels: flat, never flow, no relief
    private final boolean[] moved; // scratch: received blood during the current relax step
    private int count;
    /** Bit per horizontal direction (ordinal) whose side of this block is open to air. */
    int exposedMask;
    /** Set whenever volume changes; cleared once a relax step moves nothing. */
    boolean active;

    private NativeImage image;
    private NativeImage spec; // labPBR specular twin of image, only while Iris reads it
    int slot = -1;
    boolean dirty;
    int lastTouched;
    int light;            // packed light, refreshed every few ticks by the renderer
    int lightTick = -100;
    boolean onSeeThrough; // on a block you can see through (glass, ice...), so drawn after see-through terrain; refreshed with the light
    /** Block light (0..15) the tile gives itself: glowing blood, strongest while fresh. */
    int glow;
    /** The moving block this tile rides while a piston moves it (null: standing still). */
    net.minecraft.world.level.block.piston.PistonMovingBlockEntity mover;
    /** Whether the tile is drawn where {@link #mover} is this frame (false: it holds still meanwhile). */
    boolean followsMover;
    /** Active landing ripples: {centre x, centre y, start tick, strength x1000, reach x10 (pixels)}. */
    private final List<int[]> ripples = new ArrayList<>(2);

    /**
     * A tile on an arbitrary plane: origin corner, unit axes u (right) and v (down), extents
     * in blocks. Used for cross-model blocks such as pointed dripstone.
     */
    CanvasTile(Key key, BlockPos pos, BlockState state, Vec3 origin, Vec3 u, Vec3 v, double w, double h,
               int res, int now, boolean[] mask) {
        this(key, pos, state, origin, u, v, w, h, res, now, mask,
                Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(w * res))),
                Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(h * res))));
    }

    /** As above with an explicit pixel grid, so pixels can sit exactly on the sprite's texels. */
    CanvasTile(Key key, BlockPos pos, BlockState state, Vec3 origin, Vec3 u, Vec3 v, double w, double h,
               int res, int now, boolean[] mask, int pw, int ph) {
        this.key = key;
        this.pos = pos.immutable();
        this.face = Direction.UP;
        this.custom = true;
        this.state = state;
        this.res = res;
        this.lastTouched = now;
        this.ox = origin.x; this.oy = origin.y; this.oz = origin.z;
        this.ux = u.x; this.uy = u.y; this.uz = u.z;
        this.vx = v.x; this.vy = v.y; this.vz = v.z;
        this.w = w; this.h = h;
        Vec3 n = u.cross(v).normalize();
        this.nx = n.x; this.ny = n.y; this.nz = n.z;
        this.pw = Math.max(1, Math.min(BloodCanvasAtlas.TILE, pw));
        this.ph = Math.max(1, Math.min(BloodCanvasAtlas.TILE, ph));
        int count = this.pw * this.ph;
        this.tone = new byte[count];
        this.baseTone = new byte[count];
        this.depth = new byte[count];
        this.rgb = new int[count];
        this.born = new int[count];
        this.life = new int[count];
        this.thin = new byte[count];
        this.powder = new boolean[count];
        this.moved = new boolean[count];
        this.mask = mask != null && mask.length == count ? mask : null;
        java.util.Arrays.fill(baseTone, BASE);
    }

    CanvasTile(Key key, BlockPos pos, Direction face, BlockState state, AABB box, int res, int now) {
        this.key = key;
        this.pos = pos.immutable();
        this.face = face;
        this.custom = false;
        this.nx = face.getStepX(); this.ny = face.getStepY(); this.nz = face.getStepZ();
        this.state = state;
        this.res = res;
        this.lastTouched = now;

        double minX = pos.getX() + box.minX, maxX = pos.getX() + box.maxX;
        double minY = pos.getY() + box.minY, maxY = pos.getY() + box.maxY;
        double minZ = pos.getZ() + box.minZ, maxZ = pos.getZ() + box.maxZ;
        double sx = box.maxX - box.minX, sy = box.maxY - box.minY, sz = box.maxZ - box.minZ;

        // Texture axes: u runs "right", v runs "down" (so wall runs grow along +v).
        switch (face) {
            case UP    -> { ox = minX; oy = maxY; oz = minZ; ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1;  w = sx; h = sz; }
            case DOWN  -> { ox = minX; oy = minY; oz = maxZ; ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = -1; w = sx; h = sz; }
            case NORTH -> { ox = maxX; oy = maxY; oz = minZ; ux = -1; uy = 0; uz = 0; vx = 0; vy = -1; vz = 0; w = sx; h = sy; }
            case SOUTH -> { ox = minX; oy = maxY; oz = maxZ; ux = 1; uy = 0; uz = 0; vx = 0; vy = -1; vz = 0;  w = sx; h = sy; }
            case WEST  -> { ox = minX; oy = maxY; oz = minZ; ux = 0; uy = 0; uz = 1; vx = 0; vy = -1; vz = 0;  w = sz; h = sy; }
            default    -> { ox = maxX; oy = maxY; oz = maxZ; ux = 0; uy = 0; uz = -1; vx = 0; vy = -1; vz = 0; w = sz; h = sy; } // EAST
        }
        this.pw = Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(w * res)));
        this.ph = Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(h * res)));
        int n = pw * ph;
        this.tone = new byte[n];
        this.baseTone = new byte[n];
        this.depth = new byte[n];
        this.rgb = new int[n];
        this.born = new int[n];
        this.life = new int[n];
        this.thin = new byte[n];
        this.powder = new boolean[n];
        this.moved = new boolean[n];
        java.util.Arrays.fill(baseTone, BASE);
    }

    /**
     * This tile carried {@code (dx, dy, dz)} blocks to {@code pos} (a block a piston pushed):
     * same pixels, same age, everything moved along. The copy has no GPU slot yet.
     */
    CanvasTile(CanvasTile src, Key key, BlockPos pos, int dx, int dy, int dz) {
        this.key = key;
        this.pos = pos.immutable();
        this.face = src.face;
        this.custom = src.custom;
        this.state = src.state;
        this.res = src.res;
        this.lastTouched = src.lastTouched;
        this.nx = src.nx; this.ny = src.ny; this.nz = src.nz;
        this.mask = src.mask == null ? null : src.mask.clone();
        this.swayPivot = src.swayPivot == null ? null : src.swayPivot.add(dx, dy, dz);
        this.swayAxis = src.swayAxis;
        this.ox = src.ox + dx; this.oy = src.oy + dy; this.oz = src.oz + dz;
        this.ux = src.ux; this.uy = src.uy; this.uz = src.uz;
        this.vx = src.vx; this.vy = src.vy; this.vz = src.vz;
        this.w = src.w; this.h = src.h;
        this.pw = src.pw; this.ph = src.ph;
        this.tone = src.tone.clone();
        this.baseTone = src.baseTone.clone();
        this.depth = src.depth.clone();
        this.rgb = src.rgb.clone();
        this.born = src.born.clone();
        this.life = src.life.clone();
        this.thin = src.thin.clone();
        this.powder = src.powder.clone();
        this.moved = new boolean[src.moved.length];
        this.count = src.count;
        this.exposedMask = src.exposedMask;
        this.light = src.light;
        this.glow = src.glow;
        this.dirty = true;
    }

    // ------------------------------------------------------------------ geometry

    /** World position of the centre of pixel (px, py); also valid for out-of-range pixels. */
    Vec3 pixelCenter(int px, int py) {
        double fu = (px + 0.5) / pw * w;
        double fv = (py + 0.5) / ph * h;
        return new Vec3(ox + ux * fu + vx * fv, oy + uy * fu + vy * fv, oz + uz * fu + vz * fv);
    }

    /** Pixel column for a world point projected on this face (may be out of range). */
    int pixelX(Vec3 p) {
        double d = (p.x - ox) * ux + (p.y - oy) * uy + (p.z - oz) * uz;
        return (int) Math.floor(d / w * pw);
    }

    int pixelY(Vec3 p) {
        double d = (p.x - ox) * vx + (p.y - oy) * vy + (p.z - oz) * vz;
        return (int) Math.floor(d / h * ph);
    }

    Vec3 center() {
        return new Vec3(ox + (ux * w + vx * h) * 0.5, oy + (uy * w + vy * h) * 0.5, oz + (uz * w + vz * h) * 0.5);
    }

    boolean inside(int px, int py) {
        return px >= 0 && py >= 0 && px < pw && py < ph;
    }

    public int count() { return count; }
    public boolean isEmpty() { return count == 0; }

    /** Units of blood at a pixel (0 when empty or out of range). */
    int depthAt(int px, int py) {
        return inside(px, py) ? depth[py * pw + px] : 0;
    }

    /** Colour of a painted pixel (0 when empty). */
    int colourAt(int px, int py) {
        int i = py * pw + px;
        return tone[i] == EMPTY ? 0 : rgb[i];
    }

    boolean filled(int px, int py) {
        return inside(px, py) && tone[py * pw + px] != EMPTY;
    }

    /** How far through its life the blood at a pixel is (0 fresh .. 1 gone). */
    float ageAt(int px, int py, int now) {
        if (!inside(px, py)) return 0f;
        int i = py * pw + px;
        return (now - born[i]) / (float) Math.max(1, life[i]);
    }

    /** Units of blood at a pixel while it is still wet enough to stick to a foot, else 0. */
    int wetDepthAt(int px, int py, int now) {
        if (!inside(px, py)) return 0;
        int i = py * pw + px;
        if (tone[i] == EMPTY || thin[i] != 0) return 0;      // nothing, or a print/film
        if (now - born[i] > life[i] * 0.55f) return 0;        // drying: no longer sticky
        return depth[i];
    }

    boolean powderAt(int px, int py) {
        return inside(px, py) && powder[py * pw + px];
    }

    /** A foot takes one unit out of a pooled pixel (depth 2+). */
    boolean scoop(int px, int py) {
        if (!inside(px, py)) return false;
        int i = py * pw + px;
        if (tone[i] == EMPTY || depth[i] < 2) return false;
        takeUnit(i);
        return true;
    }

    /**
     * Paints one footprint pixel: only onto bare surface, as a thin film that never pools
     * (so a trail cannot start runs), or as a powder pixel for snow.
     */
    void paintPrint(int px, int py, int colour, int now, int lifetime, RandomSource rng, boolean pattern, boolean powder) {
        if (!inside(px, py) || tone[py * pw + px] != EMPTY) return;
        if (powder) {
            paintPowder(px, py, colour, now, lifetime, rng);
        } else {
            paintThin(px, py, colour, now, lifetime, rng, pattern, 1);
            active = false; // a film cannot flow: no need to relax the tile for it
        }
    }

    /** The block underneath changed but still offers this face: keep painting on it. */
    void rebind(BlockState newState) {
        this.state = newState;
    }

    /** Whether blood can be at (px, py): on the tile, and not hidden behind something solid. */
    boolean paintable(int px, int py) {
        return inside(px, py) && (mask == null || mask[py * pw + px]);
    }

    /**
     * Restricts a block face to the pixels that can be seen ({@code visible}, null = all):
     * the rest is covered by another part of the same block or a block pressed against it.
     * Blood already painted on a covered pixel is gone. Custom planes keep their texture mask.
     */
    void applyVisibility(boolean[] visible) {
        if (custom) return;
        if (visible != null && visible.length != tone.length) return;
        mask = visible;
        if (visible == null) return;
        for (int i = 0; i < tone.length; i++) {
            if (!visible[i] && tone[i] != EMPTY) {
                tone[i] = EMPTY;
                depth[i] = 0;
                count--;
                dirty = true;
            }
        }
    }

    /** Turns a custom plane around (its front becomes its back). */
    void flipNormal() {
        nx = -nx; ny = -ny; nz = -nz;
    }

    boolean hasRipples() { return !ripples.isEmpty(); }

    /** Whether this surface lies flat and faces up, so liquid can pool on it. */
    boolean facesUp() {
        // Custom planes (boards, banners, model faces) are all filed under UP; their real
        // orientation is the normal.
        return custom ? ny > 0.9 : face == Direction.UP;
    }

    /**
     * Starts a ripple ring expanding from (cx, cy) where {@code units} of blood landed in the
     * pool; keeps at most a few alive per tile. Only pools lying flat ripple (a wave on a wall
     * or a ceiling makes no sense), and only when both the pool and the drop are big enough:
     * a drip into a speck does nothing, a splash into a wide pool sends out a ring that
     * reaches further and shows a little more. Kept faint either way.
     */
    void addRipple(int cx, int cy, int now, int units) {
        if (!facesUp()) return;
        int area = pw * ph;
        int minPool = Math.max(8, area / 10);
        if (count < minPool || units < Math.max(2, area / 24)) return;
        float pool = Math.min(1f, count / (area * 0.5f));
        float drop = Math.min(1f, units / (area / 12f));
        float strength = pool * (0.5f + 0.5f * drop);
        // The ring dies out at the rim of the pool, not somewhere past it.
        double reach = Math.sqrt(count / Math.PI) * 1.1 + 1.0;
        if (ripples.size() >= 3) ripples.remove(0);
        ripples.add(new int[]{cx, cy, now, Math.round(strength * 1000), (int) Math.round(reach * 10)});
        dirty = true;
    }

    /** Snapshot of every painted pixel as {px, py, colour, depth}, e.g. to move the shape. */
    List<int[]> snapshot() {
        List<int[]> out = new ArrayList<>(count);
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) out.add(new int[]{i % pw, i / pw, rgb[i], depth[i], powder[i] ? 1 : 0});
        }
        return out;
    }

    /** One painted pixel with everything it carries, lifted off to be set down elsewhere. */
    record Lifted(int px, int py, byte tone, byte depth, int rgb, int born, int life, byte thin, boolean powder) {}

    /** Every painted pixel, as it is: depth, colour, age, film. */
    List<Lifted> lift() {
        List<Lifted> out = new ArrayList<>(count);
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) {
                out.add(new Lifted(i % pw, i / pw, tone[i], depth[i], rgb[i], born[i], life[i], thin[i], powder[i]));
            }
        }
        return out;
    }

    /**
     * Sets a lifted pixel down at (px, py) as it was (a door swung, the blood went with it).
     * Two landing on one pixel pool together. False when the pixel cannot take blood.
     */
    boolean place(Lifted l, int px, int py, int now) {
        if (!paintable(px, py)) return false;
        int i = py * pw + px;
        if (tone[i] == EMPTY) {
            count++;
            tone[i] = l.tone();
            depth[i] = l.depth();
            born[i] = l.born();
            life[i] = l.life();
            thin[i] = l.thin();
            powder[i] = l.powder();
        } else {
            depth[i] = (byte) Math.min(MAX_DEPTH, depth[i] + l.depth());
            born[i] = Math.max(born[i], l.born());
        }
        rgb[i] = l.rgb();
        dirty = true;
        active = !powder[i];
        lastTouched = now;
        return true;
    }

    /** Number of painted 4-neighbours of (px, py). */
    int filledNeighbours(int px, int py) {
        int n = 0;
        if (filled(px + 1, py)) n++;
        if (filled(px - 1, py)) n++;
        if (filled(px, py + 1)) n++;
        if (filled(px, py - 1)) n++;
        return n;
    }

    void setBaseTones(byte[] tones) {
        if (tones != null && tones.length == baseTone.length) {
            System.arraycopy(tones, 0, baseTone, 0, tones.length);
        }
    }

    // ------------------------------------------------------------------ painting

    /**
     * Deposits {@code n} units of blood at (px, py). On an empty spot the units become a splat
     * of depth-1 pixels laid out by Eden growth: an empty pixel next to the blob is chosen with
     * probability proportional to the square of its filled neighbours and its closeness to the
     * impact point, which gives a dense core with a ragged stepped edge. Landing in an existing
     * puddle instead adds the units as depth around the impact; {@link #relax} spreads them.
     *
     * @param spill receives pixels the growth wanted to place outside this tile
     * @return the number of units placed into this tile
     */
    int stamp(int px, int py, int n, int colour, int now, int lifetime, RandomSource rng,
              boolean pattern, List<int[]> spill) {
        return stamp(px, py, n, colour, now, lifetime, rng, pattern, spill, 0, 0);
    }

    /**
     * As {@link #stamp(int, int, int, int, int, int, RandomSource, boolean, List)}, with the
     * blob stretched along (bu, bv) in pixel space: blood that lands while moving spatters
     * further in the direction it travelled, the way real drops do.
     */
    int stamp(int px, int py, int n, int colour, int now, int lifetime, RandomSource rng,
              boolean pattern, List<int[]> spill, double bu, double bv) {
        if (face == Direction.UP && filled(px, py)) {
            // Into the puddle: volume, not new outline.
            addRipple(px, py, now, n);
            addVolume(px, py, n, colour, now, lifetime, rng, pattern);
            return n;
        }

        Set<Long> blob = new HashSet<>();
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) blob.add(pack(i % pw, i / pw));
        }
        List<int[]> chosen = new ArrayList<>(n);
        boolean vertical = custom || face.getAxis().isHorizontal();

        if (filledNeighbours(px, py) > 0) {
            addRipple(px, py, now, n);
        }

        // Seed pixel first (if free).
        if (!blob.contains(pack(px, py))) {
            blob.add(pack(px, py));
            chosen.add(new int[]{px, py});
        }
        double R = Math.max(1.2, Math.sqrt(n) * 0.9);
        double bias = Math.sqrt(bu * bu + bv * bv);
        if (bias > 1.0e-6) { bu /= bias; bv /= bias; }
        bias = Math.min(1.0, bias);
        Map<Long, Integer> cand = new HashMap<>();
        int guard = 0;
        while (chosen.size() < n && guard++ < n * 8) {
            cand.clear();
            // Only grow from cells near the impact point so old puddle edges do not keep spreading.
            for (int[] c : chosen) addCandidates(c[0], c[1], blob, cand);
            if (cand.isEmpty()) addCandidates(px, py, blob, cand);
            if (cand.isEmpty()) break;

            double total = 0;
            long[] keys = new long[cand.size()];
            double[] weights = new double[cand.size()];
            int k = 0;
            for (Map.Entry<Long, Integer> e : cand.entrySet()) {
                int cx = unpackX(e.getKey()), cy = unpackY(e.getKey());
                double dx = cx - px, dy = cy - py;
                double dist2 = dx * dx + dy * dy;
                if (bias > 0.05) {
                    // Anisotropic: distance along the travel direction counts for less, so the
                    // blob elongates that way (and prefers the "ahead" side).
                    double par = dx * bu + dy * bv;
                    double perp2 = Math.max(0, dist2 - par * par);
                    double stretch = 1.0 + 2.0 * bias;
                    dist2 = (par / stretch) * (par / stretch) + perp2;
                    if (par < 0) dist2 *= 1.0 + bias; // behind the impact: shorter tail
                }
                double wgt = e.getValue() * e.getValue() * Math.exp(-dist2 / (R * R) * 0.8);
                if (vertical) wgt *= dy > 0 ? 1.8 : (dy < 0 ? 0.45 : 1.0); // blood runs down walls
                if (!inside(cx, cy)) wgt *= 0.6;                            // prefer staying on this face
                keys[k] = e.getKey();
                weights[k] = wgt;
                total += wgt;
                k++;
            }
            double r = rng.nextDouble() * total;
            int pick = 0;
            for (; pick < k - 1; pick++) {
                r -= weights[pick];
                if (r <= 0) break;
            }
            blob.add(keys[pick]);
            chosen.add(new int[]{unpackX(keys[pick]), unpackY(keys[pick])});
        }

        int painted = 0;
        for (int[] c : chosen) {
            if (inside(c[0], c[1])) {
                paint(c[0], c[1], colour, now, lifetime, rng, pattern);
                painted++;
            } else if (spill != null) {
                spill.add(c);
            }
        }
        if (painted > 0) {
            dirty = true;
            lastTouched = now;
        }
        return painted;
    }

    private void addCandidates(int x, int y, Set<Long> blob, Map<Long, Integer> cand) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
            int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
            long k = pack(nx, ny);
            if (blob.contains(k)) continue;
            if (inside(nx, ny) && !paintable(nx, ny)) continue; // hidden: a wall, not a way through
            int neighbours = 0;
            if (blob.contains(pack(nx + 1, ny))) neighbours++;
            if (blob.contains(pack(nx - 1, ny))) neighbours++;
            if (blob.contains(pack(nx, ny + 1))) neighbours++;
            if (blob.contains(pack(nx, ny - 1))) neighbours++;
            cand.put(k, neighbours);
        }
    }

    /**
     * Adds {@code units} of depth at and around (px, py): the impact pixel takes the first,
     * the rest go to painted pixels within one step (or the impact again when it is alone).
     */
    void addVolume(int px, int py, int units, int colour, int now, int lifetime, RandomSource rng, boolean pattern) {
        if (!inside(px, py)) return;
        for (int u = 0; u < units; u++) {
            int tx = px, ty = py;
            if (u > 0) {
                int dir = rng.nextInt(5);
                int nx = px + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = py + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (filled(nx, ny)) { tx = nx; ty = ny; }
            }
            paintThin(tx, ty, colour, now, lifetime, rng, pattern, 0);
        }
    }

    /** Paints a single pixel (no growth). Used for patterns such as footprints and drips. */
    void paint(int px, int py, int colour, int now, int lifetime, RandomSource rng, boolean pattern) {
        paintThin(px, py, colour, now, lifetime, rng, pattern, 0);
    }

    /**
     * Paints one pixel: an empty pixel becomes a depth-1 film, a painted one gains a unit of
     * depth. {@code thinLevel} 1..3 renders increasingly transparent, so a trickle looks
     * thinner than a pooled puddle.
     */
    void paintThin(int px, int py, int colour, int now, int lifetime, RandomSource rng, boolean pattern, int thinLevel) {
        if (!inside(px, py)) return;
        int i = py * pw + px;
        if (twin != null && !propagating) {
            // Both faces of a cross plane show blood at the same spot, each through its own mask.
            propagating = true;
            try {
                Vec3 p = pixelCenter(px, py);
                twin.paintThin(twin.pixelX(p), twin.pixelY(p), colour, now, lifetime, rng, pattern, thinLevel);
            } finally {
                propagating = false;
            }
        }
        if (mask != null && !mask[i]) return; // off the stone: nothing to cling to
        if (tone[i] == EMPTY) {
            count++;
            depth[i] = 1;
            born[i] = now;
            life[i] = (int) (lifetime * (0.8f + rng.nextFloat() * 0.4f));
        } else {
            depth[i] = (byte) Math.min(MAX_DEPTH, depth[i] + 1);
            // Fresh blood on old blood: age it back to at most a quarter of its life.
            born[i] = Math.max(born[i], now - life[i] / 4);
        }
        thin[i] = (byte) Math.max(0, Math.min(3, thinLevel));
        powder[i] = false;
        byte t = baseTone[i];
        if (pattern) {
            // Flat look: a few hashed darker pixels, nothing else.
            int hsh = (px * 73856093) ^ (py * 19349663) ^ (pos.hashCode() * 83492791);
            t = ((hsh >>> 4) % 8) == 0 ? DARK : BASE;
        }
        tone[i] = t;
        rgb[i] = colour;
        dirty = true;
        active = true;
        lastTouched = now;
    }

    /** Paints one pixel of powder (snow): a flat pile pixel that never flows. */
    void paintPowder(int px, int py, int colour, int now, int lifetime, RandomSource rng) {
        if (!inside(px, py)) return;
        paintThin(px, py, colour, now, lifetime, rng, true, 0);
        int i = py * pw + px;
        powder[i] = true;
        depth[i] = 1;
        tone[i] = BASE;
        active = false;
    }

    /** Powder splat: Eden growth like {@link #stamp}, but every pixel is a flat pile pixel. */
    int stampPowder(int px, int py, int n, int colour, int now, int lifetime, RandomSource rng, List<int[]> spill) {
        int before = count;
        List<int[]> cells = new ArrayList<>();
        Set<Long> blob = new HashSet<>();
        for (int i = 0; i < tone.length; i++) if (tone[i] != EMPTY) blob.add(pack(i % pw, i / pw));
        if (!blob.contains(pack(px, py))) { blob.add(pack(px, py)); cells.add(new int[]{px, py}); }
        Map<Long, Integer> cand = new HashMap<>();
        int guard = 0;
        double R = Math.max(1.2, Math.sqrt(n) * 0.9);
        while (cells.size() < n && guard++ < n * 8) {
            cand.clear();
            for (int[] c : cells) addCandidates(c[0], c[1], blob, cand);
            if (cand.isEmpty()) addCandidates(px, py, blob, cand);
            if (cand.isEmpty()) break;
            long bestKey = 0; double best = -1;
            for (Map.Entry<Long, Integer> e : cand.entrySet()) {
                int cx = unpackX(e.getKey()), cy = unpackY(e.getKey());
                double dx = cx - px, dy = cy - py;
                double wgt = (e.getValue() * e.getValue() + 0.2) * Math.exp(-(dx * dx + dy * dy) / (R * R) * 0.8) * (0.5 + rng.nextDouble());
                if (!inside(cx, cy)) wgt *= 0.6;
                if (wgt > best) { best = wgt; bestKey = e.getKey(); }
            }
            blob.add(bestKey);
            cells.add(new int[]{unpackX(bestKey), unpackY(bestKey)});
        }
        for (int[] c : cells) {
            if (inside(c[0], c[1])) paintPowder(c[0], c[1], colour, now, lifetime, rng);
            else if (spill != null) spill.add(c);
        }
        return count - before;
    }

    /**
     * Lets one random painted pixel give up a unit of blood, returning {px, py, colour} or
     * null when the tile is empty. Used for drips off ceilings.
     */
    int[] releaseUnit(RandomSource rng) {
        if (count == 0) return null;
        int start = rng.nextInt(tone.length);
        for (int k = 0; k < tone.length; k++) {
            int i = (start + k) % tone.length;
            if (tone[i] == EMPTY || powder[i]) continue;
            int colour = rgb[i];
            int px = i % pw, py = i / pw;
            takeUnit(i);
            return new int[]{px, py, colour};
        }
        return null;
    }

    // ------------------------------------------------------------------ pools

    /**
     * The pool - the 4-connected patch of liquid pixels - that contains (px, py), or touches
     * it when that pixel itself is empty (the pixel just inside an edge a run pours over).
     * Powder piles are not liquid and never join. Returns pixel indices, or an empty array.
     */
    private int[] pool(int px, int py) {
        int seed = -1;
        if (liquid(px, py)) {
            seed = py * pw + px;
        } else {
            for (int dy = -1; dy <= 1 && seed < 0; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx != 0 || dy != 0) && liquid(px + dx, py + dy)) { seed = (py + dy) * pw + px + dx; break; }
                }
            }
        }
        if (seed < 0) return new int[0];
        boolean[] seen = new boolean[tone.length];
        int[] queue = new int[tone.length];
        int head = 0, tail = 0;
        queue[tail++] = seed;
        seen[seed] = true;
        while (head < tail) {
            int i = queue[head++];
            int x = i % pw, y = i / pw;
            int[][] n = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
            for (int[] c : n) {
                if (!liquid(c[0], c[1])) continue;
                int j = c[1] * pw + c[0];
                if (!seen[j]) { seen[j] = true; queue[tail++] = j; }
            }
        }
        return java.util.Arrays.copyOf(queue, tail);
    }

    private boolean liquid(int px, int py) {
        if (!inside(px, py)) return false;
        int i = py * pw + px;
        return tone[i] != EMPTY && !powder[i];
    }

    /**
     * One body of liquid: the pool at a pixel together with every pool it joins across block
     * seams. A run over an edge drains the whole body - a basin with several sections empties
     * as one - so neighbouring puddles sink too, if they are actually connected.
     */
    static final class Body {
        final List<CanvasTile> tiles = new ArrayList<>();
        final List<int[]> pools = new ArrayList<>();

        /** Volume (sum of depth) of the whole body. */
        int mass() {
            int m = 0;
            for (int k = 0; k < tiles.size(); k++) {
                CanvasTile t = tiles.get(k);
                for (int i : pools.get(k)) m += t.depth[i];
            }
            return m;
        }

        boolean contains(CanvasTile t) {
            return tiles.contains(t);
        }

        /**
         * Takes one unit off the body's deepest level, at the pixel nearest the lip it pours
         * over - with a couple of pixels of jitter, so the falling level frays outward from the
         * outlet instead of sweeping across in a hard line. Returns false when nothing deeper
         * than a film is left anywhere in the body.
         */
        boolean draw(Vec3 lip, RandomSource rng) {
            int deepest = 1, best = -1;
            CanvasTile bestTile = null;
            double bestScore = Double.MAX_VALUE;
            for (int k = 0; k < tiles.size(); k++) {
                CanvasTile t = tiles.get(k);
                double jitter = 2.5 / t.res;
                for (int i : pools.get(k)) {
                    int d = t.depth[i];
                    if (d < 2 || d < deepest) continue;
                    double score = t.pixelCenter(i % t.pw, i / t.pw).distanceToSqr(lip) + rng.nextDouble() * jitter * jitter;
                    if (d > deepest || score < bestScore) {
                        deepest = d;
                        bestTile = t;
                        best = i;
                        bestScore = score;
                    }
                }
            }
            if (bestTile == null) return false;
            bestTile.takeUnit(best);
            bestTile.active = true; // let the relaxation even the surface out again
            return true;
        }
    }

    /**
     * The body of liquid at (px, py) on {@code start}: its pool plus, through {@code ctx}, the
     * pools on neighbouring tiles that touch it across the seams, up to {@code maxTiles}.
     */
    static Body body(CanvasTile start, int px, int py, FlowContext ctx, int maxTiles) {
        Body body = new Body();
        List<CanvasTile> seen = new ArrayList<>();
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        queue.add(new Object[]{start, px, py});
        seen.add(start);
        int[] xy = new int[2];
        while (!queue.isEmpty() && body.tiles.size() < maxTiles) {
            Object[] item = queue.poll();
            CanvasTile t = (CanvasTile) item[0];
            int[] pool = t.pool((Integer) item[1], (Integer) item[2]);
            if (pool.length == 0) continue;
            body.tiles.add(t);
            body.pools.add(pool);
            if (ctx == null) continue;
            for (int i : pool) {
                int x = i % t.pw, y = i / t.pw;
                if (x == 0) join(t, -1, y, ctx, xy, seen, queue);
                if (x == t.pw - 1) join(t, t.pw, y, ctx, xy, seen, queue);
                if (y == 0) join(t, x, -1, ctx, xy, seen, queue);
                if (y == t.ph - 1) join(t, x, t.ph, ctx, xy, seen, queue);
            }
        }
        return body;
    }

    private static void join(CanvasTile t, int ox, int oy, FlowContext ctx, int[] xy, List<CanvasTile> seen, java.util.ArrayDeque<Object[]> queue) {
        CanvasTile n = ctx.across(t, ox, oy, xy, false);
        if (n == null || seen.contains(n) || !n.liquid(xy[0], xy[1])) return;
        seen.add(n);
        queue.add(new Object[]{n, xy[0], xy[1]});
    }

    /** Whether water stands over the painted pixel (px, py): the spot just in front of it is below a fluid's surface. */
    boolean underWater(net.minecraft.client.multiplayer.ClientLevel level, int px, int py) {
        Vec3 c = pixelCenter(px, py);
        double fx = c.x + nx * 0.05, fy = c.y + ny * 0.05, fz = c.z + nz * 0.05;
        BlockPos bp = BlockPos.containing(fx, fy, fz);
        net.minecraft.world.level.material.FluidState fluid = level.getFluidState(bp);
        return !fluid.isEmpty() && fy < bp.getY() + fluid.getHeight(level, bp);
    }

    /**
     * Water has come over part of this surface (a flooded hole, a waterlogged block): every
     * painted pixel under its surface is washed off.
     *
     * @return the washed pixels as {px, py, colour}, for the caller to cloud up
     */
    List<int[]> washUnderWater(net.minecraft.client.multiplayer.ClientLevel level) {
        List<int[]> washed = null;
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] == EMPTY) continue;
            int px = i % pw, py = i / pw;
            if (!underWater(level, px, py)) continue;
            if (washed == null) washed = new ArrayList<>();
            washed.add(new int[]{px, py, rgb[i]});
            depth[i] = 0;
            tone[i] = EMPTY;
            count--;
        }
        if (washed == null) return List.of();
        dirty = true;
        active = true;
        return washed;
    }

    /** Takes one unit of depth from a pixel (removing it when the last unit goes). */
    private void takeUnit(int i) {
        if (depth[i] <= 1) {
            depth[i] = 0;
            tone[i] = EMPTY;
            count--;
        } else {
            depth[i]--;
        }
        dirty = true;
    }

    private static long pack(int x, int y) { return ((long) x << 32) | (y & 0xFFFFFFFFL); }
    private static int unpackX(long k) { return (int) (k >> 32); }
    private static int unpackY(long k) { return (int) k; }

    // ------------------------------------------------------------------ flow

    /**
     * One relaxation step of the puddle on a top face. Every pixel may move one unit to a
     * neighbour: to a painted neighbour at least two units shallower (levelling), to an empty
     * pixel when the source is deep enough (spreading), across a block seam to the tile that
     * continues this surface, or over an open block edge (draining into a run down the side).
     * Blood that is fenced in by walls simply gets deeper, and pours out once a wall goes.
     *
     * @return true when any blood moved (the tile stays active)
     */
    boolean relax(FlowContext ctx, RandomSource rng, int now, int lifetime, boolean pattern) {
        if (custom || face != Direction.UP || count == 0) return false;
        boolean anyMoved = false;
        java.util.Arrays.fill(moved, false);
        int n = tone.length;
        int start = rng.nextInt(n);
        int stride = strideFor(n, rng);
        int[] xy = new int[2];

        for (int k = 0; k < n; k++) {
            int i = (start + k * stride) % n;
            int d = depth[i];
            if (d < 2 || moved[i] || powder[i]) continue;
            int x = i % pw, y = i / pw;

            // Pick the lowest target among the four neighbours, in a random order for ties.
            int firstDir = rng.nextInt(4);
            int bestKind = -1, bestDepth = Integer.MAX_VALUE, bestIdx = -1, bestX = 0, bestY = 0;
            CanvasTile bestTile = null;
            for (int q = 0; q < 4; q++) {
                int dir = (firstDir + q) & 3;
                int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = y + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (inside(nx, ny)) {
                    if (!paintable(nx, ny)) continue; // covered by more of the block: blood stops there
                    int j = ny * pw + nx;
                    int nd = depth[j];
                    if (nd == 0 ? d >= SPREAD_DEPTH : d - nd >= 2) {
                        if (nd < bestDepth) { bestDepth = nd; bestKind = 0; bestIdx = j; }
                    }
                } else if (ctx != null) {
                    // Only deep enough blood may open a fresh tile on the neighbouring block.
                    CanvasTile other = ctx.across(this, nx, ny, xy, d >= SPREAD_DEPTH);
                    if (other != null && !other.paintable(xy[0], xy[1])) other = null;
                    if (other != null) {
                        int nd = other.depthAt(xy[0], xy[1]);
                        if ((nd == 0 ? d >= SPREAD_DEPTH : d - nd >= 2) && nd < bestDepth) {
                            bestDepth = nd; bestKind = 1; bestTile = other; bestX = xy[0]; bestY = xy[1];
                        }
                    } else if (ctx.canDrain(this, nx, ny)) {
                        // Over the edge counts as the lowest place there is.
                        if (-1 < bestDepth) { bestDepth = -1; bestKind = 2; bestX = nx; bestY = ny; }
                    }
                }
            }
            if (bestKind < 0) continue;

            int colour = rgb[i];
            if (bestKind == 2) {
                // The run down the side may refuse (cooldown); then the blood stays put.
                if (!ctx.drain(this, bestX, bestY, colour)) continue;
                takeUnit(i);
            } else if (bestKind == 1) {
                takeUnit(i);
                bestTile.paintThin(bestX, bestY, colour, now, lifetime, rng, pattern, 0);
                bestTile.active = true;
            } else {
                takeUnit(i);
                paintThin(bestIdx % pw, bestIdx / pw, colour, now, lifetime, rng, pattern, 0);
                moved[bestIdx] = true;
            }
            anyMoved = true;
        }
        return anyMoved;
    }

    /** A stride coprime with n, so a strided walk visits every pixel in a scrambled order. */
    private static int strideFor(int n, RandomSource rng) {
        int[] options = {1, 3, 5, 7, 11, 13, 17, 19};
        for (int tries = 0; tries < 8; tries++) {
            int s = options[rng.nextInt(options.length)];
            if (gcd(s, n) == 1) return s;
        }
        return 1;
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    // ------------------------------------------------------------------ aging

    /** Shifts every pixel's birth tick back so the tile ages faster (rain). */
    void accelerateAging(int ticks) {
        for (int i = 0; i < born.length; i++) {
            if (tone[i] != EMPTY) born[i] -= ticks;
        }
        dirty = true;
    }

    /**
     * Ages the pixels: deep blood dries shallower, expired pixels vanish, and thin film is
     * eroded from the edge inward during the last part of its life. Returns true when the
     * image needs re-uploading.
     */
    boolean age(int now, RandomSource rng) {
        boolean changed = dirty;
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] == EMPTY) continue;
            int agePct = (int) (100L * (now - born[i]) / Math.max(1, life[i]));
            if (agePct >= 100) {
                depth[i] = 0;
                tone[i] = EMPTY;
                count--;
                changed = true;
            } else if (depth[i] > 1 && agePct > (depth[i] > 2 ? 50 : 70)) {
                depth[i]--; // drying: the pool sinks into a film before it fades
                changed = true;
            } else if (agePct > 60 && depth[i] == 1 && isEdge(i) && rng.nextInt(100) < (agePct - 60) / 2) {
                // Erode edge pixels progressively so a puddle shrinks instead of blinking out.
                depth[i] = 0;
                tone[i] = EMPTY;
                count--;
                changed = true;
            } else if (agePct > 30 && ((now + slot) & 63) == 0) {
                changed = true; // tone/alpha keeps evolving with age; refresh every ~3 s, staggered per tile
            }
        }
        return changed;
    }

    private boolean isEdge(int i) {
        int x = i % pw, y = i / pw;
        return x == 0 || y == 0 || x == pw - 1 || y == ph - 1
                || tone[i - 1] == EMPTY || tone[i + 1] == EMPTY
                || tone[i - pw] == EMPTY || tone[i + pw] == EMPTY;
    }

    // ------------------------------------------------------------------ upload

    /**
     * Edge class per pixel for puddle lighting: 0 empty, 1 rim with one open side (shadowed),
     * 4 rim with two or more open sides (a corner or a thin tip: left plain so corners do not
     * go dark), 2 one pixel in from the rim (highlight), 3 interior. Pixels on the tile
     * border consult the neighbouring tile so a puddle crossing a block seam has no false rim.
     */
    private byte[] edgeClasses(NeighbourLookup lookup) {
        byte[] cls = new byte[tone.length];
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] == EMPTY) continue;
            int x = i % pw, y = i / pw;
            int open = 0;
            if (!filledOrAcross(x + 1, y, lookup)) open++;
            if (!filledOrAcross(x - 1, y, lookup)) open++;
            if (!filledOrAcross(x, y + 1, lookup)) open++;
            if (!filledOrAcross(x, y - 1, lookup)) open++;
            cls[i] = open == 0 ? (byte) 3 : (open == 1 ? (byte) 1 : (byte) 4);
        }
        for (int i = 0; i < tone.length; i++) {
            if (cls[i] != 3) continue;
            int x = i % pw, y = i / pw;
            if (isRim(cls, x + 1, y) || isRim(cls, x - 1, y) || isRim(cls, x, y + 1) || isRim(cls, x, y - 1)) {
                cls[i] = 2;
            }
        }
        return cls;
    }

    private boolean isRim(byte[] cls, int x, int y) {
        if (!inside(x, y)) return false; // beyond the seam: assume interior
        byte c = cls[y * pw + x];
        return c == 1 || c == 4;
    }

    private boolean filledOrAcross(int x, int y, NeighbourLookup lookup) {
        if (inside(x, y)) return tone[y * pw + x] != EMPTY;
        return lookup != null && lookup.filledAcross(this, x, y);
    }

    /** Rewrites the tile image from the pixel state and uploads it into the atlas. */
    /** Depth averaged over the liquid pixels within two of (x, y), nearer ones weighing more. */
    private float smoothDepth(int x, int y) {
        float sum = 0, weight = 0;
        for (int dy = -2; dy <= 2; dy++) {
            int ny = y + dy;
            if (ny < 0 || ny >= ph) continue;
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                if (nx < 0 || nx >= pw) continue;
                int j = ny * pw + nx;
                if (tone[j] == EMPTY || powder[j]) continue;
                float w = 1f / (1 + dx * dx + dy * dy);
                sum += depth[j] * w;
                weight += w;
            }
        }
        return weight == 0 ? depth[y * pw + x] : sum / weight;
    }

    /** Age fraction averaged over the liquid pixels within one of (x, y). */
    private float smoothAge(float[] ages, int x, int y) {
        float sum = 0, weight = 0;
        for (int dy = -1; dy <= 1; dy++) {
            int ny = y + dy;
            if (ny < 0 || ny >= ph) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                if (nx < 0 || nx >= pw) continue;
                float a = ages[ny * pw + nx];
                if (a < 0) continue;
                float w = dx == 0 && dy == 0 ? 2f : 1f;
                sum += a * w;
                weight += w;
            }
        }
        return weight == 0 ? Math.max(0f, ages[y * pw + x]) : sum / weight;
    }

    void refresh(BloodCanvasAtlas atlas, int now, float opacity, NeighbourLookup lookup) {
        if (slot < 0) {
            slot = atlas.acquireSlot();
            if (slot < 0) return; // atlas full; tile stays invisible until a slot frees up
        }
        if (image == null) {
            image = new NativeImage(pw, ph, true);
        }
        if (spec == null && atlas.wantsSpecular()) {
            spec = new NativeImage(pw, ph, true);
        }
        // Puddles on top faces get a darker rim and a lighter ring just inside it, which reads
        // as a thin film of liquid catching the light. Tiny specks stay plain.
        boolean puddle = !custom && face == Direction.UP && count > 2;
        byte[] cls = puddle ? edgeClasses(lookup) : null;
        ripples.removeIf(r -> now - r[2] >= RIPPLE_TICKS);

        // Every pixel was painted at its own moment and lives its own jittered life; taken raw
        // that shows as specks drying and fading at random all over a puddle. The age used
        // for shading is blended over the neighbourhood so the whole pool dries together.
        float[] ages = new float[tone.length];
        for (int i = 0; i < tone.length; i++) {
            ages[i] = tone[i] == EMPTY || powder[i] ? -1 : (now - born[i]) / (float) Math.max(1, life[i]);
        }

        float glowSum = 0f;
        for (int i = 0; i < tone.length; i++) {
            int x = i % pw, y = i / pw;
            if (spec != null) Pixels.setAbgr(spec, x, y, 0); // matte unless wet liquid below
            if (tone[i] == EMPTY) {
                Pixels.setAbgr(image, x, y, 0);
                continue;
            }
            int age = now - born[i];
            float agePct = age / (float) Math.max(1, life[i]);
            if (powder[i]) {
                // Snow: one flat tone, fully opaque, just melts away at the end of its life.
                int c = rgb[i];
                float alpha = opacity;
                if (agePct > 0.6f) alpha *= 1.0f - (agePct - 0.6f) / 0.4f;
                Pixels.setAbgr(image, x, y, Pixels.packAbgr((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, clamp((int) (alpha * 255))));
                continue;
            }
            int t = tone[i];
            float shade = smoothAge(ages, x, y);
            float dry = Math.max(0f, Math.min(1f, (shade - 0.35f) / 0.35f)); // 0 fresh .. 1 dried
            if ((rgb[i] & BloodColor.GLOW_BIT) != 0) glowSum += 1f - dry;
            // Depth drives everything: a film has no relief and shows the block through it,
            // a deep pool has a clear dark edge, a bright ring and is opaque. The depth is
            // blended over the neighbourhood so a level that drops in a front, as a draining
            // puddle's does, shades as a soft gradient rather than a step.
            float sd = smoothDepth(x, y);
            float deep = Math.min(1.0f, (sd - 1) / 3.0f);
            float f;
            if (puddle && cls[i] != 3) {
                float k = deep * (1.0f - 0.5f * dry); // drying flattens the relief
                if (cls[i] == 1) f = 1.0f - 0.22f * k;
                else if (cls[i] == 2) f = 1.0f + 0.30f * k;
                else f = 1.0f;
            } else {
                f = TONE_FACTOR[t];
            }
            f *= 1.0f - 0.16f * dry; // dried blood is darker, evenly across the pool
            // A landing ripple: a ring that expands, widens slightly and fades out, weaker as
            // it nears the rim of the pool. Faint - a shade lighter and barely more see-through -
            // so it reads as a wave on the surface rather than a band.
            float rippleAlpha = 1.0f;
            for (int[] r : ripples) {
                double radius = (now - r[2]) * 0.45 + 0.5;
                double reach = r[4] / 10.0;
                if (radius > reach) continue;
                float lifeFrac = (now - r[2]) / (float) RIPPLE_TICKS;
                double dd = Math.sqrt((x - r[0]) * (x - r[0]) + (y - r[1]) * (y - r[1]));
                if (Math.abs(dd - radius) < 0.6 + lifeFrac * 0.4) {
                    float k = (1.0f - lifeFrac) * (r[3] / 1000f) * (float) (1.0 - 0.6 * radius / reach);
                    f = Math.max(f, f + (TONE_FACTOR[LIGHT] - f) * 0.22f * k);
                    rippleAlpha = 1.0f - 0.1f * k;
                    break;
                }
            }
            int c = rgb[i];
            int r = clamp((int) (((c >> 16) & 0xFF) * f));
            int g = clamp((int) (((c >> 8) & 0xFF) * f));
            int b = clamp((int) ((c & 0xFF) * f));
            float alpha = opacity;
            alpha *= 0.62f + 0.38f * Math.min(1.0f, (sd - 1) / 2.0f);       // film shows the surface through
            alpha *= 1.0f - 0.18f * thin[i];                                 // a running film even more so
            alpha *= rippleAlpha;
            if (shade > 0.75f) alpha *= 1.0f - Math.min(1f, (shade - 0.75f) / 0.25f);
            Pixels.setAbgr(image, x, y, Pixels.packAbgr(r, g, b, clamp((int) (alpha * 255))));
            // Banner cloth soaks the blood up: it stays matte.
            if (spec != null && swayPivot == null) Pixels.setAbgr(spec, x, y, wetSpecular(deep, thin[i], dry));
        }
        // The tile lights itself by the share of fresh glowing blood on it: full for a fresh
        // glowing puddle, nothing once it has dried.
        glow = count == 0 ? 0 : Math.round(15f * Math.min(1f, glowSum / count));
        atlas.upload(slot, image, spec);
        dirty = false;
    }

    /** labPBR F0 of blood: a plain dielectric liquid, about water's 0.02. */
    private static final int F0_BLOOD = 6;

    /**
     * labPBR specular texel of a liquid pixel (R smoothness, G F0, B porosity, A 255 = no
     * emission): glossy while wet, deep pools most and running films a little less, going
     * matte as the pool dries.
     */
    private static int wetSpecular(float deep, int thinLevel, float dry) {
        float smooth = (0.78f + 0.20f * deep - 0.06f * thinLevel) * (1.0f - dry);
        return Pixels.packAbgr(clamp((int) (smooth * 255)), F0_BLOOD, 0, 255);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    /** Releases GPU slot and native memory. */
    void dispose(BloodCanvasAtlas atlas) {
        if (atlas != null) atlas.releaseSlot(slot);
        slot = -1;
        if (image != null) {
            image.close();
            image = null;
        }
        if (spec != null) {
            spec.close();
            spec = null;
        }
    }
}
