package com.simpleblood.surface;

import com.simpleblood.BloodColor;
import com.simpleblood.surface.store.SavedTile;

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

public final class CanvasTile {

    static final byte EMPTY = 0, DARK = 1, BASE = 2, LIGHT = 3;
    private static final float[] TONE_FACTOR = {0f, 0.87f, 1.0f, 1.28f};

    static final int MAX_DEPTH = 6;
    private static final int SPREAD_DEPTH = 3;
    private static final int OVERFLOW_REACH = 64;

    public record Key(long pos, Direction face, int box) {}

    interface NeighbourLookup {
        boolean filledAcross(CanvasTile tile, int px, int py);

        default CanvasTile beside(CanvasTile tile, int px, int py, int[] xy) {
            return null;
        }
    }

    interface FlowContext extends NeighbourLookup {
        CanvasTile across(CanvasTile tile, int px, int py, int[] xy, boolean create);

        default boolean continues(CanvasTile tile, int px, int py) {
            return false;
        }

        boolean canDrain(CanvasTile tile, int px, int py);

        boolean drain(CanvasTile tile, int px, int py, int colour);
    }

    static final int RIPPLE_TICKS = 22;

    static final int FOREVER = Integer.MAX_VALUE;
    private static final int FOREVER_BIT = SavedTile.Pixels.FOREVER_BIT;
    private static final int FOREVER_CURVE_TICKS = com.simpleblood.SimpleBloodConfig.SurfaceSettings.LIFETIME_MAX_SECONDS * 20;
    private static final float FOREVER_HOLD = 0.72f;
    private static final int HOLD_REDRAW_TICKS = 40;
    private static final int WASHED_FOREVER_LIFE_TICKS = 60 * 20;

    final Key key;
    final BlockPos pos;
    final Direction face;
    BlockState state;
    final boolean custom;
    double nx, ny, nz;
    private boolean[] mask;
    CanvasTile twin;
    Vec3 swayPivot, swayAxis;
    Slope slope;
    boolean turning;
    int turnDrips, turnPours;
    com.simpleblood.compat.CreateKinetics.Part spin;
    boolean spinAsked;
    Boolean onSubLevel;
    boolean sizzled;
    private boolean propagating;

    final double ox, oy, oz;
    final double ux, uy, uz;
    final double vx, vy, vz;
    final double w, h;
    final int pw, ph;
    final int res;

    private final byte[] tone;
    private final byte[] baseTone;
    private final byte[] depth;
    private final int[] rgb;
    private final int[] born;
    private final int[] life;
    private final byte[] thin;
    private final boolean[] powder;
    private final boolean[] moved;
    private int count;
    int exposedMask;
    boolean active;

    private NativeImage image;
    private NativeImage spec;
    FlowContext flow;
    boolean reshade;
    private int seamSignature;
    int slot = -1;
    boolean dirty;
    int lastTouched;
    int light;
    int lightTick = -100;
    boolean onSeeThrough;
    int glow;
    net.minecraft.world.level.block.piston.PistonMovingBlockEntity mover;
    boolean followsMover;
    private final List<int[]> ripples = new ArrayList<>(2);

    CanvasTile(Key key, BlockPos pos, BlockState state, Vec3 origin, Vec3 u, Vec3 v, double w, double h,
               int res, int now, boolean[] mask) {
        this(key, pos, state, origin, u, v, w, h, res, now, mask,
                Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(w * res))),
                Math.max(1, Math.min(BloodCanvasAtlas.TILE, (int) Math.round(h * res))));
    }

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

        switch (face) {
            case UP    -> { ox = minX; oy = maxY; oz = minZ; ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1;  w = sx; h = sz; }
            case DOWN  -> { ox = minX; oy = minY; oz = maxZ; ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = -1; w = sx; h = sz; }
            case NORTH -> { ox = maxX; oy = maxY; oz = minZ; ux = -1; uy = 0; uz = 0; vx = 0; vy = -1; vz = 0; w = sx; h = sy; }
            case SOUTH -> { ox = minX; oy = maxY; oz = maxZ; ux = 1; uy = 0; uz = 0; vx = 0; vy = -1; vz = 0;  w = sx; h = sy; }
            case WEST  -> { ox = minX; oy = maxY; oz = minZ; ux = 0; uy = 0; uz = 1; vx = 0; vy = -1; vz = 0;  w = sz; h = sy; }
            default    -> { ox = maxX; oy = maxY; oz = maxZ; ux = 0; uy = 0; uz = -1; vx = 0; vy = -1; vz = 0; w = sz; h = sy; }
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
        this.flow = src.flow;
        this.dirty = true;
    }

    Vec3 pixelCenter(int px, int py) {
        double fu = (px + 0.5) / pw * w;
        double fv = (py + 0.5) / ph * h;
        return new Vec3(ox + ux * fu + vx * fv, oy + uy * fu + vy * fv, oz + uz * fu + vz * fv);
    }

    int pixelX(Vec3 p) {
        double d = (p.x - ox) * ux + (p.y - oy) * uy + (p.z - oz) * uz;
        return (int) Math.floor(d / w * pw);
    }

    int pixelY(Vec3 p) {
        double d = (p.x - ox) * vx + (p.y - oy) * vy + (p.z - oz) * vz;
        return (int) Math.floor(d / h * ph);
    }

    public Vec3 center() {
        return new Vec3(ox + (ux * w + vx * h) * 0.5, oy + (uy * w + vy * h) * 0.5, oz + (uz * w + vz * h) * 0.5);
    }

    boolean inside(int px, int py) {
        return px >= 0 && py >= 0 && px < pw && py < ph;
    }

    public int count() { return count; }

    public int countWhere(java.util.function.Predicate<Vec3> where) {
        int n = 0;
        for (int py = 0; py < ph; py++) {
            for (int px = 0; px < pw; px++) {
                if (filled(px, py) && where.test(pixelCenter(px, py))) n++;
            }
        }
        return n;
    }

    public long[] ageSums(int now) {
        long ticks = 0, share = 0;
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] == EMPTY) continue;
            ticks += now - born[i];
            share += 1000L * (now - born[i]) / Math.max(1, span(i));
        }
        return new long[] {ticks, share};
    }

    public int pixelsWide() { return pw; }

    public int pixelsHigh() { return ph; }

    public int bloodAt(int px, int py) {
        return filled(px, py) ? rgb[py * pw + px] & 0xFFFFFF : 0;
    }

    public int drawnAt(int px, int py) {
        if (image == null || !inside(px, py)) return 0;
        return Pixels.alpha(image, px, py) << 24 | (Pixels.rgb(image, px, py) & 0xFFFFFF);
    }

    public int wetCount(int now) {
        int n = 0;
        for (int py = 0; py < ph; py++) for (int px = 0; px < pw; px++) if (wetDepthAt(px, py, now) > 0) n++;
        return n;
    }

    public int units() {
        int n = 0;
        for (byte d : depth) n += d;
        return n;
    }

    public BlockPos blockPos() { return pos; }

    public Direction face() { return face; }

    public String describe() {
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT,
                "box %d %s n(%.2f %.2f %.2f) from (%.3f %.3f %.3f) u(%.2f %.2f %.2f) v(%.2f %.2f %.2f) %.3f x %.3f, %dx%d px, %d blood%s%n",
                key.box(), face, nx, ny, nz, ox - pos.getX(), oy - pos.getY(), oz - pos.getZ(), ux, uy, uz, vx, vy, vz,
                w, h, pw, ph, count, swayPivot != null ? ", moves" : ""));
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) sb.append(filled(x, y) ? '#' : paintable(x, y) ? '.' : ' ');
            sb.append('\n');
        }
        return sb.toString();
    }
    public boolean isEmpty() { return count == 0; }

    int depthAt(int px, int py) {
        return inside(px, py) ? depth[py * pw + px] : 0;
    }

    int colourAt(int px, int py) {
        int i = py * pw + px;
        return tone[i] == EMPTY ? 0 : rgb[i];
    }

    boolean filled(int px, int py) {
        return inside(px, py) && tone[py * pw + px] != EMPTY;
    }

    float ageAt(int px, int py, int now) {
        if (!inside(px, py)) return 0f;
        int i = py * pw + px;
        return (now - born[i]) / (float) Math.max(1, span(i));
    }

    private int span(int i) {
        return life[i] & ~FOREVER_BIT;
    }

    private boolean forever(int i) {
        return (life[i] & FOREVER_BIT) != 0;
    }

    private static int lifeFor(int lifetime, RandomSource rng) {
        float jitter = 0.8f + rng.nextFloat() * 0.4f;
        if (!lastsForever(lifetime)) return (int) (lifetime * jitter);
        int curve = lifetime == FOREVER ? FOREVER_CURVE_TICKS : lifetime & ~FOREVER_BIT;
        return FOREVER_BIT | (int) (curve * jitter);
    }

    private static boolean lastsForever(int lifetime) {
        return (lifetime & FOREVER_BIT) != 0;
    }

    static int foreverOver(int curveTicks) {
        return FOREVER_BIT | Math.max(1, Math.min(curveTicks, FOREVER_BIT - 1));
    }

    int wetDepthAt(int px, int py, int now) {
        if (!inside(px, py)) return 0;
        int i = py * pw + px;
        if (tone[i] == EMPTY || thin[i] != 0) return 0;
        if (now - born[i] > span(i) * 0.55f) return 0;
        return depth[i];
    }

    boolean powderAt(int px, int py) {
        return inside(px, py) && powder[py * pw + px];
    }

    boolean scoop(int px, int py) {
        if (!inside(px, py)) return false;
        int i = py * pw + px;
        if (tone[i] == EMPTY || depth[i] < 2) return false;
        takeUnit(i);
        return true;
    }

    void paintPrint(int px, int py, int colour, int now, int lifetime, RandomSource rng, boolean pattern, boolean powder) {
        if (!inside(px, py) || tone[py * pw + px] != EMPTY) return;
        if (powder) {
            paintPowder(px, py, colour, now, lifetime, rng);
        } else {
            paintThin(px, py, colour, now, lifetime, rng, pattern, 1);
            active = false;
        }
    }

    int tread(int px, int py, int now, float trample, int[] src) {
        if (!inside(px, py)) return -1;
        int i = py * pw + px;
        if (tone[i] == EMPTY || powder[i] || now - born[i] > span(i) * 0.55f) return -1;
        src[0] = rgb[i];
        src[1] = born[i];
        src[2] = life[i];
        int out = Math.max(0, depth[i] - 1);
        depth[i] = 1;
        born[i] -= Math.round(span(i) * trample);
        dirty = true;
        active = true;
        lastTouched = now;
        return out;
    }

    boolean carry(int px, int py, int colour, int srcBorn, int srcLife, int thinLevel, int now, int lifetime,
                  RandomSource rng, boolean pattern) {
        if (!paintable(px, py)) return false;
        int i = roomFor(py * pw + px);
        if (i < 0) i = py * pw + px;
        px = i % pw;
        py = i / pw;
        boolean was = tone[i] != EMPTY;
        int oldBorn = born[i];
        byte oldThin = thin[i];
        paintThin(px, py, colour, now, lifetime, rng, pattern, thinLevel);
        if (tone[i] == EMPTY) return false;
        if (was) {
            born[i] = Math.max(oldBorn, srcBorn);
            thin[i] = (byte) Math.min(oldThin, thinLevel);
        } else {
            born[i] = srcBorn;
            life[i] = srcLife;
        }
        return true;
    }

    void rebind(BlockState newState) {
        this.state = newState;
    }

    boolean paintable(int px, int py) {
        return inside(px, py) && (mask == null || mask[py * pw + px]);
    }

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

    void flipNormal() {
        nx = -nx; ny = -ny; nz = -nz;
    }

    boolean hasRipples() { return !ripples.isEmpty(); }

    boolean facesUp() {
        return custom ? ny > 0.9 : face == Direction.UP;
    }

    void addRipple(int cx, int cy, int now, int units) {
        if (!facesUp()) return;
        int area = pw * ph;
        int minPool = Math.max(8, area / 10);
        if (count < minPool || units < Math.max(2, area / 24)) return;
        float pool = Math.min(1f, count / (area * 0.5f));
        float drop = Math.min(1f, units / (area / 12f));
        float strength = pool * (0.5f + 0.5f * drop);
        double reach = Math.sqrt(count / Math.PI) * 1.1 + 1.0;
        if (ripples.size() >= 3) ripples.remove(0);
        ripples.add(new int[]{cx, cy, now, Math.round(strength * 1000), (int) Math.round(reach * 10)});
        dirty = true;
    }

    void addWake(int cx, int cy, int now, float strength, float reach) {
        if (!facesUp() || count == 0) return;
        if (ripples.size() >= 3) ripples.remove(0);
        ripples.add(new int[]{cx, cy, now, Math.round(Math.min(1f, strength) * 1000), Math.round(reach * 10)});
        dirty = true;
    }

    List<int[]> snapshot() {
        List<int[]> out = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) out.add(new int[]{i % pw, i / pw, rgb[i], depth[i], powder[i] ? 1 : 0});
        }
        count = out.size();
        return out;
    }

    record Lifted(int px, int py, byte tone, byte depth, int rgb, int born, int life, byte thin, boolean powder) {}

    List<Lifted> lift() {
        List<Lifted> out = new ArrayList<>(count);
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) {
                out.add(new Lifted(i % pw, i / pw, tone[i], depth[i], rgb[i], born[i], life[i], thin[i], powder[i]));
            }
        }
        return out;
    }

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
            rgb[i] = com.simpleblood.BloodMix.join(rgb[i], depth[i], l.rgb(), l.depth(),
                    com.simpleblood.BloodMix.wetness(now, born[i], span(i)), spot(px, py, 0), spot(px, py, 1));
            int total = depth[i] + l.depth();
            depth[i] = (byte) Math.min(MAX_DEPTH, total);
            born[i] = Math.max(born[i], l.born());
            for (int extra = total - MAX_DEPTH; extra > 0 && !powder[i]; extra--) {
                int j = roomFor(i);
                if (j < 0) break;
                overflowInto(j, i, now);
            }
            dirty = true;
            active = !powder[i];
            lastTouched = now;
            return true;
        }
        rgb[i] = l.rgb();
        dirty = true;
        active = !powder[i];
        lastTouched = now;
        return true;
    }

    private int roomFor(int i) {
        if (tone[i] == EMPTY || powder[i] || depth[i] < MAX_DEPTH) return i;
        int[] queue = new int[OVERFLOW_REACH];
        int head = 0, tail = 0;
        queue[tail++] = i;
        while (head < tail) {
            int at = queue[head++];
            int x = at % pw, y = at / pw;
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (!paintable(nx, ny)) continue;
                int j = ny * pw + nx;
                if (queued(queue, tail, j)) continue;
                if (tone[j] == EMPTY) return j;
                if (powder[j]) continue;
                if (depth[j] < MAX_DEPTH) return j;
                if (tail < queue.length) queue[tail++] = j;
            }
        }
        return -1;
    }

    private CanvasTile roomAcross(int start, int[] out) {
        CanvasTile[] tiles = new CanvasTile[OVERFLOW_REACH];
        int[] cells = new int[OVERFLOW_REACH];
        int head = 0, tail = 0;
        tiles[tail] = this;
        cells[tail++] = start;
        int[] xy = new int[2];
        while (head < tail) {
            CanvasTile at = tiles[head];
            int ai = cells[head++];
            int x = ai % at.pw, y = ai / at.pw;
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                CanvasTile t = at;
                int tx = nx, ty = ny;
                if (!at.inside(nx, ny)) {
                    t = at.flow == null ? null : at.flow.across(at, nx, ny, xy, false);
                    if (t == null) {
                        t = at.flow == null ? null : at.flow.across(at, nx, ny, xy, true);
                        if (t == null || !t.paintable(xy[0], xy[1]) || t.tone[xy[1] * t.pw + xy[0]] != EMPTY) continue;
                        out[0] = xy[0];
                        out[1] = xy[1];
                        return t;
                    }
                    tx = xy[0];
                    ty = xy[1];
                }
                if (!t.paintable(tx, ty)) continue;
                int j = ty * t.pw + tx;
                if (queuedIn(tiles, cells, tail, t, j)) continue;
                if (t.tone[j] == EMPTY || (!t.powder[j] && t.depth[j] < MAX_DEPTH)) {
                    out[0] = tx;
                    out[1] = ty;
                    return t;
                }
                if (t.powder[j]) continue;
                if (tail < tiles.length) {
                    tiles[tail] = t;
                    cells[tail++] = j;
                }
            }
        }
        return null;
    }

    private static boolean queuedIn(CanvasTile[] tiles, int[] cells, int size, CanvasTile t, int j) {
        for (int k = 0; k < size; k++) if (tiles[k] == t && cells[k] == j) return true;
        return false;
    }

    private static boolean queued(int[] queue, int size, int j) {
        for (int k = 0; k < size; k++) if (queue[k] == j) return true;
        return false;
    }

    private void overflowInto(int to, int from, int now) {
        if (tone[to] == EMPTY) {
            count++;
            tone[to] = tone[from];
            depth[to] = 1;
            born[to] = born[from];
            life[to] = life[from];
            thin[to] = thin[from];
            powder[to] = false;
            rgb[to] = rgb[from];
            return;
        }
        int x = to % pw, y = to / pw;
        rgb[to] = com.simpleblood.BloodMix.join(rgb[to], depth[to], rgb[from], 1,
                com.simpleblood.BloodMix.wetness(now, born[to], span(to)), spot(x, y, 0), spot(x, y, 1));
        depth[to]++;
        born[to] = Math.max(born[to], born[from]);
    }

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

    int stamp(int px, int py, int n, int colour, int now, int lifetime, RandomSource rng,
              boolean pattern, List<int[]> spill) {
        return stamp(px, py, n, colour, now, lifetime, rng, pattern, spill, 0, 0);
    }

    int stamp(int px, int py, int n, int colour, int now, int lifetime, RandomSource rng,
              boolean pattern, List<int[]> spill, double bu, double bv) {
        if (face == Direction.UP && filled(px, py)) {
            addRipple(px, py, now, n);
            addVolume(px, py, n, colour, now, lifetime, rng, pattern);
            return n;
        }

        Set<Long> blob = new HashSet<>();
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) blob.add(pack(i % pw, i / pw));
        }
        if (flow != null && !custom && face == Direction.UP) addBlobBeside(blob);
        List<int[]> chosen = new ArrayList<>(n);
        boolean vertical = custom || face.getAxis().isHorizontal();

        if (filledNeighbours(px, py) > 0) {
            addRipple(px, py, now, n);
        }

        if (!blob.contains(pack(px, py))) {
            blob.add(pack(px, py));
            chosen.add(new int[]{px, py});
        }
        double R = Math.max(1.2, Math.sqrt(n) * 0.9);
        double bias = Math.sqrt(bu * bu + bv * bv);
        if (bias > 1.0e-6) { bu /= bias; bv /= bias; }
        bias = Math.min(1.0, bias);
        Map<Long, Integer> cand = new HashMap<>();
        int[] goesOn = new int[9];
        int guard = 0;
        while (chosen.size() < n && guard++ < n * 8) {
            cand.clear();
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
                    double par = dx * bu + dy * bv;
                    double perp2 = Math.max(0, dist2 - par * par);
                    double stretch = 1.0 + 2.0 * bias;
                    dist2 = (par / stretch) * (par / stretch) + perp2;
                    if (par < 0) dist2 *= 1.0 + bias;
                }
                double wgt = e.getValue() * e.getValue() * Math.exp(-dist2 / (R * R) * 0.8);
                if (vertical) wgt *= dy > 0 ? 1.8 : (dy < 0 ? 0.45 : 1.0);
                if (!inside(cx, cy) && !surfaceGoesOn(cx, cy, goesOn)) wgt *= 0.6;
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

    private void addBlobBeside(Set<Long> blob) {
        int[] xy = new int[2];
        for (int sy = -1; sy <= 1; sy++) {
            for (int sx = -1; sx <= 1; sx++) {
                if (sx == 0 && sy == 0) continue;
                int rx = sx < 0 ? -1 : sx == 0 ? pw / 2 : pw, ry = sy < 0 ? -1 : sy == 0 ? ph / 2 : ph;
                CanvasTile n = flow.beside(this, rx, ry, xy);
                if (n == null || n.res != res || n.count == 0) continue;
                int dx = xy[0] - rx, dy = xy[1] - ry;
                for (int j = 0; j < n.tone.length; j++) {
                    if (n.tone[j] != EMPTY) blob.add(pack(j % n.pw - dx, j / n.pw - dy));
                }
            }
        }
    }

    private boolean surfaceGoesOn(int x, int y, int[] asked) {
        if (flow == null || custom || face != Direction.UP) return false;
        int side = (y < 0 ? 0 : y >= ph ? 2 : 1) * 3 + (x < 0 ? 0 : x >= pw ? 2 : 1);
        if (asked[side] == 0) asked[side] = flow.continues(this, x, y) ? 1 : 2;
        return asked[side] == 1;
    }

    private void addCandidates(int x, int y, Set<Long> blob, Map<Long, Integer> cand) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
            int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
            long k = pack(nx, ny);
            if (blob.contains(k)) continue;
            if (inside(nx, ny) && !paintable(nx, ny)) continue;
            int neighbours = 0;
            if (blob.contains(pack(nx + 1, ny))) neighbours++;
            if (blob.contains(pack(nx - 1, ny))) neighbours++;
            if (blob.contains(pack(nx, ny + 1))) neighbours++;
            if (blob.contains(pack(nx, ny - 1))) neighbours++;
            cand.put(k, neighbours);
        }
    }

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

    void paint(int px, int py, int colour, int now, int lifetime, RandomSource rng, boolean pattern) {
        paintThin(px, py, colour, now, lifetime, rng, pattern, 0);
    }

    void paintThin(int px, int py, int colour, int now, int lifetime, RandomSource rng, boolean pattern, int thinLevel) {
        if (!inside(px, py)) return;
        int i = py * pw + px;
        if (twin != null && !propagating) {
            propagating = true;
            try {
                Vec3 p = pixelCenter(px, py);
                twin.paintThin(twin.pixelX(p), twin.pixelY(p), colour, now, lifetime, rng, pattern, thinLevel);
            } finally {
                propagating = false;
            }
        }
        if (mask != null && !mask[i]) return;
        if (flow != null && !custom && face == Direction.UP && tone[i] != EMPTY && !powder[i] && depth[i] >= MAX_DEPTH) {
            int[] xy = new int[2];
            CanvasTile there = roomAcross(i, xy);
            if (there != null && there != this) {
                there.paintThin(xy[0], xy[1], colour, now, lifetime, rng, pattern, thinLevel);
                return;
            }
        }
        int room = roomFor(i);
        if (room >= 0 && room != i) {
            i = room;
            px = i % pw;
            py = i / pw;
        }
        int mixed = colour;
        if (tone[i] == EMPTY) {
            count++;
            depth[i] = 1;
            born[i] = now;
            life[i] = lifeFor(lifetime, rng);
        } else {
            mixed = com.simpleblood.BloodMix.join(rgb[i], depth[i], colour, 1,
                    com.simpleblood.BloodMix.wetness(now, born[i], span(i)), spot(px, py, 0), rng.nextFloat());
            depth[i] = (byte) Math.min(MAX_DEPTH, depth[i] + 1);
            if (lastsForever(lifetime) && !forever(i)) life[i] = lifeFor(lifetime, rng);
            born[i] = Math.max(born[i], now - span(i) / 4);
        }
        thin[i] = (byte) Math.max(0, Math.min(3, thinLevel));
        powder[i] = false;
        byte t = baseTone[i];
        if (pattern) {
            int hsh = (px * 73856093) ^ (py * 19349663) ^ (pos.hashCode() * 83492791);
            t = ((hsh >>> 4) % 8) == 0 ? DARK : BASE;
        }
        tone[i] = t;
        rgb[i] = mixed;
        dirty = true;
        active = true;
        lastTouched = now;
    }

    private float spot(int px, int py, int which) {
        double fu = (px + 0.5) * w / pw, fv = (py + 0.5) * h / ph;
        int u = (int) Math.floor(((ox * ux + oy * uy + oz * uz) + fu) * res);
        int v = (int) Math.floor(((ox * vx + oy * vy + oz * vz) + fv) * res);
        int plane = (int) Math.round((ox * nx + oy * ny + oz * nz) * res);
        int salt = (plane * 31 + face.ordinal()) * 2 + which;
        return com.simpleblood.BloodMix.spot(u, v, salt);
    }

    void paintPowder(int px, int py, int colour, int now, int lifetime, RandomSource rng) {
        if (!inside(px, py)) return;
        paintThin(px, py, colour, now, lifetime, rng, true, 0);
        int i = py * pw + px;
        if (tone[i] == EMPTY) return;
        powder[i] = true;
        depth[i] = 1;
        tone[i] = BASE;
        active = false;
    }

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

    private static final float WET_WHILE = 0.5f;

    private boolean wetPool(int i, int now) {
        return tone[i] != EMPTY && !powder[i] && thin[i] == 0 && now - born[i] <= span(i) * WET_WHILE;
    }

    private static double downhill(Slope slope, int x, int y, RandomSource rng) {
        return x * slope.stepU() + y * slope.stepV() + rng.nextDouble() * 0.25;
    }

    boolean downhillRim(Slope slope, int now, RandomSource rng, int[] out) {
        if (count == 0) return false;
        int sx = (int) Math.signum(Math.round(slope.stepU())), sy = (int) Math.signum(Math.round(slope.stepV()));
        double best = -Double.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < tone.length; i++) {
            if (!wetPool(i, now)) continue;
            int x = i % pw, y = i / pw;
            int n = (y + sy) * pw + x + sx;
            if (inside(x + sx, y + sy) && tone[n] != EMPTY && thin[n] == 0) continue;
            double score = downhill(slope, x, y, rng);
            if (score > best) {
                best = score;
                out[0] = x;
                out[1] = y;
                found = true;
            }
        }
        return found;
    }

    boolean releaseLowest(Slope slope, int now, RandomSource rng, int[] out) {
        if (count == 0) return false;
        double best = -Double.MAX_VALUE;
        int pick = -1;
        for (int i = 0; i < tone.length; i++) {
            if (depth[i] < 2 || !wetPool(i, now)) continue;
            double score = downhill(slope, i % pw, i / pw, rng);
            if (score > best) {
                best = score;
                pick = i;
            }
        }
        if (pick < 0) return false;
        out[0] = pick % pw;
        out[1] = pick / pw;
        out[2] = rgb[pick];
        takeUnit(pick);
        return true;
    }

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

    public int liquidCount() {
        int n = 0;
        for (int i = 0; i < tone.length; i++) if (tone[i] != EMPTY && !powder[i]) n++;
        return n;
    }

    public int powderCount() {
        int n = 0;
        for (int i = 0; i < tone.length; i++) if (tone[i] != EMPTY && powder[i]) n++;
        return n;
    }

    private boolean liquid(int px, int py) {
        if (!inside(px, py)) return false;
        int i = py * pw + px;
        return tone[i] != EMPTY && !powder[i];
    }

    static final class Body {
        final List<CanvasTile> tiles = new ArrayList<>();
        final List<int[]> pools = new ArrayList<>();

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

        int excess() {
            int m = 0;
            for (int k = 0; k < tiles.size(); k++) {
                CanvasTile t = tiles.get(k);
                for (int i : pools.get(k)) m += Math.max(0, t.depth[i] - 1);
            }
            return m;
        }

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
            bestTile.active = true;
            return true;
        }
    }

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

    boolean underWater(net.minecraft.client.multiplayer.ClientLevel level, int px, int py) {
        Vec3 c = pixelCenter(px, py);
        double fx = c.x + nx * 0.05, fy = c.y + ny * 0.05, fz = c.z + nz * 0.05;
        BlockPos bp = BlockPos.containing(fx, fy, fz);
        net.minecraft.world.level.material.FluidState fluid = level.getFluidState(bp);
        if (fluid.isEmpty()) return BloodSurfaces.inCauldronWater(level, fx, fy, fz);
        return fy < bp.getY() + fluid.getHeight(level, bp);
    }

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

    private void takeUnit(int i) {
        if (tone[i] == EMPTY) return;
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

            int firstDir = rng.nextInt(4);
            int bestKind = -1, bestDepth = Integer.MAX_VALUE, bestIdx = -1, bestX = 0, bestY = 0;
            CanvasTile bestTile = null;
            for (int q = 0; q < 4; q++) {
                int dir = (firstDir + q) & 3;
                int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = y + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (inside(nx, ny)) {
                    if (!paintable(nx, ny)) continue;
                    int j = ny * pw + nx;
                    int nd = depth[j];
                    if (nd == 0 ? d >= SPREAD_DEPTH : d - nd >= 2) {
                        if (nd < bestDepth) { bestDepth = nd; bestKind = 0; bestIdx = j; }
                    }
                } else if (ctx != null) {
                    CanvasTile other = ctx.across(this, nx, ny, xy, d >= SPREAD_DEPTH);
                    if (other != null && !other.paintable(xy[0], xy[1])) other = null;
                    if (other != null) {
                        int nd = other.depthAt(xy[0], xy[1]);
                        if ((nd == 0 ? d >= SPREAD_DEPTH : d - nd >= 2) && nd < bestDepth) {
                            bestDepth = nd; bestKind = 1; bestTile = other; bestX = xy[0]; bestY = xy[1];
                        }
                    } else if (ctx.canDrain(this, nx, ny)) {
                        if (-1 < bestDepth) { bestDepth = -1; bestKind = 2; bestX = nx; bestY = ny; }
                    }
                }
            }
            if (bestKind < 0) continue;

            int colour = rgb[i];
            if (bestKind == 2) {
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

    private static int strideFor(int n, RandomSource rng) {
        int[] options = {1, 3, 5, 7, 11, 13, 17, 19};
        for (int tries = 0; tries < 8; tries++) {
            int s = options[rng.nextInt(options.length)];
            if (gcd(s, n) == 1) return s;
        }
        return 1;
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    void accelerateAging(int ticks, int now, boolean water) {
        for (int i = 0; i < born.length; i++) {
            if (tone[i] == EMPTY) continue;
            if (water && forever(i)) {
                int span = span(i);
                float share = (now - born[i]) / (float) Math.max(1, span);
                life[i] = Math.max(1, Math.round(WASHED_FOREVER_LIFE_TICKS * (span / (float) FOREVER_CURVE_TICKS)));
                born[i] = now - Math.round(share * life[i]);
            }
            born[i] -= ticks;
        }
        dirty = true;
    }

    boolean age(int now, RandomSource rng, boolean foreverFresh) {
        boolean changed = dirty;
        int alive = 0;
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] == EMPTY) continue;
            alive++;
            boolean held = false;
            if (forever(i)) {
                int hold = foreverFresh ? 0 : (int) (span(i) * FOREVER_HOLD);
                int over = now - born[i] - hold;
                if (over > 0) {
                    born[i] += over;
                    held = true;
                    if (over > HOLD_REDRAW_TICKS) changed = true;
                }
            }
            int agePct = (int) (100L * (now - born[i]) / Math.max(1, span(i)));
            if (held) {
                if (depth[i] > 1 && agePct > (depth[i] > 2 ? 50 : 70)) {
                    depth[i]--;
                    changed = true;
                }
                continue;
            }
            if (agePct >= 100) {
                depth[i] = 0;
                tone[i] = EMPTY;
                alive--;
                changed = true;
            } else if (depth[i] > 1 && agePct > (depth[i] > 2 ? 50 : 70)) {
                depth[i]--;
                changed = true;
            } else if (agePct > 60 && depth[i] == 1 && !forever(i) && isEdge(i) && rng.nextInt(100) < (agePct - 60) / 2) {
                depth[i] = 0;
                tone[i] = EMPTY;
                alive--;
                changed = true;
            } else if (agePct > 30 && ((now + slot) & 63) == 0) {
                changed = true;
            }
        }
        count = alive;
        return changed;
    }

    private boolean isEdge(int i) {
        int x = i % pw, y = i / pw;
        return x == 0 || y == 0 || x == pw - 1 || y == ph - 1
                || tone[i - 1] == EMPTY || tone[i + 1] == EMPTY
                || tone[i - pw] == EMPTY || tone[i + pw] == EMPTY;
    }

    private static final int HALO = 6;

    private final class View {
        final int w = pw + 2 * HALO, h = ph + 2 * HALO;
        final boolean[] filled = new boolean[w * h];
        final byte[] depth = new byte[w * h];
        final float[] age = new float[w * h];
        int filledCount;

        int at(int x, int y) {
            return (y + HALO) * w + x + HALO;
        }
    }

    private View view(NeighbourLookup lookup, int now) {
        View v = new View();
        java.util.Arrays.fill(v.age, -1f);
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) take(v, v.at(i % pw, i / pw), this, i, now);
        }
        if (lookup == null) return v;
        int[] xy = new int[2];
        for (int sy = -1; sy <= 1; sy++) {
            for (int sx = -1; sx <= 1; sx++) {
                if (sx == 0 && sy == 0) continue;
                int x0 = sx < 0 ? -HALO : sx == 0 ? 0 : pw, x1 = sx < 0 ? 0 : sx == 0 ? pw : pw + HALO;
                int y0 = sy < 0 ? -HALO : sy == 0 ? 0 : ph, y1 = sy < 0 ? 0 : sy == 0 ? ph : ph + HALO;
                int rx = sx < 0 ? -1 : sx == 0 ? pw / 2 : pw, ry = sy < 0 ? -1 : sy == 0 ? ph / 2 : ph;
                CanvasTile n = lookup.beside(this, rx, ry, xy);
                if (n != null && n.res == res && n.face == face) {
                    int dx = xy[0] - rx, dy = xy[1] - ry;
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            int nx = x + dx, ny = y + dy;
                            if (!n.inside(nx, ny)) continue;
                            int j = ny * n.pw + nx;
                            if (n.tone[j] != EMPTY) take(v, v.at(x, y), n, j, now);
                        }
                    }
                } else {
                    for (int y = Math.max(y0, -1); y < Math.min(y1, ph + 1); y++) {
                        for (int x = Math.max(x0, -1); x < Math.min(x1, pw + 1); x++) {
                            int c = v.at(x, y);
                            if (!v.filled[c] && lookup.filledAcross(this, x, y)) {
                                v.filled[c] = true;
                                v.filledCount++;
                            }
                        }
                    }
                }
            }
        }
        return v;
    }

    private static void take(View v, int c, CanvasTile t, int j, int now) {
        v.filled[c] = true;
        v.filledCount++;
        if (!t.powder[j]) {
            v.depth[c] = t.depth[j];
            v.age[c] = (now - t.born[j]) / (float) Math.max(1, t.span(j));
        }
    }

    private byte[] edgeClasses(View v) {
        byte[] cls = new byte[v.w * v.h];
        int m = HALO - 1;
        for (int y = -m; y < ph + m; y++) {
            for (int x = -m; x < pw + m; x++) {
                int c = v.at(x, y);
                if (!v.filled[c]) continue;
                int open = 0;
                if (!v.filled[c + 1]) open++;
                if (!v.filled[c - 1]) open++;
                if (!v.filled[c + v.w]) open++;
                if (!v.filled[c - v.w]) open++;
                cls[c] = open == 0 ? (byte) 3 : (open == 1 ? (byte) 1 : (byte) 4);
            }
        }
        for (int y = -m; y < ph + m; y++) {
            for (int x = -m; x < pw + m; x++) {
                int c = v.at(x, y);
                if (cls[c] == 3 && (isRim(cls[c + 1]) || isRim(cls[c - 1]) || isRim(cls[c + v.w]) || isRim(cls[c - v.w]))) {
                    cls[c] = 2;
                }
            }
        }
        return cls;
    }

    private static boolean isRim(byte c) {
        return c == 1 || c == 4;
    }

    private int[] edgeDistances(View v, byte[] cls) {
        int[] dist = new int[v.w * v.h];
        for (int c = 0; c < dist.length; c++) {
            dist[c] = !v.filled[c] ? 0 : isRim(cls[c]) ? PoolShading.STEP : PoolShading.UNSET;
        }
        PoolShading.edgeDistances(dist, v.w, v.h);
        return dist;
    }

    private float smoothDepth(View v, int x, int y) {
        float sum = 0, weight = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int d = v.depth[v.at(x + dx, y + dy)];
                if (d == 0) continue;
                float w = 1f / (1 + dx * dx + dy * dy);
                sum += d * w;
                weight += w;
            }
        }
        return weight == 0 ? depth[y * pw + x] : sum / weight;
    }

    private float smoothAge(View v, int x, int y) {
        float sum = 0, weight = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                float a = v.age[v.at(x + dx, y + dy)];
                if (a < 0) continue;
                float w = dx == 0 && dy == 0 ? 2f : 1f;
                sum += a * w;
                weight += w;
            }
        }
        return weight == 0 ? Math.max(0f, v.age[v.at(x, y)]) : sum / weight;
    }

    private int seamSignature() {
        int band = HALO + 1, hash = 1;
        for (int i = 0; i < tone.length; i++) {
            int x = i % pw, y = i / pw;
            if (x >= band && y >= band && x < pw - band && y < ph - band) continue;
            hash = hash * 31 + (tone[i] == EMPTY ? 0 : powder[i] ? 7 : depth[i]);
        }
        return hash;
    }

    boolean refresh(BloodCanvasAtlas atlas, int now, float opacity, NeighbourLookup lookup) {
        if (slot < 0) {
            slot = atlas.acquireSlot();
            if (slot < 0) return false;
        }
        if (image == null) {
            image = new NativeImage(pw, ph, true);
        }
        if (spec == null && atlas.wantsSpecular()) {
            spec = new NativeImage(pw, ph, true);
        }
        View view = view(custom ? null : lookup, now);
        boolean puddle = !custom && face == Direction.UP && view.filledCount > 2;
        byte[] cls = puddle ? edgeClasses(view) : null;
        int[] edgeDistance = puddle ? edgeDistances(view, cls) : null;
        ripples.removeIf(r -> now - r[2] >= RIPPLE_TICKS);

        float glowSum = 0f;
        boolean snow = state != null && BloodSurfaces.isSnow(state);
        for (int i = 0; i < tone.length; i++) {
            int x = i % pw, y = i / pw;
            if (spec != null) Pixels.setAbgr(spec, x, y, 0);
            if (tone[i] == EMPTY) {
                Pixels.setAbgr(image, x, y, 0);
                continue;
            }
            int age = now - born[i];
            float agePct = age / (float) Math.max(1, span(i));
            if (powder[i]) {
                int c = rgb[i];
                float alpha = opacity;
                if (agePct > 0.6f) alpha *= 1.0f - (agePct - 0.6f) / 0.4f;
                Pixels.setAbgr(image, x, y, Pixels.packAbgr((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, clamp((int) (alpha * 255))));
                continue;
            }
            int t = tone[i];
            float shade = smoothAge(view, x, y);
            float dry = Math.max(0f, Math.min(1f, (shade - 0.35f) / 0.35f));
            if ((rgb[i] & BloodColor.GLOW_BIT) != 0) glowSum += 1f - dry;
            float sd = smoothDepth(view, x, y);
            float deep = Math.min(1.0f, (sd - 1) / 3.0f);
            float f;
            int vc = view.at(x, y);
            float edge = puddle ? PoolShading.pixels(edgeDistance[vc]) : 0f;
            if (puddle && cls[vc] != 3) {
                float k = deep * (1.0f - 0.5f * dry);
                if (cls[vc] == 1) f = 1.0f - 0.22f * k;
                else if (cls[vc] == 2) f = 1.0f + 0.30f * k;
                else f = 1.0f;
            } else {
                f = TONE_FACTOR[t];
            }
            float rich = 1.0f;
            if (puddle) {
                f *= PoolShading.depthTone(edge, deep, dry);
                rich = PoolShading.depthRichness(edge, deep, dry);
            }
            if (snow) f *= 1.04f;
            else if (puddle) f *= PoolShading.ringTone(edge, dry);
            else f *= 1.0f - 0.16f * dry;
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
            int g = clamp((int) (((c >> 8) & 0xFF) * f * rich));
            int b = clamp((int) ((c & 0xFF) * f * rich));
            float alpha = opacity;
            alpha *= 0.62f + 0.38f * Math.min(1.0f, (sd - 1) / 2.0f);
            alpha *= 1.0f - 0.18f * thin[i];
            alpha *= rippleAlpha;
            if (puddle) {
                alpha *= PoolShading.depthAlpha(edge, deep, dry);
                if (!snow) alpha *= PoolShading.ringAlpha(edge, dry);
            }
            if (snow) alpha = Math.min(opacity, alpha * 1.35f);
            if (shade > 0.75f) alpha *= 1.0f - Math.min(1f, (shade - 0.75f) / 0.25f);
            Pixels.setAbgr(image, x, y, Pixels.packAbgr(r, g, b, clamp((int) (alpha * 255))));
            if (spec != null && !snow && !(swayPivot != null && state.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock)) {
                Pixels.setAbgr(spec, x, y, wetSpecular(deep, thin[i], dry));
            }
        }
        glow = count == 0 ? 0 : Math.round(15f * Math.min(1f, glowSum / count));
        atlas.upload(slot, image, spec);
        dirty = false;
        reshade = false;
        int seams = seamSignature();
        boolean seamsChanged = seams != seamSignature;
        seamSignature = seams;
        return seamsChanged;
    }

    int settleExcess(int now, int[] low) {
        int excess = 0;
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] == EMPTY || powder[i] || depth[i] <= 1) continue;
            if (com.simpleblood.BloodMix.wetness(now, born[i], span(i)) < 0.5f) continue;
            excess += depth[i] - 1;
            depth[i] = 1;
            double y = pixelCenter(i % pw, i / pw).y;
            if (y < lowest) {
                lowest = y;
                low[0] = i % pw;
                low[1] = i / pw;
                low[2] = rgb[i];
            }
        }
        if (excess > 0) {
            dirty = true;
            active = true;
        }
        return excess;
    }

    private static final int F0_BLOOD = 6;

    private static int wetSpecular(float deep, int thinLevel, float dry) {
        float smooth = (0.78f + 0.20f * deep - 0.06f * thinLevel) * (1.0f - dry);
        return Pixels.packAbgr(clamp((int) (smooth * 255)), F0_BLOOD, 0, 255);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    SavedTile.Pixels savePixels(int now) {
        int n = 0;
        for (int i = 0; i < tone.length; i++) if (keeps(i, now)) n++;
        short[] index = new short[n];
        byte[] tones = new byte[n], depths = new byte[n], thins = new byte[n], flags = new byte[n];
        int[] colours = new int[n], elapsed = new int[n], lives = new int[n];
        int k = 0;
        for (int i = 0; i < tone.length; i++) {
            if (!keeps(i, now)) continue;
            index[k] = (short) i;
            tones[k] = tone[i];
            depths[k] = depth[i];
            thins[k] = thin[i];
            flags[k] = powder[i] ? SavedTile.Pixels.POWDER : 0;
            colours[k] = rgb[i];
            elapsed[k] = Math.max(0, now - born[i]);
            lives[k] = life[i];
            k++;
        }
        return new SavedTile.Pixels(index, tones, depths, thins, flags, colours, elapsed, lives);
    }

    private boolean keeps(int i, int now) {
        return tone[i] != EMPTY && (forever(i) || now - born[i] < span(i));
    }

    int loadPixels(SavedTile.Pixels p, int now) {
        boolean live = count > 0;
        int loaded = 0;
        for (int k = 0; k < p.count(); k++) {
            int i = p.index()[k];
            if (i < 0 || i >= tone.length || (mask != null && !mask[i]) || tone[i] != EMPTY) continue;
            count++;
            loaded++;
            tone[i] = p.tone()[k];
            depth[i] = (byte) Math.min(MAX_DEPTH, p.depth()[k]);
            thin[i] = p.thin()[k];
            powder[i] = (p.flags()[k] & SavedTile.Pixels.POWDER) != 0;
            rgb[i] = p.rgb()[k];
            born[i] = now - p.elapsed()[k];
            life[i] = p.life()[k];
        }
        dirty = true;
        if (!live) active = false;
        lastTouched = Math.max(lastTouched, now);
        return loaded;
    }

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
