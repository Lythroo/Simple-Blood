package com.simpleblood;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shared client-side state and per-tick driver for Simple Blood.
 *
 * Loader entrypoints ({@code com.simpleblood.fabric} / {@code com.simpleblood.neoforge})
 * are responsible only for wiring: they call {@link #load()} once, register the
 * particle providers, and invoke {@link #clientTick(Minecraft)} every client tick.
 * All actual behaviour lives here so it stays identical across loaders.
 */
public final class SimpleBloodClient {

    private static final List<ClientBloodBurstTask> activeBursts = new ArrayList<>();
    private static SimpleBloodConfig config;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    private SimpleBloodClient() {}

    /** Load config from disk. Call once during client init. */
    public static void load() {
        config = SimpleBloodConfig.load();
        SimpleBlood.LOGGER.info("Simple Blood config loaded - mod enabled: {}", config.globalEnabled());
    }

    /** The live settings. Never null once the client has started: defaults stand in until loaded. */
    public static SimpleBloodConfig getConfig() {
        SimpleBloodConfig cfg = config;
        if (cfg == null) {
            cfg = SimpleBloodConfig.defaults();
            config = cfg;
        }
        return cfg;
    }

    /** Swaps in another config instance (the settings screen restoring a backup). */
    public static void setConfig(SimpleBloodConfig cfg) {
        config = cfg == null ? SimpleBloodConfig.defaults() : cfg;
    }

    /** Any thread: the list itself is the render thread's. */
    public static void addBurstTask(ClientBloodBurstTask task) {
        if (task == null) return;
        RenderThread.run(() -> activeBursts.add(task));
    }

    /**
     * Advances active hit-bursts and emits low-health drips for nearby entities.
     * Safe to call before {@link #load()}; it simply no-ops until config exists.
     */
    public static void clientTick(Minecraft client) {
        // Anything waiting to be said about an earlier error, and a broken settings screen.
        Guard.tick(client);
        // The settings button on menus and the /simpleblood command: paused or not, in a world or not.
        Guard.run(Guard.Part.OTHER, "the Blood button or /simpleblood", () -> com.simpleblood.gui.ConfigAccess.tick(client));
        Guard.run(Guard.Part.OTHER, "reading your blood colour from your skin", SkinColours::tickOwn);
        // Surface blood ages, validates and uploads every tick, and clears itself when the
        // level goes away; it must run even while paused so uploads never lag behind.
        if (config != null) {
            if (Guard.ok(Guard.Part.SURFACES)) {
                Guard.run(Guard.Part.SURFACES, "blood on blocks moving and drying", () -> com.simpleblood.surface.BloodSurfaces.tick(client));
            } else if (com.simpleblood.surface.BloodSurfaces.tileCount() > 0) {
                // Switched off after an error: whatever is left on the blocks goes.
                Guard.run(Guard.Part.OTHER, "clearing blood on blocks", com.simpleblood.surface.BloodSurfaces::clear);
            }
        }
        // Blood on mobs dries and uploads every tick too, paused or not (it stands still itself).
        if (config != null) {
            Guard.run(Guard.Part.SURFACES, "blood on mobs drying", () -> com.simpleblood.mob.MobBlood.tick(client));
        }
        if (client.level != lastLevel) {
            // The particle engine dropped every particle with the old level.
            lastLevel = client.level;
            com.simpleblood.particle.BloodParticle.resetBudget();
            com.simpleblood.particle.DebrisPieceParticle.forgetLying();
            activeBursts.clear();
            // A fresh world gets every part of the mod back; if it breaks again it is caught again.
            if (client.level != null) Guard.reset();
            // By now every mod has registered its mobs: match the mob table to this game.
            if (client.level != null && config != null) Guard.run(Guard.Part.OTHER, "matching the mob table to this game", () -> MobTable.sync(config));
        }
        //? if 1.20.1 {
        /*if (config == null || client.level == null || client.isPaused()) {
        *///?} else {
        if (config == null || client.level == null || client.isPaused()
                || !client.level.tickRateManager().runsNormally()) {
        //?}
            return; // the world stands still, and so does the blood
        }
        Guard.run(Guard.Part.SURFACES, "footprints", () -> com.simpleblood.surface.Footprints.tick(client));
        Guard.run(Guard.Part.HITS, "Physics Mod ragdolls bleeding", () -> com.simpleblood.compat.PhysicsModRagdolls.tick(client.level));

        // Advance / retire hit-burst tasks.
        Iterator<ClientBloodBurstTask> it = activeBursts.iterator();
        while (it.hasNext()) {
            ClientBloodBurstTask task = it.next();
            if (!Guard.call(Guard.Part.HITS, "a hit burst playing", task::tick, false)) {
                it.remove();
            }
        }

        // Low-health dripping for all nearby living entities. Cheap tests first: most
        // entities are healthy or were not hurt recently and are skipped in two comparisons.
        if (config.globalEnabled() && config.lowHealthEnabled()) {
            Guard.run(Guard.Part.HITS, "low health dripping", () -> dripLowHealth(client));
        }
    }

    /** Low-health dripping for all nearby living entities. */
    private static void dripLowHealth(Minecraft client) {
        float threshold = config.lowHealthThreshold();
        long dripWindow = config.dripWindowMs();
        long nowMs = System.currentTimeMillis();
        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity entity)) continue;
            if (entity.getHealth() > entity.getMaxHealth() * threshold) continue;
            // Only bleed for a while after the last hit, then the wound "closes".
            if (!(entity instanceof BloodEntityAccess access)
                    || nowMs - access.simpleblood$getLastDamageTime() > dripWindow) {
                continue;
            }

            if (entity instanceof Player player) {
                if (!config.playerBleed()) continue;
                if (player.isCreative() || player.isSpectator()) continue;
            }

            // No dripping while drowning / suffocating / drying out (e.g. a fish
            // flopping out of water losing health).
            if (SimpleBlood.isEnvironmentalNoBleed(entity)) {
                continue;
            }

            if (SimpleBlood.shouldEntityDripAtLowHealth(entity)) {
                ClientBloodParticleSpawner.spawnBloodForLowHealth(client.level, entity, config);
            }
        }
    }
}
