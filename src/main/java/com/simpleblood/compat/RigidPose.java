package com.simpleblood.compat;

import net.minecraft.world.phys.Vec3;

public final class RigidPose implements BodyPose {
    private double r00, r01, r02, r10, r11, r12, r20, r21, r22;
    private double tx, ty, tz;
    private double bx, by, bz;
    private RigidPose last;

    public RigidPose() {
        r00 = r11 = r22 = 1;
    }

    public RigidPose(RigidPose p, RigidPose last) {
        set(p);
        if (last == null || last.last == null) {
            this.last = last;
        } else {
            this.last = new RigidPose();
            this.last.set(last);
        }
    }

    public void set(double[] rows, double tx, double ty, double tz, double bx, double by, double bz) {
        r00 = rows[0]; r01 = rows[1]; r02 = rows[2];
        r10 = rows[3]; r11 = rows[4]; r12 = rows[5];
        r20 = rows[6]; r21 = rows[7]; r22 = rows[8];
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.bx = bx; this.by = by; this.bz = bz;
    }

    public void set(RigidPose p) {
        r00 = p.r00; r01 = p.r01; r02 = p.r02;
        r10 = p.r10; r11 = p.r11; r12 = p.r12;
        r20 = p.r20; r21 = p.r21; r22 = p.r22;
        tx = p.tx; ty = p.ty; tz = p.tz;
        bx = p.bx; by = p.by; bz = p.bz;
    }

    @Override
    public Vec3 toWorld(Vec3 plot) {
        double x = plot.x - bx, y = plot.y - by, z = plot.z - bz;
        return new Vec3(r00 * x + r01 * y + r02 * z + tx, r10 * x + r11 * y + r12 * z + ty, r20 * x + r21 * y + r22 * z + tz);
    }

    @Override
    public Vec3 toPlot(Vec3 world) {
        double x = world.x - tx, y = world.y - ty, z = world.z - tz;
        return new Vec3(r00 * x + r10 * y + r20 * z + bx, r01 * x + r11 * y + r21 * z + by, r02 * x + r12 * y + r22 * z + bz);
    }

    @Override
    public Vec3 lastToPlot(Vec3 world) {
        return last == null ? toPlot(world) : last.toPlot(world);
    }

    @Override
    public Vec3 lastToWorld(Vec3 plot) {
        return last == null ? toWorld(plot) : last.toWorld(plot);
    }

    @Override
    public Vec3 turn(Vec3 d) {
        return new Vec3(r00 * d.x + r01 * d.y + r02 * d.z, r10 * d.x + r11 * d.y + r12 * d.z, r20 * d.x + r21 * d.y + r22 * d.z);
    }

    public boolean isTranslation() {
        return Math.abs(r00 - 1) < 1.0e-6 && Math.abs(r11 - 1) < 1.0e-6 && Math.abs(r22 - 1) < 1.0e-6;
    }

    public RigidPose snapped() {
        RigidPose s = new RigidPose();
        double[] rows = {r00, r01, r02, r10, r11, r12, r20, r21, r22};
        for (int i = 0; i < 9; i++) rows[i] = Math.abs(rows[i]) < 0.5 ? 0 : Math.signum(rows[i]);
        s.set(rows, 0, 0, 0, bx, by, bz);
        Vec3 c = toWorld(new Vec3(bx + 0.5, by + 0.5, bz + 0.5));
        Vec3 sc = s.toWorld(new Vec3(bx + 0.5, by + 0.5, bz + 0.5));
        s.tx = Math.floor(c.x) + 0.5 - sc.x;
        s.ty = Math.floor(c.y) + 0.5 - sc.y;
        s.tz = Math.floor(c.z) + 0.5 - sc.z;
        return s;
    }
}
