package com.simpleblood.geom;

public final class OrientedBox {
    public final double cx, cy, cz;
    public final double hx, hy, hz;
    public final double radius;
    private final double ux, uy, uz, vx, vy, vz, wx, wy, wz;

    private static final double PARALLEL = 1.0e-12;
    private static final double NO_TURN = 1.0e-9;
    private static final double SQRT3 = Math.sqrt(3);

    public OrientedBox(double cx, double cy, double cz, double qx, double qy, double qz, double qw,
                       double hx, double hy, double hz) {
        double len = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (!(len > NO_TURN) || !Double.isFinite(len)) throw new IllegalArgumentException("turn is not a rotation: " + len);
        if (!(hx >= 0 && hy >= 0 && hz >= 0) || !Double.isFinite(hx + hy + hz)) {
            throw new IllegalArgumentException("half extents must be finite and not negative: " + hx + " " + hy + " " + hz);
        }
        if (!Double.isFinite(cx + cy + cz)) throw new IllegalArgumentException("middle is not finite");
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
        this.radius = Math.sqrt(hx * hx + hy * hy + hz * hz);
        double x = qx / len, y = qy / len, z = qz / len, w = qw / len;
        this.ux = 1 - 2 * (y * y + z * z);
        this.uy = 2 * (x * y + w * z);
        this.uz = 2 * (x * z - w * y);
        this.vx = 2 * (x * y - w * z);
        this.vy = 1 - 2 * (x * x + z * z);
        this.vz = 2 * (y * z + w * x);
        this.wx = 2 * (x * z + w * y);
        this.wy = 2 * (y * z - w * x);
        this.wz = 1 - 2 * (x * x + y * y);
    }

    public void toWorldDir(double x, double y, double z, double[] out) {
        out[0] = ux * x + vx * y + wx * z;
        out[1] = uy * x + vy * y + wy * z;
        out[2] = uz * x + vz * y + wz * z;
    }

    public void toLocalDir(double x, double y, double z, double[] out) {
        out[0] = along(0, x, y, z);
        out[1] = along(1, x, y, z);
        out[2] = along(2, x, y, z);
    }

    public void toWorldPoint(double x, double y, double z, double[] out) {
        toWorldDir(x, y, z, out);
        out[0] += cx;
        out[1] += cy;
        out[2] += cz;
    }

    public void toLocalPoint(double x, double y, double z, double[] out) {
        toLocalDir(x - cx, y - cy, z - cz, out);
    }

    public boolean contains(double x, double y, double z) {
        return contains(x, y, z, 0);
    }

    public boolean contains(double x, double y, double z, double margin) {
        double dx = x - cx, dy = y - cy, dz = z - cz, reach = radius + margin * SQRT3;
        if (dx * dx + dy * dy + dz * dz > reach * reach) return false;
        return Math.abs(along(0, dx, dy, dz)) <= hx + margin && Math.abs(along(1, dx, dy, dz)) <= hy + margin
                && Math.abs(along(2, dx, dy, dz)) <= hz + margin;
    }

    public static int stepOut(OrientedBox[] boxes, double[] point, double dx, double dy, double dz, double step, int maxSteps) {
        for (int k = 0; ; k++) {
            if (!inAny(boxes, point)) return k;
            if (k == maxSteps) return -1;
            point[0] += dx * step;
            point[1] += dy * step;
            point[2] += dz * step;
        }
    }

    private static boolean inAny(OrientedBox[] boxes, double[] p) {
        for (OrientedBox b : boxes) if (b != null && b.contains(p[0], p[1], p[2])) return true;
        return false;
    }

    public double entry(double ax, double ay, double az, double bx, double by, double bz, double[] normal) {
        if (!nearSegment(ax, ay, az, bx, by, bz)) return -1;
        double rx = ax - cx, ry = ay - cy, rz = az - cz, sx = bx - ax, sy = by - ay, sz = bz - az;
        double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
        int axis = -1;
        double side = 0;
        for (int i = 0; i < 3; i++) {
            double o = along(i, rx, ry, rz), d = along(i, sx, sy, sz), h = half(i);
            if (Math.abs(d) < PARALLEL) {
                if (Math.abs(o) > h) return -1;
                continue;
            }
            double t1 = (-h - o) / d, t2 = (h - o) / d;
            double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
            if (lo > near) {
                near = lo;
                axis = i;
                side = d > 0 ? -1 : 1;
            }
            far = Math.min(far, hi);
            if (near > far) return -1;
        }
        if (axis < 0 || near < 0 || near > 1) return -1;
        if (normal != null) toWorldDir(axis == 0 ? side : 0, axis == 1 ? side : 0, axis == 2 ? side : 0, normal);
        return near;
    }

    public double exit(double ax, double ay, double az, double bx, double by, double bz) {
        double rx = ax - cx, ry = ay - cy, rz = az - cz, sx = bx - ax, sy = by - ay, sz = bz - az;
        double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            double o = along(i, rx, ry, rz), d = along(i, sx, sy, sz), h = half(i);
            if (Math.abs(d) < PARALLEL) {
                if (Math.abs(o) > h) return -1;
                continue;
            }
            double t1 = (-h - o) / d, t2 = (h - o) / d;
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return -1;
        }
        return far == Double.POSITIVE_INFINITY || far < 0 ? -1 : far;
    }

    public void nearestOnSurface(double px, double py, double pz, double[] point, double[] normal) {
        double rx = px - cx, ry = py - cy, rz = pz - cz;
        double lx = clamp(along(0, rx, ry, rz), hx), ly = clamp(along(1, rx, ry, rz), hy), lz = clamp(along(2, rx, ry, rz), hz);
        double gx = hx - Math.abs(lx), gy = hy - Math.abs(ly), gz = hz - Math.abs(lz);
        int axis = gx <= gy && gx <= gz ? 0 : gy <= gz ? 1 : 2;
        double sx = 0, sy = 0, sz = 0;
        if (axis == 0) lx = (sx = lx >= 0 ? 1 : -1) * hx;
        else if (axis == 1) ly = (sy = ly >= 0 ? 1 : -1) * hy;
        else lz = (sz = lz >= 0 ? 1 : -1) * hz;
        toWorldPoint(lx, ly, lz, point);
        toWorldDir(sx, sy, sz, normal);
    }

    public boolean nearSegment(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 < PARALLEL ? 0 : ((cx - ax) * dx + (cy - ay) * dy + (cz - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        double gx = ax + dx * t - cx, gy = ay + dy * t - cy, gz = az + dz * t - cz;
        return gx * gx + gy * gy + gz * gz <= radius * radius;
    }

    private double along(int i, double x, double y, double z) {
        return switch (i) {
            case 0 -> ux * x + uy * y + uz * z;
            case 1 -> vx * x + vy * y + vz * z;
            default -> wx * x + wy * y + wz * z;
        };
    }

    private double half(int i) {
        return i == 0 ? hx : i == 1 ? hy : hz;
    }

    private static double clamp(double v, double h) {
        return Math.max(-h, Math.min(h, v));
    }
}
