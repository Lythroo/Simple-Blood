package com.simpleblood.mixin;

import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
//? if >=26.1 {
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void simpleblood$bloodInCauldron(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int base = cir.getReturnValueI();
        int tinted = BloodSurfaces.cauldronWaterColour(pos, base);
        if (tinted != base) cir.setReturnValue(tinted);
    }
}
