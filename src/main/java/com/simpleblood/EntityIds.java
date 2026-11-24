package com.simpleblood;

import com.simpleblood.mixin.EntityIdAccessor;
import net.minecraft.world.entity.Entity;

public final class EntityIds {
    private EntityIds() {}

    public static final int NONE = -1;
    private static final int UNASSIGNED = 0;

    public static int of(Entity entity) {
        int id = ((EntityIdAccessor) entity).simpleblood$rawId();
        return id == UNASSIGNED ? NONE : id;
    }
}
