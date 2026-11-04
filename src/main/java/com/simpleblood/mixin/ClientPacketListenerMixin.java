package com.simpleblood.mixin;

import com.simpleblood.BloodEntityAccess;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void simpleblood$onAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        try {
            int action = packet.getAction();
            if (action != ClientboundAnimatePacket.CRITICAL_HIT && action != ClientboundAnimatePacket.MAGIC_CRITICAL_HIT) return;
            ClientPacketListener self = (ClientPacketListener) (Object) this;
            if (self.getLevel() == null) return;
            Entity entity = self.getLevel().getEntity(packet.getId());
            if (entity instanceof BloodEntityAccess access) {
                access.simpleblood$markCrit();
            }
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.HITS, "noticing a critical hit", t);
        }
    }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void simpleblood$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.HITS)) return;
        try {
            ClientPacketListener self = (ClientPacketListener) (Object) this;
            if (self.getLevel() == null) return;
            //? if <=1.21.1 {
            /*net.minecraft.world.phys.Vec3 centre = new net.minecraft.world.phys.Vec3(packet.getX(), packet.getY(), packet.getZ());
            float radius = packet.getPower();
            *///?} else {
            net.minecraft.world.phys.Vec3 centre = packet.center();
            float radius = packet.radius();
            //?}
            com.simpleblood.Blasts.heard(self.getLevel(), centre, radius);
            com.simpleblood.CreeperBlast.heard(self.getLevel(), centre, radius);
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.HITS, "noticing an explosion", t);
        }
    }
}
