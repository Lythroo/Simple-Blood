package com.simpleblood.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

interface DrawnBeforeWater {
    boolean isIn(ClientLevel level);

    boolean drawnBeforeWater();

    void quadBeforeWater(VertexConsumer vc, PoseStack.Pose pose, Camera camera, float partialTick);

    static Vec3 cameraPos(Camera camera) {
        //? if <=1.21.1 {
        /*return camera.getPosition();
        *///?} else {
        return camera.position();
        //?}
    }

    static void quad(VertexConsumer vc, PoseStack.Pose pose, Quaternionf rotation, float cx, float cy, float cz,
                     float size, float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light) {
        corner(vc, pose, rotation, cx, cy, cz, 1f, -1f, size, u1, v1, r, g, b, a, light);
        corner(vc, pose, rotation, cx, cy, cz, 1f, 1f, size, u1, v0, r, g, b, a, light);
        corner(vc, pose, rotation, cx, cy, cz, -1f, 1f, size, u0, v0, r, g, b, a, light);
        corner(vc, pose, rotation, cx, cy, cz, -1f, -1f, size, u0, v1, r, g, b, a, light);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, Quaternionf rotation, float cx, float cy, float cz,
                               float dx, float dy, float size, float u, float v, float r, float g, float b, float a, int light) {
        Vector3f p = new Vector3f(dx, dy, 0f).rotate(rotation).mul(size).add(cx, cy, cz);
        //? if 1.20.1 {
        /*vc.vertex(pose.pose(), p.x(), p.y(), p.z())
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0f, 1f, 0f)
                .endVertex();
        *///?} else {
        vc.addVertex(pose, p.x(), p.y(), p.z())
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
        //?}
    }
}
