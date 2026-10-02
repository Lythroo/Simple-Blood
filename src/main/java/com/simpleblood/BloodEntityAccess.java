package com.simpleblood;

public interface BloodEntityAccess {
    long simpleblood$getLastDamageTime();

    void simpleblood$markCrit();

    boolean simpleblood$critRecently();

    void simpleblood$setWound(net.minecraft.world.phys.Vec3 world);

    net.minecraft.world.phys.Vec3 simpleblood$wound();
}
