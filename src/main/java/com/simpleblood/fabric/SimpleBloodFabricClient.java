//? if fabric {
package com.simpleblood.fabric;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.BloodParticles;
import com.simpleblood.particle.BloodFogParticle;
import com.simpleblood.particle.BloodParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

public class SimpleBloodFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SimpleBlood.LOGGER.info("Simple Blood (Fabric) initializing...");

        BloodParticles.init();
        SimpleBloodClient.load();

        ParticleProviderRegistry providers = ParticleProviderRegistry.getInstance();
        providers.register(BloodParticles.BLOOD_DRIP, BloodParticle.Factory::new);
        providers.register(BloodParticles.BLOOD_SPLASH, sprites -> new BloodParticle.Factory(sprites, true));
        providers.register(BloodParticles.BLOOD_DROP, com.simpleblood.particle.BloodDropParticle.Factory::new);
        providers.register(BloodParticles.BLOOD_STREAK, com.simpleblood.particle.BloodStreakParticle.Factory::new);
        providers.register(BloodParticles.BLOOD_FOG, BloodFogParticle.Factory::new);
        providers.register(BloodParticles.BONE_CHIP, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.BONE));
        providers.register(BloodParticles.METAL_FLAKE, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.METAL));
        providers.register(BloodParticles.WOOD_SPLINTER, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.WOOD));
        providers.register(BloodParticles.GIBLET, sprites -> new com.simpleblood.particle.DebrisPieceParticle.Factory(sprites, com.simpleblood.BloodKind.LIQUID));
        providers.register(BloodParticles.BONE_DUST, sprites -> new com.simpleblood.particle.PuffParticle.Factory(sprites, false));
        providers.register(BloodParticles.WIND_PUFF, sprites -> new com.simpleblood.particle.PuffParticle.Factory(sprites, true));
        providers.register(BloodParticles.SPIRIT_SPARK, com.simpleblood.particle.SparkParticle.Factory::new);
        providers.register(BloodParticles.EMBER, com.simpleblood.particle.EmberParticle.Factory::new);

        ClientTickEvents.END_CLIENT_TICK.register(SimpleBloodClient::clientTick);

        FabricSurfaceHook.register();
        FabricPreviewHook.register();
        FabricConfigAccessHook.register();

        SimpleBlood.LOGGER.info("Simple Blood (Fabric) ready.");
    }

}
//?}
