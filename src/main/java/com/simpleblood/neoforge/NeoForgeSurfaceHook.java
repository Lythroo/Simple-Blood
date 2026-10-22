//? if neoforge && >1.20.1 && <1.21.11 {
/*package com.simpleblood.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                        () -> com.simpleblood.particle.UnderwaterParticles.draw(new PoseStack().last()));
                draw(false);
            } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                draw(true);
            }
        });
    }

    private static void draw(boolean onSeeThrough) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || BloodSurfaces.tileCount() == 0) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
            RenderType type = (RenderType) SurfaceRenderer.renderType();
            MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
            SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), new PoseStack().last(), source.getBuffer(type), onSeeThrough);
            source.endBatch(type);
        });
    }
}
*///?}

//? if neoforge && >=1.21.11 && <26.1 {
/*package com.simpleblood.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities e) -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.draw(new PoseStack().last()));
            draw(false);
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks e) -> draw(true));
    }

    private static void draw(boolean onSeeThrough) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || BloodSurfaces.tileCount() == 0) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
            RenderType type = (RenderType) SurfaceRenderer.renderType();
            MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
            SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), new PoseStack().last(), source.getBuffer(type), onSeeThrough);
            source.endBatch(type);
        });
    }
}
*///?}

//? if neoforge && >=26.1 && <26.2 {
/*package com.simpleblood.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.submit(e.getSubmitNodeCollector()));
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || BloodSurfaces.tileCount() == 0 || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                Vec3 cam = SurfaceRenderer.cameraPos();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                e.getSubmitNodeCollector().submitCustomGeometry(new PoseStack(), type, (pose, consumer) ->
                        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks",
                                () -> SurfaceRenderer.render(level, cam, pose, consumer, false)));
            });
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks e) -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || BloodSurfaces.tileCount() == 0) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
                MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
                RenderType type = (RenderType) SurfaceRenderer.renderType();
                SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), new PoseStack().last(), source.getBuffer(type), true);
                source.endBatch(type);
            });
        });
    }
}
*///?}

//? if neoforge && >=26.2 {
/*package com.simpleblood.neoforge;

import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> {
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                    () -> com.simpleblood.particle.UnderwaterParticles.submit(e.getSubmitNodeCollector()));
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || BloodSurfaces.tileCount() == 0 || !com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
            com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks",
                    () -> SurfaceRenderer.submit(level, e.getSubmitNodeCollector()));
        });
    }
}
*///?}

//? if neoforge && 1.20.1 {
/*package com.simpleblood.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

final class NeoForgeSurfaceHook {
    private NeoForgeSurfaceHook() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water",
                        () -> com.simpleblood.particle.UnderwaterParticles.draw(e.getPoseStack().last()));
                draw(e.getPoseStack().last(), false);
            } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                draw(e.getPoseStack().last(), true);
            }
        });
    }

    private static void draw(PoseStack.Pose pose, boolean onSeeThrough) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || BloodSurfaces.tileCount() == 0) return;
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> {
            RenderType type = (RenderType) SurfaceRenderer.renderType();
            MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
            SurfaceRenderer.render(level, SurfaceRenderer.cameraPos(), pose, source.getBuffer(type), onSeeThrough);
            source.endBatch(type);
        });
    }
}
*///?}
