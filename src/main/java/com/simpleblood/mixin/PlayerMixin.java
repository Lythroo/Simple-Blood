package com.simpleblood.mixin;

import com.simpleblood.HitContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The attack cooldown is reset the moment a swing lands, so the charge has to be read at the
 * head of {@code attack}. Only the local player's charge is known client-side; other players'
 * hits fall back to a full charge.
 */
@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void simpleblood$rememberCharge(Entity target, CallbackInfo ci) {
        try {
            Player self = (Player) (Object) this;
            if (!self.level().isClientSide()) return;
            // Where the crosshair is on the target right now: that is where the blow lands.
            net.minecraft.world.phys.Vec3 hit = null;
            net.minecraft.world.phys.HitResult look = net.minecraft.client.Minecraft.getInstance().hitResult;
            if (look instanceof net.minecraft.world.phys.EntityHitResult ehr && ehr.getEntity() == target) {
                hit = ehr.getLocation();
            }
            // A mace smash is decided the moment the swing lands: falling far enough, not gliding.
            //? if 1.20.1 {
            /*boolean smash = false; // no mace before 1.21
            *///?} else {
            boolean smash = net.minecraft.world.item.MaceItem.canSmashAttack(self);
            //?}
            HitContext.rememberLocalCharge(target.getId(), self.getAttackStrengthScale(0.5f), hit, smash);
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.HITS, "reading the attack charge", t);
        }
    }
}
