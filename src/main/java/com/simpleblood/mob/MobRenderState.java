package com.simpleblood.mob;

/** Which entity a render state was filled from (added to the game's render states by a mixin). */
public interface MobRenderState {
    int simpleblood$entityId();

    void simpleblood$setEntityId(int id);
}
