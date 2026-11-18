package com.simpleblood.gui;

import com.simpleblood.Lang;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SavedStains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TileShapeChange {

    private TileShapeChange() {}

    public static void request(Runnable change) {
        if (!SavedStains.worldHasBlood()) {
            change.run();
            BloodSurfaces.unloadAll();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen back = Screens.current(mc);
        Screens.open(mc, new ConfirmScreen(yes -> {
            Screens.open(mc, back);
            if (!yes) return;
            change.run();
            BloodSurfaces.clear();
        }, Component.literal(Lang.t("simpleblood.gui.confirm_reshape.remove_all_blood_on_blocks")),
                Component.literal(Lang.t("simpleblood.gui.confirm_reshape.text"))));
    }
}
