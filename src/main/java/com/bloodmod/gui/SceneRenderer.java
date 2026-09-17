package com.bloodmod.gui;

import com.bloodmod.BloodMod;
import com.bloodmod.surface.PreviewScene;
import com.bloodmod.surface.SurfaceRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class SceneRenderer {

    private SceneRenderer() {}

    public interface Buffers {
        void geometry(Object renderType, GeometryDrawer drawer);

        void modelPart(ModelPart part, PoseStack pose, Object renderType, int light, int overlay);
    }

    public interface GeometryDrawer {
        void draw(PoseStack.Pose pose, VertexConsumer vc);
    }

    public static final int FULL_BRIGHT = 0xF000F0;
    private static final Identifier ZOMBIE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final Identifier UNDERWATER = Identifier.withDefaultNamespace("textures/misc/underwater.png");
    private static final int WATER_TINT = 0x6A82C4;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(BloodMod.MOD_ID, "textures/gui/white.png");
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static ModelPart zombie;

    private static Identifier particleTexture(String name) {
        return TEXTURES.computeIfAbsent(name, n -> Identifier.fromNamespaceAndPath(BloodMod.MOD_ID, "textures/particle/" + n + ".png"));
    }

    static Object cutout(Identifier texture) {
        //? if 1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.entityCutout(texture);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        //?}
    }

    static Object opaque(Identifier texture) {
        //? if 1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.beaconBeam(texture, false);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(texture, false);
        //?}
    }

    static Object unlit(Identifier texture) {
        //? if 1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture);
        //?}
    }

    public static Quaternionf viewRotation(float yaw, float pitch) {
        return new Quaternionf().rotationX((float) Math.PI).rotateX(pitch).rotateY(yaw);
    }

    public static Vec3 towardViewer(Quaternionf view) {
        Vector3f v = view.invert(new Quaternionf()).transform(new Vector3f(0, 0, -1));
        return new Vec3(v.x, v.y, v.z).normalize();
    }

    public static void draw(PreviewScene scene, PoseStack pose, Buffers buffers, float yaw, float pitch, Vec3 focus) {
        com.bloodmod.Guard.run(com.bloodmod.Guard.Part.SCREEN, "drawing a preview",
                () -> drawScene(scene, pose, buffers, yaw, pitch, focus));
    }

    private static void drawScene(PreviewScene scene, PoseStack pose, Buffers buffers, float yaw, float pitch, Vec3 focus) {
        com.bloodmod.surface.BloodCanvasAtlas.get();
        Quaternionf view = viewRotation(yaw, pitch);
        Vec3 toViewer = towardViewer(view);
        scene.camera = toViewer;
        Quaternionf inv = view.invert(new Quaternionf());
        Vector3f r = inv.transform(new Vector3f(1, 0, 0));
        Vector3f u = inv.transform(new Vector3f(0, -1, 0));
        Vec3 right = new Vec3(r.x, r.y, r.z), up = new Vec3(u.x, u.y, u.z);
        Vec3 camPos = toViewer.scale(60);

        pose.pushPose();
        //? if <26.3 {
        pose.mulPose(view);
        //?} else {
        /*pose.rotate(view);
        *///?}
        pose.translate((float) -focus.x, (float) -focus.y, (float) -focus.z);

        int blockTint = scene.hasWater() ? WATER_TINT : 0xFFFFFF;
        buffers.geometry(opaque(TextureAtlas.LOCATION_BLOCKS), (p, vc) -> {
            for (PreviewScene.Block b : scene.blocks()) {
                for (Direction face : Direction.values()) {
                    if (scene.hasBlock(b.pos().relative(face))) continue;
                    TextureAtlasSprite sprite = scene.sprite(b, face);
                    if (sprite == null) continue;
                    blockFace(p, vc, b.pos(), face, sprite, Gfx.scale(blockTint, SurfaceRenderer.faceShade(face)));
                }
            }
        });

        if (scene.mob.present) drawMob(scene, pose, buffers);

        buffers.geometry(unlit(com.bloodmod.surface.BloodCanvasAtlas.ATLAS_ID), (p, vc) -> scene.drawTiles(p, vc, camPos, FULL_BRIGHT));

        Map<String, java.util.List<PreviewScene.Drop>> byTexture = new HashMap<>();
        for (PreviewScene.Drop d : scene.drops()) {
            String tex = switch (d.kind) {
                case 1 -> "blood_splash_" + d.sprite;
                case 2 -> "blood_streak_" + streakIndex(view, d);
                case 3 -> "blood_drop";
                default -> "blood_" + d.sprite;
            };
            byTexture.computeIfAbsent(tex, k -> new java.util.ArrayList<>()).add(d);
        }
        for (Map.Entry<String, java.util.List<PreviewScene.Drop>> e : byTexture.entrySet()) {
            buffers.geometry(unlit(particleTexture(e.getKey())), (p, vc) -> {
                for (PreviewScene.Drop d : e.getValue()) {
                    int c = Gfx.scale(d.colour, d.tone);
                    float half = d.kind == 3 ? 0.05f : d.size;
                    float hh = d.kind == 3 ? 0.10f : half;
                    billboard(p, vc, new Vec3(d.x, d.y, d.z), right, up, half, hh, c, 243);
                }
            });
        }
        Map<Integer, java.util.List<PreviewScene.Fog>> fogBySprite = new HashMap<>();
        for (PreviewScene.Fog f : scene.fogs()) fogBySprite.computeIfAbsent(f.sprite, k -> new java.util.ArrayList<>()).add(f);
        for (Map.Entry<Integer, java.util.List<PreviewScene.Fog>> e : fogBySprite.entrySet()) {
            buffers.geometry(unlit(particleTexture("blood_fog_" + e.getKey())), (p, vc) -> {
                for (PreviewScene.Fog f : e.getValue()) {
                    float agePct = f.age / (float) Math.max(1, f.life);
                    float fade = agePct < 0.65f ? 1f : 1f - (float) Math.pow((agePct - 0.65f) / 0.35f, 2);
                    float grow = Math.min(1f, 0.4f + f.age / 40f);
                    int alpha = Math.round(255 * f.alpha * fade);
                    if (alpha <= 2) continue;
                    billboard(p, vc, new Vec3(f.x, f.y, f.z), right, up, f.size * grow, f.size * grow, f.colour, alpha);
                }
            });
        }
        if (scene.hasWater()) {
            Vec3 veil = focus.add(toViewer.scale(4));
            buffers.geometry(unlit(WHITE), (p, vc) -> billboard(p, vc, veil, right, up, 8, 8, 0x0B2A66, 96));
            buffers.geometry(unlit(UNDERWATER), (p, vc) -> billboard(p, vc, veil, right, up, 8, 8, 0xB0C8FF, 36));
        }
        pose.popPose();
    }

    private static void blockFace(PoseStack.Pose p, VertexConsumer vc, BlockPos pos, Direction face, TextureAtlasSprite s, int tint) {
        float x = pos.getX(), y = pos.getY(), z = pos.getZ();
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        switch (face) {
            case UP -> quad(p, vc, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, x, y + 1, z, u0, v1, u1, v1, u1, v0, u0, v0, nx, ny, nz, tint);
            case DOWN -> quad(p, vc, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, u0, v0, u1, v0, u1, v1, u0, v1, nx, ny, nz, tint);
            case NORTH -> quad(p, vc, x + 1, y + 1, z, x + 1, y, z, x, y, z, x, y + 1, z, u0, v0, u0, v1, u1, v1, u1, v0, nx, ny, nz, tint);
            case SOUTH -> quad(p, vc, x, y + 1, z + 1, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, u0, v0, u0, v1, u1, v1, u1, v0, nx, ny, nz, tint);
            case WEST -> quad(p, vc, x, y + 1, z, x, y, z, x, y, z + 1, x, y + 1, z + 1, u0, v0, u0, v1, u1, v1, u1, v0, nx, ny, nz, tint);
            case EAST -> quad(p, vc, x + 1, y + 1, z + 1, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z, u0, v0, u0, v1, u1, v1, u1, v0, nx, ny, nz, tint);
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float ua, float va, float ub, float vb, float uc, float vcc, float ud, float vd,
                             int nx, int ny, int nz, int tint) {
        vertex(vc, p, x0, y0, z0, ua, va, tint, 255, FULL_BRIGHT, nx, ny, nz);
        vertex(vc, p, x1, y1, z1, ub, vb, tint, 255, FULL_BRIGHT, nx, ny, nz);
        vertex(vc, p, x2, y2, z2, uc, vcc, tint, 255, FULL_BRIGHT, nx, ny, nz);
        vertex(vc, p, x3, y3, z3, ud, vd, tint, 255, FULL_BRIGHT, nx, ny, nz);
    }

    private static void billboard(PoseStack.Pose p, VertexConsumer vc, Vec3 c, Vec3 right, Vec3 up,
                                  float hw, float hh, int rgb, int alpha) {
        Vec3 r = right.scale(hw), u = up.scale(hh);
        Vec3 a = c.subtract(r).subtract(u), b = c.add(r).subtract(u), cc = c.add(r).add(u), d = c.subtract(r).add(u);
        vertex(vc, p, (float) a.x, (float) a.y, (float) a.z, 0, 1, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
        vertex(vc, p, (float) b.x, (float) b.y, (float) b.z, 1, 1, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
        vertex(vc, p, (float) cc.x, (float) cc.y, (float) cc.z, 1, 0, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
        vertex(vc, p, (float) d.x, (float) d.y, (float) d.z, 0, 0, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               int rgb, int alpha, int light, int nx, int ny, int nz) {
        vc.addVertex(p, x, y, z)
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, nx, ny, nz);
    }

    private static int streakIndex(Quaternionf view, PreviewScene.Drop d) {
        Vector3f v = view.transform(new Vector3f((float) d.vx, (float) d.vy, (float) d.vz));
        double angle = Math.atan2(-v.y, v.x);
        int idx = (int) Math.round(angle / (Math.PI / 4)) & 7;
        return idx;
    }

    private static void drawMob(PreviewScene scene, PoseStack pose, Buffers buffers) {
        if (zombie == null) {
            try {
                zombie = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.ZOMBIE);
            } catch (Throwable t) {
                BloodMod.LOGGER.warn("Preview zombie model unavailable", t);
                return;
            }
        }
        PreviewScene.Mob mob = scene.mob;
        pose.pushPose();
        pose.translate((float) mob.x, (float) mob.y, (float) mob.z);
        //? if <26.3 {
        pose.mulPose(new Quaternionf().rotationY((float) Math.PI + mob.yaw));
        //?} else {
        /*pose.rotate(new Quaternionf().rotationY((float) Math.PI + mob.yaw));
        *///?}
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        float sway = (float) Math.sin(mob.age * 0.067f) * 0.05f;
        float step = (float) Math.cos(mob.swing * 0.6662f) * 1.4f * mob.amount;
        part(zombie, "right_arm", -1.45f + sway, 0.08f, 0f);
        part(zombie, "left_arm", -1.45f - sway, -0.08f, 0f);
        part(zombie, "right_leg", step, 0, 0);
        part(zombie, "left_leg", -step, 0, 0);
        part(zombie, "head", 0.05f, (float) Math.sin(mob.age * 0.03f) * 0.12f, 0);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(mob.hurtTicks > 0));
        buffers.modelPart(zombie, pose, cutout(ZOMBIE), FULL_BRIGHT, overlay);
        pose.popPose();
    }

    private static void part(ModelPart root, String name, float xRot, float yRot, float zRot) {
        try {
            ModelPart p = root.getChild(name);
            p.xRot = xRot;
            p.yRot = yRot;
            p.zRot = zRot;
            if (name.equals("head")) {
                ModelPart hat = root.getChild("hat");
                hat.xRot = xRot; hat.yRot = yRot; hat.zRot = zRot;
            }
        } catch (Throwable ignored) {}
    }
}
