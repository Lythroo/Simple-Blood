//? if neoforge && <1.21.11 {
/*package com.simpleblood.neoforge;

import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || BloodSurfaces.tileCount() == 0) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
                SurfaceRenderer.render(level, cam, new PoseStack().last(), source.getBuffer(type));
                source.endBatch(type);
            });
        });
    }
}
*///?}

//? if neoforge && >=1.21.11 && <26.1 {
/*package com.simpleblood.neoforge;

import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities e) -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || BloodSurfaces.tileCount() == 0) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
                SurfaceRenderer.render(level, cam, new PoseStack().last(), source.getBuffer(type));
                source.endBatch(type);
            });
        });
    }
}
*///?}

//? if neoforge && >=26.1 {
/*package com.simpleblood.neoforge;

import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || BloodSurfaces.tileCount() == 0 || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                e.getSubmitNodeCollector().submitCustomGeometry(new PoseStack(), type, (pose, consumer) ->
                        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks",
                                () -> SurfaceRenderer.render(level, cam, pose, consumer)));
            });
        });
    }
}
*///?}
