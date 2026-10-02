//? if fabric {
package com.simpleblood.fabric;

import com.simpleblood.gui.ConfigAccess;
import com.simpleblood.surface.BloodSurfaces;
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
            if (com.simpleblood.studio.Studio.enabled()) dispatcher.register(com.simpleblood.studio.Studio.<FabricClientCommandSource>command());
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("simpleblood")
                        .executes(ctx -> { ConfigAccess.requestOpen(); return 1; })
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(ctx -> {
                            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.OTHER, "clearing blood on blocks", BloodSurfaces::clear);
                            ctx.getSource().sendFeedback(Component.literal("All clean."));
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(ctx -> {
                            com.simpleblood.Guard.reset();
                            ctx.getSource().sendFeedback(Component.literal("Simple Blood: everything back on."));
                            return 1;
                        }))));
    }
}
//?}
