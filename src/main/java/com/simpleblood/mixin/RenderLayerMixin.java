//? if >=1.21.11 {
package com.simpleblood.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.mob.MobBlood;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Layers that draw another model over a mob (wool, a drowned's outer layer, a villager's
 * clothes) go through here: the blood on mobs goes on that model where it covers the body.
 */
@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin {
    @Inject(method = "renderColoredCutoutModel", at = @At("HEAD"))
    private static void simpleblood$layerModel(Model<?> model, Identifier texture, PoseStack pose, SubmitNodeCollector collector,
                                               int light, LivingEntityRenderState state, int colour, int order, CallbackInfo ci) {
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "blood on a mob's layers",
                () -> MobBlood.layerModel(model, state, pose));
    }
}
//?}
