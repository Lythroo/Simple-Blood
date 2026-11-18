package com.simpleblood.gui;

import com.simpleblood.Lang;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SavedStains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class TileShapeChange {

    private TileShapeChange() {}

    private static List<Runnable> gathered;

    public static boolean reshapes(SimpleBloodConfig from, SimpleBloodConfig to) {
        return from != null && to != null
                && (from.surfaces.resolution != to.surfaces.resolution || from.surfaces.detailedShapes != to.surfaces.detailedShapes);
    }

    public static void request(Runnable change) {
        if (gathered != null) {
            gathered.add(change);
            return;
        }
        confirm(true, () -> {
            change.run();
            BloodSurfaces.unloadAll();
        });
    }

    public static void confirm(boolean reshape, Runnable action) {
        confirm(reshape, action, () -> {});
    }

    private static void confirm(boolean reshape, Runnable action, Runnable no) {
        if (!reshape || !SavedStains.worldHasBlood()) {
            action.run();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen back = Screens.current(mc);
        Screens.open(mc, new ConfirmScreen(yes -> {
            Screens.open(mc, back);
            if (!yes) {
                no.run();
                return;
            }
            action.run();
            BloodSurfaces.clear();
        }, Component.literal(Lang.t("simpleblood.gui.confirm_reshape.remove_all_blood_on_blocks")),
                Component.literal(Lang.t("simpleblood.gui.confirm_reshape.text"))));
    }

    public static void reset(Runnable resetAll, Runnable undo, Runnable done) {
        List<Runnable> shape = new ArrayList<>();
        gathered = shape;
        try {
            resetAll.run();
        } finally {
            gathered = null;
        }
        confirm(!shape.isEmpty(), () -> {
            shape.forEach(Runnable::run);
            if (!shape.isEmpty()) BloodSurfaces.unloadAll();
            done.run();
        }, undo);
    }
}
