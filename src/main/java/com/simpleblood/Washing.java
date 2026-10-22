package com.simpleblood;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class Washing {

    private Washing() {}

    private static final double SPLASH_RADIUS = 4.0;

    private static final Map<Integer, Vec3> POTIONS = new HashMap<>();
    private static ClientLevel lastLevel;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            lastLevel = level;
            POTIONS.clear();
        }
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (level == null || mc.player == null || cfg == null || !cfg.globalEnabled() || mc.isPaused()) return;
        Set<Integer> seen = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof AreaEffectCloud cloud) {
                com.simpleblood.Tally.count("wash.cloud");
                wash(level, cloud.position(), cloud.getRadius(), 6, 4);
            } else if (isThrownPotion(e)) {
                seen.add(e.getId());
                POTIONS.put(e.getId(), e.position());
            }
        }
        Iterator<Map.Entry<Integer, Vec3>> it = POTIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Vec3> entry = it.next();
            if (seen.contains(entry.getKey())) continue;
            it.remove();
            Vec3 at = entry.getValue();
            if (mc.player.distanceToSqr(at) < 96 * 96) {
                com.simpleblood.Tally.count("wash.splash");
                wash(level, at, SPLASH_RADIUS, 20 * 60, 60);
            }
        }
    }

    private static boolean isThrownPotion(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().contains("potion");
    }

    private static void wash(ClientLevel level, Vec3 at, double radius, int ticks, int mobTicks) {
        com.simpleblood.surface.BloodSurfaces.washAround(level, at, radius, ticks);
        com.simpleblood.mob.MobBlood.washAround(level, at, radius, mobTicks);
        com.simpleblood.surface.Footprints.washAround(level, at, radius);
    }
}
