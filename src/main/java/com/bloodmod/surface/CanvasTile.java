package com.bloodmod.surface;

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

    public record Key(long pos, Direction face, int box) {}

    interface NeighbourLookup {
        boolean filledAcross(CanvasTile tile, int px, int py);
    }

    interface FlowContext extends NeighbourLookup {
        CanvasTile across(CanvasTile tile, int px, int py, int[] xy, boolean create);

        boolean canDrain(CanvasTile tile, int px, int py);

        boolean drain(CanvasTile tile, int px, int py, int colour);
    }

    static final int RIPPLE_TICKS = 22;

    final Key key;
    final BlockPos pos;
    final Direction face;
    BlockState state;
    final boolean custom;
    double nx, ny, nz;
    private boolean[] mask;
    CanvasTile twin;
    Vec3 swayPivot, swayAxis;
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
    int slot = -1;
    boolean dirty;
    int lastTouched;
    int light;
    int lightTick = -100;
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

    Vec3 center() {
        return new Vec3(ox + (ux * w + vx * h) * 0.5, oy + (uy * w + vy * h) * 0.5, oz + (uz * w + vz * h) * 0.5);
    }

    boolean inside(int px, int py) {
        return px >= 0 && py >= 0 && px < pw && py < ph;
    }

    public int count() { return count; }
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

    int wetDepthAt(int px, int py, int now) {
        if (!inside(px, py)) return 0;
        int i = py * pw + px;
        if (tone[i] == EMPTY || thin[i] != 0) return 0;
        if (now - born[i] > life[i] * 0.55f) return 0;
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

    void rebind(BlockState newState) {
        this.state = newState;
    }

    void flipNormal() {
        nx = -nx; ny = -ny; nz = -nz;
    }

    boolean hasRipples() { return !ripples.isEmpty(); }

    void addRipple(int cx, int cy, int now) {
        if (ripples.size() >= 3) ripples.remove(0);
        ripples.add(new int[]{cx, cy, now});
        dirty = true;
    }

    List<int[]> snapshot() {
        List<int[]> out = new ArrayList<>(count);
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) out.add(new int[]{i % pw, i / pw, rgb[i], depth[i], powder[i] ? 1 : 0});
        }
        return out;
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
            if (count >= 6) addRipple(px, py, now);
            addVolume(px, py, n, colour, now, lifetime, rng, pattern);
            return n;
        }

        Set<Long> blob = new HashSet<>();
        for (int i = 0; i < tone.length; i++) {
            if (tone[i] != EMPTY) blob.add(pack(i % pw, i / pw));
        }
        List<int[]> chosen = new ArrayList<>(n);
        boolean vertical = custom || face.getAxis().isHorizontal();

        if (face == Direction.UP && count >= 6 && filledNeighbours(px, py) > 0) {
            addRipple(px, py, now);
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
                if (!inside(cx, cy)) wgt *= 0.6;
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
        if (tone[i] == EMPTY) {
            count++;
            depth[i] = 1;
            born[i] = now;
            life[i] = (int) (lifetime * (0.8f + rng.nextFloat() * 0.4f));
        } else {
            depth[i] = (byte) Math.min(MAX_DEPTH, depth[i] + 1);
            born[i] = Math.max(born[i], now - life[i] / 4);
        }
        thin[i] = (byte) Math.max(0, Math.min(3, thinLevel));
        powder[i] = false;
        byte t = baseTone[i];
        if (pattern) {
            int hsh = (px * 73856093) ^ (py * 19349663) ^ (pos.hashCode() * 83492791);
            t = ((hsh >>> 4) % 8) == 0 ? DARK : BASE;
        }
        tone[i] = t;
        rgb[i] = colour;
        dirty = true;
        active = true;
        lastTouched = now;
    }

    void paintPowder(int px, int py, int colour, int now, int lifetime, RandomSource rng) {
        if (!inside(px, py)) return;
        paintThin(px, py, colour, now, lifetime, rng, true, 0);
        int i = py * pw + px;
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
                    int j = ny * pw + nx;
                    int nd = depth[j];
                    if (nd == 0 ? d >= SPREAD_DEPTH : d - nd >= 2) {
                        if (nd < bestDepth) { bestDepth = nd; bestKind = 0; bestIdx = j; }
                    }
                } else if (ctx != null) {
                    CanvasTile other = ctx.across(this, nx, ny, xy, d >= SPREAD_DEPTH);
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

    void accelerateAging(int ticks) {
        for (int i = 0; i < born.length; i++) {
            if (tone[i] != EMPTY) born[i] -= ticks;
        }
        dirty = true;
    }

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
                depth[i]--;
                changed = true;
            } else if (agePct > 60 && depth[i] == 1 && isEdge(i) && rng.nextInt(100) < (agePct - 60) / 2) {
                depth[i] = 0;
                tone[i] = EMPTY;
                count--;
                changed = true;
            } else if (agePct > 30 && ((now + slot) & 63) == 0) {
                changed = true;
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
        if (!inside(x, y)) return false;
        byte c = cls[y * pw + x];
        return c == 1 || c == 4;
    }

    private boolean filledOrAcross(int x, int y, NeighbourLookup lookup) {
        if (inside(x, y)) return tone[y * pw + x] != EMPTY;
        return lookup != null && lookup.filledAcross(this, x, y);
    }

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
            if (slot < 0) return;
        }
        if (image == null) {
            image = new NativeImage(pw, ph, true);
        }
        boolean puddle = !custom && face == Direction.UP && count > 2;
        byte[] cls = puddle ? edgeClasses(lookup) : null;
        ripples.removeIf(r -> now - r[2] >= RIPPLE_TICKS);

        float[] ages = new float[tone.length];
        for (int i = 0; i < tone.length; i++) {
            ages[i] = tone[i] == EMPTY || powder[i] ? -1 : (now - born[i]) / (float) Math.max(1, life[i]);
        }

        for (int i = 0; i < tone.length; i++) {
            int x = i % pw, y = i / pw;
            if (tone[i] == EMPTY) {
                Pixels.setAbgr(image, x, y, 0);
                continue;
            }
            int age = now - born[i];
            float agePct = age / (float) Math.max(1, life[i]);
            if (powder[i]) {
                int c = rgb[i];
                float alpha = opacity;
                if (agePct > 0.6f) alpha *= 1.0f - (agePct - 0.6f) / 0.4f;
                Pixels.setAbgr(image, x, y, Pixels.packAbgr((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, clamp((int) (alpha * 255))));
                continue;
            }
            int t = tone[i];
            float shade = smoothAge(ages, x, y);
            float dry = Math.max(0f, Math.min(1f, (shade - 0.35f) / 0.35f));
            float sd = smoothDepth(x, y);
            float deep = Math.min(1.0f, (sd - 1) / 3.0f);
            float f;
            if (puddle && cls[i] != 3) {
                float k = deep * (1.0f - 0.5f * dry);
                if (cls[i] == 1) f = 1.0f - 0.22f * k;
                else if (cls[i] == 2) f = 1.0f + 0.30f * k;
                else f = 1.0f;
            } else {
                f = TONE_FACTOR[t];
            }
            f *= 1.0f - 0.16f * dry;
            float rippleAlpha = 1.0f;
            for (int[] r : ripples) {
                float lifeFrac = (now - r[2]) / (float) RIPPLE_TICKS;
                double radius = (now - r[2]) * 0.45 + 0.5;
                double dd = Math.sqrt((x - r[0]) * (x - r[0]) + (y - r[1]) * (y - r[1]));
                if (Math.abs(dd - radius) < 0.75 + lifeFrac * 0.5) {
                    float k = 1.0f - lifeFrac;
                    f = Math.max(f, f + (TONE_FACTOR[LIGHT] - f) * 0.45f * k);
                    rippleAlpha = 1.0f - 0.25f * k;
                    break;
                }
            }
            int c = rgb[i];
            int r = clamp((int) (((c >> 16) & 0xFF) * f));
            int g = clamp((int) (((c >> 8) & 0xFF) * f));
            int b = clamp((int) ((c & 0xFF) * f));
            float alpha = opacity;
            alpha *= 0.62f + 0.38f * Math.min(1.0f, (sd - 1) / 2.0f);
            alpha *= 1.0f - 0.18f * thin[i];
            alpha *= rippleAlpha;
            if (shade > 0.75f) alpha *= 1.0f - Math.min(1f, (shade - 0.75f) / 0.25f);
            Pixels.setAbgr(image, x, y, Pixels.packAbgr(r, g, b, clamp((int) (alpha * 255))));
        }
        atlas.upload(slot, image);
        dirty = false;
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    void dispose(BloodCanvasAtlas atlas) {
        if (atlas != null) atlas.releaseSlot(slot);
        slot = -1;
        if (image != null) {
            image.close();
            image = null;
        }
    }
}
