package com.simpleblood.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if <=1.21.1 {
/*@Mixin(net.minecraft.client.gui.GuiGraphics.class)
public interface GuiRenderStateAccessor {
    @Accessor("minecraft")
    net.minecraft.client.Minecraft simpleblood$minecraft();
}
*///?} elif <26.1 {
/*@Mixin(net.minecraft.client.gui.GuiGraphics.class)
public interface GuiRenderStateAccessor {
    @Accessor("guiRenderState")
    net.minecraft.client.gui.render.state.GuiRenderState simpleblood$guiRenderState();
}
*///?} else {
@Mixin(net.minecraft.client.gui.GuiGraphicsExtractor.class)
public interface GuiRenderStateAccessor {
    @Accessor("guiRenderState")
    net.minecraft.client.renderer.state.gui.GuiRenderState simpleblood$guiRenderState();
}
//?}
