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

    public static void clear() {
        WAITING.clear();
    }
}
