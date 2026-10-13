package com.simpleblood;

/**
 * Duck interface implemented by {@code LivingEntityMixin}: lets shared code read when an
 * entity last took damage without holding a map of entities.
 */
public interface BloodEntityAccess {
    /** {@link System#currentTimeMillis()} of the last damage burst, or 0 if never hit. */
    long simpleblood$getLastDamageTime();

    /** A critical-hit animation just played on this entity. */
    void simpleblood$markCrit();

    /** Whether a critical hit was marked within the last few ticks. */
    boolean simpleblood$critRecently();

    /** Remembers where the last blow landed (world space); null forgets it. */
    void simpleblood$setWound(net.minecraft.world.phys.Vec3 world);

    /** The last wound in world space, following the body as it moves and turns; null if none. */
    net.minecraft.world.phys.Vec3 simpleblood$wound();
}
