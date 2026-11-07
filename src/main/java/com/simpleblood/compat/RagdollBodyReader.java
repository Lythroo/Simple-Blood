package com.simpleblood.compat;

import com.simpleblood.mob.RagdollBodies;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;

public final class RagdollBodyReader {

    private RagdollBodyReader() {}

    private static final List<RagdollBodies.Body> READ = new ArrayList<>();

    public static void tick(ClientLevel level) {
        READ.clear();
        if (level != null && PhysicsModRagdolls.anyRagdollMod()
                && (com.simpleblood.mob.MobBlood.catchesDrops() || PhysicsModRagdolls.enabled())) {
            OtherRagdolls.bodies(level, READ);
            PhysicsModRagdolls.bodies(level, READ);
        }
        RagdollBodies.publish(READ);
    }
}
