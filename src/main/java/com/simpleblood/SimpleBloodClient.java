package com.simpleblood;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SimpleBloodClient {

    private static final List<ClientBloodBurstTask> activeBursts = new ArrayList<>();
    private static SimpleBloodConfig config;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    private SimpleBloodClient() {}

    public static void load() {
        config = SimpleBloodConfig.load();
        SimpleBlood.LOGGER.info("Simple Blood config loaded - mod enabled: {}", config.globalEnabled());
    }

    public static SimpleBloodConfig getConfig() {
        SimpleBloodConfig cfg = config;
        if (cfg == null) {
            cfg = SimpleBloodConfig.defaults();
            config = cfg;
        }
        return cfg;
    }

    public static void setConfig(SimpleBloodConfig cfg) {
        config = cfg == null ? SimpleBloodConfig.defaults() : cfg;
    }

    public static void addBurstTask(ClientBloodBurstTask task) {
        if (task == null) return;
        RenderThread.run(() -> activeBursts.add(task));
    }

    public static void clientTick(Minecraft client) {
        Guard.tick(client);
        Guard.run(Guard.Part.OTHER, "the Blood button or /simpleblood", () -> com.simpleblood.gui.ConfigAccess.tick(client));
        Guard.run(Guard.Part.OTHER, "reading your blood colour from your skin", SkinColours::tickOwn);
        if (config != null) {
            if (Guard.ok(Guard.Part.SURFACES)) {
                Guard.run(Guard.Part.SURFACES, "blood on blocks moving and drying", () -> com.simpleblood.surface.BloodSurfaces.tick(client));
            } else if (com.simpleblood.surface.BloodSurfaces.tileCount() > 0) {
                Guard.run(Guard.Part.OTHER, "clearing blood on blocks", com.simpleblood.surface.BloodSurfaces::clear);
            }
        }
        if (client.level != lastLevel) {
            lastLevel = client.level;
            com.simpleblood.particle.BloodParticle.resetBudget();
            com.simpleblood.particle.DebrisPieceParticle.forgetLying();
            activeBursts.clear();
            if (client.level != null) Guard.reset();
        }
        //? if 1.20.1 {
        /*if (config == null || client.level == null || client.isPaused()) {
        *///?} else {
        if (config == null || client.level == null || client.isPaused()
                || !client.level.tickRateManager().runsNormally()) {
        //?}
            return;
        }
        Guard.run(Guard.Part.SURFACES, "footprints", () -> com.simpleblood.surface.Footprints.tick(client));
        Guard.run(Guard.Part.HITS, "Physics Mod ragdolls bleeding", () -> com.simpleblood.compat.PhysicsModRagdolls.tick(client.level));

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
                    || nowMs - access.simpleblood$getLastDamageTime() > dripWindow) {
                continue;
            }

            if (entity instanceof Player player) {
                if (!config.playerBleed()) continue;
                if (player.isCreative() || player.isSpectator()) continue;
            }

            if (SimpleBlood.isEnvironmentalNoBleed(entity)) {
                continue;
            }

            if (SimpleBlood.shouldEntityDripAtLowHealth(entity)) {
                ClientBloodParticleSpawner.spawnBloodForLowHealth(client.level, entity, config);
            }
        }
    }
}
