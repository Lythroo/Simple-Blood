//? if >=1.21.11 {
package com.simpleblood.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType simpleblood$create(String name, RenderSetup setup) {
        throw new AssertionError("mixin not applied");
    }
}
//?}
