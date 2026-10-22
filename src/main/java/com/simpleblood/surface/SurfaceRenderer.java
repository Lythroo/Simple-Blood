package com.simpleblood.surface;

import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class SurfaceRenderer {

    private SurfaceRenderer() {}

    public static net.minecraft.client.Camera camera() {
        //? if >=26.2 {
        /*return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
        *///?} else {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        //?}
    }

    public static Vec3 cameraPos() {
        net.minecraft.client.Camera cam = camera();
        //? if <=1.21.1 {
        /*return cam.getPosition();
        *///?} else {
        return cam.position();
        //?}
    }

    private static float partialTick() {
        //? if 1.20.1 {
        /*return net.minecraft.client.Minecraft.getInstance().getFrameTime();
        *///?} elif 1.21.1 {
        /*return net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        *///?} else {
        return net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?}
    }

    public static Object renderType() {
        //? if <=1.21.1 {
        /*return net.minecraft.client.renderer.RenderType.entityTranslucent(BloodCanvasAtlas.ATLAS_ID);
        *///?} else {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(BloodCanvasAtlas.ATLAS_ID);
        //?}
    }

    public static void render(ClientLevel level, Vec3 cam, PoseStack.Pose pose, VertexConsumer vc, boolean onSeeThrough) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.surfaces.enabled || BloodSurfaces.tileCount() == 0) return;

        double maxDistSq = (double) cfg.surfaces.renderDistance * cfg.surfaces.renderDistance;

        for (CanvasTile t : BloodSurfaces.tiles()) {
            if (t.slot < 0 || t.isEmpty()) continue;

            Vec3 c = t.center();
            double dx = c.x - cam.x, dy = c.y - cam.y, dz = c.z - cam.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistSq) continue;

            int nowTick = BloodSurfaces.now();
            if (nowTick - t.lightTick >= 5) {
                t.light = light(level, t);
                t.onSeeThrough = seeThrough(t) || BlockToneSampler.isSeeThrough(level.getBlockState(t.pos));
                t.lightTick = nowTick;
            }
            if (t.onSeeThrough != onSeeThrough) continue;
            double sway = t.swayPivot == null ? 0 : SignFaces.sway(level.getGameTime(), partialTick(), t.pos);
            int light = t.glow > 0 ? withBlockLight(t.light, t.glow) : t.light;
            double lift = 0.0025 + Math.sqrt(distSq) * 0.00012 + (t.swayPivot == null ? 0 : 0.004);
            Vec3 origin = cam;
            if (t.mover != null && t.followsMover && !t.mover.isRemoved()) {
                float pt = partialTick();
                origin = cam.subtract(t.mover.getXOff(pt), t.mover.getYOff(pt), t.mover.getZOff(pt));
            }
            drawTile(t, pose, vc, origin, origin, light, lift, 255, sway);
        }
    }

    static void drawTile(CanvasTile t, PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, Vec3 origin, int light, double lift) {
        drawTile(t, pose, vc, cam, origin, light, lift, 255);
    }

    public static float faceShade(Direction face) {
        return switch (face) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
    }

    static void drawTile(CanvasTile t, PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, Vec3 origin, int light, double lift, int grey) {
        drawTile(t, pose, vc, cam, origin, light, lift, grey, 0);
    }

    static void drawTile(CanvasTile t, PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, Vec3 origin, int light, double lift, int grey, double sway) {
        float texel = BloodCanvasAtlas.texel();
        Vec3 o = new Vec3(t.ox, t.oy, t.oz), u = new Vec3(t.ux, t.uy, t.uz), v = new Vec3(t.vx, t.vy, t.vz), n = new Vec3(t.nx, t.ny, t.nz);
        if (sway != 0 && t.swayPivot != null) {
            o = t.swayPivot.add(swing(o.subtract(t.swayPivot), t.swayAxis, sway));
            u = swing(u, t.swayAxis, sway);
            v = swing(v, t.swayAxis, sway);
            n = swing(n, t.swayAxis, sway);
        }
        double nx = n.x, ny = n.y, nz = n.z;
        double cx = o.x + (u.x * t.w + v.x * t.h) * 0.5, cy = o.y + (u.y * t.w + v.y * t.h) * 0.5, cz = o.z + (u.z * t.w + v.z * t.h) * 0.5;
        double dx = cx - cam.x, dy = cy - cam.y, dz = cz - cam.z;
        boolean behind = -(dx * nx + dy * ny + dz * nz) <= 0;
        if (behind && !seeThrough(t)) return;

        double ox = o.x + (nx - u.x - v.x) * lift - origin.x;
        double oy = o.y + (ny - u.y - v.y) * lift - origin.y;
        double oz = o.z + (nz - u.z - v.z) * lift - origin.z;
        double w2 = t.w + 2 * lift, h2 = t.h + 2 * lift;
        double uxw = u.x * w2, uyw = u.y * w2, uzw = u.z * w2;
        double vxh = v.x * h2, vyh = v.y * h2, vzh = v.z * h2;

        float u0 = BloodCanvasAtlas.u0(t.slot), v0 = BloodCanvasAtlas.v0(t.slot);
        float u1 = u0 + t.pw * texel, v1 = v0 + t.ph * texel;

        double cxv = u.y * v.z - u.z * v.y, cyv = u.z * v.x - u.x * v.z, czv = u.x * v.y - u.y * v.x;
        boolean flip = (cxv * nx + cyv * ny + czv * nz) < 0;
        int inx = (int) Math.round(nx), iny = (int) Math.round(ny), inz = (int) Math.round(nz);
        if (t.custom) { inx = 0; iny = 1; inz = 0; }
        if (behind) {
            flip = !flip;
            if (!t.custom) { inx = -inx; iny = -iny; inz = -inz; }
        }

        if (!flip) {
            vertex(vc, pose, ox, oy, oz, u0, v0, light, inx, iny, inz, grey);
            vertex(vc, pose, ox + uxw, oy + uyw, oz + uzw, u1, v0, light, inx, iny, inz, grey);
            vertex(vc, pose, ox + uxw + vxh, oy + uyw + vyh, oz + uzw + vzh, u1, v1, light, inx, iny, inz, grey);
            vertex(vc, pose, ox + vxh, oy + vyh, oz + vzh, u0, v1, light, inx, iny, inz, grey);
        } else {
            vertex(vc, pose, ox, oy, oz, u0, v0, light, inx, iny, inz, grey);
            vertex(vc, pose, ox + vxh, oy + vyh, oz + vzh, u0, v1, light, inx, iny, inz, grey);
            vertex(vc, pose, ox + uxw + vxh, oy + uyw + vyh, oz + uzw + vzh, u1, v1, light, inx, iny, inz, grey);
            vertex(vc, pose, ox + uxw, oy + uyw, oz + uzw, u1, v0, light, inx, iny, inz, grey);
        }
    }

    static boolean seeThrough(CanvasTile t) {
        if (t.state == null) return false;
        net.minecraft.world.level.block.Block b = t.state.getBlock();
        return b instanceof net.minecraft.world.level.block.HalfTransparentBlock
                || b instanceof net.minecraft.world.level.block.IronBarsBlock;
    }

    private static Vec3 swing(Vec3 p, Vec3 k, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        Vec3 kxp = k.cross(p);
        double kdp = k.dot(p);
        return new Vec3(p.x * c + kxp.x * s + k.x * kdp * (1 - c),
                p.y * c + kxp.y * s + k.y * kdp * (1 - c),
                p.z * c + kxp.z * s + k.z * kdp * (1 - c));
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z,
                               float u, float v, int light, int nx, int ny, int nz, int grey) {
        //? if 1.20.1 {
        /*vc.vertex(pose.pose(), (float) x, (float) y, (float) z)
                .color(grey, grey, grey, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        *///?} else {
        vc.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(grey, grey, grey, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        //?}
    }

    private static int withBlockLight(int packed, int level) {
        int block = (packed >> 4) & 0xF, sky = (packed >> 20) & 0xF;
        return (Math.max(block, level) << 4) | (sky << 20);
    }

    //? if >=26.2 {
    /*public static void submit(ClientLevel level, net.minecraft.client.renderer.SubmitNodeCollector collector) {
        Vec3 cam = cameraPos();
        net.minecraft.client.renderer.rendertype.RenderType type = (net.minecraft.client.renderer.rendertype.RenderType) renderType();
        collector.submitCustomGeometry(new PoseStack(), type, (pose, consumer) -> com.simpleblood.Guard.run(
                com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> render(level, cam, pose, consumer, false)));
        if (collector instanceof net.minecraft.client.renderer.SubmitNodeStorage storage) {
            storage.order(0).afterTerrain.submit(new net.minecraft.client.renderer.feature.CustomFeatureRenderer.Submit(
                    new PoseStack().last(), type, (pose, consumer) -> com.simpleblood.Guard.run(
                            com.simpleblood.Guard.Part.SURFACES, "drawing blood on blocks", () -> render(level, cam, pose, consumer, true))));
        }
    }
    *///?}

    private static int light(ClientLevel level, CanvasTile t) {
        var p = t.custom ? t.pos : t.pos.relative(t.face);
        //? if <26.1 {
        /*return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, p);
        *///?} elif <26.2 {
        return net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, p);
        //?} else {
        /*return net.minecraft.util.LightCoordsUtil.getLightCoords(level, p);
        *///?}
    }
}
