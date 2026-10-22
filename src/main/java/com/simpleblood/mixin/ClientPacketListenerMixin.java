package com.simpleblood.mixin;

import com.simpleblood.BloodEntityAccess;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
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
}
