package com.simpleblood;

import net.minecraft.core.particles.SimpleParticleType;

//? if fabric {
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
//?}
//? if neoforge && >1.20.1 {
/*import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}
//? if neoforge && 1.20.1 {
/*import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
*///?}

public final class BloodParticles {

    private BloodParticles() {}

    //? if fabric {
    public static final SimpleParticleType BLOOD_DRIP   = simple("blood_drip");
    public static final SimpleParticleType BLOOD_SPLASH = simple("blood_splash");
    public static final SimpleParticleType BLOOD_DROP   = simple("blood_drop");
    public static final SimpleParticleType BLOOD_STREAK = simple("blood_streak");
    public static final SimpleParticleType BLOOD_FOG    = simple("blood_fog");
    public static final SimpleParticleType BONE_CHIP    = simple("bone_chip");
    public static final SimpleParticleType BONE_DUST    = simple("bone_dust");
    public static final SimpleParticleType METAL_FLAKE  = simple("metal_flake");
    public static final SimpleParticleType WOOD_SPLINTER = simple("wood_splinter");
    public static final SimpleParticleType GIBLET       = simple("giblet");
    public static final SimpleParticleType SPIRIT_SPARK = simple("spirit_spark");
    public static final SimpleParticleType WIND_PUFF    = simple("wind_puff");
    public static final SimpleParticleType EMBER        = simple("ember");

    private static SimpleParticleType simple(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Ids.mod(name),
                FabricParticleTypes.simple());
    }

    public static void init() {}
    //?}

    //? if neoforge && >1.20.1 {
    /*public static final DeferredRegister<ParticleType<?>> REGISTRY =
            DeferredRegister.create(Registries.PARTICLE_TYPE, SimpleBlood.MOD_ID);

    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DRIP_H   = REGISTRY.register("blood_drip",   () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_SPLASH_H = REGISTRY.register("blood_splash", () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DROP_H   = REGISTRY.register("blood_drop",   () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_STREAK_H = REGISTRY.register("blood_streak", () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_FOG_H    = REGISTRY.register("blood_fog",    () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BONE_CHIP_H    = REGISTRY.register("bone_chip",    () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> BONE_DUST_H    = REGISTRY.register("bone_dust",    () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> METAL_FLAKE_H  = REGISTRY.register("metal_flake",  () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> WOOD_SPLINTER_H = REGISTRY.register("wood_splinter", () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> GIBLET_H       = REGISTRY.register("giblet",       () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPIRIT_SPARK_H = REGISTRY.register("spirit_spark", () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_PUFF_H    = REGISTRY.register("wind_puff",    () -> newSimple());
    private static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER_H        = REGISTRY.register("ember",        () -> newSimple());

    public static SimpleParticleType BLOOD_DRIP;
    public static SimpleParticleType BLOOD_SPLASH;
    public static SimpleParticleType BLOOD_DROP;
    public static SimpleParticleType BLOOD_STREAK;
    public static SimpleParticleType BLOOD_FOG;
    public static SimpleParticleType BONE_CHIP;
    public static SimpleParticleType BONE_DUST;
    public static SimpleParticleType METAL_FLAKE;
    public static SimpleParticleType WOOD_SPLINTER;
    public static SimpleParticleType GIBLET;
    public static SimpleParticleType SPIRIT_SPARK;
    public static SimpleParticleType WIND_PUFF;
    public static SimpleParticleType EMBER;

    private static SimpleParticleType newSimple() {
        return new SimpleParticleType(false) {};
    }

    public static void bind() {
        BLOOD_DRIP   = BLOOD_DRIP_H.get();
        BLOOD_SPLASH = BLOOD_SPLASH_H.get();
        BLOOD_DROP   = BLOOD_DROP_H.get();
        BLOOD_STREAK = BLOOD_STREAK_H.get();
        BLOOD_FOG    = BLOOD_FOG_H.get();
        BONE_CHIP    = BONE_CHIP_H.get();
        BONE_DUST    = BONE_DUST_H.get();
        METAL_FLAKE  = METAL_FLAKE_H.get();
        WOOD_SPLINTER = WOOD_SPLINTER_H.get();
        GIBLET       = GIBLET_H.get();
        SPIRIT_SPARK = SPIRIT_SPARK_H.get();
        WIND_PUFF    = WIND_PUFF_H.get();
        EMBER        = EMBER_H.get();
    }
    *///?}

    //? if neoforge && 1.20.1 {
    /*    public static final DeferredRegister<ParticleType<?>> REGISTRY =
            DeferredRegister.create(Registries.PARTICLE_TYPE, SimpleBlood.MOD_ID);

    private static final RegistryObject<SimpleParticleType> BLOOD_DRIP_H   = REGISTRY.register("blood_drip",   () -> newSimple());
    private static final RegistryObject<SimpleParticleType> BLOOD_SPLASH_H = REGISTRY.register("blood_splash", () -> newSimple());
    private static final RegistryObject<SimpleParticleType> BLOOD_DROP_H   = REGISTRY.register("blood_drop",   () -> newSimple());
    private static final RegistryObject<SimpleParticleType> BLOOD_STREAK_H = REGISTRY.register("blood_streak", () -> newSimple());
    private static final RegistryObject<SimpleParticleType> BLOOD_FOG_H    = REGISTRY.register("blood_fog",    () -> newSimple());
    private static final RegistryObject<SimpleParticleType> BONE_CHIP_H    = REGISTRY.register("bone_chip",    () -> newSimple());
    private static final RegistryObject<SimpleParticleType> BONE_DUST_H    = REGISTRY.register("bone_dust",    () -> newSimple());
    private static final RegistryObject<SimpleParticleType> METAL_FLAKE_H  = REGISTRY.register("metal_flake",  () -> newSimple());
    private static final RegistryObject<SimpleParticleType> WOOD_SPLINTER_H = REGISTRY.register("wood_splinter", () -> newSimple());
    private static final RegistryObject<SimpleParticleType> GIBLET_H       = REGISTRY.register("giblet",       () -> newSimple());
    private static final RegistryObject<SimpleParticleType> SPIRIT_SPARK_H = REGISTRY.register("spirit_spark", () -> newSimple());
    private static final RegistryObject<SimpleParticleType> WIND_PUFF_H    = REGISTRY.register("wind_puff",    () -> newSimple());
    private static final RegistryObject<SimpleParticleType> EMBER_H        = REGISTRY.register("ember",        () -> newSimple());

    public static SimpleParticleType BLOOD_DRIP;
    public static SimpleParticleType BLOOD_SPLASH;
    public static SimpleParticleType BLOOD_DROP;
    public static SimpleParticleType BLOOD_STREAK;
    public static SimpleParticleType BLOOD_FOG;
    public static SimpleParticleType BONE_CHIP;
    public static SimpleParticleType BONE_DUST;
    public static SimpleParticleType METAL_FLAKE;
    public static SimpleParticleType WOOD_SPLINTER;
    public static SimpleParticleType GIBLET;
    public static SimpleParticleType SPIRIT_SPARK;
    public static SimpleParticleType WIND_PUFF;
    public static SimpleParticleType EMBER;

    private static SimpleParticleType newSimple() {
        return new SimpleParticleType(false) {};
    }

    public static void bind() {
        BLOOD_DRIP   = BLOOD_DRIP_H.get();
        BLOOD_SPLASH = BLOOD_SPLASH_H.get();
        BLOOD_DROP   = BLOOD_DROP_H.get();
        BLOOD_STREAK = BLOOD_STREAK_H.get();
        BLOOD_FOG    = BLOOD_FOG_H.get();
        BONE_CHIP    = BONE_CHIP_H.get();
        BONE_DUST    = BONE_DUST_H.get();
        METAL_FLAKE  = METAL_FLAKE_H.get();
        WOOD_SPLINTER = WOOD_SPLINTER_H.get();
        GIBLET       = GIBLET_H.get();
        SPIRIT_SPARK = SPIRIT_SPARK_H.get();
        WIND_PUFF    = WIND_PUFF_H.get();
        EMBER        = EMBER_H.get();
    }
    *///?}
}
