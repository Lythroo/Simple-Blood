//? if >=1.21.11 {
package com.simpleblood.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.mob.MobRenderState;
import com.simpleblood.mob.RagdollCoats;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherRagdollMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void simpleblood$entityOf(net.minecraft.world.entity.Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        if (cir.getReturnValue() instanceof MobRenderState s && s.simpleblood$entityId() < 0) s.simpleblood$setEntityId(entity.getId());
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void simpleblood$begin(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                   PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        if (com.simpleblood.compat.OtherRagdolls.sarosThere()) RagdollCoats.beginEntity(state);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void simpleblood$finish(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                    PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        RagdollCoats.finishEntity(collector, state.lightCoords);
    }
}
//?}
