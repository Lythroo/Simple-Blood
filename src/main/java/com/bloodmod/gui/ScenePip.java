//? if >=1.21.11 && <26.1 {
/*package com.bloodmod.gui;

import com.bloodmod.surface.PreviewScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

public final class ScenePip {

    private ScenePip() {}

    public record State(PreviewScene scene, float yaw, float pitch, Vec3 focus,
                        int x0, int y0, int x1, int y1, float scale,
                        ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState {

        public static State of(PreviewScene scene, float yaw, float pitch, Vec3 focus,
                               int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissor) {
            ScreenRectangle bounds = net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
            return new State(scene, yaw, pitch, focus, x0, y0, x1, y1, scale, scissor, bounds);
        }
    }

    public static final class Renderer extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<State> {

        public Renderer(net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) {
            super(bufferSource);
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose) {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI);
            centre(state, pose);
            SceneRenderer.draw(state.scene(), pose, bufferSourceBuffers(bufferSource, pose), state.yaw(), state.pitch(), state.focus());
        }

        private static void centre(State state, PoseStack pose) {
            float halfHeight = (state.y1() - state.y0()) / 2f / Math.max(0.001f, state.scale());
            pose.translate(0f, -halfHeight, 0f);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected String getTextureLabel() {
            return "bloodmod_scene";
        }
    }

    public static SceneRenderer.Buffers bufferSourceBuffers(net.minecraft.client.renderer.MultiBufferSource.BufferSource source, PoseStack pose) {
        return new SceneRenderer.Buffers() {
            @Override
            public void geometry(Object renderType, SceneRenderer.GeometryDrawer drawer) {
                drawer.draw(pose.last(), source.getBuffer((net.minecraft.client.renderer.rendertype.RenderType) renderType));
            }

            @Override
            public void modelPart(ModelPart part, PoseStack ps, Object renderType, int light, int overlay) {
                part.render(ps, source.getBuffer((net.minecraft.client.renderer.rendertype.RenderType) renderType), light, overlay);
            }
        };
    }

}
*///?}

//? if >=26.1 && <26.2 {
package com.bloodmod.gui;

import com.bloodmod.surface.PreviewScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

public final class ScenePip {

    private ScenePip() {}

    public record State(PreviewScene scene, float yaw, float pitch, Vec3 focus,
                        int x0, int y0, int x1, int y1, float scale,
                        ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState {

        public static State of(PreviewScene scene, float yaw, float pitch, Vec3 focus,
                               int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissor) {
            ScreenRectangle bounds = net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
            return new State(scene, yaw, pitch, focus, x0, y0, x1, y1, scale, scissor, bounds);
        }
    }

    public static final class Renderer extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<State> {

        public Renderer(net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) {
            super(bufferSource);
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose) {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI);
            centre(state, pose);
            SceneRenderer.draw(state.scene(), pose, bufferSourceBuffers(bufferSource, pose), state.yaw(), state.pitch(), state.focus());
        }

        private static void centre(State state, PoseStack pose) {
            float halfHeight = (state.y1() - state.y0()) / 2f / Math.max(0.001f, state.scale());
            pose.translate(0f, -halfHeight, 0f);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected String getTextureLabel() {
            return "bloodmod_scene";
        }
    }

    public static SceneRenderer.Buffers bufferSourceBuffers(net.minecraft.client.renderer.MultiBufferSource.BufferSource source, PoseStack pose) {
        return new SceneRenderer.Buffers() {
            @Override
            public void geometry(Object renderType, SceneRenderer.GeometryDrawer drawer) {
                drawer.draw(pose.last(), source.getBuffer((net.minecraft.client.renderer.rendertype.RenderType) renderType));
            }

            @Override
            public void modelPart(ModelPart part, PoseStack ps, Object renderType, int light, int overlay) {
                part.render(ps, source.getBuffer((net.minecraft.client.renderer.rendertype.RenderType) renderType), light, overlay);
            }
        };
    }

}

//?}

//? if >=26.2 {
/*package com.bloodmod.gui;

import com.bloodmod.surface.PreviewScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

public final class ScenePip {

    private ScenePip() {}

    public record State(PreviewScene scene, float yaw, float pitch, Vec3 focus,
                        int x0, int y0, int x1, int y1, float scale,
                        ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState {

        public static State of(PreviewScene scene, float yaw, float pitch, Vec3 focus,
                               int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissor) {
            ScreenRectangle bounds = net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor);
            return new State(scene, yaw, pitch, focus, x0, y0, x1, y1, scale, scissor, bounds);
        }
    }

    public static final class Renderer extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<State> {

        public Renderer() {
            super();
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ENTITY_IN_UI);
            centre(state, pose);
            SceneRenderer.draw(state.scene(), pose, collectorBuffers(collector, pose), state.yaw(), state.pitch(), state.focus());
        }

        private static void centre(State state, PoseStack pose) {
            float halfHeight = (state.y1() - state.y0()) / 2f / Math.max(0.001f, state.scale());
            pose.translate(0f, -halfHeight, 0f);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected String getTextureLabel() {
            return "bloodmod_scene";
        }
    }

    public static SceneRenderer.Buffers collectorBuffers(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack pose) {
        return new SceneRenderer.Buffers() {
            @Override
            public void geometry(Object renderType, SceneRenderer.GeometryDrawer drawer) {
                collector.submitCustomGeometry(pose, (net.minecraft.client.renderer.rendertype.RenderType) renderType, drawer::draw);
            }

            @Override
            public void modelPart(ModelPart part, PoseStack ps, Object renderType, int light, int overlay) {
                collector.submitModelPart(part, ps, (net.minecraft.client.renderer.rendertype.RenderType) renderType, light, overlay, null);
            }
        };
    }

}
*///?}
