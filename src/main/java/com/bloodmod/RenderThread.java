package com.bloodmod;

import net.minecraft.client.Minecraft;

public final class RenderThread {

    private RenderThread() {}

    public static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.isSameThread();
    }

    public static void run(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }
}
