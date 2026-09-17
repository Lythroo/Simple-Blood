package com.bloodmod.mixin;

import com.bloodmod.HitContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void bloodmod$rememberCharge(Entity target, CallbackInfo ci) {
        try {
            Player self = (Player) (Object) this;
            if (!self.level().isClientSide()) return;
            net.minecraft.world.phys.Vec3 hit = null;
            net.minecraft.world.phys.HitResult look = net.minecraft.client.Minecraft.getInstance().hitResult;
            if (look instanceof net.minecraft.world.phys.EntityHitResult ehr && ehr.getEntity() == target) {
                hit = ehr.getLocation();
            }
            boolean smash = net.minecraft.world.item.MaceItem.canSmashAttack(self);
            HitContext.rememberLocalCharge(target.getId(), self.getAttackStrengthScale(0.5f), hit, smash);
        } catch (Throwable t) {
            com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.HITS, "reading the attack charge", t);
        }
    }
}
