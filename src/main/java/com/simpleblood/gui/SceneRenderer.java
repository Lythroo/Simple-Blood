package com.simpleblood.gui;

import com.simpleblood.SimpleBlood;
import com.simpleblood.surface.PreviewScene;
import com.simpleblood.surface.SurfaceRenderer;
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

/**
 * Draws a {@link PreviewScene} in 3D: real block textures from the block atlas, the zombie
 * model, the blood tiles through the same quad code as the world, and the mod's own particle
 * sprites as billboards. Everything goes through {@link Buffers}, which hides whether the
 * game hands us a buffer source (up to 26.1) or a submit-node collector (26.2).
 */
public final class SceneRenderer {

    private SceneRenderer() {}

    /** Where vertices go. */
    public interface Buffers {
        /** Draws custom quads with the given render type (a {@code RenderType}). */
        void geometry(Object renderType, GeometryDrawer drawer);

        /** Draws an entity model part. */
        void modelPart(ModelPart part, PoseStack pose, Object renderType, int light, int overlay);
    }

    public interface GeometryDrawer {
        void draw(PoseStack.Pose pose, VertexConsumer vc);
    }

    public static final int FULL_BRIGHT = 0xF000F0;
    private static final Identifier ZOMBIE = com.simpleblood.Ids.vanilla("textures/entity/zombie/zombie.png");
    private static final Identifier UNDERWATER = com.simpleblood.Ids.vanilla("textures/misc/underwater.png");
    /** Vertex colour for blocks seen through water: darker and shifted toward the water fog. */
    private static final int WATER_TINT = 0x6A82C4;
    private static final Identifier WHITE = com.simpleblood.Ids.mod("textures/gui/white.png");
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static ModelPart zombie;

    private static Identifier particleTexture(String name) {
        return TEXTURES.computeIfAbsent(name, n -> com.simpleblood.Ids.mod("textures/particle/" + n + ".png"));
    }

    // ------------------------------------------------------------------ render types (version forks)

    /** Lit by the GUI's entity lights: only the zombie, which is exactly what those lights are for. */
    static Object cutout(Identifier texture) {
        //? if <=1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.entityCutout(texture);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        //?}
    }

    /**
     * Opaque and unlit, and it writes depth: for the blocks. The unlit translucent type below
     * does not write depth, and on 26.2+ the submit collector draws translucent types in its
     * own order, so blocks drawn with it could land on top of the blood, and drops showed
     * through the floor. Vanilla's opaque beacon beam type is exactly "texture times colour".
     */
    static Object opaque(Identifier texture) {
        //? if <=1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.beaconBeam(texture, false);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(texture, false);
        //?}
    }

    /**
     * Translucent and unlit: the GUI's entity lights are aimed at an upright mob and leave a
     * floor seen from above dim and view-dependent, so blocks, blood, drops and clouds are
     * drawn with their shading baked into the vertex colour instead.
     */
    static Object unlit(Identifier texture) {
        //? if <=1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture);
        //?}
    }

    // ------------------------------------------------------------------ view

    /** The rotation that turns scene space into the ortho GUI space the pose arrives in. */
    public static Quaternionf viewRotation(float yaw, float pitch) {
        return new Quaternionf().rotationX((float) Math.PI).rotateX(pitch).rotateY(yaw);
    }

    /** Scene-space unit vector pointing from the scene toward the viewer. */
    public static Vec3 towardViewer(Quaternionf view) {
        Vector3f v = view.invert(new Quaternionf()).transform(new Vector3f(0, 0, -1));
        return new Vec3(v.x, v.y, v.z).normalize();
    }

    /**
     * Draws the scene. The pose must already sit at the point of the texture where the focus
     * should land, scaled so one scene block is {@code scale} texture pixels (and z mirrored,
     * as vanilla does for GUI entities).
     */
    public static void draw(PreviewScene scene, PoseStack pose, Buffers buffers, float yaw, float pitch, Vec3 focus) {
        // Drawn from inside the game's GUI renderer: an error here is reported and closes the
        // settings screen rather than the game.
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SCREEN, "drawing a preview",
                () -> drawScene(scene, pose, buffers, yaw, pitch, focus));
    }

    private static void drawScene(PreviewScene scene, PoseStack pose, Buffers buffers, float yaw, float pitch, Vec3 focus) {
        // The blood render type names the atlas texture; make sure it exists before the first
        // draw, or the texture manager registers a "missing" stand-in under that name (26.2+
        // keeps drawing with it after the real atlas appears).
        com.simpleblood.surface.BloodCanvasAtlas.get();
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

        // Blocks, shaded per face like the world does it. Under water they take the water's
        // colour like everything else down there.
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

        // The zombie.
        if (scene.mob.present) drawMob(scene, pose, buffers);

        // Painted blood, lifted a hair above the faces like in the world.
        buffers.geometry(unlit(com.simpleblood.surface.BloodCanvasAtlas.ATLAS_ID), (p, vc) -> scene.drawTiles(p, vc, camPos, FULL_BRIGHT));

        // Drops and streaks: one render type per sprite texture.
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
        // Fog clouds.
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
        // Under water: the viewer is in the pool, so everything sits in blue water fog with
        // vanilla's underwater overlay on top - two quads across the whole view, nearer than
        // anything in the scene.
        if (scene.hasWater()) {
            Vec3 veil = focus.add(toViewer.scale(4));
            buffers.geometry(unlit(WHITE), (p, vc) -> billboard(p, vc, veil, right, up, 8, 8, 0x0B2A66, 96));
            buffers.geometry(unlit(UNDERWATER), (p, vc) -> billboard(p, vc, veil, right, up, 8, 8, 0xB0C8FF, 36));
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ pieces

    private static void blockFace(PoseStack.Pose p, VertexConsumer vc, BlockPos pos, Direction face, TextureAtlasSprite s, int tint) {
        float x = pos.getX(), y = pos.getY(), z = pos.getZ();
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        // Corners counter-clockwise as seen from outside the block.
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
        // Both windings so the quad never culls, whichever way the render type is set up.
        vertex(vc, p, (float) a.x, (float) a.y, (float) a.z, 0, 1, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
        vertex(vc, p, (float) b.x, (float) b.y, (float) b.z, 1, 1, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
        vertex(vc, p, (float) cc.x, (float) cc.y, (float) cc.z, 1, 0, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
        vertex(vc, p, (float) d.x, (float) d.y, (float) d.z, 0, 0, rgb, alpha, FULL_BRIGHT, 0, 1, 0);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               int rgb, int alpha, int light, int nx, int ny, int nz) {
        //? if 1.20.1 {
        /*vc.vertex(p.pose(), x, y, z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(p.normal(), nx, ny, nz)
                .endVertex();
        *///?} else {
        vc.addVertex(p, x, y, z)
                .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, nx, ny, nz);
        //?}
    }

    /** Which of the eight pre-rotated streak sprites matches the drop's on-screen direction. */
    private static int streakIndex(Quaternionf view, PreviewScene.Drop d) {
        Vector3f v = view.transform(new Vector3f((float) d.vx, (float) d.vy, (float) d.vz));
        double angle = Math.atan2(-v.y, v.x); // screen y points down
        int idx = (int) Math.round(angle / (Math.PI / 4)) & 7;
        return idx;
    }

    private static void drawMob(PreviewScene scene, PoseStack pose, Buffers buffers) {
        if (zombie == null) {
            try {
                zombie = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.ZOMBIE);
            } catch (Throwable t) {
                SimpleBlood.LOGGER.warn("Preview zombie model unavailable", t);
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
        // Zombie arms out with vanilla's slow sway; legs swing exactly as the humanoid model
        // does (cos(position * 0.6662) * 1.4 * amount), so the feet land where the scene
        // paints the prints. Standing still the amount settles to zero and the legs hang.
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
