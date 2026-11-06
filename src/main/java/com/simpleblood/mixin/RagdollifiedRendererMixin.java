//? if >=1.21.1 {
package com.simpleblood.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.mob.RagdollCoats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public abstract class RagdollifiedRendererMixin {
    @Inject(method = {"renderMobRagdoll", "renderPlayerRagdoll"}, at = @At("HEAD"), require = 0, remap = false)
    private static void simpleblood$begin(@Coerce Object ragdoll, @Coerce Object snapshot, PoseStack pose, @Coerce Object buffers,
                                          int light, float partialTick, double offset, CallbackInfo ci) {
        RagdollCoats.begin(RagdollCoats.mobOf(ragdoll));
    }

    @Inject(method = {"renderMobRagdoll", "renderPlayerRagdoll"}, at = @At("RETURN"), require = 0, remap = false)
    private static void simpleblood$finish(@Coerce Object ragdoll, @Coerce Object snapshot, PoseStack pose, @Coerce Object buffers,
                                           int light, float partialTick, double offset, CallbackInfo ci) {
        RagdollCoats.finish(buffers, light);
    }

    @Inject(method = "renderDetachedLimb(Lcom/raiiiden/ragdollified/client/ClientDetachedLimb;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/raiiiden/ragdollified/client/RagdollBufferSource;I)V", at = @At("HEAD"), require = 0, remap = false)
    private static void simpleblood$beginLimb(@Coerce Object limb, @Coerce Object transform, PoseStack pose, @Coerce Object buffers,
                                              int light, CallbackInfo ci) {
        com.simpleblood.compat.OtherRagdolls.limbDrawn(limb, transform);
        RagdollCoats.begin(RagdollCoats.mobOf(limb));
    }

    @Inject(method = "renderDetachedLimb(Lcom/raiiiden/ragdollified/client/ClientDetachedLimb;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/raiiiden/ragdollified/client/RagdollBufferSource;I)V", at = @At("RETURN"), require = 0, remap = false)
    private static void simpleblood$finishLimb(@Coerce Object limb, @Coerce Object transform, PoseStack pose, @Coerce Object buffers,
                                               int light, CallbackInfo ci) {
        RagdollCoats.finish(buffers, light);
    }

    @Inject(method = "renderDetachedLimb(Lcom/raiiiden/ragdollified/client/ClientDetachedLimb;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/raiiiden/ragdollified/client/RagdollBufferSource;ID)V", at = @At("HEAD"), require = 0, remap = false)
    private static void simpleblood$beginLimbSince120(@Coerce Object limb, @Coerce Object transform, PoseStack pose, @Coerce Object buffers,
                                                      int light, double offset, CallbackInfo ci) {
        com.simpleblood.compat.OtherRagdolls.limbDrawn(limb, transform);
        RagdollCoats.begin(RagdollCoats.mobOf(limb));
    }

    @Inject(method = "renderDetachedLimb(Lcom/raiiiden/ragdollified/client/ClientDetachedLimb;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/raiiiden/ragdollified/client/RagdollBufferSource;ID)V", at = @At("RETURN"), require = 0, remap = false)
    private static void simpleblood$finishLimbSince120(@Coerce Object limb, @Coerce Object transform, PoseStack pose, @Coerce Object buffers,
                                                       int light, double offset, CallbackInfo ci) {
        RagdollCoats.finish(buffers, light);
    }

    //? if neoforge && 1.21.1 {
    //?}
    //? if fabric && 1.21.1 {
    //?}
}
//?}
