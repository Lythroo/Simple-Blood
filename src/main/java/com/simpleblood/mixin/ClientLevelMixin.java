package com.simpleblood.mixin;

import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void simpleblood$beforeSetBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                            CallbackInfoReturnable<Boolean> cir) {
        BloodSurfaces.blockChanging((ClientLevel) (Object) this, pos);
    }
}
