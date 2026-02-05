package com.bloodmod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class BloodParticles {
    public static final SimpleParticleType BLOOD_DRIP = Registry.register(
            Registries.PARTICLE_TYPE,
            Identifier.of(BloodMod.MOD_ID, "blood_drip"),
            FabricParticleTypes.simple()
    );

    public static final SimpleParticleType BLOOD_SPLASH = Registry.register(
            Registries.PARTICLE_TYPE,
            Identifier.of(BloodMod.MOD_ID, "blood_splash"),
            FabricParticleTypes.simple()
    );

    public static final SimpleParticleType BLOOD_STAIN = Registry.register(
            Registries.PARTICLE_TYPE,
            Identifier.of(BloodMod.MOD_ID, "blood_stain"),
            FabricParticleTypes.simple()
    );

    public static void register() {
        BloodMod.LOGGER.info("Registering blood particles");
    }
}