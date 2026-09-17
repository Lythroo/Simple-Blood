package com.bloodmod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class BloodModClient {

    private static final List<ClientBloodBurstTask> activeBursts = new ArrayList<>();
    private static BloodModConfig config;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    private BloodModClient() {}

    public static void load() {
        config = BloodModConfig.load();
        BloodMod.LOGGER.info("Simple Blood config loaded - mod enabled: {}", config.globalEnabled());
    }

    public static BloodModConfig getConfig() {
        BloodModConfig cfg = config;
        if (cfg == null) {
            cfg = BloodModConfig.defaults();
            config = cfg;
        }
        return cfg;
    }

    public static void setConfig(BloodModConfig cfg) {
        config = cfg == null ? BloodModConfig.defaults() : cfg;
    }

    public static void addBurstTask(ClientBloodBurstTask task) {
        if (task == null) return;
        RenderThread.run(() -> activeBursts.add(task));
    }

    public static void clientTick(Minecraft client) {
        Guard.tick(client);
        Guard.run(Guard.Part.OTHER, "the Blood button or /bloodmod", () -> com.bloodmod.gui.ConfigAccess.tick(client));
        if (config != null) {
            if (Guard.ok(Guard.Part.SURFACES)) {
                Guard.run(Guard.Part.SURFACES, "blood on blocks moving and drying", () -> com.bloodmod.surface.BloodSurfaces.tick(client));
            } else if (com.bloodmod.surface.BloodSurfaces.tileCount() > 0) {
                Guard.run(Guard.Part.OTHER, "clearing blood on blocks", com.bloodmod.surface.BloodSurfaces::clear);
            }
        }
        if (client.level != lastLevel) {
            lastLevel = client.level;
            com.bloodmod.particle.BloodParticle.resetBudget();
            activeBursts.clear();
            if (client.level != null) Guard.reset();
        }
        if (config == null || client.level == null || client.isPaused()) {
            return;
        }
        Guard.run(Guard.Part.SURFACES, "footprints", () -> com.bloodmod.surface.Footprints.tick(client));
        if (com.bloodmod.studio.Studio.enabled()) {
            Guard.run(Guard.Part.OTHER, "the studio emitters", () -> com.bloodmod.studio.Studio.tick(client));
        }

        Iterator<ClientBloodBurstTask> it = activeBursts.iterator();
        while (it.hasNext()) {
            ClientBloodBurstTask task = it.next();
            if (!Guard.call(Guard.Part.HITS, "a hit burst playing", task::tick, false)) {
                it.remove();
            }
        }

        if (config.globalEnabled() && config.lowHealthEnabled()) {
            Guard.run(Guard.Part.HITS, "low health dripping", () -> dripLowHealth(client));
        }
    }

    private static void dripLowHealth(Minecraft client) {
        float threshold = config.lowHealthThreshold();
        long dripWindow = config.dripWindowMs();
        long nowMs = System.currentTimeMillis();
        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity entity)) continue;
            if (entity.getHealth() > entity.getMaxHealth() * threshold) continue;
            if (!(entity instanceof BloodEntityAccess access)
                    || nowMs - access.bloodmod$getLastDamageTime() > dripWindow) {
                continue;
            }

            if (entity instanceof Player player) {
                if (!config.playerBleed()) continue;
                if (player.isCreative() || player.isSpectator()) continue;
            }

            if (BloodMod.isEnvironmentalNoBleed(entity)) {
                continue;
            }

            if (BloodMod.shouldEntityDripAtLowHealth(entity)) {
                ClientBloodParticleSpawner.spawnBloodForLowHealth(client.level, entity, config);
            }
        }
    }
}
