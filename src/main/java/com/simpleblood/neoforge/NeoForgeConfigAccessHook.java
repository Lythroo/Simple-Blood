//? if neoforge {
/*package com.simpleblood.neoforge;

import com.simpleblood.gui.ConfigAccess;
import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeConfigAccessHook {
    private NeoForgeConfigAccessHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ConfigAccess.screenInitialised(e.getScreen()));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(
                Commands.literal("simpleblood")
                        .executes(ctx -> { ConfigAccess.requestOpen(); return 1; })
                        .then(Commands.literal("clear").executes(ctx -> {
                            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.OTHER, "clearing blood on blocks", BloodSurfaces::clear);
                            ctx.getSource().sendSuccess(() -> Component.literal("All clean."), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            com.simpleblood.Guard.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal("Simple Blood: everything back on."), false);
                            return 1;
                        }))));
    }
}
*///?}
