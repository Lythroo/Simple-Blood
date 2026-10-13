//? if >=1.21.11 {
package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The layer every mob is drawn with that puts its blood on it (see {@link MobBlood}). */
public final class MobBloodLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    public MobBloodLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        if (state.isInvisible) return;
        int id = ((MobRenderState) state).simpleblood$entityId();
        if (!MobBlood.has(id) || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on a mob",
                () -> MobBlood.submit(id, state, getParentModel().root(), poseStack, collector, light));
    }
}
//?}
