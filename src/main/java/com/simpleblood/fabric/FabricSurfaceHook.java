//? if fabric && 1.20.1 {
/*package com.simpleblood.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, hit) -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.draw(ctx.matrixStack().last()));
            draw(ctx.world(), ctx.matrixStack().last(), ctx.consumers(), false);
            return true;
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> draw(ctx.world(), ctx.matrixStack().last(), ctx.consumers(), true));
    }

    private static void draw(ClientLevel level, PoseStack.Pose pose, MultiBufferSource consumers, boolean onSeeThrough) {
        if (level == null || consumers == null || BloodSurfaces.tileCount() == 0) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
            RenderType type = (RenderType) SurfaceRenderer.renderType();
            SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), pose, consumers.getBuffer(type), onSeeThrough);
            if (consumers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
        });
    }
}
*///?}

//? if fabric && 1.21.1 {
/*package com.simpleblood.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, hit) -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.draw(new PoseStack().last()));
            draw(ctx.world(), ctx.consumers(), false);
            return true;
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> draw(ctx.world(), ctx.consumers(), true));
    }

    private static void draw(ClientLevel level, MultiBufferSource consumers, boolean onSeeThrough) {
        if (level == null || consumers == null || BloodSurfaces.tileCount() == 0) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
            RenderType type = (RenderType) SurfaceRenderer.renderType();
            SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), new PoseStack().last(), consumers.getBuffer(type), onSeeThrough);
            if (consumers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
        });
    }
}
*///?}

//? if fabric && >1.21.1 && <26.1 {
/*package com.simpleblood.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.draw(new PoseStack().last()));
            draw(ctx.consumers(), false);
        });
        WorldRenderEvents.END_MAIN.register(ctx -> draw(Minecraft.getInstance().renderBuffers().bufferSource(), true));
    }

    private static void draw(MultiBufferSource consumers, boolean onSeeThrough) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || consumers == null || BloodSurfaces.tileCount() == 0) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
            RenderType type = (RenderType) SurfaceRenderer.renderType();
            SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), new PoseStack().last(), consumers.getBuffer(type), onSeeThrough);
            if (consumers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
        });
    }
}
*///?}

//? if fabric && >=26.1 && <26.2 {
package com.simpleblood.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.submit(ctx.submitNodeCollector()));
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || BloodSurfaces.tileCount() == 0 || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                ctx.submitNodeCollector().submitCustomGeometry(new PoseStack(), type, (pose, consumer) ->
                        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks",
                                () -> SurfaceRenderer.render(level, cam, pose, consumer, false)));
            });
        });
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || BloodSurfaces.tileCount() == 0) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), new PoseStack().last(), source.getBuffer(type), true);
                source.endBatch(type);
            });
        });
    }
}
//?}

//? if fabric && >=26.2 {
/*package com.simpleblood.fabric;

import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.submit(ctx.submitNodeCollector()));
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || BloodSurfaces.tileCount() == 0 || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks",
                    () -> SurfaceRenderer.submit(level, ctx.submitNodeCollector()));
        });
    }
}
*///?}
