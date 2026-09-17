//? if fabric {
package com.bloodmod.fabric;

import com.bloodmod.gui.ConfigAccess;
import com.bloodmod.surface.BloodSurfaces;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.network.chat.Component;

final class FabricConfigAccessHook {
    private FabricConfigAccessHook() {}

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ConfigAccess.screenInitialised(screen));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            if (com.bloodmod.studio.Studio.enabled()) dispatcher.register(com.bloodmod.studio.Studio.<FabricClientCommandSource>command());
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("bloodmod")
                        .executes(ctx -> { ConfigAccess.requestOpen(); return 1; })
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(ctx -> {
                            com.bloodmod.Guard.run(com.bloodmod.Guard.Part.OTHER, "clearing blood on blocks", BloodSurfaces::clear);
                            ctx.getSource().sendFeedback(Component.literal("All clean."));
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(ctx -> {
                            com.bloodmod.Guard.reset();
                            ctx.getSource().sendFeedback(Component.literal("Simple Blood: everything back on."));
                            return 1;
                        }))));
    }
}
//?}
