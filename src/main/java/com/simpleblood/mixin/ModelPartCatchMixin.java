// Catches a mob's drawn parts for the blood on mobs (1.21.1 and older; later versions read the
// model's faces straight off it). Listed in the mixins only on those versions.
//
// Hooked at the head of render() and not in compile(): Sodium and Embeddium cancel render()
// right there and draw the part and all its children themselves, so compile() and the
// children's render() never run with them. The priority puts this ahead of their cancel.

//? if 1.21.1 {
/*package com.simpleblood.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.mob.ModelFacesOfMob;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelPart.class, priority = 500)
public abstract class ModelPartCatchMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void simpleblood$catch(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int colour, CallbackInfo ci) {
        if (ModelFacesOfMob.catching()) ModelFacesOfMob.rendering((ModelPart) (Object) this, poseStack);
    }
}
*///?}

//? if 1.20.1 {
/*package com.simpleblood.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.mob.ModelFacesOfMob;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelPart.class, priority = 500)
public abstract class ModelPartCatchMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void simpleblood$catch(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                                   float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (ModelFacesOfMob.catching()) ModelFacesOfMob.rendering((ModelPart) (Object) this, poseStack);
    }
}
*///?}
