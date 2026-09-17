package com.bloodmod;

import net.minecraft.core.particles.SimpleParticleType;

//? if fabric {
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
//?}
//? if neoforge {
/*import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class BloodParticles {

    private BloodParticles() {}

    //? if fabric {
    public static final SimpleParticleType BLOOD_DRIP   = simple("blood_drip");
    public static final SimpleParticleType BLOOD_SPLASH = simple("blood_splash");
    public static final SimpleParticleType BLOOD_DROP   = simple("blood_drop");
    public static final SimpleParticleType BLOOD_STREAK = simple("blood_streak");
    public static final SimpleParticleType BLOOD_FOG    = simple("blood_fog");

    private static SimpleParticleType simple(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(BloodMod.MOD_ID, name),
                FabricParticleTypes.simple());
    }

    public static void init() {}
    //?}

    //? if neoforge {
    /*public static final DeferredRegister<ParticleType<?>> REGISTRY =
            DeferredRegister.create(Registries.PARTICLE_TYPE, BloodMod.MOD_ID);

    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DRIP_H   = REGISTRY.register("blood_drip",   () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_SPLASH_H = REGISTRY.register("blood_splash", () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DROP_H   = REGISTRY.register("blood_drop",   () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_STREAK_H = REGISTRY.register("blood_streak", () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_FOG_H    = REGISTRY.register("blood_fog",    () -> newSimple());

    public static SimpleParticleType BLOOD_DRIP;
    public static SimpleParticleType BLOOD_SPLASH;
    public static SimpleParticleType BLOOD_DROP;
    public static SimpleParticleType BLOOD_STREAK;
    public static SimpleParticleType BLOOD_FOG;

    private static SimpleParticleType newSimple() {
        return new SimpleParticleType(false) {};
    }

    public static void bind() {
        BLOOD_DRIP   = BLOOD_DRIP_H.get();
        BLOOD_SPLASH = BLOOD_SPLASH_H.get();
        BLOOD_DROP   = BLOOD_DROP_H.get();
        BLOOD_STREAK = BLOOD_STREAK_H.get();
        BLOOD_FOG    = BLOOD_FOG_H.get();
    }
    *///?}
}
