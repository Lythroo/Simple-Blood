//? if neoforge {
/*package com.simpleblood.neoforge;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.BloodParticles;
import com.simpleblood.particle.BloodFogParticle;
import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "simpleblood", dist = Dist.CLIENT)
public class SimpleBloodNeoForge {

    public SimpleBloodNeoForge(IEventBus modBus, ModContainer container) {
        SimpleBlood.LOGGER.info("Simple Blood (NeoForge) initializing...");

        BloodParticles.REGISTRY.register(modBus);

        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterParticleProviders);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForgeSurfaceHook.register();
        NeoForgePreviewHook.register(modBus);
        NeoForgeConfigAccessHook.register();

        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mc, parent) -> new com.simpleblood.gui.BloodConfigScreen(parent));

        SimpleBlood.LOGGER.info("Simple Blood (NeoForge) ready.");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        SimpleBloodClient.load();
    }

    private void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        BloodParticles.bind();
        event.registerSpriteSet(BloodParticles.BLOOD_DRIP, BloodParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BLOOD_SPLASH, sprites -> new BloodParticle.Factory(sprites, true));
        event.registerSpriteSet(BloodParticles.BLOOD_DROP, com.simpleblood.particle.BloodDropParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BLOOD_STREAK, com.simpleblood.particle.BloodStreakParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BLOOD_FOG, BloodFogParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.BONE_CHIP, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.BONE));
        event.registerSpriteSet(BloodParticles.METAL_FLAKE, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.METAL));
        event.registerSpriteSet(BloodParticles.WOOD_SPLINTER, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.WOOD));
        event.registerSpriteSet(BloodParticles.BONE_DUST, sprites -> new com.simpleblood.particle.PuffParticle.Factory(sprites, false));
        event.registerSpriteSet(BloodParticles.WIND_PUFF, sprites -> new com.simpleblood.particle.PuffParticle.Factory(sprites, true));
        event.registerSpriteSet(BloodParticles.SPIRIT_SPARK, com.simpleblood.particle.SparkParticle.Factory::new);
        event.registerSpriteSet(BloodParticles.EMBER, com.simpleblood.particle.EmberParticle.Factory::new);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        SimpleBloodClient.clientTick(Minecraft.getInstance());
    }

}
*///?}
