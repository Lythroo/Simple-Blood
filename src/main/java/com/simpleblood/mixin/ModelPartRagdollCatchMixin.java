//? if >=1.21.11 {
package com.simpleblood.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.mob.RagdollCoats;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelPart.class, priority = 500)
public abstract class ModelPartRagdollCatchMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void simpleblood$catchRagdoll(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int colour, CallbackInfo ci) {
        if (RagdollCoats.catching()) RagdollCoats.rendering((ModelPart) (Object) this, poseStack);
    }
}
//?}
