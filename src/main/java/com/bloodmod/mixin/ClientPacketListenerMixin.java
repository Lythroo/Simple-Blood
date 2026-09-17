package com.bloodmod.mixin;

import com.bloodmod.BloodEntityAccess;
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
    private void bloodmod$onAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        try {
            int action = packet.getAction();
            if (action != ClientboundAnimatePacket.CRITICAL_HIT && action != ClientboundAnimatePacket.MAGIC_CRITICAL_HIT) return;
            ClientPacketListener self = (ClientPacketListener) (Object) this;
            if (self.getLevel() == null) return;
            Entity entity = self.getLevel().getEntity(packet.getId());
            if (entity instanceof BloodEntityAccess access) {
                access.bloodmod$markCrit();
            }
        } catch (Throwable t) {
            com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.HITS, "noticing a critical hit", t);
        }
    }
}
