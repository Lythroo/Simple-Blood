//? if fabric && 1.21.1 {
/*package com.bloodmod.fabric;

import com.bloodmod.surface.BloodSurfaces;
import com.bloodmod.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, hit) -> {
            ClientLevel level = ctx.world();
            if (level == null || ctx.consumers() == null || BloodSurfaces.tileCount() == 0) return true;
            com.bloodmod.Guard.run(com.bloodmod.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                SurfaceRenderer.render(level, cam, new PoseStack().last(), ctx.consumers().getBuffer(type));
                if (ctx.consumers() instanceof MultiBufferSource.BufferSource source) {
                    source.endBatch(type);
                }
            });
            return true;
        });
    }
}
*///?}

//? if fabric && >1.21.1 && <26.1 {
/*package com.bloodmod.fabric;

import com.bloodmod.surface.BloodSurfaces;
import com.bloodmod.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || ctx.consumers() == null || BloodSurfaces.tileCount() == 0) return;
            com.bloodmod.Guard.run(com.bloodmod.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                SurfaceRenderer.render(level, cam, new PoseStack().last(), ctx.consumers().getBuffer(type));
                if (ctx.consumers() instanceof MultiBufferSource.BufferSource source) {
                    source.endBatch(type);
                }
            });
        });
    }
}
*///?}

//? if fabric && >=26.1 {
package com.bloodmod.fabric;

import com.bloodmod.surface.BloodSurfaces;
import com.bloodmod.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

final class FabricSurfaceHook {
    private FabricSurfaceHook() {}

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || BloodSurfaces.tileCount() == 0 || !com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.SURFACES)) return;
            com.bloodmod.Guard.run(com.bloodmod.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                ctx.submitNodeCollector().submitCustomGeometry(new PoseStack(), type, (pose, consumer) ->
                        com.bloodmod.Guard.run(com.bloodmod.Guard.Part.SURFACES, "drawing blood on blocks",
                                () -> SurfaceRenderer.render(level, cam, pose, consumer)));
            });
        });
    }
}
//?}
