package com.simpleblood.compat;

import net.minecraft.world.phys.Vec3;

public interface BodyPose {
    Vec3 toWorld(Vec3 plot);

    Vec3 toPlot(Vec3 world);

    Vec3 lastToPlot(Vec3 world);

    Vec3 turn(Vec3 dir);

    default Vec3 lastToWorld(Vec3 plot) {
        return toWorld(plot);
    }
}
