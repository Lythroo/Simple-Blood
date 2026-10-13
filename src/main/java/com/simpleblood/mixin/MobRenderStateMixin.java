//? if >=1.21.11 {
package com.simpleblood.mixin;

import com.simpleblood.mob.MobRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Remembers which entity a render state came from, so its blood can be found while it is drawn. */
@Mixin(EntityRenderState.class)
public abstract class MobRenderStateMixin implements MobRenderState {
    @Unique
    private int simpleblood$entityId = -1;

    @Override
    public int simpleblood$entityId() {
        return simpleblood$entityId;
    }

    @Override
    public void simpleblood$setEntityId(int id) {
        simpleblood$entityId = id;
    }
}
//?}
