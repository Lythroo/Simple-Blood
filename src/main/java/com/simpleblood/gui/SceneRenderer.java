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
    private static final int DIM = 0x200020;
    private static final Identifier ZOMBIE = com.simpleblood.Ids.vanilla("textures/entity/zombie/zombie.png");
    private static final Identifier UNDERWATER = com.simpleblood.Ids.vanilla("textures/misc/underwater.png");
    private static final int WATER_TINT = 0x6A82C4;
    private static final int DARK_TINT = 0x2A3040;
    private static final Identifier WHITE = com.simpleblood.Ids.mod("textures/gui/white.png");
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static ModelPart zombie;
    private static ModelPart armor;
    //? if <=1.21.1 {
    /*private static final Identifier IRON_ARMOR = com.simpleblood.Ids.vanilla("textures/models/armor/iron_layer_1.png");
    *///?} else {
    private static final Identifier IRON_ARMOR = com.simpleblood.Ids.vanilla("textures/entity/equipment/humanoid/iron.png");
    //?}

    private static Identifier particleTexture(String name) {
        return TEXTURES.computeIfAbsent(name, n -> com.simpleblood.Ids.mod("textures/particle/" + n + ".png"));
    }

    static Object cutout(Identifier texture) {
        //? if <=1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.entityCutout(texture);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        //?}
    }

    static Object opaque(Identifier texture) {
        //? if <=1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.beaconBeam(texture, false);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(texture, false);
        //?}
    }

    static Object unlit(Identifier texture) {
        //? if <=1.21.1 {
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
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SCREEN, "drawing a preview",
                () -> drawScene(scene, pose, buffers, yaw, pitch, focus));
    }

    private static void drawScene(PreviewScene scene, PoseStack pose, Buffers buffers, float yaw, float pitch, Vec3 focus) {
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

        int blockTint = scene.hasWater() ? WATER_TINT : scene.dark() ? DARK_TINT : 0xFFFFFF;
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

        buffers.geometry(unlit(com.simpleblood.surface.BloodCanvasAtlas.ATLAS_ID), (p, vc) -> scene.drawTiles(p, vc, camPos, FULL_BRIGHT,
                scene.dark() && !scene.bloodGlows() ? 0.25f : 1f));

        Map<String, java.util.List<PreviewScene.Drop>> byTexture = new HashMap<>();
        for (PreviewScene.Drop d : scene.drops()) {
            String tex = switch (d.kind) {
                case 1 -> "blood_splash_" + d.sprite;
                case 2 -> "blood_streak_" + streakIndex(view, d);
                case 3 -> "blood_drop";
                case 4 -> "blood_spray_" + sprayIndex(view, d);
                default -> "blood_" + d.sprite;
            };
            byTexture.computeIfAbsent(tex, k -> new java.util.ArrayList<>()).add(d);
        }
        for (Map.Entry<String, java.util.List<PreviewScene.Drop>> e : byTexture.entrySet()) {
            buffers.geometry(unlit(particleTexture(e.getKey())), (p, vc) -> {
                for (PreviewScene.Drop d : e.getValue()) {
                    int c = Gfx.scale(d.colour, d.tone * (scene.dark() && !scene.bloodGlows() ? 0.25f : 1f));
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
        Map<String, java.util.List<PreviewScene.Piece>> pieceByTexture = new HashMap<>();
        for (PreviewScene.Piece pc : scene.pieces()) pieceByTexture.computeIfAbsent(pc.texture(), k -> new java.util.ArrayList<>()).add(pc);
        for (Map.Entry<String, java.util.List<PreviewScene.Piece>> e : pieceByTexture.entrySet()) {
            buffers.geometry(unlit(particleTexture(e.getKey())), (p, vc) -> {
                for (PreviewScene.Piece pc : e.getValue()) {
                    int alpha = pc.alpha();
                    if (alpha <= 2) continue;
                    int tint = scene.dark() && !pc.bright() ? Gfx.scale(pc.tint(), 0.25f) : pc.tint();
                    billboard(p, vc, new Vec3(pc.x, pc.y + (pc.resting ? pc.size * 0.6 : 0), pc.z), right, up, pc.size, pc.size, tint, alpha);
                }
            });
        }
        if (!scene.rain.isEmpty()) {
            buffers.geometry(unlit(WHITE), (p, vc) -> {
                for (Vec3 r0 : scene.rain) billboard(p, vc, r0, right, up, 0.008f, 0.12f, 0x9DB8E0, 150);
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

    private static int sprayIndex(Quaternionf view, PreviewScene.Drop d) {
        Vector3f v = view.transform(new Vector3f((float) d.vx, (float) d.vy, (float) d.vz));
        double speed = Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
        return com.simpleblood.particle.BloodSprayParticle.pick(v.x, -v.y, speed, d.shown);
    }

    private static int streakIndex(Quaternionf view, PreviewScene.Drop d) {
        Vector3f v = view.transform(new Vector3f((float) d.vx, (float) d.vy, (float) d.vz));
        double angle = Math.atan2(-v.y, v.x);
        int idx = (int) Math.round(angle / (Math.PI / 4)) & 7;
        return idx;
    }

    private static Identifier zombieTexture;

    private static Identifier zombieTexture() {
        if (zombieTexture != null) return zombieTexture;
        zombieTexture = ZOMBIE;
        //? if >=26.3 {
        /*try (java.io.InputStream in = Minecraft.getInstance().getVanillaPackResources().asResourceManager().open(ZOMBIE)) {
        *///?} else {
        try (java.io.InputStream in = Minecraft.getInstance().getVanillaPackResources()
                .getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, ZOMBIE).get()) {
        //?}
            com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(in);
            //? if <=1.21.1 {
            /*net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
            *///?} else {
            net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "simpleblood preview zombie", image);
            //?}
            Identifier id = com.simpleblood.Ids.mod("preview_zombie");
            Minecraft.getInstance().getTextureManager().register(id, texture);
            zombieTexture = id;
        } catch (Throwable t) {
            SimpleBlood.LOGGER.warn("Preview zombie skin: using the resource pack's ({})", t.toString());
        }
        return zombieTexture;
    }

    private static ModelPart playerWide, playerSlim;
    private static Identifier ownSkin;
    private static boolean ownSkinSlim;
    //? if 1.20.1 {
    /*private static final Identifier ALEX = com.simpleblood.Ids.vanilla("textures/entity/alex.png");
    *///?} else {
    private static final Identifier ALEX = com.simpleblood.Ids.vanilla("textures/entity/player/slim/alex.png");
    //?}

    private static Identifier ownSkin() {
        if (ownSkin != null) return ownSkin;
        Object[] skin = com.simpleblood.SkinColours.previewSkin();
        if (skin == null) return null;
        try {
            com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read((byte[]) skin[0]);
            if (image.getHeight() < 64) return null;
            //? if <=1.21.1 {
            /*net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
            *///?} else {
            net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "simpleblood preview skin", image);
            //?}
            Identifier id = com.simpleblood.Ids.mod("preview_skin");
            Minecraft.getInstance().getTextureManager().register(id, texture);
            ownSkinSlim = (Boolean) skin[1];
            ownSkin = id;
        } catch (Throwable t) {
            SimpleBlood.LOGGER.debug("Preview skin unreadable: {}", t.toString());
        }
        return ownSkin;
    }

    private static ModelPart playerModel(boolean slim) {
        try {
            if (slim) {
                if (playerSlim == null) playerSlim = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM);
                return playerSlim;
            }
            if (playerWide == null) playerWide = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER);
            return playerWide;
        } catch (Throwable t) {
            SimpleBlood.LOGGER.warn("Preview player model unavailable", t);
            return null;
        }
    }

    private static void drawMob(PreviewScene scene, PoseStack pose, Buffers buffers) {
        if (zombie == null) {
            try {
                zombie = com.simpleblood.compat.EmfPoses.vanilla(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.ZOMBIE));
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
        if (mob.deathTicks > 0) {
            float f = (float) Math.min(1.0, Math.sqrt(mob.deathTicks / 20.0 * 1.6));
            //? if <26.3 {
            pose.mulPose(new Quaternionf().rotationZ(f * (float) Math.PI / 2f));
            //?} else {
            /*pose.rotate(new Quaternionf().rotationZ(f * (float) Math.PI / 2f));
            *///?}
        }
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        float sway = (float) Math.sin(mob.age * 0.067f) * 0.05f;
        float step = (float) Math.cos(mob.swing * 0.6662f) * 1.4f * mob.amount;
        ModelPart body = zombie;
        Identifier texture = zombieTexture();
        boolean player = false;
        if (scene.figure == PreviewScene.YOU) {
            Identifier own = ownSkin();
            ModelPart model = own == null ? null : playerModel(ownSkinSlim);
            if (model != null) { body = model; texture = own; player = true; }
        } else if (scene.figure == PreviewScene.OTHER) {
            ModelPart model = playerModel(true);
            if (model != null) { body = model; texture = ALEX; player = true; }
        }
        float armX = player ? sway : -1.45f + sway;
        part(body, "right_arm", armX, 0.08f, player ? 0.05f : 0f);
        part(body, "left_arm", player ? -sway : -1.45f - sway, -0.08f, player ? -0.05f : 0f);
        part(body, "right_sleeve", armX, 0.08f, player ? 0.05f : 0f);
        part(body, "left_sleeve", player ? -sway : -1.45f - sway, -0.08f, player ? -0.05f : 0f);
        part(body, "right_leg", step, 0, 0);
        part(body, "left_leg", -step, 0, 0);
        part(body, "right_pants", step, 0, 0);
        part(body, "left_pants", -step, 0, 0);
        part(body, "head", 0.05f, (float) Math.sin(mob.age * 0.03f) * 0.12f, 0);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(mob.hurtTicks > 0 || mob.deathTicks > 0));
        int light = scene.dark() ? DIM : FULL_BRIGHT;
        buffers.modelPart(body, pose, cutout(texture), light, overlay);
        if (mob.armored) drawArmor(pose, buffers, sway, overlay, mob);
        pose.popPose();
        if (scene.coat() != null) drawCoat(scene, buffers);
    }

    private static void drawCoat(PreviewScene scene, Buffers buffers) {
        PreviewScene.Mob mob = scene.mob;
        PoseStack model = new PoseStack();
        model.translate((float) mob.x, (float) mob.y, (float) mob.z);
        //? if <26.3 {
        model.mulPose(new Quaternionf().rotationY((float) Math.PI + mob.yaw));
        //?} else {
        /*model.rotate(new Quaternionf().rotationY((float) Math.PI + mob.yaw));
        *///?}
        model.scale(-1f, -1f, 1f);
        model.translate(0f, -1.501f, 0f);
        com.simpleblood.SimpleBloodConfig c = com.simpleblood.SimpleBloodClient.getConfig();
        boolean runs = c == null || c.mobCoat.runs, drips = c == null || c.mobCoat.drips;
        Object quads = scene.coat().prepare(zombie, model, scene.coatLifetime(), runs, drips, FULL_BRIGHT);
        buffers.geometry(unlit(com.simpleblood.surface.BloodCanvasAtlas.ATLAS_ID), (p, vc) -> scene.coat().draw(quads, p, vc));
    }

    private static void drawArmor(PoseStack pose, Buffers buffers, float sway, int overlay, PreviewScene.Mob mob) {
        if (armor == null) {
            try {
                armor = net.minecraft.client.model.geom.builders.LayerDefinition.create(
                        net.minecraft.client.model.HumanoidModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(1.0f), 0f), 64, 32).bakeRoot();
            } catch (Throwable t) {
                SimpleBlood.LOGGER.warn("Preview armour model unavailable", t);
                return;
            }
        }
        part(armor, "right_arm", -1.45f + sway, 0.08f, 0f);
        part(armor, "left_arm", -1.45f - sway, -0.08f, 0f);
        part(armor, "head", 0.05f, (float) Math.sin(mob.age * 0.03f) * 0.12f, 0);
        for (String leg : new String[]{"right_leg", "left_leg"}) {
            try {
                armor.getChild(leg).visible = false;
            } catch (Throwable ignored) {}
        }
        buffers.modelPart(armor, pose, cutout(IRON_ARMOR), FULL_BRIGHT, overlay);
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
