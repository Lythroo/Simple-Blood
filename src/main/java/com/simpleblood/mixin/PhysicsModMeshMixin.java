package com.simpleblood.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.diebuddies.physics.Mesh", remap = false)
public abstract class PhysicsModMeshMixin {
    @Inject(method = "clearMemory", at = @At("HEAD"), require = 0, remap = false)
    private void simpleblood$keepVertices(CallbackInfo ci) {
        com.simpleblood.compat.PhysicsModRagdolls.meshCleared(this);
    }
}
