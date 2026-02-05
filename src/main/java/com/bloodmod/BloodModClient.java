package com.bloodmod;

import com.bloodmod.particle.BloodParticle;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BloodModClient implements ClientModInitializer {

    private static final List<ClientBloodBurstTask> activeBursts = new ArrayList<>();
    private static BloodModConfig config;

    @Override
    public void onInitializeClient() {
        BloodMod.LOGGER.info("Blood Mod Client initializing...");

        // Register AutoConfig
        AutoConfig.register(BloodModConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(BloodModConfig.class).getConfig();

        BloodMod.LOGGER.info("Config loaded - Mod enabled: {}", config.globalEnabled());

        // Register particle factories
        ParticleFactoryRegistry.getInstance().register(
                BloodParticles.BLOOD_DRIP,
                BloodParticle.Factory::new
        );

        ParticleFactoryRegistry.getInstance().register(
                BloodParticles.BLOOD_SPLASH,
                BloodParticle.Factory::new
        );

        ParticleFactoryRegistry.getInstance().register(
                BloodParticles.BLOOD_STAIN,
                com.bloodmod.particle.BloodStainParticle.Factory::new
        );

        // Client tick event for burst tasks and low health drip
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            // Skip processing if game is paused (singleplayer only)
            if (client.isPaused()) return;

            // Tick burst tasks
            Iterator<ClientBloodBurstTask> it = activeBursts.iterator();
            while (it.hasNext()) {
                if (!it.next().tick()) {
                    it.remove();
                }
            }

            // Low health drip for all nearby entities
            if (config.globalEnabled() && config.lowHealthEnabled()) {
                for (net.minecraft.entity.Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity entity)) continue;

                    if (entity instanceof PlayerEntity player) {
                        if (!config.playerBleed()) continue;
                        if (player.isCreative() || player.isSpectator()) continue;
                    }

                    // Use the new filter that excludes entities without circulatory systems
                    if (BloodMod.shouldEntityDripAtLowHealth(entity)) {
                        ClientBloodParticleSpawner.spawnBloodForLowHealth(client.world, entity, config);
                    }
                }
            }
        });

        BloodMod.LOGGER.info("Simple Blood initialized!");
    }

    public static BloodModConfig getConfig() {
        return config;
    }

    public static void addBurstTask(ClientBloodBurstTask task) {
        activeBursts.add(task);
    }
}