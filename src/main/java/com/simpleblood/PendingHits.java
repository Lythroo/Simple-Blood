package com.simpleblood;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public final class PendingHits {
    private PendingHits() {}

    private static final List<LivingEntity> WAITING = new ArrayList<>();
    private static final int MAX = 256;

    public static boolean hold(LivingEntity entity) {
        if (WAITING.size() >= MAX) return false;
        WAITING.add(entity);
        return true;
    }

    public static void flush() {
        if (WAITING.isEmpty()) return;
        for (int i = 0; i < WAITING.size(); i++) {
            if (WAITING.get(i) instanceof BloodEntityAccess a) a.simpleblood$flushPendingHit();
        }
        WAITING.clear();
    }

    private static final List<LivingEntity> AWAITING_HEALTH = new ArrayList<>();

    public static boolean awaitHealth(LivingEntity entity) {
        if (AWAITING_HEALTH.size() >= MAX || AWAITING_HEALTH.contains(entity)) return AWAITING_HEALTH.contains(entity);
        AWAITING_HEALTH.add(entity);
        return true;
    }

    public static void settleEvents() {
        if (AWAITING_HEALTH.isEmpty()) return;
        AWAITING_HEALTH.removeIf(e -> !(e instanceof BloodEntityAccess a) || a.simpleblood$settleEventWithoutHealth());
    }

    public static void clear() {
        WAITING.clear();
        AWAITING_HEALTH.clear();
    }
}
