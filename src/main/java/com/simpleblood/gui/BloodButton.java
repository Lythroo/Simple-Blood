package com.simpleblood.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The vanilla button that opens the settings, with a bit of blood on it: a splat in one
 * corner, a couple of specks, and a drip down the right edge that runs a little further
 * while the mouse is on it. All flat pixel art in the mod's three tones.
 */
//? if <=1.21.1 {
/*public final class BloodButton extends Button {
*///?} else {
public final class BloodButton extends Button.Plain {
//?}

    private static final int DARK = 0xFF6E0C0C, BASE = 0xFF9E1818, LIGHT = 0xFFC42A2A;

    public BloodButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    //? if <=1.21.1 {
    /*@Override
    protected void renderWidget(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        splatter(new Gfx(g, Minecraft.getInstance().font));
    }
    *///?} elif <26.1 {
    /*@Override
    protected void renderContents(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderContents(g, mouseX, mouseY, partialTick);
        splatter(new Gfx(g, Minecraft.getInstance().font));
    }
    *///?} else {
    @Override
    protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractContents(g, mouseX, mouseY, partialTick);
        splatter(new Gfx(g, Minecraft.getInstance().font));
    }
    //?}

    private void splatter(Gfx g) {
        // Fade with the button: the title screen eases its buttons in, and the blood must not
        // show up before the button it sits on.
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        if (a < 8) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // A splat in the top-left corner.
        px(g, x + 3, y + 2, BASE); px(g, x + 4, y + 2, LIGHT);
        px(g, x + 2, y + 3, DARK); px(g, x + 3, y + 3, BASE); px(g, x + 4, y + 3, BASE); px(g, x + 5, y + 3, DARK);
        px(g, x + 3, y + 4, DARK); px(g, x + 4, y + 4, BASE);
        px(g, x + 7, y + 2, DARK);
        // Specks along the bottom-left.
        px(g, x + 6, y + h - 5, DARK); px(g, x + 8, y + h - 4, BASE); px(g, x + 7, y + h - 3, DARK);
        // A drip down the right edge, a little longer under the mouse.
        int dx = x + w - 5;
        int len = isHoveredOrFocused() ? 10 : 6;
        for (int i = 1; i <= len; i++) px(g, dx, y + i, i < 3 ? DARK : BASE);
        px(g, dx - 1, y + len + 1, BASE); px(g, dx, y + len + 1, LIGHT);
        px(g, dx - 1, y + len + 2, DARK); px(g, dx, y + len + 2, BASE);
        px(g, dx + 3, y + 3, DARK);
    }

    private void px(Gfx g, int x, int y, int argb) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        g.fill(x, y, x + 1, y + 1, (a << 24) | (argb & 0xFFFFFF));
    }
}
