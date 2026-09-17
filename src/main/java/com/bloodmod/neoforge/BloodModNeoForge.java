//? if neoforge {
/*package com.bloodmod.neoforge;

import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodParticles;
import com.bloodmod.particle.BloodFogParticle;
import com.bloodmod.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "bloodmod", dist = Dist.CLIENT)
public class BloodModNeoForge {

    public BloodModNeoForge(IEventBus modBus, ModContainer container) {
        BloodMod.LOGGER.info("Simple Blood (NeoForge) initializing...");

        BloodParticles.REGISTRY.register(modBus);

        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterParticleProviders);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForgeSurfaceHook.register();
        NeoForgePreviewHook.register(modBus);
        NeoForgeConfigAccessHook.register();

        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mc, parent) -> new com.bloodmod.gui.BloodConfigScreen(parent));

        BloodMod.LOGGER.info("Simple Blood (NeoForge) ready.");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        BloodModClient.load();
    }

    private void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        BloodParticles.bind();
        event.registerSpriteSet(BloodParticles.BLOOD_DRIP, BloodParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BLOOD_SPLASH, sprites -> new BloodParticle.Factory(sprites, true));
        event.registerSpriteSet(BloodParticles.BLOOD_DROP, com.bloodmod.particle.BloodDropParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BLOOD_STREAK, com.bloodmod.particle.BloodStreakParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BLOOD_FOG, BloodFogParticle.Factory::new);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        BloodModClient.clientTick(Minecraft.getInstance());
    }

}
*///?}
