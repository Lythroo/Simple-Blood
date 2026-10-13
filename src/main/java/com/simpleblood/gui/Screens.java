package com.simpleblood.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Where the current screen lives: on {@code Minecraft} up to 26.1, on {@code Gui} from 26.2. */
public final class Screens {

    private Screens() {}

    public static Screen current(Minecraft mc) {
        //? if <26.2 {
        return mc.screen;
        //?} else {
        /*return mc.gui.screen();
        *///?}
    }

    public static void open(Minecraft mc, Screen screen) {
        //? if <26.2 {
        mc.setScreen(screen);
        //?} else {
        /*mc.gui.setScreen(screen);
        *///?}
    }
}
