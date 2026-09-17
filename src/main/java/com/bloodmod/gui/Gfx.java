package com.bloodmod.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Gfx {

    //? if <26.1 {
    /*private final net.minecraft.client.gui.GuiGraphics g;

    public Gfx(net.minecraft.client.gui.GuiGraphics g, Font font) {
        this.g = g;
        this.font = font;
    }
    *///?} else {
    private final net.minecraft.client.gui.GuiGraphicsExtractor g;

    public Gfx(net.minecraft.client.gui.GuiGraphicsExtractor g, Font font) {
        this.g = g;
        this.font = font;
    }
    //?}

    public final Font font;

    public int width() { return g.guiWidth(); }
    public int height() { return g.guiHeight(); }
    public int lineHeight() { return font.lineHeight; }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1 || (argb >>> 24) == 0) return;
        g.fill(x1, y1, x2, y2, argb);
    }

    public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        if (x2 <= x1 || y2 <= y1) return;
        g.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void outline(int x1, int y1, int x2, int y2, int argb) {
        fill(x1, y1, x2, y1 + 1, argb);
        fill(x1, y2 - 1, x2, y2, argb);
        fill(x1, y1 + 1, x1 + 1, y2 - 1, argb);
        fill(x2 - 1, y1 + 1, x2, y2 - 1, argb);
    }

    public void bevel(int x1, int y1, int x2, int y2, int light, int dark) {
        fill(x1, y1, x2, y1 + 1, light);
        fill(x1, y1, x1 + 1, y2, light);
        fill(x1, y2 - 1, x2, y2, dark);
        fill(x2 - 1, y1, x2, y2, dark);
    }

    public int textWidth(String s) { return font.width(s); }

    public void text(String s, int x, int y, int argb) { text(s, x, y, argb, true); }

    public void text(String s, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) == 0) return;
        //? if <26.1 {
        /*g.drawString(font, s, x, y, argb, shadow);
        *///?} else {
        g.text(font, s, x, y, argb, shadow);
        //?}
    }

    public void textCentered(String s, int cx, int y, int argb) {
        text(s, cx - font.width(s) / 2, y, argb, true);
    }

    public void textRight(String s, int rightX, int y, int argb) {
        text(s, rightX - font.width(s), y, argb, true);
    }

    public void textClipped(String s, int x, int y, int maxWidth, int argb) {
        if (font.width(s) <= maxWidth) {
            text(s, x, y, argb, true);
            return;
        }
        String cut = font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width("...")));
        text(cut + "...", x, y, argb, true);
    }

    public List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(s), width)) {
            StringBuilder sb = new StringBuilder();
            line.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
            out.add(sb.toString());
        }
        return out;
    }

    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier SLIDER = Identifier.withDefaultNamespace("widget/slider");
    private static final Identifier SLIDER_HOVER = Identifier.withDefaultNamespace("widget/slider_highlighted");
    private static final Identifier HANDLE = Identifier.withDefaultNamespace("widget/slider_handle");
    private static final Identifier HANDLE_HOVER = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");
    private static final Identifier FIELD = Identifier.withDefaultNamespace("widget/text_field");
    private static final Identifier FIELD_HOVER = Identifier.withDefaultNamespace("widget/text_field_highlighted");
    private static final Identifier CHECK = Identifier.withDefaultNamespace("widget/checkbox");
    private static final Identifier CHECK_HOVER = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
    private static final Identifier CHECK_ON = Identifier.withDefaultNamespace("widget/checkbox_selected");
    private static final Identifier CHECK_ON_HOVER = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");

    public void sprite(Identifier id, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        //? if 1.21.1 {
        /*g.blitSprite(id, x, y, w, h);
        *///?} else {
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, w, h);
        //?}
    }

    public void button(int x, int y, int w, int h, boolean enabled, boolean hovered) {
        sprite(!enabled ? BUTTON_OFF : hovered ? BUTTON_HOVER : BUTTON, x, y, w, h);
    }

    public void sliderTrack(int x, int y, int w, int h, boolean hovered) {
        sprite(hovered ? SLIDER_HOVER : SLIDER, x, y, w, h);
    }

    public void sliderHandle(int x, int y, int w, int h, boolean hovered) {
        sprite(hovered ? HANDLE_HOVER : HANDLE, x, y, w, h);
    }

    public void textField(int x, int y, int w, int h, boolean focused) {
        sprite(focused ? FIELD_HOVER : FIELD, x, y, w, h);
    }

    public void checkbox(int x, int y, int size, boolean on, boolean hovered) {
        sprite(on ? (hovered ? CHECK_ON_HOVER : CHECK_ON) : (hovered ? CHECK_HOVER : CHECK), x, y, size, size);
    }

    public void scrollbar(int x, int y, int w, int trackH, int thumbY, int thumbH) {
        sprite(SCROLLER_BG, x, y, w, trackH);
        sprite(SCROLLER, x, thumbY, w, thumbH);
    }

    private net.minecraft.client.gui.navigation.ScreenRectangle clip;

    public void scissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, Math.max(x1, x2), Math.max(y1, y2));
        clip = new net.minecraft.client.gui.navigation.ScreenRectangle(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public void unscissor() {
        g.disableScissor();
        clip = null;
    }

    public void scene(com.bloodmod.surface.PreviewScene scene, int x0, int y0, int x1, int y1, float scale,
                      float yaw, float pitch, net.minecraft.world.phys.Vec3 focus) {
        if (x1 <= x0 || y1 <= y0) return;
        //? if 1.21.1 {
        /*com.mojang.blaze3d.vertex.PoseStack pose = g.pose();
        g.enableScissor(x0, y0, x1, y1);
        pose.pushPose();
        pose.translate((x0 + x1) / 2f, (y0 + y1) / 2f, Math.min(380f, 50f + 2.5f * scale));
        pose.scale(scale, scale, -scale);
        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource source = g.bufferSource();
        SceneRenderer.Buffers buffers = new SceneRenderer.Buffers() {
            @Override
            public void geometry(Object renderType, SceneRenderer.GeometryDrawer drawer) {
                drawer.draw(pose.last(), source.getBuffer((net.minecraft.client.renderer.RenderType) renderType));
            }

            @Override
            public void modelPart(net.minecraft.client.model.geom.ModelPart part, com.mojang.blaze3d.vertex.PoseStack ps, Object renderType, int light, int overlay) {
                part.render(ps, source.getBuffer((net.minecraft.client.renderer.RenderType) renderType), light, overlay);
            }
        };
        SceneRenderer.draw(scene, pose, buffers, yaw, pitch, focus);
        g.flush();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        pose.popPose();
        g.disableScissor();
        *///?} elif <26.1 {
        /*((com.bloodmod.mixin.GuiRenderStateAccessor) g).bloodmod$guiRenderState()
                .submitPicturesInPictureState(ScenePip.State.of(scene, yaw, pitch, focus, x0, y0, x1, y1, scale, clip));
        *///?} else {
        ((com.bloodmod.mixin.GuiRenderStateAccessor) g).bloodmod$guiRenderState()
                .addPicturesInPictureState(ScenePip.State.of(scene, yaw, pitch, focus, x0, y0, x1, y1, scale, clip));
        //?}
    }

    public void layer() {
        //? if >=1.21.11 {
        g.nextStratum();
        //?}
    }

    public void tooltip(List<String> lines, int mx, int my) {
        if (lines == null || lines.isEmpty()) return;
        List<Component> comps = new ArrayList<>(lines.size());
        for (String l : lines) comps.add(Component.literal(l));
        //? if 1.21.1 {
        /*g.renderTooltip(font, comps, Optional.empty(), mx, my);
        *///?} else {
        g.setTooltipForNextFrame(font, comps, Optional.empty(), mx, my);
        //?}
    }

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int alpha(int argb, float mul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * mul);
        return argb(a, argb);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t), rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t), rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }
}
