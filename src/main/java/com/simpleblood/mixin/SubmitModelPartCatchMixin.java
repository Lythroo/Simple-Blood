//? if >=1.21.11 {
package com.simpleblood.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.mob.RagdollCoats;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OrderedSubmitNodeCollector.class)
public interface SubmitModelPartCatchMixin {
    @Inject(method = "submitModelPart", at = @At("HEAD"), require = 0)
    private void simpleblood$catchPart(CallbackInfo ci, @Local(argsOnly = true) ModelPart part, @Local(argsOnly = true) PoseStack pose) {
        if (RagdollCoats.catching()) RagdollCoats.rendering(part, pose);
    }
}
//?}
