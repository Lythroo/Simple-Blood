package com.bloodmod;

public interface BloodEntityAccess {
    long bloodmod$getLastDamageTime();

    void bloodmod$markCrit();

    boolean bloodmod$critRecently();

    void bloodmod$setWound(net.minecraft.world.phys.Vec3 world);

    net.minecraft.world.phys.Vec3 bloodmod$wound();
}
