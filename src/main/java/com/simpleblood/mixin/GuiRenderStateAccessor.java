package com.simpleblood.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the GUI render state behind the graphics object, so the settings screen can queue
 * its 3D diorama as a picture-in-picture element (there is no public way to submit a custom
 * one). On 1.21.1 the GUI is drawn immediately and no such state exists; the accessor then
 * just points at a harmless field so the mixin still applies.
 */
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
