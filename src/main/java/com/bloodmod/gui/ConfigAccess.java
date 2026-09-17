package com.bloodmod.gui;

import com.bloodmod.mixin.ScreenInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

public final class ConfigAccess {

    private ConfigAccess() {}

    private static final int W = 44, H = 20, GAP = 6;
    private static final Component LABEL = Component.literal("Blood");
    private static final Component TIP = Component.literal("Simple Blood settings");

    private static boolean openRequested;
    private static Screen pending;
    private static Button placed;

    public static void requestOpen() {
        openRequested = true;
    }

    public static void screenInitialised(Screen screen) {
        if (screen instanceof TitleScreen || screen instanceof PauseScreen) pending = screen;
    }

    public static void tick(Minecraft mc) {
        if (openRequested) {
            openRequested = false;
            Screens.open(mc, new BloodConfigScreen(Screens.current(mc)));
        }
        if (pending != null) {
            Screen screen = pending;
            pending = null;
            if (Screens.current(mc) == screen) place(mc, screen);
        }
    }

    private static void place(Minecraft mc, Screen screen) {
        if (placed != null && screen.children().contains(placed)) return;
        List<AbstractWidget> widgets = new ArrayList<>();
        AbstractWidget options = null;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w)) continue;
            widgets.add(w);
            if (options == null && w.getMessage().getContents() instanceof TranslatableContents t && "menu.options".equals(t.getKey())) {
                options = w;
            }
        }
        int sw = screen.width, sh = screen.height;
        int gridLeft = sw / 2 - 102, gridRight = sw / 2 + 102;
        int top = Integer.MAX_VALUE;
        for (AbstractWidget w : widgets) if (w.getY() > 20 && w.getX() >= gridLeft - 10 && w.getX() <= gridRight) top = Math.min(top, w.getY());
        List<int[]> spots = new ArrayList<>();
        if (options != null) {
            spots.add(new int[]{gridRight + GAP, options.getY()});
            spots.add(new int[]{gridLeft - GAP - W, options.getY()});
        }
        if (top != Integer.MAX_VALUE) {
            spots.add(new int[]{gridRight + GAP, top});
            spots.add(new int[]{gridLeft - GAP - W, top});
        }
        spots.add(new int[]{sw - W - GAP, sh - H - GAP});
        spots.add(new int[]{sw - W - GAP, GAP});
        for (int[] s : spots) {
            if (s[0] < 2 || s[1] < 2 || s[0] + W > sw - 2 || s[1] + H > sh - 2) continue;
            if (!free(widgets, s[0], s[1])) continue;
            Button b = new BloodButton(s[0], s[1], W, H, LABEL, btn -> Screens.open(mc, new BloodConfigScreen(screen)));
            b.setTooltip(Tooltip.create(TIP));
            ((ScreenInvoker) screen).bloodmod$addWidget(b);
            placed = b;
            return;
        }
    }

    private static boolean free(List<AbstractWidget> widgets, int x, int y) {
        for (AbstractWidget w : widgets) {
            if (x < w.getX() + w.getWidth() + 2 && x + W + 2 > w.getX()
                    && y < w.getY() + w.getHeight() + 2 && y + H + 2 > w.getY()) return false;
        }
        return true;
    }
}
