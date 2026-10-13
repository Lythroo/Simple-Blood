package com.simpleblood.mixin;

//? if >=1.21.11 {
import com.simpleblood.mob.MobBloodLayer;
import com.simpleblood.mob.MobRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Every mob is drawn with a layer for the blood stuck to it (see {@link com.simpleblood.mob.MobBlood}). */
@Mixin(LivingEntityRenderer.class)
//? if >=1.21.11 {
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        implements RenderLayerParent<S, M> {
    @Shadow
    protected abstract boolean addLayer(RenderLayer<S, M> layer);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void simpleblood$addBloodLayer(EntityRendererProvider.Context context, M model, float shadow, CallbackInfo ci) {
        addLayer(new MobBloodLayer<>(this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void simpleblood$rememberEntity(T entity, S state, float partialTicks, CallbackInfo ci) {
        ((MobRenderState) state).simpleblood$setEntityId(entity.getId());
    }
}
//?} else {
/*public abstract class LivingEntityRendererMixin<T extends net.minecraft.world.entity.LivingEntity> {
    // The mob is drawn, then its layers (wool, clothes, armour): catch where all their parts are
    // drawn, and put the blood on at the end, over the outermost.
    @org.spongepowered.asm.mixin.injection.Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void simpleblood$catchModel(T entity, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose,
                                        net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                                        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.simpleblood.mob.MobBlood.has(entity.getId())) com.simpleblood.mob.ModelFacesOfMob.startCatching();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void simpleblood$drawBlood(T entity, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose,
                                       net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!com.simpleblood.mob.ModelFacesOfMob.catching()) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on a mob",
                () -> com.simpleblood.mob.MobBlood.render(entity.getId(), entity.isInvisible(), buffers, light));
        com.simpleblood.mob.ModelFacesOfMob.stopCatching();
    }
}
*///?}
