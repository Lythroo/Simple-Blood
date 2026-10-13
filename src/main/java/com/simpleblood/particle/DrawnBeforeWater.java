package com.simpleblood.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A particle of ours that {@link UnderwaterParticles} draws before the water surface while it
 * is in water, instead of with the other particles (which the surface would hide from above).
 */
interface DrawnBeforeWater {
    /** Still alive, and in this level. */
    boolean isIn(ClientLevel level);

    /** Drawn before the water this frame, and so skipped where the particles are drawn. */
    boolean drawnBeforeWater();

    /** This particle as one quad, camera-relative, in the entity vertex format. */
    void quadBeforeWater(VertexConsumer vc, PoseStack.Pose pose, Camera camera, float partialTick);

    /** Where the camera is (the accessor was renamed). */
    static Vec3 cameraPos(Camera camera) {
        //? if <=1.21.1 {
        /*return camera.getPosition();
        *///?} else {
        return camera.position();
        //?}
    }

    /**
     * One quad of half-size {@code size} centred on the camera-relative point
     * {@code (cx, cy, cz)} and turned by {@code rotation}. The render type draws both sides.
     */
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
