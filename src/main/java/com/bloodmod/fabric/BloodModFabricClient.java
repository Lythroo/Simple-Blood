//? if fabric {
package com.bloodmod.fabric;

import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodParticles;
import com.bloodmod.particle.BloodFogParticle;
import com.bloodmod.particle.BloodParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

public class BloodModFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BloodMod.LOGGER.info("Simple Blood (Fabric) initializing...");

        BloodParticles.init();
        BloodModClient.load();

        ParticleProviderRegistry providers = ParticleProviderRegistry.getInstance();
        providers.register(BloodParticles.BLOOD_DRIP, BloodParticle.Factory::new);
        providers.register(BloodParticles.BLOOD_SPLASH, sprites -> new BloodParticle.Factory(sprites, true));
        providers.register(BloodParticles.BLOOD_DROP, com.bloodmod.particle.BloodDropParticle.Factory::new);
        providers.register(BloodParticles.BLOOD_STREAK, com.bloodmod.particle.BloodStreakParticle.Factory::new);
        providers.register(BloodParticles.BLOOD_FOG, BloodFogParticle.Factory::new);

        ClientTickEvents.END_CLIENT_TICK.register(BloodModClient::clientTick);

        FabricSurfaceHook.register();
        FabricPreviewHook.register();
        FabricConfigAccessHook.register();

        BloodMod.LOGGER.info("Simple Blood (Fabric) ready.");
    }

}
//?}
