package com.simpleblood;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** A line in the player's chat, from the render thread (the call is handed over from anywhere else). */
public final class Chat {

    private Chat() {}

    public static void say(String text) {
        say(Component.literal(text));
    }

    public static void say(Component line) {
        RenderThread.run(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            //? if <26.1 {
            /*mc.player.displayClientMessage(line, false);
            *///?} else {
            mc.player.sendSystemMessage(line);
            //?}
        });
    }
}
