//? if neoforge {
/*package com.bloodmod.neoforge;

import com.bloodmod.gui.ConfigAccess;
import com.bloodmod.surface.BloodSurfaces;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeConfigAccessHook {
    private NeoForgeConfigAccessHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ConfigAccess.screenInitialised(e.getScreen()));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> {
            if (com.bloodmod.studio.Studio.enabled()) e.getDispatcher().register(com.bloodmod.studio.Studio.<net.minecraft.commands.CommandSourceStack>command());
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(
                Commands.literal("bloodmod")
                        .executes(ctx -> { ConfigAccess.requestOpen(); return 1; })
                        .then(Commands.literal("clear").executes(ctx -> {
                            com.bloodmod.Guard.run(com.bloodmod.Guard.Part.OTHER, "clearing blood on blocks", BloodSurfaces::clear);
                            ctx.getSource().sendSuccess(() -> Component.literal("All clean."), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            com.bloodmod.Guard.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal("Simple Blood: everything back on."), false);
                            return 1;
                        }))));
    }
}
*///?}
