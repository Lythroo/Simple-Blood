package com.simpleblood;

import net.minecraft.client.Minecraft;

/**
 * The render thread owns every piece of mutable state in this mod: the canvas tiles and
 * runs, the model caches, the sound budget, the particle count. Vanilla ticks particles on
 * that thread too, but mods such as AsyncParticles move particle ticks onto worker threads,
 * and anything a particle then wants to change has to be handed back here first.
 */
public final class RenderThread {

    private RenderThread() {}

    /** Whether the calling thread is the game's render thread. */
    public static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.isSameThread();
    }

    /** Runs {@code task} right away on the render thread, or queues it for it from anywhere else. */
    public static void run(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }
}
